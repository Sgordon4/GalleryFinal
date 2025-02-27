package aaa.sgordon.galleryfinal.gallery;

import android.app.Dialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import aaa.sgordon.galleryfinal.R;

public class TagFullscreen extends DialogFragment {
	private final DirFragment dirFragment;
	private final DirectoryViewModel dirViewModel;
	private MaterialToolbar toolbar;
	private EditText search;
	private ImageButton searchClear;
	private ChipGroup chipGroup;

	public static void launch(DirFragment fragment) {
		TagFullscreen dialog = new TagFullscreen(fragment);
		dialog.show(fragment.getChildFragmentManager(), "tag_fullscreen");
	}
	private TagFullscreen(DirFragment dirFragment) {
		this.dirFragment = dirFragment;
		this.dirViewModel = dirFragment.dirViewModel;
	}


	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_directory_tag_fullscreen, container, false);
		toolbar = view.findViewById(R.id.toolbar);
		search = view.findViewById(R.id.search);
		searchClear = view.findViewById(R.id.search_clear);
		chipGroup = view.findViewById(R.id.chip_group);
		return view;
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		toolbar.setNavigationOnClickListener(v -> dismiss());
		toolbar.setTitle("Apply Tags");
		toolbar.setOnMenuItemClickListener(item -> {
			dismiss();
			return true;
		});

		//Use this for the fullscreen filter, not this one
		//search.setText(dirViewModel.query.getValue());

		search.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
				Set<String> filteredTags = dirViewModel.fileTags.getValue().stream()
						.filter(tag -> tag.contains(charSequence.toString()))
						.collect(Collectors.toSet());
				refreshChips(filteredTags);
				//dirViewModel.onQueryChanged(charSequence.toString());
			}

			@Override
			public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}
			@Override
			public void afterTextChanged(Editable editable) {}
		});

		searchClear.setOnClickListener(view2 -> search.setText(""));


		//dirViewModel.getFilterController().filteredTags.observe(getViewLifecycleOwner(), tags -> );
	}

	private void refreshChips(Set<String> tags) {
		if(tags.isEmpty()) {
			chipGroup.removeAllViews();
			Chip noTags = (Chip) dirFragment.getLayoutInflater().inflate(R.layout.dir_tag_chip, chipGroup, false);
			noTags.setText("No tags");
			noTags.setTextColor(Color.GRAY);
			noTags.setClickable(false);
			noTags.setCheckable(false);
			chipGroup.addView(noTags);
			return;
		}

		//Make the chips for all the given tags
		List<Chip> chips = new ArrayList<>();
		for(String tag : tags) {
			Chip chip = (Chip) dirFragment.getLayoutInflater().inflate(R.layout.dir_tag_chip, chipGroup, false);
			chip.setText(tag);
			chips.add(chip);
		}

		//Grab the tags of the selected files
		Set<UUID> selected = dirViewModel.getSelectionRegistry().getSelectedList();
		Map<String, Integer> tagCount = new HashMap<>();
		for(UUID fileUID : selected) {
			try {
				//Grab the list of tags for this file from its attributes
				JsonArray tagArray = dirViewModel.getAttrCache().getAttr(fileUID).getAsJsonArray("tags");
				if(tagArray == null) continue;

				//Convert the JsonArray to a List of Strings
				List<String> fileTags = tagArray.asList().stream().map(JsonElement::getAsString).collect(Collectors.toList());

				//Add the tags to our tag count
				for(String tag : fileTags) {
					if(!tagCount.containsKey(tag))
						tagCount.put(tag, 0);
					tagCount.put(tag, tagCount.get(tag) + 1);
				}
			} catch (FileNotFoundException e) {
				//Just skip if we can't find the file
			}
		}


		//Style the chips based on if they appear in the tags of the selected items
		int selectedCount = selected.size();
		for(Chip chip : chips) {
			String tag = chip.getText().toString();
			if(tagCount.containsKey(tag)) {
				int count = tagCount.get(tag);
				if(count == selectedCount) {
					chip.setChecked(true);
				}
				else if(count > 0) {
					chip.setSelected(true);
				}
			}
		}

		for(Chip chip : chips) {
			chip.setOnClickListener(view1 -> {
				System.out.println("Clicking on "+chip.getText());
			});
		}



		chipGroup.removeAllViews();
		for(Chip chip : chips)
			chipGroup.addView(chip);
	}




	@Override
	public void onStart() {
		super.onStart();
		Dialog dialog = getDialog();
		if (dialog != null) {
			int width = ViewGroup.LayoutParams.MATCH_PARENT;
			int height = ViewGroup.LayoutParams.MATCH_PARENT;
			dialog.getWindow().setLayout(width, height);
		}
	}

	@Override
	public void onStop() {
		super.onStop();
		//Use this for the fullscreen filter, not this one
		//dirFragment.binding.galleryAppbar.filterBar.search.setText(dirViewModel.query.getValue());
	}
}
