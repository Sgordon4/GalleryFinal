package aaa.sgordon.galleryfinal;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import aaa.sgordon.galleryfinal.repository.hybrid.HybridAPI;

public class MainViewModel extends AndroidViewModel {
	public HybridAPI hAPI;
	public int testInt;

	public MainViewModel(@NonNull Application application) {
		super(application);

		hAPI = HybridAPI.getInstance();
		testInt = 0;
	}
}
