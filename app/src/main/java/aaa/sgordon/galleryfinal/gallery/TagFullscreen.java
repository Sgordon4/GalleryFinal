package aaa.sgordon.galleryfinal.gallery;

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
import androidx.fragment.app.FragmentManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;

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
				dirViewModel.onQueryChanged(charSequence.toString());
			}

			@Override
			public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}
			@Override
			public void afterTextChanged(Editable editable) {}
		});

		searchClear.setOnClickListener(view2 -> search.setText(""));


		dirViewModel.filteredTags.observe(getViewLifecycleOwner(), tags -> {
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

			List<Chip> chips = new ArrayList<>();
			for(String tag : tags) {
				Chip chip = (Chip) dirFragment.getLayoutInflater().inflate(R.layout.dir_tag_chip, chipGroup, false);
				chip.setText(tag);
				chips.add(chip);
			}

			chipGroup.removeAllViews();
			for(Chip chip : chips)
				chipGroup.addView(chip);
		});
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
