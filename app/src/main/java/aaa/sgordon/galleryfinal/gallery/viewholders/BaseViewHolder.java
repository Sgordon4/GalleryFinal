package aaa.sgordon.galleryfinal.gallery.viewholders;

import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.divider.MaterialDivider;
import com.google.gson.JsonObject;

import java.util.UUID;

import aaa.sgordon.galleryfinal.repository.gallery.ListItem;
import aaa.sgordon.galleryfinal.repository.gallery.caches.AttrCache;

public class BaseViewHolder extends RecyclerView.ViewHolder {
	protected ListItem listItem;
	@Nullable
	protected ListItem parentItem;


	public BaseViewHolder(@NonNull View itemView) {
		super(itemView);
	}

	public ListItem getListItem() {
		return listItem;
	}
	@Nullable
	public ListItem getParentItem() {
		return parentItem;
	}

	public void bind(@NonNull ListItem listItem, @Nullable ListItem parent) {
		this.listItem = listItem;
		this.parentItem = parent;
	}


	//---------------------------------------------------------------------------------------------

	public static class ColorUtil {
		public static void setIconColor(@Nullable Integer color, @NonNull ImageView colorView) {
			//Change the color of the item
			if(color != null) {
				colorView.setColorFilter(color, PorterDuff.Mode.SRC_IN);
			} else {
				colorView.setColorFilter(Color.TRANSPARENT, PorterDuff.Mode.SRC_IN);
				/*
				TypedValue typedValue = new TypedValue();

				//Get the default icon color from the current theme
				//We need to apply this or the card will retain previous colors from other items as the RecyclerView recycles it
				if (colorView.getContext().getTheme().resolveAttribute(com.google.android.material.R.attr.colorControlNormal , typedValue, true)) {
					int defaultBackgroundColor = typedValue.data;
					colorView.setColorFilter(defaultBackgroundColor, PorterDuff.Mode.SRC_IN);
				} else {
					colorView.setColorFilter(Color.GRAY, PorterDuff.Mode.SRC_IN);
				}
				 */
			}
		}
		public static void setDividerColor(@Nullable Integer color, @NonNull MaterialDivider divider) {
			//Change the color of the item
			if(color != null) {
				divider.setDividerColor(color);
			} else {
				TypedValue typedValue = new TypedValue();

				//Get the default icon color from the current theme
				//We need to apply this or the card will retain previous colors from other items as the RecyclerView recycles it
				if (divider.getContext().getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnBackground , typedValue, true)) {
					int defaultBackgroundColor = typedValue.data;
					divider.setDividerColor(defaultBackgroundColor);
				} else {
					divider.setDividerColor(Color.GRAY);
				}
			}
		}
		public static void setBorderColor(@Nullable Integer color, @NonNull View borderView) {
			//Change the border color of the child
			Drawable background = borderView.getBackground();
			if (background instanceof GradientDrawable) {
				GradientDrawable shapeDrawable = (GradientDrawable) background;

				//Reset the default background color
				//We need to apply this or the card will retain previous colors from other items as the RecyclerView recycles it
				if(color == null)
					shapeDrawable.setStroke(4, Color.TRANSPARENT);
				else
					shapeDrawable.setStroke(4, color);
			}
		}


		public static void setIconColorAsync(@NonNull UUID fileUID, @NonNull ImageView colorView) {
			AttrCache.getInstance().getAttrAsync(fileUID, new AttrCache.AttrCallback() {
				@Override
				public void onAttrReady(@NonNull JsonObject attr) {
					if(attr.has("color"))
						colorView.post(() -> setIconColor(attr.get("color").getAsInt(), colorView));
					else
						colorView.post(() -> setIconColor(null, colorView));
				}
				@Override
				public void onConnectException() {}
				@Override
				public void onFileNotFoundException() {}
			});
		}
		public static void setDividerColorAsync(@NonNull UUID fileUID, @NonNull MaterialDivider dividerView) {
			AttrCache.getInstance().getAttrAsync(fileUID, new AttrCache.AttrCallback() {
				@Override
				public void onAttrReady(@NonNull JsonObject attr) {
					if(attr.has("color"))
						dividerView.post(() -> setDividerColor(attr.get("color").getAsInt(), dividerView));
					else
						dividerView.post(() -> setDividerColor(null, dividerView));
				}
				@Override
				public void onConnectException() {}
				@Override
				public void onFileNotFoundException() {}
			});
		}
		public static void setBorderColorAsync(@NonNull UUID fileUID, @NonNull View borderView) {
			AttrCache.getInstance().getAttrAsync(fileUID, new AttrCache.AttrCallback() {
				@Override
				public void onAttrReady(@NonNull JsonObject attr) {
					if(attr.has("color"))
						borderView.post(() -> setBorderColor(attr.get("color").getAsInt(), borderView));
					else
						borderView.post(() -> setBorderColor(null, borderView));
				}
				@Override
				public void onConnectException() {}
				@Override
				public void onFileNotFoundException() {}
			});
		}
	}
}
