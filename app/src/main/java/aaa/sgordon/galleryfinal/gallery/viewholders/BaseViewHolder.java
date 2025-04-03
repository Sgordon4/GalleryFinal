package aaa.sgordon.galleryfinal.gallery.viewholders;

import android.graphics.Color;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.apache.commons.io.FilenameUtils;

import java.util.UUID;

import aaa.sgordon.galleryfinal.gallery.ListItem;

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
}
