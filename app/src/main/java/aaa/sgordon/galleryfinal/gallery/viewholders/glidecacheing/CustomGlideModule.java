package aaa.sgordon.galleryfinal.gallery.viewholders.glidecacheing;

import android.content.Context;
import android.graphics.Bitmap;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.module.AppGlideModule;

import java.io.InputStream;

import aaa.sgordon.galleryfinal.gallery.viewholders.glidecacheing.cim.ChecksumKeyModel;
import aaa.sgordon.galleryfinal.gallery.viewholders.glidecacheing.cim.ChecksumKeyModelLoader;

@GlideModule
public class CustomGlideModule extends AppGlideModule {
	@Override
	public void registerComponents(@NonNull Context context, @NonNull Glide glide, @NonNull Registry registry) {
		registry.append(ChecksumKeyModel.class, InputStream.class, new ChecksumKeyModelLoader.Factory());
	}
}
