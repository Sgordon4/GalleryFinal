package aaa.sgordon.galleryfinal.gallery.viewholders;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import org.apache.commons.io.FilenameUtils;

import java.util.UUID;

import aaa.sgordon.galleryfinal.R;

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
	public void bind(UUID fileUID, String fileName) {
		fileName = FilenameUtils.removeExtension(fileName);
		super.bind(fileUID, fileName);

		name.setText(fileName);
	}
}
