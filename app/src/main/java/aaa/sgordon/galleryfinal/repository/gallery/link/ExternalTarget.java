package aaa.sgordon.galleryfinal.repository.gallery.components.link;

import android.net.Uri;

import androidx.annotation.NonNull;

public class ExternalTarget implements LinkTarget{
	@NonNull
	private final Uri uri;

	public ExternalTarget(@NonNull Uri uri) {
		this.uri = uri;
	}

	@NonNull
	@Override
	public String toString() {
		return toUri().toString();
	}

	@NonNull
	public Uri toUri() {
		return uri;
	}
}
