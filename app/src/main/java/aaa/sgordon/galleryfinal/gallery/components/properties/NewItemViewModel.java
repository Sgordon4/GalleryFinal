package aaa.sgordon.galleryfinal.gallery.components.properties;

import android.graphics.Color;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.google.gson.JsonObject;

import aaa.sgordon.galleryfinal.repository.gallery.ListItem;
import aaa.sgordon.galleryfinal.repository.gallery.link.InternalTarget;

public class NewItemViewModel extends ViewModel {
	protected final ListItem startDir;
	protected final ListItem startItem;
	protected final JsonObject startAttr;

	public String itemName = "";
	public String selectedDropdownItem = "Directory";
	public Integer color = Color.TRANSPARENT;

	public boolean isInternalLinkSelected = true;
	public InternalTarget internalTarget = null;
	public String internalTargetName = "";
	public String externalTarget = "";

	public NewItemViewModel(ListItem startDir, ListItem startItem, JsonObject startAttr) {
		this.startDir = startDir;
		this.startItem = startItem;
		this.startAttr = startAttr;
	}


//=================================================================================================
//=================================================================================================

	public static class Factory implements ViewModelProvider.Factory {
		private final ListItem startDir;
		private final ListItem startItem;
		private final JsonObject startAttr;

		public Factory(ListItem startDir, ListItem startItem, JsonObject startAttr) {
			this.startDir = startDir;
			this.startItem = startItem;
			this.startAttr = startAttr;
		}

		@NonNull
		@Override
		public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
			if (modelClass.isAssignableFrom(NewItemViewModel.class)) {
				return (T) new NewItemViewModel(startDir, startItem, startAttr);
			}
			throw new IllegalArgumentException("Unknown ViewModel class");
		}
	}
}
