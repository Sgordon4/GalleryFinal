package aaa.sgordon.galleryfinal.utilities;

import android.net.Uri;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.URL;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import aaa.sgordon.galleryfinal.gallery.TraversalHelper;
import aaa.sgordon.galleryfinal.repository.caches.LinkCache;
import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridAPI;
import aaa.sgordon.galleryfinal.repository.hybrid.types.HFile;

public class DirUtilities {
	private final static String TAG = "Gal.DirUtil";


	public static List<Pair<UUID, String>> readDir(@NonNull UUID dirUID) throws ContentsNotFoundException, FileNotFoundException, ConnectException {
		//Get the contents of the directory
		Uri uri = HybridAPI.getInstance().getFileContent(dirUID).first;


		//Read the directory into a list of UUID::FileName pairs
		List<Pair<UUID, String>> dirList = new ArrayList<>();
		try (InputStream inputStream = new URL(uri.toString()).openStream();
			 BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {

			String line;
			while ((line = reader.readLine()) != null) {
				//TODO Handle invalid filenames
				//Split each line into UUID::FileName and add it to our list
				String[] parts = line.trim().split(" ", 2);
				Pair<UUID, String> entry = new Pair<>(UUID.fromString(parts[0]), parts[1]);
				dirList.add(entry);
			}
		}
		catch (IOException e) { throw new RuntimeException(e); }

		return dirList;
	}

	/*
	//Only use with directory links, not normal links
	public static UUID readDirLink(UUID linkUID) throws ContentsNotFoundException, FileNotFoundException, ConnectException {
		Uri uri = HybridAPI.getInstance().getFileContent(linkUID).first;

		try (InputStream inputStream = new URL(uri.toString()).openStream();
				BufferedReader reader = new BufferedReader( new InputStreamReader(inputStream) )) {
			String firstLine = reader.readLine();
			return UUID.fromString(firstLine);
		}
		catch (IOException e) { throw new RuntimeException(e); }
	}
	 */



	public static void renameFile(@NonNull UUID fileUID, @NonNull UUID dirUID, @NonNull String newName)
			throws FileNotFoundException, ContentsNotFoundException, ConnectException {

		HybridAPI hAPI = HybridAPI.getInstance();
		try {
			hAPI.lockLocal(dirUID);

			HFile fileProps = hAPI.getFileProps(dirUID);

			//Find the fileUID in the current list and replace its name
			List<Pair<UUID, String>> dirList = readDir(dirUID);
			for(int i = 0; i < dirList.size(); i++) {
				Pair<UUID, String> entry = dirList.get(i);
				if(entry.first.equals(fileUID)) {
					dirList.set(i, new Pair<>(entry.first, newName));
					break;
				}
			}

			//Compile the list into a String
			List<String> rootLines = dirList.stream().map(pair -> pair.first+" "+pair.second)
					.collect(Collectors.toList());
			byte[] newContent = String.join("\n", rootLines).getBytes();

			//Write the new content to the directory
			hAPI.writeFile(dirUID, newContent, fileProps.checksum);

		} finally {
			hAPI.unlockLocal(dirUID);
		}
	}



	//TODO Cut this up into pieces (this is my last resort)
	public static boolean moveFiles(@NonNull List<TraversalHelper.ListItem> toMove, @NonNull UUID destinationUID, @Nullable UUID nextItem)
			throws FileNotFoundException, ContentsNotFoundException, ConnectException, NotDirectoryException {
		//System.out.println("Moving files!");

		if(toMove.isEmpty()) {
			Log.w(TAG, "moveFiles was called with no files to move!");
			return false;
		}

		LinkCache linkCache = LinkCache.getInstance();
		UUID destinationDirUID = linkCache.resolvePotentialLink(destinationUID);



		//Group each file by its parent directory so we know where to remove them from
		//Exclude links being moved inside themselves
		Map<UUID, List<UUID>> dirMap = new HashMap<>();
		Iterator<TraversalHelper.ListItem> iterator = toMove.iterator();
		while (iterator.hasNext()) {
			TraversalHelper.ListItem itemToMove = iterator.next();
			UUID parentUID = UUID.fromString(itemToMove.filePath.getParent().getFileName().toString());
			UUID fileUID = itemToMove.fileUID;

			if(destinationUID.equals(fileUID) || destinationDirUID.equals(fileUID)) {
				Log.d(TAG, "Not allowed to move links inside themselves");
				iterator.remove();
				continue;
			}

			//In case this is a link to a directory, get the actual directory UID it points to
			parentUID = linkCache.resolvePotentialLink(parentUID);

			dirMap.putIfAbsent(parentUID, new ArrayList<>());
			dirMap.get(parentUID).add(fileUID);
		}

		//-----------------------------------------------------------------------------------------

		HybridAPI hAPI = HybridAPI.getInstance();

		//Add all the files in order to the destination directory
		try {
			hAPI.lockLocal(destinationDirUID);

			HFile destinationProps = hAPI.getFileProps(destinationDirUID);
			List<Pair<UUID, String>> dirList = readDir(destinationDirUID);


			//Find the position to insert the items into
			int insertPos = 0;
			if(nextItem == null)
				//NextItem should only ever be null if we dragged to the end of the dir
				insertPos = dirList.size();
			else {
				//We need to find the nextItem in the list (if it still exists). We will insert the moved file(s) before it
				for(int i = 0; i < dirList.size(); i++) {
					if(dirList.get(i).first.equals(nextItem)) {
						insertPos = i;
						break;
					}
				}
			}


			//Convert the list items to UUID::String pairs
			List<Pair<UUID, String>> moveOrdering = toMove.stream()
					.map(item -> new Pair<>(item.fileUID, item.name))
					.collect(Collectors.toList());

			//Insert the moved files at the correct position, making sure to reposition files already in the directory
			List<Pair<UUID, String>> newList = new ArrayList<>(dirList);
			newList.add(insertPos, null);
			newList.removeAll(moveOrdering);
			int markedIndex = newList.indexOf(null);
			newList.remove(markedIndex);
			newList.addAll(markedIndex, moveOrdering);

			//If the lists are the same, this means nothing was actually moved, and we should skip the write
			//We should also skip the deletes after this because nothing was moved
			if(dirList.size() == newList.size() && dirList.equals(newList)) {
				Log.i(TAG, "When moving, no items were changed, so nothing is being written");
				return false;
			}


			//Write the list back to the directory
			List<String> newLines = newList.stream().map(pair -> pair.first+" "+pair.second)
					.collect(Collectors.toList());
			byte[] newContent = String.join("\n", newLines).getBytes();
			hAPI.writeFile(destinationDirUID, newContent, destinationProps.checksum);
		}
		finally {
			hAPI.unlockLocal(destinationDirUID);
		}

		//-----------------------------------------------------------------------------------------

		//Remove all the toMove files from their original directories
		for(UUID parentUID : dirMap.keySet()) {
			//Skip the destination dir
			if(parentUID.equals(destinationDirUID)) continue;

			List<UUID> filesToRemove = dirMap.get(parentUID);
			try {
				hAPI.lockLocal(parentUID);

				HFile parentProps = hAPI.getFileProps(parentUID);
				List<Pair<UUID, String>> dirList = readDir(parentUID);

				//Remove all files in toMove that are from this dir
				Set<UUID> filesToRemoveSet = new HashSet<>(filesToRemove);
				dirList.removeIf(item -> filesToRemoveSet.contains(item.first));

				//Write the list back to the directory
				List<String> newLines = dirList.stream().map(pair -> pair.first+" "+pair.second).collect(Collectors.toList());
				byte[] newContent = String.join("\n", newLines).getBytes();
				hAPI.writeFile(parentUID, newContent, parentProps.checksum);
			} finally {
				hAPI.unlockLocal(parentUID);
			}
		}

		return true;
	}


	public static boolean copyFiles(@NonNull List<TraversalHelper.ListItem> toCopy, @NonNull UUID destinationUID, @Nullable UUID nextItem)
			throws FileNotFoundException, ContentsNotFoundException, ConnectException {
		if(toCopy.isEmpty()) {
			Log.w(TAG, "copyFiles was called with no files to copy!");
			return false;
		}

		LinkCache linkCache = LinkCache.getInstance();
		UUID destinationDirUID = linkCache.resolvePotentialLink(destinationUID);


		//TODO Links can be moved inside themselves atm


		HybridAPI hAPI = HybridAPI.getInstance();

		//Since we're copying, we need to create a new file for each of the toCopy files
		List<Pair<UUID, String>> newFiles = new ArrayList<>();
		for(TraversalHelper.ListItem item : toCopy) {
			try {
				UUID newFileUID = hAPI.copyFile(item.fileUID, hAPI.getCurrentAccount());
				newFiles.add(new Pair<>(newFileUID, "Copy of "+item.name));
				System.out.println("CopyID: "+newFileUID+" from: "+item.fileUID);
			}
			catch (FileNotFoundException e) {
				//Skip this file
				continue;
			}
		}



		//Add all the files in order to the destination directory
		try {
			hAPI.lockLocal(destinationDirUID);

			HFile destinationProps = hAPI.getFileProps(destinationDirUID);
			List<Pair<UUID, String>> dirList = readDir(destinationDirUID);


			//Find the position to insert the items into
			int insertPos = 0;
			if(nextItem == null)
				//NextItem should only ever be null if we dragged to the end of the dir
				insertPos = dirList.size();
			else {
				//We need to find the nextItem in the list (if it still exists). We will insert the moved file(s) before it
				for(int i = 0; i < dirList.size(); i++) {
					if(dirList.get(i).first.equals(nextItem)) {
						insertPos = i;
						break;
					}
				}
			}


			//Convert the list items to UUID::String pairs
			List<Pair<UUID, String>> moveOrdering = newFiles.stream()
					.map(item -> new Pair<>(item.first, item.second))
					.collect(Collectors.toList());

			//Insert the moved files at the correct position
			List<Pair<UUID, String>> newList = new ArrayList<>(dirList);
			newList.addAll(insertPos, moveOrdering);

			//If the lists are the same, this means nothing was actually moved, and we should skip the write
			//We should also skip the deletes after this because nothing was moved
			if(dirList.size() == newList.size() && dirList.equals(newList)) {
				Log.i(TAG, "When copying, no items were changed, so nothing is being written");
				return false;
			}


			//Write the list back to the directory
			List<String> newLines = newList.stream().map(pair -> pair.first+" "+pair.second)
					.collect(Collectors.toList());
			byte[] newContent = String.join("\n", newLines).getBytes();
			hAPI.writeFile(destinationDirUID, newContent, destinationProps.checksum);
		}
		finally {
			hAPI.unlockLocal(destinationDirUID);
		}

		return true;
	}
}
