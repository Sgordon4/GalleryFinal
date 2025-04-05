package aaa.sgordon.galleryfinal.gallery.viewholders.modelloaders;

import android.content.Context;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.module.AppGlideModule;

import java.io.InputStream;
import java.util.UUID;

@GlideModule
public class CustomGlideModule extends AppGlideModule {
	@Override
	public void registerComponents(@NonNull Context context, @NonNull Glide glide, @NonNull Registry registry) {
		registry.append(UUID.class, InputStream.class, new UuidModelLoader.Factory());
	}
}
