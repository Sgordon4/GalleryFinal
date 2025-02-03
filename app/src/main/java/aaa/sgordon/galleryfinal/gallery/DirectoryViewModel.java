package aaa.sgordon.galleryfinal.gallery;

import android.app.Application;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.URL;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridAPI;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridListeners;
import aaa.sgordon.galleryfinal.repository.hybrid.types.HFile;

public class DirectoryViewModel extends AndroidViewModel {
	private final static String TAG = "Gal.DirVM";
	private final HybridAPI hAPI;
	private final UUID currDirUID;

	private final HybridListeners.FileChangeListener fileChangeListener;
	private final Map<UUID, List<Pair<UUID, String>>> directoryCache = new HashMap<>();
	private final Map<UUID, UUID> linkCache = new HashMap<>();

	MutableLiveData< List<Pair<Path, String>> > flatList;



	public DirectoryViewModel(@NonNull Application application, @NonNull UUID currDirUID) {
		super(application);
		this.currDirUID = currDirUID;
		this.flatList = new MutableLiveData<>();
		flatList.setValue(new ArrayList<>());

		hAPI = HybridAPI.getInstance();


		//Whenever a directory in our listing is updated, update our data
		fileChangeListener = uuid -> {
			//If the cache doesn't contain this file's UUID, we don't need to update anything
			if(!directoryCache.containsKey(uuid))
				return;

			//If it is in the cache, remove it and update the list
			directoryCache.remove(uuid);
			try {
				List<Pair<Path, String>> newList = traverse(currDirUID, new HashSet<>(), Paths.get(""));
				flatList.postValue(newList);
			} catch (ContentsNotFoundException | FileNotFoundException | ConnectException e) {
				//TODO Actually handle the error. Dir should be on local, but jic
				throw new RuntimeException(e);
			}
		};
		hAPI.addListener(fileChangeListener);


		//Fetch the directory list and update our livedata
		Thread updateViaTraverse = new Thread(() -> {
			try {
				List<Pair<Path, String>> newList = traverse(currDirUID, new HashSet<>(), Paths.get(""));
				flatList.postValue(newList);
			} catch (ContentsNotFoundException | FileNotFoundException | ConnectException e) {
				//TODO Actually handle the error. Dir should be on local, but jic
				throw new RuntimeException(e);
			}
		});
		updateViaTraverse.start();


		//Add some items to start to fill in the screen for testing with scrolling
		Thread importStart = new Thread(() -> DirSampleData.fakeImportFiles(currDirUID, 50));
		//importStart.start();


		//Loop importing items for testing adapter notifications
		HandlerThread handlerThread = new HandlerThread("BackgroundThread");
		handlerThread.start();
		Looper looper = handlerThread.getLooper();
		Handler handler = new Handler(looper);
		Runnable runnable = new Runnable() {
			public void run() {
				//Get a random fileUID from our list
				Random random = new Random();
				int randomIndex = random.nextInt(directoryCache.size());
				UUID randomDirUID = (UUID) directoryCache.keySet().toArray()[randomIndex];

				//randomDirUID = currDirUID;

				//Import to that directory
				DirSampleData.fakeImportFiles(randomDirUID, 2);

				//Do it again in a few seconds
				handler.postDelayed(this, 3000);
			}
		};
		//handler.postDelayed(runnable, 3000);
	}
	@Override
	protected void onCleared() {
		super.onCleared();
		if(fileChangeListener != null)
			hAPI.removeListener(fileChangeListener);
	}



