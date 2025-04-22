package aaa.sgordon.galleryfinal.gallery.components.movecopy;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.UUID;

import aaa.sgordon.galleryfinal.gallery.ListItem;
import aaa.sgordon.galleryfinal.gallery.viewholders.BaseViewHolder;

public class NewDirViewHolder extends BaseViewHolder {
	private final Fragment fragment;
	private final UUID destinationUID;

	public NewDirViewHolder(@NonNull View itemView, @NonNull Fragment fragment, UUID destinationUID) {
		super(itemView);
		this.fragment = fragment;
		this.destinationUID = destinationUID;
	}

	@Override
	public void bind(@NonNull ListItem listItem, @Nullable ListItem parent) {
		super.bind(listItem, parent);

		itemView.setOnClickListener(v -> {
			MCNewItemModal.launch(fragment, destinationUID);
		});
	}
}
