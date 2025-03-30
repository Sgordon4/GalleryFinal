package aaa.sgordon.galleryfinal.gallery.viewholders;

import android.net.Uri;
import android.os.Handler;
import android.util.Pair;
import android.view.View;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.signature.ObjectKey;

import java.io.FileNotFoundException;
import java.net.ConnectException;
import java.util.UUID;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.gallery.ListItem;
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
				mainHandler.post(() ->
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
			}
			catch (ContentsNotFoundException | FileNotFoundException | ConnectException e) {
				//Do nothing
			}
		});
		thread.start();
	}
}
