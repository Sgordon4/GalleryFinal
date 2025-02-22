package aaa.sgordon.galleryfinal.gallery;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.leinardi.android.speeddial.SpeedDialView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import aaa.sgordon.galleryfinal.MainViewModel;
import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.databinding.FragmentDirectoryBinding;
import aaa.sgordon.galleryfinal.gallery.viewsetups.AdapterTouchSetup;
import aaa.sgordon.galleryfinal.gallery.touch.DragSelectCallback;
import aaa.sgordon.galleryfinal.gallery.touch.ItemReorderCallback;
import aaa.sgordon.galleryfinal.gallery.viewsetups.FilterSetup;
import aaa.sgordon.galleryfinal.gallery.viewsetups.ReorderSetup;
import aaa.sgordon.galleryfinal.gallery.touch.SelectionController;
import aaa.sgordon.galleryfinal.gallery.viewsetups.SelectionSetup;

public class DirFragment extends Fragment {
	public FragmentDirectoryBinding binding;
	public DirectoryViewModel dirViewModel;

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


		postponeEnterTransition(); // Pause the transition
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
		toolbar.setNavigationOnClickListener(view4 -> navController.popBackStack());

		//Hide the navigation icon when we're at the top-level
		navController.addOnDestinationChangedListener((navController1, navDestination, bundle) -> {
			if(navController1.getPreviousBackStackEntry() == null)
				toolbar.setNavigationIcon(null);
		});

		//Must set title after configuration
		DirFragmentArgs args = DirFragmentArgs.fromBundle(getArguments());
		String directoryName = args.getDirectoryName();
		toolbar.setTitle(directoryName);



		toolbar.setOnMenuItemClickListener(item -> {
			if (item.getItemId() == R.id.gallery_filter) {
				View filterView = binding.galleryAppbar.filterBar.getRoot();
				if(filterView.getVisibility() == View.GONE)
					filterView.setVisibility(View.VISIBLE);
				else
					requireActivity().getOnBackPressedDispatcher().onBackPressed();
			}
			else if (item.getItemId() == R.id.gallery_tag) {
				System.out.println("Clicked tags");
			}
			else if (item.getItemId() == R.id.action_settings) {
				System.out.println("Clicked settings");
			}
			return false;
		});


		FilterSetup.setupFilters(this);

		//-----------------------------------------------------------------------------------------

		SelectionController.SelectionCallbacks selectionCallbacks = SelectionSetup.makeSelectionCallbacks(toolbar, selectionToolbar, recyclerView);
		SelectionController selectionController = new SelectionController(dirViewModel.getSelectionRegistry(), selectionCallbacks);

		SelectionSetup.setupSelectionToolbar(toolbar, selectionToolbar, adapter, selectionController);

		//-----------------------------------------------------------------------------------------

		DragSelectCallback dragSelectCallback = new DragSelectCallback(recyclerView, adapter, selectionController);
		ItemTouchHelper dragSelectHelper = new ItemTouchHelper(dragSelectCallback);
		dragSelectHelper.attachToRecyclerView(recyclerView);

		//-----------------------------------------------------------------------------------------

		ItemReorderCallback reorderCallback = ReorderSetup.setupReorderCallback(dirViewModel, recyclerView);
		ItemTouchHelper reorderHelper = new ItemTouchHelper(reorderCallback);
		reorderHelper.attachToRecyclerView(recyclerView);

		//-----------------------------------------------------------------------------------------


		dirViewModel.fullList.observe(getViewLifecycleOwner(), list -> {
			if(selectionController.isSelecting())
				SelectionSetup.deselectAnyRemoved(list, dirViewModel, selectionCallbacks);
		});
		dirViewModel.filteredList.observe(getViewLifecycleOwner(), list -> {
			adapter.setList(list);
			reorderCallback.applyReorder();
		});


