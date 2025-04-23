package aaa.sgordon.galleryfinal.gallery;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

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
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import aaa.sgordon.galleryfinal.gallery.touch.SelectionController;
import aaa.sgordon.galleryfinal.repository.caches.AttrCache;
import aaa.sgordon.galleryfinal.repository.caches.DirCache;
import aaa.sgordon.galleryfinal.repository.caches.LinkCache;
import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;
import aaa.sgordon.galleryfinal.utilities.DirSampleData;

public class DirectoryViewModel extends ViewModel {
	private final static String TAG = "Gal.Dir.VM";
	public final ListItem listItem;

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


	public DirectoryViewModel(ListItem listItem) {
		this.listItem = listItem;

		this.dirCache = DirCache.getInstance();
		this.linkCache = LinkCache.getInstance();
		this.attrCache = AttrCache.getInstance();

		this.filterRegistry = new FilterController.FilterRegistry();
		this.selectionRegistry = new SelectionController.SelectionRegistry();

		this.fileList = new MutableLiveData<>();
		this.fileList.setValue(new ArrayList<>());
		this.fileTags = new MutableLiveData<>();
		this.fileTags.setValue(new HashMap<>());





		System.out.println("Creating viewmodel! FileUID='"+listItem.fileUID+"'");


		dirListener = uuid -> {
			//If this update isn't for us, ignore it
			if(!uuid.equals(listItem.fileUID))
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
		dirCache.addListener(dirListener, listItem.fileUID);

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
			//Exclude link ends (only consider their parent link), then map each item to UUID
			List<UUID> fileUIDs = fileList.getValue().stream()
					.filter(item -> !item.type.equals(ListItem.ListItemType.LINKEND))
					.map(item -> item.fileUID)
					.collect(Collectors.toList());

			//If none of our files changed, leave
			if(!fileUIDs.contains(uuid)) return;


			//Update the tags
			Map<String, Set<UUID>> newTags = attrCache.compileTags(fileUIDs);
			fileTags.postValue(newTags);

			try {
				//Grab the new attributes from the repository
				JsonObject newAttr = attrCache.getAttr(uuid);
				String newAttrHash = attrCache.getAttrHash(uuid);

				//For each file in the fileList...
				List<ListItem> updatedList = fileList.getValue();
				for(int i = 0; i < updatedList.size(); i++) {
					ListItem item = updatedList.get(i);

					//If the file has an attribute update...
					if(item.fileUID.equals(uuid)) {
						//Replace the list item with a new one, containing the updated attributes
						ListItem updated = new ListItem.Builder(item).build();
						updated.fileProps.userattr = newAttr;
						updated.fileProps.attrhash = newAttrHash;

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


		//Fetch the directory list and update our livedata
		Thread updateViaTraverse = new Thread(this::refreshList);
		updateViaTraverse.start();


		//Add some items to start to fill in the screen for testing with scrolling
		Thread importStart = new Thread(() -> DirSampleData.fakeImportFiles(listItem.fileUID, 50));
		//importStart.start();


		//Loop importing items for testing viewpager
		System.out.println("Done with viewmodel");
		scheduler = Executors.newSingleThreadScheduledExecutor();
		Runnable runnable = () -> {
			//'Import' to this directory
			DirSampleData.fakeImportFiles(listItem.fileUID, 1);
		};
		//scheduler.scheduleWithFixedDelay(runnable, 4000, 4000, TimeUnit.MILLISECONDS);
	}
	private final ScheduledExecutorService scheduler;
	@Override
	protected void onCleared() {
		super.onCleared();
		if(dirListener != null)
			dirCache.removeListener(dirListener);
		if(attrListener != null)
			attrCache.removeListener(attrListener);
	}


	boolean printed = false;
	private void refreshList() {
		try {
			//Grab the current list of all files in this directory from the system
			List<ListItem> newFileList = TraversalHelper.traverseDir(listItem.fileUID);

			/**/
			if(!printed) {
				System.out.println("NewFiles: ");
				for(ListItem item : newFileList) {
					//Print only the first 8 digits of the UUID
					Path printPath = Paths.get("");
					for(Path path : item.pathFromRoot) {
						printPath = printPath.resolve(path.getFileName().toString().substring(0, 8));
					}
					System.out.println(printPath+"   "+item.type+" "+item.name);
				}

				printed = true;
			}
			/**/

			fileList.postValue(newFileList);
		}
		catch (ContentsNotFoundException | FileNotFoundException | ConnectException e) {
			//TODO Actually handle the error. Dir should be on local, but jic
			throw new RuntimeException(e);
		}
	}



//=================================================================================================
//=================================================================================================

	public static class Factory implements ViewModelProvider.Factory {
		private final ListItem listItem;

		public Factory(ListItem listItem) {
			this.listItem = listItem;
		}

		@NonNull
		@Override
		public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
			if (modelClass.isAssignableFrom(DirectoryViewModel.class)) {
				return (T) new DirectoryViewModel(listItem);
			}
			throw new IllegalArgumentException("Unknown ViewModel class");
		}
	}
}
