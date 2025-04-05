package aaa.sgordon.galleryfinal.gallery.viewholders;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
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
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.signature.ObjectKey;

import java.io.FileNotFoundException;
import java.net.ConnectException;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.gallery.ListItem;
import aaa.sgordon.galleryfinal.gallery.viewholders.glidecacheing.cim.ChecksumKeyModel;
import aaa.sgordon.galleryfinal.repository.caches.LinkCache;
import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridAPI;

public class ImageViewHolder extends BaseViewHolder {
	public View wrapper;
	public ImageView image;

	public ImageViewHolder(View itemView) {
		super(itemView);

		wrapper = itemView.findViewById(R.id.wrapper);
		image = itemView.findViewById(R.id.media);
	}

	@Override
	public void bind(@NonNull ListItem listItem, @Nullable ListItem parent) {
		super.bind(listItem, parent);

		//Change the border color of the wrapper
		if(parent != null && parent.attr.has("color")) {
			Drawable background = wrapper.getBackground();
			if (background instanceof GradientDrawable) {
				GradientDrawable shapeDrawable = (GradientDrawable) background;
				shapeDrawable.setStroke(4, parent.attr.get("color").getAsInt());
			}
		} else {
			//Reset the default background color
			//We need to do this or the card will retain previous background colors from other items thanks to the RecyclerView
			Drawable background = wrapper.getBackground();
			if (background instanceof GradientDrawable) {
				GradientDrawable shapeDrawable = (GradientDrawable) background;
				shapeDrawable.setStroke(4, Color.TRANSPARENT);
			}
		}


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
