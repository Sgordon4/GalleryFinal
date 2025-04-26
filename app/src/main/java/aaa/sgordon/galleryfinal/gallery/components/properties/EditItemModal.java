package aaa.sgordon.galleryfinal.gallery.components.properties;

import android.app.Dialog;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.util.Pair;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.gson.JsonObject;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ConnectException;
import java.util.UUID;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.repository.gallery.ListItem;
import aaa.sgordon.galleryfinal.repository.gallery.caches.AttrCache;
import aaa.sgordon.galleryfinal.repository.gallery.caches.LinkCache;
import aaa.sgordon.galleryfinal.repository.gallery.link.ExternalTarget;
import aaa.sgordon.galleryfinal.repository.gallery.link.InternalTarget;
import aaa.sgordon.galleryfinal.repository.gallery.link.LinkTarget;
import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridAPI;
import aaa.sgordon.galleryfinal.repository.hybrid.types.HFile;
import aaa.sgordon.galleryfinal.utilities.DirUtilities;

public class EditItemModal extends NewItemModal {

	public static void launch(@NonNull Fragment parentFragment, @NonNull ListItem startItem, @NonNull ListItem startDir) {
		AttrCache.getInstance().getAttrAsync(startItem.fileUID, new AttrCache.AttrCallback() {
			@Override
			public void onAttrReady(@NonNull JsonObject attr) {
				EditItemModal dialog = new EditItemModal();
				dialog.tempStartDirDoNotUse = startDir;
				dialog.tempStartItemDoNotUse = startItem;
				dialog.tempStartAttrDoNotUse = attr;
				dialog.show(parentFragment.getChildFragmentManager(), "edit_item");
			}
			@Override
			public void onConnectException() {
				Toast.makeText(parentFragment.requireContext(), "Could not connect to server!", Toast.LENGTH_SHORT).show();
			}
			@Override
			public void onFileNotFoundException() {
				Toast.makeText(parentFragment.requireContext(), "File not found!", Toast.LENGTH_SHORT).show();
			}
		});
	}

	@NonNull
	@Override
	public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
		//Get the dialog from NewItemModal
		Dialog dialog = super.onCreateDialog(savedInstanceState);

		//Change the title
		dialog.setTitle("Edit Item");

		return dialog;
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		if(savedInstanceState == null) {
			//---------------------------------------------------------
			// Name

			viewModel.itemName = viewModel.startItem.getPrettyName();

			//---------------------------------------------------------
			// Color

			if(viewModel.startAttr.has("color"))
				viewModel.color = viewModel.startAttr.get("color").getAsInt();

			if(viewModel.startItem.isMedia())
				binding.colorSection.setVisibility(View.GONE);

			//---------------------------------------------------------
			// Dropdown

			if(viewModel.startItem.isDir) {
				viewModel.selectedDropdownItem = "Directory";
			}
			else if(viewModel.startItem.isLink) {
				viewModel.selectedDropdownItem = "Link";
				viewModel.isInternalLinkSelected = !viewModel.startItem.type.equals(ListItem.Type.LINKEXTERNAL);
			}
			else if(viewModel.startItem.type.equals(ListItem.Type.DIVIDER)) {
				viewModel.selectedDropdownItem = "Divider";
			}
			else {
				viewModel.selectedDropdownItem = "Notes";
			}

			//---------------------------------------------------------
			// Link Selection

			if(viewModel.startItem.isLink) {
				Thread fetchLinkInfo = new Thread(() -> {
					try {
						LinkCache linkCache = LinkCache.getInstance();
						LinkTarget target = linkCache.getLinkTarget(viewModel.startItem.fileUID);

						if(target instanceof InternalTarget) {
							InternalTarget inTarget = (InternalTarget) target;
							viewModel.internalTarget = inTarget;

							Pair<UUID, UUID> trueDirAndParent = linkCache.getTrueDirAndParent(inTarget.fileUID, inTarget.parentUID);
							viewModel.internalTargetName = (trueDirAndParent != null) ?
									DirUtilities.getFileNameFromDir(trueDirAndParent.first, trueDirAndParent.second) :
									"Unknown Item";

							binding.targetInternal.post(() -> binding.targetInternal.setText(viewModel.internalTargetName));
						}
						else if(target instanceof ExternalTarget) {
							viewModel.externalTarget = target.toUri().toString();
							binding.targetExternal.post(() -> binding.targetExternal.setText(viewModel.externalTarget));
						}
					}
					catch (ContentsNotFoundException | FileNotFoundException | ConnectException ignored) {}
				});
				fetchLinkInfo.start();
			}
		}


		//Run NewItemModal's setup code
		super.onViewCreated(view, savedInstanceState);


		//Don't allow type change for an existing item
		binding.dropdown.setVisibility(View.GONE);
	}

	@Override
	protected void onConfirm() {
		String newName = binding.name.getText().toString();
		String oldName = viewModel.startItem.getPrettyName();
		if(!newName.equals(oldName))
			viewModel.startItem.rename(newName);


		Thread writeChanges = new Thread(() -> {
			HybridAPI hAPI = HybridAPI.getInstance();
			try {
				hAPI.lockLocal(viewModel.startItem.fileUID);
				HFile fileProps = hAPI.getFileProps(viewModel.startItem.fileUID);

				if(viewModel.color != null)
					fileProps.userattr.addProperty("color", viewModel.color);
				else
					fileProps.userattr.remove("color");
				hAPI.setAttributes(viewModel.startItem.fileUID, fileProps.userattr, fileProps.attrhash);


				//Write the link target to the new file
				if(viewModel.startItem.isLink) {
					LinkTarget linkTarget;
					if(binding.linkType.getCheckedRadioButtonId() == R.id.internal_link)
						linkTarget = viewModel.internalTarget;
					else
						linkTarget = new ExternalTarget(Uri.parse(viewModel.externalTarget));

					hAPI.writeFile(viewModel.startItem.fileUID, linkTarget.toString().getBytes(), fileProps.checksum);
				}
			}
			catch (FileNotFoundException e) {
				Looper.prepare();
				Toast.makeText(requireContext(), "File not found!", Toast.LENGTH_SHORT).show();
				Looper.loop();
			}
			catch (ConnectException e) {
				Looper.prepare();
				Toast.makeText(requireContext(), "Unable to connect to server!", Toast.LENGTH_SHORT).show();
				Looper.loop();
			} catch (IOException e) {
				//Ignore idgaf
			} finally {
				hAPI.unlockLocal(viewModel.startItem.fileUID);
			}
		});
		writeChanges.start();
	}
}
