package aaa.sgordon.galleryfinal.gallery.viewholders;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.apache.commons.io.FilenameUtils;

import java.util.UUID;

public class BaseViewHolder extends RecyclerView.ViewHolder {
	protected UUID fileUID;
	protected String fileName;

	public BaseViewHolder(@NonNull View itemView) {
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
