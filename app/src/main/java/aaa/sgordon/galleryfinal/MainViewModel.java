package aaa.sgordon.galleryfinal;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import aaa.sgordon.galleryfinal.repository.gallery.caches.AttrCache;
import aaa.sgordon.galleryfinal.repository.gallery.caches.DirCache;
import aaa.sgordon.galleryfinal.repository.gallery.caches.LinkCache;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridAPI;

public class MainViewModel extends AndroidViewModel {
	public HybridAPI hAPI;
	public DirCache dirCache;
	public LinkCache linkCache;
	public AttrCache attrCache;

	public int testInt;

	public MainViewModel(@NonNull Application application) {
		super(application);

		hAPI = HybridAPI.getInstance();
		dirCache = DirCache.getInstance();
		linkCache = LinkCache.getInstance();
		attrCache = AttrCache.getInstance();

		testInt = 0;
	}
}
