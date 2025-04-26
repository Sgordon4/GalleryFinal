package aaa.sgordon.galleryfinal.gallery.components.properties;

import android.app.Dialog;
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
	protected final LinkTarget linkTarget;
	protected final String targetName;

	public static void launch(@NonNull Fragment parentFragment, @NonNull ListItem startItem, @NonNull ListItem startDir) {
		AttrCache.getInstance().getAttrAsync(startItem.fileUID, new AttrCache.AttrCallback() {
			@Override
			public void onAttrReady(@NonNull JsonObject attr) {
				LinkTarget target = null;
				String targetName = null;
				if(startItem.isLink) {
					try {
						LinkCache linkCache = LinkCache.getInstance();
						target = linkCache.getLinkTarget(startItem.fileUID);

						//If we have an internal target, get the target's name
						if(target instanceof InternalTarget) {
							InternalTarget inTarget = (InternalTarget) target;
							Pair<UUID, UUID> trueDirAndParent = linkCache.getTrueDirAndParent(inTarget.fileUID, inTarget.parentUID);
							if(trueDirAndParent != null)
								targetName = DirUtilities.getFileNameFromDir(trueDirAndParent.first, trueDirAndParent.second);
						}
					}
					catch (ContentsNotFoundException | FileNotFoundException | ConnectException ignored) {}
				}

				EditItemModal dialog = new EditItemModal(parentFragment, startItem, attr, target, targetName, startDir);
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
	protected EditItemModal(@NonNull Fragment parentFragment, @NonNull ListItem startItem, @NonNull JsonObject startAttr,
							@Nullable LinkTarget linkTarget, @Nullable String targetName, @NonNull ListItem startDir) {
		super(parentFragment, startDir);
		this.startItem = startItem;
		this.startAttr = startAttr;
		this.linkTarget = linkTarget;
		this.targetName = targetName;

		if(linkTarget instanceof ExternalTarget)
	}

	@NonNull
	@Override
	public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
		Dialog dialog = super.onCreateDialog(savedInstanceState);

		dialog.setTitle("Edit Item");
		dropdown.setVisibility(View.GONE);

		//On first run
		if(savedInstanceState == null) {
			name.setText(startItem.getPrettyName());

			if(startItem.isMedia())
				colorSection.setVisibility(View.GONE);

			if(startAttr.has("color"))
				color = startAttr.get("color").getAsInt();
			else
				color = defaultColor;

			if(startItem.isDir) {
				dropdown.setSelection(dropdownAdapter.getPosition("Directory"));
			}
			else if(startItem.isLink) {
				dropdown.setSelection(dropdownAdapter.getPosition("Link"));
				linkInfo.setVisibility(View.VISIBLE);

				if(startItem.type.equals(ListItem.Type.LINKEXTERNAL)) {
					linkType.check(R.id.external_link);
					new Thread(() -> {
						try {
							LinkTarget target = LinkCache.getInstance().getLinkTarget(startItem.fileUID);
							targetExternal.setText(target.toString());
						}
						catch (ContentsNotFoundException | FileNotFoundException | ConnectException ignored) {}
					});
				}
			}
			else if(startItem.type.equals(ListItem.Type.DIVIDER)) {
				dropdown.setSelection(dropdownAdapter.getPosition("Divider"));
			}
			else {
				dropdown.setSelection(dropdownAdapter.getPosition("Notes"));
			}
		}

		return dialog;
	}


	@Override
	protected void onConfirm() {
		String newName = name.getText().toString();
		String oldName = startItem.getPrettyName();
		if(!newName.equals(oldName))
			startItem.rename(newName);


		Thread writeAttr = new Thread(() -> {
			HybridAPI hAPI = HybridAPI.getInstance();
			try {
				hAPI.lockLocal(startItem.fileUID);
				HFile fileProps = hAPI.getFileProps(startItem.fileUID);

				if(color != null)
					fileProps.userattr.addProperty("color", color);
				else
					fileProps.userattr.remove("color");

				hAPI.setAttributes(startItem.fileUID, fileProps.userattr, fileProps.attrhash);
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
			}
			finally {
				hAPI.unlockLocal(startItem.fileUID);
			}
		});
		writeAttr.start();
	}
}
