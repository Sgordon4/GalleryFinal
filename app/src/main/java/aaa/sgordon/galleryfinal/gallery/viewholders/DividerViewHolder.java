package aaa.sgordon.galleryfinal.gallery.viewholders;

import android.content.res.TypedArray;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.apache.commons.io.FilenameUtils;

import java.util.UUID;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.gallery.ListItem;

public class DividerViewHolder extends BaseViewHolder {
	public TextView name;

	public DividerViewHolder(@NonNull View itemView) {
		super(itemView);

		name = itemView.findViewById(R.id.name);
	}

	@Override
	public void bind(@NonNull ListItem listItem, @Nullable ListItem parent) {
		super.bind(listItem, parent);

		String fileName = FilenameUtils.removeExtension(listItem.name);
		name.setText(fileName);

		if(listItem.attr.has("color")) {
			//color.setBackgroundColor(listItem.attr.get("color").getAsInt());
		} else {
			TypedValue typedValue = new TypedValue();

			//Get the default card background color from the theme
			if (itemView.getContext().getTheme().resolveAttribute(com.google.android.material.R.attr.colorBackgroundFloating , typedValue, true)) {
				int defaultBackgroundColor = typedValue.data;
				//color.setBackgroundColor(defaultBackgroundColor);
			} else {
				//color.setBackgroundColor(Color.GRAY);
			}
		}
	}
}
