package aaa.sgordon.galleryfinal.gallery;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.file.Path;
import java.util.UUID;

import aaa.sgordon.galleryfinal.repository.hybrid.database.HZone;
import aaa.sgordon.galleryfinal.repository.hybrid.types.HFile;

public class ListItem {
	@NonNull
	public final UUID fileUID;
	@Nullable
	public final UUID parentUID;
	@NonNull
	public final String name;
	@NonNull
	public final Path pathFromRoot;
	@NonNull
	public final ListItemType type;
	@NonNull
	public final HFile fileProps;
	@Nullable
	public final HZone zoning;

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
	

	public ListItem(@NonNull UUID fileUID, @Nullable UUID parentUID, @NonNull String name, @NonNull Path pathFromRoot,
					@NonNull HFile fileProps, @Nullable HZone zoning, @NonNull ListItemType type) {
		this.fileUID = fileUID;
		this.parentUID = parentUID;
		this.name = name;
		this.pathFromRoot = pathFromRoot;
		this.fileProps = fileProps;
		this.zoning = zoning;
		this.type = type;
	}

	@NonNull
	@Override
	public String toString() {
		return "ListItem{" +
				"fileUID=" + fileUID +
				", parentUID=" + parentUID +
				", isDir=" + fileProps.isdir +
				", isLink=" + fileProps.islink +
				", zoning=" + zoning +
				", type=" + type +
				", name='" + name + '\'' +
				", filePath=" + pathFromRoot +
				'}';
	}


	public static class Builder {
		private UUID fileUID;
		private UUID parentUID;
		private String name;
		private Path filePath;
		private HFile fileProps;
		private HZone zoning;
		private ListItemType type;

		public Builder(@NonNull ListItem item) {
			this.fileUID = item.fileUID;
			this.parentUID = item.parentUID;
			this.name = item.name;
			this.filePath = item.pathFromRoot;
			this.fileProps = HFile.copy(item.fileProps);
			this.zoning = (item.zoning == null) ? null : HZone.copy(item.zoning);
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
		public Builder setName(@NonNull String name) {
			this.name = name;
			return this;
		}
		public Builder setFilePath(@NonNull Path filePath) {
			this.filePath = filePath;
			return this;
		}
		public Builder setFileProps(@NonNull HFile fileProps) {
			this.fileProps = fileProps;
			return this;
		}
		public Builder setZoning(@Nullable HZone zoning) {
			this.zoning = zoning;
			return this;
		}
		public Builder setType(@NonNull ListItemType type) {
			this.type = type;
			return this;
		}

		public ListItem build() {
			return new ListItem(fileUID, parentUID, name, filePath, fileProps, zoning, type);
		}
	}
}