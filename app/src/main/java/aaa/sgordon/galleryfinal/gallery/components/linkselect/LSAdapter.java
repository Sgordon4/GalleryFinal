package aaa.sgordon.galleryfinal.gallery.components.linkselect;

import android.annotation.SuppressLint;

import androidx.annotation.NonNull;

import java.util.List;

import aaa.sgordon.galleryfinal.gallery.ListItem;
import aaa.sgordon.galleryfinal.gallery.components.movecopy.MCAdapter;
import aaa.sgordon.galleryfinal.gallery.components.movecopy.MoveCopyFragment;

public class LSAdapter extends MCAdapter {

	public LSAdapter(@NonNull MCAdapterCallbacks callbacks, @NonNull MoveCopyFragment fragment) {
		super(callbacks, fragment);
	}

	@SuppressLint("NotifyDataSetChanged")
	@Override
	public void setList(List<ListItem> newList) {
		list = newList;

		//When changing dirs, we want the full dataset to reset, even if there are common items
		//Dir content updates also change the list, but we should be displaying so few items that idc
		notifyDataSetChanged();
	}
}
