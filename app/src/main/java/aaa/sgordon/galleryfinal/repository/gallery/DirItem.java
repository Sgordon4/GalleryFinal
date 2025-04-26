package aaa.sgordon.galleryfinal.repository.gallery;

import androidx.annotation.NonNull;

import java.util.Objects;
import java.util.UUID;

public class DirItem {
	@NonNull
	public final UUID fileUID;
	public final boolean isDir;
	public final boolean isLink;
	@NonNull
	public final String name;

	public DirItem(@NonNull UUID fileUID, boolean isDir, boolean isLink, @NonNull String name) {
		this.fileUID = fileUID;
		this.isDir = isDir;
		this.isLink = isLink;
		this.name = name;
	}

	@NonNull
	@Override
	public String toString() {
		return fileUID +" "+ isDir +" "+ isLink +" "+ name;
	}

	public static class Builder {
		private UUID fileUID;
		private boolean isDir;
		private boolean isLink;
		private String name;

		public Builder(@NonNull DirItem item) {
			this.fileUID = item.fileUID;
			this.isDir = item.isDir;
			this.isLink = item.isLink;
			this.name = item.name;
		}

		public Builder setFileUID(@NonNull UUID fileUID) {
			this.fileUID = fileUID;
			return this;
		}
		public Builder setIsDir(boolean isDir) {
			this.isDir = isDir;
			return this;
		}
		public Builder setIsLink(boolean isLink) {
			this.isLink = isLink;
			return this;
		}
		public Builder setName(@NonNull String name) {
			this.name = name;
			return this;
		}

		public DirItem build() {
			return new DirItem(fileUID, isDir, isLink, name);
		}
	}


	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		DirItem dirItem = (DirItem) o;
		return Objects.equals(fileUID, dirItem.fileUID);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(fileUID);
	}
}
