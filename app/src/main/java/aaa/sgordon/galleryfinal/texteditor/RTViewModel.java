package aaa.sgordon.galleryfinal.texteditor;

import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.apache.commons.io.FilenameUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ConnectException;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import aaa.sgordon.galleryfinal.repository.hybrid.HybridAPI;
import aaa.sgordon.galleryfinal.repository.hybrid.types.HFile;
import aaa.sgordon.galleryfinal.repository.local.LocalRepo;
import aaa.sgordon.galleryfinal.utilities.DirUtilities;
import aaa.sgordon.galleryfinal.utilities.MyApplication;

public class RTViewModel extends ViewModel {
	private final static String TAG = "Gal.RT.VM";
	public final UUID fileUID;
	public final UUID dirUID;

	public String content;
	public String fileName;
	public String fileExtension;

	public HFile lastProps;

	private final Runnable writeRunnable;
	private final Runnable titleChangeRunnable;

	private final ScheduledExecutorService scheduler;
	private ScheduledFuture<?> scheduledWrite = null;
	private ScheduledFuture<?> scheduledTitleChange = null;


	public RTViewModel(String content, HFile fileProps, String fileName, UUID dirUID) {
		this.content = content;
		this.fileUID = fileProps.fileuid;
		this.lastProps = fileProps;
		this.dirUID = dirUID;

		this.fileName = FilenameUtils.getBaseName(fileName);
		this.fileExtension = FilenameUtils.getExtension(fileName);


		scheduler = Executors.newScheduledThreadPool(2);
		writeRunnable = () -> {
			try {
				writeContents();
			}
			catch (ConnectException e) {
				Toast.makeText(MyApplication.getAppContext(), "No connection, could not save file! Retrying in 5 seconds.", Toast.LENGTH_SHORT).show();
				//Try to write again in a few seconds
				persistContents();
			}
			catch (IOException e) {
				Toast.makeText(MyApplication.getAppContext(), "Could not save file! Retrying in 5 seconds.", Toast.LENGTH_SHORT).show();
				//Try to write again in a few seconds
				persistContents();
			}
		};
		titleChangeRunnable = () -> {
			try {
				renameFile();
			}
			catch (IOException e) {
				Toast.makeText(MyApplication.getAppContext(), "Could not rename file! Retrying in 5 seconds.", Toast.LENGTH_SHORT).show();
				//Try to write again in a few seconds
				persistTitle();
			}
		};
	}


	//---------------------------------------------------------------------------------------------

	public void persistTitle() {
		if(scheduledTitleChange != null && !scheduledTitleChange.isDone()) return;

		scheduledTitleChange = scheduler.schedule(titleChangeRunnable, 5, java.util.concurrent.TimeUnit.SECONDS);
	}
	public void persistTitleImmediately() {
		//If there is no scheduled write, there is no data to persist. Just return.
		if(scheduledTitleChange == null || scheduledTitleChange.isDone()) return;

		//We're writing here and now, so cancel the runnable
		scheduledTitleChange.cancel(false);


		scheduler.execute(() -> {
			try {
				renameFile();
			}
			catch (IOException e) {
				Toast.makeText(MyApplication.getAppContext(), "Failed to rename file!", Toast.LENGTH_SHORT).show();

				Log.e(TAG, "It's a little joever, IOException when renaming fileUID='"+fileUID+"'");
				e.printStackTrace();
			}
		});
	}


	private void renameFile() throws IOException {
		String fullFileName = fileName + "." + fileExtension;
		DirUtilities.renameFile(fileUID, dirUID, fullFileName);
	}


	//---------------------------------------------------------------------------------------------

	public void persistContents() {
		if(scheduledWrite != null && !scheduledWrite.isDone()) return;

		scheduledWrite = scheduler.schedule(writeRunnable, 5, java.util.concurrent.TimeUnit.SECONDS);
	}

	public void persistContentsImmediately() {
		//If there is no scheduled write, there is no data to persist. Just return.
		if(scheduledWrite == null || scheduledWrite.isDone()) return;

		//We're writing here and now, so cancel the runnable
		scheduledWrite.cancel(false);


		scheduler.execute(() -> {
			try {
				writeContents();
			}
			catch (IOException e) {
				Toast.makeText(MyApplication.getAppContext(), "Could not save file!", Toast.LENGTH_SHORT).show();

				Log.e(TAG, "It's so joever, IOException when persisting fileUID='"+fileUID+"'");
				e.printStackTrace();
			}
		});
	}


	//---------------------------------------------------------------------------------------------

	private void writeContents() throws IOException {
		Log.v(TAG, "Writing rich text contents for fileUID='"+fileUID+"'");
		HybridAPI hAPI = HybridAPI.getInstance();

		try {
			hAPI.lockLocal(fileUID);
			HFile fileProps = hAPI.getFileProps(fileUID);

			//Ideally, we would compare checksums, and if hAPI has new content we would merge.
			//Counterpoint, I hate merging a lot.
			//TODO This will write if the file was just deleted. We need to figure out what we're doing with that delete bit
			hAPI.writeFile(fileUID, content.getBytes(), fileProps.checksum);

			//We could re-fetch props to update last props here, but (read below) those are only really used on first write
		}
		catch (FileNotFoundException e) {
			Toast.makeText(MyApplication.getAppContext(), "File no longer exists!", Toast.LENGTH_SHORT).show();
			//If the file doesn't exist anymore, it was just deleted, and we should just ignore the write.
			//Leave the fragment up though because I'm lazy, and also in case the user wants to copy anything last minute for some reason.
		}
		catch (ConnectException e) {
			Log.w(TAG, "File is not on local and we could not reach the server! Panic writing props to local! FileUID='"+fileUID+"'");
			try {
				//We NEED to write or the data will be lost if the user leaves.
				//If we can't connect, just take the hit and panic write the starting properties to local, then try to write again
				//This will only ever occur on the first write after launching the fragment,
				// or if the file is deleted and then we can't connect. I only really care about the first one.
				LocalRepo localRepo = LocalRepo.getInstance();
				localRepo.putFileProps(lastProps.toLocalFile(), "", "");
			}
			catch (IllegalStateException ignored) {
				//This only gets thrown if the passed checksums don't match the current file's,
				// so this means the file is now in Local somehow or we can connect. Technically a success.
			}
			writeContents();
		}
		finally {
			hAPI.unlockLocal(fileUID);
		}
	}



	@Override
	protected void onCleared() {
		scheduler.shutdown();
		super.onCleared();
	}


//=================================================================================================
//=================================================================================================

	public static class Factory implements ViewModelProvider.Factory {
		private final HFile fileProps;
		private final String content;
		private final UUID dirUID;
		private final String filename;

		public Factory(String content, HFile fileProps, String filename, UUID dirUID) {
			this.content = content;
			this.fileProps = fileProps;
			this.dirUID = dirUID;
			this.filename = filename;
		}

		@NonNull
		@Override
		public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
			if (modelClass.isAssignableFrom(RTViewModel.class)) {
				return (T) new RTViewModel(content, fileProps, filename, dirUID);
			}
			throw new IllegalArgumentException("Unknown ViewModel class");
		}
	}
}
