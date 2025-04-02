package aaa.sgordon.galleryfinal.gallery.viewholders.glidecacheing.cim;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.data.DataFetcher;
import com.bumptech.glide.load.data.HttpUrlFetcher;
import com.bumptech.glide.load.data.StreamLocalUriFetcher;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.MultiModelLoaderFactory;
import com.bumptech.glide.signature.ObjectKey;

import java.io.InputStream;

import aaa.sgordon.galleryfinal.utilities.MyApplication;

public class ChecksumKeyModelLoader implements ModelLoader<ChecksumKeyModel, InputStream> {

	@Nullable
	@Override
	public LoadData<InputStream> buildLoadData(@NonNull ChecksumKeyModel model, int width, int height, @NonNull Options options) {
		String uriString = model.getUri();
		Uri uri = Uri.parse(uriString);
		ObjectKey cacheKey = new ObjectKey(model.getCacheKey());

		DataFetcher<InputStream> fetcher;
		if ("content".equals(uri.getScheme()) || "file".equals(uri.getScheme())) {
			// Use the built-in LocalUriFetcher for content:// and file:// URIs
			fetcher = new StreamLocalUriFetcher(MyApplication.getAppContext().getContentResolver(), uri);
		} else if ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) {
			// Use HttpUrlFetcher for remote URLs
			fetcher = new HttpUrlFetcher(new GlideUrl(model.getUri()), 3000);
		} else {
			return null; // Unsupported scheme
		}

		return new LoadData<>(cacheKey, fetcher);

		//return new LoadData<>(new ObjectKey(model.getCacheKey()), new HttpUrlFetcher(new GlideUrl(model.getUri()), 3000));
	}

	@Override
	public boolean handles(@NonNull ChecksumKeyModel model) {
		Uri uri = Uri.parse(model.getUri());
		return ("content".equals(uri.getScheme()) || "file".equals(uri.getScheme())) ||
				("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()));
	}

	public static class Factory implements ModelLoaderFactory<ChecksumKeyModel, InputStream> {
		@NonNull
		@Override
		public ModelLoader<ChecksumKeyModel, InputStream> build(@NonNull MultiModelLoaderFactory multiFactory) {
			return new ChecksumKeyModelLoader();
		}

		@Override
		public void teardown() {
		}
	}
}
