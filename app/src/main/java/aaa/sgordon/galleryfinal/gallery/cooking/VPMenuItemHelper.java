package aaa.sgordon.galleryfinal.gallery.cooking;

import android.content.Intent;
import android.view.MenuItem;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;

import aaa.sgordon.galleryfinal.repository.galleryhelpers.ExportStorageHandler;

public class VPMenuItemHelper {
	public ActivityResultLauncher<Intent> exportPickerLauncher;

	public void onCreate(Fragment fragment) {
		exportPickerLauncher = fragment.registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
			ExportStorageHandler.onStorageLocationPicked(fragment.requireActivity(), result);
			//onExport();
		});
	}



	public boolean onItemClicked(MenuItem menuItem) {
		return false;
	}
}
