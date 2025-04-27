package aaa.sgordon.galleryfinal.gallery.cooking;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.documentfile.provider.DocumentFile;

import com.google.gson.JsonObject;
import com.leinardi.android.speeddial.SpeedDialActionItem;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
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
import aaa.sgordon.galleryfinal.repository.gallery.ListItem;
import aaa.sgordon.galleryfinal.gallery.components.filter.TagFullscreen;
import aaa.sgordon.galleryfinal.gallery.components.zoning.ZoningModal;
import aaa.sgordon.galleryfinal.gallery.components.movecopy.MoveCopyFragment;
import aaa.sgordon.galleryfinal.gallery.components.properties.EditItemModal;
import aaa.sgordon.galleryfinal.gallery.components.properties.NewItemModal;
import aaa.sgordon.galleryfinal.gallery.components.properties.SettingsFragment;
import aaa.sgordon.galleryfinal.gallery.components.trash.TrashFragment;
import aaa.sgordon.galleryfinal.gallery.touch.SelectionController;
import aaa.sgordon.galleryfinal.repository.gallery.caches.AttrCache;
import aaa.sgordon.galleryfinal.repository.gallery.caches.LinkCache;
import aaa.sgordon.galleryfinal.repository.gallery.link.ExternalTarget;
import aaa.sgordon.galleryfinal.repository.gallery.link.InternalTarget;
import aaa.sgordon.galleryfinal.repository.gallery.link.LinkTarget;
import aaa.sgordon.galleryfinal.repository.galleryhelpers.ExportStorageHandler;
import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridAPI;
import aaa.sgordon.galleryfinal.utilities.DirUtilities;
import aaa.sgordon.galleryfinal.utilities.MyApplication;

public class MenuItemHelper {
	private DirFragment dirFragment;
	private ActivityResultLauncher<Intent> filePickerLauncher;
	private ActivityResultLauncher<Intent> takePhotoLauncher;
	private ActivityResultLauncher<Intent> shareLauncher;
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

