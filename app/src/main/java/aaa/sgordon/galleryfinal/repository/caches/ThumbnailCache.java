package aaa.sgordon.galleryfinal.repository.caches;

import android.content.Context;
import android.media.ThumbnailUtils;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Size;

import java.io.File;
import java.util.UUID;

import aaa.sgordon.galleryfinal.repository.hybrid.HybridAPI;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridListeners;

public class ThumbnailCache {
	private static final String thumbnailDir = "thumbnails";
	private final String storageDir;

	private final HybridAPI hAPI;
	private final HybridListeners.FileChangeListener fileChangeListener;

	private static ThumbnailCache instance;
	public static ThumbnailCache getInstance() {
		if (instance == null)
			throw new IllegalStateException("LocalRepo is not initialized. Call initialize() first.");
		return instance;
	}

	public static synchronized void initialize(Context context) {
		if (instance == null) instance = new ThumbnailCache(context.getCacheDir().toString());
	}
	public static synchronized void initialize(String storageDir) {
		if (instance == null) instance = new ThumbnailCache(storageDir);
	}
	private ThumbnailCache(String storageDir) {
		this.storageDir = storageDir;
		this.hAPI = HybridAPI.getInstance();

		//Whenever any file changes, remove the thumbnail
		fileChangeListener = uuid -> {
			File thumbnailFile = getThumbnailLocationOnDisk(uuid);
			thumbnailFile.delete();
		};
		hAPI.addListener(fileChangeListener);
	}



	public boolean thumbnailExists(UUID fileUID) {
		File thumbnailFile = getThumbnailLocationOnDisk(fileUID);
		return thumbnailFile.exists();
	}


	@NonNull
	private File getThumbnailLocationOnDisk(UUID fileUID) {
		//Thumbnails are stored in a thumbnail subdirectory
		File contentRoot = new File(storageDir, thumbnailDir);

		//With each thumbnail file named by its fileUID
		return new File(contentRoot, fileUID.toString());
	}
}
