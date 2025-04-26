package aaa.sgordon.galleryfinal.gallery.viewholders;

import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.repository.gallery.ListItem;

public class ImageViewHolder extends BaseViewHolder {
	public View child;
	public ImageView image;

	public ImageViewHolder(View itemView) {
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
				.error(new ColorDrawable(ContextCompat.getColor(image.getContext(), R.color.gray)))
				//.diskCacheStrategy(DiskCacheStrategy.RESOURCE)	//Only cache the transformed image
				.diskCacheStrategy(DiskCacheStrategy.ALL)
				.centerCrop()
				.override(150, 150)
				.dontAnimate()
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
	}
}
