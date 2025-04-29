package aaa.sgordon.galleryfinal.gallery.cooking;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.MenuItem;
import android.view.View;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.google.android.material.appbar.MaterialToolbar;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.databinding.FragDirBinding;
import aaa.sgordon.galleryfinal.gallery.DirFragment;
import aaa.sgordon.galleryfinal.gallery.FilterController;
import aaa.sgordon.galleryfinal.gallery.touch.SelectionController;

public class ToolbarStyler {
	private MaterialToolbar toolbar;
	private MaterialToolbar selectionToolbar;


	public void onViewCreated(DirFragment dirFragment, SelectionController selectionController) {
		FragDirBinding binding = dirFragment.binding;

		toolbar = binding.galleryAppbar.toolbar;
		selectionToolbar = binding.galleryAppbar.selectionToolbar;


		toolbar.setNavigationOnClickListener(view4 -> dirFragment.getParentFragmentManager().popBackStack());

		selectionToolbar.setNavigationOnClickListener(view2 -> selectionController.stopSelecting());

		//Hide the navigation icon when we're at the top-level
		if(dirFragment.getParentFragmentManager().getBackStackEntryCount() <= 1)
			toolbar.setNavigationIcon(null);

		//If we're at the top level
		if(dirFragment.dirViewModel.listItem.parentUID == null) {
			MaterialToolbar toolbar = binding.galleryAppbar.toolbar;
			toolbar.getMenu().clear();
			toolbar.inflateMenu(R.menu.gallery_menu_main_toplevel);
		}

		//Must set title after configuration
		String directoryName = dirFragment.dirViewModel.listItem.getPrettyName();
		toolbar.setTitle(directoryName);




		//final int activeColor = ContextCompat.getColor(dirFragment.requireContext(), R.color.goldenrod);

		//Get the icon color from the current theme
		final int activeColor;
		TypedValue typedValue = new TypedValue();
		if (dirFragment.requireContext().getTheme().resolveAttribute(androidx.appcompat.R.attr.colorPrimary, typedValue, true)) {
			activeColor = typedValue.data;
		} else {
			activeColor = Color.YELLOW;
		}

		//The filter button itself unfortunately can't just use a selector since it's in a menu so it has to be special
		FilterController.FilterRegistry fRegistry = dirFragment.dirViewModel.getFilterRegistry();
		fRegistry.activeQuery.observe(dirFragment.getViewLifecycleOwner(), query -> {
			boolean active = !query.isEmpty() || !fRegistry.activeTags.getValue().isEmpty();
			MenuItem filterItem = selectionToolbar.getMenu().findItem(R.id.filter);
			Drawable filterDrawable = filterItem.getIcon();

			if(active) DrawableCompat.setTint(filterDrawable, activeColor);
			else filterItem.setIcon(R.drawable.icon_filter);		//Reset the color to default by just resetting the icon
		});
		fRegistry.activeTags.observe(dirFragment.getViewLifecycleOwner(), tags -> {
			boolean active = !fRegistry.activeQuery.getValue().isEmpty() || !tags.isEmpty();
			MenuItem filterItem = selectionToolbar.getMenu().findItem(R.id.filter);
			Drawable filterDrawable = filterItem.getIcon();

			if(active) DrawableCompat.setTint(filterDrawable, activeColor);
			else filterItem.setIcon(R.drawable.icon_filter);		//Reset the color to default by just resetting the icon
		});


		if(selectionController.isSelecting()) {
			selectionToolbar.setTitle( String.valueOf(selectionController.getNumSelected()) );
			toolbar.setVisibility(View.GONE);
			selectionToolbar.setVisibility(View.VISIBLE);
		}
	}


	//---------------------------------------------------------------------------------------------


	public void onSelectionStarted() {
		toolbar.setVisibility(View.GONE);
		selectionToolbar.setVisibility(View.VISIBLE);
	}
	public void onSelectionStopped() {
		toolbar.setVisibility(View.VISIBLE);
		selectionToolbar.setVisibility(View.GONE);
	}
	public void onNumSelectedChanged(int numSelected) {
		selectionToolbar.setTitle( String.valueOf(numSelected) );
		selectionToolbar.getMenu().findItem(R.id.edit).setEnabled(numSelected == 1);	//Disable edit button unless only one item is selected
		selectionToolbar.getMenu().findItem(R.id.tag).setEnabled(numSelected >= 1);		//Disable tag button unless one or more items are selected
		selectionToolbar.getMenu().findItem(R.id.share).setEnabled(numSelected >= 1);	//Disable share button unless one or more items are selected
		selectionToolbar.getMenu().findItem(R.id.trash).setEnabled(numSelected >= 1);	//Disable trash button unless one or more items are selected
		selectionToolbar.getMenu().findItem(R.id.move).setEnabled(numSelected >= 1);	//Disable move button unless one or more items are selected
		selectionToolbar.getMenu().findItem(R.id.copy).setEnabled(numSelected >= 1);	//Disable copy button unless one or more items are selected
		selectionToolbar.getMenu().findItem(R.id.export).setEnabled(numSelected >= 1);	//Disable export button unless one or more items are selected
		selectionToolbar.getMenu().findItem(R.id.zoning).setEnabled(numSelected >= 1);	//Disable zoning button unless one or more items are selected

	}
}
