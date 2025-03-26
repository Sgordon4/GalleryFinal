package aaa.sgordon.galleryfinal.repository.galleryhelpers;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import java.util.List;

import aaa.sgordon.galleryfinal.gallery.ListItem;

public class FileMovement {

	public static final ActivityResultLauncher<Intent> exportPickerLauncher;

	public void initialize(Activity activity) {
		exportPickerLauncher = activity.registerForActivityResult(
				new ActivityResultContracts.StartActivityForResult(),
				result -> {
					ExportStorageHandler.onStorageLocationPicked(dirFragment.requireContext(), result);
					System.out.println("Exporting!");
				});
	}

	public static void exportFiles(Activity activity, List<ListItem> toExport) {

		//If the storage directory is not accessible...
		if(!ExportStorageHandler.isStorageAccessible(activity)) {
			System.out.println("Launching");
			Log.w("Gal.Export", "Export directory is inaccessible. Prompting user to reselect.");
			ExportStorageHandler.showPickStorageDialog(activity, exportPickerLauncher);
		}
	}
}
