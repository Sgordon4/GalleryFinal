package aaa.sgordon.galleryfinal.gallery.viewsetups;

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

import java.util.HashSet;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.databinding.FragmentDirectoryBinding;
import aaa.sgordon.galleryfinal.gallery.DirFragment;
import aaa.sgordon.galleryfinal.gallery.DirectoryViewModel;

public class FilterSetup {
	public static void setupFilters(@NonNull DirFragment dirFragment) {
		FragmentDirectoryBinding binding = dirFragment.binding;
		DirectoryViewModel dirViewModel = dirFragment.dirViewModel;
		MaterialToolbar toolbar = binding.galleryAppbar.toolbar;



		//Listen for text changes in the search bar
		binding.galleryAppbar.filterBar.search.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
				dirViewModel.setQuery(charSequence.toString());
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

		binding.galleryAppbar.filterBar.searchGo.setOnClickListener(view2 -> {
			dirViewModel.activeQuery.postValue(binding.galleryAppbar.filterBar.search.getText().toString());
			dirViewModel.onActiveQueryChanged();
		});

		binding.galleryAppbar.filterBar.searchClear.setOnClickListener(view2 -> {
			binding.galleryAppbar.filterBar.search.setText("");
		});

		//-----------------------------------------------------------------------------------------
		//Color icons based on filter results

		binding.galleryAppbar.filterBar.tagClear.setOnClickListener(view2 -> {
			dirViewModel.activeTags.postValue(new HashSet<>());
			//dirViewModel.onActiveTagsChanged();
			//Using this instead so we refresh tag list too (make sure this doesn't backfire if we change things)
			dirViewModel.onActiveQueryChanged();
		});

		dirViewModel.activeQuery.observe(dirFragment.getViewLifecycleOwner(), query -> {
			ImageButton searchGo = binding.galleryAppbar.filterBar.searchGo;
			searchGo.setSelected(!query.isEmpty());
		});
		dirViewModel.activeTags.observe(dirFragment.getViewLifecycleOwner(), tags -> {
			ImageButton tagClear = binding.galleryAppbar.filterBar.tagClear;
			tagClear.setSelected(!tags.isEmpty());
		});


		//The filter button itself unfortunately can't just use a selector since it's in a menu so it has to be special
		final int activeColor = ContextCompat.getColor(dirFragment.getContext(), R.color.goldenrod);
		dirViewModel.activeQuery.observe(dirFragment.getViewLifecycleOwner(), query -> {
			boolean active = !query.isEmpty() || !dirViewModel.activeTags.getValue().isEmpty();
			MenuItem filterItem = toolbar.getMenu().findItem(R.id.gallery_filter);
			Drawable filterDrawable = filterItem.getIcon();

			if(active) DrawableCompat.setTint(filterDrawable, activeColor);
			else filterItem.setIcon(R.drawable.icon_filter);		//Reset the color to default by just resetting the icon
		});
		dirViewModel.activeTags.observe(dirFragment.getViewLifecycleOwner(), tags -> {
			boolean active = !dirViewModel.activeQuery.getValue().isEmpty() || !tags.isEmpty();
			MenuItem filterItem = toolbar.getMenu().findItem(R.id.gallery_filter);
			Drawable filterDrawable = filterItem.getIcon();

			if(active) DrawableCompat.setTint(filterDrawable, activeColor);
			else filterItem.setIcon(R.drawable.icon_filter);		//Reset the color to default by just resetting the icon
		});
	}
}
