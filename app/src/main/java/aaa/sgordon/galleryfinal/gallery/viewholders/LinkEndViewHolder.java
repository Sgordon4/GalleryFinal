package aaa.sgordon.galleryfinal.gallery.viewholders;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.divider.MaterialDivider;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.repository.gallery.ListItem;

public class LinkEndViewHolder extends BaseViewHolder {
	public View child;
	public MaterialDivider color;

	public LinkEndViewHolder(@NonNull View itemView) {
		super(itemView);

		child = itemView.findViewById(R.id.child);
		color = itemView.findViewById(R.id.color);
	}

	@Override
	public void bind(@NonNull ListItem listItem, @Nullable ListItem parent) {
		super.bind(listItem, parent);

		ColorUtil.setDividerColor(null, color);
		ColorUtil.setDividerColorAsync(listItem.fileUID, color);

		ColorUtil.setBorderColor(null, child);
		if(parent != null) ColorUtil.setBorderColorAsync(parent.fileUID, child);
	}
}
