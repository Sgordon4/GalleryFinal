package aaa.sgordon.galleryfinal;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import aaa.sgordon.galleryfinal.repository.combined.GalleryRepo;

public class MainViewModel extends AndroidViewModel {
	public GalleryRepo galleryRepo;
	public int testInt;

	public MainViewModel(@NonNull Application application) {
		super(application);

		galleryRepo = GalleryRepo.getInstance();
		testInt = 0;
	}
}
