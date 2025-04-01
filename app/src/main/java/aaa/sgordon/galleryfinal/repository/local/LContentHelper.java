package aaa.sgordon.galleryfinal.repository.local;

import android.net.Uri;

import androidx.annotation.NonNull;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import aaa.sgordon.galleryfinal.repository.galleryhelpers.SAFGoFuckYourself;
import aaa.sgordon.galleryfinal.repository.galleryhelpers.MainStorageHandler;
import aaa.sgordon.galleryfinal.utilities.MyApplication;
import aaa.sgordon.galleryfinal.utilities.Utilities;
import aaa.sgordon.galleryfinal.repository.local.types.LContent;

//TODO Move the content out of the data dir in case the app is deleted

public class LContentHelper {
	private static final String TAG = "Hyb.Local.Cont";
	private static final String contentDir = "content";
	private final Uri storageDir;

	public LContentHelper(@NonNull Uri storageDir) {
		//Contents are stored in the app's data directory
		this.storageDir = storageDir;
	}


	/*
	//WARNING: This method does not create the file or parent directory, it only provides the location
	@NonNull
	private File getContentLocationOnDisk(@NonNull String hash) {
		//Content is stored in a content subdirectory
		File contentRoot = new File(storageDir.getPath(), contentDir);

		//With each content file named by its SHA256 hash
		return new File(contentRoot, hash);
	}
	/**/



	//---------------------------------------------------------------------------------------------


	//WARNING: This method does not create the file or parent directories, it only provides the location
	@NonNull
	public Uri getContentUri(@NonNull String name) {
		//File contents = getContentLocationOnDisk(name);
		//return Uri.fromFile(contents);

		//Since we're using SHA-256 hashes for the content, and we'll have a lot of them, split them into subfolders for efficiency
		String subFolder = name.substring(0, 2);

		return SAFGoFuckYourself.makeDocUriFromDocUri(storageDir, contentDir, subFolder, name);
	}


	public LContent writeContents(@NonNull String name, @NonNull byte[] contents) throws IOException {
		Uri contentUri = getContentUri(name);

		//Create the file if it does not already exist, along with all parent directories
		SAFGoFuckYourself.createFile(MyApplication.getAppContext(), contentUri);

		try (OutputStream out = MyApplication.getAppContext().getContentResolver().openOutputStream(contentUri);
			 DigestOutputStream dos = new DigestOutputStream(out, MessageDigest.getInstance("SHA-256"))) {

			dos.write(contents);
			String fileHash = Utilities.bytesToHex(dos.getMessageDigest().digest());

			return new LContent(name, fileHash, contents.length);
		}
		catch (NoSuchAlgorithmException e) { throw new RuntimeException(e); }
	}


	public LContent writeContents(@NonNull String name, @NonNull Uri source) throws IOException {
		Uri contentUri = getContentUri(name);

		//Create the file if it does not already exist, along with all parent directories
		SAFGoFuckYourself.createFile(MyApplication.getAppContext(), contentUri);


		InputStream in = null;
		try {
			//If the file can be opened using ContentResolver, do that. Otherwise, open using URL's openStream
			try {
				in = MyApplication.getAppContext().getContentResolver().openInputStream(source);
			} catch (FileNotFoundException e) {
				in = new URL(source.toString()).openStream();
			}

			//Write the source data to the destination file
			try (OutputStream out = MyApplication.getAppContext().getContentResolver().openOutputStream(contentUri);
				 DigestOutputStream dos = new DigestOutputStream(out, MessageDigest.getInstance("SHA-256"))) {

				byte[] dataBuffer = new byte[1024];
				int bytesRead;
				while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
					dos.write(dataBuffer, 0, bytesRead);
				}

				String fileHash = Utilities.bytesToHex(dos.getMessageDigest().digest());
				int fileSize = SAFGoFuckYourself.getFileSize(MyApplication.getAppContext(), contentUri);

				return new LContent(name, fileHash, fileSize);
			}
			catch (NoSuchAlgorithmException e) { throw new RuntimeException(e); }

		} finally {
			if(in != null) in.close();
		}
	}


	public void deleteContents(@NonNull String name) {
		Uri contentUri = getContentUri(name);
		boolean del = SAFGoFuckYourself.delete(MyApplication.getAppContext(), contentUri);
	}
}
