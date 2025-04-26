package aaa.sgordon.galleryfinal.gallery;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.leinardi.android.speeddial.SpeedDialView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import aaa.sgordon.galleryfinal.MainViewModel;
import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.databinding.FragDirBinding;
import aaa.sgordon.galleryfinal.gallery.cooking.MenuItemHelper;
import aaa.sgordon.galleryfinal.gallery.cooking.ToolbarStyler;
import aaa.sgordon.galleryfinal.gallery.touch.DragSelectCallback;
import aaa.sgordon.galleryfinal.gallery.touch.ItemReorderCallback;
import aaa.sgordon.galleryfinal.gallery.touch.SelectionController;
import aaa.sgordon.galleryfinal.gallery.viewsetups.AdapterTouchSetup;
import aaa.sgordon.galleryfinal.gallery.viewsetups.FilterSetup;
import aaa.sgordon.galleryfinal.gallery.viewsetups.ReorderSetup;
import aaa.sgordon.galleryfinal.repository.gallery.caches.AttrCache;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridAPI;
import aaa.sgordon.galleryfinal.repository.hybrid.jobs.Cleanup;

public class DirFragment extends Fragment {
	public FragDirBinding binding;
	public DirectoryViewModel dirViewModel;

	private MenuItemHelper menuItemHelper;
	private SelectionController selectionController;

	private AttrCache.UpdateListener attrListener;


	private ListItem tempItemDoNotUse;
	public static DirFragment initialize(ListItem listItem) {
		DirFragment fragment = new DirFragment();
		fragment.tempItemDoNotUse = listItem;
		return fragment;
	}



	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		MainViewModel mainViewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
		System.out.println("Inside directory, Activity has been created "+mainViewModel.testInt+" times.");

		dirViewModel = new ViewModelProvider(this,
				new DirectoryViewModel.Factory(tempItemDoNotUse))
				.get(DirectoryViewModel.class);


