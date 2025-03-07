package aaa.sgordon.galleryfinal.gallery.modals;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import java.util.UUID;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.gallery.DirFragment;
import aaa.sgordon.galleryfinal.gallery.DirectoryViewModel;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridAPI;

public class EditItemModal extends DialogFragment {
	private final DirFragment dirFragment;
	private final DirectoryViewModel dirViewModel;

	private EditText name;
	private UUID fileUID;

	public static void launch(DirFragment fragment, UUID fileUID) {
		EditItemModal dialog = new EditItemModal(fragment, fileUID);
		dialog.show(fragment.getChildFragmentManager(), "edit_item");
	}
	private EditItemModal(DirFragment dirFragment, UUID fileUID) {
		this.dirFragment = dirFragment;
		this.dirViewModel = dirFragment.dirViewModel;
		this.fileUID = fileUID;
	}


	@NonNull
	@Override
	public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
		AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
		builder.setView(R.layout.fragment_directory_edit);
		builder.setTitle("Edit Item");

		LayoutInflater inflater = LayoutInflater.from(getContext());
		View view = inflater.inflate(R.layout.fragment_directory_edit, null);
		builder.setView(view);


		Thread thread = new Thread(() -> {
			HybridAPI hAPI = HybridAPI.getInstance();
			try {

				Handler mainHandler = new Handler(getContext().getMainLooper());
				mainHandler.post(() -> {});

			} catch (Exception e) {

			}
		});
		thread.start();

		name = view.findViewById(R.id.name);
		name.setText(fileName);


		builder.setPositiveButton("OK", (dialog, which) -> {
			System.out.println("OK Clicked");
		});
		builder.setNegativeButton("Cancel", (dialog, which) -> {
			System.out.println("Cancel clicked");
		});


		return builder.create();
	}
}
