package aaa.sgordon.galleryfinal.gallery.viewsetups;

import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.util.Pair;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.apache.commons.io.FilenameUtils;

import java.io.FileNotFoundException;
import java.net.ConnectException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.databinding.FragmentDirectoryBinding;
import aaa.sgordon.galleryfinal.gallery.DirFragment;
import aaa.sgordon.galleryfinal.gallery.DirRVAdapter;
import aaa.sgordon.galleryfinal.gallery.DirectoryViewModel;
import aaa.sgordon.galleryfinal.gallery.FilterController;
import aaa.sgordon.galleryfinal.gallery.TraversalHelper;
import aaa.sgordon.galleryfinal.gallery.modals.EditItemModal;
import aaa.sgordon.galleryfinal.gallery.modals.MoveCopyFullscreen;
import aaa.sgordon.galleryfinal.gallery.modals.TagFullscreen;
import aaa.sgordon.galleryfinal.gallery.touch.SelectionController;
import aaa.sgordon.galleryfinal.repository.caches.DirCache;
import aaa.sgordon.galleryfinal.repository.caches.LinkCache;
import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridAPI;
import aaa.sgordon.galleryfinal.repository.hybrid.types.HFile;
import aaa.sgordon.galleryfinal.utilities.DirUtilities;
import aaa.sgordon.galleryfinal.utilities.Utilities;

public class SelectionSetup {

