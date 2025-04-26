package aaa.sgordon.galleryfinal.repository.gallery.link;

import android.net.Uri;

import androidx.annotation.NonNull;

public interface LinkTarget {
	@NonNull
	Uri toUri();
}
