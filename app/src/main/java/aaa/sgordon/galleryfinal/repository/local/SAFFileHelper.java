package aaa.sgordon.galleryfinal.repository.local;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import androidx.documentfile.provider.DocumentFile;
import java.io.OutputStream;

//Thanks ChatGPT for the code!
//SAF is a pain in the ass!
public class SAFFileHelper {

	// Ensures parent directories exist and creates the file
	public static DocumentFile getOrCreateFile(Context context, Uri rootUri, String relativePath, String mimeType) {
		DocumentFile rootDir = DocumentFile.fromTreeUri(context, rootUri);
		if (rootDir == null || !rootDir.exists()) {
			Log.e("SAFFileHelper", "Root directory is inaccessible.");
			return null;
		}

		// Split path into directories & file
		String[] parts = relativePath.split("/");
		DocumentFile currentDir = rootDir;

		// Traverse and create directories if needed
		for (int i = 0; i < parts.length - 1; i++) {
			String folderName = parts[i];
			DocumentFile nextDir = findFile(currentDir, folderName);
			if (nextDir == null)
				nextDir = currentDir.createDirectory(folderName);
			if (nextDir == null) {
				Log.e("SAFFileHelper", "Failed to create/access directory: " + folderName);
				return null;
			}
			currentDir = nextDir;
		}

		// Create file if it doesn't exist
		String fileName = parts[parts.length - 1];
		DocumentFile existingFile = findFile(currentDir, fileName);
		if (existingFile != null) return existingFile; // Return existing file

		return currentDir.createFile(mimeType, fileName); // Create new file
	}

	// Finds a file or directory within a parent directory
	private static DocumentFile findFile(DocumentFile parent, String name) {
		for (DocumentFile file : parent.listFiles()) {
			if (file.getName() != null && file.getName().equals(name)) {
				return file;
			}
		}
		return null;
	}

	// Writes content to the file
	public static boolean writeToFile(Context context, DocumentFile file, byte[] content) {
		if (file == null || !file.exists()) return false;

		try (OutputStream outputStream = context.getContentResolver().openOutputStream(file.getUri())) {
			outputStream.write(content);
			return true;
		} catch (Exception e) {
			Log.e("SAFFileHelper", "Error writing file", e);
			return false;
		}
	}
}
