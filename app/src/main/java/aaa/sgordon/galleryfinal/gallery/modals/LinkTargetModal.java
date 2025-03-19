package aaa.sgordon.galleryfinal.gallery.modals;

import android.app.Dialog;
import android.os.Bundle;
import android.os.Handler;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;

import org.apache.commons.io.FilenameUtils;

import java.io.FileNotFoundException;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.gallery.DirFragment;
import aaa.sgordon.galleryfinal.gallery.DirRVAdapter;
import aaa.sgordon.galleryfinal.gallery.ListItem;
import aaa.sgordon.galleryfinal.gallery.TraversalHelper;
import aaa.sgordon.galleryfinal.gallery.touch.SelectionController;
import aaa.sgordon.galleryfinal.gallery.viewholders.BaseViewHolder;
import aaa.sgordon.galleryfinal.repository.caches.LinkCache;
import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;
import aaa.sgordon.galleryfinal.utilities.DirUtilities;

public class LinkTargetModal extends DialogFragment {
	private final LinkTargetViewModel viewModel;
	private final LinkTargetCallback callback;

	private MaterialToolbar toolbar;
	private MaterialToolbar selectionToolbar;
	private RecyclerView recyclerView;
	private ViewGroup bottomBar;
	private Button confirmButton;

	private DirRVAdapter adapter;
	private SelectionController selectionController;


	public static void launch(DirFragment dirFragment, UUID dirUID, LinkTargetCallback callback) {
		LinkTargetModal dialog = new LinkTargetModal(dirFragment, dirUID, callback);

		FragmentTransaction transaction = dirFragment.getChildFragmentManager().beginTransaction();
		transaction.add(dialog, "linktarget_fullscreen");
		transaction.commitAllowingStateLoss();
	}
	private LinkTargetModal(DirFragment dirFragment, UUID dirUID, LinkTargetCallback callback) {
		this.callback = callback;

		viewModel = new ViewModelProvider(dirFragment,
				new LinkTargetViewModel.Factory(dirUID))
				.get(LinkTargetViewModel.class);
	}


