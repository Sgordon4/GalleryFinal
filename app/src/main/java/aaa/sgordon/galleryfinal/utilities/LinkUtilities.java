package aaa.sgordon.galleryfinal.utilities;

import android.net.Uri;

import androidx.annotation.NonNull;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.URL;
import java.util.List;
import java.util.UUID;

import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridAPI;

public class LinkUtilities {

	public static LinkTarget readLink(UUID linkUID) throws ContentsNotFoundException, FileNotFoundException, ConnectException {
		Uri uri = HybridAPI.getInstance().getFileContent(linkUID).first;

		try (InputStream inputStream = new URL(uri.toString()).openStream();
			 BufferedReader reader = new BufferedReader( new InputStreamReader(inputStream) )) {
			String firstLine = reader.readLine();

			Uri linkUri = Uri.parse(firstLine);

			//If the uri scheme starts with "gallery", it's an internal link
			if ("gallery".equals(linkUri.getScheme())) {
				List<String> pathSegments = uri.getPathSegments();
				UUID dirUID = UUID.fromString(pathSegments.get(0));
				UUID fileUID = UUID.fromString(pathSegments.get(1));

				return new InternalTarget(dirUID, fileUID);
			}
			//Otherwise this points to somewhere on the internet
			else {
				return new ExternalTarget(Uri.parse(firstLine));
			}
		}
		catch (IOException e) { throw new RuntimeException(e); }
	}


	//---------------------------------------------------------------------------------------------

	public interface LinkTarget {
		@NonNull
		Uri toUri();
	}


	public static class InternalTarget implements LinkTarget {
		@NonNull
		private final UUID parentUID;
		@NonNull
		private final UUID fileUID;

		public InternalTarget(@NonNull UUID parentUID, @NonNull UUID fileUID) {
			this.parentUID = parentUID;
			this.fileUID = fileUID;
		}
		@NonNull
		public UUID getParentUID() { return parentUID; }
		@NonNull
		public UUID getFileUID() { return fileUID; }

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


	public static class ExternalTarget implements LinkTarget {
		@NonNull
		private final Uri uri;

		public ExternalTarget(@NonNull Uri uri) {
			this.uri = uri;
		}
		@NonNull
		public Uri getUri() { return uri; }

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
}
