package aaa.sgordon.galleryfinal.gallery.viewholders;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.apache.commons.io.FilenameUtils;

import java.util.UUID;

import aaa.sgordon.galleryfinal.R;

public class DividerViewHolder extends BaseViewHolder {
	public TextView name;

	public DividerViewHolder(@NonNull View itemView) {
		super(itemView);

		name = itemView.findViewById(R.id.name);
	}

	@Override
	public void bind(UUID fileUID, String fileName) {
		fileName = FilenameUtils.removeExtension(fileName);
		super.bind(fileUID, fileName);

		name.setText(fileName);
	}
}
