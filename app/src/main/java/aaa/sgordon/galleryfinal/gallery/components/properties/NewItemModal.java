package aaa.sgordon.galleryfinal.gallery.components.properties;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.google.gson.JsonObject;

import org.apache.commons.io.FilenameUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ConnectException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.databinding.FragDirNewitemBinding;
import aaa.sgordon.galleryfinal.repository.gallery.DirItem;
import aaa.sgordon.galleryfinal.repository.gallery.ListItem;
import aaa.sgordon.galleryfinal.repository.gallery.link.ExternalTarget;
import aaa.sgordon.galleryfinal.repository.gallery.link.InternalTarget;
import aaa.sgordon.galleryfinal.repository.gallery.link.LinkTarget;
import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridAPI;
import aaa.sgordon.galleryfinal.repository.hybrid.types.HFile;
import aaa.sgordon.galleryfinal.utilities.DirUtilities;

public class NewItemModal extends DialogFragment {
	protected FragDirNewitemBinding binding;
	protected NewItemViewModel viewModel;

	protected ArrayAdapter<CharSequence> dropdownAdapter;


	protected ListItem tempStartDirDoNotUse;
	protected ListItem tempStartItemDoNotUse;		//These two are used in EditItemModal
	protected JsonObject tempStartAttrDoNotUse;		//It's a bit hacky, but whatever
	public static void launch(@NonNull Fragment parentFragment, @NonNull ListItem startDir) {
		NewItemModal dialog = new NewItemModal();
		dialog.tempStartDirDoNotUse = startDir;
		dialog.show(parentFragment.getChildFragmentManager(), "new_item");
	}


	@NonNull
	@Override
	public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
		binding = FragDirNewitemBinding.inflate(getLayoutInflater());
		View view = binding.getRoot();

		viewModel = new ViewModelProvider(this,
				new NewItemViewModel.Factory(tempStartDirDoNotUse, tempStartItemDoNotUse, tempStartAttrDoNotUse))
				.get(NewItemViewModel.class);


		dropdownAdapter = ArrayAdapter.createFromResource(requireContext(),
				R.array.new_items_array, android.R.layout.simple_spinner_item);
		dropdownAdapter.setDropDownViewResource(androidx.appcompat.R.layout.support_simple_spinner_dropdown_item);
		binding.dropdown.setAdapter(dropdownAdapter);


		AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
		builder.setView(view);
		builder.setTitle("New Item");


		builder.setPositiveButton(android.R.string.ok, null);
		builder.setNegativeButton("Cancel", null);


		AlertDialog dialog = builder.create();
		dialog.setCanceledOnTouchOutside(false);

		//Need to define the positive button in this way since we don't want the dialog to be auto-dismissed on OK
		dialog.setOnShowListener(dialogInterface -> {
			Button button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
			button.setOnClickListener(v -> {
				if(binding.name.getText().toString().isEmpty()) {
					Toast.makeText(requireContext(), "Name cannot be empty!", Toast.LENGTH_SHORT).show();
					return;
				}
				if(binding.name.getText().toString().startsWith(".")) {
					Toast.makeText(requireContext(), "Name cannot start with \".\"!", Toast.LENGTH_SHORT).show();
					return;
				}

				String selectedType = binding.dropdown.getSelectedItem().toString();

				if(selectedType.equals("Link") && binding.linkType.getCheckedRadioButtonId() == R.id.internal_link && viewModel.internalTarget == null) {
					Toast.makeText(requireContext(), "Please select a link target!", Toast.LENGTH_SHORT).show();
					return;
				}

				if(selectedType.equals("Link") && binding.linkType.getCheckedRadioButtonId() == R.id.external_link) {
					if(binding.targetExternal.getText().toString().isEmpty()) {
						Toast.makeText(requireContext(), "Please enter a target URL!", Toast.LENGTH_SHORT).show();
						return;
					}

					//Make sure the Url is valid
					try {
						Uri.parse(binding.targetExternal.getText().toString());
					}
					catch (IllegalArgumentException e) {
						Toast.makeText(requireContext(), "Please enter a valid URL!", Toast.LENGTH_SHORT).show();
						return;
					}
				}


				onConfirm();
				dismiss();
			});
		});


		return dialog;
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		//Need to return this here or onViewCreated won't be called
		return binding.getRoot();
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		//---------------------------------------------------------
		// Name

