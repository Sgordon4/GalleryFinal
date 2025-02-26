package aaa.sgordon.galleryfinal.gallery.viewsetups;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.inputmethod.EditorInfo;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.databinding.FragmentDirectoryBinding;
import aaa.sgordon.galleryfinal.gallery.DirFragment;
import aaa.sgordon.galleryfinal.gallery.DirectoryViewModel;
import aaa.sgordon.galleryfinal.gallery.FilterController;

public class FilterSetup {
	public static void setupFilters(@NonNull DirFragment dirFragment) {
		FragmentDirectoryBinding binding = dirFragment.binding;
		DirectoryViewModel dirViewModel = dirFragment.dirViewModel;
		MaterialToolbar toolbar = binding.galleryAppbar.toolbar;
		FilterController fControl = dirViewModel.getFilterController();


		//Listen for text changes in the search bar
		binding.galleryAppbar.filterBar.search.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
				fControl.onQueryChanged(charSequence.toString(), dirViewModel.fullTags.getValue());
			}

			@Override
			public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}
			@Override
			public void afterTextChanged(Editable editable) {}
		});

		//Upon pressing enter, update active query
		binding.galleryAppbar.filterBar.search.setOnEditorActionListener((textView, actionID, keyEvent) -> {
			if(actionID == EditorInfo.IME_ACTION_DONE || (keyEvent != null && keyEvent.getAction() == KeyEvent.ACTION_DOWN))
				binding.galleryAppbar.filterBar.searchGo.callOnClick();		//Listener is defined just below
			return true;
		});

		binding.galleryAppbar.filterBar.searchGo.setOnClickListener(view2 ->
				fControl.onActiveQueryChanged( binding.galleryAppbar.filterBar.search.getText().toString() , dirViewModel.fullList.getValue()));

		binding.galleryAppbar.filterBar.searchClear.setOnClickListener(view2 ->
				binding.galleryAppbar.filterBar.search.setText(""));

		//-----------------------------------------------------------------------------------------
		//Color icons based on filter results

		binding.galleryAppbar.filterBar.tagClear.setOnClickListener(view2 -> {
			fControl.onActiveTagsChanged(new HashSet<>(), dirViewModel.fullList.getValue());
			//Using this too so we refresh tag list (make sure this doesn't backfire if we change things)
			//dirViewModel.onActiveQueryChanged(dirViewModel.activeQuery.getValue());
		});

		fControl.activeQuery.observe(dirFragment.getViewLifecycleOwner(), query -> {
			ImageButton searchGo = binding.galleryAppbar.filterBar.searchGo;
			searchGo.setSelected(!query.isEmpty());
		});
		fControl.activeTags.observe(dirFragment.getViewLifecycleOwner(), tags -> {
			ImageButton tagClear = binding.galleryAppbar.filterBar.tagClear;
			tagClear.setSelected(!tags.isEmpty());
		});


		//The filter button itself unfortunately can't just use a selector since it's in a menu so it has to be special
		final int activeColor = ContextCompat.getColor(dirFragment.getContext(), R.color.goldenrod);
		fControl.activeQuery.observe(dirFragment.getViewLifecycleOwner(), query -> {
			boolean active = !query.isEmpty() || !fControl.activeTags.getValue().isEmpty();
			MenuItem filterItem = toolbar.getMenu().findItem(R.id.filter);
			Drawable filterDrawable = filterItem.getIcon();

			if(active) DrawableCompat.setTint(filterDrawable, activeColor);
			else filterItem.setIcon(R.drawable.icon_filter);		//Reset the color to default by just resetting the icon
		});
		fControl.activeTags.observe(dirFragment.getViewLifecycleOwner(), tags -> {
			boolean active = !fControl.activeQuery.getValue().isEmpty() || !tags.isEmpty();
			MenuItem filterItem = toolbar.getMenu().findItem(R.id.filter);
			Drawable filterDrawable = filterItem.getIcon();

			if(active) DrawableCompat.setTint(filterDrawable, activeColor);
			else filterItem.setIcon(R.drawable.icon_filter);		//Reset the color to default by just resetting the icon
		});


		//-----------------------------------------------------------------------------------------


		fControl.activeTags.observe(dirFragment.getViewLifecycleOwner(), tags -> {
			ChipGroup chipGroup = binding.galleryAppbar.filterBar.chipGroup;
			//Make sure each chip is checked/unchecked based on the active tags, which can be updated in the background
			for(int i = 0; i < chipGroup.getChildCount(); i++) {
				Chip chip = (Chip) chipGroup.getChildAt(i);

				boolean isActive = tags.contains(chip.getText().toString());
				chip.setChecked(isActive);
			}
		});


		fControl.filteredTags.observe(dirFragment.getViewLifecycleOwner(), tags -> {
			ChipGroup chipGroup = binding.galleryAppbar.filterBar.chipGroup;

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


			//List<String> sortedTags = tags.stream().sorted().collect(Collectors.toList());
			List<String> sortedTags = tags.stream().sorted((a, b) -> {
				//Check if the items are active
				Set<String> activeTags = fControl.activeTags.getValue();
				boolean isActive_A = activeTags.contains(a);
				boolean isActive_B = activeTags.contains(b);

				//If a is active but b is not, a comes before b
				if (isActive_A && !isActive_B) return -1;
				if (!isActive_A && isActive_B) return 1;

				//If both are active or both are not, sort alphabetically
				return a.compareTo(b);
			}).collect(Collectors.toList());

			//Grab the currently displayed tags
			List<String> currentTags = new ArrayList<>();
			for(int i = 0; i < chipGroup.getChildCount(); i++) {
				Chip chip = (Chip) chipGroup.getChildAt(i);
				currentTags.add(chip.getText().toString());
			}

			//If there are no visible changes, do nothing
			if(sortedTags.equals(currentTags))
				return;

			//Create a new list of chips to display based on the sorted tags
			List<Chip> chips = new ArrayList<>();
			for(String tag : sortedTags) {
				Chip chip = (Chip) dirFragment.getLayoutInflater().inflate(R.layout.dir_tag_chip, chipGroup, false);
				chip.setText(tag);

				if(fControl.activeTags.getValue().contains(tag)) {
					chip.setChecked(true);
				}

				chip.setOnClickListener(view2 -> {
					Set<String> activeTags = fControl.activeTags.getValue();
					boolean isChecked = activeTags.contains(tag);
					if(isChecked)
						activeTags.remove(tag);
					else
						activeTags.add(tag);

					fControl.onActiveTagsChanged(activeTags, dirViewModel.fullList.getValue());
				});

				chips.add(chip);
			}

			chipGroup.removeAllViews();
			for(Chip chip : chips)
				chipGroup.addView(chip);
		});

	}
}
