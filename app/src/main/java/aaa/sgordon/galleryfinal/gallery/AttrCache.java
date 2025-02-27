package aaa.sgordon.galleryfinal.gallery;

import androidx.annotation.NonNull;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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


	//Compile a list of all the tags used by any file
	public Set<String> compileTags(List<UUID> fileUIDs) {
		Set<String> compiled = new HashSet<>();
		for(UUID file : fileUIDs) {
			try {
				JsonObject attrs = getAttr(file);
				if(attrs == null) continue;
				JsonArray tags = attrs.getAsJsonArray("tags");
				if(tags == null) continue;

				for(JsonElement tag : tags)
					compiled.add(tag.getAsString());
			} catch (FileNotFoundException e) {
				//Skip the file if we can't find it
			}
		}
		return compiled;
	}



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
