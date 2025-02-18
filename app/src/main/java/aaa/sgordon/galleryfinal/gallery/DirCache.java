package aaa.sgordon.galleryfinal.gallery;

import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
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
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridAPI;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridListeners;
import aaa.sgordon.galleryfinal.repository.hybrid.types.HFile;


//WARNING: This object should live as long as the Application is running. Keep in Activity ViewModel.
public class DirCache {
	private final static String TAG = "Gal.FileCache";
	private final HybridAPI hAPI;

	private final Map<UUID, List<Pair<UUID, String>>> directoryCache;
	private final Map<UUID, UUID> dirLinkCache;
	private final Map<UUID, Set<UUID>> dependencyCache;

	//In our current implementation, files can't change their nature (directory/link).
	//Therefore, we can append only and not worry about concurrency or conflicting values with these.
	private final Set<UUID> isDirCache;
	private final Set<UUID> isLinkCache;

	private final HybridListeners.FileChangeListener fileChangeListener;
	private final UpdateListeners updateListeners;

	//TODO Make listeners for this


	public static DirCache getInstance() {
		return SingletonHelper.INSTANCE;
	}
	private static class SingletonHelper {
		private static final DirCache INSTANCE = new DirCache();
	}
	private DirCache() {
		this.hAPI = HybridAPI.getInstance();

		this.directoryCache = new HashMap<>();
		this.dirLinkCache = new HashMap<>();
		this.dependencyCache = new HashMap<>();

		this.isDirCache = new HashSet<>();
		this.isLinkCache = new HashSet<>();

		this.updateListeners = new UpdateListeners();


		//Whenever any file we have cached is changed, update our data
		fileChangeListener = uuid -> {
			//Notify any listener for this directory and remove the cache entry
			if(directoryCache.containsKey(uuid)) {
				directoryCache.remove(uuid);
				dependencyCache.remove(uuid);
				updateListeners.notifyDataChanged(uuid);
			}
			
			//Notify any listeners whose directory contains the updated directory/link
			for(Map.Entry<UUID, Set<UUID>> entry : dependencyCache.entrySet()) {
				UUID dirUID = entry.getKey();
				Set<UUID> dependencies = entry.getValue();

				if(dependencies.contains(uuid)) {
					if(directoryCache.containsKey(dirUID))
						updateListeners.notifyDataChanged(dirUID);
				}
			}
		};
		hAPI.addListener(fileChangeListener);



		//Loop importing items for testing notifications
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

	public boolean isDir(UUID fileUID) {
		return isDirCache.contains(fileUID);
	}
	public boolean isLink(UUID fileUID) {
		return isLinkCache.contains(fileUID);
	}

	public void addListener(@NonNull UpdateListener listener, @NonNull UUID dirUID) {
		updateListeners.addListener(listener, dirUID);
	}
	public void removeListener(@NonNull UpdateListener listener) {
		updateListeners.removeListener(listener);
	}



	public List<Pair<Path, String>> getDirList(UUID dirUID) throws ContentsNotFoundException, FileNotFoundException, ConnectException {
		return traverse(dirUID, new HashSet<>(), Paths.get(dirUID.toString()));
	}
	private List<Pair<Path, String>> traverse(UUID dirUID, Set<UUID> visited, Path currPath)
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

			//Add it to the current directory's list of files
			Path thisFilePath = currPath.resolve(entry.first.toString());
			files.add(new Pair<>(thisFilePath, entry.second));


			try {
				HFile fileProps = hAPI.getFileProps(fileUID);
				if(fileProps.isdir) isDirCache.add(fileUID);
				if(fileProps.islink) isLinkCache.add(fileUID);

				//If this isn't a link to a directory, we don't care
				if(!(fileProps.isdir && fileProps.islink))
					continue;

				//If this is a link, follow it until a directory is found (or until the link ends)
				Set<UUID> localVisited = new HashSet<>(visited);
				while( fileProps.islink ) {
					if (!localVisited.add(fileUID))
						break; // Prevent cycles

					//Update any dependency lists for parent directories
					for(int i = 0; i < currPath.getNameCount(); i++) {
						UUID pathItem = UUID.fromString(currPath.getName(i).toString());
						dependencyCache.putIfAbsent(pathItem, new HashSet<>());
						dependencyCache.get(pathItem).add(fileUID);
					}

					//Follow the link
					fileUID = readDirLink(fileUID);
					fileProps = hAPI.getFileProps(fileUID);
					if(fileProps.isdir) isDirCache.add(fileUID);
					if(fileProps.islink) isLinkCache.add(fileUID);
				}


				//If we reached a directory and this isn't a broken link, traverse it
				if(fileProps.isdir && !fileProps.islink) {
					if (!localVisited.add(fileUID))
						continue; // Prevent cycles

					//Update any dependency lists for parent directories
					for(int i = 0; i < currPath.getNameCount(); i++) {
						UUID pathItem = UUID.fromString(currPath.getName(i).toString());
						dependencyCache.putIfAbsent(pathItem, new HashSet<>());
						dependencyCache.get(pathItem).add(fileUID);
					}

					files.addAll(traverse(fileUID, localVisited, thisFilePath));

					//Ad a bookend for the link
					Path linkEnd = thisFilePath.resolve("END");
					//files.add(new Pair<>(linkEnd, entry.second+" END"));
					files.add(new Pair<>(linkEnd, "END"));
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
		//If we have the directory contents cached, just return that
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


	//Only use with directory links, not normal links
	private UUID readDirLink(UUID linkUID) throws ContentsNotFoundException, FileNotFoundException, ConnectException {
		//If we have the link destination directory cached, just return that
		if(dirLinkCache.containsKey(linkUID))
			return dirLinkCache.get(linkUID);

		UUID linkDestination = DirUtilities.readDirLink(linkUID);
		dirLinkCache.put(linkUID, linkDestination);
		return linkDestination;
	}



	//UUID could be a dir, or it could be a link to a dir
	//Thanks Sophia for the naming suggestion
	@Nullable
	public UUID resolveDirUID(UUID bartholomew) throws FileNotFoundException, NotDirectoryException, ConnectException {
		HFile fileProps = hAPI.getFileProps(bartholomew);
		if(!fileProps.isdir) throw new NotDirectoryException(bartholomew.toString());

		try {
			while(fileProps.islink) {
				bartholomew = readDirLink(bartholomew);
				fileProps = hAPI.getFileProps(bartholomew);
			}
		} catch (ContentsNotFoundException | FileNotFoundException e) {
			//If we can't follow the link, assume it's broken
			return null;
		}

		return bartholomew;
	}


	/*
	@NonNull
	public UUID getDirFromPath(@NonNull Path path) throws FileNotFoundException, NotDirectoryException {

		//Thanks Sophia for the naming suggestion
		UUID bartholomew = UUID.fromString(path.getFileName().toString());

		//If this is a link UUID, get the directory it points to
		while(dirLinkCache.containsKey(bartholomew))
			bartholomew = dirLinkCache.get(bartholomew);
		assert bartholomew != null;

		HFile dirProps = hAPI.getFileProps(bartholomew);
		if(!dirProps.isdir) throw new NotDirectoryException(bartholomew.toString());

		return bartholomew;
	}
	 */



	private static class UpdateListeners {
		private final Map<UpdateListener, UUID> listeners = new HashMap<>();

		public void addListener(@NonNull UpdateListener listener, @NonNull UUID dirUID) {
			listeners.put(listener, dirUID);
		}
		public void removeListener(@NonNull UpdateListener listener) {
			listeners.remove(listener);
		}

		public void notifyDataChanged(@NonNull UUID uuid) {
			for(Map.Entry<UpdateListener, UUID> entry : listeners.entrySet()) {
				UpdateListener listener = entry.getKey();
				UUID dirUID = entry.getValue();

				if(dirUID.equals(uuid))
					listener.onDirContentsChanged(uuid);
			}
		}
	}
	public interface UpdateListener {
		void onDirContentsChanged(UUID uuid);
	}
}
