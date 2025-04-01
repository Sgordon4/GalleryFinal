package aaa.sgordon.galleryfinal.gallery.viewholders.glidecacheing;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.data.DataFetcher;
import com.bumptech.glide.load.data.HttpUrlFetcher;
import com.bumptech.glide.load.data.StreamLocalUriFetcher;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.MultiModelLoaderFactory;
import com.bumptech.glide.module.AppGlideModule;
import com.bumptech.glide.signature.ObjectKey;

import java.io.InputStream;

import aaa.sgordon.galleryfinal.utilities.MyApplication;

@GlideModule
public class NoCacheUrlGlideModule extends AppGlideModule {
	@Override
	public void registerComponents(@NonNull Context context, @NonNull Glide glide, @NonNull Registry registry) {
		registry.append(UrlIgnoringModel.class, InputStream.class, new CacheIgnoringModelLoader.Factory());
	}


	public static class UrlIgnoringModel {
		private final String cacheKey;
		private final String uri;

		public UrlIgnoringModel(String cacheKey, String uri) {
			this.cacheKey = cacheKey;
			this.uri = uri;
		}

		public String getCacheKey() {
			return cacheKey;
		}

		public String getUri() {
			return uri;
		}
	}


	public static class CacheIgnoringModelLoader implements ModelLoader<UrlIgnoringModel, InputStream> {

		@Nullable
		@Override
		public LoadData<InputStream> buildLoadData(@NonNull UrlIgnoringModel model, int width, int height, @NonNull Options options) {
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
		public boolean handles(@NonNull UrlIgnoringModel model) {
			return true;
		}

		public static class Factory implements ModelLoaderFactory<UrlIgnoringModel, InputStream> {
			@NonNull
			@Override
			public ModelLoader<UrlIgnoringModel, InputStream> build(@NonNull MultiModelLoaderFactory multiFactory) {
				return new CacheIgnoringModelLoader();
			}

			@Override
			public void teardown() {
			}
		}
	}

}
