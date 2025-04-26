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
import aaa.sgordon.galleryfinal.repository.galleryhelpers.ExportStorageHandler;
import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;
import aaa.sgordon.galleryfinal.utilities.DirUtilities;

public class VPMenuItemHelperOld {
	public ActivityResultLauncher<Intent> exportPickerLauncher;
	private final VPMenuItemHelperCallback callback;

	public VPMenuItemHelperOld(@NonNull VPMenuItemHelperCallback callback) {
		this.callback = callback;
	}

	public interface VPMenuItemHelperCallback {
		ListItem getCurrentItem();
	}

	public void onCreate(Fragment fragment) {
		exportPickerLauncher = fragment.registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
			ExportStorageHandler.onStorageLocationPicked(fragment.requireActivity(), result);
			onExport(List.of(callback.getCurrentItem()), fragment.requireContext());
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





	public static void onTrash(@NonNull List<ListItem> toTrash, @NonNull Context context, @NonNull Consumer<Boolean> callback) {
		int numSelected = toTrash.size();

		//Launch a confirmation dialog first
		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setTitle("Move to Trash");
		builder.setMessage("Are you sure you want to move "+numSelected+" item"+(numSelected==1?"":"s")+" to trash?");

		builder.setPositiveButton("Yes", (dialogInterface, which) -> {
			//Update each item's name with a 'trashed' suffix
			String suffix = ".trashed_"+ Instant.now().getEpochSecond();
			List<ListItem> renamed = toTrash.stream()
					.map(item -> new ListItem.Builder(item).setRawName(item.getRawName() + suffix).build())
					.collect(Collectors.toList());

			//And 'trash' them
			new Thread(() -> {
				DirUtilities.renameFiles(renamed);
			}).start();

			callback.accept(true);
		});
		builder.setNegativeButton("No", (dialogInterface, which) -> callback.accept(false));

		AlertDialog dialog = builder.create();
		dialog.show();
	}



	public static MoveCopyFragment buildMoveCopy(@NonNull List<ListItem> toMove, @NonNull ListItem startDir, boolean isMove, @NonNull Context context) {
		MoveCopyFragment fragment = MoveCopyFragment.newInstance(startDir, isMove);
		fragment.setMoveCopyCallback((destinationUID, nextItem) -> {
			new Thread(() -> {
				System.out.println("Moving to "+destinationUID+" after "+nextItem);

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



	public static void onExport(@NonNull List<ListItem> toExport, @NonNull Context context) {
		int numSelected = toExport.size();

		//Launch a confirmation dialog first
		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setTitle("Export");
		builder.setMessage("Are you sure you want to export "+numSelected+" item"+(numSelected==1?"":"s")+"?");

		builder.setPositiveButton("Yes", (dialogInterface, which) -> {
			//Export all items
			new Thread(() -> DirUtilities.export(toExport)).start();
		});
		builder.setNegativeButton("No", null);

		AlertDialog dialog = builder.create();
		dialog.show();
	}
}