	public interface LinkTargetCallback {
		void onTargetSelected(ListItem target);
	}


	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.frag_dir_linktarget_fullscreen, container, false);
		toolbar = view.findViewById(R.id.toolbar);
		selectionToolbar = view.findViewById(R.id.selection_toolbar);
		recyclerView = view.findViewById(R.id.recyclerview);
		bottomBar = view.findViewById(R.id.bottom_bar);
		confirmButton = view.findViewById(R.id.confirm);

		return view;
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		toolbar.setNavigationOnClickListener(v -> dismiss());
		toolbar.setOnMenuItemClickListener(item -> {
			if(item.getItemId() == R.id.select)
				selectionController.startSelecting();

			if(item.getItemId() == R.id.refresh)
				updateList();
			return false;
		});



		confirmButton.setOnClickListener(view1 -> {
			//Get the selected item (there should only be one)
			ListItem target = getSelected().get(0);

			//And return the result
			callback.onTargetSelected(target);
			dismiss();
		});



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
			GestureDetector detector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
				@Override
				public void onLongPress(@NonNull MotionEvent e) {
					selectionController.startSelecting();
					selectionController.selectItem(fileUID);
				}

				@Override
				public boolean onSingleTapUp(@NonNull MotionEvent e) {
					//If we're selecting, select/deselect the item
					if(selectionController.isSelecting()) {
						selectionController.toggleSelectItem(fileUID);
					}

					return true;
				}

				@Override
				public boolean onDown(@NonNull MotionEvent e) {
					return true;
				}
			});
		});



		selectionController = new SelectionController(viewModel.selectionRegistry, new SelectionController.SelectionCallbacks() {
			@Override
			public void onSelectionStarted() {
				selectionToolbar.setTitle("Target: Nothing");
				confirmButton.setEnabled(false);

				toolbar.setVisibility(View.GONE);
				selectionToolbar.setVisibility(View.VISIBLE);
				bottomBar.setVisibility(View.VISIBLE);
			}

			@Override
			public void onSelectionStopped() {
				toolbar.setVisibility(View.VISIBLE);
				selectionToolbar.setVisibility(View.GONE);
				bottomBar.setVisibility(View.GONE);
			}

			@Override
			public void onSelectionChanged(UUID fileUID, boolean isSelected) {
				//For any visible item, update the item selection status to change its appearance
				//There may be more than one item in the list with the same fileUID due to links
				//Non-visible items will have their selection status set later when they are bound by the adapter
				for(int i = 0; i < recyclerView.getChildCount(); i++) {
					View itemView = recyclerView.getChildAt(i);
					if(itemView != null) {
						int adapterPos = recyclerView.getChildAdapterPosition(itemView);

						if(fileUID.equals( getUUIDAtPos(adapterPos)) )
							itemView.setSelected(isSelected);
					}
				}
			}

			@Override
			public void onNumSelectedChanged(int numSelected) {
				if(numSelected == 0) {
					selectionController.stopSelecting();
					return;
				}

				selectionToolbar.setTitle("Target: \""+getSelected().get(0).name+"\"");
				confirmButton.setEnabled(true);
			}

			@Override
			public UUID getUUIDAtPos(int pos) {
				return adapter.list.get(pos).fileUID;
			}
		});
		selectionController.setSingleItemMode(true);
		if(selectionController.isSelecting()) {
			List<ListItem> selected = getSelected();
			if(selected.isEmpty()) {
				selectionToolbar.setTitle("Target: Nothing");
				confirmButton.setEnabled(false);
			}
			else {
				selectionToolbar.setTitle("Target: \"" + selected.get(0).name + "\"");
				confirmButton.setEnabled(true);
			}


			toolbar.setVisibility(View.GONE);
			selectionToolbar.setVisibility(View.VISIBLE);
			bottomBar.setVisibility(View.VISIBLE);
		}
		selectionToolbar.setNavigationOnClickListener(v -> selectionController.stopSelecting());


		//Update the list
		updateList();
	}


	private void updateList() {
		Thread traverse = new Thread(() -> {
			List<ListItem> list = traverseDir(viewModel.dirUID);

			//Remove duplicate items from the list
			Set<UUID> duplicate = new HashSet<>();
			list.removeIf(item -> !duplicate.add(item.fileUID));

			Handler mainHandler = new Handler(getContext().getMainLooper());
			mainHandler.post(() -> adapter.setList(list));
		});
		traverse.start();
	}



	@NonNull
	private List<ListItem> traverseDir(UUID dirUID) {
		try {
			//If the item is a link to a directory, follow that link
			dirUID = LinkCache.getInstance().resolvePotentialLink(dirUID);

			//Grab the current list of all files in this directory from the system
			List<ListItem> newFileList = TraversalHelper.traverseDir(dirUID);

			newFileList = newFileList.stream()
					//Filter out anything that is trashed
					.filter(item -> !FilenameUtils.getExtension(item.name).startsWith("trashed_"))
					.collect(Collectors.toList());

			return newFileList;
		}
		catch (ContentsNotFoundException | FileNotFoundException | ConnectException e) {
			//TODO Actually handle the error. Dir should be on local, but jic
			throw new RuntimeException(e);
		}
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




	@Override
	public void onStart() {
		super.onStart();

		//Make the dialog fullscreen
		Dialog dialog = getDialog();
		if (dialog != null) {
			int width = ViewGroup.LayoutParams.MATCH_PARENT;
			int height = ViewGroup.LayoutParams.MATCH_PARENT;
			dialog.getWindow().setLayout(width, height);

			dialog.setOnKeyListener((dialogInterface, keyCode, event) -> {
				if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
					//If we're selecting...
					if(selectionController.isSelecting()) {
						//Stop selection mode
						selectionController.stopSelecting();

						//Stop the back event from dismissing the dialog
						return true;
					}
				}
				return false; //Let the event propagate normally
			});
		}
	}



	public static class LinkTargetViewModel extends ViewModel {
		public final UUID dirUID;
		public final SelectionController.SelectionRegistry selectionRegistry;

		public LinkTargetViewModel(UUID dirUID) {
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
				if (modelClass.isAssignableFrom(LinkTargetViewModel.class)) {
					return (T) new LinkTargetViewModel(dirUID);
				}
				throw new IllegalArgumentException("Unknown ViewModel class");
			}
		}
	}
}
