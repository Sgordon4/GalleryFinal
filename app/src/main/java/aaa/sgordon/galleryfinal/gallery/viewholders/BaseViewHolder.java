package aaa.sgordon.galleryfinal.gallery.viewholders;

import android.graphics.Color;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.apache.commons.io.FilenameUtils;

import java.util.UUID;

import aaa.sgordon.galleryfinal.gallery.ListItem;

public class BaseViewHolder extends RecyclerView.ViewHolder {
	protected ListItem listItem;

	public BaseViewHolder(@NonNull View itemView) {
		super(itemView);
	}

	public ListItem getListItem() {
		return listItem;
	}

	public void bind(ListItem listItem) {
		this.listItem = listItem;
	}
}
