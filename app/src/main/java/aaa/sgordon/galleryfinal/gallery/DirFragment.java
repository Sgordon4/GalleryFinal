package aaa.sgordon.galleryfinal.gallery;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.leinardi.android.speeddial.SpeedDialView;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import aaa.sgordon.galleryfinal.MainViewModel;
import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.databinding.FragmentDirectoryBinding;
import aaa.sgordon.galleryfinal.gallery.modals.MoveCopyFullscreen;
import aaa.sgordon.galleryfinal.gallery.touch.DragSelectCallback;
import aaa.sgordon.galleryfinal.gallery.touch.ItemReorderCallback;
import aaa.sgordon.galleryfinal.gallery.touch.SelectionController;
import aaa.sgordon.galleryfinal.gallery.viewsetups.AdapterTouchSetup;
import aaa.sgordon.galleryfinal.gallery.viewsetups.FilterSetup;
import aaa.sgordon.galleryfinal.gallery.viewsetups.ReorderSetup;
import aaa.sgordon.galleryfinal.gallery.viewsetups.SelectionSetup;

public class DirFragment extends Fragment {
	public FragmentDirectoryBinding binding;
	public DirectoryViewModel dirViewModel;

	ActivityResultLauncher<Intent> filePickerLauncher;

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

		filePickerLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
			if (result.getResultCode() == Activity.RESULT_OK) {
				List<Uri> uris = ImportHelper.getUrisFromIntent(result.getData());
				Map<Uri, DocumentFile> fileInfo = ImportHelper.getFileInfoForUris(getContext(), uris);

				Thread importThread = new Thread(() -> {
					ImportHelper.importFiles(getContext(), dirViewModel.getDirUID(), uris, fileInfo);
				});
				importThread.start();
			}
		});
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
			if (item.getItemId() == R.id.filter) {
				View filterView = binding.galleryAppbar.filterBar.getRoot();
				if(filterView.getVisibility() == View.GONE)
					filterView.setVisibility(View.VISIBLE);
				else
					requireActivity().getOnBackPressedDispatcher().onBackPressed();
			}
			//TODO Add fab options


			else if (item.getItemId() == R.id.settings) {
				System.out.println("Clicked settings");

				//This is for testing, settings should not launch this
				Path pathFromRootButNotReally = Paths.get(dirViewModel.getDirUID().toString());
				MoveCopyFullscreen.launch(this, pathFromRootButNotReally);
			}
			return false;
		});


		FilterController filterController = new FilterController(dirViewModel.getFilterRegistry(), dirViewModel.getAttrCache());

		FilterSetup.setupFilters(this, filterController);

		dirViewModel.fileList.observe(getViewLifecycleOwner(), list -> {
			filterController.onListUpdated(list);
		});
		dirViewModel.fileTags.observe(getViewLifecycleOwner(), tags -> {
			filterController.onTagsUpdated(tags);
		});


		//-----------------------------------------------------------------------------------------

		SelectionController.SelectionCallbacks selectionCallbacks = SelectionSetup.makeSelectionCallbacks(toolbar, selectionToolbar, recyclerView);
		SelectionController selectionController = new SelectionController(dirViewModel.getSelectionRegistry(), selectionCallbacks);

		SelectionSetup.setupSelectionToolbar(this, selectionController);

		dirViewModel.fileList.observe(getViewLifecycleOwner(), list -> {
			if(selectionController.isSelecting())
				SelectionSetup.deselectAnyRemoved(list, dirViewModel, selectionCallbacks);
		});

		//-----------------------------------------------------------------------------------------

		DragSelectCallback dragSelectCallback = new DragSelectCallback(recyclerView, adapter, selectionController);
		ItemTouchHelper dragSelectHelper = new ItemTouchHelper(dragSelectCallback);
		dragSelectHelper.attachToRecyclerView(recyclerView);

		//-----------------------------------------------------------------------------------------

		ItemReorderCallback reorderCallback = ReorderSetup.setupReorderCallback(dirViewModel, recyclerView);
		ItemTouchHelper reorderHelper = new ItemTouchHelper(reorderCallback);
		reorderHelper.attachToRecyclerView(recyclerView);

		//-----------------------------------------------------------------------------------------

		filterController.registry.filteredList.observe(getViewLifecycleOwner(), list -> {
			adapter.setList(list);
			reorderCallback.applyReorder();
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


		binding.fab.inflate(R.menu.gallery_menu_fab);

		binding.fab.setOnActionSelectedListener(actionItem -> {
			if (actionItem.getId() == R.id.new_item) {
				System.out.println("Clicked new item");
			}
			else if (actionItem.getId() == R.id.import_image) {
				System.out.println("Clicked import image");

				//Launch the file picker intent
				Intent filePicker = new Intent(Intent.ACTION_GET_CONTENT);
				//filePicker.setType("image/*");
				filePicker.setType("*/*");
				filePicker.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
				filePicker = Intent.createChooser(filePicker, "Select Items to Import");

				filePickerLauncher.launch(filePicker);
			}
			else if (actionItem.getId() == R.id.take_photo) {
				System.out.println("Clicked take photo");
			}
			binding.fab.close();
			return true;
		});



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
