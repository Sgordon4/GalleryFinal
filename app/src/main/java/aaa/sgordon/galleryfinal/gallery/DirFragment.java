package aaa.sgordon.galleryfinal.gallery;

import static android.os.Looper.getMainLooper;

import android.annotation.SuppressLint;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.util.Pair;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.FragmentNavigator;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
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
import aaa.sgordon.galleryfinal.gallery.touch.DragSelectCallback;
import aaa.sgordon.galleryfinal.gallery.touch.ItemReorderCallback;
import aaa.sgordon.galleryfinal.gallery.touch.SelectionController;
import aaa.sgordon.galleryfinal.gallery.viewholders.BaseViewHolder;
import aaa.sgordon.galleryfinal.gallery.viewholders.GifViewHolder;
import aaa.sgordon.galleryfinal.gallery.viewholders.ImageViewHolder;
import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;

public class DirFragment extends Fragment {
	private FragmentDirectoryBinding binding;

	DirectoryViewModel dirViewModel;

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		MainViewModel mainViewModel = new ViewModelProvider(getActivity()).get(MainViewModel.class);
		System.out.println("Inside directory, Activity has been created "+mainViewModel.testInt+" times.");

		DirFragmentArgs args = DirFragmentArgs.fromBundle(getArguments());
		UUID directoryUID = args.getDirectoryUID();
		dirViewModel = new ViewModelProvider(getParentFragment(),
				new DirectoryViewModel.Factory(directoryUID))
				.get(DirectoryViewModel.class);
	}

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		binding = FragmentDirectoryBinding.inflate(inflater, container, false);
		return binding.getRoot();
	}

	@SuppressLint("ClickableViewAccessibility")
	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);


		postponeEnterTransition(); // Pause the transition

		// Recyclerview things:
		RecyclerView recyclerView = binding.recyclerview;
		GridLayoutManager layoutManager = new GridLayoutManager(getContext(), 4) {
			@Override
			public void calculateItemDecorationsForChild(@NonNull View child, @NonNull Rect outRect) {
				super.calculateItemDecorationsForChild(child, outRect);

				//Since the RV is in a coordinatorlayout, sometimes the end of the RV is offscreen
				//We want to scroll once we reach the end of the screen, so return that difference
				int rvBottom = recyclerView.getBottom();
				int parentBottom = ((View)recyclerView.getParent()).getBottom();
				outRect.bottom = rvBottom - parentBottom;

				//Also add some leeway for ease of use
				outRect.top += 50;
				outRect.bottom += 50;
			}
		};
		recyclerView.setLayoutManager(layoutManager);

		recyclerView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
			@Override
			public boolean onPreDraw() {
				recyclerView.getViewTreeObserver().removeOnPreDrawListener(this);
				startPostponedEnterTransition(); // Resume transition once layout is ready
				return true;
			}
		});



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

		MaterialToolbar toolbar = binding.galleryAppbar.toolbar;
		MaterialToolbar selectionToolbar = binding.galleryAppbar.selectionToolbar;
		NavController navController = Navigation.findNavController(view);
		AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder().build();
		//NavigationUI.setupWithNavController(toolbar, navController, appBarConfiguration);

		toolbar.setNavigationOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				navController.popBackStack();
			}
		});
		//Hide the navigation icon when we're at the top-level
		navController.addOnDestinationChangedListener((navController1, navDestination, bundle) -> {
			if(navController1.getPreviousBackStackEntry() == null)
				toolbar.setNavigationIcon(null);
		});

		//Must set title after configuration
		DirFragmentArgs args = DirFragmentArgs.fromBundle(getArguments());
		String directoryName = args.getDirectoryName();
		binding.galleryAppbar.toolbar.setTitle(directoryName);


		SelectionController.SelectionCallbacks selectionCallbacks = new SelectionController.SelectionCallbacks() {
			@Override
			public void onSelectionStarted() {
				toolbar.setVisibility(View.GONE);
				selectionToolbar.setVisibility(View.VISIBLE);
			}
			@Override
			public void onSelectionStopped() {
				toolbar.setVisibility(View.VISIBLE);
				selectionToolbar.setVisibility(View.GONE);
			}

			@Override
			public void onSelectionChanged(UUID fileUID, boolean isSelected) {

				/*
				for(int i = 0; i < adapter.list.size(); i++) {
					if(fileUID.equals( getUUIDAtPos(i)) )
						adapter.notifyItemChanged(i);
				}
				/**/

				/**/
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
				/**/
			}

			@Override
			public void onNumSelectedChanged(int numSelected) {
				selectionToolbar.setTitle( String.valueOf(numSelected) );
				selectionToolbar.getMenu().getItem(1).setEnabled(numSelected == 1);	//Disable edit button unless only one item is selected
			}

			@Override
			public UUID getUUIDAtPos(int pos) {
				String UUIDString = adapter.list.get(pos).first.getFileName().toString();
				if(UUIDString.equals("END"))
					UUIDString = adapter.list.get(pos).first.getParent().getFileName().toString();

				return UUID.fromString(UUIDString);
			}
		};
		SelectionController selectionController = new SelectionController(dirViewModel.getSelectionRegistry(), selectionCallbacks);


		selectionToolbar.setOnMenuItemClickListener(menuItem -> {
			//TODO How do we deal with disappearing items that are selected? Do we just watch for that in the livedata listener?
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

		if(selectionController.isSelecting()) {
			selectionToolbar.setTitle( String.valueOf(selectionController.getNumSelected()) );
			toolbar.setVisibility(View.GONE);
			selectionToolbar.setVisibility(View.VISIBLE);
		}

		selectionToolbar.setNavigationOnClickListener(view2 -> {
			toolbar.setVisibility(View.VISIBLE);
			selectionToolbar.setVisibility(View.GONE);

			selectionController.stopSelecting();
		});

		//-----------------------------------------------------------------------------------------


		DragSelectCallback dragSelectCallback = new DragSelectCallback(recyclerView, adapter, selectionController);
		ItemTouchHelper dragSelectHelper = new ItemTouchHelper(dragSelectCallback);
		dragSelectHelper.attachToRecyclerView(recyclerView);



		ItemReorderCallback reorderCallback = new ItemReorderCallback(recyclerView, (destination, nextItem) -> {
			Thread reorderThread = new Thread(() -> {
				//Get the selected items from the viewModel's list and pass them along
				Set<UUID> selectedItems = new HashSet<>(dirViewModel.getSelectionRegistry().getSelectedList());
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

				} catch (FileNotFoundException | NotDirectoryException | ContentsNotFoundException |
						 ConnectException e) {
					throw new RuntimeException(e);
				}
			});
			reorderThread.start();
		});
		ItemTouchHelper reorderHelper = new ItemTouchHelper(reorderCallback);
		reorderHelper.attachToRecyclerView(recyclerView);



		dirViewModel.flatList.observe(getViewLifecycleOwner(), list -> {
			if(selectionController.isSelecting()) {
				//Unselect any items that are no longer in the list
				//Note: This is completely untested
				Set<UUID> inAdapter = new HashSet<>();
				for(Pair<Path, String> item : adapter.list) {
					String UUIDString = item.first.getFileName().toString();
					if(UUIDString.equals("END"))
						UUIDString = item.first.getParent().getFileName().toString();
					UUID itemUID = UUID.fromString(UUIDString);

					inAdapter.add(itemUID);
				}
				Set<UUID> inNewList = new HashSet<>();
				for(Pair<Path, String> item : list) {
					String UUIDString = item.first.getFileName().toString();
					if(UUIDString.equals("END"))
						UUIDString = item.first.getParent().getFileName().toString();
					UUID itemUID = UUID.fromString(UUIDString);

					inNewList.add(itemUID);
				}

				//Since the items are gone and won't be in the list anymore, we can just directly deselect them
				inAdapter.removeAll(inNewList);
				for(UUID itemUID : inAdapter) {
					dirViewModel.getSelectionRegistry().deselectItem(itemUID);
				}
				selectionCallbacks.onNumSelectedChanged(dirViewModel.getSelectionRegistry().getNumSelected());
			}


			adapter.setList(list);
			reorderCallback.applyReorder();
		});



		DirRVAdapter.AdapterCallbacks adapterCallbacks = new DirRVAdapter.AdapterCallbacks() {
			@Override
			public boolean isItemSelected(UUID fileUID) {
				return selectionController.isSelected(fileUID);
			}
			@Override
			public boolean isDir(UUID fileUID) {
				return dirViewModel.isDir(fileUID);
			}
			@Override
			public boolean isLink(UUID fileUID) {
				return dirViewModel.isLink(fileUID);
			}

			UUID fileUID = null;
			BaseViewHolder holder;
			@Override
			public boolean onHolderMotionEvent(UUID fileUID, BaseViewHolder holder, MotionEvent event) {
				if(event.getAction() == MotionEvent.ACTION_DOWN) {
					this.fileUID = fileUID;
					this.holder = holder;
					isDoubleTapInProgress = false;
				}
				else if(event.getAction() == MotionEvent.ACTION_UP) {
					isDoubleTapInProgress = false;
				}

				reorderCallback.onMotionEvent(event);
				dragSelectCallback.onMotionEvent(event);


				return detector.onTouchEvent(event);
			}

			boolean isDoubleTapInProgress = false;
			final GestureDetector detector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
				@Override
				public void onLongPress(@NonNull MotionEvent e) {
					selectionController.startSelecting();
					selectionController.selectItem(fileUID);

					if(isDoubleTapInProgress) {
						System.out.println("Double longPress");
						//DoubleTap LongPress triggers a reorder
						isDoubleTapInProgress = false;
						reorderHelper.startDrag(holder);
					}
					else {
						System.out.println("Single longPress");
						//SingleTap LongPress triggers drag selection
						dragSelectHelper.startDrag(holder);
					}
				}

				@Override
				public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
					//If we're selecting, select/deselect the item
					if(selectionController.isSelecting())
						selectionController.toggleSelectItem(fileUID);
					//If we're not selecting, launch a new fragment
					else if(holder instanceof ImageViewHolder || holder instanceof GifViewHolder) {
						int pos = holder.getAdapterPosition();

						//Transition is causing visual problems I don't like, and Google photos
						// doesn't even use an exit transition, so I'm disabling it
						/*
						//Fade out the grid when transitioning
						setExitTransition(TransitionInflater.from(getContext())
								.inflateTransition(R.transition.grid_fade_transition));

						// Exclude the clicked card from the exit transition (e.g. the card will disappear immediately
						// instead of fading out with the rest to prevent an overlapping animation of fade and move).
						((TransitionSet) getExitTransition()).excludeTarget(holder.itemView.findViewById(R.id.child), true);
						 */

						DirFragmentDirections.ActionToViewPagerFragment action = DirFragmentDirections
								.actionToViewPagerFragment(dirViewModel.getDirUID());
						action.setFromPosition(pos);

						View mediaView = holder.itemView.findViewById(R.id.media);
						System.out.println(mediaView);
						System.out.println(mediaView.getTransitionName());

						FragmentNavigator.Extras extras = new FragmentNavigator.Extras.Builder()
								.addSharedElement(mediaView, mediaView.getTransitionName())
								.build();

						//binding.galleryAppbar.appbar.setExpanded(false, false);
						navController.navigate(action, extras);
					}
					return true;
				}

				@Override
				public boolean onDoubleTapEvent(@NonNull MotionEvent e) {
					System.out.println("Doubling");
					if(e.getAction() == MotionEvent.ACTION_DOWN)
						isDoubleTapInProgress = true;

					System.out.println("OOOgha: "+e.getAction());
					return false;
				}

				@Override
				public boolean onDown(@NonNull MotionEvent e) {
					return true;
				}
			});
		};
		adapter.setCallbacks(adapterCallbacks);




		recyclerView.setOnTouchListener((view3, motionEvent) -> {
			reorderCallback.onMotionEvent(motionEvent);
			dragSelectCallback.onMotionEvent(motionEvent);
			return false;
		});




		requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
			@Override
			public void handleOnBackPressed() {
				//If we're selecting, stop selection mode rather than leaving
				if(selectionController.isSelecting()) {
					toolbar.setVisibility(View.VISIBLE);
					selectionToolbar.setVisibility(View.GONE);

					selectionController.stopSelecting();
				}
				else if (navController.getPreviousBackStackEntry() != null) {
					navController.popBackStack();
				} else {
					requireActivity().finish(); // Close the app if no back stack
				}
			}
		});

		//-----------------------------------------------------------------------------------------

		//Temporary button for testing
		binding.buttonDrilldown.setOnClickListener(view1 -> {
			DirFragmentDirections.ActionToDirectoryFragment action =
					DirFragmentDirections.actionToDirectoryFragment(dirViewModel.getDirUID());
			action.setDirectoryName("AAAAAA");
			NavHostFragment.findNavController(this).navigate(action);
		});
		//Button is kinda not useful now that we've hooked things up to the database, but I'm keeping this code for ref
		//Note: Using this button will make another fragment using the same directoryID, meaning that if we import things
		// in any of the child frags those things will show up in the previous frags too.
		//This is NOT a bug, and is actually the intended use case lmao. Pretty neat that it works though.
		//binding.buttonDrilldown.setVisibility(View.GONE);


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
	public void onResume() {
		super.onResume();
		setExitTransition(null); // Clears the transition when returning
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
