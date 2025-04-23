package aaa.sgordon.galleryfinal.gallery;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Pair;

import androidx.documentfile.provider.DocumentFile;

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import aaa.sgordon.galleryfinal.repository.caches.DirCache;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridAPI;
import aaa.sgordon.galleryfinal.repository.hybrid.types.HFile;
import aaa.sgordon.galleryfinal.utilities.DirUtilities;
import aaa.sgordon.galleryfinal.utilities.Utilities;

public class ImportHelper {
	//Grab all the selected Uris returned by the file picker
	public static List<Uri> getUrisFromIntent(Intent data) {
		List<Uri> urisToImport = new ArrayList<>();

		//If clipData is null, we received only one file. Otherwise, we received multiple files.
		// Don't know why they can't both be an array...
		if(data.getClipData() == null)
			urisToImport.add(data.getData());
		else {
			for(int i = 0; i < data.getClipData().getItemCount(); i++)
				urisToImport.add(data.getClipData().getItemAt(i).getUri());
		}

		return urisToImport;
	}

	public static Map<Uri, DocumentFile> getFileInfoForUris(Context context, List<Uri> urisToImport) {
		//Grab the date for each uri
		Map<Uri, DocumentFile> fileInfo = new HashMap<>();
		for(Uri uri : urisToImport) {
			DocumentFile documentFile = DocumentFile.fromSingleUri(context, uri);
			fileInfo.put(uri, documentFile);
		}
		return fileInfo;
	}

	public static void importFiles(Context context, UUID directoryUID, List<Uri> urisToImport, Map<Uri, DocumentFile> fileInfo) {
		//Sort by date
		urisToImport.sort((uri, t1) -> {
			DocumentFile u1Doc = fileInfo.get(uri);
			DocumentFile u2Doc = fileInfo.get(t1);
			if(u1Doc == null || u2Doc == null) return 0;

			return Long.compare(u1Doc.lastModified(), u2Doc.lastModified());
		});

		//Import the files
		HybridAPI hAPI = HybridAPI.getInstance();
		UUID accountUID = hAPI.getCurrentAccount();

		//Import in reverse to preserve ordering
		//Doing this one by one isn't too bad, since after the first write the dirList is guaranteed to be on local
		for(int i = urisToImport.size()-1; i >= 0; i--) {
			Uri uri = urisToImport.get(i);

			//context.getContentResolver().takePersistableUriPermission(uri,
			//		Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

			//DocumentFile documentFile = fileInfo.get(uri);
			DocumentFile documentFile = DocumentFile.fromSingleUri(context, uri);
			if(documentFile == null) continue;

			UUID newUID;
			try { newUID = hAPI.createFile(accountUID, false, false); }
			catch (IOException e) { throw new RuntimeException(e); }

			try {
				//Write the uri to the new file in the local repo
				hAPI.lockLocal(newUID);
				String checksum = computeChecksum(context, uri);
				hAPI.writeFile(newUID, uri, checksum, HFile.defaultChecksum);


				//Add the new file to the designated directory
				hAPI.lockLocal(directoryUID);
				HFile dirProps = hAPI.getFileProps(directoryUID);

				List<DirItem> dirContents = DirUtilities.readDir(directoryUID);
				dirContents.add(0, new DirItem(newUID, false, false, documentFile.getName()));
				List<String> newLines = dirContents.stream().map(DirItem::toString).collect(Collectors.toList());
				byte[] newContent = String.join("\n", newLines).getBytes();

				hAPI.writeFile(directoryUID, newContent, dirProps.checksum);
			}
			catch (IOException e) {
				throw new RuntimeException(e);
			}
			finally {
				hAPI.unlockLocal(directoryUID);
				hAPI.unlockLocal(newUID);
			}

			//TODO Uncomment when we want to delete things
			//Now that we've imported the file, delete it from the system
			//documentFile.delete();
		}
	}




	private static String computeChecksum(Context context, Uri uri) {
		try (InputStream inputStream = context.getContentResolver().openInputStream(uri)) {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");

			byte[] buffer = new byte[1024];
			int bytesRead;

			// Read the InputStream and update the MessageDigest
			while ((bytesRead = inputStream.read(buffer)) != -1) {
				digest.update(buffer, 0, bytesRead);
			}

			return Utilities.bytesToHex(digest.digest());
		}
		catch (NoSuchAlgorithmException e) { throw new RuntimeException(e); }
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}


	//TODO Make sure this is put in the cache dir since we changed up how we're accessing files. I didn't check this one.
	//Returns filehash
	private static String importToTempFile(Context context, Path tempFile, Uri uri) throws IOException {
		if(!tempFile.toFile().exists()) {
			Files.createDirectories(tempFile.getParent());
			Files.createFile(tempFile);
		}

		try (InputStream in = context.getContentResolver().openInputStream(uri);
			 DigestInputStream dis = new DigestInputStream(in, MessageDigest.getInstance("SHA-256"));
			 FileOutputStream fileOutputStream = new FileOutputStream(tempFile.toFile())) {

			byte[] dataBuffer = new byte[1024];
			int bytesRead;
			while ((bytesRead = dis.read(dataBuffer, 0, 1024)) != -1) {
				fileOutputStream.write(dataBuffer, 0, bytesRead);
			}

			return Utilities.bytesToHex( dis.getMessageDigest().digest() );
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}
}
