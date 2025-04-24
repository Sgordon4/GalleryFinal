package aaa.sgordon.galleryfinal.repository.gallery.components.link;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.UUID;

public class InternalTarget implements LinkTarget {
	@NonNull
	public final UUID parentUID;
	@NonNull
	public final UUID fileUID;
	@NonNull
	public final Type type;

	public enum Type {
		DIRECTORY,
		LINK,
		DIVIDER,
		NORMAL
	}


	public InternalTarget(@NonNull UUID parentUID, @NonNull UUID fileUID, @NonNull Type type) {
		this.parentUID = parentUID;
		this.fileUID = fileUID;
		this.type = type;
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
