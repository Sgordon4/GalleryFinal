package aaa.sgordon.galleryfinal.gallery;

import android.util.Pair;

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

import aaa.sgordon.galleryfinal.gallery.touch.SelectionController;
import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;

public class DirectoryViewModel extends ViewModel {
	private final static String TAG = "Gal.DirVM";
	private final UUID currDirUID;

	private final DirCache dirCache;
	private final AttrCache attrCache;
	private final DirCache.UpdateListener updateListener;

	private final SelectionController.SelectionRegistry selectionRegistry;
	private String filterQuery;
	private final List<String> filterTags;



	Thread queuedUpdateThread;
	public List<Pair<Path, String>> fullList;
	public MutableLiveData< List<Pair<Path, String>> > filteredList;


	public UUID getDirUID() {
		return currDirUID;
	}

	public DirCache getDirCache() {
		return dirCache;
	}
	public AttrCache getAttrCache() {
		return attrCache;
	}

	public String getFilterQuery() {
		return filterQuery;
	}
	public void setFilterQuery(String query) {
		filterQuery = query;

		Thread filter = new Thread(this::applyFiltering);
		filter.start();
	}
	public List<String> getFilterTags() {
		return filterTags;
	}

	public DirectoryViewModel(UUID currDirUID) {
		this.currDirUID = currDirUID;

		this.dirCache = DirCache.getInstance();
		this.attrCache = AttrCache.getInstance();

		this.filterQuery = "";
		this.filterTags = new ArrayList<>();

		this.fullList = new ArrayList<>();

		this.filteredList = new MutableLiveData<>();
		this.filteredList.setValue(new ArrayList<>());

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

					refreshList();
				});
				thread.start();
				queuedUpdateThread = thread;
			}
		};
		dirCache.addListener(updateListener, currDirUID);


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
		if(updateListener != null)
			dirCache.removeListener(updateListener);
	}

	private void refreshList() {
		try {
			List<Pair<Path, String>> newList = dirCache.getDirList(currDirUID);

			fullList = newList;
			applyFiltering();
		}
		catch (ContentsNotFoundException | FileNotFoundException | ConnectException e) {
			//TODO Actually handle the error. Dir should be on local, but jic
			throw new RuntimeException(e);
		}
	}


	//Take the fullList and filter out anything that doesn't match our filters (name and tags)
	private void applyFiltering() {
		List<Pair<Path, String>> filtered = fullList.stream().filter(pathStringPair -> {
			boolean keep = true;

			String fileName = pathStringPair.second;
			if(!fileName.toLowerCase().contains(filterQuery.toLowerCase()))
				keep = false;

			//If we're filtering for tags, make sure each item has all filtered tags
			if(keep && !filterQuery.isEmpty()) {
				Path path = pathStringPair.first;
				String UUIDString = path.getFileName().toString();
				if(UUIDString.equals("END"))
					UUIDString = path.getParent().getFileName().toString();
				UUID thisFileUID = UUID.fromString(UUIDString);

				try {
					System.out.println("Here");
					JsonObject attr = attrCache.getAttr(thisFileUID);
					System.out.println("There");

					//Check if the file has the tags we're filtering for
					//keep = false;
				} catch (FileNotFoundException e) {
					//Do nothing
				}
			}

			return keep;
		}).collect(Collectors.toList());
		filteredList.postValue(filtered);
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
