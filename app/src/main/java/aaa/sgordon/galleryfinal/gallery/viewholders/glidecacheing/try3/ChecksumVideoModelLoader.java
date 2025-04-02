package aaa.sgordon.galleryfinal.gallery.viewholders.glidecacheing.try3;


import android.graphics.Bitmap;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.MultiModelLoaderFactory;
import com.bumptech.glide.load.model.ResourceLoader;
import com.bumptech.glide.load.resource.bitmap.VideoDecoder;
import com.bumptech.glide.signature.ObjectKey;

import aaa.sgordon.galleryfinal.utilities.MyApplication;

public class ChecksumVideoModelLoader implements ModelLoader<ChecksumVideoModel, Bitmap> {

	private final ModelLoader<Uri, Bitmap> uriModelLoader;

	public ChecksumVideoModelLoader(ModelLoader<Uri, Bitmap> uriModelLoader) {
		this.uriModelLoader = uriModelLoader;
	}

	@Override
	public LoadData<Bitmap> buildLoadData(ChecksumVideoModel model, int width, int height, @NonNull Options options) {
		LoadData<Bitmap> originalLoadData = uriModelLoader.buildLoadData(model.getUri(), width, height, options);

		if (originalLoadData == null) {
			return null;
		}

		// Replace the cache key with the checksum
		return new LoadData<>(new ObjectKey(model.getChecksum()), originalLoadData.fetcher);
	}

	@Override
	public boolean handles(ChecksumVideoModel model) {
		boolean result = uriModelLoader.handles(model.getUri());
		System.out.println("handles: " + result + " for URI: " + model.getUri());
		return uriModelLoader.handles(model.getUri());
	}

	public static class Factory implements ModelLoaderFactory<ChecksumVideoModel, Bitmap> {
		@NonNull
		@Override
		public ModelLoader<ChecksumVideoModel, Bitmap> build(MultiModelLoaderFactory multiFactory) {
			BitmapPool bitmapPool = Glide.get(MyApplication.getAppContext()).getBitmapPool();
			return VideoDecoder.parcel(bitmapPool);
			ModelLoader<Uri, Bitmap> uriModelLoader = multiFactory.build(Uri.class, Bitmap.class);
			System.out.println("Build item: "+uriModelLoader);
			return new ChecksumVideoModelLoader(uriModelLoader);
		}

		@Override
		public void teardown() {
			// No cleanup needed
		}
	}
}

