package aaa.sgordon.galleryfinal.repository;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.documentfile.provider.DocumentFile;

public class StorageHandler {
	private static final String TAG = "Gal.Storage";
	private static final String PREFCATEGORY = "AppPrefs";
	private static final String PREFTAG = "device_storage_location";


	public static Uri getGalleryStorageUri(@NonNull Context context) {
		if(!isStorageAccessible(context))
			return null;


		String storageUri = getStorageTreeUri(context);
		Uri savedUri = Uri.parse(storageUri);
		DocumentFile pickedDir = DocumentFile.fromTreeUri(context, savedUri);
		return pickedDir.getUri();

		//return pickedDir.getUri().buildUpon().appendPath(".Gallery").build();
	}


	public static boolean isStorageAccessible(@NonNull Context context) {
		String storageUri = getStorageTreeUri(context);
		System.out.println("StorageUri: "+storageUri);
		if (storageUri == null)
			return false;

		Uri savedUri = Uri.parse(Uri.decode(storageUri));
		System.out.println("Uri uri: "+savedUri);
		DocumentFile pickedDir = DocumentFile.fromTreeUri(context, savedUri);
		System.out.println("DocUri: "+pickedDir.getUri().toString());
		System.out.println(pickedDir.exists());
		System.out.println(pickedDir.canWrite());

		return pickedDir != null && pickedDir.exists() && pickedDir.canWrite();
	}


	public static void onStorageLocationPicked(Context context, ActivityResult result) {
		System.out.println("Saving");
		if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
			Uri treeUri = result.getData().getData();
			ContentResolver contentResolver = context.getContentResolver();

			// Take persistable permissions
			int takeFlags = (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
			contentResolver.takePersistableUriPermission(treeUri, takeFlags);

			// Store the URI in SharedPreferences for later use
			SharedPreferences prefs = context.getSharedPreferences(PREFCATEGORY, Context.MODE_PRIVATE);
			prefs.edit().putString(PREFTAG, treeUri.toString()).apply();

			Log.d(TAG, "Directory selected: " + treeUri);
		}
	}

	public static void showPickStorageDialog(Activity activity, ActivityResultLauncher<Intent> launcher) {
		AlertDialog dialog = new AlertDialog.Builder(activity)
				.setTitle("Storage Location Unavailable")
				.setMessage("The previously selected storage location is no longer accessible. Please select a new location.")
				.setPositiveButton("Select New Location", (dialogInterface, which) -> {
					Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
					intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
							Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
							Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
					launcher.launch(intent);
				})
				.setNegativeButton("Exit App", (dialog1, which) -> {
					activity.finishAffinity();	//Close all activities and exit
					System.exit(0);  	//Ensure the app process is killed
				})
				.create();

		dialog.setCancelable(false);  // Prevents dismissing via back button
		dialog.setCanceledOnTouchOutside(false);  // Prevents dismissing via outside click
		dialog.show();
	}



	@Nullable
	public static String getStorageTreeUri(@NonNull Context context) {
		SharedPreferences prefs = context.getSharedPreferences(PREFCATEGORY, Context.MODE_PRIVATE);
		return prefs.getString(PREFTAG, null);
	}
}
