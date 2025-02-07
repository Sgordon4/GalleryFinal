package aaa.sgordon.galleryfinal.gallery;

import static android.os.Looper.getMainLooper;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.leinardi.android.speeddial.SpeedDialView;

import java.io.FileNotFoundException;
import java.net.ConnectException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import aaa.sgordon.galleryfinal.MainViewModel;
import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.databinding.FragmentDirectoryBinding;
import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;

public class DirFragment extends Fragment {
	private FragmentDirectoryBinding binding;

	DirectoryViewModel dirViewModel;

	boolean isDoubleTapInProgress = false;


	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		binding = FragmentDirectoryBinding.inflate(inflater, container, false);
		return binding.getRoot();
	}

	@SuppressLint("ClickableViewAccessibility")
	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		MainViewModel mainViewModel = new ViewModelProvider(getActivity()).get(MainViewModel.class);
		System.out.println("Inside directory, Activity has been created "+mainViewModel.testInt+" times.");

		DirFragmentArgs args = DirFragmentArgs.fromBundle(getArguments());
		UUID directoryUID = UUID.fromString( args.getDirectoryUID() );
		//dirViewModel = new ViewModelProvider(this).get(DirectoryViewModel.class);
		dirViewModel = new ViewModelProvider(this,
				new DirectoryViewModel.DirectoryViewModelFactory(getActivity().getApplication(), directoryUID))
				.get(DirectoryViewModel.class);
		SelectionController.SelectionRegistry registry = dirViewModel.getSelectionRegistry();

		//-----------------------------------------------------------------------------------------

		MaterialToolbar toolbar = binding.galleryAppbar.toolbar;
		NavController navController = Navigation.findNavController(view);
		AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder().build();
		NavigationUI.setupWithNavController(toolbar, navController, appBarConfiguration);

		//Hide the navigation icon when we're at the top-level
		navController.addOnDestinationChangedListener((navController1, navDestination, bundle) -> {
			if(navController1.getPreviousBackStackEntry() == null)
				toolbar.setNavigationIcon(null);
		});

		//Must set title after configuration
		String directoryName = args.getDirectoryName();
		binding.galleryAppbar.toolbar.setTitle(directoryName);

		//-----------------------------------------------------------------------------------------

		// Recyclerview things:
		RecyclerView recyclerView = binding.recyclerview;
		recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
		//recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 4));

		DirRVAdapter adapter = new DirRVAdapter();
		recyclerView.setAdapter(adapter);

		//DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(getContext(), DividerItemDecoration.VERTICAL);
		//recyclerView.addItemDecoration(dividerItemDecoration);

		if(savedInstanceState != null) {
			Parcelable rvState = savedInstanceState.getParcelable("rvState");
			if(rvState != null) {
				System.out.println("Parcel found: "+rvState);
				recyclerView.getLayoutManager().onRestoreInstanceState(rvState);
			}
		}

		//-----------------------------------------------------------------------------------------

		ItemReorderCallback reorderCallback = new ItemReorderCallback(recyclerView, (destination, nextItem) -> {
			Thread reorderThread = new Thread(() -> {
				//Get the selected items from the viewModel's list and pass them along
				Set<UUID> selectedItems = new HashSet<>(registry.getSelectedList());
				List<Pair<Path, String>> toMove = new ArrayList<>();

				//Grab the first instance of each selected item in the list
				List<Pair<Path, String>> currList = dirViewModel.flatList.getValue();
				for(int i = 0; i < currList.size(); i++) {
					//Get the UUID of this item
					String UUIDString = currList.get(i).first.getFileName().toString();
					if(UUIDString.equals("END"))
						UUIDString = currList.get(i).first.getParent().getFileName().toString();
					UUID itemUID = UUID.fromString(UUIDString);

					if(selectedItems.contains(itemUID)) {
						toMove.add(currList.get(i));
						selectedItems.remove(itemUID);
					}
				}


				try {
					boolean successful = dirViewModel.moveFiles(destination, nextItem, toMove);
					if(successful) return;

					//If the move was not successful, we want to return the list to how it was before we dragged
					Runnable myRunnable = () -> adapter.setList(dirViewModel.flatList.getValue());
					new Handler(getMainLooper()).post(myRunnable);

				} catch (FileNotFoundException | NotDirectoryException | ContentsNotFoundException | ConnectException e) {
					throw new RuntimeException(e);
				}
			});
			reorderThread.start();
		});
		ItemTouchHelper reorderHelper = new ItemTouchHelper(reorderCallback);
		reorderHelper.attachToRecyclerView(recyclerView);

		dirViewModel.flatList.observe(getViewLifecycleOwner(), list -> {
			//TODO Unselect any items that are no longer in the list. Prob want to make a registry remove accessibility method
			adapter.setList(list);
			reorderCallback.applyReorder();
		});

		//-----------------------------------------------------------------------------------------

		MaterialToolbar selectionToolbar = binding.galleryAppbar.selectionToolbar;

		SelectionController selectionController = new SelectionController(registry, new SelectionController.SelectionCallbacks() {
			@Override
			public void onSelectionChanged(UUID fileUID, boolean isSelected) {
				//For any visible item, update the item selection status to change its appearance
				//There may be more than one item in the list with the same fileUID due to links
				//Non-visible items will have their selection status set later when they are bound by the adapter
				for(int i = 0; i < recyclerView.getChildCount(); i++) {
					View itemView = recyclerView.getChildAt(i);
					int adapterPos = recyclerView.getChildAdapterPosition(itemView);

					//Get the UUID of this item
					String UUIDString = adapter.list.get(adapterPos).first.getFileName().toString();
					if(UUIDString.equals("END"))
						UUIDString = adapter.list.get(adapterPos).first.getParent().getFileName().toString();
					UUID itemUID = UUID.fromString(UUIDString);

					if(fileUID.equals(itemUID))
						itemView.setSelected(isSelected);
				}
			}

			@Override
			public void onNumSelectedChanged(int numSelected) {
				selectionToolbar.setTitle( String.valueOf(numSelected) );
				if(numSelected == 1)
					selectionToolbar.getMenu().getItem(1).setEnabled(true);
				else
					selectionToolbar.getMenu().getItem(1).setEnabled(false);
			}
		});

		Handler handler = new Handler(Looper.getMainLooper());

		adapter.setCallbacks(new DirRVAdapter.AdapterCallbacks() {
			@Override
			public void onLongPress(DirRVAdapter.GalViewHolder holder, UUID fileUID) {
				if (!isDoubleTapInProgress) {
					System.out.println("Superlong");
					selectionController.startSelecting();
					selectionController.selectItem(fileUID);

					toolbar.setVisibility(View.GONE);
					selectionToolbar.setVisibility(View.VISIBLE);
				}
			}

			@Override
			public void onSingleTapConfirmed(DirRVAdapter.GalViewHolder holder, UUID fileUID) {
				if(selectionController.isSelecting())
					selectionController.toggleSelectItem(fileUID);
			}

			@Override
			public void onDoubleTap(DirRVAdapter.GalViewHolder holder, UUID fileUID) {
				//Prevents longPress from triggering, since we want to do different things when double tapping
				isDoubleTapInProgress = true;
				handler.postDelayed(() -> isDoubleTapInProgress = false, 300);

				selectionController.startSelecting();
				selectionController.selectItem(fileUID);

				toolbar.setVisibility(View.GONE);
				selectionToolbar.setVisibility(View.VISIBLE);

				reorderHelper.startDrag(holder);
			}

			@Override
			public boolean isItemSelected(UUID fileUID) {
				return selectionController.isSelected(fileUID);
			}
		});

		selectionToolbar.setOnMenuItemClickListener(menuItem -> {
			//TODO How do we deal with disappearing items that are selected?
			if(menuItem.getItemId() == R.id.select_all) {
				System.out.println("Select all!");
				Set<UUID> toSelect = new HashSet<>();
				for(Pair<Path, String> item : adapter.list) {
					String UUIDString = item.first.getFileName().toString();
					if(UUIDString.equals("END"))
						UUIDString = item.first.getParent().getFileName().toString();
					UUID itemUID = UUID.fromString(UUIDString);

					toSelect.add(itemUID);
				}

				//If all items are currently selected, we want to deselect all instead
				Set<UUID> currSelected = selectionController.getSelectedList();
				toSelect.removeAll(currSelected);
				if(!toSelect.isEmpty())
					selectionController.selectAll(toSelect);
				else
					selectionController.deselectAll();

			} else if(menuItem.getItemId() == R.id.edit) {
				System.out.println("Edit!");
			} else if(menuItem.getItemId() == R.id.share) {
				System.out.println("Share!");
			}
			return false;
		});

		selectionToolbar.setNavigationOnClickListener(view2 -> {
			toolbar.setVisibility(View.VISIBLE);
			selectionToolbar.setVisibility(View.GONE);

			selectionController.stopSelecting();
		});

		//-----------------------------------------------------------------------------------------

		recyclerView.setOnTouchListener((v, event) -> {
			if(event.getAction() == MotionEvent.ACTION_UP)
				v.performClick(); // Ensure accessibility compliance

			if(reorderCallback.isDragging())
				reorderCallback.onMotionEvent(event);

			View child = recyclerView.findChildViewUnder(event.getX(), event.getY());
			if(child != null) {
				int pos = recyclerView.getChildAdapterPosition(child);
				Pair<Path, String> item = adapter.list.get(pos);

				
			}


			return false; 	//Do not consume the event. We only want to spy.
		});

		//-----------------------------------------------------------------------------------------

		//Temporary button for testing
		binding.buttonDrilldown.setOnClickListener(view1 -> {
			DirFragmentDirections.ActionToDirectoryFragment action = DirFragmentDirections.actionToDirectoryFragment();
			action.setDirectoryUID(directoryUID.toString());
			action.setDirectoryName("AAAAAA");
			NavHostFragment.findNavController(this).navigate(action);
		});
		//Button is kinda not useful now that we've hooked things up to the database, but I'm keeping this code for ref
		//Note: Using this button will make another fragment using the same directoryID, meaning that if we import things
		// in any of the child frags those things will show up in the previous frags too.
		//This is NOT a bug, and is actually the intended use case lmao. Pretty neat that it works though.
		binding.buttonDrilldown.setVisibility(View.GONE);


		//Show/Hide the fab when scrolling:
		recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
			@Override
			public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
				SpeedDialView fab = binding.fab;
				if(dy > 0 && fab.isShown()) fab.hide();
				else if(dy < 0 && !fab.isShown()) fab.show();
			}
		});
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		System.out.println("Directory destroying ");
		binding = null;
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		System.out.println("Directory Saving: ");

		//If binding is null, extremely likely this frag is in the backstack and was destroyed (and saved) earlier
		if(binding != null) {
			Parcelable listState = binding.recyclerview.getLayoutManager().onSaveInstanceState();
			outState.putParcelable("rvState", listState);
		}

		super.onSaveInstanceState(outState);
	}
}