		dirViewModel.activeTags.observe(getViewLifecycleOwner(), tags -> {
			dirViewModel.onActiveTagsChanged();

			ChipGroup chipGroup = binding.galleryAppbar.filterBar.chipGroup;
			//Make sure each chip is checked/unchecked based on the active tags, which can be updated in the background
			for(int i = 0; i < chipGroup.getChildCount(); i++) {
				Chip chip = (Chip) chipGroup.getChildAt(i);

				boolean isActive = tags.contains(chip.getText().toString());
				chip.setChecked(isActive);
			}
		});


		dirViewModel.filteredTags.observe(getViewLifecycleOwner(), tags -> {
			ChipGroup chipGroup = binding.galleryAppbar.filterBar.chipGroup;

			if(tags.isEmpty()) {
				chipGroup.removeAllViews();
				Chip noTags = (Chip) getLayoutInflater().inflate(R.layout.dir_tag_chip, chipGroup, false);
				noTags.setText("No tags");
				noTags.setTextColor(Color.GRAY);
				noTags.setClickable(false);
				noTags.setCheckable(false);
				chipGroup.addView(noTags);
				return;
			}


			//List<String> sortedTags = tags.stream().sorted().collect(Collectors.toList());
			List<String> sortedTags = tags.stream().sorted((a, b) -> {
				//Check if the items are active
				Set<String> activeTags = dirViewModel.activeTags.getValue();
				boolean isActive_A = activeTags.contains(a);
				boolean isActive_B = activeTags.contains(b);

				//If a is active but b is not, a comes before b
				if (isActive_A && !isActive_B) return -1;
				if (!isActive_A && isActive_B) return 1;

				//If both are active or both are not, sort alphabetically
				return a.compareTo(b);
			}).collect(Collectors.toList());

			//Grab the currently displayed tags
			List<String> currentTags = new ArrayList<>();
			for(int i = 0; i < chipGroup.getChildCount(); i++) {
				Chip chip = (Chip) chipGroup.getChildAt(i);
				currentTags.add(chip.getText().toString());
			}

			//If there are no visible changes, do nothing
			if(sortedTags.equals(currentTags))
				return;

			//Create a new list of chips to display based on the sorted tags
			List<Chip> chips = new ArrayList<>();
			for(String tag : sortedTags) {
				Chip chip = (Chip) getLayoutInflater().inflate(R.layout.dir_tag_chip, chipGroup, false);
				chip.setText(tag);

				if(dirViewModel.activeTags.getValue().contains(tag)) {
					chip.setChecked(true);
				}

				chip.setOnClickListener(view2 -> {
					Set<String> activeTags = dirViewModel.activeTags.getValue();
					boolean isChecked = activeTags.contains(tag);
					if(isChecked)
						activeTags.remove(tag);
					else
						activeTags.add(tag);

					dirViewModel.activeTags.postValue(activeTags);
				});

				chips.add(chip);
			}

			chipGroup.removeAllViews();
			for(Chip chip : chips)
				chipGroup.addView(chip);
		});



		DirRVAdapter.AdapterCallbacks adapterCallbacks = AdapterTouchSetup.setupAdapterCallbacks(dirViewModel, selectionController,
				reorderCallback, dragSelectCallback, getContext(), reorderHelper, dragSelectHelper, navController);
		adapter.setCallbacks(adapterCallbacks);




		recyclerView.setOnTouchListener((view3, motionEvent) -> {
			reorderCallback.onMotionEvent(motionEvent);
			dragSelectCallback.onMotionEvent(motionEvent);
			return false;
		});







		selectionToolbar.setNavigationOnClickListener(view2 -> {
			selectionController.stopSelecting();
		});

		requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
			@Override
			public void handleOnBackPressed() {
				//If the filter menu is open, close that first
				if(binding.galleryAppbar.filterBar.getRoot().getVisibility() == View.VISIBLE) {
					binding.galleryAppbar.filterBar.getRoot().setVisibility(View.GONE);

					//Hide the keyboard
					EditText search = binding.galleryAppbar.filterBar.search;
					InputMethodManager imm = (InputMethodManager) requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
					imm.hideSoftInputFromWindow(search.getWindowToken(), 0);
				}
				//If we're selecting, stop selection mode rather than leaving
				else if(selectionController.isSelecting()) {
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
		setExitSharedElementCallback(null);
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
