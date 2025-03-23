package aaa.sgordon.galleryfinal.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

public class DeviceStorageHandler {


	private boolean isStorageDirAccessible(@NonNull Context context) {
		String storageUri = getStorageUri(context);
		if (storageUri == null)
			return false;

		Uri savedUri = Uri.parse(storageUri);
		DocumentFile pickedDir = DocumentFile.fromTreeUri(context, savedUri);

		return pickedDir != null && pickedDir.exists() && pickedDir.canWrite();
	}


	@Nullable
	private String getStorageUri(@NonNull Context context) {
		SharedPreferences prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
		return prefs.getString("device_storage_location", null);
	}
}
