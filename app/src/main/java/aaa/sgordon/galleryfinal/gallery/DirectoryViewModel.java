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
import java.util.List;
import java.util.UUID;

import aaa.sgordon.galleryfinal.gallery.touch.SelectionController;
import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;

public class DirectoryViewModel extends ViewModel {
	private final static String TAG = "Gal.DirVM";
	private final UUID currDirUID;

	private final DirCache dirCache;
	private final DirCache.UpdateListener updateListener;

	private final SelectionController.SelectionRegistry selectionRegistry;
	private String filterQuery = "";



	Thread queuedUpdateThread;
	public MutableLiveData< List<Pair<Path, String>> > flatList;


	public UUID getDirUID() {
		return currDirUID;
	}

	public DirCache getDirCache() {
		return dirCache;
	}

	public String getFilterQuery() {
		return filterQuery;
	}
	public void setFilterQuery(String query) {
		filterQuery = query;
	}

	public DirectoryViewModel(UUID currDirUID) {
		this.currDirUID = currDirUID;

		this.dirCache = DirCache.getInstance();

		this.flatList = new MutableLiveData<>();
		this.flatList.setValue(new ArrayList<>());

		this.selectionRegistry = new SelectionController.SelectionRegistry();


		System.out.println("Creating viewmodel! FileUID='"+currDirUID+"'");


		updateListener = uuid -> {
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

					update();
				});
				thread.start();
				queuedUpdateThread = thread;
			}
		};
		dirCache.addListener(updateListener, currDirUID);


		//Fetch the directory list and update our livedata
		Thread updateViaTraverse = new Thread(this::update);
		updateViaTraverse.start();


		//Add some items to start to fill in the screen for testing with scrolling
		Thread importStart = new Thread(() -> DirSampleData.fakeImportFiles(currDirUID, 50));
		//importStart.start();
	}
	@Override
	protected void onCleared() {
		super.onCleared();
		if(updateListener != null)
			dirCache.removeListener(updateListener);
	}

	private void update() {
		try {
			List<Pair<Path, String>> newList = dirCache.getDirList(currDirUID);
			flatList.postValue(newList);
		}
		catch (ContentsNotFoundException | FileNotFoundException | ConnectException e) {
			//TODO Actually handle the error. Dir should be on local, but jic
			throw new RuntimeException(e);
		}
	}

	public SelectionController.SelectionRegistry getSelectionRegistry() {
		return selectionRegistry;
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
