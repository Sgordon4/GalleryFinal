package aaa.sgordon.galleryfinal.repository.hybrid;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class HybridListeners {
	private final Set<FileChangeListener> listeners = new HashSet<>();
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


	public void addListener(FileChangeListener listener) {
		listeners.add(listener);
	}
	public void removeListener(FileChangeListener listener) {
		listeners.remove(listener);
	}

	public void notifyDataChanged(UUID uuid) {
		for(FileChangeListener listener : listeners)
			listener.onDataChanged(uuid);
	}
}