	public List<Pair<Path, String>> traverse(UUID dirUID, Set<UUID> visited, Path currPath)
			throws ContentsNotFoundException, FileNotFoundException, ConnectException {
		List<Pair<Path, String>> files = new ArrayList<>();

		List<Pair<UUID, String>> contents;
		//If we have the directory list cached, just use that
		if(directoryCache.containsKey(dirUID))
			contents = directoryCache.get(dirUID);
		else
			contents = readDir(dirUID);

		//For each file in the directory...
		for (Pair<UUID, String> entry : contents) {
			UUID fileUID = entry.first;

			Path thisFilePath = currPath.resolve(entry.first.toString());
			files.add(new Pair<>(thisFilePath, entry.second));


			try {
				HFile fileProps = hAPI.getFileProps(fileUID);

				//If this isn't a link to a directory, we don't care
				if(!(fileProps.isdir && fileProps.islink))
					continue;

				//Follow links until a directory is found
				Set<UUID> localVisited = new HashSet<>(visited);
				while( fileProps.isdir && fileProps.islink ) {
					if (!localVisited.add(fileUID))
						break; // Prevent cycles

					fileUID = readLink(fileUID, fileProps.filesize);
					fileProps = hAPI.getFileProps(fileUID);
				}

				//If we reached a directory and this isn't a broken link, traverse it
				if(fileProps.isdir && !fileProps.islink) {
					if (!localVisited.add(fileUID))
						continue; // Prevent cycles

					files.addAll(traverse(fileUID, localVisited, thisFilePath));

					//Ad a bookend for the link
					Path linkEnd = thisFilePath.resolve("END");
					files.add(new Pair<>(linkEnd, entry.second+" END"));
				}
			}
			catch (FileNotFoundException | ConnectException e) {
				//If the file isn't found or we just can't reach it, skip it
				continue;
			}
			catch (ContentsNotFoundException e) {
				//If we can't find the link's contents, this is an issue, but skip it
				continue;
			}
		}

		return files;
	}




	private List<Pair<UUID, String>> readDir(@NonNull UUID dirUID) throws ContentsNotFoundException, FileNotFoundException, ConnectException {
		//Otherwise we gotta get it from the file itself
		Uri uri = hAPI.getFileContent(dirUID).first;

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

		directoryCache.put(dirUID, dirList);
		return dirList;
	}


	private UUID readLink(UUID linkUID, int fileSize) throws ContentsNotFoundException, FileNotFoundException, ConnectException {
		//If we have the link destination cached, just return that
		if(linkCache.containsKey(linkUID))
			return linkCache.get(linkUID);

		Uri uri = hAPI.getFileContent(linkUID).first;

		try (InputStream inputStream = new URL(uri.toString()).openStream()) {
			byte[] buffer = new byte[fileSize];
			inputStream.read(buffer);

			UUID linkDestination = UUID.fromString(new String(buffer));
			linkCache.put(linkUID, linkDestination);
			return linkDestination;
		}
		catch (IOException e) { throw new RuntimeException(e); }
	}



