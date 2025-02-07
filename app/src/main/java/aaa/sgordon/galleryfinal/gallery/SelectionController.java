package aaa.sgordon.galleryfinal.gallery;

import androidx.annotation.NonNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class SelectionController {
	private final SelectionRegistry registry;
	private final SelectionCallbacks callbacks;

	public SelectionController(@NonNull SelectionRegistry registry, @NonNull SelectionCallbacks callbacks) {
		this.registry = registry;
		this.callbacks = callbacks;
	}


	public boolean isSelecting() {
		return registry.isSelecting();
	}

	public void startSelecting() {
		if(isSelecting()) return;

		registry.clearSelection();
		registry.setSelecting(true);
	}
	public void stopSelecting() {
		if(!isSelecting()) return;

		deselectAll();
		registry.setSelecting(false);
	}



	public int getNumSelected(){
		return isSelecting() ? registry.getNumSelected() : 0;
	}
	public boolean isSelected(UUID item) {
		return isSelecting() && registry.isSelected(item);
	}
	public Set<UUID> getSelectedList() {
		return isSelecting() ? registry.getSelectedList() : new HashSet<>();
	}


	public void selectItem(UUID fileUID) {
		if(!isSelecting() || registry.isSelected(fileUID)) return;

		registry.selectItem(fileUID);

		callbacks.onSelectionChanged(fileUID, true);
		callbacks.onNumSelectedChanged(registry.getNumSelected());
	}
	public void deselectItem(UUID fileUID) {
		if(!isSelecting() || !registry.isSelected(fileUID)) return;

		registry.deselectItem(fileUID);

		callbacks.onSelectionChanged(fileUID, false);
		callbacks.onNumSelectedChanged(registry.getNumSelected());
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

		Set<UUID> selected = new HashSet<>(registry.getSelectedList());
		for(UUID item : selected)
			deselectItem(item);
		registry.clearSelection();
	}








	public interface SelectionCallbacks {
		void onSelectionChanged(UUID fileUID, boolean isSelected);
		void onNumSelectedChanged(int numSelected);
	}

	public static class SelectionRegistry {
		private boolean selecting;
		private final Set<UUID> selected = new HashSet<>();

		public boolean isSelecting() {
			return selecting;
		}
		public void setSelecting(boolean selecting) {
			this.selecting = selecting;
		}

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
