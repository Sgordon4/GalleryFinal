package aaa.sgordon.galleryfinal.gallery;

import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.FileNotFoundException;
import java.net.ConnectException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import aaa.sgordon.galleryfinal.gallery.touch.SelectionController;
import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;

public class DirectoryViewModel extends ViewModel {
	private final static String TAG = "Gal.DirVM";
	private final UUID currDirUID;

	private final DirCache dirCache;
	private final DirCache.UpdateListener dirListener;
	private final AttrCache attrCache;
	private final AttrCache.UpdateListener attrListener;

	private Thread queuedUpdateThread;

	//Query is the current string in the searchView, activeQuery is the one that was last submitted
	public final MutableLiveData<String> query;
	public final MutableLiveData<String> activeQuery;

	public final MutableLiveData< Set<String> > fullTags;
	public final MutableLiveData< Set<String> > activeTags;

	public MutableLiveData< List<Pair<Path, String>> > fullList;
	public final MutableLiveData< List<Pair<Path, String>> > filteredList;
	public final MutableLiveData< Set<String> > filteredTags;

	private final SelectionController.SelectionRegistry selectionRegistry;



	public UUID getDirUID() {
		return currDirUID;
	}

	public DirCache getDirCache() {
		return dirCache;
	}
	public AttrCache getAttrCache() {
		return attrCache;
	}


	public void onQueryChanged(String newQuery) {
		this.query.postValue(newQuery);

		Thread filter = new Thread(() -> {
			Set<String> filtered = filterTags(newQuery, fullTags.getValue());
			filteredTags.postValue(filtered);
		});
		filter.start();
	}


	public void onActiveQueryChanged(String newActiveQuery) {
		Thread filter = new Thread(() -> {
			List<Pair<Path, String>> filtered = filterList(newActiveQuery, activeTags.getValue(), fullList.getValue());
			filteredList.postValue(filtered);
		});
		filter.start();
	}

	public void onActiveTagsChanged(Set<String> newActiveTags) {
		Thread filter = new Thread(() -> {
			List<Pair<Path, String>> filtered = filterList(activeQuery.getValue(), newActiveTags, fullList.getValue());
			filteredList.postValue(filtered);
		});
		filter.start();
	}




	public DirectoryViewModel(UUID currDirUID) {
		this.currDirUID = currDirUID;

		this.dirCache = DirCache.getInstance();
		this.attrCache = AttrCache.getInstance();

		this.query = new MutableLiveData<>();
		this.query.setValue("");

		this.activeQuery = new MutableLiveData<>();
		this.activeQuery.setValue("");
		this.activeTags = new MutableLiveData<>();
		this.activeTags.setValue(new HashSet<>());

		this.fullTags = new MutableLiveData<>();
		this.fullTags.setValue(new HashSet<>());
		this.filteredTags = new MutableLiveData<>();
		this.filteredTags.setValue(new HashSet<>());

		this.fullList = new MutableLiveData<>();
		this.fullList.setValue(new ArrayList<>());
		this.filteredList = new MutableLiveData<>();
		this.filteredList.setValue(new ArrayList<>());

		this.selectionRegistry = new SelectionController.SelectionRegistry();


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
			Set<String> newTags = compileTags(fullList.getValue());
			fullTags.postValue(newTags);

			Set<String> filtered = filterTags(query.getValue(), newTags);
			filteredTags.postValue(filtered);
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
			List<Pair<Path, String>> newList = dirCache.getDirList(currDirUID);
			fullList.postValue(newList);

			List<Pair<Path, String>> fList = filterList(activeQuery.getValue(), activeTags.getValue(), newList);
			filteredList.postValue(fList);


			Set<String> newTags = compileTags(newList);
			fullTags.postValue(newTags);

			Set<String> fTags = filterTags(query.getValue(), newTags);
			filteredTags.postValue(fTags);
		}
		catch (ContentsNotFoundException | FileNotFoundException | ConnectException e) {
			//TODO Actually handle the error. Dir should be on local, but jic
			throw new RuntimeException(e);
		}
	}


	//Take the list and filter out anything that doesn't match our filters (name and tags)
	private List<Pair<Path, String>> filterList(String filterQuery, Set<String> filterTags, List<Pair<Path, String>> list) {
		return list.stream().filter(pathStringPair -> {
			boolean keep = false;

			//Make sure the fileName contains the query string
			String fileName = pathStringPair.second;
			if(fileName.toLowerCase().contains(filterQuery.toLowerCase()))
				keep = true;

			//If we're filtering for tags, make sure each item has all filtered tags
			if(keep && !filterTags.isEmpty()) {
				//Get the UUID of the file from the path
				Path path = pathStringPair.first;
				String UUIDString = path.getFileName().toString();
				if(UUIDString.equals("END"))
					return false;	//Exclude ends, since we can't reorder
				//	UUIDString = path.getParent().getFileName().toString();
				UUID thisFileUID = UUID.fromString(UUIDString);

				try {
					//Get the tags for the file. Since we have tags, if they have no tags filter them out
					JsonObject attrs = attrCache.getAttr(thisFileUID);
					if(attrs == null) return false;
					JsonArray fileTags = attrs.getAsJsonArray("tags");
					if(fileTags == null)  return false;

					//Check if any of the tags we're searching for are contained in the file's tags
					keep = false;
					for(JsonElement tag : fileTags) {
						if(filterTags.contains(tag.getAsString())) {
							keep = true;
							break;
						}
					}
				} catch (FileNotFoundException e) {
					keep = false;
				}
			}

			return keep;
		}).collect(Collectors.toList());
	}

	private Set<String> filterTags(String filter, Set<String> tags) {
		return tags.stream()
				.filter(tag -> tag.contains(filter))
				.collect(Collectors.toSet());
	}

	private Set<String> compileTags(List<Pair<Path, String>> newList) {
		Set<String> compiled = new HashSet<>();

		//Compile a list of all the tags used by any file
		for(Pair<Path, String> file : newList) {
			String UUIDString = file.first.getFileName().toString();
			if(UUIDString.equals("END"))	//Don't consider ends, we already considered their parent
				continue;
			UUID thisFileUID = UUID.fromString(UUIDString);

			try {
				JsonObject attrs = attrCache.getAttr(thisFileUID);
				if(attrs == null) continue;
				JsonArray tags = attrs.getAsJsonArray("tags");
				if(tags == null) continue;

				for(JsonElement tag : tags)
					compiled.add(tag.getAsString());
			} catch (FileNotFoundException e) {
				//Do nothing
			}
		}

		//Remove any active tags that have vanished from the list of tags
		Set<String> active = activeTags.getValue();
		active.retainAll(compiled);
		activeTags.postValue(active);

		return compiled;
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
