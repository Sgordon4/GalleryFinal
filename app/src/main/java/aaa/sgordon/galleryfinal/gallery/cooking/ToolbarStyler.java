package aaa.sgordon.galleryfinal.gallery.cooking;

import android.graphics.drawable.Drawable;
import android.view.MenuItem;
import android.view.View;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.google.android.material.appbar.MaterialToolbar;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.databinding.FragDirBinding;
import aaa.sgordon.galleryfinal.gallery.DirFragment;
import aaa.sgordon.galleryfinal.gallery.DirFragmentArgs;
import aaa.sgordon.galleryfinal.gallery.FilterController;
import aaa.sgordon.galleryfinal.gallery.touch.SelectionController;

public class ToolbarStyler {
	private DirFragment dirFragment;
	private SelectionController selectionController;

	private MaterialToolbar toolbar;
	private MaterialToolbar selectionToolbar;


	public void onViewCreated(DirFragment dirFragment, SelectionController selectionController) {
		this.dirFragment = dirFragment;
		this.selectionController = selectionController;

		FragDirBinding binding = dirFragment.binding;

		toolbar = binding.galleryAppbar.toolbar;
		selectionToolbar = binding.galleryAppbar.selectionToolbar;



		NavController navController = Navigation.findNavController(binding.getRoot());
		toolbar.setNavigationOnClickListener(view4 -> navController.popBackStack());

		selectionToolbar.setNavigationOnClickListener(view2 -> selectionController.stopSelecting());


		//Hide the navigation icon when we're at the top-level
		navController.addOnDestinationChangedListener((navController1, navDestination, bundle) -> {
			if(navController1.getPreviousBackStackEntry() == null)
				toolbar.setNavigationIcon(null);
		});

		//TODO Have DirViewModel store a ListItem and use that here instead of directly grabbing args
		//Must set title after configuration
		DirFragmentArgs args = DirFragmentArgs.fromBundle(dirFragment.getArguments());
		String directoryName = args.getDirectoryName();
		toolbar.setTitle(directoryName);



		//The filter button itself unfortunately can't just use a selector since it's in a menu so it has to be special
		FilterController.FilterRegistry fRegistry = dirFragment.dirViewModel.getFilterRegistry();
		final int activeColor = ContextCompat.getColor(dirFragment.requireContext(), R.color.goldenrod);
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
	}
}
