package aaa.sgordon.galleryfinal.gallery.viewholders;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.UUID;

public class DirViewHolder extends RecyclerView.ViewHolder {
	private UUID fileUID;
	private String fileName;

	public DirViewHolder(@NonNull View itemView) {
		super(itemView);
	}

	public UUID getFileUID() {
		return fileUID;
	}
	public String getFileName() {
		return fileName;
	}

	public void bind(UUID fileUID, String fileName) {
		this.fileUID = fileUID;
		this.fileName = fileName;
	}
}