		binding.name.setText(viewModel.itemName);
		binding.name.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
			@Override
			public void afterTextChanged(Editable s) {}
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				viewModel.itemName = s.toString();
			}
		});

		//---------------------------------------------------------
		// Color

		//Get the default card background color from the theme
		int defaultBorder = Color.BLACK;
		//TypedValue typedValue = new TypedValue();
		//if (requireContext().getTheme().resolveAttribute(com.google.android.material.R.attr.colorSurface , typedValue, true))
		//	defaultBorder = typedValue.data;

		//Set the background color of the button to the current color
		GradientDrawable drawable = new GradientDrawable();
		drawable.setShape(GradientDrawable.RECTANGLE);
		drawable.setColor(viewModel.color);
		drawable.setStroke(4, defaultBorder);
		binding.colorPickerButton.setBackground(drawable);


		//Deselect the slider's currently selected item
		binding.colorSlider.setSelection(-1);
		binding.colorSlider.setListener((position, newColor) -> {
			viewModel.color =  newColor | 0xFF000000;		//Set alpha to 100%

			//Change the color picker button's background color
			GradientDrawable background = (GradientDrawable) binding.colorPickerButton.getBackground();
			background.setColor(viewModel.color);
			binding.colorPickerButton.setBackground(background);
		});


		binding.colorPickerButton.setOnClickListener(v -> {
			ColorPickerModal.launch(requireParentFragment(), viewModel.color, newColor -> {
				//viewModel.color = newColor | 0xFF000000;		//Set alpha to 100%
				viewModel.color = newColor;						//ColorPicker is now allowed to set alpha when selecting transparent

				//Change the color picker button's background color
				GradientDrawable background = (GradientDrawable) binding.colorPickerButton.getBackground();
				background.setColor(viewModel.color);
				binding.colorPickerButton.setBackground(background);

				//Unselect the slider's currently selected item
				binding.colorSlider.setSelection(-1);
			});
		});


		Drawable checkerboard = AlphaCheckerboard.createCheckerboardDrawable(20, Color.LTGRAY, Color.WHITE);
		binding.alphaCheckerboard.setBackground(checkerboard);

		//---------------------------------------------------------
		// Dropdown

		binding.dropdown.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				String dropdownItem = parent.getItemAtPosition(position).toString();
				if(dropdownItem.equals("Link"))
					binding.linkInfo.setVisibility(View.VISIBLE);
				else
					binding.linkInfo.setVisibility(View.GONE);
			}
			@Override
			public void onNothingSelected(AdapterView<?> parent) {
			}
		});
		binding.dropdown.setSelection( dropdownAdapter.getPosition(viewModel.selectedDropdownItem) );

		if(viewModel.selectedDropdownItem.equals("Link"))
			binding.linkInfo.setVisibility(View.VISIBLE);
		else
			binding.linkInfo.setVisibility(View.GONE);

		//---------------------------------------------------------
		// Link Selection

		binding.linkType.setOnCheckedChangeListener((group, checkedId) -> {
			if(checkedId == R.id.internal_link) {
				viewModel.isInternalLinkSelected = true;
				binding.linkInternal.setVisibility(View.VISIBLE);
				binding.linkExternal.setVisibility(View.GONE);
			}
			else {
				viewModel.isInternalLinkSelected = false;
				binding.linkInternal.setVisibility(View.GONE);
				binding.linkExternal.setVisibility(View.VISIBLE);
			}
		});
		if(viewModel.isInternalLinkSelected)
			binding.linkType.check(R.id.internal_link);
		else
			binding.linkType.check(R.id.external_link);

		//---------------------------------------------------------
		// Internal Link

		binding.targetInternal.setText(viewModel.internalTargetName);

		ImageButton browse = view.findViewById(R.id.browse);
		browse.setOnClickListener(v -> {
			LinkSelectFragment fragment = LinkSelectFragment.newInstance(viewModel.startDir);
			fragment.setLinkSelectCallback(target -> {
				viewModel.internalTarget = new InternalTarget(target.fileUID, target.parentUID);
				viewModel.internalTargetName = FilenameUtils.removeExtension(target.getPrettyName());

				binding.targetInternal.setText(viewModel.internalTargetName);
			});

			requireParentFragment().getChildFragmentManager().addOnBackStackChangedListener(new FragmentManager.OnBackStackChangedListener() {
				@Override
				public void onBackStackChanged() {
					//If back stack is empty, child fragment is gone
					if (requireParentFragment().getChildFragmentManager().getBackStackEntryCount() == 0) {
						if (getDialog() != null)
							getDialog().show();
						requireParentFragment().getChildFragmentManager().removeOnBackStackChangedListener(this);
					}
				}
			});
			requireParentFragment().getChildFragmentManager().beginTransaction()
					.replace(R.id.dir_child_container, fragment, LinkSelectFragment.class.getSimpleName())
					.addToBackStack(null)
					.commit();

			requireDialog().hide();
		});

		//---------------------------------------------------------
		// External Link

		binding.targetExternal.setText(viewModel.externalTarget);
		binding.targetExternal.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
			@Override
			public void afterTextChanged(Editable s) {}
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				viewModel.externalTarget = s.toString();
			}
		});

		//---------------------------------------------------------
	}



	protected void onConfirm() {
		String dropdownItem = binding.dropdown.getSelectedItem().toString();

		boolean isDir = dropdownItem.equals("Directory");
		boolean isLink = dropdownItem.equals("Link");

		//Add a file extension based on the dropdown choice
		String fileName = binding.name.getText().toString();
		if(dropdownItem.equals("Notes"))
			fileName += ".rtf";
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
				if(viewModel.color != null) {
					JsonObject attributes = new JsonObject();
					attributes.addProperty("color", viewModel.color);
					hAPI.setAttributes(newFileUID, attributes, HFile.defaultAttrHash);
				}

				//Write the link target to the new file
				if(isLink) {
					LinkTarget linkTarget;
					if(binding.linkType.getCheckedRadioButtonId() == R.id.internal_link)
						linkTarget = viewModel.internalTarget;
					else
						linkTarget = new ExternalTarget(Uri.parse(viewModel.externalTarget));

					hAPI.writeFile(newFileUID, linkTarget.toString().getBytes(), HFile.defaultChecksum);
				}
			}
			catch (FileNotFoundException e) {
				new Handler(Looper.getMainLooper()).post(() -> {
					Toast.makeText(requireContext(), "File not found!", Toast.LENGTH_SHORT).show();
				});
			}
			catch (ConnectException e) {
				new Handler(Looper.getMainLooper()).post(() -> {
					Toast.makeText(requireContext(), "Unable to connect to server!", Toast.LENGTH_SHORT).show();
				});
			} catch (IOException e) {
				//Ignore idgaf
			} finally {
				hAPI.unlockLocal(newFileUID);
			}


			//Add the new file to the top of the current directory
			UUID dirUID = viewModel.startDir.fileUID;
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
				//Ignore idgaf
			} finally {
				hAPI.unlockLocal(dirUID);
			}
		});
		thread.start();
	}
}
