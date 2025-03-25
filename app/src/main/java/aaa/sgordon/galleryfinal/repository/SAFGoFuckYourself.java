package aaa.sgordon.galleryfinal.repository;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;


/*
This FUCKING GODDAMN BULLSHIT framework that Android dragged out from the depths of their dumbest think-tank
has some of the ABSOLUTE FUCKING WORST performance and functionality I have ever seen for a mandatory API.
I am not dealing with the fucking atrocious slowdown incurred by using DocumentFile's bullshit,
so these are alternatives.

Also, why the FUCK does contentResolver's query not fucking work. Legitimately unreal.
*/

public class SAFGoFuckYourself {


	public static Uri makeDocUriFromRoot(@NonNull Uri rootTreeUri, String... segments) {
		String rootTreeDocID = DocumentsContract.getTreeDocumentId(rootTreeUri);

		String name = "";
		if(segments.length > 0)
			name = "/"+String.join("/", segments);

		return DocumentsContract.buildDocumentUriUsingTree(rootTreeUri, rootTreeDocID + name);
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



	public static void createFile(Context context, Uri fileDocUri) throws FileNotFoundException {
		if(fileExists(context, fileDocUri)) return;

		//If there is no parent, we're trying to create a top-level file, which we can't do
		Uri parentUri = getParentFromDocUri(fileDocUri);
		if(parentUri == null) throw new IllegalArgumentException("Cannot create top-level file: "+fileDocUri);

		//If the parent doesn't exist, create that
		if(!directoryExists(context, parentUri))
			createDirectory(context, parentUri);

		String fileName = getNameFromDocUri(fileDocUri);
		Uri uri = createFile(context, parentUri, fileName);
	}
	private static Uri createFile(Context context, Uri parentUri, String dirName) throws FileNotFoundException {
		return DocumentsContract.createDocument(
				context.getContentResolver(), parentUri,
				"*/*", dirName);
	}



	public static void createDirectory(Context context, Uri directoryDocUri) throws FileNotFoundException {
		if(directoryExists(context, directoryDocUri)) return;

		//If there is no parent, we're trying to create a top-level directory, which we can't do
		Uri parentUri = getParentFromDocUri(directoryDocUri);
		if(parentUri == null) throw new IllegalArgumentException("Cannot create top-level dir: "+directoryDocUri);

		//If the parent doesn't exist, create that
		if(!directoryExists(context, parentUri))
			createDirectory(context, parentUri);

		String fileName = getNameFromDocUri(directoryDocUri);
		Uri uri = createDirectory(context, parentUri, fileName);
	}
	private static Uri createDirectory(Context context, Uri parentUri, String dirName) throws FileNotFoundException {
		return DocumentsContract.createDocument(
				context.getContentResolver(), parentUri,
				DocumentsContract.Document.MIME_TYPE_DIR, dirName);
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
		} catch (IllegalArgumentException e) {
			return 0; // File does not exist
		}
		return 0; // Default to non-existing
	}













	public static String readFromFile(Uri fileUri, Context context) throws IOException {
		StringBuilder content = new StringBuilder();
		try (InputStream inputStream = context.getContentResolver().openInputStream(fileUri);
			 BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
			String line;
			while ((line = reader.readLine()) != null) {
				content.append(line).append("\n");
			}
		}
		return content.toString();
	}

	public static void writeToFile(Uri fileUri, String content, Context context) throws IOException {
		try (OutputStream outputStream = context.getContentResolver().openOutputStream(fileUri);
			 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream))) {
			writer.write(content);
		}
	}
}
