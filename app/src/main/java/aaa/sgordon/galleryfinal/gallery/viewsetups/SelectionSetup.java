package aaa.sgordon.galleryfinal.gallery.viewsetups;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.databinding.FragmentDirectoryBinding;
import aaa.sgordon.galleryfinal.gallery.DirFragment;
import aaa.sgordon.galleryfinal.gallery.DirRVAdapter;
import aaa.sgordon.galleryfinal.gallery.DirectoryViewModel;
import aaa.sgordon.galleryfinal.gallery.FilterController;
import aaa.sgordon.galleryfinal.gallery.ListItem;
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
				Set<UUID> toSelect = new HashSet<>();
				for(ListItem item : adapter.list) {
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

				Path pathFromRootButNotReally = Paths.get(dirFragment.dirViewModel.getDirUID().toString());
				MoveCopyFullscreen.launch(dirFragment, pathFromRootButNotReally, destinationUID -> {


					//Get the selected items
					List<ListItem> toMove = getSelected(dirFragment, selectionController);

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

			else if(menuItem.getItemId() == R.id.export) {
				System.out.println("Export!");
			}

			else if(menuItem.getItemId() == R.id.trash) {
				//Get the selected items
				List<ListItem> toTrash = getSelected(dirFragment, selectionController);

				//Update each item's name with a 'trashed' suffix
				String suffix = ".trashed_"+Instant.now().getEpochSecond();
				List<ListItem> renamed = new ArrayList<>();
				for(ListItem item : toTrash) {
					renamed.add(new ListItem.Builder(item)
							.setName(item.name + suffix)
							.build());
				}

				//And 'trash' them
				Thread trashThread = new Thread(() -> {
					DirUtilities.renameFiles(renamed);
				});
				trashThread.start();

				selectionController.stopSelecting();
			}

			else if(menuItem.getItemId() == R.id.share) {
				System.out.println("Share!");
				/*
				HybridAPI hAPI = HybridAPI.getInstance();

				//Get the selected items
				List<ListItem> toShare = getSelected(dirFragment, selectionController);


				Thread share = new Thread(() -> {
					ArrayList<Uri> uris = new ArrayList<>();
					for(ListItem item : toShare) {
						try {
							Uri uri = hAPI.getFileContent(item.fileUID).first;
							uris.add(uri);
						} catch (FileNotFoundException | ContentsNotFoundException e) {
							Toast.makeText(dirFragment.getContext(), "Could not find file contents!", Toast.LENGTH_SHORT).show();
						} catch (ConnectException e) {
							Toast.makeText(dirFragment.getContext(), "Could not find file contents on device!", Toast.LENGTH_SHORT).show();
						}
					}
					if(uris.isEmpty()) {
						Toast.makeText(dirFragment.getContext(), "No files to share!", Toast.LENGTH_SHORT).show();
						return;
					}


					Handler handler = new Handler(dirFragment.getActivity().getMainLooper());
					handler.post(() -> {
						Intent shareIntent = new Intent(Intent.ACTION_SEND_MULTIPLE);
						//shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
						shareIntent.setType("* /*");  //If use, remove space. No space interrupts the comment

						//Grant permission to other apps to read the files
						for (Uri uri : uris) {
							dirFragment.getActivity().getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
						}

						//Add URIs to the intent
						shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);

						//Start the share intent
						dirFragment.startActivity(Intent.createChooser(shareIntent, "Share files"));
					});
				});
				share.start();
				*/

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



	private static List<ListItem> getSelected(DirFragment dirFragment, SelectionController selectionController) {
		//Get the selected items from the viewModel's list
		Set<UUID> selectedItems = new HashSet<>(selectionController.getSelectedList());

		//Grab the first instance of each selected item in the list
		List<ListItem> selected = new ArrayList<>();
		List<ListItem> currList = dirFragment.dirViewModel.getFilterRegistry().filteredList.getValue();
		for(int i = 0; i < currList.size(); i++) {
			UUID itemUID = currList.get(i).fileUID;

			if(selectedItems.contains(itemUID)) {
				selected.add(currList.get(i));
				selectedItems.remove(itemUID);
			}
		}

		return selected;
	}



	//Unselect any items that are no longer in the list
	//Note: This is mostly untested
	public static void deselectAnyRemoved(List<ListItem> newList, DirectoryViewModel dirViewModel,
										  SelectionController.SelectionCallbacks selectionCallbacks) {
		//Grab all UUIDs from the full list
		Set<UUID> inAdapter = new HashSet<>();
		for(ListItem item : dirViewModel.fileList.getValue()) {
			inAdapter.add(item.fileUID);
		}

		//Grab all UUIDs from the new list
		Set<UUID> inNewList = new HashSet<>();
		for(ListItem item : newList) {
			inNewList.add(item.fileUID);
		}

		//Directly deselect any missing items (no need to worry about visuals, the items don't exist anymore)
		inAdapter.removeAll(inNewList);
		for(UUID itemUID : inAdapter)
			dirViewModel.getSelectionRegistry().deselectItem(itemUID);

		selectionCallbacks.onNumSelectedChanged(dirViewModel.getSelectionRegistry().getNumSelected());
	}
}
