package aaa.sgordon.galleryfinal.gallery.cooking;

import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ConnectException;
import java.nio.file.NotDirectoryException;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.gallery.ListItem;
import aaa.sgordon.galleryfinal.gallery.components.movecopy.MoveCopyFragment;
import aaa.sgordon.galleryfinal.gallery.components.zoning.ZoningModal;
import aaa.sgordon.galleryfinal.repository.galleryhelpers.ExportStorageHandler;
import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;
import aaa.sgordon.galleryfinal.utilities.DirUtilities;

public class VPMenuItemHelper {
	public final Fragment parentFragment;
	public final ListItem startDir;
	public final Context context;
	private final VPMenuItemHelperCallback callback;

	public ActivityResultLauncher<Intent> exportPickerLauncher;

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
	}




	public boolean onItemClicked(MenuItem menuItem) {
		if(menuItem.getItemId() == R.id.share) {

			return true;
		}
		else if(menuItem.getItemId() == R.id.move) {

			return true;
		}
		else if(menuItem.getItemId() == R.id.copy) {

			return true;
		}
		else if(menuItem.getItemId() == R.id.export) {

			return true;
		}
		else if(menuItem.getItemId() == R.id.trash) {

			return true;
		}
		else if(menuItem.getItemId() == R.id.backup) {

			return true;
		}
		return false;
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
					Looper.prepare();
					Toast.makeText(context, "Operation failed!", Toast.LENGTH_SHORT).show();
				}
				catch (IOException e) {
					Looper.prepare();
					Toast.makeText(context, "Operation failed, could not write!", Toast.LENGTH_SHORT).show();
				}
			}).start();
		});
		return fragment;
	}



	public void onExport() {
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
		ZoningModal.launch(parentFragment, List.of(toBackup.fileUID));
	}
}
