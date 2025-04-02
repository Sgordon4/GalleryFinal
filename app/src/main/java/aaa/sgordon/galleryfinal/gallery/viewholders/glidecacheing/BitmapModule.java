package aaa.sgordon.galleryfinal.gallery.viewholders.glidecacheing;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.Priority;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.data.DataFetcher;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.MultiModelLoaderFactory;
import com.bumptech.glide.module.AppGlideModule;
import com.bumptech.glide.signature.ObjectKey;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import aaa.sgordon.galleryfinal.utilities.MyApplication;

public class BitmapModule {


	public static class CacheIgnoringModel {
		private final String cacheKey;
		private final String uri;

		public CacheIgnoringModel(String cacheKey, String uri) {
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


	public static class CacheIgnoringBitmapLoader implements ModelLoader<CacheIgnoringModel, Bitmap> {

		@Nullable
		@Override
		public LoadData<Bitmap> buildLoadData(@NonNull CacheIgnoringModel model, int width, int height, @NonNull Options options) {
			Uri uri = Uri.parse(model.getUri());
			ObjectKey cacheKey = new ObjectKey(model.getCacheKey());

			DataFetcher<Bitmap> fetcher;
			if ("content".equals(uri.getScheme()) || "file".equals(uri.getScheme())) {
				fetcher = new BitmapLocalUriFetcher(MyApplication.getAppContext().getContentResolver(), uri);
			} else if ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) {
				fetcher = new BitmapHttpFetcher(uri.toString());
			} else {
				return null; // Unsupported scheme
			}

			return new LoadData<>(cacheKey, fetcher);
		}

		@Override
		public boolean handles(@NonNull CacheIgnoringModel model) {
			Uri uri = Uri.parse(model.getUri());
			return ("content".equals(uri.getScheme()) || "file".equals(uri.getScheme())) ||
					("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()));
		}

		public static class Factory implements ModelLoaderFactory<CacheIgnoringModel, Bitmap> {
			@NonNull
			@Override
			public ModelLoader<CacheIgnoringModel, Bitmap> build(@NonNull MultiModelLoaderFactory multiFactory) {
				return new CacheIgnoringBitmapLoader();
			}

			@Override
			public void teardown() {
			}
		}
	}



	//---------------------------------------------------------------------------------------------


	public static class BitmapLocalUriFetcher implements DataFetcher<Bitmap> {
		private final ContentResolver contentResolver;
		private final Uri uri;

		public BitmapLocalUriFetcher(ContentResolver contentResolver, Uri uri) {
			this.contentResolver = contentResolver;
			this.uri = uri;
		}

		@Override
		public void loadData(@NonNull Priority priority, @NonNull DataCallback<? super Bitmap> callback) {
			try {
				InputStream inputStream = contentResolver.openInputStream(uri);
				if (inputStream == null) {
					callback.onLoadFailed(new FileNotFoundException("Failed to open InputStream for: " + uri));
					return;
				}
				Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
				inputStream.close();
				if (bitmap != null) {
					callback.onDataReady(bitmap);
				} else {
					callback.onLoadFailed(new IOException("Failed to decode Bitmap from: " + uri));
				}
			} catch (IOException e) {
				callback.onLoadFailed(e);
			}
		}

		@Override
		public void cleanup() {
			// Nothing to clean up
		}

		@Override
		public void cancel() {
			// Nothing to cancel
		}

		@NonNull
		@Override
		public Class<Bitmap> getDataClass() {
			return Bitmap.class;
		}

		@NonNull
		@Override
		public DataSource getDataSource() {
			return DataSource.LOCAL;
		}
	}




	public static class BitmapHttpFetcher implements DataFetcher<Bitmap> {
		private final String url;

		public BitmapHttpFetcher(String url) {
			this.url = url;
		}

		@Override
		public void loadData(@NonNull Priority priority, @NonNull DataCallback<? super Bitmap> callback) {
			HttpURLConnection connection = null;
			InputStream inputStream = null;
			try {
				URL urlObj = new URL(url);
				connection = (HttpURLConnection) urlObj.openConnection();
				connection.setDoInput(true);
				connection.connect();
				inputStream = connection.getInputStream();
				Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
				if (bitmap != null) {
					callback.onDataReady(bitmap);
				} else {
					callback.onLoadFailed(new IOException("Failed to decode Bitmap from URL: " + url));
				}
			} catch (IOException e) {
				callback.onLoadFailed(e);
			} finally {
				if (inputStream != null) {
					try {
						inputStream.close();
					} catch (IOException ignored) {
					}
				}
				if (connection != null) {
					connection.disconnect();
				}
			}
		}

		@Override
		public void cleanup() {
			// Nothing to clean up
		}

		@Override
		public void cancel() {
			// Nothing to cancel
		}

		@NonNull
		@Override
		public Class<Bitmap> getDataClass() {
			return Bitmap.class;
		}

		@NonNull
		@Override
		public DataSource getDataSource() {
			return DataSource.REMOTE;
		}
	}
}
