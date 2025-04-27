package aaa.sgordon.galleryfinal.utilities;

import android.content.Context;
import android.net.Uri;
import android.os.Looper;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.apache.commons.io.FilenameUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.URL;
import java.nio.file.NotDirectoryException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import aaa.sgordon.galleryfinal.repository.gallery.DirItem;
import aaa.sgordon.galleryfinal.repository.gallery.ListItem;
import aaa.sgordon.galleryfinal.repository.gallery.caches.DirCache;
import aaa.sgordon.galleryfinal.repository.gallery.caches.LinkCache;
import aaa.sgordon.galleryfinal.repository.galleryhelpers.ExportStorageHandler;
import aaa.sgordon.galleryfinal.repository.galleryhelpers.SAFGoFuckYourself;
import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridAPI;
import aaa.sgordon.galleryfinal.repository.hybrid.types.HFile;

public class DirUtilities {
	private final static String TAG = "Gal.DirUtil";





	public static List<DirItem> readDir(@NonNull UUID dirUID) throws ContentsNotFoundException, FileNotFoundException, ConnectException {
		//Get the contents of the directory
		Uri uri = HybridAPI.getInstance().getFileContent(dirUID).first;


		//Read the directory into a list of UUID::FileName pairs
		List<DirItem> dirList = new ArrayList<>();

		InputStream in = null;
		try {
			//If the file can be opened using ContentResolver, do that. Otherwise, open using URL's openStream
			try {
				in = MyApplication.getAppContext().getContentResolver().openInputStream(uri);
			} catch (FileNotFoundException e) {
				in = new URL(uri.toString()).openStream();
			}

			try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
				String line;
				while ((line = reader.readLine()) != null) {
					//TODO Handle invalid filenames
					//Split each line into UUID::isdir::islink::FileName and add it to our list
					String[] parts = line.trim().split(" ", 4);
					//System.out.println(Arrays.toString(parts));
					DirItem entry = new DirItem(UUID.fromString(parts[0]), Boolean.parseBoolean(parts[1]), Boolean.parseBoolean(parts[2]), parts[3]);
					dirList.add(entry);
				}
			}
		}
		catch (IOException e) { throw new RuntimeException(e); }
		finally {
			try {
				if(in != null) in.close();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

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


	@Nullable
	public static String getFileNameFromDir(UUID fileUID, UUID parentDirUID) {
		try {
			return DirCache.getInstance().getDirContents(parentDirUID).stream()
					.filter(item -> item.fileUID.equals(fileUID))
					.map(item -> item.name)
					.findFirst()
					.orElse(null);
		} catch (ContentsNotFoundException | FileNotFoundException | ConnectException e) {
			return null;
		}
	}


	public static void renameFile(@NonNull UUID fileUID, @NonNull UUID dirUID, @NonNull String newName)
			throws FileNotFoundException, ContentsNotFoundException, ConnectException, IOException {

		HybridAPI hAPI = HybridAPI.getInstance();
		try {
			hAPI.lockLocal(dirUID);

			HFile fileProps = hAPI.getFileProps(dirUID);

			//Find the fileUID in the current list and replace its name
			List<DirItem> dirList = readDir(dirUID);
			for(int i = 0; i < dirList.size(); i++) {
				DirItem entry = dirList.get(i);
				if(entry.fileUID.equals(fileUID)) {
					dirList.set(i, new DirItem.Builder(entry).setName(newName).build());
					break;
				}
			}

			//Compile the list into a String
			List<String> rootLines = dirList.stream().map(DirItem::toString)
					.collect(Collectors.toList());
			byte[] newContent = String.join("\n", rootLines).getBytes();

			//Write the new content to the directory
			hAPI.writeFile(dirUID, newContent, fileProps.checksum);

		} finally {
			hAPI.unlockLocal(dirUID);
		}
	}


	//Each file in renamed should have "name" updated before passing to this function
	//Returns true if all renames were successful, false if not
	public static boolean renameFiles(List<ListItem> renamed) {
		HybridAPI hAPI = HybridAPI.getInstance();

		//Map each item to its parent directory for ease of access
		Map<UUID, Map<UUID, String>> dirMap = new HashMap<>();
		for(ListItem item : renamed) {
			//If the item's parent is a link, we need the target dir or target parent
			UUID parentUID = LinkCache.getInstance().getLinkDir(item.parentUID);
			dirMap.putIfAbsent(parentUID, new HashMap<>());
			dirMap.get(parentUID).put(item.fileUID, item.getRawName());
		}


		//For each parent directory...
		for(Map.Entry<UUID, Map<UUID, String>> entry : dirMap.entrySet()) {
			UUID dirUID = entry.getKey();
			Map<UUID, String> fileUIDs = entry.getValue();

			try {
				hAPI.lockLocal(dirUID);
				HFile fileProps = hAPI.getFileProps(dirUID);

				//For each file in the directory...
				List<DirItem> dirList = DirUtilities.readDir(dirUID);
				for(int i = 0; i < dirList.size(); i++) {
					DirItem file = dirList.get(i);
					UUID fileUID = file.fileUID;

					//If the file is in the list of files to rename, rename it
					if(fileUIDs.containsKey(fileUID))
						dirList.set(i, new DirItem.Builder(file).setName(fileUIDs.get(fileUID)).build());
				}

				//Compile the list into a String
				List<String> rootLines = dirList.stream().map(DirItem::toString)
						.collect(Collectors.toList());
				byte[] newContent = String.join("\n", rootLines).getBytes();

				//Write the new content to the directory
				hAPI.writeFile(dirUID, newContent, fileProps.checksum);

			} catch (FileNotFoundException | ContentsNotFoundException e) {
				//If the directory or its contents are not found, skip it
				return false;
			} catch (ConnectException e) {
				//If we can't reach the file, skip it
			} catch (IOException e) {
				//If we can't write to the file, skip it
			} finally {
				hAPI.unlockLocal(dirUID);
			}
		}

		return true;
	}




	//Returns a list of files that were unable to be deleted, empty if all were successfully removed
	//TODO This doesn't allow deleting files from a directory listing if they aren't in our db (e.g. local on another device)
	// How would we even do that? Like, how do we notify the server this file should be deleted? Just orphan it?
	public static List<ListItem> deleteFiles(List<ListItem> toDelete) {
		HybridAPI hAPI = HybridAPI.getInstance();
		List<ListItem> failed = new ArrayList<>();

		//For each item to delete...
		Iterator<ListItem> iterator = toDelete.iterator();
		while (iterator.hasNext()) {
			ListItem item = iterator.next();
			if(!item.isDir || item.isLink) continue;

			//If the file is a directory, recursively delete all files within
			try {
				deleteDirContents(item.fileUID);
			} catch (ContentsNotFoundException | ConnectException e) {
				//If we can't reach this file, exclude this file from the list since we can't delete it
				iterator.remove();
				failed.add(item);
			}
		}



		//For each item to delete
		Map<UUID, Set<ListItem>> parentMap = new HashMap<>();
		for(ListItem item : toDelete) {
			//If the item's parent is a link, we need the target dir or target parent
			UUID parentUID = LinkCache.getInstance().getLinkDir(item.parentUID);

			//Group the files by parent
			parentMap.putIfAbsent(parentUID, new HashSet<>());
			parentMap.get(parentUID).add(item);
		}


		//For each parent directory with files to delete...
		for(Map.Entry<UUID, Set<ListItem>> entry : parentMap.entrySet()) {
			UUID parentUID = entry.getKey();
			Set<ListItem> filesToDelete = entry.getValue();
			Set<UUID> UUIDsToDelete = filesToDelete.stream().map(item -> item.fileUID).collect(Collectors.toSet());

			try {
				hAPI.lockLocal(parentUID);

				HFile destinationProps = hAPI.getFileProps(parentUID);
				List<DirItem> dirList = readDir(parentUID);

				//Remove all file entries in this dir for files that were marked for deletion
				dirList.removeIf(item -> UUIDsToDelete.contains(item.fileUID));

				//Write the list back to the directory
				List<String> newLines = dirList.stream().map(DirItem::toString)
						.collect(Collectors.toList());
				byte[] newContent = String.join("\n", newLines).getBytes();
				hAPI.writeFile(parentUID, newContent, destinationProps.checksum);
			} catch (FileNotFoundException e) {
				//If the directory doesn't exist, our job is technically done
			} catch (ContentsNotFoundException | ConnectException e) {
				//If we can't reach this directory, skip it, and mark the delete as a fail
				toDelete.removeAll(filesToDelete);
				failed.addAll(filesToDelete);
			} catch (IOException e) {
				//If we can't write to the directory, skip it, and mark the delete as a fail
				toDelete.removeAll(filesToDelete);
				failed.addAll(filesToDelete);
			} finally {
				hAPI.unlockLocal(parentUID);
			}
		}

		//For the remaining files, delete them
		for(ListItem item : toDelete) {
			//Delete the file
			UUID fileUID = item.fileUID;
			try {
				hAPI.lockLocal(fileUID);
				hAPI.deleteFile(fileUID);
			} catch (FileNotFoundException e) {
				//If the file is already deleted, our job is technically done
			} finally {
				hAPI.unlockLocal(fileUID);
			}
		}

		return failed;
	}


	private static void deleteDirContents(UUID dirUID) throws ContentsNotFoundException, ConnectException {
		HybridAPI hAPI = HybridAPI.getInstance();
		try {
			//Get the contents of the directory
			List<DirItem> dirList = readDir(dirUID);

			//For each item in the directory...
			for(DirItem entry : dirList) {
				try {
					//If the file is a directory, delete its contents
					//Use HAPI to check instead of entry.isDir just in case
					boolean isDir = hAPI.getFileProps(entry.fileUID).isdir;
					if(isDir) deleteDirContents(entry.fileUID);

					//Delete the file
					hAPI.lockLocal(entry.fileUID);
					hAPI.deleteFile(entry.fileUID);
				} catch (FileNotFoundException e) {
					//If the file doesn't exist, our job is technically done
				} finally {
					hAPI.unlockLocal(entry.fileUID);
				}
			}

		} catch (FileNotFoundException e) {
			//If the directory doesn't exist, our job is technically done
		}
	}




	//TODO Cut this up into pieces (this is my last resort)
	public static boolean moveFiles(@NonNull List<ListItem> toMove, @NonNull UUID destinationUID, @Nullable UUID nextItem)
			throws FileNotFoundException, ContentsNotFoundException, ConnectException, NotDirectoryException, IOException {

		if(toMove.isEmpty()) {
			Log.w(TAG, "moveFiles was called with no files to move!");
			return false;
		}


		//If the destination is a link, we need the target dir or target parent
		UUID destinationDirUID = LinkCache.getInstance().getLinkDir(destinationUID);


		//We do not want to move links directly inside themselves or things will visually disappear. Exclude any.
		Iterator<ListItem> iterator = toMove.iterator();
		while (iterator.hasNext()) {
			UUID fileUID = iterator.next().fileUID;
			if(destinationUID.equals(fileUID) || destinationDirUID.equals(fileUID)) {
				Log.d(TAG, "Move failed, not allowed to move items inside themselves");
				iterator.remove();
			}
		}


		//Group each file by its parent directory so we know where to remove them from
		Map<UUID, List<UUID>> dirMap = mapToParent(toMove);


		//-----------------------------------------------------------------------------------------

		HybridAPI hAPI = HybridAPI.getInstance();

		//Add all the files in order to the destination directory
		try {
			hAPI.lockLocal(destinationDirUID);

			HFile destinationProps = hAPI.getFileProps(destinationDirUID);
			List<DirItem> dirList = readDir(destinationDirUID);


			//Find the position to insert the items into
			int insertPos = 0;
			if(nextItem == null)
				//NextItem should only ever be null if we dragged to the end of the dir
				insertPos = dirList.size();
			else {
				//We need to find the nextItem in the list (if it still exists). We will insert the moved file(s) before it
				for(int i = 0; i < dirList.size(); i++) {
					if(dirList.get(i).fileUID.equals(nextItem)) {
						insertPos = i;
						break;
					}
				}
			}


			//Convert the list items to DirItems
			List<DirItem> moveOrdering = toMove.stream()
					.map(item -> new DirItem(item.fileUID, item.isDir, item.isLink, item.getRawName()))
					.collect(Collectors.toList());

			//Insert the moved files at the correct position, making sure to reposition files already in the directory
			List<DirItem> newList = new ArrayList<>(dirList);
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
			List<String> newLines = newList.stream().map(DirItem::toString)
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
				List<DirItem> dirList = readDir(parentUID);

				//Remove all files in toMove that are from this dir
				Set<UUID> filesToRemoveSet = new HashSet<>(filesToRemove);
				dirList.removeIf(item -> filesToRemoveSet.contains(item.fileUID));

				//Write the list back to the directory
				List<String> newLines = dirList.stream().map(DirItem::toString).collect(Collectors.toList());
				byte[] newContent = String.join("\n", newLines).getBytes();
				hAPI.writeFile(parentUID, newContent, parentProps.checksum);
			} finally {
				hAPI.unlockLocal(parentUID);
			}
		}

		return true;
	}

	private static Map<UUID, List<UUID>> mapToParent(List<ListItem> toMap) {
		Map<UUID, List<UUID>> dirMap = new HashMap<>();
		for (ListItem itemToMove : toMap) {
			UUID parentUID = itemToMove.parentUID;
			UUID fileUID = itemToMove.fileUID;

			//If the item's parent is a link, we need the targeted item if dir, or its parent if not
			parentUID = LinkCache.getInstance().getLinkDir(parentUID);

			dirMap.putIfAbsent(parentUID, new ArrayList<>());
			dirMap.get(parentUID).add(fileUID);
		}

		return dirMap;
	}




	public static boolean copyFiles(@NonNull List<ListItem> toCopy, @NonNull UUID destinationUID, @Nullable UUID nextItem)
			throws FileNotFoundException, ContentsNotFoundException, ConnectException, IOException {
		if(toCopy.isEmpty()) {
			Log.w(TAG, "copyFiles was called with no files to copy!");
			return false;
		}

		//If the destination is a link, we need the target dir or target parent
		UUID destinationDirUID = LinkCache.getInstance().getLinkDir(destinationUID);

		//TODO Links can be moved inside themselves atm


		HybridAPI hAPI = HybridAPI.getInstance();

		//Since we're copying, we need to create a new file for each of the toCopy files
		List<DirItem> newFiles = new ArrayList<>();
		for(ListItem item : toCopy) {
			try {
				if(item.isDir) {
					//Make a new empty directory instead of copying directory contents
					UUID newDirUID = hAPI.createFile(hAPI.getCurrentAccount(), true, false);
					newFiles.add(new DirItem(newDirUID, item.isDir, item.isLink, "Copy of "+item.getPrettyName()));
				}
				else {
					UUID newFileUID = hAPI.copyFile(item.fileUID, hAPI.getCurrentAccount());
					newFiles.add(new DirItem(newFileUID, item.isDir, item.isLink, "Copy of "+item.getPrettyName()));
				}
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
			List<DirItem> dirList = readDir(destinationDirUID);


			//Find the position to insert the items into
			int insertPos = 0;
			if(nextItem == null)
				//NextItem should only ever be null if we dragged to the end of the dir
				insertPos = dirList.size();
			else {
				//We need to find the nextItem in the list (if it still exists). We will insert the moved file(s) before it
				for(int i = 0; i < dirList.size(); i++) {
					if(dirList.get(i).fileUID.equals(nextItem)) {
						insertPos = i;
						break;
					}
				}
			}


			//Insert the copied files at the correct position
			List<DirItem> newList = new ArrayList<>(dirList);
			newList.addAll(insertPos, newFiles);

			//If the lists are the same, this means nothing was actually moved, and we should skip the write
			//We should also skip the deletes after this because nothing was moved
			if(dirList.size() == newList.size() && dirList.equals(newList)) {
				Log.i(TAG, "When copying, no items were changed, so nothing is being written");
				return false;
			}


			//Write the list back to the directory
			List<String> newLines = newList.stream().map(DirItem::toString)
					.collect(Collectors.toList());
			byte[] newContent = String.join("\n", newLines).getBytes();
			hAPI.writeFile(destinationDirUID, newContent, destinationProps.checksum);
		}
		finally {
			hAPI.unlockLocal(destinationDirUID);
		}

		return true;
	}



	@Nullable
	private static UUID copyDirectory(@NonNull UUID dirUID) {
		return copyDirectoryRecursive(dirUID, new HashSet<>());
	}

	//Ayo this thing sucks. We can't copy files we cant reach, so we're kinda fucked.
	//I'm just not going to use this
	@Nullable
	private static UUID copyDirectoryRecursive(@NonNull UUID dirUID, @NonNull Set<UUID> visited) {
		if(!visited.add(dirUID)) return null;

		HybridAPI hAPI = HybridAPI.getInstance();
		try {
			UUID newDirUID = hAPI.copyFile(dirUID, hAPI.getCurrentAccount());

			List<DirItem> dirListToCopy = readDir(dirUID);

			List<DirItem> newList = new ArrayList<>();
			for(DirItem item : dirListToCopy) {
				if(item.isDir) {
					UUID newSubDirUID = copyDirectoryRecursive(item.fileUID, new HashSet<>(visited));
					if(newSubDirUID != null)
						newList.add(new DirItem(newSubDirUID, item.isDir, item.isLink, item.name));
				}
				else {
					try {
						UUID newFileUID = hAPI.copyFile(item.fileUID, hAPI.getCurrentAccount());
						newList.add(new DirItem(newFileUID, item.isDir, item.isLink, item.name));
					}
					catch (FileNotFoundException | ConnectException ignored) {}
				}
			}


			try {
				hAPI.lockLocal(newDirUID);
				HFile newDirProps = hAPI.getFileProps(newDirUID);

				//Write the list back to the directory
				List<String> newLines = newList.stream().map(DirItem::toString)
						.collect(Collectors.toList());
				byte[] newContent = String.join("\n", newLines).getBytes();
				hAPI.writeFile(newDirUID, newContent, newDirProps.checksum);
			} catch (FileNotFoundException | ConnectException e) {
				return null;
			} catch (IOException e) {
				throw new RuntimeException(e);
			} finally {
				hAPI.unlockLocal(newDirUID);
			}

			return newDirUID;
		} catch (FileNotFoundException | ContentsNotFoundException | ConnectException e) {
			return null;
		}
	}



	public static boolean export(@NonNull List<ListItem> toExport) {
		Context context = MyApplication.getAppContext();
		HybridAPI hAPI = HybridAPI.getInstance();

		if(toExport.isEmpty()) {
			Log.w(TAG, "Export was called with no files to export!");
			return false;
		}

		Uri exportDocUri = ExportStorageHandler.getStorageTreeUri(context);
		if(exportDocUri == null)
			throw new RuntimeException("Export document URI is null!");

		//Add all the files in order to the export directory
		for(int i = toExport.size()-1; i >= 0; i--) {
			ListItem item = toExport.get(i);

			//Skip directories and links
			if(item.isDir || item.isLink) {
				toExport.remove(i);
				continue;
			}

			//Create the file Uri to export, with an incremented filename if necessary
			Uri exportUri;
			int count = 0;
			//Fucking SAF replaces all spaces with underscores so we're just doing that here to avoid catastrophic failure
			//What a shitty fucking joke of an API
			String sanitizedName = SAFGoFuckYourself.sanitizeFilename(item.getRawName());
			do {
				String fileName = FilenameUtils.removeExtension(sanitizedName);
				String extension = FilenameUtils.getExtension(sanitizedName);
				String name = (count == 0) ? sanitizedName : fileName+" ("+count+")."+extension;
				count++;
				exportUri = SAFGoFuckYourself.makeDocUriFromDocUri(exportDocUri, name);
			} while(SAFGoFuckYourself.fileExists(context, exportUri));

			SAFGoFuckYourself.createFile(context, exportUri);


			//Actually export the file
			try {
				Pair<Uri, String> contentInfo = hAPI.getFileContent(item.fileUID);

				try (InputStream in = context.getContentResolver().openInputStream(contentInfo.first);
					 BufferedInputStream bin = new BufferedInputStream(in);

					 OutputStream out = context.getContentResolver().openOutputStream(exportUri);
					 BufferedOutputStream bout = new BufferedOutputStream(out)) {

					byte[] buffer = new byte[8192]; // Buffer size can be adjusted
					int bytesRead;
					while ((bytesRead = bin.read(buffer)) != -1) {
						bout.write(buffer, 0, bytesRead);
					}

					// Ensure that the data is written to the file
					bout.flush();

				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			} catch (ContentsNotFoundException | FileNotFoundException | ConnectException e) {
				toExport.remove(i);
				Looper.prepare();
				//TODO Maybe make different toasts for each error type. Except contents, that one means it's just broke.
				Toast.makeText(context, "A file was unable to be exported!", Toast.LENGTH_SHORT).show();
			}
		}



		//Finally, delete all files that were successfully exported
		List<ListItem> failedItems = deleteFiles(toExport);
		if(!failedItems.isEmpty()) {
			Looper.prepare();
			Toast.makeText(context, "Some items failed to be removed!", Toast.LENGTH_SHORT).show();
		}



		return true;
	}
}
