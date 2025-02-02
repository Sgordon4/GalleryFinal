package aaa.sgordon.galleryfinal.gallery;

import android.app.Application;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Pair;

import androidx.annotation.NonNull;
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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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



		//Add some items to start to fill in the screen for testing with scrolling
		Thread importStart = new Thread(() -> fakeImportFiles(currDirUID, 50));
		//importStart.start();


		Thread updateViaTraverse = new Thread(() -> {
			try {
				List<Pair<Path, String>> newList = traverse(currDirUID, new HashSet<>(), Paths.get(""));
				flatList.postValue(newList);
			} catch (ContentsNotFoundException | FileNotFoundException | ConnectException e) {
				//TODO Actually handle the error. Dir should be on local, but jic
				throw new RuntimeException(e);
			}
		});


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
		updateViaTraverse.start();



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
				fakeImportFiles(randomDirUID, 2);

				//Do it again in a few seconds
				handler.postDelayed(this, 3000);
			}
		};
		handler.postDelayed(runnable, 3000);
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

		//For each file in the directory...
		List<Pair<UUID, String>> contents = readDir(dirUID);
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
		//If we have the directory list cached, just return that
		if(directoryCache.containsKey(dirUID))
			return directoryCache.get(dirUID);

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




	public void fakeImportFiles(@NonNull UUID destinationDirUID, int numImported) {
		HybridAPI hAPI = HybridAPI.getInstance();

		try {
			hAPI.lockLocal(destinationDirUID);

			Pair<Uri, String> dirContent = hAPI.getFileContent(destinationDirUID);
			Uri dirUri = dirContent.first;
			String dirChecksum = dirContent.second;


			//Read the directory into a list of UUID::FileName pairs
			ArrayList<Pair<UUID, String>> dirList = new ArrayList<>();
			try (InputStream inputStream = new URL(dirUri.toString()).openStream();
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


			//Add our new 'imported' files to the beginning
			for(int i = 0; i < numImported; i++) {
				UUID fileUID = UUID.randomUUID();
				String fileName = "File number "+dirList.size();
				dirList.add(0, new Pair<>(fileUID, fileName));
			}


			//Write the list back to the directory
			List<String> newLines = dirList.stream().map(pair -> pair.first+" "+pair.second)
					.collect(Collectors.toList());
			byte[] newContent = String.join("\n", newLines).getBytes();
			hAPI.writeFile(destinationDirUID, newContent, dirChecksum);

		} catch (ContentsNotFoundException | FileNotFoundException | ConnectException e) {
			throw new RuntimeException(e);
		} finally {
			hAPI.unlockLocal(destinationDirUID);
		}
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



	/*
	//Only make sure to only traverse links to directories, not directories themselves
	private List<Pair<UUID, String>> traverse(@NonNull UUID nextUUID, @NonNull Set<UUID> visited) {
		//System.out.println("Visited identity: "+visited.hashCode());
		//System.out.println("Size is "+visited.size());

		//If we've already touched this directory, skip it so we don't loop forever
		if(visited.contains(nextUUID))
			return new ArrayList<>();
		visited.add(nextUUID);

		//Get the list of files in this directory
		List<Pair<UUID, String>> thisList = readDir(nextUUID);
		//And make an empty list for results
		List<Pair<UUID, String>> retList = new ArrayList<>();


		//For each file in the directory
		for (Pair<UUID, String> pair : thisList) {
			retList.add(pair);

			//If this item is a link to a directory, traverse it
			if(sampleData.isLinkToDir(pair.first)) {
				Set<UUID> shallowCopy = new HashSet<>(visited);
				retList.addAll(traverse(pair.first, shallowCopy));
			}
		}

		return retList;
	}
	 */
}
