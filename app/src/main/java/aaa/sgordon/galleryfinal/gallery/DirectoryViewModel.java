package aaa.sgordon.galleryfinal.gallery;

import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import java.io.FileNotFoundException;
import java.net.ConnectException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import aaa.sgordon.galleryfinal.gallery.touch.SelectionController;
import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;
import aaa.sgordon.galleryfinal.utilities.Utilities;

public class DirectoryViewModel extends ViewModel {
	private final static String TAG = "Gal.DirVM";
	private final UUID currDirUID;

	private final DirCache dirCache;
	private final DirCache.UpdateListener dirListener;
	private final AttrCache attrCache;
	private final AttrCache.UpdateListener attrListener;

	private Thread queuedUpdateThread;

	private final FilterController.FilterRegistry filterRegistry;
	private final SelectionController.SelectionRegistry selectionRegistry;

	public final MutableLiveData< List<Pair<Path, String>> > fileList;
	public final MutableLiveData< Set<String> > fileTags;


	public UUID getDirUID() {
		return currDirUID;
	}

	public DirCache getDirCache() {
		return dirCache;
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
		this.attrCache = AttrCache.getInstance();

		this.filterRegistry = new FilterController.FilterRegistry();
		this.selectionRegistry = new SelectionController.SelectionRegistry();

		this.fileList = new MutableLiveData<>();
		this.fileList.setValue(new ArrayList<>());
		this.fileTags = new MutableLiveData<>();
		this.fileTags.setValue(new HashSet<>());





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
					try { Thread.sleep(30); }
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
			//Don't even check if this update affects one of our files, just refresh things idc
			//90% chance it does anyway
			List<UUID> fileUIDs = Utilities.getUUIDsFromPaths(fileList.getValue());
			Set<String> newTags = attrCache.compileTags(fileUIDs);
			fileTags.postValue(newTags);
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


	private void refreshList() {
		try {
			//Grab the current list of all files in this directory from the system
			List<Pair<Path, String>> newFileList = dirCache.getDirList(currDirUID);

			//Grab the UUIDs of all the files in the new list
			List<UUID> fileUIDs = Utilities.getUUIDsFromPaths(newFileList);

			//Grab all tags for each file
			//TODO Expand this to include a list of files per tag
			Set<String> newTags = attrCache.compileTags(fileUIDs);

			fileList.postValue(newFileList);
			fileTags.postValue(newTags);
		}
		catch (ContentsNotFoundException | FileNotFoundException | ConnectException e) {
			//TODO Actually handle the error. Dir should be on local, but jic
			throw new RuntimeException(e);
		}
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
