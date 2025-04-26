package aaa.sgordon.galleryfinal.gallery.viewholders;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.apache.commons.io.FilenameUtils;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.repository.gallery.ListItem;

public class UnknownViewHolder extends BaseViewHolder {
	public View child;
	public ImageView color;
	public ImageView image;
	public TextView name;

	public UnknownViewHolder(View itemView) {
		super(itemView);

		child = itemView.findViewById(R.id.child);
		color = itemView.findViewById(R.id.color);
		image = itemView.findViewById(R.id.media);
		name = itemView.findViewById(R.id.name);
	}

	@Override
	public void bind(@NonNull ListItem listItem, @Nullable ListItem parent) {
		super.bind(listItem, parent);

		String fileName = FilenameUtils.removeExtension(listItem.getPrettyName());
		name.setText(fileName);


		ColorUtil.setIconColor(null, color);
		ColorUtil.setIconColorAsync(listItem.fileUID, color);

		ColorUtil.setBorderColor(null, child);
		if(parent != null) ColorUtil.setBorderColorAsync(parent.fileUID, child);
	}
}
