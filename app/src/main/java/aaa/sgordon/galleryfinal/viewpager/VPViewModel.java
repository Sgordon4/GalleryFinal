package aaa.sgordon.galleryfinal.viewpager;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import java.util.List;

import aaa.sgordon.galleryfinal.gallery.ListItem;

public class VPViewModel extends ViewModel {
	public final MutableLiveData<List<ListItem>> list;
	public final VPCallback callback;
	public int currPos;

	public VPViewModel(MutableLiveData<List<ListItem>> list, VPCallback callback) {
		this.list = list;
		this.callback = callback;
		currPos = -1;
	}

	public interface VPCallback {
		void onClose(ListItem currItem);
	}


	public static class Factory implements ViewModelProvider.Factory {
		private final MutableLiveData<List<ListItem>> list;
		private final VPCallback callback;
		public Factory(MutableLiveData<List<ListItem>> list, VPCallback callback) {
			this.list = list;
			this.callback = callback;
		}

		@NonNull
		@Override
		public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
			if (modelClass.isAssignableFrom(VPViewModel.class)) {
				return (T) new VPViewModel(list, callback);
			}
			throw new IllegalArgumentException("Unknown ViewModel class");
		}
	}
}
