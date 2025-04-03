package aaa.sgordon.galleryfinal.gallery.viewholders;

import android.graphics.Color;
import android.graphics.PorterDuff;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.divider.MaterialDivider;

import org.apache.commons.io.FilenameUtils;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.gallery.ListItem;

public class LinkViewHolder extends BaseViewHolder {
	public ImageView icon;
	public TextView name;
	public MaterialDivider divider;

	public LinkViewHolder(@NonNull View itemView) {
		super(itemView);

		icon = itemView.findViewById(R.id.icon);
		name = itemView.findViewById(R.id.name);
		divider = itemView.findViewById(R.id.divider);
	}

	@Override
	public void bind(@NonNull ListItem listItem, @Nullable ListItem parent) {
		super.bind(listItem, parent);

		String fileName = FilenameUtils.removeExtension(listItem.name);
		name.setText(fileName);



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
