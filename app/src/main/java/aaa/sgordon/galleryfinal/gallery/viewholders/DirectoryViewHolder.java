package aaa.sgordon.galleryfinal.gallery.viewholders;

import android.content.res.TypedArray;
import android.graphics.Color;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import org.apache.commons.io.FilenameUtils;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.gallery.ListItem;

public class DirectoryViewHolder extends BaseViewHolder{
	public View color;
	public ImageView image;
	public TextView name;

	public DirectoryViewHolder(View itemView) {
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

		//Get the default background color from the theme
		try (TypedArray typedArray = itemView.getContext().getTheme()
				.obtainStyledAttributes(new int[]{android.R.attr.windowBackground})) {
			int defaultBackgroundColor = typedArray.getColor(0, Color.GRAY); //Default to Gray

			if(listItem.attr.has("color"))
				color.setBackgroundColor(listItem.attr.get("color").getAsInt());
			//else
				//color.setBackgroundColor(defaultBackgroundColor);
		}
	}
}
