package aaa.sgordon.galleryfinal.gallery.viewholders;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.apache.commons.io.FilenameUtils;

import java.util.UUID;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.gallery.ListItem;

public class UnknownViewHolder extends BaseViewHolder {
	public View color;
	public ImageView image;
	public TextView name;

	public UnknownViewHolder(View itemView) {
		super(itemView);

		color = itemView.findViewById(R.id.color);
		image = itemView.findViewById(R.id.media);
		name = itemView.findViewById(R.id.name);
	}

	@Override
	public void bind(ListItem listItem) {
		super.bind(listItem);

		String fileName = FilenameUtils.removeExtension(listItem.name);
		name.setText(fileName);


		if(listItem.attr.has("color")) {
			color.setBackgroundColor(listItem.attr.get("color").getAsInt());
		} else {
			TypedValue typedValue = new TypedValue();

			//Get the default card background color from the theme
			if (itemView.getContext().getTheme().resolveAttribute(com.google.android.material.R.attr.colorBackgroundFloating , typedValue, true)) {
				int defaultBackgroundColor = typedValue.data;
				color.setBackgroundColor(defaultBackgroundColor);
			} else {
				color.setBackgroundColor(Color.GRAY);
			}
		}
	}
}
