package aaa.sgordon.galleryfinal.gallery;

import androidx.annotation.NonNull;

import com.google.gson.JsonObject;

import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import aaa.sgordon.galleryfinal.repository.hybrid.HybridAPI;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridListeners;


//WARNING: This object should live as long as the Application is running. Keep in Activity ViewModel.
public class AttrCache {
	private final static String TAG = "Gal.AttrCache";
	private final HybridAPI hAPI;

	private final Map<UUID, JsonObject> attrCache;

	private final HybridListeners.FileChangeListener fileChangeListener;
	private final UpdateListeners updateListeners;


	public static AttrCache getInstance() {
		return SingletonHelper.INSTANCE;
	}
	private static class SingletonHelper {
		private static final AttrCache INSTANCE = new AttrCache();
	}
	private AttrCache() {
		this.hAPI = HybridAPI.getInstance();
		this.attrCache = new HashMap<>();
		this.updateListeners = new UpdateListeners();

		//Whenever any file we have cached is changed, update our data
		fileChangeListener = uuid -> {

			if(attrCache.containsKey(uuid)) {
				attrCache.remove(uuid);
				updateListeners.notifyDataChanged(uuid);
			}
		};
		hAPI.addListener(fileChangeListener);
	}


	public JsonObject getAttr(UUID fileUID) throws FileNotFoundException {
		//If we have the attributes cached, just use that
		if(attrCache.containsKey(fileUID))
			return attrCache.get(fileUID);

		//Grab the attributes from the repository and cache them
		JsonObject attr = hAPI.getFileProps(fileUID).userattr;
		attrCache.put(fileUID, attr);
		return attr;
	}



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
