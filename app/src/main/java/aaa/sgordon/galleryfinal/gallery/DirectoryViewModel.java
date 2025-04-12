package aaa.sgordon.galleryfinal.gallery;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.google.gson.JsonObject;

import org.apache.commons.io.FilenameUtils;

import java.io.FileNotFoundException;
import java.net.ConnectException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import aaa.sgordon.galleryfinal.gallery.touch.SelectionController;
import aaa.sgordon.galleryfinal.repository.caches.AttrCache;
import aaa.sgordon.galleryfinal.repository.caches.DirCache;
import aaa.sgordon.galleryfinal.repository.caches.LinkCache;
import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;
import aaa.sgordon.galleryfinal.utilities.DirSampleData;

public class DirectoryViewModel extends ViewModel {
	private final static String TAG = "Gal.DirVM";
	private final UUID currDirUID;

	private final DirCache dirCache;
	private final DirCache.UpdateListener dirListener;
	private final LinkCache linkCache;
	//private final LinkCache.UpdateListener linkListener;
	private final AttrCache attrCache;
	private final AttrCache.UpdateListener attrListener;

	private Thread queuedUpdateThread;

	private final FilterController.FilterRegistry filterRegistry;
	private final SelectionController.SelectionRegistry selectionRegistry;

	public final MutableLiveData< List<ListItem> > fileList;
	public final MutableLiveData< Map<String, Set<UUID>> > fileTags;

	public ListItem viewPagerCurrItem = null;


	public UUID getDirUID() {
		return currDirUID;
	}

	public DirCache getDirCache() {
		return dirCache;
	}
	public LinkCache getLinkCache() {
		return linkCache;
	}
	public AttrCache getAttrCache() {
		return attrCache;
	}


	public FilterController.FilterRegistry getFilterRegistry() {
		return filterRegistry;
	}

	public SelectionController.SelectionRegistry getSelectionRegistry() {
		return selectionRegistry;
	}







	public DirectoryViewModel(UUID currDirUID) {
		this.currDirUID = currDirUID;

		this.dirCache = DirCache.getInstance();
		this.linkCache = LinkCache.getInstance();
		this.attrCache = AttrCache.getInstance();

		this.filterRegistry = new FilterController.FilterRegistry();
		this.selectionRegistry = new SelectionController.SelectionRegistry();

		this.fileList = new MutableLiveData<>();
		this.fileList.setValue(new ArrayList<>());
		this.fileTags = new MutableLiveData<>();
		this.fileTags.setValue(new HashMap<>());





		System.out.println("Creating viewmodel! FileUID='"+currDirUID+"'");


		dirListener = uuid -> {
			//If this update isn't for us, ignore it
			if(!uuid.equals(currDirUID))
				return;

			//This setup only queues a list update if there is not already one queued
			//It will queue one if there is an in-progress update
			if(queuedUpdateThread == null) {
				//During a move operation (usually from reorder), the list will update after every directory change
				//This causes animation flickers, so we want to delay the visual list update just a touch
				Thread thread = new Thread(() -> {
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

		/*
		linkListener = uuid -> {
			//Don't even check if this update affects one of our files, just refresh things idc
			//90% chance it does anyway
			List<UUID> fileUIDs = Utilities.getUUIDsFromPaths(fileList.getValue());
			Map<String, Set<UUID>> newTags = attrCache.compileTags(fileUIDs);
			fileTags.postValue(newTags);
		};
		linkCache.addListener(linkListener);
		 */

		attrListener = uuid -> {
			List<ListItem> items = excludeLinkEnds(fileList.getValue());
			List<UUID> fileUIDs = getUUIDsFromItems(items);

			//If one of our files changed, update its attributes
			if(fileUIDs.contains(uuid)) {

				//Update the tags
				Map<String, Set<UUID>> newTags = attrCache.compileTags(fileUIDs);
				fileTags.postValue(newTags);


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
							updatedList.set(i, new ListItem.Builder(item).setAttr(newAttr).build());
						}
					}

					//Update the livedata
					fileList.postValue(updatedList);
				}
				catch (FileNotFoundException | ConnectException e) {
					//Skip updating the file if we can't find it
				}
			}
		};
		attrCache.addListener(attrListener);


		//Fetch the directory list and update our livedata
		Thread updateViaTraverse = new Thread(this::refreshList);
		updateViaTraverse.start();


		//Add some items to start to fill in the screen for testing with scrolling
		Thread importStart = new Thread(() -> DirSampleData.fakeImportFiles(currDirUID, 50));
		//importStart.start();
	}
	@Override
	protected void onCleared() {
		super.onCleared();
		if(dirListener != null)
			dirCache.removeListener(dirListener);
		if(attrListener != null)
			attrCache.removeListener(attrListener);
	}


	boolean printed = true;
	private void refreshList() {
		try {
			//Grab the current list of all files in this directory from the system
			List<ListItem> newFileList = TraversalHelper.traverseDir(currDirUID);

			/**/
			if(!printed) {
				System.out.println("NewFiles: ");
				for(ListItem item : newFileList) {
					//Print only the first 8 digits of the UUID
					Path printPath = Paths.get("");
					for(Path path : item.filePath) {
						printPath = printPath.resolve(path.getFileName().toString().substring(0, 8));
					}
					System.out.println(printPath+"   "+item.type+" "+item.name);
				}

				printed = true;
			}
			/**/


			newFileList = newFileList.stream()
					//Filter out anything that is trashed
					.filter(item -> !FilenameUtils.getExtension(item.name).startsWith("trashed_"))
					.collect(Collectors.toList());

			//Grab the UUIDs of all the files in the new list for use with tagging
			//Don't include link ends, we only consider their parents
			List<UUID> fileUIDs = getUUIDsFromItems( excludeLinkEnds(newFileList) );

			//Grab all tags for each fileUID
			//TODO Expand this to include a list of files per tag
			Map<String, Set<UUID>> newTags = attrCache.compileTags(fileUIDs);

			fileList.postValue(newFileList);
			fileTags.postValue(newTags);
		}
		catch (ContentsNotFoundException | FileNotFoundException | ConnectException e) {
			//TODO Actually handle the error. Dir should be on local, but jic
			throw new RuntimeException(e);
		}
	}


	private List<ListItem> excludeLinkEnds(List<ListItem> fileList) {
		return fileList.stream()
				.filter(item -> !LinkCache.isLinkEnd(item))
				.collect(Collectors.toList());
	}
	private List<UUID> getUUIDsFromItems(List<ListItem> fileList) {
		return fileList.stream().map(item -> item.fileUID).collect(Collectors.toList());
	}




//=================================================================================================
//=================================================================================================

	public static class Factory implements ViewModelProvider.Factory {
		private final UUID dirUID;
		public Factory(UUID dirUID) {
			this.dirUID = dirUID;
		}

		@NonNull
		@Override
		public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
			if (modelClass.isAssignableFrom(DirectoryViewModel.class)) {
				return (T) new DirectoryViewModel(dirUID);
			}
			throw new IllegalArgumentException("Unknown ViewModel class");
		}
	}
}
