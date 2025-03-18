package aaa.sgordon.galleryfinal.gallery.modals;

import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.util.Pair;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
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
import java.util.stream.Collectors;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.gallery.DirFragment;
import aaa.sgordon.galleryfinal.gallery.ListItem;
import aaa.sgordon.galleryfinal.gallery.touch.SelectionController;
import aaa.sgordon.galleryfinal.repository.caches.LinkCache;
import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridAPI;
import aaa.sgordon.galleryfinal.repository.hybrid.types.HFile;
import aaa.sgordon.galleryfinal.utilities.DirUtilities;

public class NewItemModal extends DialogFragment {
	private final DirFragment dirFragment;

	private static final Integer defaultColor = Color.GRAY;
	private Integer color;
	private LinkCache.LinkTarget linkTarget;

	private EditText name;
	private Spinner dropdown;
	private ColorSlider colorSlider;
	private View colorPickerButton;



	public static void launch(@NonNull DirFragment fragment) {
		NewItemModal dialog = new NewItemModal(fragment);
		dialog.show(fragment.getChildFragmentManager(), "edit_item");
	}
	private NewItemModal(@NonNull DirFragment fragment) {
		this.dirFragment = fragment;
		color = defaultColor;
	}


	@NonNull
	@Override
	public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
		AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
		LayoutInflater inflater = getLayoutInflater();
		View view = inflater.inflate(R.layout.fragment_directory_new, null);
		builder.setView(view);


		builder.setTitle("New Item");


		name = view.findViewById(R.id.name);
		dropdown = view.findViewById(R.id.dropdown);
		colorSlider = view.findViewById(R.id.color_slider);
		colorPickerButton = view.findViewById(R.id.color_picker_button);


		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(requireContext(),
				R.array.new_items_array, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(androidx.appcompat.R.layout.support_simple_spinner_dropdown_item);
		dropdown.setAdapter(adapter);


		dropdown.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				System.out.println("Position "+position+" clicked! ID:"+id);

			}
			@Override
			public void onNothingSelected(AdapterView<?> parent) {
			}
		});




		//Get the default card background color from the theme
		int defaultBorder = Color.GRAY;
		TypedValue typedValue = new TypedValue();
		if (requireContext().getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurface , typedValue, true))
			defaultBorder = typedValue.data;


		//Set the background color of the button to the current color
		GradientDrawable drawable = new GradientDrawable();
		drawable.setShape(GradientDrawable.RECTANGLE);
		drawable.setColor(color);
		drawable.setStroke(4, defaultBorder);
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
		String dropdownItem = dropdown.getSelectedItem().toString();

		boolean isDir = dropdownItem.equals("Directory");
		boolean isLink = dropdownItem.equals("Link");

		//Add a file extension based on the dropdown choice
		String fileName = name.getText().toString();
		if(dropdownItem.equals("Text"))
			fileName += ".txt";
		else if(dropdownItem.equals("Divider"))
			fileName += ".div";


		final String fFileName = fileName;
		Thread thread = new Thread(() -> {
			HybridAPI hAPI = HybridAPI.getInstance();

			//Create a new file
			UUID newFileUID = hAPI.createFile(hAPI.getCurrentAccount(), isDir, isLink);

			try {
				hAPI.lockLocal(newFileUID);

				//Add color to the new file's properties
				if(color != null) {
					JsonObject attributes = new JsonObject();
					attributes.addProperty("color", color);
					hAPI.setAttributes(newFileUID, attributes, HFile.defaultAttrHash);
				}

				//Write the link target to the new file
				if(isLink && linkTarget != null) {
					hAPI.writeFile(newFileUID, linkTarget.toString().getBytes(), HFile.defaultChecksum);
				}
			}
			catch (FileNotFoundException e) {
				throw new RuntimeException(e);
			} finally {
				hAPI.unlockLocal(newFileUID);
			}


			//Add the new file to the top of the current directory
			UUID dirUID = dirFragment.dirViewModel.getDirUID();
			try {
				hAPI.lockLocal(dirUID);
				HFile dirProps = hAPI.getFileProps(dirUID);

				//Get the current directory contents in a list, and add our new file to the top
				List<Pair<UUID, String>> dirList = DirUtilities.readDir(dirUID);
				dirList.add(0, new Pair<>(newFileUID, fFileName));

				//Write the updated contents back to the directory
				List<String> dirLines = dirList.stream().map(pair -> pair.first+" "+pair.second)
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
			} finally {
				hAPI.unlockLocal(dirUID);
			}
		});
		thread.start();
	}
}
