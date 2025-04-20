package aaa.sgordon.galleryfinal.gallery.cooking;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Pair;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;

import com.google.gson.JsonObject;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ConnectException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.databinding.FragDirBinding;
import aaa.sgordon.galleryfinal.gallery.DirFragment;
import aaa.sgordon.galleryfinal.gallery.DirRVAdapter;
import aaa.sgordon.galleryfinal.gallery.ListItem;
import aaa.sgordon.galleryfinal.gallery.components.filter.TagFullscreen;
import aaa.sgordon.galleryfinal.gallery.components.modals.MoveCopyFullscreen;
import aaa.sgordon.galleryfinal.gallery.components.modals.ZoningModal;
import aaa.sgordon.galleryfinal.gallery.components.properties.EditItemModal;
import aaa.sgordon.galleryfinal.gallery.components.properties.SettingsFragment;
import aaa.sgordon.galleryfinal.gallery.components.trash.TrashFullscreen;
import aaa.sgordon.galleryfinal.gallery.touch.SelectionController;
import aaa.sgordon.galleryfinal.repository.caches.AttrCache;
import aaa.sgordon.galleryfinal.repository.caches.DirCache;
import aaa.sgordon.galleryfinal.repository.caches.LinkCache;
import aaa.sgordon.galleryfinal.repository.galleryhelpers.ExportStorageHandler;
import aaa.sgordon.galleryfinal.repository.galleryhelpers.SAFGoFuckYourself;
import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;
import aaa.sgordon.galleryfinal.utilities.DirUtilities;

public class MenuItemHelper {
	private DirFragment dirFragment;
	private ActivityResultLauncher<Intent> exportPickerLauncher;

	//TODO Move SelectionController definition to onCreate in DirFragment
	private DirRVAdapter adapter;
	private SelectionController selectionController;


