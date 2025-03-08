package aaa.sgordon.galleryfinal.gallery;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.FileNotFoundException;
import java.net.ConnectException;
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
import aaa.sgordon.galleryfinal.utilities.LinkUtilities;


//WARNING: This object should live as long as the Application is running. Keep in Activity ViewModel.
public class DirCache {
	private final static String TAG = "Gal.DirCache";
	private final HybridAPI hAPI;

	private final Map<UUID, List<Pair<UUID, String>>> cDirContents;		//Holds directory contents
	private final Map<UUID, LinkUtilities.LinkTarget> cLinkTarget;		//Holds link targets

	//Per directory, holds links and link targets. This is for use with the listener to refresh the dir on item change
	private final Map<UUID, Set<UUID>> cDirChildTargets;


	//In our current implementation, files can't change their nature (directory/link).
	//Therefore, we can append only and not worry about concurrency or conflicting values with these.
	private final Set<UUID> isDirCache;
	private final Set<UUID> isLinkCache;

	private final HybridListeners.FileChangeListener fileChangeListener;
	private final UpdateListeners updateListeners;


	public static DirCache getInstance() {
		return SingletonHelper.INSTANCE;
	}
	private static class SingletonHelper {
		private static final DirCache INSTANCE = new DirCache();
	}
	private DirCache() {
		this.hAPI = HybridAPI.getInstance();

		this.cDirContents = new HashMap<>();
		this.cLinkTarget = new HashMap<>();
		this.cDirChildTargets = new HashMap<>();

		this.isDirCache = new HashSet<>();
		this.isLinkCache = new HashSet<>();

		this.updateListeners = new UpdateListeners();


		//Whenever any file we have cached is changed, update our data
		fileChangeListener = uuid -> {
			//If we have this directory cached...
			if(cDirContents.containsKey(uuid)) {
				//Remove the cached contents
				cDirContents.remove(uuid);
				//Since we need to re-cache anyway, remove the list of dependencies
				cDirChildTargets.remove(uuid);

				updateListeners.notifyDataChanged(uuid);
			}

			//For each cached directory, look at each link it depends on...
			for(Map.Entry<UUID, Set<UUID>> entry : cDirChildTargets.entrySet()) {
				UUID dirUID = entry.getKey();
				Set<UUID> dependencies = entry.getValue();

				//If the dir relies on this file, notify listeners
				if(dependencies.contains(uuid)) {
					if(cDirContents.containsKey(dirUID))
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
				int randomIndex = random.nextInt(cDirContents.size());
				UUID randomDirUID = (UUID) cDirContents.keySet().toArray()[randomIndex];

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



	//Recursively read directory contents, drilling down only through links to directories
	public List<Pair<Path, String>> getDirList(UUID dirUID) throws ContentsNotFoundException, FileNotFoundException, ConnectException {
		return traverse(dirUID, new HashSet<>(), Paths.get(dirUID.toString()));
	}
	private List<Pair<Path, String>> traverse(UUID dirUID, Set<UUID> visited, Path currPath)
			throws ContentsNotFoundException, FileNotFoundException, ConnectException {

		List<Pair<UUID, String>> contents = readDir(dirUID);

		//For each file in the directory...
		List<Pair<Path, String>> files = new ArrayList<>();
		for (Pair<UUID, String> entry : contents) {
			UUID fileUID = entry.first;

			//Add it to the current directory's list of files
			Path thisFilePath = currPath.resolve(entry.first.toString());
			files.add(new Pair<>(thisFilePath, entry.second));


			try {
				HFile fileProps = hAPI.getFileProps(fileUID);
				if (fileProps.islink) isLinkCache.add(fileUID);
				else if (fileProps.isdir) isDirCache.add(fileUID);

				//If this isn't a link, we don't care
				if (!fileProps.islink)
					continue;

				//If this is a link, follow it until a non-link is found (or until the link breaks)
				Set<UUID> localVisited = new HashSet<>(visited);
				while (fileProps.islink) {
					if (!localVisited.add(fileUID))
						break; // Prevent cycles

					//Follow the link
					LinkUtilities.LinkTarget target = LinkUtilities.readLink(fileUID);
					cLinkTarget.put(fileUID, target);

					//If this link is external, we don't care about it
					if(target instanceof LinkUtilities.ExternalTarget)
						continue;



					fileProps = hAPI.getFileProps(fileUID);
					if(fileProps.isdir) isDirCache.add(fileUID);
					if(fileProps.islink) isLinkCache.add(fileUID);
				}
			}


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
						cDirChildTargets.putIfAbsent(pathItem, new HashSet<>());
						cDirChildTargets.get(pathItem).add(fileUID);
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
						cDirChildTargets.putIfAbsent(pathItem, new HashSet<>());
						cDirChildTargets.get(pathItem).add(fileUID);
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
		if(cDirContents.containsKey(dirUID))
			return cDirContents.get(dirUID);

		List<Pair<UUID, String>> dirList = DirUtilities.readDir(dirUID);
		cDirContents.put(dirUID, dirList);
		return dirList;
	}


	//Only use with directory links, not normal links
	private UUID readDirLink(UUID linkUID) throws ContentsNotFoundException, FileNotFoundException, ConnectException {
		//If we have the link destination directory cached, just return that
		if(cLinkTarget.containsKey(linkUID))
			return cLinkTarget.get(linkUID);

		UUID linkDestination = DirUtilities.readDirLink(linkUID);
		cLinkTarget.put(linkUID, linkDestination);
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
