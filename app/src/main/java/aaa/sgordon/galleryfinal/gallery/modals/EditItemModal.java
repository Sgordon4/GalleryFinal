package aaa.sgordon.galleryfinal.gallery.modals;

import androidx.fragment.app.DialogFragment;

import aaa.sgordon.galleryfinal.gallery.DirFragment;
import aaa.sgordon.galleryfinal.gallery.DirectoryViewModel;

public class EditItemModal extends DialogFragment {
	private final DirFragment dirFragment;
	private final DirectoryViewModel dirViewModel;

	public static void launch(DirFragment fragment) {
		EditItemModal dialog = new EditItemModal(fragment);
		dialog.show(fragment.getChildFragmentManager(), "edit_item");
	}
	private EditItemModal(DirFragment dirFragment) {
		this.dirFragment = dirFragment;
		this.dirViewModel = dirFragment.dirViewModel;
	}



}
