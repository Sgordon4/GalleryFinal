package aaa.sgordon.galleryfinal.gallery.viewholders;

import android.net.Uri;
import android.os.Handler;
import android.view.View;
import android.widget.ImageView;

import com.bumptech.glide.Glide;

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
				UUID fileUID = linkCache.resolvePotentialLink(listItem.fileUID);

				//If the file is a link, we have an external link
				if(linkCache.isLink(fileUID)) {
					LinkCache.ExternalTarget target = (LinkCache.ExternalTarget) linkCache.getLinkTarget(fileUID);
					content = target.getUri();
				}
				else {
					content = hAPI.getFileContent(fileUID).first;
				}


				Handler mainHandler = new Handler(image.getContext().getMainLooper());
				mainHandler.post(() ->
					Glide.with(image.getContext())
						.asBitmap()
						.load(content)
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
