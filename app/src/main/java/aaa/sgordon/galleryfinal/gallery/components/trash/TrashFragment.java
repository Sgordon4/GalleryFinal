package aaa.sgordon.galleryfinal.gallery.components.trash;

import android.os.Bundle;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;

import org.apache.commons.io.FilenameUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import aaa.sgordon.galleryfinal.databinding.DirTrashBinding;
import aaa.sgordon.galleryfinal.gallery.DirFragment;
import aaa.sgordon.galleryfinal.gallery.DirRVAdapter;
import aaa.sgordon.galleryfinal.gallery.FilterController;
import aaa.sgordon.galleryfinal.gallery.ListItem;
import aaa.sgordon.galleryfinal.gallery.touch.SelectionController;
import aaa.sgordon.galleryfinal.gallery.viewholders.BaseViewHolder;
import aaa.sgordon.galleryfinal.utilities.DirUtilities;

public class TrashFragment extends Fragment {
	private DirTrashBinding binding;
	private DirFragment dirFragment;
	private TrashViewModel viewModel;

	private MaterialToolbar toolbar;
	private MaterialToolbar selectionToolbar;
	private RecyclerView recyclerView;
	private ViewGroup noItemsMessage;
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
				new TrashViewModel.Factory(dirFragment.dirViewModel.listItem.fileUID))
				.get(TrashViewModel.class);
	}

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		binding = DirTrashBinding.inflate(inflater, container, false);
		toolbar = binding.toolbar;
		selectionToolbar = binding.selectionToolbar;
		recyclerView = binding.recyclerview;
		noItemsMessage = binding.noItemsMessage;
		bottomBar = binding.bottomBar;
		deleteButton = binding.delete;
		restoreButton = binding.restore;

		return binding.getRoot();
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		toolbar.setNavigationOnClickListener(v -> getParentFragmentManager().popBackStack());

		deleteButton.setOnClickListener(view1 -> delete());
		restoreButton.setOnClickListener(view1 -> restore());


		LinearLayoutManager layoutManager = new GridLayoutManager(getContext(), 3);
		recyclerView.setLayoutManager(layoutManager);

		adapter = new DirRVAdapter();
		recyclerView.setAdapter(adapter);

		adapter.setCallbacks(new DirRVAdapter.AdapterCallbacks() {
			@Override
			public boolean isItemSelected(UUID fileUID) {
				return selectionController.isSelected(fileUID);
			}

			@Override
			public boolean onHolderMotionEvent(UUID fileUID, BaseViewHolder holder, MotionEvent event) {
				this.fileUID = fileUID;
				return detector.onTouchEvent(event);
			}

			UUID fileUID = null;
			final GestureDetector detector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
				@Override
				public void onLongPress(@NonNull MotionEvent e) {
					selectionController.startSelecting();
					selectionController.selectItem(fileUID);
				}

				@Override
				public boolean onSingleTapUp(@NonNull MotionEvent e) {
					//If we're selecting, select/deselect the item
					if(selectionController.isSelecting())
						selectionController.toggleSelectItem(fileUID);

					//TODO If we're not selecting, launch a new fragment

					return true;
				}

				@Override
				public boolean onDown(@NonNull MotionEvent e) {
					return true;
				}
			});
		});



		SelectionController.SelectionCallbacks selectionCallbacks = new SelectionController.SelectionCallbacks() {
			@Override
			public void onSelectionStarted() {
				toolbar.setVisibility(View.GONE);
				selectionToolbar.setVisibility(View.VISIBLE);
				deleteButton.setText("Delete Selected");
				restoreButton.setText("Restore Selected");
			}

			@Override
			public void onSelectionStopped() {
				toolbar.setVisibility(View.VISIBLE);
				selectionToolbar.setVisibility(View.GONE);
				deleteButton.setText("Delete All");
				restoreButton.setText("Restore All");
			}

			@Override
			public void onNumSelectedChanged(int numSelected) {
				selectionToolbar.setTitle( String.valueOf(numSelected) );
				if(numSelected == 0) selectionController.stopSelecting();
			}

			@Override
			public UUID getUUIDAtPos(int pos) {
				return adapter.list.get(pos).fileUID;
			}

			@Override
			public void onSelectionChanged(UUID fileUID, boolean isSelected) {
				//For any visible item, update the item selection status to change its appearance
				//There may be more than one item in the list with the same fileUID due to links
				//Non-visible items will have their selection status set later when they are bound by the adapter
				//TODO This isn't catching items just off the screen, fix this garbage
				for(int i = 0; i < recyclerView.getChildCount(); i++) {
					View itemView = recyclerView.getChildAt(i);
					if(itemView != null) {
						int adapterPos = recyclerView.getChildAdapterPosition(itemView);

						if(fileUID.equals( getUUIDAtPos(adapterPos)) )
							itemView.setSelected(isSelected);
					}
				}
			}
		};
		selectionController = new SelectionController(viewModel.selectionRegistry, selectionCallbacks);

		if(selectionController.isSelecting()) {
			selectionToolbar.setTitle( String.valueOf(selectionController.getNumSelected()) );
			toolbar.setVisibility(View.GONE);
			selectionToolbar.setVisibility(View.VISIBLE);
			deleteButton.setText("Delete Selected");
			restoreButton.setText("Restore Selected");
		}
		selectionToolbar.setNavigationOnClickListener(v -> selectionController.stopSelecting());

		//Deselect any items that were removed from the list
		dirFragment.dirViewModel.fileList.observe(getViewLifecycleOwner(), list -> {
			if(selectionController.isSelecting()) {
				//Grab all UUIDs from the full list
				Set<UUID> inAdapter = adapter.list.stream()
						.map(item -> item.fileUID)
						.collect(Collectors.toSet());

				//Grab all UUIDs from the new list
				Set<UUID> inNewList = list.stream()
						.map(item -> item.fileUID)
						.collect(Collectors.toSet());

				//Directly deselect any missing items (no need to worry about visuals, the items don't exist anymore)
				inAdapter.removeAll(inNewList);
				for(UUID itemUID : inAdapter)
					viewModel.selectionRegistry.deselectItem(itemUID);

				selectionCallbacks.onNumSelectedChanged(viewModel.selectionRegistry.getNumSelected());
			}
		});



		FilterController filterController = new FilterController(viewModel.filterRegistry, dirFragment.dirViewModel.getAttrCache());
		dirFragment.dirViewModel.fileList.observe(getViewLifecycleOwner(), filterController::onListUpdated);

		filterController.addExtraQueryFilter(listItem -> {
			//Include only trashed items
			return FilenameUtils.getExtension(listItem.name).startsWith("trashed_");
		});

		filterController.registry.filteredList.observe(getViewLifecycleOwner(), list -> {
			//Remove the .trashed_... suffix from each item so they display correctly
			list = list.stream().map(item -> new ListItem.Builder(item)
					.setName(FilenameUtils.removeExtension(item.name)).build())
					.collect(Collectors.toList());

			if(list.isEmpty())
				noItemsMessage.setVisibility(View.VISIBLE);
			else
				noItemsMessage.setVisibility(View.GONE);

			adapter.setList(list);
		});



		requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
			@Override
			public void handleOnBackPressed() {
				//If we're selecting, stop selection mode rather than leaving
				if(selectionController.isSelecting())
					selectionController.stopSelecting();
				else
					getParentFragmentManager().popBackStack();
			}
		});
	}




	private void delete() {
		//If there are no items to delete, do nothing
		if(adapter.list.isEmpty()) return;

		int numSelected = selectionController.getNumSelected();
		boolean isSelecting = selectionController.isSelecting() && numSelected > 0;

		//Launch a confirmation dialog first
		AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
		builder.setTitle((isSelecting) ?
				"Delete "+numSelected+" item"+(numSelected==1?"":"s")+"?" :
				"Delete all items?");
		builder.setMessage("Deleted items will be permanently removed from your account.");


		builder.setPositiveButton("Yes", (dialogInterface, which) -> {
			//Get the selected items, or all items if not selecting
			List<ListItem> toDelete;
			if(selectionController.isSelecting() && selectionController.getNumSelected() > 0)
				toDelete = getSelected();
			else
				toDelete = new ArrayList<>(adapter.list);

			selectionController.stopSelecting();


			//And full-delete them
			Thread deleteThread = new Thread(() -> {
				List<ListItem> failed = DirUtilities.deleteFiles(toDelete);
				if(!failed.isEmpty()) {
					Toast.makeText(getContext(), failed.size()+" files were unable to be deleted!", Toast.LENGTH_SHORT).show();
				}
			});
			deleteThread.start();
		});
		builder.setNegativeButton("No", null);

		AlertDialog dialog = builder.create();
		dialog.show();
	}
	private void restore() {
		//If there are no items to restore, do nothing
		if(adapter.list.isEmpty()) return;

		int numSelected = selectionController.getNumSelected();
		boolean isSelecting = selectionController.isSelecting() && numSelected > 0;

		//Launch a confirmation dialog first
		AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
		builder.setTitle((isSelecting) ?
				"Restore "+numSelected+" item"+(numSelected==1?"":"s")+"?" :
				"Restore all items?");
		builder.setMessage("Restored items will reappear in their original positions.");


		builder.setPositiveButton("Yes", (dialogInterface, which) -> {
			//Get the selected items, or all items if not selecting
			List<ListItem> toRestore;
			if(selectionController.isSelecting() && selectionController.getNumSelected() > 0)
				toRestore = getSelected();
			else
				toRestore = new ArrayList<>(adapter.list);

			selectionController.stopSelecting();

			//The .trashed suffix is already removed when passing items to the adapter in order to
			// display it correctly, so each ListItem conveniently already has the correct name

			//And 'restore' them
			Thread restoreThread = new Thread(() -> {
				DirUtilities.renameFiles(toRestore);
			});
			restoreThread.start();
		});
		builder.setNegativeButton("No", null);

		AlertDialog dialog = builder.create();
		dialog.show();
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
		public final FilterController.FilterRegistry filterRegistry;

		public TrashViewModel(UUID dirUID) {
			this.dirUID = dirUID;
			this.selectionRegistry = new SelectionController.SelectionRegistry();
			this.filterRegistry = new FilterController.FilterRegistry();
		}

		public static class Factory implements ViewModelProvider.Factory {
			private final UUID dirUID;
			public Factory(UUID dirUID) {
				this.dirUID = dirUID;
			}

			@NonNull
			@Override
			public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
				if (modelClass.isAssignableFrom(TrashViewModel.class)) {
					return (T) new TrashViewModel(dirUID);
				}
				throw new IllegalArgumentException("Unknown ViewModel class");
			}
		}
	}
}
