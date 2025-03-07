package aaa.sgordon.galleryfinal.gallery.modals;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.google.gson.JsonObject;

import java.io.FileNotFoundException;
import java.util.Objects;
import java.util.UUID;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.gallery.DirFragment;
import aaa.sgordon.galleryfinal.gallery.DirectoryViewModel;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridAPI;
import aaa.sgordon.galleryfinal.repository.hybrid.types.HFile;
import aaa.sgordon.galleryfinal.utilities.Utilities;

public class EditItemModal extends DialogFragment {
	private final DirFragment dirFragment;

	private EditProps props;
	private EditProps originalProps;

	private EditText name;
	private EditText color;
	private EditText description;


	public static class EditProps {
		@NonNull
		public UUID fileUID;
		@NonNull
		public String fileName;
		@Nullable
		public Integer color;
		@Nullable
		public String description;

		public EditProps(@NonNull UUID fileUID, @NonNull String fileName, @Nullable Integer color, @Nullable String description) {
			this.fileUID = fileUID;
			this.fileName = fileName;
			this.color = color;
			this.description = description;
		}
	}

	public static void launch(@NonNull DirFragment fragment, @NonNull EditProps props) {
		EditItemModal dialog = new EditItemModal(fragment, props);
		dialog.show(fragment.getChildFragmentManager(), "edit_item");
	}
	private EditItemModal(@NonNull DirFragment fragment, EditProps props) {
		this.dirFragment = fragment;
		this.props = props;
		this.originalProps = new EditProps(props.fileUID, props.fileName, props.color, props.description);
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

		name = view.findViewById(R.id.name);
		color = view.findViewById(R.id.color);
		description = view.findViewById(R.id.description);

		boolean isMedia = Utilities.isFileMedia(props.fileName);


		name.setText(props.fileName);

		if(isMedia) {
			color.setVisibility(View.GONE);
			description.setVisibility(View.VISIBLE);
		} else {
			color.setVisibility(View.VISIBLE);
			description.setVisibility(View.GONE);
		}


		if(props.color != null)
			color.setText(String.valueOf(props.color));
		if(props.description != null)
			description.setText(props.description);


		builder.setPositiveButton("OK", (dialog, which) -> {
			System.out.println("OK Clicked");
		});
		builder.setNegativeButton("Cancel", (dialog, which) -> {
			System.out.println("Cancel clicked");
		});


		return builder.create();
	}


	private void commitProps() {
		Thread thread = new Thread(() -> {
			HybridAPI hAPI = HybridAPI.getInstance();

			//Update the file attributes
			try {
				hAPI.lockLocal(props.fileUID);

				HFile fileProps = hAPI.getFileProps(props.fileUID);

				JsonObject attributes = fileProps.userattr;
				if(!Objects.equals(originalProps.color, props.color))
					attributes.addProperty("color", Integer.parseInt(color.getText().toString()));
				if(!Objects.equals(originalProps.description, props.description))
					attributes.addProperty("description", description.getText().toString());

				hAPI.setAttributes(props.fileUID, attributes, fileProps.attrhash);
			}
			catch (FileNotFoundException e) {
				Toast.makeText(getContext(), "Cannot save, file not found!", Toast.LENGTH_SHORT).show();
				dismiss();
			}
			finally {
				hAPI.unlockLocal(props.fileUID);
			}


			//Update the file name
			try {
				UUID dirUID = dirFragment.dirViewModel.getDirUID();
				hAPI.lockLocal(dirUID);


				dirFragment.dirViewModel.getDirCache().
			}

		});
		thread.start();
	}
}
