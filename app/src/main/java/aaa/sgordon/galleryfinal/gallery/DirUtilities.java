package aaa.sgordon.galleryfinal.gallery;

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

import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridAPI;
import aaa.sgordon.galleryfinal.repository.hybrid.types.HFile;

public class DirUtilities {
	private final static String TAG = "Gal.DirUtil";


	public static List<Pair<UUID, String>> readDir(@NonNull UUID dirUID) throws ContentsNotFoundException, FileNotFoundException, ConnectException {
		//Otherwise we gotta get it from the file itself
		Uri uri = HybridAPI.getInstance().getFileContent(dirUID).first;

		//Read the directory into a list of UUID::FileName pairs
		ArrayList<Pair<UUID, String>> dirList = new ArrayList<>();
		try (InputStream inputStream = new URL(uri.toString()).openStream();
			 BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {

			String line;
			while ((line = reader.readLine()) != null) {
				//Split each line into UUID::FileName and add it to our list
				String[] parts = line.trim().split(" ", 2);
				Pair<UUID, String> entry = new Pair<>(UUID.fromString(parts[0]), parts[1]);
				dirList.add(entry);
			}
		}
		catch (IOException e) { throw new RuntimeException(e); }

		return dirList;
	}

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



	//TODO Cut this up into pieces (this is my last resort)
	public static boolean moveFiles(@NonNull List<Pair<Path, String>> toMove, @NonNull UUID destinationUID, @Nullable UUID nextItem)
			throws FileNotFoundException, ContentsNotFoundException, ConnectException, NotDirectoryException {
		//System.out.println("Moving files!");

		if(toMove.isEmpty()) {
			Log.w(TAG, "moveFiles was called with no files to move!");
			return false;
		}

		DirCache dirCache = DirCache.getInstance();
		UUID destinationDirUID = dirCache.resolveDirUID(destinationUID);



		//Group each file by its parent directory so we know where to remove them from
		//Exclude links being moved inside themselves
		Map<UUID, List<UUID>> dirMap = new HashMap<>();
		Iterator<Pair<Path, String>> iterator = toMove.iterator();
		while (iterator.hasNext()) {
			Pair<Path, String> itemToMove = iterator.next();
			UUID parentUID = UUID.fromString(itemToMove.first.getParent().getFileName().toString());
			UUID fileUID = UUID.fromString(itemToMove.first.getFileName().toString());

			if(destinationUID.equals(fileUID)) {
				Log.d(TAG, "Not allowed to move links inside themselves");
				iterator.remove();
				continue;
			}

			//In case this is a link to a directory, get the actual directory UID it points to
			parentUID = dirCache.resolveDirUID(parentUID);
			if(parentUID == null) {
				Log.wtf(TAG, "When moving, parent resolution returned null! Path='"+itemToMove.first+"'");
				continue;
			}

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


			//Convert the list types to match, grabbing the fileUID out of the path
			List<Pair<UUID, String>> moveOrdering = toMove.stream().map(pair ->
							new Pair<>(UUID.fromString(pair.first.getFileName().toString()), pair.second))
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
				Log.i(TAG, "When moving, no items were moved, so nothing is being written");
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
}
