package aaa.sgordon.galleryfinal.gallery.viewholders;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.divider.MaterialDivider;

import org.apache.commons.io.FilenameUtils;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.repository.gallery.ListItem;

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

		String fileName = FilenameUtils.removeExtension(listItem.getPrettyName());
		name.setText(fileName);


		setCollapsed(listItem.isCollapsed());
		collapse.setOnClickListener(v -> {
			listItem.setCollapsed(!listItem.isCollapsed());
		});


		ColorUtil.setDividerColor(null, color);
		ColorUtil.setDividerColorAsync(listItem.fileUID, color);

		ColorUtil.setBorderColor(null, child);
		if(parent != null) ColorUtil.setBorderColorAsync(parent.fileUID, child);
	}

	private void setCollapsed(boolean isCollapsed) {
		if(isCollapsed)
			collapse.setImageResource(R.drawable.ic_chevron_up);
		else
			collapse.setImageResource(R.drawable.ic_chevron_down);
	}
}
