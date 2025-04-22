package aaa.sgordon.galleryfinal.gallery.viewholders;

import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.divider.MaterialDivider;

import org.apache.commons.io.FilenameUtils;

import java.io.FileNotFoundException;
import java.net.ConnectException;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.gallery.ListItem;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridAPI;
import aaa.sgordon.galleryfinal.repository.hybrid.types.HFile;

public class LinkViewHolder extends BaseViewHolder {
	public View child;
	public TextView name;
	public MaterialDivider color;
	public ImageView collapse;

	public LinkViewHolder(@NonNull View itemView) {
		super(itemView);

		child = itemView.findViewById(R.id.child);
		name = itemView.findViewById(R.id.name);
		color = itemView.findViewById(R.id.color);
		collapse = itemView.findViewById(R.id.collapse);
	}

	@Override
	public void bind(@NonNull ListItem listItem, @Nullable ListItem parent) {
		super.bind(listItem, parent);

		String fileName = FilenameUtils.removeExtension(listItem.name);
		name.setText(fileName);



		//Change the color of the item
		if(listItem.fileProps.userattr.has("color")) {
			color.setDividerColor(listItem.fileProps.userattr.get("color").getAsInt());

		} else {
			TypedValue typedValue = new TypedValue();

			//Get the default card background color from the theme
			//We need to apply this or the card will retain previous colors from other items as the RecyclerView recycles it
			if (itemView.getContext().getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnBackground, typedValue, true)) {
				int defaultBackgroundColor = typedValue.data;
				color.setDividerColor(defaultBackgroundColor);
			} else {
				color.setDividerColor(Color.GRAY);
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



		boolean isCollapsed = listItem.fileProps.userattr.has("collapsed") && listItem.fileProps.userattr.get("collapsed").getAsBoolean();
		if(isCollapsed)
			collapse.setImageResource(R.drawable.ic_chevron_up);
		else
			collapse.setImageResource(R.drawable.ic_chevron_down);

		collapse.setOnClickListener(v -> {
			Thread collapseThread = new Thread(() -> {
				System.out.println("Setting collapsed to "+!isCollapsed);
				HybridAPI hAPI = HybridAPI.getInstance();
				try {
					hAPI.lockLocal(listItem.fileUID);
					HFile fileProps = hAPI.getFileProps(listItem.fileUID);
					if(isCollapsed)
						fileProps.userattr.remove("collapsed");
					else
						fileProps.userattr.addProperty("collapsed", true);
					hAPI.setAttributes(listItem.fileUID, fileProps.userattr, fileProps.attrhash);
				} catch (FileNotFoundException | ConnectException e) {
					//Do nothing
				} finally {
					hAPI.unlockLocal(listItem.fileUID);
				}
			});
			collapseThread.start();
		});
	}
}
