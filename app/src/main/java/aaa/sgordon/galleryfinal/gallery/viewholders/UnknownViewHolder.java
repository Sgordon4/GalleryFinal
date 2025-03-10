package aaa.sgordon.galleryfinal.gallery.viewholders;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.UUID;

import aaa.sgordon.galleryfinal.R;

public class UnknownViewHolder extends BaseViewHolder {
	public View color;
	public ImageView image;
	public TextView name;

	public UnknownViewHolder(View itemView) {
		super(itemView);

		color = itemView.findViewById(R.id.color);
		image = itemView.findViewById(R.id.media);
		name = itemView.findViewById(R.id.name);
	}

	@Override
	public void bind(UUID fileUID, String fileName) {
		super.bind(fileUID, fileName);

		name.setText(this.fileName);
	}
}
