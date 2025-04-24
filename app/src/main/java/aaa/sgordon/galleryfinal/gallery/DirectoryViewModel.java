package aaa.sgordon.galleryfinal.gallery;

import androidx.annotation.NonNull;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import java.io.FileNotFoundException;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import aaa.sgordon.galleryfinal.gallery.touch.SelectionController;
import aaa.sgordon.galleryfinal.repository.gallery.FileExplorer;
import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;
import aaa.sgordon.galleryfinal.utilities.DirSampleData;

public class DirectoryViewModel extends ViewModel {
	private final static String TAG = "Gal.Dir.VM";
	private final FileExplorer fileExplorer;
	private final FilterController.FilterRegistry filterRegistry;
	private final SelectionController.SelectionRegistry selectionRegistry;

	public final ListItem listItem;
	public final MutableLiveData< List<ListItem> > fileList;
	public final MutableLiveData< Map<String, Set<UUID>> > fileTags;


	public FilterController.FilterRegistry getFilterRegistry() {
		return filterRegistry;
	}
	public SelectionController.SelectionRegistry getSelectionRegistry() {
		return selectionRegistry;
	}


	public DirectoryViewModel(ListItem listItem) {
		this.listItem = listItem;

		this.filterRegistry = new FilterController.FilterRegistry();
		this.selectionRegistry = new SelectionController.SelectionRegistry();

		this.fileExplorer = new FileExplorer();
		this.fileList = new MutableLiveData<>();
		this.fileList.setValue(new ArrayList<>());

		this.fileTags = new MutableLiveData<>();
		this.fileTags.setValue(new HashMap<>());

		System.out.println("Creating viewmodel! FileUID='"+listItem.fileUID+"'");


		MediatorLiveData<List<ListItem>> mediator = new MediatorLiveData<>();
		mediator.addSource(fileExplorer.list, fileList::postValue);

		//Fetch the directory list and update our livedata
		Thread updateViaTraverse = new Thread(() -> {
			try {
				fileExplorer.traverseDir(listItem.fileUID);
			}
			catch (ContentsNotFoundException | FileNotFoundException | ConnectException e) {
				throw new RuntimeException(e);
			}
		});
		updateViaTraverse.start();


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
		fileExplorer.destroy();
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
