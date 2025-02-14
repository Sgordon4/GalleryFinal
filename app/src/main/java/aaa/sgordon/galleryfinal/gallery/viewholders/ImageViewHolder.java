package aaa.sgordon.galleryfinal.gallery.viewholders;

import android.net.Uri;
import android.os.Handler;
import android.util.Pair;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import java.io.FileNotFoundException;
import java.net.ConnectException;
import java.util.UUID;
import java.util.concurrent.Callable;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridAPI;

public class ImageViewHolder extends BaseViewHolder {
	public ImageView image;

	public ImageViewHolder(View itemView) {
		super(itemView);

		image = itemView.findViewById(R.id.image);
	}

	@Override
	public void bind(UUID fileUID, String fileName) {
		super.bind(fileUID, fileName);

		//We can't call getFileContent without a thread, so just Load a placeholder here
		Glide.with(image.getContext())
				.load(R.drawable.ic_launcher_foreground)
				.into(image);


		Thread thread = new Thread(() -> {
			HybridAPI hAPI = HybridAPI.getInstance();
			try {
				Uri content = hAPI.getFileContent(fileUID).first;

				Handler mainHandler = new Handler(image.getContext().getMainLooper());
				mainHandler.post(() ->
					Glide.with(image.getContext())
						.load(content)
						.centerCrop()
						//.override(150, 150)
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
