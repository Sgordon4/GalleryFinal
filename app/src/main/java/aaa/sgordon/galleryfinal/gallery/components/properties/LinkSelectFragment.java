package aaa.sgordon.galleryfinal.gallery.components.properties;

import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.apache.commons.io.FilenameUtils;

import java.nio.file.Path;
import java.util.UUID;

import aaa.sgordon.galleryfinal.repository.gallery.ListItem;
import aaa.sgordon.galleryfinal.gallery.components.movecopy.MoveCopyFragment;
import aaa.sgordon.galleryfinal.repository.gallery.caches.LinkCache;
import aaa.sgordon.galleryfinal.repository.gallery.link.InternalTarget;
import aaa.sgordon.galleryfinal.repository.gallery.link.LinkTarget;
import aaa.sgordon.galleryfinal.utilities.DirUtilities;

public class LinkSelectFragment extends MoveCopyFragment {

	public static LinkSelectFragment newInstance(ListItem startItem) {
		LinkSelectFragment fragment = new LinkSelectFragment();
		fragment.tempItemDoNotUse = startItem;

		Bundle args = new Bundle();
		args.putBoolean("isMove", false);
		fragment.setArguments(args);

		return fragment;
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		adapter.showCreateNewDir = false;
	}

	@Override
	protected void updateConfirmButton(@Nullable ListItem selectedItem) {
		boolean isRootSelected = selectedItem == null && viewModel.currPathFromRoot.getNameCount() == 1;
		binding.confirm.post(() -> binding.confirm.setEnabled(!isRootSelected));

		String text;
		if(selectedItem == null)
			text = "Link to This Directory";
		else
			text = "Link to "+ FilenameUtils.removeExtension(selectedItem.getPrettyName());

		binding.confirm.post(() -> binding.confirm.setText(text));
	}

	@Override
	protected void onDirectoryChanged(UUID dirUID, UUID parentUID, Path pathFromRoot) {
		super.onDirectoryChanged(dirUID, parentUID, pathFromRoot);

		boolean isRootSelected = selectionController.getNumSelected() == 0 && pathFromRoot.getNameCount() == 1;
		binding.confirm.post(() -> binding.confirm.setEnabled(!isRootSelected));
	}



	private LinkSelectCallback callback;
	public void setLinkSelectCallback(LinkSelectCallback callback) {
		this.callback = callback;
	}
	public interface LinkSelectCallback {
		void onConfirm(ListItem target);
	}

	@Override
	protected void onConfirm() {
		if(callback == null) return;

		//If the user selected an item, link to it
		if(selectionController.getNumSelected() > 0) {
			UUID selectedUID = selectionController.getSelectedList().iterator().next();
			ListItem selectedItem = adapter.list.stream()
					.filter(item -> item.fileUID.equals(selectedUID)).findFirst().orElse(null);
			if(selectedItem == null) return;

			//This item must be a link or a divider. If it's a link, get the target
			if(selectedItem.isLink) {
				LinkTarget linkTarget = LinkCache.getInstance().getFinalTarget(selectedUID);
				if(!(linkTarget instanceof InternalTarget)) return;

				InternalTarget target = (InternalTarget) linkTarget;
				String targetName = DirUtilities.getFileNameFromDir(target.fileUID, target.parentUID);
				if(targetName == null) return;

				selectedItem = new ListItem(target.fileUID, target.parentUID, false, false,
						targetName, viewModel.currPathFromRoot, ListItem.Type.DIRECTORY);
			}

			callback.onConfirm(selectedItem);
		}
		//If the user selected the directory, and we're not at root, link to it
		else if(viewModel.currPathFromRoot.getNameCount() > 1) {
			UUID dirUID = viewModel.currDirUID;
			UUID parentUID = viewModel.currParentUID;
			String fileName = DirUtilities.getFileNameFromDir(dirUID, parentUID);
			if(fileName == null) return;

			ListItem selectedItem = new ListItem(dirUID, parentUID, true, false,
					fileName, viewModel.currPathFromRoot, ListItem.Type.DIRECTORY);
			callback.onConfirm(selectedItem);
		}
		//If the user selected the directory, and we ARE at root, do nothing. Confirm should be disabled for this.
		else {
			Log.e(TAG, "Somehow tried to link to root directory!");
		}
	}
}