		takePhotoLauncher = dirFragment.registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
			System.out.println("Result is at "+result);
			if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
				System.out.println("Data: "+result.getData());
			}
		});

		shareLauncher = dirFragment.registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
			cleanupSharedFiles();
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
			TrashFragment fragment = new TrashFragment();
			dirFragment.getChildFragmentManager().beginTransaction()
					.replace(R.id.dir_child_container, fragment, TrashFragment.class.getSimpleName())
					.addToBackStack(null)
					.commit();
			return true;
		}
		else if (menuItem.getItemId() == R.id.settings) {
			onSettings();
			return true;
		}
		else if (menuItem.getItemId() == R.id.account) {
			Toast.makeText(dirFragment.requireContext(), "< This is a picture of you :)", Toast.LENGTH_SHORT).show();
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
			ZoningModal.launch(dirFragment, getSelected());
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
				new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(dirFragment.requireContext(),
						"Could not open settings, file not found!", Toast.LENGTH_SHORT).show());
			}
			catch (ConnectException e) {
				new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(dirFragment.requireContext(),
						"Could not open settings, connection failed!", Toast.LENGTH_SHORT).show());
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

				try {
					if(isMove)
						DirUtilities.moveFiles(toMove, destinationUID, nextItem);
					else
						DirUtilities.copyFiles(toMove, destinationUID, nextItem);
				}
				catch (FileNotFoundException | NotDirectoryException | ContentsNotFoundException | ConnectException e) {
					new Handler(Looper.getMainLooper()).post(() ->
							Toast.makeText(dirFragment.requireContext(), "Operation failed!", Toast.LENGTH_SHORT).show());
				}
				catch (IOException e) {
					new Handler(Looper.getMainLooper()).post(() ->
							Toast.makeText(dirFragment.requireContext(), "Operation failed, could not write!", Toast.LENGTH_SHORT).show());
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
		//Get all selected items
		List<ListItem> toShare = getSelected();
		if(toShare.isEmpty()) return;


		Thread cacheThread = new Thread(() -> {
			//We need to download, unencrypt, and cache each file that we want to share
			ArrayList<Uri> shareUris = cacheFilesToShare(toShare);
			if(shareUris.isEmpty())
				return;


			Handler handler = new Handler(Looper.getMainLooper());
			handler.post(() -> {
				//Build a share intent based on the number of items to share
				Intent shareIntent;
				if(shareUris.size() == 1) {
					shareIntent = new Intent(Intent.ACTION_SEND);
					shareIntent.putExtra(Intent.EXTRA_STREAM, shareUris.get(0));
				}
				else {
					shareIntent = new Intent(Intent.ACTION_SEND_MULTIPLE);
					shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, shareUris);
				}
				shareIntent.setType("*/*");
				shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);


				//Launch the chooser
				shareLauncher.launch(Intent.createChooser(shareIntent, "Share file with..."));
			});
		});
		cacheThread.start();
	}


	//---------------------------------------------------------------------------------------------


	private void cleanupSharedFiles() {
		File sharedDir = Paths.get(dirFragment.requireContext().getCacheDir().getPath(), "shared_files").toFile();
		deleteRecursive(sharedDir);
	}


	private ArrayList<Uri> cacheFilesToShare(List<ListItem> toShare) {
		int numDirectories = 0;
		int numLinks = 0;
		int numDividers = 0;

		ArrayList<Uri> shareUris = new ArrayList<>();
		for(ListItem item : toShare) {
			if (item.getPrettyName().contains("..")) {
				Log.e("Gal.Share", "Invalid filename for share: " + item.getPrettyName());
				continue;
			}

			if(item.isDir) {
				numDirectories++;
				continue;
			}
			else if(item.type.equals(ListItem.Type.DIVIDER)) {
				numDividers++;
				continue;
			}
			else if(item.isLink) {		//Links can only link to dividers, directories, or other links.
				numLinks++;
				continue;
			}


			//There can be duplicate filenames being shared at the same time, so separate each file by UUID
			File cacheFile = Paths.get(dirFragment.requireContext().getCacheDir().getPath(), "shared_files",
					item.fileUID.toString(), item.getPrettyName()).toFile();


			boolean cached = downloadAndCache(item, cacheFile);
			if(!cached) continue;


			//Build a share uri from each item
			//Uri becomes content://sgordon.gallery.shared/shared_files/{fileUID}/{fileName}
			Uri shareUri = new Uri.Builder()
					.scheme("content")
					.authority("sgordon.gallery.shared")
					.appendPath("shared_files")
					.appendPath(item.fileUID.toString())
					.appendPath(item.getPrettyName())
					.build();
			shareUris.add(shareUri);

		}


		int total = numDirectories + numLinks + numDividers;
		if(total > 0) {
			new Handler(Looper.getMainLooper()).post(() -> {
				Toast.makeText(dirFragment.requireContext(), "Directories, Links, and Dividers cannot be shared!", Toast.LENGTH_SHORT).show();
			});
		}

		return shareUris;
	}

	private boolean downloadAndCache(ListItem toCache, File cacheFile) {
		InputStream in = null;
		try {
			Log.d("Gal.Share", "Downloading file to share. FileUID='"+ toCache.fileUID +"'");

			//Get the content uri from the repo, considering if the file is a link or not
			Uri contentUri;
			if(toCache.isLink) {
				LinkTarget linkTarget = LinkCache.getInstance().getFinalTarget(toCache.fileUID);

				if(linkTarget instanceof ExternalTarget)
					contentUri = linkTarget.toUri();
				else if(linkTarget instanceof InternalTarget) {
					UUID targetUID = ((InternalTarget) linkTarget).fileUID;
					contentUri = HybridAPI.getInstance().getFileContent(targetUID).first;
				}
				else
					throw new IllegalStateException("Link target is null! FileUID='"+toCache.fileUID+"'");
			}
			else {
				contentUri = HybridAPI.getInstance().getFileContent(toCache.fileUID).first;
			}


			//Create the temp file in the app cache directory
			cacheFile.getParentFile().mkdirs();
			cacheFile.createNewFile();

			//If the file can be opened using ContentResolver, do that. Otherwise, open using URL's openStream
			try {
				in = MyApplication.getAppContext().getContentResolver().openInputStream(contentUri);
			} catch (FileNotFoundException e) {
				in = new URL(contentUri.toString()).openStream();
			}

			//Write the source data to the destination file
			try (OutputStream out = Files.newOutputStream(cacheFile.toPath())) {
				byte[] dataBuffer = new byte[1024];
				int bytesRead;
				while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
					out.write(dataBuffer, 0, bytesRead);
				}
			}
			Log.d("Gal.Share", "File caching successful! FileUID='"+ toCache.fileUID +"'");
		}
		catch (FileNotFoundException | ContentsNotFoundException | ConnectException e) {
			//Just don't make the file
			return false;
		}
		catch (IOException e) {
			return false;
		}
		finally {
			try { if(in != null) in.close(); }
			catch (IOException ignored) {/* Oop */}
		}
		return true;
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


	public static boolean deleteRecursive(File fileOrDirectory) {
		if (fileOrDirectory.isDirectory()) {
			File[] children = fileOrDirectory.listFiles();
			if (children != null) {
				for (File child : children) {
					deleteRecursive(child);
				}
			}
		}
		return fileOrDirectory.delete();
	}
}
