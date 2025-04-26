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
import aaa.sgordon.galleryfinal.gallery.ListItem;
import aaa.sgordon.galleryfinal.repository.gallery.caches.AttrCache;
import aaa.sgordon.galleryfinal.repository.gallery.caches.LinkCache;
import aaa.sgordon.galleryfinal.repository.gallery.components.link.ExternalTarget;
import aaa.sgordon.galleryfinal.repository.gallery.components.link.InternalTarget;
import aaa.sgordon.galleryfinal.repository.gallery.components.link.LinkTarget;
import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridAPI;
import aaa.sgordon.galleryfinal.repository.hybrid.types.HFile;
import aaa.sgordon.galleryfinal.utilities.DirUtilities;

public class EditItemModal extends NewItemModal {
	protected final ListItem startItem;
	protected final JsonObject startAttr;

	public static void launch(@NonNull Fragment parentFragment, @NonNull ListItem startItem, @NonNull ListItem startDir) {
		AttrCache.getInstance().getAttrAsync(startItem.fileUID, new AttrCache.AttrCallback() {
			@Override
			public void onAttrReady(@NonNull JsonObject attr) {
				EditItemModal dialog = new EditItemModal(parentFragment, startItem, attr, startDir);
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
	protected EditItemModal(@NonNull Fragment parentFragment, @NonNull ListItem startItem, @NonNull JsonObject startAttr, @NonNull ListItem startDir) {
		super(parentFragment, startDir);
		this.startItem = startItem;
		this.startAttr = startAttr;
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

			this.itemName = startItem.getPrettyName();

			//---------------------------------------------------------
			// Color

			if(startAttr.has("color"))
				color = startAttr.get("color").getAsInt();

			if(startItem.isMedia())
				binding.colorSection.setVisibility(View.GONE);

			//---------------------------------------------------------
			// Dropdown

			if(startItem.isDir) {
				this.selectedDropdownItem = "Directory";
			}
			else if(startItem.isLink) {
				this.selectedDropdownItem = "Link";
				this.isInternalLinkSelected = !startItem.type.equals(ListItem.Type.LINKEXTERNAL);
			}
			else if(startItem.type.equals(ListItem.Type.DIVIDER)) {
				this.selectedDropdownItem = "Divider";
			}
			else {
				this.selectedDropdownItem = "Notes";
			}

			//---------------------------------------------------------
			// Link Selection

			if(startItem.isLink) {
				Thread fetchLinkInfo = new Thread(() -> {
					try {
						LinkCache linkCache = LinkCache.getInstance();
						LinkTarget target = linkCache.getLinkTarget(startItem.fileUID);

						if(target instanceof InternalTarget) {
							InternalTarget inTarget = (InternalTarget) target;
							this.internalTarget = inTarget;

							Pair<UUID, UUID> trueDirAndParent = linkCache.getTrueDirAndParent(inTarget.fileUID, inTarget.parentUID);
							this.internalTargetName = (trueDirAndParent != null) ?
									DirUtilities.getFileNameFromDir(trueDirAndParent.first, trueDirAndParent.second) :
									"Unknown Item";

							binding.targetInternal.post(() -> binding.targetInternal.setText(this.internalTargetName));
						}
						else if(target instanceof ExternalTarget) {
							this.externalTarget = target.toUri().toString();
							binding.targetExternal.post(() -> binding.targetExternal.setText(this.externalTarget));
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
		String oldName = startItem.getPrettyName();
		if(!newName.equals(oldName))
			startItem.rename(newName);


		Thread writeChanges = new Thread(() -> {
			HybridAPI hAPI = HybridAPI.getInstance();
			try {
				hAPI.lockLocal(startItem.fileUID);
				HFile fileProps = hAPI.getFileProps(startItem.fileUID);

				if(color != null)
					fileProps.userattr.addProperty("color", color);
				else
					fileProps.userattr.remove("color");
				hAPI.setAttributes(startItem.fileUID, fileProps.userattr, fileProps.attrhash);


				//Write the link target to the new file
				if(startItem.isLink) {
					LinkTarget linkTarget;
					if(binding.linkType.getCheckedRadioButtonId() == R.id.internal_link)
						linkTarget = internalTarget;
					else
						linkTarget = new ExternalTarget(Uri.parse(externalTarget));

					hAPI.writeFile(startItem.fileUID, linkTarget.toString().getBytes(), fileProps.checksum);
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
				hAPI.unlockLocal(startItem.fileUID);
			}
		});
		writeChanges.start();
	}
}
