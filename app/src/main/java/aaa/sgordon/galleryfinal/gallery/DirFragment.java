package aaa.sgordon.galleryfinal.gallery;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.SharedElementCallback;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.MaterialToolbar;
import com.leinardi.android.speeddial.SpeedDialView;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import aaa.sgordon.galleryfinal.MainViewModel;
import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.databinding.FragDirBinding;
import aaa.sgordon.galleryfinal.gallery.components.properties.NewItemModal;
import aaa.sgordon.galleryfinal.gallery.cooking.MenuItemHelper;
import aaa.sgordon.galleryfinal.gallery.cooking.ToolbarStyler;
import aaa.sgordon.galleryfinal.gallery.touch.DragSelectCallback;
import aaa.sgordon.galleryfinal.gallery.touch.ItemReorderCallback;
import aaa.sgordon.galleryfinal.gallery.touch.SelectionController;
import aaa.sgordon.galleryfinal.gallery.viewsetups.AdapterTouchSetup;
import aaa.sgordon.galleryfinal.gallery.viewsetups.FilterSetup;
import aaa.sgordon.galleryfinal.gallery.viewsetups.ReorderSetup;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridAPI;
import aaa.sgordon.galleryfinal.repository.hybrid.jobs.Cleanup;

public class DirFragment extends Fragment {
	public FragDirBinding binding;
	public DirectoryViewModel dirViewModel;

	public ActivityResultLauncher<Intent> filePickerLauncher;

	private MenuItemHelper menuItemHelper;
	private SelectionController selectionController;

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		MainViewModel mainViewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
		System.out.println("Inside directory, Activity has been created "+mainViewModel.testInt+" times.");

		DirFragmentArgs args = DirFragmentArgs.fromBundle(getArguments());
		UUID directoryUID = args.getDirectoryUID();
		dirViewModel = new ViewModelProvider(this,
				new DirectoryViewModel.Factory(directoryUID))
				.get(DirectoryViewModel.class);


		menuItemHelper = new MenuItemHelper();
		menuItemHelper.onCreate(this);


		filePickerLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
			if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
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
		binding = FragDirBinding.inflate(inflater, container, false);

		setExitSharedElementCallback(new SharedElementCallback() {
			@Override
			public void onMapSharedElements(List<String> names, Map<String, View> sharedElements) {
				if(names.isEmpty()) return;

				ViewGroup item = (ViewGroup) binding.recyclerview.getChildAt(0);
				View media = item.findViewById(R.id.media);

				sharedElements.put(names.get(0), media);
			}
		});


