package aaa.sgordon.galleryfinal.gallery.viewsetups;

import android.graphics.drawable.Drawable;
import android.util.Pair;
import android.view.MenuItem;
import android.view.View;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;

import java.nio.file.Path;
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
import aaa.sgordon.galleryfinal.gallery.TagFullscreen;
import aaa.sgordon.galleryfinal.gallery.touch.SelectionController;

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
				String UUIDString = adapter.list.get(pos).first.getFileName().toString();
				if(UUIDString.equals("END"))
					UUIDString = adapter.list.get(pos).first.getParent().getFileName().toString();

				return UUID.fromString(UUIDString);
			}
		};
	}



	public static void setupSelectionToolbar(DirFragment dirFragment, SelectionController selectionController) {
		FragmentDirectoryBinding binding = dirFragment.binding;

		MaterialToolbar toolbar = binding.galleryAppbar.toolbar;
		MaterialToolbar selectionToolbar = binding.galleryAppbar.selectionToolbar;

		DirRVAdapter adapter = (DirRVAdapter) binding.recyclerview.getAdapter();


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

			} else if (menuItem.getItemId() == R.id.filter) {
				View filterView = binding.galleryAppbar.filterBar.getRoot();
				if(filterView.getVisibility() == View.GONE)
					filterView.setVisibility(View.VISIBLE);
				else
					dirFragment.requireActivity().getOnBackPressedDispatcher().onBackPressed();
			} else if(menuItem.getItemId() == R.id.edit) {
				System.out.println("Edit!");
			} else if(menuItem.getItemId() == R.id.tag) {
				TagFullscreen.launch(dirFragment);
				System.out.println("Clicked tags");
			}
			else if(menuItem.getItemId() == R.id.share) {
				System.out.println("Share!");
			}
			return false;
		});

		//The filter button itself unfortunately can't just use a selector since it's in a menu so it has to be special
		FilterController fControl = dirFragment.dirViewModel.getFilterController();
		final int activeColor = ContextCompat.getColor(dirFragment.getContext(), R.color.goldenrod);
		fControl.activeQuery.observe(dirFragment.getViewLifecycleOwner(), query -> {
			boolean active = !query.isEmpty() || !fControl.activeTags.getValue().isEmpty();
			MenuItem filterItem = selectionToolbar.getMenu().findItem(R.id.filter);
			Drawable filterDrawable = filterItem.getIcon();

			if(active) DrawableCompat.setTint(filterDrawable, activeColor);
			else filterItem.setIcon(R.drawable.icon_filter);		//Reset the color to default by just resetting the icon
		});
		fControl.activeTags.observe(dirFragment.getViewLifecycleOwner(), tags -> {
			boolean active = !fControl.activeQuery.getValue().isEmpty() || !tags.isEmpty();
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
	public static void deselectAnyRemoved(List<Pair<Path, String>> newList, DirectoryViewModel dirViewModel,
										  SelectionController.SelectionCallbacks selectionCallbacks) {
		//Grab all UUIDs from the full list
		Set<UUID> inAdapter = new HashSet<>();
		for(Pair<Path, String> item : dirViewModel.fullList.getValue()) {
			String UUIDString = item.first.getFileName().toString();
			if(UUIDString.equals("END"))
				UUIDString = item.first.getParent().getFileName().toString();
			UUID itemUID = UUID.fromString(UUIDString);

			inAdapter.add(itemUID);
		}

		//Grab all UUIDs from the new list
		Set<UUID> inNewList = new HashSet<>();
		for(Pair<Path, String> item : newList) {
			String UUIDString = item.first.getFileName().toString();
			if(UUIDString.equals("END"))
				UUIDString = item.first.getParent().getFileName().toString();
			UUID itemUID = UUID.fromString(UUIDString);

			inNewList.add(itemUID);
		}

		//Directly deselect any missing items (no need to worry about visuals, the items don't exist anymore)
		inAdapter.removeAll(inNewList);
		for(UUID itemUID : inAdapter)
			dirViewModel.getSelectionRegistry().deselectItem(itemUID);

		selectionCallbacks.onNumSelectedChanged(dirViewModel.getSelectionRegistry().getNumSelected());
	}
}
