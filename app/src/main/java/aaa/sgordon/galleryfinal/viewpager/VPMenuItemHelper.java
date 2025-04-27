package aaa.sgordon.galleryfinal.viewpager;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import aaa.sgordon.galleryfinal.gallery.components.movecopy.MoveCopyFragment;
import aaa.sgordon.galleryfinal.gallery.components.zoning.ZoningModal;
import aaa.sgordon.galleryfinal.repository.gallery.ListItem;
import aaa.sgordon.galleryfinal.repository.gallery.caches.LinkCache;
import aaa.sgordon.galleryfinal.repository.gallery.link.ExternalTarget;
import aaa.sgordon.galleryfinal.repository.gallery.link.InternalTarget;
import aaa.sgordon.galleryfinal.repository.gallery.link.LinkTarget;
import aaa.sgordon.galleryfinal.repository.galleryhelpers.ExportStorageHandler;
import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridAPI;
import aaa.sgordon.galleryfinal.utilities.DirUtilities;
import aaa.sgordon.galleryfinal.utilities.MyApplication;

public class VPMenuItemHelper {
	public final Fragment parentFragment;
	public final ListItem startDir;
	public final Context context;
	private final VPMenuItemHelperCallback callback;

	public ActivityResultLauncher<Intent> exportPickerLauncher;
	public ActivityResultLauncher<Intent> shareLauncher;

	public VPMenuItemHelper(@NonNull Fragment fragment, @NonNull ListItem startDir, @NonNull Context context, @NonNull VPMenuItemHelperCallback callback) {
		this.parentFragment = fragment;
		this.startDir = startDir;
		this.context = context;
		this.callback = callback;
	}

	public interface VPMenuItemHelperCallback {
		ListItem getCurrentItem();
	}

	public void onCreate() {
		exportPickerLauncher = parentFragment.registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
			ExportStorageHandler.onStorageLocationPicked(parentFragment.requireActivity(), result);
			onExport();
		});
		shareLauncher = parentFragment.registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
			cleanupSharedFiles();
		});
	}



	public MoveCopyFragment buildMoveCopy(boolean isMove) {
		List<ListItem> toMove = List.of(callback.getCurrentItem());

		MoveCopyFragment fragment = MoveCopyFragment.newInstance(startDir, isMove);
		fragment.setMoveCopyCallback((destinationUID, nextItem) -> {
			new Thread(() -> {
				try {
					if(isMove)
						DirUtilities.moveFiles(toMove, destinationUID, nextItem);
					else
						DirUtilities.copyFiles(toMove, destinationUID, nextItem);
				}
				catch (FileNotFoundException | NotDirectoryException | ContentsNotFoundException |
					   ConnectException e) {
					new Handler(Looper.getMainLooper()).post(() -> {
						Toast.makeText(context, "Operation failed!", Toast.LENGTH_SHORT).show();
					});
				}
				catch (IOException e) {
					new Handler(Looper.getMainLooper()).post(() -> {
						Toast.makeText(context, "Operation failed, could not write!", Toast.LENGTH_SHORT).show();
					});
				}
			}).start();
		});
		return fragment;
	}



	public void onExport() {
		if(!ExportStorageHandler.isStorageAccessible(parentFragment.requireContext())) {
			ExportStorageHandler.showPickStorageDialog(parentFragment.requireActivity(), exportPickerLauncher);
			return;
		}


		List<ListItem> toExport = List.of(callback.getCurrentItem());

		//Launch a confirmation dialog first
		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setTitle("Export");
		builder.setMessage("Are you sure you want to export this item?");

		builder.setPositiveButton("Yes", (dialogInterface, which) -> {
			//Export all items
			new Thread(() -> DirUtilities.export(toExport)).start();
		});
		builder.setNegativeButton("No", null);

		AlertDialog dialog = builder.create();
		dialog.show();
	}



	public void onTrash() {
		ListItem toTrash = callback.getCurrentItem();

		//Launch a confirmation dialog first
		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setTitle("Move to Trash");
		builder.setMessage("Are you sure you want to move this item to trash?");

		builder.setPositiveButton("Yes", (dialogInterface, which) -> {
			toTrash.setTrashed(true);
		});
		builder.setNegativeButton("No", null);

		AlertDialog dialog = builder.create();
		dialog.show();
	}



	public void onBackup() {
		ListItem toBackup = callback.getCurrentItem();
		ZoningModal.launch(parentFragment, List.of(toBackup));
	}



	public void onShare() {
		ListItem toShare = callback.getCurrentItem();
		Thread cacheThread = new Thread(() -> {
			//We need to download, unencrypt, and cache each file that we want to share
			ArrayList<Uri> shareUris = cacheFilesToShare(List.of(toShare));
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
		File sharedDir = Paths.get(parentFragment.requireContext().getCacheDir().getPath(), "shared_files").toFile();
		deleteRecursive(sharedDir);
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
			File cacheFile = Paths.get(parentFragment.requireContext().getCacheDir().getPath(), "shared_files",
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
				Toast.makeText(parentFragment.requireContext(), "Directories, Links, and Dividers cannot be shared!", Toast.LENGTH_SHORT).show();
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
}
