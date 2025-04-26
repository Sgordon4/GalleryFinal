package aaa.sgordon.galleryfinal.viewpager.viewpages;

import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.google.gson.JsonObject;

import org.apache.commons.io.FilenameUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ConnectException;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import aaa.sgordon.galleryfinal.repository.gallery.ListItem;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridAPI;
import aaa.sgordon.galleryfinal.repository.hybrid.database.HZone;
import aaa.sgordon.galleryfinal.repository.hybrid.types.HFile;
import aaa.sgordon.galleryfinal.repository.local.LocalRepo;
import aaa.sgordon.galleryfinal.utilities.DirUtilities;
import aaa.sgordon.galleryfinal.utilities.MyApplication;

public class ViewPageViewModel extends ViewModel {
	private final static String TAG = "Gal.VP.VM";
	public final UUID fileUID;
	public final UUID parentUID;
	public final Path pathFromRoot;

	public String fileName;
	public String fileExtension;
	public String description;

	public HFile fileProps;
	public HZone zoning;

	private final Runnable descriptionChangeRunnable;
	private final Runnable fileNameChangeRunnable;

	private final ScheduledExecutorService scheduler;
	private ScheduledFuture<?> scheduledWrite = null;
	private ScheduledFuture<?> scheduledFileNameChange = null;


	public ViewPageViewModel(ListItem listItem) {
		this.fileUID = listItem.fileUID;
		this.pathFromRoot = listItem.pathFromRoot;
		this.parentUID = listItem.parentUID;

		String name = listItem.getPrettyName();
		this.fileName = FilenameUtils.getBaseName(name);
		this.fileExtension = FilenameUtils.getExtension(name);
		if(!this.fileExtension.isEmpty()) this.fileExtension = "."+this.fileExtension;


		scheduler = Executors.newScheduledThreadPool(2);
		descriptionChangeRunnable = () -> {
			try {
				writeDescription();
			}
			catch (ConnectException e) {
				Toast.makeText(MyApplication.getAppContext(), "No connection, could not save file! Retrying in 5 seconds.", Toast.LENGTH_SHORT).show();
				//Try to write again in a few seconds
				persistDescription();
			}
			catch (IOException e) {
				Toast.makeText(MyApplication.getAppContext(), "Could not save file! Retrying in 5 seconds.", Toast.LENGTH_SHORT).show();
				//Try to write again in a few seconds
				persistDescription();
			}
		};
		fileNameChangeRunnable = () -> {
			try {
				renameFile();
			}
			catch (IOException e) {
				Toast.makeText(MyApplication.getAppContext(), "Could not rename file! Retrying in 5 seconds.", Toast.LENGTH_SHORT).show();
				//Try to write again in a few seconds
				persistFileName();
			}
		};

		new Thread(this::refreshData).start();
	}


	//---------------------------------------------------------------------------------------------

	public interface DataRefreshedListener {
		void onDataReady(HFile fileProps, HZone zoning);
		void onConnectException();
		void onFileNotFoundException();
	}
	private DataRefreshedListener dataRefreshedListener;
	public void setDataReadyListener(DataRefreshedListener listener) {
		dataRefreshedListener = listener;

		if(fileProps != null)
			listener.onDataReady(fileProps, zoning);
	}

	public void refreshData() {
		try {
			HybridAPI hAPI = HybridAPI.getInstance();

			fileProps = hAPI.getFileProps(fileUID);
			zoning = hAPI.getZoningInfo(fileUID);

			if(fileProps.userattr.has("description"))
				description = fileProps.userattr.get("description").getAsString();

			if(dataRefreshedListener != null)
				dataRefreshedListener.onDataReady(fileProps, zoning);
		}
		catch (FileNotFoundException e) {
			if(dataRefreshedListener != null)
				dataRefreshedListener.onFileNotFoundException();
		}
		catch (ConnectException e) {
			if(dataRefreshedListener != null)
				dataRefreshedListener.onConnectException();
		}
	}

	//---------------------------------------------------------------------------------------------

	public void persistFileName() {
		if(scheduledFileNameChange != null && !scheduledFileNameChange.isDone()) return;

		scheduledFileNameChange = scheduler.schedule(fileNameChangeRunnable, 5, java.util.concurrent.TimeUnit.SECONDS);
	}
	public void persistFileNameImmediately() {
		//If there is no scheduled write, there is no data to persist. Just return.
		if(scheduledFileNameChange == null || scheduledFileNameChange.isDone()) return;

		//We're writing here and now, so cancel the runnable
		scheduledFileNameChange.cancel(false);


		scheduler.execute(() -> {
			try {
				renameFile();
			}
			catch (IOException e) {
				Toast.makeText(MyApplication.getAppContext(), "Failed to rename file!", Toast.LENGTH_SHORT).show();

				Log.e(TAG, "IOException when renaming fileUID='"+fileUID+"'");
				e.printStackTrace();
			}
		});
	}


	private void renameFile() throws IOException {
		Log.v(TAG, "Writing title for fileUID='"+fileUID+"'");
		String fullFileName = fileName + fileExtension;
		DirUtilities.renameFile(fileUID, parentUID, fullFileName);
	}


	//---------------------------------------------------------------------------------------------

	public void persistDescription() {
		if(scheduledWrite != null && !scheduledWrite.isDone()) return;

		scheduledWrite = scheduler.schedule(descriptionChangeRunnable, 5, java.util.concurrent.TimeUnit.SECONDS);
	}

	public void persistDescriptionImmediately() {
		//If there is no scheduled write, there is no data to persist. Just return.
		if(scheduledWrite == null || scheduledWrite.isDone()) return;

		//We're writing here and now, so cancel the runnable
		scheduledWrite.cancel(false);


		scheduler.execute(() -> {
			try {
				writeDescription();
			}
			catch (IOException e) {
				Toast.makeText(MyApplication.getAppContext(), "Could not save description!", Toast.LENGTH_SHORT).show();

				Log.e(TAG, "IOException when persisting description for fileUID='"+fileUID+"'");
				e.printStackTrace();
			}
		});
	}


	//---------------------------------------------------------------------------------------------

	private void writeDescription() throws IOException {
		Log.v(TAG, "Writing description for fileUID='"+fileUID+"'");
		HybridAPI hAPI = HybridAPI.getInstance();

		try {
			hAPI.lockLocal(fileUID);
			HFile fileProps = hAPI.getFileProps(fileUID);

			//Ideally, we would compare checksums, and if hAPI has updated attributes we would merge.
			//Counterpoint, I hate merging a lot.
			//TODO This will write if the file was just deleted. We need to figure out what we're doing with that delete bit
			JsonObject attrs = fileProps.userattr;
			attrs.addProperty("description", description);
			hAPI.setAttributes(fileUID, attrs, fileProps.attrhash);

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
				localRepo.putFileProps(fileProps.toLocalFile(), "", "");
			}
			catch (IllegalStateException ignored) {
				//This only gets thrown if the passed checksums don't match the current file's,
				// so this means the file is now in Local somehow or we can connect. Technically a success.
			}
			writeDescription();
		}
		finally {
			hAPI.unlockLocal(fileUID);
		}
	}



//=================================================================================================
//=================================================================================================

	public static class Factory implements ViewModelProvider.Factory {
		private final ListItem listItem;

		public Factory(ListItem listItem) {
			this.listItem = listItem;
		}

		@NonNull
		@Override
		public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
			if (modelClass.isAssignableFrom(ViewPageViewModel.class)) {
				return (T) new ViewPageViewModel(listItem);
			}
			throw new IllegalArgumentException("Unknown ViewModel class");
		}
	}
}