		menuItemHelper = new MenuItemHelper();
		menuItemHelper.onCreate(this);
	}


	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		binding = FragDirBinding.inflate(inflater, container, false);
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

		binding.fab.inflate(R.menu.gallery_menu_fab);
		binding.fab.setOnActionSelectedListener(actionItem -> {
			boolean handled = menuItemHelper.onFabItemClicked(actionItem);
			if(handled) binding.fab.close();
			return handled;
		});



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
		//Note: This technically doesn't handle trashed items, but selection ends upon trashing so it doesn't matter
		dirViewModel.getFileListLiveData().observe(getViewLifecycleOwner(), list -> {
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




		FilterController filterController = new FilterController(dirViewModel.getFilterRegistry());
		filterController.addExtraQueryFilter(listItem -> {
			//Exclude hidden Directory items if the active query doesn't match their name exactly
			if(!listItem.isHidden()) return true;

			String activeQuery = filterController.registry.activeQuery.getValue();
			return listItem.getPrettyName().equalsIgnoreCase(activeQuery);
		});

		FilterSetup.setupFilters(this, filterController);

		dirViewModel.getFileListLiveData().observe(getViewLifecycleOwner(), list -> {
			//Don't compile tags and don't allow filtering for trashed items
			list = hideTrashedItems(list);


			//Grab the UUIDs of all the files in the new list for use with tagging
			//Don't include link ends, we only consider their parents
			List<UUID> fileUIDs = list.stream()
					.filter(item -> !item.type.equals(ListItem.Type.LINKEND))
					.map(item -> item.fileUID)
					.collect(Collectors.toList());


			//Don't allow filtering for collapsed items
			list = hideCollapsedItems(list);


			List<ListItem> finalList = list;
			Thread getTags = new Thread(() -> {
				//Grab all tags for each fileUID
				//TODO Expand this to include a list of files per tag
				Map<String, Set<UUID>> newTags = AttrCache.getInstance().compileTags(fileUIDs);

				dirViewModel.postFileTags(newTags);
				filterController.onListUpdated(finalList);
			});
			getTags.start();
		});
		dirViewModel.getFileTagsLiveData().observe(getViewLifecycleOwner(), filterController::onTagsUpdated);


		//-----------------------------------------------------------------------------------------

		DragSelectCallback dragSelectCallback = new DragSelectCallback(recyclerView, adapter, selectionController);
		ItemTouchHelper dragSelectHelper = new ItemTouchHelper(dragSelectCallback);
		dragSelectHelper.attachToRecyclerView(recyclerView);

		//-----------------------------------------------------------------------------------------

		ItemReorderCallback reorderCallback = ReorderSetup.setupReorderCallback(dirViewModel, recyclerView);
		ItemTouchHelper reorderHelper = new ItemTouchHelper(reorderCallback);
		reorderHelper.attachToRecyclerView(recyclerView);

		//-----------------------------------------------------------------------------------------


		//TODO This gets called twice on start/return for the same list because of the observer above
		// Brain too small rn to fix that, and it doesn't really matter
		filterController.registry.filteredList.observe(getViewLifecycleOwner(), list -> {
			adapter.setList(list);
			reorderCallback.applyReorder();
		});


		//Upon attribute updates, update relevant adapter items
		AttrCache attrCache = AttrCache.getInstance();
		attrListener = uuid -> {
			List<ListItem> adapterList = adapter.list;

			//Exclude link ends (only consider their parent link), then map each item to UUID
			List<UUID> fileUIDs = adapterList.stream()
					.filter(item -> !item.type.equals(ListItem.Type.LINKEND))
					.map(item -> item.fileUID)
					.collect(Collectors.toList());

			//If none of our files changed, leave
			if(!fileUIDs.contains(uuid)) return;


			//Update the tags
			Map<String, Set<UUID>> newTags = attrCache.compileTags(fileUIDs);
			dirViewModel.postFileTags(newTags);


			Handler handler = new Handler(Looper.getMainLooper());
			handler.post(() -> {
				//For each file in the adapter list...
				for(int i = 0; i < adapterList.size(); i++) {
					ListItem item = adapterList.get(i);

					//If the file or the file's parent (item inside a link) updated...
					if(uuid.equals(item.fileUID) || uuid.equals(item.parentUID))
						adapter.notifyItemChanged(i);	//Tell the adapter to redraw the item
				}
			});
		};
		attrCache.addListener(attrListener);




		DirRVAdapter.AdapterCallbacks adapterCallbacks = AdapterTouchSetup.setupAdapterCallbacks(this, selectionController,
				reorderCallback, dragSelectCallback, requireContext(), reorderHelper, dragSelectHelper);
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
				else if (getParentFragmentManager().getBackStackEntryCount() > 1) {
					getParentFragmentManager().popBackStack();
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
	private boolean firstTime = true;




	//Hide items that are trashed, or part of trashed links
	private List<ListItem> hideTrashedItems(List<ListItem> list) {
		List<ListItem> newList = new ArrayList<>();

		ListItem trashedLink = null;
		for(ListItem item : list) {
			//If we are working inside a trashed link...
			if (trashedLink != null) {
				//Wait until we reach the linkEnd to un-trash
				if (item.type == ListItem.Type.LINKEND && trashedLink.fileUID.equals(item.fileUID)) {
					trashedLink = null;
					continue;	//also skip the linkEnd
				}
			}

			//Skip items inside trashed links
			if(trashedLink != null)
				continue;

			//Skip trashed items
			if(item.isTrashed()) {
				if(item.isLink)
					trashedLink = item;
				continue;
			}

			newList.add(item);
		}
		return newList;
	}

	//Hide items that are part of collapsed links or dividers
	private List<ListItem> hideCollapsedItems(List<ListItem> list) {
		List<ListItem> newList = new ArrayList<>();
		ListItem collapsedItem = null;
		for(ListItem item : list) {
			if(collapsedItem != null) {
				//If the collapsed item is a link, wait until we reach the linkEnd to un-collapse
				if(collapsedItem.isLink &&
								item.type == ListItem.Type.LINKEND &&
								collapsedItem.fileUID.equals(item.fileUID))
					collapsedItem = null;

				//If the collapsed item is a Divider, wait until we reach another divider or link to un-collapse
				else if(collapsedItem.type.equals(ListItem.Type.DIVIDER) && (
								item.type.equals(ListItem.Type.LINKDIRECTORY) ||
								item.type.equals(ListItem.Type.LINKDIVIDER) ||
								item.type.equals(ListItem.Type.LINKEND) ||
								item.type.equals(ListItem.Type.DIVIDER)))
					collapsedItem = null;
			}

			if(collapsedItem != null)
				continue;

			newList.add(item);

			//If this item is a Divider, LinkDivider, or LinkDirectory and is collapsed, remember it
			if((item.isCollapsed()) && (
					item.type.equals(ListItem.Type.DIVIDER) ||
					item.type.equals(ListItem.Type.LINKDIVIDER) ||
					item.type.equals(ListItem.Type.LINKDIRECTORY)))
				collapsedItem = item;
		}
		return newList;
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
