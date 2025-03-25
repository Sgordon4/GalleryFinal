package aaa.sgordon.galleryfinal.repository;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import androidx.documentfile.provider.DocumentFile;

import java.io.BufferedReader;
import java.io.BufferedWriter;
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

	public static Uri createFile(Uri parentUri, String mimeType, String fileName, Context context) {
		try {
			return DocumentsContract.createDocument(
					context.getContentResolver(), parentUri, mimeType, fileName);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	public static Uri findOrCreateDirectory(Uri parentUri, String dirName, Context context) {
		Uri existingDir = findFileUri(parentUri, dirName, context);
		return (existingDir != null) ? existingDir : createDirectory(parentUri, dirName, context);
	}
	public static Uri createDirectory(Uri parentUri, String dirName, Context context) {
		try {
			return DocumentsContract.createDocument(
					context.getContentResolver(), parentUri,
					DocumentsContract.Document.MIME_TYPE_DIR, dirName);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}




	public static Uri guessFileUri(Uri directoryUri, String fileName) {
		String dirDocId = DocumentsContract.getDocumentId(directoryUri);
		String fileDocId = dirDocId + "/" + fileName; // This assumes standard ID formatting
		return DocumentsContract.buildDocumentUriUsingTree(directoryUri, fileDocId);
	}
	/*
	public static boolean fileExists(Uri fileUri, Context context) {
		try (InputStream ignored = context.getContentResolver().openInputStream(fileUri)) {
			return true;
		} catch (IllegalArgumentException | IOException e) {
			return false;
		}
	}
	 */


	public static Uri findFileUri(Uri parentUri, String fileName, Context context) {
		Uri guessedUri = guessFileUri(parentUri, fileName);
		return fileExists(guessedUri, context) ? guessedUri : null;
	}
	public static boolean fileExists(Uri fileUri, Context context) {
		// If the cursor has data, the directory exists
		try (Cursor cursor = context.getContentResolver().query(
				fileUri,
				new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID},
				null, null, null)) {
			return cursor != null && cursor.getCount() > 0;
		}
	}








	//------------------------------------------------------




	public static boolean directoryExists(Uri directoryUri, Context context) {
		ContentResolver resolver = context.getContentResolver();

		try (Cursor cursor = resolver.query(
				directoryUri,
				new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID},
				null, null, null)) {
			return cursor != null && cursor.getCount() > 0; // If the cursor has data, the directory exists
		}
	}



	public static DocumentFile getFileDirectly(Uri directoryUri, String fileName, Context context) {
		ContentResolver resolver = context.getContentResolver();
		Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(directoryUri,
				DocumentsContract.getDocumentId(directoryUri));

		try (Cursor cursor = resolver.query(childrenUri,
				new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME},
				null, null, null)) {

			if (cursor != null) {
				while (cursor.moveToNext()) {
					String documentId = cursor.getString(0);
					String name = cursor.getString(1);

					if (fileName.equals(name)) {
						Uri fileUri = DocumentsContract.buildDocumentUriUsingTree(directoryUri, documentId);
						return DocumentFile.fromSingleUri(context, fileUri);
					}
				}
			}
		}
		return null;
	}





	public static DocumentFile findFileInDirectory(Uri directoryUri, String fileName, Context context) {
		ContentResolver resolver = context.getContentResolver();
		Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(directoryUri, DocumentsContract.getDocumentId(directoryUri));

		try (Cursor cursor = resolver.query(
				childrenUri,
				new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME},
				DocumentsContract.Document.COLUMN_DISPLAY_NAME + " = ?",
				new String[]{fileName},
				null)) {

			if (cursor != null && cursor.moveToFirst()) {
				String documentId = cursor.getString(0);
				Uri fileUri = DocumentsContract.buildDocumentUriUsingTree(directoryUri, documentId);
				return DocumentFile.fromSingleUri(context, fileUri);
			}
		}
		return null;
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
