package aaa.sgordon.galleryfinal.gallery.viewholders;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.apache.commons.io.FilenameUtils;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.repository.gallery.ListItem;

public class DividerViewHolder extends BaseViewHolder {
	public View child;
	public ImageView color;
	public TextView name;
	public ImageView collapse;

	public DividerViewHolder(@NonNull View itemView) {
		super(itemView);

		child = itemView.findViewById(R.id.child);
		color = itemView.findViewById(R.id.color);
		name = itemView.findViewById(R.id.name);
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


		ColorUtil.setIconColor(null, color);
		ColorUtil.setIconColorAsync(listItem.fileUID, color);

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
