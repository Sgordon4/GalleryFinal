package aaa.sgordon.galleryfinal.gallery.components.properties;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

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
import aaa.sgordon.galleryfinal.gallery.ListItem;
import aaa.sgordon.galleryfinal.gallery.components.modals.LinkTargetModal;
import aaa.sgordon.galleryfinal.repository.gallery.caches.LinkCache;
import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridAPI;
import aaa.sgordon.galleryfinal.repository.hybrid.types.HFile;
import aaa.sgordon.galleryfinal.utilities.DirUtilities;

public class NewItemModal extends DialogFragment {
	private final DirFragment dirFragment;

	private static final Integer defaultColor = Color.GRAY;
	private Integer color;
	private ListItem internalTarget;

	private EditText name;
	private Spinner dropdown;
	private ViewGroup linkInfo;
	private RadioGroup linkType;
	private ViewGroup linkInternal;
	private TextView targetInternal;
	private ViewGroup linkExternal;
	private EditText targetExternal;
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
		View view = inflater.inflate(R.layout.frag_dir_newitem, null);
		builder.setView(view);


		builder.setTitle("New Item");


		name = view.findViewById(R.id.name);
		dropdown = view.findViewById(R.id.dropdown);
		linkInfo = view.findViewById(R.id.link_info);
		linkType = view.findViewById(R.id.link_type);
		linkInternal = view.findViewById(R.id.link_internal);
		targetInternal = view.findViewById(R.id.target_internal);
		linkExternal = view.findViewById(R.id.link_external);
		targetExternal = view.findViewById(R.id.target_external);
		colorSlider = view.findViewById(R.id.color_slider);
		colorPickerButton = view.findViewById(R.id.color_picker_button);



		//---------------------------------------------------------


		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(requireContext(),
				R.array.new_items_array, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(androidx.appcompat.R.layout.support_simple_spinner_dropdown_item);
		dropdown.setAdapter(adapter);


		dropdown.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				String dropdownItem = parent.getItemAtPosition(position).toString();
				if(dropdownItem.equals("Link"))
					linkInfo.setVisibility(View.VISIBLE);
				else
					linkInfo.setVisibility(View.GONE);
			}
			@Override
			public void onNothingSelected(AdapterView<?> parent) {
			}
		});
		if(dropdown.getSelectedItem() != null && dropdown.getSelectedItem().toString().equals("Link"))
			linkInfo.setVisibility(View.VISIBLE);
		else
			linkInfo.setVisibility(View.GONE);


		//---------------------------------------------------------


		linkType.setOnCheckedChangeListener((group, checkedId) -> {
			if(checkedId == R.id.internal_link) {
				linkInternal.setVisibility(View.VISIBLE);
				linkExternal.setVisibility(View.GONE);
			}
			else {
				linkInternal.setVisibility(View.GONE);
				linkExternal.setVisibility(View.VISIBLE);
			}
		});
		linkType.check(R.id.internal_link);


		//---------------------------------------------------------

		ImageButton browse = view.findViewById(R.id.browse);
		browse.setOnClickListener(view1 ->
			LinkTargetModal.launch(dirFragment, dirFragment.dirViewModel.listItem.fileUID, target -> {
				internalTarget = target;
				targetInternal.setText(target.name);
			})
		);




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

				String selectedType = dropdown.getSelectedItem().toString();

				if(selectedType.equals("Link") && linkType.getCheckedRadioButtonId() == R.id.internal_link && internalTarget == null) {
					Toast.makeText(requireContext(), "Please select a link target!", Toast.LENGTH_SHORT).show();
					return;
				}

				if(selectedType.equals("Link") && linkType.getCheckedRadioButtonId() == R.id.external_link) {
					if(targetExternal.getText().toString().isEmpty()) {
						Toast.makeText(requireContext(), "Please enter a target URL!", Toast.LENGTH_SHORT).show();
						return;
					}

					//Make sure the Url is valid
					try {
						Uri.parse(targetExternal.getText().toString());
					}
					catch (IllegalArgumentException e) {
						Toast.makeText(requireContext(), "Please enter a valid URL!", Toast.LENGTH_SHORT).show();
						return;
					}
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

				//Write the link target to the new file
				if(isLink) {
					LinkCache.LinkTarget linkTarget;
					if(linkType.getCheckedRadioButtonId() == R.id.internal_link)
						linkTarget = new LinkCache.InternalTarget(internalTarget.parentUID, internalTarget.fileUID);
					else
						linkTarget = new LinkCache.ExternalTarget(Uri.parse(targetExternal.getText().toString()));

					hAPI.writeFile(newFileUID, linkTarget.toString().getBytes(), HFile.defaultChecksum);
				}
			}
			catch (FileNotFoundException | ConnectException e) {
				throw new RuntimeException(e);
			} catch (IOException e) {
				throw new RuntimeException(e);
			} finally {
				hAPI.unlockLocal(newFileUID);
			}


			//Add the new file to the top of the current directory
			UUID dirUID = dirFragment.dirViewModel.listItem.fileUID;
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
