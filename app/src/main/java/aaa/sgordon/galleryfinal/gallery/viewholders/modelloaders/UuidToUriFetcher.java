package aaa.sgordon.galleryfinal.gallery.viewholders.modelloaders;

import android.net.Uri;

import androidx.annotation.NonNull;

import com.bumptech.glide.Priority;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.data.DataFetcher;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.Executors;

import aaa.sgordon.galleryfinal.repository.gallery.caches.LinkCache;
import aaa.sgordon.galleryfinal.utilities.MyApplication;

public class UuidToUriFetcher implements DataFetcher<InputStream> {
	private final UUID uuid;

	private InputStream stream;

	public UuidToUriFetcher(UUID uuid) {
		this.uuid = uuid;
	}

	@Override
	public void loadData(@NonNull Priority priority, @NonNull DataCallback<? super InputStream> callback) {
		Executors.newSingleThreadExecutor().execute(() -> {
			try {
				Uri uri = LinkCache.getInstance().getContentInfo(uuid).first;

				//If the file can be opened using ContentResolver, do that. Otherwise, open using URL's openStream
				try {
					stream = MyApplication.getAppContext().getContentResolver().openInputStream(uri);
				} catch (FileNotFoundException e) {
					stream = new URL(uri.toString()).openStream();
				}
				if (stream == null) {
					callback.onLoadFailed(new IOException("Could not open stream for URI: " + uri));
					return;
				}

				callback.onDataReady(stream);
			} catch (Exception e) {
				callback.onLoadFailed(e);
			}
		});
	}

	@Override
	public void cleanup() {
		try {
			if (stream != null) stream.close();
		} catch (IOException ignored) {}
	}

	@Override
	public void cancel() {}

	@NonNull
	@Override
	public Class<InputStream> getDataClass() {
		return InputStream.class;
	}

	@NonNull
	@Override
	public DataSource getDataSource() {
		return DataSource.REMOTE;
	}
}
