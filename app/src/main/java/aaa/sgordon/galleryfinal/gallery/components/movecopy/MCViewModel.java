package aaa.sgordon.galleryfinal.gallery.components.movecopy;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.google.gson.JsonObject;

import java.io.FileNotFoundException;
import java.net.ConnectException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import aaa.sgordon.galleryfinal.gallery.FilterController;
import aaa.sgordon.galleryfinal.gallery.ListItem;
import aaa.sgordon.galleryfinal.gallery.TraversalHelper;
import aaa.sgordon.galleryfinal.gallery.touch.SelectionController;
import aaa.sgordon.galleryfinal.repository.caches.AttrCache;
import aaa.sgordon.galleryfinal.repository.caches.DirCache;
import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;

public class MCViewModel extends ViewModel {
	public final ListItem startItem;
	public final boolean isMove;

	public UUID currDirUID;
	public UUID currParentUID;
	public Path currPathFromRoot;

	public final MutableLiveData<List<ListItem>> fileList;

	private final DirCache dirCache;
	private final DirCache.UpdateListener dirListener;
	private final AttrCache attrCache;
	private final AttrCache.UpdateListener attrListener;
	private Thread queuedUpdateThread;

	public final SelectionController.SelectionRegistry selectionRegistry;
	public final FilterController.FilterRegistry filterRegistry;

	public MCViewModel(ListItem startItem, boolean isMove) {
		this.startItem = startItem;
		this.isMove = isMove;

		this.currDirUID = startItem.fileUID;
		this.currParentUID = startItem.parentUID;
		this.currPathFromRoot = startItem.pathFromRoot;

		this.dirCache = DirCache.getInstance();
		this.attrCache = AttrCache.getInstance();

		this.selectionRegistry = new SelectionController.SelectionRegistry();
		this.filterRegistry = new FilterController.FilterRegistry();

		this.fileList = new MutableLiveData<>();
		this.fileList.setValue(new ArrayList<>());


		dirListener = uuid -> {
			//If this update isn't for us, ignore it
			if(!uuid.equals(currDirUID))
				return;

			//This setup only queues a list update if there is not already one queued
			//It will queue one if there is an in-progress update
			if(queuedUpdateThread == null) {
				Thread thread = new Thread(() -> {
					//During a move operation (usually from reorder), the list will update after every directory change
					//This causes animation flickers, so we want to delay the visual list update just a touch
					try { Thread.sleep(50); }
					catch (InterruptedException e) { throw new RuntimeException(e); }
					this.queuedUpdateThread = null;

					refreshList();
				});
				thread.start();
				queuedUpdateThread = thread;
			}
		};
		dirCache.addListener(dirListener, currDirUID);


		attrListener = uuid -> {
			//Exclude link ends (only consider their parent link), then map each item to UUID
			List<UUID> fileUIDs = fileList.getValue().stream()
					.filter(item -> !item.type.equals(ListItem.ListItemType.LINKEND))
					.map(item -> item.fileUID)
					.collect(Collectors.toList());

			//If none of our files changed, leave
			if(!fileUIDs.contains(uuid)) return;


			try {
				//Grab the new attributes from the repository
				JsonObject newAttr = attrCache.getAttr(uuid);

				//For each file in the fileList...
				List<ListItem> updatedList = fileList.getValue();
				for(int i = 0; i < updatedList.size(); i++) {
					ListItem item = updatedList.get(i);

					//If the file has an attribute update...
					if(item.fileUID.equals(uuid)) {
						//Replace the list item with a new one, containing the updated attributes
						ListItem updated = new ListItem.Builder(item).build();
						updated.fileProps.userattr = newAttr;
						updatedList.set(i, updated);
					}
				}

				//Update the livedata
				fileList.postValue(updatedList);
			}
			catch (FileNotFoundException | ConnectException e) {
				//Skip updating the file if we can't find it
			}
		};
		attrCache.addListener(attrListener);
	}

	public void refreshList() {
		try {
			//Grab the current list of all files in this directory from the system
			List<ListItem> newFileList = TraversalHelper.traverseDir(currDirUID);
			fileList.postValue(newFileList);
		}
		catch (ContentsNotFoundException | FileNotFoundException | ConnectException e) {
			//TODO Actually handle the error. Dir should be on local, but jic
			throw new RuntimeException(e);
		}
	}

	@Override
	protected void onCleared() {
		super.onCleared();
		if(dirListener != null)
			dirCache.removeListener(dirListener);
		if(attrListener != null)
			attrCache.removeListener(attrListener);
	}


	public FilterController.FilterRegistry getFilterRegistry() {
		return filterRegistry;
	}

	public AttrCache getAttrCache() {
		return attrCache;
	}

	public static class Factory implements ViewModelProvider.Factory {
		private final ListItem startItem;
		private final boolean isMove;
		public Factory(ListItem startItem, boolean isMove) {
			this.startItem = startItem;
			this.isMove = isMove;
		}

		@NonNull
		@Override
		public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
			if (modelClass.isAssignableFrom(MCViewModel.class)) {
				return (T) new MCViewModel(startItem, isMove);
			}
			throw new IllegalArgumentException("Unknown ViewModel class");
		}
	}
}