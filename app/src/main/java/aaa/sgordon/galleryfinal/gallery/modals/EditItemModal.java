package aaa.sgordon.galleryfinal.gallery.modals;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.github.naz013.colorslider.ColorSlider;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.FileNotFoundException;
import java.net.ConnectException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.gallery.DirFragment;
import aaa.sgordon.galleryfinal.gallery.ListItem;
import aaa.sgordon.galleryfinal.gallery.touch.SelectionController;
import aaa.sgordon.galleryfinal.repository.caches.LinkCache;
import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridAPI;
import aaa.sgordon.galleryfinal.repository.hybrid.types.HFile;
import aaa.sgordon.galleryfinal.utilities.DirUtilities;
import aaa.sgordon.galleryfinal.utilities.Utilities;

public class EditItemModal extends DialogFragment {
	private final DirFragment dirFragment;

	private final EditProps props;
	private int color;

	private EditText name;
	private EditText description;
	private ColorSlider colorSlider;
	private View colorPickerButton;

	private static final Integer defaultColor = Color.GRAY;
	private static final String defaultDescription = "";


	public static class EditProps {
		@NonNull
		public UUID fileUID;
		@NonNull
		public UUID dirUID;
		@NonNull
		public String fileName;
		@Nullable
		public Integer color;
		@Nullable
		public String description;

		public EditProps(@NonNull UUID fileUID, @NonNull UUID dirUID, @NonNull String fileName, @Nullable Integer color, @Nullable String description) {
			this.fileUID = fileUID;
			this.dirUID = dirUID;
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

		color = props.color != null ? props.color : defaultColor;
	}


	@NonNull
	@Override
	public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
		AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
		builder.setView(R.layout.fragment_directory_edit);
		builder.setTitle("Edit Item");

		LayoutInflater inflater = getLayoutInflater();
		View view = inflater.inflate(R.layout.fragment_directory_edit, null);
		builder.setView(view);



		name = view.findViewById(R.id.name);
		description = view.findViewById(R.id.description);
		colorSlider = view.findViewById(R.id.color_slider);
		colorPickerButton = view.findViewById(R.id.color_picker_button);


		boolean isMedia = Utilities.isFileMedia(props.fileName);
		if(isMedia) {
			colorSlider.setVisibility(View.GONE);
			colorPickerButton.setVisibility(View.GONE);
			description.setVisibility(View.VISIBLE);
		} else {
			colorSlider.setVisibility(View.VISIBLE);
			colorPickerButton.setVisibility(View.VISIBLE);
			description.setVisibility(View.GONE);
		}


		name.setText(props.fileName);

		if(props.description != null)
			description.setText(props.description);





		//Set the background color of the button to the current color
		GradientDrawable drawable = new GradientDrawable();
		drawable.setShape(GradientDrawable.RECTANGLE);
		drawable.setColor(color);
		drawable.setStroke(4, Color.DKGRAY);
		colorPickerButton.setBackground(drawable);


		colorSlider.setSelection(-1);
		colorSlider.setListener((position, newColor) -> {
			color =  newColor | 0xFF000000;		//Set alpha to 100%

			//Change the color picker button's background color
			GradientDrawable background = (GradientDrawable) colorPickerButton.getBackground();
			background.setColor(color);
			colorPickerButton.setBackground(background);
		});


		colorPickerButton.setOnClickListener(v -> {
			ColorPickerModal.launch(dirFragment, color, newColor -> {
				color =  newColor | 0xFF000000;		//Set alpha to 100%

				//Change the color picker button's background color
				GradientDrawable background = (GradientDrawable) colorPickerButton.getBackground();
				background.setColor(color);
				colorPickerButton.setBackground(background);

				//Unselect the slider's currently selected item
				colorSlider.setSelection(-1);
			});
		});










		builder.setPositiveButton("OK", (dialog, which) -> {
			System.out.println("OK Clicked");
			commitProps();
		});
		builder.setNegativeButton("Cancel", (dialog, which) -> {
			System.out.println("Cancel clicked");
		});


