package aaa.sgordon.galleryfinal.gallery.viewholders;

import android.content.res.TypedArray;
import android.graphics.Color;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.apache.commons.io.FilenameUtils;

import java.util.UUID;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.gallery.ListItem;

public class LinkViewHolder extends BaseViewHolder {
	public TextView name;

	public LinkViewHolder(@NonNull View itemView) {
		super(itemView);

		name = itemView.findViewById(R.id.name);
	}

	@Override
	public void bind(ListItem listItem) {
		super.bind(listItem);

		String fileName = FilenameUtils.removeExtension(listItem.name);
		name.setText(fileName);

		//Get the default background color from the theme
		try (TypedArray typedArray = itemView.getContext().getTheme()
				.obtainStyledAttributes(new int[]{android.R.attr.windowBackground})) {
			int defaultBackgroundColor = typedArray.getColor(0, Color.GRAY); //Default to Gray

			if(listItem.attr.has("color")) {
				//color.setBackgroundColor(listItem.attr.get("color").getAsInt());
			} else {
				//color.setBackgroundColor(defaultBackgroundColor);
			}
		}
	}
}
