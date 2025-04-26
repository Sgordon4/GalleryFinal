package aaa.sgordon.galleryfinal.gallery.components.movecopy;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import java.io.FileNotFoundException;
import java.net.ConnectException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import aaa.sgordon.galleryfinal.gallery.FilterController;
import aaa.sgordon.galleryfinal.repository.gallery.ListItem;
import aaa.sgordon.galleryfinal.gallery.touch.SelectionController;
import aaa.sgordon.galleryfinal.repository.gallery.FileExplorer;
import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;

public class MCViewModel extends ViewModel {
	public static final String TAG = "Gal.MC.VM";
	public final ListItem startItem;
	public final boolean isMove;

	public UUID currDirUID;
	public UUID currParentUID;
	public Path currPathFromRoot;

	private final FileExplorer fileExplorer;

	public final SelectionController.SelectionRegistry selectionRegistry;
	public final FilterController.FilterRegistry filterRegistry;


	public List<ListItem> getFileList() {
		return fileExplorer.list.getValue();
	}
	public MutableLiveData< List<ListItem> > getFileListLiveData() {
		return fileExplorer.list;
	}
	public void postFileList(List<ListItem> list) {
		fileExplorer.list.postValue(list);
	}


	public MCViewModel(ListItem startItem, boolean isMove) {
		this.startItem = startItem;
		this.isMove = isMove;

		this.currDirUID = startItem.fileUID;
		this.currParentUID = startItem.parentUID;
		this.currPathFromRoot = startItem.pathFromRoot;

		this.fileExplorer = new FileExplorer();
		this.selectionRegistry = new SelectionController.SelectionRegistry();
		this.filterRegistry = new FilterController.FilterRegistry();


		//Fetch the directory list and update our livedata
		Thread updateViaTraverse = new Thread(() -> {
			try {
				fileExplorer.traverseDir(currDirUID);
			}
			catch (ContentsNotFoundException | FileNotFoundException | ConnectException e) {
				throw new RuntimeException(e);
			}
		});
		updateViaTraverse.start();
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