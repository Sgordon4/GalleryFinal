package aaa.sgordon.galleryfinal.gallery.components.trash;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import aaa.sgordon.galleryfinal.databinding.DirTrashBinding;
import aaa.sgordon.galleryfinal.gallery.DirFragment;
import aaa.sgordon.galleryfinal.gallery.DirRVAdapter;
import aaa.sgordon.galleryfinal.gallery.ListItem;
import aaa.sgordon.galleryfinal.gallery.touch.SelectionController;
import aaa.sgordon.galleryfinal.utilities.DirUtilities;

public class TrashFragment extends Fragment {
	private DirTrashBinding binding;
	private DirFragment dirFragment;
	private TrashFullscreen.TrashViewModel viewModel;

	private MaterialToolbar toolbar;
	private MaterialToolbar selectionToolbar;
	private RecyclerView recyclerView;
	private ViewGroup bottomBar;
	private Button deleteButton;
	private Button restoreButton;

	private DirRVAdapter adapter;
	private SelectionController selectionController;


	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		dirFragment = (DirFragment) requireParentFragment();

		viewModel = new ViewModelProvider(this,
				new TrashFullscreen.TrashViewModel.Factory(dirFragment.dirViewModel.getDirUID()))
				.get(TrashFullscreen.TrashViewModel.class);
	}

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		binding = DirTrashBinding.inflate(inflater, container, false);
		toolbar = binding.toolbar;
		selectionToolbar = binding.selectionToolbar;
		recyclerView = binding.recyclerview;
		bottomBar = binding.bottomBar;
		deleteButton = binding.delete;
		restoreButton = binding.restore;

		return binding.getRoot();
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		toolbar.setNavigationOnClickListener(v -> getParentFragmentManager().popBackStack());
	}




	private void deleteSelectedItems() {
		//Get the selected items
		List<ListItem> toDelete = getSelected();

		selectionController.stopSelecting();

		//And full-delete them
		Thread trashThread = new Thread(() -> {
			List<ListItem> failed = DirUtilities.deleteFiles(toDelete);
			if(!failed.isEmpty()) {
				Toast.makeText(getContext(), failed.size()+" files were unable to be deleted!", Toast.LENGTH_SHORT).show();
			}
			updateList();
		});
		trashThread.start();
	}


	private List<ListItem> getSelected() {
		//Get the selected items
		Set<UUID> selectedItems = new HashSet<>(selectionController.getSelectedList());

		//Grab the first instance of each selected item in the list
		List<ListItem> selected = new ArrayList<>();
		List<ListItem> currList = adapter.list;
		for(int i = 0; i < currList.size(); i++) {
			UUID itemUID = currList.get(i).fileUID;

			if(selectedItems.contains(itemUID)) {
				selected.add(currList.get(i));
				selectedItems.remove(itemUID);
			}
		}

		return selected;
	}


	//---------------------------------------------------------------------------------------------

	public static class TrashViewModel extends ViewModel {
		public final UUID dirUID;
		public final SelectionController.SelectionRegistry selectionRegistry;

		public TrashViewModel(UUID dirUID) {
			this.dirUID = dirUID;
			this.selectionRegistry = new SelectionController.SelectionRegistry();
		}


		public static class Factory implements ViewModelProvider.Factory {
			private final UUID dirUID;
			public Factory(UUID dirUID) {
				this.dirUID = dirUID;
			}

			@NonNull
			@Override
			public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
				if (modelClass.isAssignableFrom(TrashFullscreen.TrashViewModel.class)) {
					return (T) new TrashFullscreen.TrashViewModel(dirUID);
				}
				throw new IllegalArgumentException("Unknown ViewModel class");
			}
		}
	}
}
