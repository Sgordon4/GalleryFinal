package aaa.sgordon.galleryfinal.gallery.viewholders.glidecacheing.hybrid;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.MultiModelLoaderFactory;
import com.bumptech.glide.signature.ObjectKey;

import java.io.InputStream;
import java.util.UUID;

public class UuidModelLoader implements ModelLoader<UUID, InputStream> {

	@Override
	public boolean handles(@NonNull UUID uuid) {
		return true; // Always try to handle UUIDs
	}

	@Nullable
	@Override
	public LoadData<InputStream> buildLoadData(@NonNull UUID uuid, int width, int height, @NonNull Options options) {
		return new LoadData<>(new ObjectKey(uuid), new UuidToUriFetcher(uuid));
	}


	public static class Factory implements ModelLoaderFactory<UUID, InputStream> {

		@NonNull
		@Override
		public ModelLoader<UUID, InputStream> build(@NonNull MultiModelLoaderFactory multiFactory) {
			return new UuidModelLoader();
		}

		@Override
		public void teardown() {

		}
	}
}
