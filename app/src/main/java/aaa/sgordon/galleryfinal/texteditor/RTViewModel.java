package aaa.sgordon.galleryfinal.texteditor;

import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ConnectException;
import java.util.UUID;

import aaa.sgordon.galleryfinal.repository.hybrid.HybridAPI;
import aaa.sgordon.galleryfinal.repository.hybrid.types.HFile;
import aaa.sgordon.galleryfinal.utilities.MyApplication;

public class RTViewModel extends ViewModel {
	private final static String TAG = "Gal.RT.VM";
	private final UUID fileUID;
	private String content;
	private String lastWriteChecksum;

	private Runnable writeRunnable;


	public RTViewModel(UUID fileUID) {
		this.fileUID = fileUID;
	}



	public void persistContents() {
		if(writeRunnable != null) return;

		writeRunnable = () -> {
			HybridAPI hAPI = HybridAPI.getInstance();

			try {
				hAPI.lockLocal(fileUID);
				HFile fileProps = hAPI.getFileProps(fileUID);

				//Ideally, we would compare checksums, and if hAPI has new content we would merge.
				//Counterpoint, I don't care at all.
				hAPI.writeFile(fileUID, content.getBytes(), fileProps.checksum);
			} catch (IOException e) {
				Toast.makeText(MyApplication.getAppContext(), "Could not save file!", Toast.LENGTH_SHORT).show();

				//Post another write runnable

			} finally {
				hAPI.unlockLocal(fileUID);
			}
		};
	}





//=================================================================================================
//=================================================================================================

	public static class Factory implements ViewModelProvider.Factory {
		private final UUID fileUID;
		public Factory(UUID fileUID) {
			this.fileUID = fileUID;
		}

		@NonNull
		@Override
		public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
			if (modelClass.isAssignableFrom(RTViewModel.class)) {
				return (T) new RTViewModel(fileUID);
			}
			throw new IllegalArgumentException("Unknown ViewModel class");
		}
	}
}