	public static SelectionController.SelectionCallbacks makeSelectionCallbacks(MaterialToolbar toolbar, MaterialToolbar selectionToolbar,
																				RecyclerView recyclerView) {
		DirRVAdapter adapter = (DirRVAdapter) recyclerView.getAdapter();
		return new SelectionController.SelectionCallbacks() {
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
				selectionToolbar.getMenu().findItem(R.id.edit).setEnabled(numSelected == 1);	//Disable edit button unless only one item is selected
				selectionToolbar.getMenu().findItem(R.id.tag).setEnabled(numSelected >= 1);		//Disable tag button unless one or more items are selected
				selectionToolbar.getMenu().findItem(R.id.share).setEnabled(numSelected >= 1);	//Disable share button unless one or more items are selected
			}

			@Override
			public UUID getUUIDAtPos(int pos) {
				return adapter.list.get(pos).fileUID;
			}
		};
	}



	public static void setupSelectionToolbar(DirFragment dirFragment, SelectionController selectionController) {
		FragmentDirectoryBinding binding = dirFragment.binding;

		MaterialToolbar toolbar = binding.galleryAppbar.toolbar;
		MaterialToolbar selectionToolbar = binding.galleryAppbar.selectionToolbar;

		DirRVAdapter adapter = (DirRVAdapter) binding.recyclerview.getAdapter();


		selectionToolbar.setOnMenuItemClickListener(menuItem -> {
			if(menuItem.getItemId() == R.id.select_all) {
				System.out.println("Select all!");
				Set<UUID> toSelect = new HashSet<>();
				for(TraversalHelper.ListItem item : adapter.list) {
					UUID itemUID = item.fileUID;

					toSelect.add(itemUID);
				}

				//If all items are currently selected, we want to deselect all instead
				Set<UUID> currSelected = selectionController.getSelectedList();
				toSelect.removeAll(currSelected);
				if(!toSelect.isEmpty())
					selectionController.selectAll(toSelect);
				else
					selectionController.deselectAll();
			}

			else if (menuItem.getItemId() == R.id.filter) {
				View filterView = binding.galleryAppbar.filterBar.getRoot();
				if(filterView.getVisibility() == View.GONE)
					filterView.setVisibility(View.VISIBLE);
				else
					dirFragment.requireActivity().getOnBackPressedDispatcher().onBackPressed();
			}

			else if(menuItem.getItemId() == R.id.edit) {
				System.out.println("Edit!");
				return EditItemModal.launchHelper(dirFragment, selectionController, adapter.list);
			}

			else if(menuItem.getItemId() == R.id.tag) {
				System.out.println("Clicked tags");
				TagFullscreen.launch(dirFragment);
			}

			else if(menuItem.getItemId() == R.id.move || menuItem.getItemId() == R.id.copy) {
				boolean isMove = menuItem.getItemId() == R.id.move;
				System.out.println("Move/Copy: "+(isMove ? "move" : "copy"));

				Path pathFromRootButNotReally = Paths.get(dirFragment.dirViewModel.getDirUID().toString());
				MoveCopyFullscreen.launch(dirFragment, pathFromRootButNotReally, destinationUID -> {

					System.out.println("Confirmed with destination "+destinationUID);

					//Get the selected items from the viewModel's list and pass them along
					Set<UUID> selectedItems = new HashSet<>(selectionController.getSelectedList());
					List<TraversalHelper.ListItem> toMove = new ArrayList<>();

					//Grab the first instance of each selected item in the list
					List<TraversalHelper.ListItem> currList = dirFragment.dirViewModel.getFilterRegistry().filteredList.getValue();
					for(int i = 0; i < currList.size(); i++) {
						UUID itemUID = currList.get(i).fileUID;

						if(selectedItems.contains(itemUID)) {
							toMove.add(currList.get(i));
							selectedItems.remove(itemUID);
						}
					}


					Thread operation = new Thread(() -> {
						try {
							//Passing a fake item to move/copy will place the items at the start when the function can't find it
							UUID fakeItem = UUID.randomUUID();

							//Given the move/copy destination, perform the move/copy
							if(isMove)
								DirUtilities.moveFiles(toMove, destinationUID, fakeItem);
							else /*(isCopy)*/
								DirUtilities.copyFiles(toMove, destinationUID, fakeItem);
						} catch (FileNotFoundException | ContentsNotFoundException | ConnectException | NotDirectoryException e) {
							throw new RuntimeException(e);
						}
					});
					operation.start();
				});
			}

			else if(menuItem.getItemId() == R.id.share) {
				System.out.println("Share!");
			}
			return false;
		});

		//The filter button itself unfortunately can't just use a selector since it's in a menu so it has to be special
		FilterController.FilterRegistry fregistry = dirFragment.dirViewModel.getFilterRegistry();
		final int activeColor = ContextCompat.getColor(dirFragment.getContext(), R.color.goldenrod);
		fregistry.activeQuery.observe(dirFragment.getViewLifecycleOwner(), query -> {
			boolean active = !query.isEmpty() || !fregistry.activeTags.getValue().isEmpty();
			MenuItem filterItem = selectionToolbar.getMenu().findItem(R.id.filter);
			Drawable filterDrawable = filterItem.getIcon();

			if(active) DrawableCompat.setTint(filterDrawable, activeColor);
			else filterItem.setIcon(R.drawable.icon_filter);		//Reset the color to default by just resetting the icon
		});
		fregistry.activeTags.observe(dirFragment.getViewLifecycleOwner(), tags -> {
			boolean active = !fregistry.activeQuery.getValue().isEmpty() || !tags.isEmpty();
			MenuItem filterItem = selectionToolbar.getMenu().findItem(R.id.filter);
			Drawable filterDrawable = filterItem.getIcon();

			if(active) DrawableCompat.setTint(filterDrawable, activeColor);
			else filterItem.setIcon(R.drawable.icon_filter);		//Reset the color to default by just resetting the icon
		});


		if(selectionController.isSelecting()) {
			selectionToolbar.setTitle( String.valueOf(selectionController.getNumSelected()) );
			toolbar.setVisibility(View.GONE);
			selectionToolbar.setVisibility(View.VISIBLE);
		}
	}



	//Unselect any items that are no longer in the list
	//Note: This is mostly untested
	public static void deselectAnyRemoved(List<TraversalHelper.ListItem> newList, DirectoryViewModel dirViewModel,
										  SelectionController.SelectionCallbacks selectionCallbacks) {
		//Grab all UUIDs from the full list
		Set<UUID> inAdapter = new HashSet<>();
		for(TraversalHelper.ListItem item : dirViewModel.fileList.getValue()) {
			inAdapter.add(item.fileUID);
		}

		//Grab all UUIDs from the new list
		Set<UUID> inNewList = new HashSet<>();
		for(TraversalHelper.ListItem item : newList) {
			inNewList.add(item.fileUID);
		}

		//Directly deselect any missing items (no need to worry about visuals, the items don't exist anymore)
		inAdapter.removeAll(inNewList);
		for(UUID itemUID : inAdapter)
			dirViewModel.getSelectionRegistry().deselectItem(itemUID);

		selectionCallbacks.onNumSelectedChanged(dirViewModel.getSelectionRegistry().getNumSelected());
	}
}