		AlertDialog dialog = builder.create();
		dialog.setCanceledOnTouchOutside(false);
		return dialog;
	}


	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
	}

	private void commitProps() {
		//Grab the current properties, setting them to null if they are default
		Integer newColor = color;
		if(newColor.equals(defaultColor))
			newColor = null;

		String newDescription = description.getText().toString();
		if(newDescription.equals(defaultDescription))
			newDescription = null;


		final Integer fNewColor = newColor;
		final String fNewDescription = newDescription;

		Thread thread = new Thread(() -> {
			HybridAPI hAPI = HybridAPI.getInstance();



			if(!Objects.equals(props.color, fNewColor) || !Objects.equals(props.description, fNewDescription)) {
				//Update the file attributes
				try {
					hAPI.lockLocal(props.fileUID);

					HFile fileProps = hAPI.getFileProps(props.fileUID);

					JsonObject attributes = fileProps.userattr;
					if(!Objects.equals(props.color, fNewColor)) {
						if(fNewColor == null)
							attributes.remove("color");
						else
							attributes.addProperty("color", fNewColor);
					}
					if(!Objects.equals(props.description, fNewDescription)) {
						if(fNewDescription == null)
							attributes.remove("description");
						else
							attributes.addProperty("description", fNewDescription);
					}

					hAPI.setAttributes(props.fileUID, attributes, fileProps.attrhash);
				}
				catch (FileNotFoundException e) {
					Toast.makeText(getContext(), "Cannot save, file not found!", Toast.LENGTH_SHORT).show();
					return;
				}
				finally {
					hAPI.unlockLocal(props.fileUID);
				}
			}



			//Make sure the new filename isn't an empty string
			String newFilename = name.getText().toString();
			if(newFilename.isEmpty())
				newFilename = props.fileName;

			//Rename the file if the filename changed
			if(!Objects.equals(props.fileName, newFilename)) {
				try {
					//DirUID could be a link to a directory, we need the directory itself
					UUID dirUID = LinkCache.getInstance().resolvePotentialLink(props.dirUID);

					DirUtilities.renameFile(props.fileUID, dirUID, newFilename);
				} catch (ContentsNotFoundException e) {
					throw new RuntimeException(e);
				} catch (FileNotFoundException e) {
					Toast.makeText(getContext(), "Cannot rename, file not found!", Toast.LENGTH_SHORT).show();
				} catch (ConnectException e) {
					Toast.makeText(getContext(), "Could not connect, rename failed!", Toast.LENGTH_SHORT).show();
				}
			}
		});
		thread.start();
	}



	//---------------------------------------------------------------------------------------------

	public static boolean launchHelper(@NonNull DirFragment dirFragment, SelectionController selectionController, List<ListItem> adapterList) {
		//Get the current selected item
		UUID fileUID = selectionController.getSelectedList().iterator().next();

		//Get the filename from the file list
		String fileName = null;
		UUID dirUID = null;
		for(ListItem item : adapterList) {
			UUID itemUID = item.fileUID;

			if(itemUID.equals(fileUID)) {
				fileName = item.name;
				dirUID = item.parentUID;
				break;
			}
		}
		if(fileName == null) {
			Toast.makeText(dirFragment.getContext(), "Selected file was removed, cannot edit!", Toast.LENGTH_SHORT).show();
			return false;
		}
		if(dirUID == null) {
			throw new RuntimeException("Somehow DirUID is null");
		}


		//TODO Get the dirUID from the path and update the name

		String finalFileName = fileName;
		UUID finalDirUID = dirUID;
		Thread thread = new Thread(() -> {
			HybridAPI hAPI = HybridAPI.getInstance();
			try {
				//Get the file attributes from the system
				JsonObject attributes = hAPI.getFileProps(fileUID).userattr;

				//Grab any items we can edit
				JsonElement colorElement = attributes.get("color");
				Integer color = colorElement == null ? null : colorElement.getAsInt();
				JsonElement descriptionElement = attributes.get("description");
				String description = descriptionElement == null ? null : descriptionElement.getAsString();

				//Compile them into a props object
				EditItemModal.EditProps props = new EditItemModal.EditProps(fileUID, finalDirUID, finalFileName, color, description);


				//Launch the edit modal
				Handler mainHandler = new Handler(dirFragment.requireContext().getMainLooper());
				mainHandler.post(() -> EditItemModal.launch(dirFragment, props));

			} catch (FileNotFoundException e) {
				Toast.makeText(dirFragment.getContext(), "Cannot edit, file not found!", Toast.LENGTH_SHORT).show();
			}
		});
		thread.start();

		return true;
	}
}