		return binding.getRoot();
	}

	@SuppressLint("ClickableViewAccessibility")
	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);


		//Utilities.showColorDebugOverlay(binding.galleryAppbar.getRoot(), requireContext());

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
		//recyclerView.addItemDecoration(new RightSpaceDecoration(Color.RED));

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
		toolbar.setOnMenuItemClickListener(menuItemHelper::onMainItemClicked);

		MaterialToolbar selectionToolbar = binding.galleryAppbar.selectionToolbar;
		selectionToolbar.setOnMenuItemClickListener(menuItemHelper::onSelectionItemClicked);



		ToolbarStyler toolbarStyler = new ToolbarStyler();

		SelectionController.SelectionCallbacks selectionCallbacks = new SelectionController.SelectionCallbacks() {
			@Override
			public void onSelectionStarted() {
				toolbarStyler.onSelectionStarted();
			}
			@Override
			public void onSelectionStopped() {
				toolbarStyler.onSelectionStopped();
			}
			@Override
			public void onNumSelectedChanged(int numSelected) {
				toolbarStyler.onNumSelectedChanged(numSelected);
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

						DirRVAdapter adapter = (DirRVAdapter) recyclerView.getAdapter();
						if(fileUID.equals( adapter.list.get(adapterPos).fileUID ) )
							itemView.setSelected(isSelected);
					}
				}
			}
		};
		selectionController = new SelectionController(dirViewModel.getSelectionRegistry(), selectionCallbacks);

		toolbarStyler.onViewCreated(this, selectionController);

		menuItemHelper.onViewCreated(adapter, selectionController);




		//Deselect any items that were removed from the list
		dirViewModel.fileList.observe(getViewLifecycleOwner(), list -> {
			System.out.println("Trimming items");
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
					dirViewModel.getSelectionRegistry().deselectItem(itemUID);

				selectionCallbacks.onNumSelectedChanged(dirViewModel.getSelectionRegistry().getNumSelected());
			}
		});

		//-----------------------------------------------------------------------------------------




		FilterController filterController = new FilterController(dirViewModel.getFilterRegistry(), dirViewModel.getAttrCache());

		FilterSetup.setupFilters(this, filterController);

		dirViewModel.fileList.observe(getViewLifecycleOwner(), list -> {
			filterController.onListUpdated(list);
		});
		dirViewModel.fileTags.observe(getViewLifecycleOwner(), tags -> {
			filterController.onTagsUpdated(tags);
		});


		//-----------------------------------------------------------------------------------------

		DragSelectCallback dragSelectCallback = new DragSelectCallback(recyclerView, adapter, selectionController);
		ItemTouchHelper dragSelectHelper = new ItemTouchHelper(dragSelectCallback);
		//dragSelectHelper.attachToRecyclerView(recyclerView);

		//-----------------------------------------------------------------------------------------

		ItemReorderCallback reorderCallback = ReorderSetup.setupReorderCallback(dirViewModel, recyclerView);
		ItemTouchHelper reorderHelper = new ItemTouchHelper(reorderCallback);
		reorderHelper.attachToRecyclerView(recyclerView);

		//-----------------------------------------------------------------------------------------

		filterController.registry.filteredList.observe(getViewLifecycleOwner(), list -> {
			adapter.setList(list);
			reorderCallback.applyReorder();
		});





		NavController navController = Navigation.findNavController(view);
		DirRVAdapter.AdapterCallbacks adapterCallbacks = AdapterTouchSetup.setupAdapterCallbacks(this, selectionController,
				reorderCallback, dragSelectCallback, requireContext(), reorderHelper, dragSelectHelper, navController);
		adapter.setCallbacks(adapterCallbacks);




		recyclerView.setOnTouchListener((view3, motionEvent) -> {
			reorderCallback.onMotionEvent(motionEvent);
			dragSelectCallback.onMotionEvent(motionEvent);
			return false;
		});




		requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
			@Override
			public void handleOnBackPressed() {
				//If we have a child fragment, close that first
				if (getChildFragmentManager().getBackStackEntryCount() > 0) {
					getChildFragmentManager().popBackStack();
				}

				//If the filter menu is open, close that first
				else if(binding.galleryAppbar.filterBar.getRoot().getVisibility() == View.VISIBLE) {
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
			Thread thread = new Thread(() -> {
				HybridAPI hAPI = HybridAPI.getInstance();
				Cleanup.cleanOrphanContent(hAPI.tempExposedDB.getContentDao());
				Cleanup.cleanSyncedTempFiles(hAPI.tempExposedDB.getFileDao(), hAPI.tempExposedDB.getJournalDao(),
						HybridAPI.getInstance().getCurrentAccount(), 60);
			});
			thread.start();
		});


		binding.fab.inflate(R.menu.gallery_menu_fab);

		binding.fab.setOnActionSelectedListener(actionItem -> {
			if (actionItem.getId() == R.id.new_item) {
				System.out.println("Clicked new item");
				NewItemModal.launch(this);
			}
			else if (actionItem.getId() == R.id.import_image) {
				System.out.println("Clicked import image");

				//Launch the file picker intent
				Intent filePicker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
				//filePicker.setType("image/*");
				filePicker.setType("*/*");
				filePicker.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
				filePicker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
				filePicker = Intent.createChooser(filePicker, "Select Items to Import");

				filePickerLauncher.launch(filePicker);
			}
			else if (actionItem.getId() == R.id.take_photo) {
				System.out.println("Clicked take photo");
				Toast.makeText(requireContext(), "No worky :)", Toast.LENGTH_SHORT).show();
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
