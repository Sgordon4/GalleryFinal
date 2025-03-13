package aaa.sgordon.galleryfinal.repository.caches;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Pair;

import androidx.annotation.NonNull;

import java.io.FileNotFoundException;
import java.net.ConnectException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import aaa.sgordon.galleryfinal.utilities.DirSampleData;
import aaa.sgordon.galleryfinal.utilities.DirUtilities;
import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridAPI;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridListeners;

//WARNING: This object should live as long as the Application is running. Keep in Activity ViewModel.
public class DirCache {
	private final static String TAG = "Gal.DirCache";
	private final HybridAPI hAPI;
	private final HybridListeners.FileChangeListener fileChangeListener;
	private final UpdateListeners updateListeners;

	private final Map<UUID, List<Pair<UUID, String>>> dirContents;
	//Per directory, holds subordinate links and link targets the dir depends on. This is for use with the listener to refresh the dir on subordinate item updates
	public final Map<UUID, Set<UUID>> subLinks;


	public static DirCache getInstance() {
		return SingletonHelper.INSTANCE;
	}
	private static class SingletonHelper {
		private static final DirCache INSTANCE = new DirCache();
	}
	private DirCache() {
		this.hAPI = HybridAPI.getInstance();

		this.dirContents = new HashMap<>();
		this.subLinks = new HashMap<>();

		this.updateListeners = new UpdateListeners();


		//Whenever any file we have cached is changed, update our data
		fileChangeListener = uuid -> {
			//If we have this directory cached...
			if(dirContents.containsKey(uuid)) {
				//Remove the cached contents
				dirContents.remove(uuid);
				//Since we need to re-cache anyway, remove the list of dependencies
				subLinks.remove(uuid);

				updateListeners.notifyDataChanged(uuid);
			}

			//For each cached directory, look at each link it depends on...
			for(Map.Entry<UUID, Set<UUID>> entry : subLinks.entrySet()) {
				UUID dirUID = entry.getKey();
				Set<UUID> dependencySet = entry.getValue();

				//If the dir relies on this file, notify listeners
				if(dependencySet.contains(uuid)) {
					if(dirContents.containsKey(dirUID))
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
				int randomIndex = random.nextInt(dirContents.size());
				UUID randomDirUID = (UUID) dirContents.keySet().toArray()[randomIndex];

				//randomDirUID = currDirUID;

				//Import to that directory
				DirSampleData.fakeImportFiles(randomDirUID, 2);

				//Do it again in a few seconds
				handler.postDelayed(this, 3000);
			}
		};
		//handler.postDelayed(runnable, 3000);
	}


	public List<Pair<UUID, String>> getDirContents(UUID dirUID) throws ContentsNotFoundException, FileNotFoundException, ConnectException {
		//If we have the directory contents cached, just return that
		if(dirContents.containsKey(dirUID))
			return dirContents.get(dirUID);

		List<Pair<UUID, String>> dirList = DirUtilities.readDir(dirUID);
		dirContents.put(dirUID, dirList);
		return dirList;
	}


	//---------------------------------------------------------------------------------------------

	public void addListener(@NonNull UpdateListener listener, @NonNull UUID dirUID) {
		updateListeners.addListener(listener, dirUID);
	}
	public void removeListener(@NonNull UpdateListener listener) {
		updateListeners.removeListener(listener);
	}

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
