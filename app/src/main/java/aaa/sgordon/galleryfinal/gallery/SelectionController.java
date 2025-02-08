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




	private boolean isDragSelecting = false;
	public boolean isDragSelecting() {
		return isDragSelecting;
	}
	private int startPos = -1;
	private int lastPos = -1;
	public void startDragSelecting(int startPos) {
		if(isDragSelecting) return;
		isDragSelecting = true;
		this.startPos = lastPos = startPos;
		selectItem( callbacks.getUUIDAtPos(startPos) );
	}
	public void dragSelect(int nextPos) {
		int from = Math.min(startPos, nextPos);
		int to = Math.max(startPos, nextPos);

		//Select range between 'from' and 'to'
		for (int i = from; i <= to; i++)
			selectItem(callbacks.getUUIDAtPos(i));

		// Deselect items outside the new range but within the last selected range
		int lastFrom = Math.min(startPos, lastPos);
		int lastTo = Math.max(startPos, lastPos);
		for (int i = lastFrom; i <= lastTo; i++) {
			if (i < from || i > to)
				deselectItem(callbacks.getUUIDAtPos(i));
		}

		lastPos = nextPos;
	}
	public void stopDragSelecting() {
		if(!isDragSelecting) return;
		isDragSelecting = false;
		startPos = lastPos = -1;
	}



	public interface SelectionCallbacks {
		void onSelectionChanged(UUID fileUID, boolean isSelected);
		void onNumSelectedChanged(int numSelected);

		UUID getUUIDAtPos(int pos);
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