	//TODO Cut this up into pieces (this is my last resort)
	public boolean moveFiles(@Nullable Path destination, @Nullable Path nextItem, @NonNull List<Pair<Path, String>> toMove) throws FileNotFoundException, NotDirectoryException, ContentsNotFoundException, ConnectException {
		System.out.println("Moving files!");
		if(toMove.isEmpty()) {
			Log.w(TAG, "moveFiles was called with an empty toMove list!");
			return false;
		}

		toMove = new ArrayList<>(toMove);	//Make sure we can modify the list. If this was created with Arrays.asList it's immutable
		UUID destinationUID = (destination != null) ? getDirFromPath(destination) : currDirUID;

		//Group each file by its parent directory so we know where to remove them from
		Map<UUID, List<UUID>> fileOrigins = new HashMap<>();
		Iterator<Pair<Path, String>> iterator = toMove.iterator();
		while (iterator.hasNext()) {
			Pair<Path, String> itemToMove = iterator.next();
			UUID parentUID = getDirFromPath(itemToMove.first.getParent());
			UUID fileUID = UUID.fromString(itemToMove.first.getFileName().toString());

			if(destination != null && destination.toString().contains(fileUID.toString())) {
				System.out.println("Not allowed to move links inside themselves");
				iterator.remove();
				continue;
			}

			if(!fileOrigins.containsKey(parentUID))
				fileOrigins.put(parentUID, new ArrayList<>());
			fileOrigins.get(parentUID).add(fileUID);
		}
		fileOrigins.remove(destinationUID);


		if(toMove.isEmpty()) {
			System.out.println("All files being moved were links being moved inside themselves. Skipping move.");
			return false;
		}



		try {
			hAPI.lockLocal(destinationUID);

			HFile destinationProps = hAPI.getFileProps(destinationUID);
			List<Pair<UUID, String>> dirList = readDir(destinationUID);

			int insertPos = 0;
			//NextItem is only ever null if we dragged to the end of the Root dir
			if(nextItem == null || nextItem.getFileName().toString().equals("END"))
				insertPos = dirList.size();
			else {
				//We need to find the nextItem in the list (if it still exists). We will insert the moved file(s) before it
				for(int i = 0; i < dirList.size(); i++) {
					if(dirList.get(i).first.toString().equals(nextItem.getFileName().toString())) {
						insertPos = i;
						break;
					}
				}
			}

			System.out.println("Changing "+ Arrays.toString(dirList.toArray()));

			//Convert the list types to match
			List<Pair<UUID, String>> moveList = toMove.stream().map(pair ->
					new Pair<>(UUID.fromString(pair.first.getFileName().toString()), pair.second))
					.collect(Collectors.toList());

			//Insert the moved files at the correct position, making sure to reposition files already in the directory
			List<Pair<UUID, String>> newList = new ArrayList<>(dirList);
			newList.add(insertPos, null);
			newList.removeAll(moveList);
			int markedIndex = newList.indexOf(null);
			newList.remove(markedIndex);
			newList.addAll(markedIndex, moveList);

			System.out.println("To       "+ Arrays.toString(newList.toArray()));

			//If the lists are the same, this means nothing was actually moved, and we should skip the write
			//We should also skip the deletes after this because nothing was moved
			if(dirList.size() == newList.size() && dirList.equals(newList)) {
				System.out.println("No items were moved, nothing is being written");
				return false;
			}


			//Write the list back to the directory
			List<String> newLines = newList.stream().map(pair -> pair.first+" "+pair.second)
					.collect(Collectors.toList());
			byte[] newContent = String.join("\n", newLines).getBytes();
			hAPI.writeFile(destinationUID, newContent, destinationProps.checksum);

		}
		finally {
			hAPI.unlockLocal(destinationUID);
		}



		//Remove all the toMove files from their original directories. We've already excluded any that started in destination.
		for(UUID parentUID : fileOrigins.keySet()) {
			List<UUID> filesToRemove = fileOrigins.get(parentUID);
			try {
				hAPI.lockLocal(parentUID);

				HFile parentProps = hAPI.getFileProps(parentUID);
				List<Pair<UUID, String>> dirList = readDir(parentUID);

				//Remove all files in toMove that are from this dir
				Set<UUID> filesToRemoveSet = new HashSet<>(filesToRemove);
				dirList.removeIf(item -> filesToRemoveSet.contains(item.first));

				//Write the list back to the directory
				List<String> newLines = dirList.stream().map(pair -> pair.first+" "+pair.second)
						.collect(Collectors.toList());
				byte[] newContent = String.join("\n", newLines).getBytes();
				hAPI.writeFile(parentUID, newContent, parentProps.checksum);
			} finally {
				hAPI.unlockLocal(parentUID);
			}
		}

		return true;
	}

	private UUID getDirFromPath(Path path) throws FileNotFoundException, NotDirectoryException {
		if(path == null)
			return currDirUID;

		//Thanks Sophia for the naming suggestion
		UUID bartholomew = UUID.fromString(path.getFileName().toString());

		//If this is a link UUID, get the directory it points to
		while(linkCache.containsKey(bartholomew))
			bartholomew = linkCache.get(bartholomew);
		assert bartholomew != null;

		HFile dirProps = hAPI.getFileProps(bartholomew);
		if(!dirProps.isdir) throw new NotDirectoryException(bartholomew.toString());

		return bartholomew;
	}




//=================================================================================================
//=================================================================================================

	public static class DirectoryViewModelFactory implements ViewModelProvider.Factory {
		private final Application application;
		private final UUID dirUID;
		public DirectoryViewModelFactory(Application application, UUID dirUID) {
			this.application = application;
			this.dirUID = dirUID;
		}

		@NonNull
		@Override
		public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
			if (modelClass.isAssignableFrom(DirectoryViewModel.class)) {
				DirectoryViewModel viewModel = new DirectoryViewModel(application, dirUID);
				return (T) viewModel;
			}
			throw new IllegalArgumentException("Unknown ViewModel class");
		}
	}
}
