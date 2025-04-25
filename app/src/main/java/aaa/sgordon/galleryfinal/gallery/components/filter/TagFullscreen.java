package aaa.sgordon.galleryfinal.gallery.components.filter;

import android.app.Dialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.io.FileNotFoundException;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.gallery.DirFragment;
import aaa.sgordon.galleryfinal.gallery.DirectoryViewModel;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridAPI;
import aaa.sgordon.galleryfinal.repository.hybrid.types.HFile;

public class TagFullscreen extends DialogFragment {
	private final DirFragment dirFragment;
	private final DirectoryViewModel dirViewModel;
	private MaterialToolbar toolbar;
	private EditText search;
	private ImageButton searchClear;
	private ImageButton tagAdd;
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
		View view = inflater.inflate(R.layout.frag_dir_tag_fullscreen, container, false);
		toolbar = view.findViewById(R.id.toolbar);
		search = view.findViewById(R.id.search);
		searchClear = view.findViewById(R.id.search_clear);
		tagAdd = view.findViewById(R.id.tag_add);
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
				Map<String, Set<UUID>> filtered = filterTags(charSequence.toString(), dirViewModel.getFileTags());
				refreshChips(filtered);
				//dirViewModel.onQueryChanged(charSequence.toString());
			}

			@Override
			public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}
			@Override
			public void afterTextChanged(Editable editable) {}
		});

		searchClear.setOnClickListener(view2 -> search.setText(""));

		tagAdd.setOnClickListener(view2 -> {
			//Add the searched text as a new tag
			String tag = search.getText().toString();
			//To every selected item
			Set<UUID> filesToChange = new HashSet<>(dirViewModel.getSelectionRegistry().getSelectedList());

			Thread change = new Thread(() -> updateTags(tag, true, filesToChange));
			change.start();
		});

		dirViewModel.getFileTagsLiveData().observe(getViewLifecycleOwner(), tags -> {
			Map<String, Set<UUID>> filtered = filterTags(search.getText().toString(), dirViewModel.getFileTags());
			refreshChips(filtered);
		});
	}

	private Map<String, Set<UUID>> filterTags(String query, Map<String, Set<UUID>> tags) {
		if(query.isEmpty())
			return tags;

		Map<String, Set<UUID>> filtered = new HashMap<>();
		for(Map.Entry<String, Set<UUID>> entry : tags.entrySet()) {
			if(entry.getKey().contains(query))
				filtered.put(entry.getKey(), entry.getValue());
		}
		return filtered;
	}


	private void refreshChips(Map<String, Set<UUID>> tags) {
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
		List<String> sorted = tags.keySet().stream().sorted().collect(Collectors.toList());
		for(String tag : sorted) {
			Chip chip = (Chip) dirFragment.getLayoutInflater().inflate(R.layout.dir_tag_chip, chipGroup, false);
			chip.setText(tag);
			chips.add(chip);
		}

		//Grab the tags of the selected files
		Set<UUID> selected = dirViewModel.getSelectionRegistry().getSelectedList();


		//Style the chips based on if they appear in the tags of the selected items
		for(Chip chip : chips) {
			String tag = chip.getText().toString();
			if(tags.containsKey(tag)) {
				//Get the set of fileUIDs that have this tag
				Set<UUID> fileUIDs = tags.get(tag);

				//Disregard any fileUIDs that have this tag that aren't in the selected list
				Set<UUID> selectedWithTag = new HashSet<>(selected);
				selectedWithTag.retainAll(fileUIDs);

				//If EVERY selected file has this tag, mark the chip accordingly
				if(selected.size() == selectedWithTag.size()) {
					chip.setChecked(true);
				}
				//If only some of the selected files have this tag, mark the chip accordingly
				else if (!selectedWithTag.isEmpty()) {
					chip.setSelected(true);
				}
			}
		}

		//Upon clicking a chip, add/remove it as a tag from all selected items
		for(Chip chip : chips) {
			chip.setOnClickListener(view1 -> {

				//Of the selected items...
				Set<UUID> filesToChange = new HashSet<>(selected);

				//If we're adding the tag (the chip is now checked)...
				boolean shouldAdd = chip.isChecked();

				//Exclude any files that already have the tag
				String tag = chip.getText().toString();
				if(shouldAdd) filesToChange.removeAll(tags.get(tag));


				Thread change = new Thread(() -> {
					updateTags(tag, shouldAdd, filesToChange);
				});
				change.start();
			});
		}


		chipGroup.removeAllViews();
		for(Chip chip : chips)
			chipGroup.addView(chip);
	}


	public void updateTags(String tag, boolean shouldAdd, Set<UUID> filesToChange) {
		HybridAPI hAPI = HybridAPI.getInstance();

		//For each of the files we're changing...
		for(UUID fileUID : filesToChange) {
			try {
				hAPI.lockLocal(fileUID);

				//Get the file tags from the repository
				HFile fileProps = hAPI.getFileProps(fileUID);
				JsonArray fileTags = fileProps.userattr.getAsJsonArray("tags");
				if(fileTags == null) fileTags = new JsonArray();

				//Convert the JsonArray to a workable format
				//Use a Set rather than just the JsonArray to avoid duplicates
				Set<String> update = new HashSet<>();
				for(JsonElement fileTag : fileTags)
					update.add(fileTag.getAsString());

				//Add or remove the tag from the file tags based on our earlier check
				if(shouldAdd)
					update.add(tag);
				else
					update.remove(tag);


				//Update the tag array in the file attributes
				JsonArray newTags = new JsonArray();
				for(String newTag : update)
					newTags.add(newTag);
				fileProps.userattr.add("tags", newTags);

				//Update the file attributes in the repository
				hAPI.setAttributes(fileUID, fileProps.userattr, fileProps.attrhash);
			} catch (FileNotFoundException e) {
				//Just skip this file if it doesn't exist
			} catch (ConnectException e) {
				//Just skip this file if we can't reach it
				//Looper.prepare();
				//Toast.makeText(getContext(), "Connection error: Could not update tags for a file!", Toast.LENGTH_SHORT).show();
			} finally {
				hAPI.unlockLocal(fileUID);
			}
		}
	}





	@Override
	public void onStart() {
		super.onStart();

		//Make the dialog fullscreen
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
