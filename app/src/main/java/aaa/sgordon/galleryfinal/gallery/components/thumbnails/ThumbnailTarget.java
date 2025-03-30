package aaa.sgordon.galleryfinal.gallery.components.thumbnails;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;


public class ThumbnailTarget extends CustomTarget<Bitmap> {
	private final ImageView imageView;

	public ThumbnailTarget(ImageView imageView) {
		this.imageView = imageView;
	}


	@Override
	public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
		imageView.setImageBitmap(resource);

		//TODO Save the thumbnail
	}

	@Override
	public void onLoadCleared(@Nullable Drawable placeholder) {
		imageView.setImageDrawable(placeholder);
	}
}