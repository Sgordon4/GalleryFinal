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
	private DirRVAdapter adapter;

	public SelectionController(@NonNull SelectionCallbacks callbacks) {
		this.selecting = false;
		this.registry = new SelectionRegistry();
		this.callbacks = callbacks;
	}
	public void setAdapter(DirRVAdapter adapter) {
		this.adapter = adapter;
	}


	public boolean isSelecting() {
		return selecting;
	}

	public void startSelecting() {
		if(isSelecting()) return;
		System.out.println("Starting selection!");

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

		System.out.println("Selecting item!");
		registry.selectItem(fileUID);

		//Notify the adapter that the item selection status has been changed so it can change its appearance
		for(int i = 0; i < adapter.list.size(); i++) {
			String UUIDString = adapter.list.get(i).first.getFileName().toString();
			if(UUIDString.equals("END"))
				UUIDString = adapter.list.get(i).first.getParent().getFileName().toString();
			UUID itemUID = UUID.fromString(UUIDString);

			if(fileUID.equals(itemUID))
				adapter.notifyItemChanged(i);
		}
	}
	public void deselectItem(UUID fileUID) {
		if(!isSelecting() || !registry.isSelected(fileUID)) return;

		System.out.println("Deselecting item!");
		registry.deselectItem(fileUID);

		//Notify the adapter that the item selection status has been changed so it can change its appearance
		for(int i = 0; i < adapter.list.size(); i++) {
			String UUIDString = adapter.list.get(i).first.getFileName().toString();
			if(UUIDString.equals("END"))
				UUIDString = adapter.list.get(i).first.getParent().getFileName().toString();
			UUID itemUID = UUID.fromString(UUIDString);

			if(fileUID.equals(itemUID))
				adapter.notifyItemChanged(i);
		}
	}
	public void toggleSelectItem(UUID item) {
		if(!isSelecting()) return;

		if(!isSelected(item))
			selectItem(item);
		else
			deselectItem(item);
	}


	public void deselectAll() {
		if(!isSelecting()) return;
		registry.clearSelection();
		//TODO Notify adapter
	}




	public interface SelectionCallbacks {
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
