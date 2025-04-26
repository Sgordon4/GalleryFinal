package aaa.sgordon.galleryfinal.gallery.components.movecopy;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import aaa.sgordon.galleryfinal.repository.gallery.ListItem;
import aaa.sgordon.galleryfinal.gallery.viewholders.BaseViewHolder;

public class NothingViewHolder extends BaseViewHolder {

	public NothingViewHolder(@NonNull View itemView) {
		super(itemView);
	}

	@Override
	public void bind(@NonNull ListItem listItem, @Nullable ListItem parent) {
		super.bind(listItem, parent);

		itemView.setOnClickListener(v -> {
			//Do nothing
		});
	}
}
