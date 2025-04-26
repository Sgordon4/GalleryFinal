package aaa.sgordon.galleryfinal.gallery.viewholders;

import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.repository.gallery.ListItem;

public class GifViewHolder extends BaseViewHolder {
	public View child;
	public ImageView image;

	public GifViewHolder(View itemView) {
		super(itemView);

		child = itemView.findViewById(R.id.child);
		image = itemView.findViewById(R.id.media);
	}

	@Override
	public void bind(@NonNull ListItem listItem, @Nullable ListItem parent) {
		super.bind(listItem, parent);

		ColorUtil.setBorderColor(null, child);
		if(parent != null) ColorUtil.setBorderColorAsync(parent.fileUID, child);


		//Using a custom modelLoader to handle HybridAPI FileUIDs
		Glide.with(image.getContext())
				.load(listItem.fileUID)
				//.diskCacheStrategy(DiskCacheStrategy.RESOURCE)	//Only cache the transformed image
				.diskCacheStrategy(DiskCacheStrategy.ALL)
				.centerCrop()
				.override(150, 150)
				.into(image);


		/* Original
		Glide.with(image.getContext())
				//.asBitmap()
				//.asGif()	//TODO Remove
				.load(model)
				.diskCacheStrategy(DiskCacheStrategy.ALL)	//Only cache the transformed image
				.centerCrop()
				.override(150, 150)
				.placeholder(R.drawable.ic_launcher_foreground)
				.error(R.drawable.ic_launcher_background)
				.into(image);
		*/
	}
}
