package aaa.sgordon.galleryfinal.gallery.components.linkselect;

import androidx.annotation.Nullable;

import aaa.sgordon.galleryfinal.gallery.ListItem;
import aaa.sgordon.galleryfinal.gallery.components.movecopy.MoveCopyFragment;

public class LinkSelectFragment extends MoveCopyFragment {
	@Override
	protected String getConfirmText(@Nullable ListItem selectedItem) {
		if(selectedItem == null)
			return "Link to This Directory";
		else
			return "Link to "+selectedItem.getPrettyName();
	}
}
