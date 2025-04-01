package aaa.sgordon.galleryfinal.gallery.viewholders;

import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.util.Pair;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.signature.ObjectKey;

import java.io.FileNotFoundException;
import java.net.ConnectException;
import java.util.UUID;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.gallery.ListItem;
import aaa.sgordon.galleryfinal.gallery.viewholders.glidecacheing.NoCacheUrlGlideModule;
import aaa.sgordon.galleryfinal.repository.caches.LinkCache;
import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridAPI;
import aaa.sgordon.galleryfinal.repository.hybrid.types.HFile;

public class ImageViewHolder extends BaseViewHolder {
	public ImageView image;

	public ImageViewHolder(View itemView) {
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


				Handler mainHandler = new Handler(image.getContext().getMainLooper());
				mainHandler.post(() -> {
					//Load from url, ignoring the url and only considering the key when caching
					NoCacheUrlGlideModule.UrlIgnoringModel model = new NoCacheUrlGlideModule.UrlIgnoringModel(cacheKey, content.toString());

					//If the initial load from cache fails, load from the actual uri
					RequestBuilder<Drawable> normalLoad = Glide.with(image.getContext())
							.load(model)
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


					/* Original
					Glide.with(image.getContext())
							.load(content)
							.signature(new ObjectKey(cacheKey))
							.diskCacheStrategy(DiskCacheStrategy.RESOURCE)	//Only cache the transformed image
							.override(150, 150)
							.centerCrop()
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
}
