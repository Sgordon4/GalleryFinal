package aaa.sgordon.galleryfinal.repository.caches_old;

import android.util.Pair;

import androidx.annotation.NonNull;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.FileNotFoundException;
import java.net.ConnectException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import aaa.sgordon.galleryfinal.repository.hybrid.HybridAPI;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridListeners;
import aaa.sgordon.galleryfinal.repository.hybrid.types.HFile;


//WARNING: This object should live as long as the Application is running. Keep in Activity ViewModel.
public class AttrCacheOld {
	private final static String TAG = "Gal.AttrCache";
	private final HybridAPI hAPI;

	private final Map<UUID, Pair<String, JsonObject>> attrCache;

	private final HybridListeners.FileChangeListener fileChangeListener;
	private final UpdateListeners updateListeners;


	public static AttrCacheOld getInstance() {
		return SingletonHelper.INSTANCE;
	}
	private static class SingletonHelper {
		private static final AttrCacheOld INSTANCE = new AttrCacheOld();
	}
	private AttrCacheOld() {
		this.hAPI = HybridAPI.getInstance();
		this.attrCache = new HashMap<>();
		this.updateListeners = new UpdateListeners();

		//Whenever any file we have cached is changed, update our data
		fileChangeListener = uuid -> {
			if(attrCache.containsKey(uuid)) {
				try {
					//If the new hash matches the old, this file's attributes weren't updated
					String newHash = hAPI.getFileProps(uuid).attrhash;
					String oldHash = attrCache.get(uuid).first;
					if(newHash.equals(oldHash)) return;
				}
				catch (ConnectException e) {
					//If we can't reach the file, don't remove the cached attrs
					return;
				}
				catch (FileNotFoundException e) {
					//If we can't find the file, remove the cached attrs
				}

				attrCache.remove(uuid);
				updateListeners.notifyDataChanged(uuid);
			}
		};
		hAPI.addListener(fileChangeListener);
	}


	public JsonObject getAttr(UUID fileUID) throws FileNotFoundException, ConnectException {
		//If we have the attributes cached, just use that
		if(attrCache.containsKey(fileUID))
			return attrCache.get(fileUID).second;

		//Grab the attributes from the repository and cache them
		HFile props = hAPI.getFileProps(fileUID);
		String hash = props.attrhash;
		JsonObject attr = props.userattr;
		attrCache.put(fileUID, new Pair<>(hash, attr));

		return attr;
	}
	public String getAttrHash(UUID fileUID) throws FileNotFoundException, ConnectException {
		if(!attrCache.containsKey(fileUID))
			getAttr(fileUID);

		return attrCache.get(fileUID).first;
	}


	//Compile a list of all the tags used by any file in the provided list
	public Map<String, Set<UUID>> compileTags(List<UUID> fileUIDs) {
		Map<String, Set<UUID>> compiled = new HashMap<>();

		for(UUID file : fileUIDs) {
			try {
				JsonObject attrs = getAttr(file);
				if(attrs == null) continue;
				JsonArray tags = attrs.getAsJsonArray("tags");
				if(tags == null) continue;

				//Skip compiling tags for files that are hidden
				if(attrs.has("hidden") && attrs.get("hidden").getAsBoolean()) continue;

				for(JsonElement tag : tags) {
					compiled.putIfAbsent(tag.getAsString(), new HashSet<>());
					compiled.get(tag.getAsString()).add(file);
				}
			} catch (FileNotFoundException | ConnectException e) {
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
