package aaa.sgordon.galleryfinal.gallery;

import android.annotation.SuppressLint;
import android.graphics.Canvas;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Pair;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
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
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.leinardi.android.speeddial.SpeedDialView;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import aaa.sgordon.galleryfinal.MainViewModel;
import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.databinding.FragmentDirectoryBinding;

public class DirFragment extends Fragment {
	private FragmentDirectoryBinding binding;

	DirectoryViewModel dirViewModel;


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
		//recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
		//recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 4));
		recyclerView.setLayoutManager(new CustomGridLayoutManager(getContext(), 1));

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

		GalTouchSetup touchSetup = new GalTouchSetup(recyclerView, adapter);

		ItemReorderCallback reorderCallback = touchSetup.makeReorderCallback(dirViewModel, registry);
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

				/* No worky
				for(int i = 0; i < adapter.list.size(); i++) {
					if(fileUID.equals( getUUIDAtPos(i)) )
						adapter.notifyItemChanged(i);
				}
				 */


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
				selectionToolbar.setTitle( String.valueOf(numSelected) );
				if(numSelected == 1)
					selectionToolbar.getMenu().getItem(1).setEnabled(true);
				else
					selectionToolbar.getMenu().getItem(1).setEnabled(false);
			}

			@Override
			public UUID getUUIDAtPos(int pos) {
				String UUIDString = adapter.list.get(pos).first.getFileName().toString();
				if(UUIDString.equals("END"))
					UUIDString = adapter.list.get(pos).first.getParent().getFileName().toString();

				return UUID.fromString(UUIDString);
			}
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




		//touchSetup.setupRVListener(selectionController, reorderCallback);

		//adapter.setCallbacks(touchSetup.makeAdapterCallback(selectionController, reorderHelper, toolbar, selectionToolbar));


		recyclerView.setOnTouchListener((v, event) -> {
			//System.out.println(event.getAction());
			return false;
		});


		ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.Callback() {
			@Override
			public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
				return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN | ItemTouchHelper.LEFT| ItemTouchHelper.RIGHT, 0);
			}
			@Override
			public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
				return false;
			}
			@Override
			public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {}


			@Override
			public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView,
									@NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY,
									int actionState, boolean isCurrentlyActive) {

				//Get the current view being dragged
				View itemView = viewHolder.itemView;
				View child = itemView.findViewById(R.id.child);

				//Call the superclass method to ensure the normal drawing behavior is preserved
				super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);

				//Move the child of the dragged view to its original position to pretend nothing is being dragged
				child.setTranslationX(-dX);
				child.setTranslationY(-dY);


				//Select the targeted item
				View foundView = recyclerView.findChildViewUnder(itemView.getX(), itemView.getY());
				if(foundView != null) {
					int pos = recyclerView.getChildAdapterPosition(foundView);

					String UUIDString = adapter.list.get(pos).first.getFileName().toString();
					if(UUIDString.equals("END"))
						UUIDString = adapter.list.get(pos).first.getParent().getFileName().toString();
					UUID thisFileUID = UUID.fromString(UUIDString);

					//selectionController.selectItem(thisFileUID);
				}
			}

			//CanDropOver appears to oscillate between the nearest holders, not just the one underneath, so we can't use that
		});
		itemTouchHelper.attachToRecyclerView(recyclerView);






		CustomGridLayoutManager lm = (CustomGridLayoutManager) recyclerView.getLayoutManager();
		assert lm != null;

		DirRVAdapter.AdapterCallbacks callbacks = new DirRVAdapter.AdapterCallbacks() {
			@Override
			public boolean isItemSelected(UUID fileUID) {
				return selectionController.isSelected(fileUID);
			}

			DirRVAdapter.GalViewHolder holder;
			@Override
			public boolean onHolderMotionEvent(UUID fileUID, DirRVAdapter.GalViewHolder holder, MotionEvent event) {
				this.holder = holder;
				return detector.onTouchEvent(event);
			}

			final GestureDetector detector = new GestureDetector(recyclerView.getContext(), new GestureDetector.SimpleOnGestureListener() {
				@Override
				public void onLongPress(@NonNull MotionEvent e) {
				}

				@Override
				public boolean onDown(@NonNull MotionEvent e) {
					return true;
				}
			});
		};
		adapter.setCallbacks(callbacks);




























		if(selectionController.isSelecting()) {
			selectionToolbar.setTitle( String.valueOf(selectionController.getNumSelected()) );
			toolbar.setVisibility(View.GONE);
			selectionToolbar.setVisibility(View.VISIBLE);
		}

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

		selectionToolbar.setNavigationOnClickListener(view2 -> {
			toolbar.setVisibility(View.VISIBLE);
			selectionToolbar.setVisibility(View.GONE);

			selectionController.stopSelecting();
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
