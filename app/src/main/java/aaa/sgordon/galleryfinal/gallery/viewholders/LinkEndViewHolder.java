package aaa.sgordon.galleryfinal.gallery.viewholders;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.divider.MaterialDivider;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.gallery.ListItem;

public class LinkEndViewHolder extends BaseViewHolder {
	public View child;
	public MaterialDivider divider;

	public LinkEndViewHolder(@NonNull View itemView) {
		super(itemView);

		child = itemView.findViewById(R.id.child);
		divider = itemView.findViewById(R.id.divider);
	}

	@Override
	public void bind(@NonNull ListItem listItem, @Nullable ListItem parent) {
		super.bind(listItem, parent);

		if(listItem.fileProps.userattr.has("color")) {
			divider.setDividerColor(listItem.fileProps.userattr.get("color").getAsInt());
		} else {
			//Get the default card background color from the theme
			TypedValue typedValue = new TypedValue();
			if (itemView.getContext().getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnBackground , typedValue, true)) {
				int defaultBackgroundColor = typedValue.data;
				divider.setDividerColor(defaultBackgroundColor);
			} else {
				divider.setDividerColor(Color.GRAY);
			}
		}



		//Change the border color of the child
		if(parent != null && parent.fileProps.userattr.has("color")) {
			Drawable background = child.getBackground();
			if (background instanceof GradientDrawable) {
				GradientDrawable shapeDrawable = (GradientDrawable) background;
				shapeDrawable.setStroke(4, parent.fileProps.userattr.get("color").getAsInt());
			}
		} else {
			//Reset the default background color
			//We need to do this or the card will retain previous background colors from other items thanks to the RecyclerView
			Drawable background = child.getBackground();
			if (background instanceof GradientDrawable) {
				GradientDrawable shapeDrawable = (GradientDrawable) background;
				shapeDrawable.setStroke(4, Color.TRANSPARENT);
			}
		}
	}
}
