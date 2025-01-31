package aaa.sgordon.galleryfinal.repository.hybrid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class HybridListeners {
	private final Map<FileChangeListener, List<UUID>> listenerMap = new HashMap<>();
	public interface FileChangeListener {
		void onDataChanged(UUID uuid);
	}


	public static HybridListeners getInstance() {
		return SingletonHelper.INSTANCE;
	}
	private static class SingletonHelper {
		private static final HybridListeners INSTANCE = new HybridListeners();
	}
	private HybridListeners() {

	}


	public void addListener(FileChangeListener listener, UUID... uuids) {
		listenerMap.put(listener, new ArrayList<>( Arrays.asList(uuids) ));
	}
	public void removeListener(FileChangeListener listener) {
		listenerMap.remove(listener);
	}

	public void notifyDataChanged(UUID uuid) {
		for (Map.Entry<FileChangeListener, List<UUID>> entry : listenerMap.entrySet()) {
			FileChangeListener listener = entry.getKey();
			List<UUID> associatedUuids = entry.getValue();

			if (associatedUuids.contains(uuid)) {
				listener.onDataChanged(uuid);
			}
		}
	}
}
