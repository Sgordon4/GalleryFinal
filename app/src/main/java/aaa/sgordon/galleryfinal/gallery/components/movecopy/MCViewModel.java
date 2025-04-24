package aaa.sgordon.galleryfinal.gallery.components.movecopy;

import androidx.annotation.NonNull;
import androidx.lifecycle.MediatorLiveData;
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
import aaa.sgordon.galleryfinal.gallery.touch.SelectionController;
import aaa.sgordon.galleryfinal.repository.gallery.FileExplorer;
import aaa.sgordon.galleryfinal.repository.gallery.caches.AttrCache;
import aaa.sgordon.galleryfinal.repository.gallery.caches.DirCache;
import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;

public class MCViewModel extends ViewModel {
	public static final String TAG = "Gal.MC.VM";
	public final ListItem startItem;
	public final boolean isMove;

	public UUID currDirUID;
	public UUID currParentUID;
	public Path currPathFromRoot;

	private final FileExplorer fileExplorer;
	public final MutableLiveData<List<ListItem>> fileList;

	public final SelectionController.SelectionRegistry selectionRegistry;
	public final FilterController.FilterRegistry filterRegistry;


	public MCViewModel(ListItem startItem, boolean isMove) {
		this.startItem = startItem;
		this.isMove = isMove;

		this.currDirUID = startItem.fileUID;
		this.currParentUID = startItem.parentUID;
		this.currPathFromRoot = startItem.pathFromRoot;

		this.fileExplorer = new FileExplorer();
		this.selectionRegistry = new SelectionController.SelectionRegistry();
		this.filterRegistry = new FilterController.FilterRegistry();

		this.fileList = new MutableLiveData<>();
		this.fileList.setValue(new ArrayList<>());


		MediatorLiveData<List<ListItem>> mediator = new MediatorLiveData<>();
		mediator.addSource(fileExplorer.list, fileList::postValue);
	}

	public void changeDirectories(UUID newDirUID) throws ContentsNotFoundException, FileNotFoundException, ConnectException {
		this.currDirUID = newDirUID;
		fileExplorer.traverseDir(newDirUID);
	}

	@Override
	protected void onCleared() {
		super.onCleared();
		fileExplorer.destroy();
	}


//=================================================================================================
//=================================================================================================

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