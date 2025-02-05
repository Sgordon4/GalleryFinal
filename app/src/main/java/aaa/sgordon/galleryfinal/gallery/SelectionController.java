package aaa.sgordon.galleryfinal.gallery;

import androidx.annotation.NonNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class SelectionController {

	private boolean selecting;
	private final SelectionRegistry registry;
	private final SelectionCallbacks callbacks;

	public SelectionController(@NonNull SelectionRegistry registry, @NonNull SelectionCallbacks callbacks) {
		this.selecting = false;
		this.registry = registry;
		this.callbacks = callbacks;
	}


	public boolean isSelecting() {
		return selecting;
	}

	public void startSelecting() {
		if(isSelecting()) return;

		registry.clearSelection();
		selecting = true;
	}
	public void stopSelecting() {
		if(!isSelecting()) return;

		selecting = false;
		registry.clearSelection();
	}



	public int getNumSelected(){
		return isSelecting() ? registry.getNumSelected() : 0;
	}
	public boolean isSelected(UUID item) {
		return isSelecting() && registry.isSelected(item);
	}


	public void selectItem(UUID fileUID) {
		if(!isSelecting() || registry.isSelected(fileUID)) return;

		registry.selectItem(fileUID);

		callbacks.onSelectionChanged(fileUID, true);
	}
	public void deselectItem(UUID fileUID) {
		if(!isSelecting() || !registry.isSelected(fileUID)) return;

		registry.deselectItem(fileUID);

		callbacks.onSelectionChanged(fileUID, false);
	}
	public void toggleSelectItem(UUID item) {
		if(!isSelecting()) return;

		if(!isSelected(item))
			selectItem(item);
		else
			deselectItem(item);
	}


	public void selectAll(Set<UUID> toSelect) {
		if (!isSelecting()) return;

		//Remove any that are already selected
		Set<UUID> selected = registry.getSelectedList();
		toSelect.removeAll(selected);

		for(UUID item : toSelect)
			selectItem(item);
	}
	public void deselectAll() {
		if(!isSelecting()) return;

		Set<UUID> selected = registry.getSelectedList();
		for(UUID item : selected)
			deselectItem(item);
	}




	public interface SelectionCallbacks {
		void onSelectionChanged(UUID fileUID, boolean isSelected);

		void selectionStarted(int numSelected);
		void selectionStopped();
		void numberSelectedChanged(int numSelected);
	}

	public static class SelectionRegistry {
		private final Set<UUID> selected = new HashSet<>();

		public Set<UUID> getSelectedList() {
			return selected;
		}
		public int getNumSelected(){
			return selected.size();
		}

		public boolean isSelected(UUID fileUID) {
			return selected.contains(fileUID);
		}
		public boolean areSelected(List<UUID> fileUIDs) {
			return selected.size() == fileUIDs.size() && selected.containsAll(fileUIDs);
		}

		public void selectItem(UUID fileUID) {
			selected.add(fileUID);
		}
		public void deselectItem(UUID fileUID) {
			selected.remove(fileUID);
		}
		public void clearSelection() {
			selected.clear();
		}
	}
}
