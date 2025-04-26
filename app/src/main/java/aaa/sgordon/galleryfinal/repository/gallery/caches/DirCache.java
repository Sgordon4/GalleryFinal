package aaa.sgordon.galleryfinal.repository.gallery.caches;

import androidx.annotation.NonNull;

import java.io.FileNotFoundException;
import java.net.ConnectException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import aaa.sgordon.galleryfinal.repository.gallery.DirItem;
import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridAPI;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridListeners;
import aaa.sgordon.galleryfinal.repository.hybrid.types.HFile;
import aaa.sgordon.galleryfinal.utilities.DirUtilities;

//WARNING: This object should live as long as the Application is running. Keep in Activity ViewModel.
public class DirCache {
	private final static String TAG = "Gal.DirCache";
	private final HybridAPI hAPI;
	private final HybridListeners.FileChangeListener fileChangeListener;
	private final UpdateListeners updateListeners;

	private final Map<UUID, List<DirItem>> dirContents;

	//Since, in our current implementation, files cannot change their nature (isDir/isLink), this works well
	private final Map<UUID, Boolean> isDir;


	@NonNull
	public static DirCache getInstance() {
		return SingletonHelper.INSTANCE;
	}
	private static class SingletonHelper {
		private static final DirCache INSTANCE = new DirCache();
	}
	private DirCache() {
		this.hAPI = HybridAPI.getInstance();
		this.updateListeners = new UpdateListeners();

		this.dirContents = new HashMap<>();
		this.isDir = new HashMap<>();


		//Whenever any file we have cached is changed, update our data
		fileChangeListener = uuid -> {
			//If we have this directory cached...
			if(dirContents.containsKey(uuid)) {
				dirContents.remove(uuid);
				updateListeners.notifyDataChanged(uuid);
			}
		};
		hAPI.addListener(fileChangeListener);
	}



	public boolean isDir(UUID fileUID) throws FileNotFoundException, ConnectException {
		if(isDir.containsKey(fileUID))
			return isDir.get(fileUID);

		HFile fileProps = hAPI.getFileProps(fileUID);
		isDir.put(fileUID, fileProps.isdir);

		return fileProps.isdir;
	}
	public List<DirItem> getDirContents(UUID dirUID) throws ContentsNotFoundException, FileNotFoundException, ConnectException {
		if(!isDir(dirUID))
			throw new IllegalArgumentException("File is not a directory!");

		//If we have the directory contents cached, just return that
		if(dirContents.containsKey(dirUID))
			return dirContents.get(dirUID);

		List<DirItem> dirList = DirUtilities.readDir(dirUID);
		dirContents.put(dirUID, dirList);

		return dirList;
	}


	//---------------------------------------------------------------------------------------------

	public void addListener(@NonNull UpdateListener listener) {
		updateListeners.addListener(listener);
	}
	public void removeListener(@NonNull UpdateListener listener) {
		updateListeners.removeListener(listener);
	}

	private static class UpdateListeners {
		private final Set<UpdateListener> listeners = new HashSet<>();

		public void addListener(@NonNull UpdateListener listener) {
			listeners.add(listener);
		}
		public void removeListener(@NonNull UpdateListener listener) {
			listeners.remove(listener);
		}

		public void notifyDataChanged(@NonNull UUID uuid) {
			for(UpdateListener listener : listeners)
				listener.onDirContentsChanged(uuid);
		}
	}
	public interface UpdateListener {
		void onDirContentsChanged(UUID uuid);
	}
}
