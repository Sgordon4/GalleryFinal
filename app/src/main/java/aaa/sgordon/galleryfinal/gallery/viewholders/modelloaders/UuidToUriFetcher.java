package aaa.sgordon.galleryfinal.gallery.viewholders.modelloaders;

import android.net.Uri;
import android.util.Pair;

import androidx.annotation.NonNull;

import com.bumptech.glide.Priority;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.data.DataFetcher;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.Executors;

import aaa.sgordon.galleryfinal.repository.caches.LinkCache;
import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridAPI;
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
				Pair<Uri, String> content = getContentInfo(uuid);
				Uri uri = content.first;

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



	@NonNull
	private Pair<Uri, String> getContentInfo(UUID uuid) throws ContentsNotFoundException, FileNotFoundException, ConnectException {
		HybridAPI hAPI = HybridAPI.getInstance();

		//If the item is a link, the content uri is accessed differently
		LinkCache linkCache = LinkCache.getInstance();
		LinkCache.LinkTarget target = linkCache.getFinalTarget(uuid);


		//If the target is null, the item is not a link. Get the content uri from the fileUID's content
		if (target == null) {
				/*
				HFile props = hAPI.getFileProps(uuid);
				Uri uri = RemoteRepo.getInstance().getContentDownloadUri(props.checksum);
				return new Pair<>(uri, props.checksum);
				 */

			return hAPI.getFileContent(uuid);
		}
		//If the target is internal, get the content uri from that fileUID's content
		else if (target instanceof LinkCache.InternalTarget) {
			return hAPI.getFileContent(((LinkCache.InternalTarget) target).getFileUID());
		}
		//If the target is external, get the content uri from the target
		else {//if(target instanceof LinkCache.ExternalTarget) {
			Uri content = ((LinkCache.ExternalTarget) target).getUri();
			return new Pair<>(content, content.toString());
		}
	}
}
