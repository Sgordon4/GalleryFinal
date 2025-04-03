package aaa.sgordon.galleryfinal.gallery.viewholders;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.apache.commons.io.FilenameUtils;

import java.util.UUID;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.gallery.ListItem;
import aaa.sgordon.galleryfinal.utilities.Utilities;

public class UnknownViewHolder extends BaseViewHolder {
	public View wrapper;
	public View color;
	public ImageView image;
	public TextView name;

	public UnknownViewHolder(View itemView) {
		super(itemView);

		wrapper = itemView.findViewById(R.id.wrapper);
		color = itemView.findViewById(R.id.color);
		image = itemView.findViewById(R.id.media);
		name = itemView.findViewById(R.id.name);
	}

	@Override
	public void bind(@NonNull ListItem listItem, @Nullable ListItem parent) {
		super.bind(listItem, parent);

		String fileName = FilenameUtils.removeExtension(listItem.name);
		name.setText(fileName);


		//Change the background color of the card
		if(listItem.attr.has("color")) {
			color.setBackgroundColor(listItem.attr.get("color").getAsInt());
		} else {
			TypedValue typedValue = new TypedValue();

			//Get the default card background color from the theme
			//We need to do this or the card will retain previous background colors from other items thanks to the RecyclerView
			if (itemView.getContext().getTheme().resolveAttribute(com.google.android.material.R.attr.colorBackgroundFloating , typedValue, true)) {
				int defaultBackgroundColor = typedValue.data;
				color.setBackgroundColor(defaultBackgroundColor);
			} else {
				color.setBackgroundColor(Color.GRAY);
			}
		}


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
	}
}
