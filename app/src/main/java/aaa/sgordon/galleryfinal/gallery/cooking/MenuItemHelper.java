package aaa.sgordon.galleryfinal.gallery.cooking;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.documentfile.provider.DocumentFile;

import com.google.gson.JsonObject;
import com.leinardi.android.speeddial.SpeedDialActionItem;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ConnectException;
import java.nio.file.NotDirectoryException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.gallery.DirFragment;
import aaa.sgordon.galleryfinal.gallery.DirRVAdapter;
import aaa.sgordon.galleryfinal.gallery.ImportHelper;
import aaa.sgordon.galleryfinal.gallery.ListItem;
import aaa.sgordon.galleryfinal.gallery.components.filter.TagFullscreen;
import aaa.sgordon.galleryfinal.gallery.components.zoning.ZoningModal;
import aaa.sgordon.galleryfinal.gallery.components.movecopy.MoveCopyFragment;
import aaa.sgordon.galleryfinal.gallery.components.properties.EditItemModal;
import aaa.sgordon.galleryfinal.gallery.components.properties.NewItemModal;
import aaa.sgordon.galleryfinal.gallery.components.properties.SettingsFragment;
import aaa.sgordon.galleryfinal.gallery.components.trash.TrashFragment;
import aaa.sgordon.galleryfinal.gallery.touch.SelectionController;
import aaa.sgordon.galleryfinal.repository.gallery.caches.AttrCache;
import aaa.sgordon.galleryfinal.repository.galleryhelpers.ExportStorageHandler;
import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;
import aaa.sgordon.galleryfinal.utilities.DirUtilities;

public class MenuItemHelper {
	private DirFragment dirFragment;
	public ActivityResultLauncher<Intent> filePickerLauncher;
	private ActivityResultLauncher<Intent> exportPickerLauncher;

	//TODO Move SelectionController definition to onCreate in DirFragment
	private DirRVAdapter adapter;
	private SelectionController selectionController;


	public void onCreate(DirFragment dirFragment) {
		this.dirFragment = dirFragment;

		filePickerLauncher = dirFragment.registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
			if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
				List<Uri> uris = ImportHelper.getUrisFromIntent(result.getData());
				Map<Uri, DocumentFile> fileInfo = ImportHelper.getFileInfoForUris(dirFragment.requireContext(), uris);

				Thread importThread = new Thread(() -> {
					ImportHelper.importFiles(dirFragment.requireContext(), dirFragment.dirViewModel.listItem.fileUID, uris, fileInfo);
				});
				importThread.start();
			}
		});

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
			//TrashFullscreen.launch(dirFragment, dirFragment.dirViewModel.getDirUID());
			TrashFragment fragment = new TrashFragment();
			dirFragment.getChildFragmentManager().beginTransaction()
					.replace(R.id.dir_child_container, fragment, TrashFragment.class.getSimpleName())
					.addToBackStack(null)
					.commit();
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
			List<ListItem> selected = getSelected();
			if(selected.isEmpty()) return true;

			ListItem selectedItem = selected.get(0);
			EditItemModal.launch(dirFragment, selectedItem, dirFragment.dirViewModel.listItem);
			return true;
		}
		else if(menuItem.getItemId() == R.id.tag) {
			TagFullscreen.launch(dirFragment);
			return true;
		}
		else if(menuItem.getItemId() == R.id.move || menuItem.getItemId() == R.id.copy) {
			boolean isMove = menuItem.getItemId() == R.id.move;
			onMoveCopy(isMove);
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


	public boolean onFabItemClicked(SpeedDialActionItem actionItem) {
		if(actionItem.getId() == R.id.new_item) {
			NewItemModal.launch(dirFragment, dirFragment.dirViewModel.listItem);
			return true;
		}
		else if(actionItem.getId() == R.id.import_image) {
			//Launch the file picker intent
			Intent filePicker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
			//filePicker.setType("image/*");
			filePicker.setType("*/*");
			filePicker.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
			filePicker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
			filePicker = Intent.createChooser(filePicker, "Select Items to Import");

			filePickerLauncher.launch(filePicker);
			return true;
		}
		else if(actionItem.getId() == R.id.take_photo) {
			Toast.makeText(dirFragment.requireContext(), "No worky :)", Toast.LENGTH_SHORT).show();
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
				UUID dirUID = dirFragment.dirViewModel.listItem.fileUID;
				JsonObject props = AttrCache.getInstance().getAttr(dirUID);

				//Launch a Settings fragment
				Handler handler = new Handler(dirFragment.requireActivity().getMainLooper());
				handler.post(() -> {
					SettingsFragment settingsFragment = SettingsFragment
							.newInstance(dirUID, dirFragment.dirViewModel.listItem.getPrettyName(), props);
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



	private void onMoveCopy(boolean isMove) {
		MoveCopyFragment fragment = MoveCopyFragment.newInstance(dirFragment.dirViewModel.listItem, isMove);
		fragment.setMoveCopyCallback((destinationUID, nextItem) -> {
			new Thread(() -> {
				//Get the selected items
				List<ListItem> toMove = getSelected();
				System.out.println("Moving to "+destinationUID+" after "+nextItem);

				try {
					if(isMove)
						DirUtilities.moveFiles(toMove, destinationUID, nextItem);
					else
						DirUtilities.copyFiles(toMove, destinationUID, nextItem);
				}
				catch (FileNotFoundException | NotDirectoryException | ContentsNotFoundException | ConnectException e) {
					Looper.prepare();
					Toast.makeText(dirFragment.requireContext(), "Operation failed!", Toast.LENGTH_SHORT).show();
				}
				catch (IOException e) {
					Looper.prepare();
					Toast.makeText(dirFragment.requireContext(), "Operation failed, could not write!", Toast.LENGTH_SHORT).show();
				}
			}).start();
		});
		dirFragment.getChildFragmentManager().beginTransaction()
				.replace(R.id.dir_child_container, fragment, MoveCopyFragment.class.getSimpleName())
				.addToBackStack(null)
				.commit();
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
					.map(item -> new ListItem.Builder(item).setRawName(item.getRawName() + suffix).build())
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


	private List<ListItem> getSelected() {
		//Excluding duplicates...			(set returns true if the item is new, false if it already exists)
		Set<UUID> isDuplicate = new HashSet<>();

		//Grab each selected item in the adapter list
		return adapter.list.stream()
				.filter(item -> isDuplicate.add(item.fileUID) && selectionController.isSelected(item.fileUID))
				.collect(Collectors.toList());
	}
}
