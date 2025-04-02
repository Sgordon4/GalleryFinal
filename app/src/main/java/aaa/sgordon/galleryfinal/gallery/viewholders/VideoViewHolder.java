package aaa.sgordon.galleryfinal.gallery.viewholders;

import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.util.Pair;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.Key;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.model.stream.UrlLoader;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.signature.ObjectKey;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ConnectException;
import java.security.MessageDigest;
import java.util.HashMap;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.gallery.ListItem;
import aaa.sgordon.galleryfinal.gallery.viewholders.glidecacheing.BitmapModule;
import aaa.sgordon.galleryfinal.gallery.viewholders.glidecacheing.NoCacheUrlGlideModule;
import aaa.sgordon.galleryfinal.repository.caches.LinkCache;
import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridAPI;

public class VideoViewHolder extends BaseViewHolder {
	public ImageView image;

	public VideoViewHolder(View itemView) {
		super(itemView);

		image = itemView.findViewById(R.id.media);
	}

	@Override
	public void bind(ListItem listItem) {
		super.bind(listItem);

		//We can't call getFileContent without a thread, so just Load a placeholder here
		Glide.with(image.getContext())
				.load(R.drawable.ic_launcher_foreground)
				.into(image);


		Thread thread = new Thread(() -> {
			HybridAPI hAPI = HybridAPI.getInstance();
			try {
				//We are trying to get the correct content Uri for this file
				Uri content;

				LinkCache linkCache = LinkCache.getInstance();
				LinkCache.LinkTarget target = linkCache.getFinalTarget(listItem.fileUID);

				String cacheKey;

				//If the target is null, the item is not a link. Get the content uri from the fileUID's content
				if (target == null) {
					Pair<Uri, String> contentInfo = hAPI.getFileContent(listItem.fileUID);
					content = contentInfo.first;
					cacheKey = "THUMB_"+contentInfo.second;
				}
				//If the target is internal, get the content uri from that fileUID's content
				else if (target instanceof LinkCache.InternalTarget) {
					Pair<Uri, String> contentInfo = hAPI.getFileContent(((LinkCache.InternalTarget) target).getFileUID());
					content = contentInfo.first;
					cacheKey = "THUMB_"+contentInfo.second;
				}
				//If the target is external, get the content uri from the target
				else {//if(target instanceof LinkCache.ExternalTarget) {
					content = ((LinkCache.ExternalTarget) target).getUri();
					cacheKey = "THUMB_"+content;
				}


				//TODO Figure out if this is downloading the entire video from external uris or not
				Handler mainHandler = new Handler(image.getContext().getMainLooper());
				mainHandler.post(() -> {


					Glide.with(image.getContext())
							.asBitmap()
							.load(content)
							.signature(new CustomCacheKey(cacheKey))
							.diskCacheStrategy(DiskCacheStrategy.RESOURCE)	//Only cache the transformed image
							.centerCrop()
							.override(150, 150)
							.placeholder(R.drawable.ic_launcher_foreground)
							.error(R.drawable.ic_launcher_background)
							.listener(new RequestListener<Bitmap>() {
								@Override
								public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Bitmap> target, boolean isFirstResource) {
									System.out.println("Video load failed");
									return false;
								}

								@Override
								public boolean onResourceReady(Bitmap resource, Object model, Target<Bitmap> target, DataSource dataSource, boolean isFirstResource) {
									System.out.println("Video datasource: "+dataSource);
									return false;
								}
							})
							.into(image);

					/*
					//Load from url, ignoring the url and only considering the key when caching
					BitmapModule.CacheIgnoringModel model = new BitmapModule.CacheIgnoringModel(cacheKey, content.toString());

					//If the initial load from cache fails, load from the actual uri
					RequestBuilder<Bitmap> normalLoad = Glide.with(image.getContext())
							.asBitmap()
							.load(getVideoThumbnail(content.toString()))
							.signature(new ObjectKey(cacheKey))
							.diskCacheStrategy(DiskCacheStrategy.RESOURCE)	//Only cache the transformed image
							.centerCrop()
							.override(150, 150)
							.error(R.drawable.ic_launcher_background)
							.skipMemoryCache(true); 						//Prevent memory cache interfering

					//Attempt to load the file from the cache only
					Glide.with(image.getContext())
							.load(model)
							.signature(new ObjectKey(cacheKey))
							.diskCacheStrategy(DiskCacheStrategy.ALL)
							.onlyRetrieveFromCache(true)				//Try loading from the cache only
							.centerCrop()
							.override(150, 150)
							.placeholder(R.drawable.ic_launcher_foreground)
							.error(normalLoad)								//If cache misses, load normally
							.into(image);
					/**/


					/* Original
					Glide.with(image.getContext())
							.asBitmap()
							.load(content)
							.signature(new ObjectKey(cacheKey))
							.diskCacheStrategy(DiskCacheStrategy.RESOURCE)	//Only cache the transformed image
							.centerCrop()
							.override(150, 150)
							.placeholder(R.drawable.ic_launcher_foreground)
							.error(R.drawable.ic_launcher_background)
							.into(image));
					 */
				});

			}
			catch (ContentsNotFoundException | FileNotFoundException | ConnectException e) {
				//Do nothing
			}
		});
		thread.start();
	}


	@Nullable
	private Bitmap getVideoThumbnail(String videoUri) {
		try (MediaMetadataRetriever retriever = new MediaMetadataRetriever()){
			retriever.setDataSource(videoUri, new HashMap<>());

			return retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}



	public static class CustomCacheKey implements Key {
		private final String contentId; // Unique identifier for the content

		public CustomCacheKey(String contentId) {
			this.contentId = contentId;
		}

		@Override
		public String toString() {
			return contentId;
		}

		@Override
		public void updateDiskCacheKey(@NonNull MessageDigest messageDigest) {
			messageDigest.update(contentId.getBytes());
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof CustomCacheKey && ((CustomCacheKey) obj).contentId.equals(contentId);
		}

		@Override
		public int hashCode() {
			return contentId.hashCode();
		}
	}
}
