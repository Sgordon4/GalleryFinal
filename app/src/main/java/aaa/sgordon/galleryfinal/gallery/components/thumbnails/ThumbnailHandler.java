package aaa.sgordon.galleryfinal.gallery.components.thumbnails;

import android.graphics.Bitmap;
import android.net.Uri;

import androidx.annotation.NonNull;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import aaa.sgordon.galleryfinal.repository.galleryhelpers.SAFGoFuckYourself;
import aaa.sgordon.galleryfinal.repository.local.types.LContent;
import aaa.sgordon.galleryfinal.utilities.MyApplication;
import aaa.sgordon.galleryfinal.utilities.Utilities;

public class ThumbnailHandler {
	private static final String TAG = "Gal.Thumb";
	private static final String thumbnailDir = "thumbnails";
	private Uri storageDir;



	public Uri getThumbnailUri(@NonNull String name) {
		//We're using SHA-256 hashes for the names, split them into subfolders for efficiency
		String subFolder = name.substring(0, 2);

		return SAFGoFuckYourself.makeDocUriFromDocUri(storageDir, thumbnailDir, subFolder, name);
	}

	public void writeContents(@NonNull String name, @NonNull Bitmap resource) throws IOException {
		Uri thumbnailUri = getThumbnailUri(name);

		//Create the file if it does not already exist, along with all parent directories
		SAFGoFuckYourself.createFile(MyApplication.getAppContext(), thumbnailUri);

		try (OutputStream out = MyApplication.getAppContext().getContentResolver().openOutputStream(thumbnailUri);
			 BufferedOutputStream bos = new BufferedOutputStream(out);) {

			//bos.write(resource);
			//String fileHash = Utilities.bytesToHex(dos.getMessageDigest().digest());

			//return new LContent(name, fileHash, contents.length);
		}



	}










	//----------------------------------------------------
	private static ThumbnailHandler instance;
	public static synchronized void initialize(String storageDir) {
		if (instance == null) instance = new ThumbnailHandler(storageDir);
	}
	private ThumbnailHandler(String storageDir) {
		this.storageDir = Uri.parse(storageDir);
	}

	public static ThumbnailHandler getInstance() {
		if (instance == null)
			throw new IllegalStateException("ThumbnailHandler is not initialized. Call initialize() first.");
		return instance;
	}
	public static void destroyInstance() {
		instance = null;
	}
	//----------------------------------------------------
}
