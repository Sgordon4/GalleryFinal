package aaa.sgordon.galleryfinal.repository.gallery.link;

import android.net.Uri;

import androidx.annotation.NonNull;

import java.util.UUID;

public class InternalTarget implements LinkTarget {
	@NonNull
	public final UUID parentUID;
	@NonNull
	public final UUID fileUID;

	public InternalTarget(@NonNull UUID fileUID, @NonNull UUID parentUID) {
		this.parentUID = parentUID;
		this.fileUID = fileUID;
	}

	@NonNull
	@Override
	public String toString() {
		return toUri().toString();
	}

	@NonNull
	public Uri toUri() {
		Uri.Builder builder = new Uri.Builder();
		builder.scheme("gallery").appendPath(parentUID.toString()).appendPath(fileUID.toString());
		return builder.build();
	}
}
