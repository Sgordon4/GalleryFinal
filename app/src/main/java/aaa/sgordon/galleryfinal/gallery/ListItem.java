package aaa.sgordon.galleryfinal.gallery;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.file.Path;
import java.util.UUID;

public class ListItem {
	@NonNull
	public final UUID fileUID;
	@Nullable
	public final UUID parentUID;
	public final boolean isDir;
	public final boolean isLink;
	@NonNull
	public final String name;
	@NonNull
	public final Path pathFromRoot;
	@NonNull
	public final ListItemType type;

	public enum ListItemType {
		NORMAL,
		DIRECTORY,
		DIVIDER,
		LINKDIRECTORY,
		LINKDIVIDER,
		LINKSINGLE,
		LINKEXTERNAL,
		LINKCYCLE,
		LINKEND,
		LINKBROKEN,
		LINKUNREACHABLE,
		UNREACHABLE
	}
	

	public ListItem(@NonNull UUID fileUID, @Nullable UUID parentUID, boolean isDir, boolean isLink, @NonNull String name, @NonNull Path pathFromRoot, @NonNull ListItemType type) {
		this.fileUID = fileUID;
		this.parentUID = parentUID;
		this.isDir = isDir;
		this.isLink = isLink;
		this.name = name;
		this.pathFromRoot = pathFromRoot;
		this.type = type;
	}

	@NonNull
	@Override
	public String toString() {
		return "ListItem{" +
				"fileUID=" + fileUID +
				", parentUID=" + parentUID +
				", isDir=" + isDir +
				", isLink=" + isLink +
				", type=" + type +
				", name='" + name + '\'' +
				", filePath=" + pathFromRoot +
				'}';
	}


	public static class Builder {
		private UUID fileUID;
		private UUID parentUID;
		private boolean isDir;
		private boolean isLink;
		private String name;
		private Path filePath;
		private ListItemType type;

		public Builder(@NonNull ListItem item) {
			this.fileUID = item.fileUID;
			this.parentUID = item.parentUID;
			this.isDir = item.isDir;
			this.isLink = item.isLink;
			this.name = item.name;
			this.filePath = item.pathFromRoot;
			this.type = item.type;
		}

		public Builder setFileUID(@NonNull UUID fileUID) {
			this.fileUID = fileUID;
			return this;
		}
		public Builder setParentUID(@Nullable UUID parentUID) {
			this.parentUID = parentUID;
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
		public Builder setFilePath(@NonNull Path filePath) {
			this.filePath = filePath;
			return this;
		}
		public Builder setType(@NonNull ListItemType type) {
			this.type = type;
			return this;
		}

		public ListItem build() {
			return new ListItem(fileUID, parentUID, isDir, isLink, name, filePath, type);
		}
	}
}