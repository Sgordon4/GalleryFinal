package aaa.sgordon.galleryfinal.gallery.components.movecopy;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Pair;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import com.github.naz013.colorslider.ColorSlider;
import com.google.gson.JsonObject;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ConnectException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.gallery.DirFragment;
import aaa.sgordon.galleryfinal.gallery.DirItem;
import aaa.sgordon.galleryfinal.gallery.components.properties.ColorPickerModal;
import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridAPI;
import aaa.sgordon.galleryfinal.repository.hybrid.types.HFile;
import aaa.sgordon.galleryfinal.utilities.DirUtilities;

public class MCNewItemModal extends DialogFragment {
	private final Fragment fragment;
	private final UUID destinationUID;

	private static final Integer defaultColor = Color.GRAY;
	private Integer color;

	private EditText name;
	private ColorSlider colorSlider;
	private View colorPickerButton;



	public static void launch(@NonNull Fragment fragment, UUID destinationUID) {
		MCNewItemModal dialog = new MCNewItemModal(fragment, destinationUID);
		dialog.show(fragment.getChildFragmentManager(), "mc_new_item");
	}
	private MCNewItemModal(@NonNull Fragment fragment, UUID destinationUID) {
		this.fragment = fragment;
		this.destinationUID = destinationUID;
		color = defaultColor;
	}


	@NonNull
	@Override
	public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
		AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
		LayoutInflater inflater = getLayoutInflater();
		View view = inflater.inflate(R.layout.dir_mc_newitem, null);
		builder.setView(view);


		builder.setTitle("New Directory");


		name = view.findViewById(R.id.name);
		colorSlider = view.findViewById(R.id.color_slider);
		colorPickerButton = view.findViewById(R.id.color_picker_button);



		//Get the default card background color from the theme
		int defaultColor = Color.GRAY;
		TypedValue typedValue = new TypedValue();
		if (requireContext().getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurface , typedValue, true))
			defaultColor = typedValue.data;


		//Set the background color of the button to the current color
		GradientDrawable drawable = new GradientDrawable();
		drawable.setShape(GradientDrawable.RECTANGLE);
		drawable.setColor(color);
		drawable.setStroke(4, defaultColor);
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
			ColorPickerModal.launch(fragment, color, newColor -> {
				color =  newColor | 0xFF000000;		//Set alpha to 100%

				//Change the color picker button's background color
				GradientDrawable background = (GradientDrawable) colorPickerButton.getBackground();
				background.setColor(color);
				colorPickerButton.setBackground(background);

				//Unselect the slider's currently selected item
				colorSlider.setSelection(-1);
			});
		});



		builder.setPositiveButton(android.R.string.ok, null);
		builder.setNegativeButton("Cancel", (dialog2, which) -> {
			System.out.println("Cancel clicked");
		});

		AlertDialog dialog = builder.create();
		dialog.setCanceledOnTouchOutside(false);

		//Need to define the positive button in this way since we don't want the dialog to be auto-dismissed on OK
		dialog.setOnShowListener(dialogInterface -> {
			Button button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
			button.setOnClickListener(v -> {
				System.out.println("OK Clicked");
				if(name.getText().toString().isEmpty()) {
					Toast.makeText(requireContext(), "Name cannot be empty!", Toast.LENGTH_SHORT).show();
					return;
				}

				createFile();
				dismiss();
			});
		});


		return dialog;
	}



	private void createFile() {

		boolean isDir = true;
		boolean isLink = false;

		final String fFileName = name.getText().toString();
		Thread thread = new Thread(() -> {
			HybridAPI hAPI = HybridAPI.getInstance();

			//Create a new file
			UUID newFileUID;
			try {
				newFileUID = hAPI.createFile(hAPI.getCurrentAccount(), isDir, isLink);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

			try {
				hAPI.lockLocal(newFileUID);

				//Add color to the new file's properties
				if(color != null) {
					JsonObject attributes = new JsonObject();
					attributes.addProperty("color", color);
					hAPI.setAttributes(newFileUID, attributes, HFile.defaultAttrHash);
				}
			}
			catch (FileNotFoundException | ConnectException e) {
				throw new RuntimeException(e);
			} finally {
				hAPI.unlockLocal(newFileUID);
			}


			//Add the new file to the top of the current directory
			UUID dirUID = destinationUID;
			try {
				hAPI.lockLocal(dirUID);
				HFile dirProps = hAPI.getFileProps(dirUID);

				//Get the current directory contents in a list, and add our new file to the top
				List<DirItem> dirList = DirUtilities.readDir(dirUID);
				dirList.add(0, new DirItem(newFileUID, isDir, isLink, fFileName));

				//Write the updated contents back to the directory
				List<String> dirLines = dirList.stream().map(DirItem::toString)
						.collect(Collectors.toList());
				byte[] newContent = String.join("\n", dirLines).getBytes();
				hAPI.writeFile(dirUID, newContent, dirProps.checksum);
			}
			catch (FileNotFoundException | ConnectException | ContentsNotFoundException e) {
				Toast.makeText(requireContext(), "Could not create file, please try again.", Toast.LENGTH_SHORT).show();

				//If we can't find the directory for whatever reason, delete the file we just created
				try {
					hAPI.lockLocal(newFileUID);
					hAPI.deleteFile(newFileUID);
				} catch (FileNotFoundException ex) {
					//Job done
				} finally {
					hAPI.unlockLocal(newFileUID);
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			} finally {
				hAPI.unlockLocal(dirUID);
			}
		});
		thread.start();
	}
}
