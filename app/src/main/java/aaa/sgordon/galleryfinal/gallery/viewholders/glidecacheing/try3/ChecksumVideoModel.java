package aaa.sgordon.galleryfinal.gallery.viewholders.glidecacheing.try3;


import android.net.Uri;

import java.util.Objects;

public class ChecksumVideoModel {
	private final Uri uri;
	private final String checksum;

	public ChecksumVideoModel(Uri uri, String checksum) {
		this.uri = uri;
		this.checksum = checksum;
	}

	public Uri getUri() {
		return uri;
	}

	public String getChecksum() {
		return checksum;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		ChecksumVideoModel that = (ChecksumVideoModel) o;
		return uri.equals(that.uri) && checksum.equals(that.checksum);
	}

	@Override
	public int hashCode() {
		return Objects.hash(uri, checksum);
	}
}
