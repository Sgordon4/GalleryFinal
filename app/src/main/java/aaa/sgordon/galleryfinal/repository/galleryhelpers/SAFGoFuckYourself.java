package aaa.sgordon.galleryfinal.repository.galleryhelpers;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.FileNotFoundException;


/*
This FUCKING GODDAMN BULLSHIT framework that Android dragged out from the depths of their dumbest think-tank
has some of the ABSOLUTE FUCKING WORST performance and functionality I have ever seen for a mandatory API.
I am not dealing with the fucking atrocious slowdown incurred by using DocumentFile's bullshit,
so these are alternatives.

Also, why the FUCK does contentResolver's query not fucking work. Legitimately unreal.
*/

public class SAFGoFuckYourself {


	public static Uri makeDocUriFromTreeUri(@NonNull Uri treeUri, String... segments) {
		String rootTreeID = DocumentsContract.getTreeDocumentId(treeUri);

		String name = "";
		if(segments.length > 0)
			name = "/"+String.join("/", segments);

		return DocumentsContract.buildDocumentUriUsingTree(treeUri, rootTreeID + name);
	}
	public static Uri makeDocUriFromDocUri(@NonNull Uri docUri, String... segments) {
		String documentID = DocumentsContract.getDocumentId(docUri);

		String name = "";
		if(segments.length > 0)
			name = "/"+String.join("/", segments);

		return DocumentsContract.buildDocumentUriUsingTree(docUri, documentID + name);
	}
	@Nullable
	public static Uri getParentFromDocUri(@NonNull Uri documentUri) {
		String documentID = DocumentsContract.getDocumentId(documentUri);

		//Get the last index of '/'. If there is none, this is a top-level folder
		int lastSlashIndex = documentID.lastIndexOf('/');
		if (lastSlashIndex == -1) return null;

		String parentDocumentId = documentID.substring(0, lastSlashIndex);
		return DocumentsContract.buildDocumentUriUsingTree(documentUri, parentDocumentId);
	}
	@Nullable
	public static String getNameFromDocUri(@NonNull Uri documentUri) {
		String documentID = DocumentsContract.getDocumentId(documentUri);

		//Get the last index of '/'. If there is none, this is a top-level folder
		int lastSlashIndex = documentID.lastIndexOf('/');
		if (lastSlashIndex == -1) return null;

		return documentID.substring(lastSlashIndex+1);
	}


	//Will not create the file if it already exists. Creates all parent directories
	public static void createFile(Context context, Uri fileDocUri) {
		if(fileExists(context, fileDocUri)) return;

		//If there is no parent, we're trying to create a top-level file, which we can't do
		Uri parentUri = getParentFromDocUri(fileDocUri);
		if(parentUri == null) throw new IllegalArgumentException("Cannot create top-level file: "+fileDocUri);

		//If the parent doesn't exist, create that
		if(!directoryExists(context, parentUri))
			createDirectory(context, parentUri);

		String fileName = getNameFromDocUri(fileDocUri);
		try {
			Uri uri = createFile(context, parentUri, fileName);
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}
	private static Uri createFile(Context context, Uri parentUri, String dirName) throws FileNotFoundException {
		return DocumentsContract.createDocument(
				context.getContentResolver(), parentUri,
				"*/*", dirName);
	}



	//Will not create the directory if it already exists. Creates all parent directories
	public static void createDirectory(Context context, Uri directoryDocUri) {
		if(directoryExists(context, directoryDocUri)) return;

		//If there is no parent, we're trying to create a top-level directory, which we can't do
		Uri parentUri = getParentFromDocUri(directoryDocUri);
		if(parentUri == null) throw new IllegalArgumentException("Cannot create top-level dir: "+directoryDocUri);

		//If the parent doesn't exist, create that
		if(!directoryExists(context, parentUri))
			createDirectory(context, parentUri);

		String fileName = getNameFromDocUri(directoryDocUri);
		try {
			Uri uri = createDirectory(context, parentUri, fileName);
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}
	private static Uri createDirectory(Context context, Uri parentUri, String dirName) throws FileNotFoundException {
		return DocumentsContract.createDocument(
				context.getContentResolver(), parentUri,
				DocumentsContract.Document.MIME_TYPE_DIR, dirName);
	}


	//Since DocumentsContract FOR SOME REASON doesn't allow a directory/file with the same names, this works on both
	public static boolean delete(Context context, Uri fileDocUri) {
		try {
			return DocumentsContract.deleteDocument(context.getContentResolver(), fileDocUri);
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}






	public static boolean fileExists(Context context, Uri fileUri) {
		return getFileType(context, fileUri) == 2;
	}
	public static boolean directoryExists(Context context, Uri fileUri) {
		return getFileType(context, fileUri) == 1;
	}
	private static int getFileType(Context context, Uri fileUri) {
		try (Cursor cursor = context.getContentResolver().query(
				fileUri,
				new String[]{DocumentsContract.Document.COLUMN_MIME_TYPE},
				null, null, null)) {

			if (cursor != null && cursor.moveToFirst()) {
				String mimeType = cursor.getString(0);
				if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType)) {
					return 1; // Directory
				} else {
					return 2; // Regular file
				}
			}
		} catch (IllegalArgumentException | UnsupportedOperationException e) {
			return -1; // File does not exist
		}
		return -1; // Default to non-existing
	}



	public static int getFileSize(Context context, Uri fileUri) {
		try (Cursor cursor = context.getContentResolver().query(
				fileUri,
				new String[]{DocumentsContract.Document.COLUMN_SIZE},
				null, null, null)) {

			if (cursor != null && cursor.moveToFirst()) {
				return cursor.getInt(0); // File size in bytes
			}
		} catch (IllegalArgumentException e) {
			Log.e("FileSizeCheck", "Invalid file URI: " + fileUri, e);
		}
		return -1; // Return -1 if file does not exist or size cannot be determined
	}
}
