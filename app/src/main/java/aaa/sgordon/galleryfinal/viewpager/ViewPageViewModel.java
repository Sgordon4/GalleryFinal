package aaa.sgordon.galleryfinal.viewpager;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import aaa.sgordon.galleryfinal.gallery.ListItem;

public class ViewPageViewModel extends ViewModel {
	public ListItem listItem;

	public ViewPageViewModel(ListItem listItem) {
		this.listItem = listItem;
	}






//=================================================================================================
//=================================================================================================

	public static class Factory implements ViewModelProvider.Factory {
		private final ListItem listItem;

		public Factory(ListItem listItem) {
			this.listItem = listItem;
		}

		@NonNull
		@Override
		public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
			if (modelClass.isAssignableFrom(ViewPageViewModel.class)) {
				return (T) new ViewPageViewModel(listItem);
			}
			throw new IllegalArgumentException("Unknown ViewModel class");
		}
	}
}
