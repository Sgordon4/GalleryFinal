package aaa.sgordon.galleryfinal.gallery.viewholders.glidecacheing;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.resource.bitmap.VideoBitmapDecoder;
import com.bumptech.glide.load.resource.bitmap.VideoDecoder;
import com.bumptech.glide.module.AppGlideModule;

import java.io.InputStream;

import aaa.sgordon.galleryfinal.gallery.viewholders.glidecacheing.cim.CacheIgnoringModel;
import aaa.sgordon.galleryfinal.gallery.viewholders.glidecacheing.cim.CacheIgnoringModelLoader;
import aaa.sgordon.galleryfinal.gallery.viewholders.glidecacheing.try3.ChecksumVideoModel;
import aaa.sgordon.galleryfinal.gallery.viewholders.glidecacheing.try3.ChecksumVideoModelLoader;

@GlideModule
public class CustomGlideModule extends AppGlideModule {
	@Override
	public void registerComponents(@NonNull Context context, @NonNull Glide glide, @NonNull Registry registry) {
		// Ensure Glide has the default Uri loader
		BitmapPool bitmapPool = Glide.get(context).getBitmapPool();
		registry.append(Uri.class, Bitmap.class, new VideoDecoder(bitmapPool));

		registry.append(ChecksumVideoModel.class, Bitmap.class, new ChecksumVideoModelLoader.Factory());
		registry.append(CacheIgnoringModel.class, InputStream.class, new CacheIgnoringModelLoader.Factory());
	}
}