	public void onCreate(DirFragment dirFragment) {
		this.dirFragment = dirFragment;

		exportPickerLauncher = dirFragment.registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
			ExportStorageHandler.onStorageLocationPicked(dirFragment.requireActivity(), result);
			onExport();
		});
	}

	public void onViewCreated(DirRVAdapter adapter, SelectionController selectionController) {
		this.adapter = adapter;
		this.selectionController = selectionController;
	}


	//---------------------------------------------------------------------------------------------


	public boolean onMainItemClicked(MenuItem menuItem) {
		if (menuItem.getItemId() == R.id.filter) {
			onFilter();
			return true;
		}
		else if (menuItem.getItemId() == R.id.trashed) {
			TrashFullscreen.launch(dirFragment, dirFragment.dirViewModel.getDirUID());
			return true;
		}
		else if (menuItem.getItemId() == R.id.settings) {
			System.out.println("Clicked settings");
			onSettings();
			return true;
		}
		return false;
	}


	//---------------------------------------------------------------------------------------------


	public boolean onSelectionItemClicked(MenuItem menuItem) {
		if(menuItem.getItemId() == R.id.select_all) {
			onSelectAll();
			return true;
		}
		else if (menuItem.getItemId() == R.id.filter) {
			onFilter();
			return true;
		}
		else if(menuItem.getItemId() == R.id.edit) {
			return EditItemModal.launchHelper(dirFragment, selectionController, adapter.list);
		}
		else if(menuItem.getItemId() == R.id.tag) {
			TagFullscreen.launch(dirFragment);
			return true;
		}
		else if(menuItem.getItemId() == R.id.move || menuItem.getItemId() == R.id.copy) {
			boolean isMove = menuItem.getItemId() == R.id.move;

			//TODO Make actual path from root
			Path pathFromRootButNotReally = Paths.get(dirFragment.dirViewModel.getDirUID().toString());
			MoveCopyFullscreen.launch(dirFragment, pathFromRootButNotReally, destinationUID -> {
				new Thread(() -> onMoveCopy(destinationUID, isMove)).start();
			});
			return true;
		}
		else if(menuItem.getItemId() == R.id.zoning) {
			ZoningModal.launch(dirFragment, getSelected().stream().map(item -> item.fileUID).collect(Collectors.toList()));
			return true;
		}
		else if(menuItem.getItemId() == R.id.export) {
			if(!ExportStorageHandler.isStorageAccessible(dirFragment.requireContext()))
				ExportStorageHandler.showPickStorageDialog(dirFragment.requireActivity(), exportPickerLauncher);
			else
				onExport();
			return true;
		}
		else if(menuItem.getItemId() == R.id.trash) {
			onTrash();
			return true;
		}
		else if(menuItem.getItemId() == R.id.share) {
			onShare();
			return true;
		}

		return false;
	}


	//---------------------------------------------------------------------------------------------


	private void onSelectAll() {
		//Grab all UUIDs in the adapter list
		Set<UUID> toSelect = adapter.list.stream().map(item -> item.fileUID).collect(Collectors.toSet());

		//Grab the list of currently selected UUIDs
		Set<UUID> currSelected = selectionController.getSelectedList();

		//If all adapter items are currently selected, we want to deselect all instead
		toSelect.removeAll(currSelected);
		if(!toSelect.isEmpty())
			selectionController.selectAll(toSelect);
		else
			selectionController.deselectAll();
	}



	private void onFilter() {
		View filterView = dirFragment.binding.galleryAppbar.filterBar.getRoot();
		if(filterView.getVisibility() == View.GONE)
			filterView.setVisibility(View.VISIBLE);
		else
			dirFragment.requireActivity().getOnBackPressedDispatcher().onBackPressed();
	}



	private void onSettings() {
		Thread getProps = new Thread(() -> {
			try {
				//Get the props of the directory
				UUID dirUID = dirFragment.dirViewModel.getDirUID();
				JsonObject props = AttrCache.getInstance().getAttr(dirUID);

				//Launch a Settings fragment
				Handler handler = new Handler(dirFragment.requireActivity().getMainLooper());
				handler.post(() -> {
					SettingsFragment settingsFragment = SettingsFragment
							.newInstance(dirUID, dirFragment.dirViewModel.getDirName(), props);
					dirFragment.getChildFragmentManager().beginTransaction()
							.replace(R.id.dir_child_container, settingsFragment)
							.addToBackStack("Settings")
							.commit();
				});
			} catch (FileNotFoundException e) {
				Looper.prepare();
				Toast.makeText(dirFragment.requireContext(),
						"Could not open settings, file not found!", Toast.LENGTH_SHORT).show();
			}
			catch (ConnectException e) {
				Looper.prepare();
				Toast.makeText(dirFragment.requireContext(),
						"Could not open settings, connection failed!", Toast.LENGTH_SHORT).show();
			}
		});
		getProps.start();
	}



	private void onMoveCopy(UUID destinationUID, boolean isMove) {
		//Passing a fake item to move/copy will place the items at the start when the function can't find it
		UUID nextItem = UUID.randomUUID();

		//If the destination is an internal target...
		LinkCache.LinkTarget target = LinkCache.getInstance().getFinalTarget(destinationUID);
		if (target instanceof LinkCache.InternalTarget) {
			try {
				LinkCache.InternalTarget internalTarget = (LinkCache.InternalTarget) target;

				//If the target is a single item, we want to find the item directly after it
				if(!LinkCache.getInstance().isDir(internalTarget.getFileUID()))
					nextItem = getNextItem(internalTarget.getParentUID(), internalTarget.getFileUID());
			}
			catch (FileNotFoundException | ContentsNotFoundException | ConnectException e) {
				//If anything goes wrong, just don't update the next item
			}
		}


		//Get the selected items
		List<ListItem> toMove = getSelected();

		try {
			if(isMove)
				DirUtilities.moveFiles(toMove, destinationUID, nextItem);
			else
				DirUtilities.copyFiles(toMove, destinationUID, nextItem);
		} catch (FileNotFoundException | NotDirectoryException | ContentsNotFoundException | ConnectException e) {
			Looper.prepare();
			Toast.makeText(dirFragment.requireContext(), "Operation failed!", Toast.LENGTH_SHORT).show();
		} catch (IOException e) {
			Looper.prepare();
			Toast.makeText(dirFragment.requireContext(), "Operation failed, could not write!", Toast.LENGTH_SHORT).show();
		}
	}



	private void onExport() {
		int numSelected = selectionController.getNumSelected();

		//Launch a confirmation dialog first
		AlertDialog.Builder builder = new AlertDialog.Builder(dirFragment.requireContext());
		builder.setTitle("Export");
		builder.setMessage("Are you sure you want to export "+numSelected+" item"+(numSelected==1?"":"s")+"?");

		builder.setPositiveButton("Yes", (dialogInterface, which) -> {
			//Get the selected items, which should be in order
			List<ListItem> toExport = getSelected();

			//And export them
			new Thread(() -> DirUtilities.export(toExport)).start();
		});
		builder.setNegativeButton("No", null);

		AlertDialog dialog = builder.create();
		dialog.show();
	}



	private void onTrash() {
		int numSelected = selectionController.getNumSelected();

		//Launch a confirmation dialog first
		AlertDialog.Builder builder = new AlertDialog.Builder(dirFragment.requireContext());
		builder.setTitle("Move to Trash");
		builder.setMessage("Are you sure you want to move "+numSelected+" item"+(numSelected==1?"":"s")+" to trash?");

		builder.setPositiveButton("Yes", (dialogInterface, which) -> {
			//Get the selected items
			List<ListItem> toTrash = getSelected();

			//Update each item's name with a 'trashed' suffix
			String suffix = ".trashed_"+ Instant.now().getEpochSecond();
			List<ListItem> renamed = toTrash.stream()
					.map(item -> new ListItem.Builder(item).setName(item.name + suffix).build())
					.collect(Collectors.toList());

			//And 'trash' them
			new Thread(() -> {
				DirUtilities.renameFiles(renamed);
			}).start();

			selectionController.stopSelecting();
		});
		builder.setNegativeButton("No", null);

		AlertDialog dialog = builder.create();
		dialog.show();
	}



	private void onShare() {

	}


	//---------------------------------------------------------------------------------------------


	private UUID getNextItem(UUID parentDirUID, UUID targetUID)
			throws ContentsNotFoundException, FileNotFoundException, ConnectException {

		//For each item in the parent directory...
		List<Pair<UUID, String>> dirList = DirCache.getInstance().getDirContents(parentDirUID);
		for(int i = 0; i < dirList.size(); i++) {
			//If we find the target, return the next item (or null)
			if (dirList.get(i).first.equals(targetUID))
				return (i+1 < dirList.size()) ? dirList.get(i+1).first : null;
		}
		throw new FileNotFoundException("Target not found! \nParent='"+parentDirUID+"', \nTarget='"+targetUID+"'");
	}


	private List<ListItem> getSelected() {
		//Excluding duplicates...			(set returns true if the item is new, false if it already exists)
		Set<UUID> isDuplicate = new HashSet<>();

		//Grab each selected item in the adapter list
		return adapter.list.stream()
				.filter(item -> isDuplicate.add(item.fileUID) && selectionController.isSelected(item.fileUID))
				.collect(Collectors.toList());
	}
}
