package aaa.sgordon.galleryfinal.repository.gallery;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.onegravity.rteditor.utils.io.FilenameUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import aaa.sgordon.galleryfinal.repository.gallery.caches.LinkCache;
import aaa.sgordon.galleryfinal.utilities.DirUtilities;

public class ListItem {
	@NonNull
	public final UUID fileUID;
	@Nullable
	public final UUID parentUID;
	public final boolean isDir;
	public final boolean isLink;
	@NonNull
	private final String name;
	@NonNull
	public final Path pathFromRoot;
	@NonNull
	public final Type type;

	public enum Type {
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
	

	public ListItem(@NonNull UUID fileUID, @Nullable UUID parentUID, boolean isDir, boolean isLink, @NonNull String name, @NonNull Path pathFromRoot, @NonNull Type type) {
		this.fileUID = fileUID;
		this.parentUID = parentUID;
		this.isDir = isDir;
		this.isLink = isLink;
		this.name = name;
		this.pathFromRoot = pathFromRoot;
		this.type = type;
	}


	public String getRawName() {
		return name;
	}
	public String getPrettyName() {
		String name = this.name;

		if(isHidden())
			name = name.substring(1);
		if(isCollapsed())
			name = name.substring(1);
		if(isTrashed())
			name = FilenameUtils.removeExtension(name);

		return name;
	}

	//-----------------------------------------------

	public boolean isMedia() {
		return isImage() || isGif() || isVideo();
	}
	public boolean isImage() {
		String prettyExtension = FilenameUtils.getExtension(getPrettyName());
		return prettyExtension.equals("jpg") || prettyExtension.equals("jpeg") || prettyExtension.equals("png") ||
				prettyExtension.equals("webp");
	}
	public boolean isGif() {
		String prettyExtension = FilenameUtils.getExtension(getPrettyName());
		return prettyExtension.equals("gif");
	}
	public boolean isVideo() {
		String prettyExtension = FilenameUtils.getExtension(getPrettyName());
		return prettyExtension.equals("mp4") || prettyExtension.equals("mov");
	}



	public boolean isTrashed() {
		return FilenameUtils.getExtension(name).startsWith("trashed_");
	}
	public boolean isHidden() {
		return isDir && name.startsWith(".");
	}
	public boolean isCollapsed() {
		return !isDir && name.startsWith(".");
	}


	public void setTrashed(boolean trashed) {
		if(parentUID == null) return;
		if(!trashed && !isTrashed()) return;
		if(trashed && isTrashed()) return;

		String name = getRawName();
		if(trashed) name += ".trashed_"+Instant.now().getEpochSecond();
		else name = FilenameUtils.removeExtension(name);

		writeName(name);
	}
	public void setHidden(boolean hidden) {
		if(parentUID == null) return;
		if(!isDir) return;
		if(hidden && isHidden()) return;
		if(!hidden && !isHidden()) return;

		String name = getRawName();
		if(!hidden) name = name.substring(1);
		else name = "."+name;

		writeName(name);
	}
	public void setCollapsed(boolean collapsed) {
		if(parentUID == null) return;
		if(isDir) return;
		if(collapsed && isCollapsed()) return;
		if(!collapsed && !isCollapsed()) return;

		String name = getRawName();
		if(!collapsed) name = name.substring(1);
		else name = "."+name;

		writeName(name);
	}


	public void rename(String newPrettyName) {
		if(parentUID == null) return;

		if(isHidden()) newPrettyName = "."+newPrettyName;
		if(isCollapsed()) newPrettyName = "."+newPrettyName;
		if(isTrashed()) newPrettyName = newPrettyName + FilenameUtils.getExtension(getRawName());

		writeName(newPrettyName);
	}



	private void writeName(String newName) {
		if(parentUID == null) return;

		Thread renameThread = new Thread(() -> {
			try {
				//If parent is a link, we need the target dir or target parent
				UUID dirUID = LinkCache.getInstance().getLinkDir(parentUID);

				DirUtilities.renameFile(fileUID, dirUID, newName);
			} catch (IOException e) {
				//Just don't rename
			}
		});
		renameThread.start();
	}


	//-----------------------------------------------

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
		private Type type;

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
		public Builder setRawName(@NonNull String rawName) {
			this.name = rawName;
			return this;
		}
		public Builder setFilePath(@NonNull Path filePath) {
			this.filePath = filePath;
			return this;
		}
		public Builder setType(@NonNull Type type) {
			this.type = type;
			return this;
		}

		public ListItem build() {
			return new ListItem(fileUID, parentUID, isDir, isLink, name, filePath, type);
		}
	}
}