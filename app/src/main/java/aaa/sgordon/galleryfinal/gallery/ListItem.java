package aaa.sgordon.galleryfinal.gallery;

import com.google.gson.JsonObject;

import java.nio.file.Path;
import java.util.UUID;

public class ListItem {
	public final UUID fileUID;
	public final UUID parentUID;
	public final String name;
	public final Path filePath;
	public final int fileSize;
	public final boolean isDir;
	public final boolean isLink;
	public final JsonObject attr;
	public final ListItemType type;

	public enum ListItemType {
		NORMAL,
		DIRECTORY,
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
	

	public ListItem(Path filePath, UUID fileUID, UUID parentUID, String name, int fileSize,
					boolean isDir, boolean isLink, JsonObject attr, ListItemType type) {
		this.fileUID = fileUID;
		this.parentUID = parentUID;
		this.name = name;
		this.filePath = filePath;
		this.fileSize = fileSize;
		this.isDir = isDir;
		this.isLink = isLink;
		this.attr = attr;
		this.type = type;
	}

	@Override
	public String toString() {
		return "ListItem{" +
				"fileUID=" + fileUID +
				", parentUID=" + parentUID +
				", isDir=" + isDir +
				", isLink=" + isLink +
				", type=" + type +
				", name='" + name + '\'' +
				", fileSize=" + fileSize +
				", filePath=" + filePath +
				'}';
	}


	public static class Builder {
		private UUID fileUID;
		private UUID parentUID;
		private String name;
		private Path filePath;
		private int fileSize;
		private boolean isDir;
		private boolean isLink;
		private JsonObject attr;
		private ListItemType type;

		public Builder() {}
		public Builder(ListItem item) {
			this.fileUID = item.fileUID;
			this.parentUID = item.parentUID;
			this.name = item.name;
			this.filePath = item.filePath;
			this.fileSize = item.fileSize;
			this.isDir = item.isDir;
			this.isLink = item.isLink;
			this.attr = item.attr;
			this.type = item.type;
		}

		public Builder setFileUID(UUID fileUID) {
			this.fileUID = fileUID;
			return this;
		}
		public Builder setParentUID(UUID parentUID) {
			this.parentUID = parentUID;
			return this;
		}
		public Builder setName(String name) {
			this.name = name;
			return this;
		}
		public Builder setFilePath(Path filePath) {
			this.filePath = filePath;
			return this;
		}
		public Builder setFileSize(int fileSize) {
			this.fileSize = fileSize;
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
		public Builder setAttr(JsonObject attr) {
			this.attr = attr;
			return this;
		}
		public Builder setType(ListItemType type) {
			this.type = type;
			return this;
		}

		public ListItem build() {
			return new ListItem(filePath, fileUID, parentUID, name, fileSize, isDir, isLink, attr, type);
		}
	}
}