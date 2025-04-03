package aaa.sgordon.galleryfinal.gallery.viewholders;

import android.graphics.Color;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.divider.MaterialDivider;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.gallery.ListItem;

public class LinkEndViewHolder extends BaseViewHolder {
	public MaterialDivider divider;

	public LinkEndViewHolder(@NonNull View itemView) {
		super(itemView);

		divider = itemView.findViewById(R.id.divider);
	}

	@Override
	public void bind(@NonNull ListItem listItem, @Nullable ListItem parent) {
		super.bind(listItem, parent);

		if(listItem.attr.has("color")) {
			divider.setDividerColor(listItem.attr.get("color").getAsInt());
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
	}
}
