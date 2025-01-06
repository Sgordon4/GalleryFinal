package aaa.sgordon.galleryfinal.repository.combined.domain;

import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;

import aaa.sgordon.galleryfinal.utilities.MyApplication;
import aaa.sgordon.galleryfinal.repository.combined.ContentsNotFoundException;
import aaa.sgordon.galleryfinal.repository.combined.PersistedMapQueue;
import aaa.sgordon.galleryfinal.repository.combined.combinedtypes.GFile;
import aaa.sgordon.galleryfinal.repository.local.LocalRepo;
import aaa.sgordon.galleryfinal.repository.local.content.LContent;
import aaa.sgordon.galleryfinal.repository.local.file.LFile;
import aaa.sgordon.galleryfinal.repository.server.ServerRepo;
import aaa.sgordon.galleryfinal.repository.server.servertypes.SContent;
import aaa.sgordon.galleryfinal.repository.server.servertypes.SFile;

import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;


public class DomainAPI {
	private static final String TAG = "Gal.DomAPI";
	private final LocalRepo localRepo;
	private final ServerRepo serverRepo;

	private final PersistedMapQueue<UUID, Integer> pendingOperations;


	public static DomainAPI getInstance() {
		return SingletonHelper.INSTANCE;
	}
	private static class SingletonHelper {
		private static final DomainAPI INSTANCE = new DomainAPI();
	}
	private DomainAPI() {
		localRepo = LocalRepo.getInstance();
		serverRepo = ServerRepo.getInstance();


		String appDataDir = MyApplication.getAppContext().getApplicationInfo().dataDir;
		Path persistLocation = Paths.get(appDataDir, "queues", "domainOpQueue.txt");

		pendingOperations = new PersistedMapQueue<UUID, Integer>(persistLocation) {
			@Override
			public UUID parseKey(String keyString) { return UUID.fromString(keyString); }
			@Override
			public Integer parseVal(String valString) { return Integer.parseInt(valString); }
		};
	}
	


	public static final int COPY_TO_LOCAL = 1;
	public static final int REMOVE_FROM_LOCAL = 2;
	public static final int COPY_TO_SERVER = 4;
	public static final int REMOVE_FROM_SERVER = 8;
	public static final int LOCAL_MASK = COPY_TO_LOCAL | REMOVE_FROM_LOCAL;
	public static final int SERVER_MASK = COPY_TO_SERVER | REMOVE_FROM_SERVER;
	public static final int MASK = LOCAL_MASK | SERVER_MASK;




	//Launches N workers to execute the next N operations (if available)
	//Returns the number of operations launched
	public int doSomething(int times) {
		WorkManager workManager = WorkManager.getInstance(MyApplication.getAppContext());

		//Get the next N fileUID and operation pairs
		List<Map.Entry<UUID, Integer>> nextOperations = pendingOperations.pop(times);
		System.out.println("DomainAPI doing something "+nextOperations.size()+" times...");

		for(Map.Entry<UUID, Integer> entry : nextOperations) {
			UUID fileUID = entry.getKey();
			Integer operationsMask = entry.getValue();

			System.out.println("Launching DomainOp: "+fileUID+"::"+operationsMask);

			if(fileUID == null) {
				Log.w(TAG, "Null file ID in queue!");
				continue;
			}

			//If there are no operations for the file, it should be skipped
			if(operationsMask == null)
				continue;


			//Launch the worker to perform the operation
			WorkRequest request = buildWorker(fileUID, operationsMask).build();
			workManager.enqueue(request);
		}

		return nextOperations.size();
	}


	public OneTimeWorkRequest.Builder buildWorker(@NonNull UUID fileuid, @NonNull Integer operationsMask) {
		Data.Builder data = new Data.Builder();
		data.putString("FILEUID", fileuid.toString());
		data.putString("OPERATIONS", operationsMask.toString());

		return new OneTimeWorkRequest.Builder(DomainOpWorker.class)
				.setInputData(data.build())
				.addTag(fileuid.toString())
				.addTag(operationsMask.toString());
	}


	//---------------------------------------------------------------------------------------------

	public void enqueue(@NonNull UUID fileuid, @NonNull Integer... newOperations) throws InterruptedException {
		//Get any current operations for this file
		Integer operationsMask = pendingOperations.getOrDefault(fileuid, 0);

		//Add all operations to the existing mask
		for(Integer operation : newOperations) {
			operationsMask |= operation;
		}


		//Look for any conflicting operations and, if found, remove both.
		//E.g. This operations mask now contains both COPY_TO_LOCAL and REMOVE_FROM_LOCAL.
		//Copying a file to local just to remove it would be redundant, so we can safely remove both.

		if((operationsMask & LOCAL_MASK) == LOCAL_MASK)
			operationsMask &= ~(LOCAL_MASK);
		else if((operationsMask & SERVER_MASK) == SERVER_MASK)
			operationsMask &= ~(SERVER_MASK);


		//Queue the updated operations
		pendingOperations.enqueue(fileuid, operationsMask);
	}


	//---------------------------------------------------

	/** @return True if operations were removed, false if there were no operations to remove */
	public boolean dequeue(@NonNull UUID fileuid, @NonNull Integer... operations) {
		Integer operationsMask = pendingOperations.get(fileuid);
		if(operationsMask == null) return false;

		//Remove all specified operations
		for(Integer operation : operations)
			operationsMask &= ~operation;

		//If there are no operations left to perform, remove the file from the mapping
		if(operationsMask == 0)
			pendingOperations.dequeue(fileuid);

		//Otherwise, update the operations mask
		pendingOperations.enqueue(fileuid, operationsMask);
		return true;
	}
	/**  @return True if operations were removed, false if there were no operations to remove */
	public boolean dequeue(@NonNull UUID fileuid) {
		if(!pendingOperations.containsKey(fileuid)) return false;

		pendingOperations.dequeue(fileuid);
		return true;
	}


	public void clearQueuedItems() {
		pendingOperations.clear();
	}



	//=============================================================================================
	// API
	//=============================================================================================

	public SFile createFileOnServer(@NonNull LFile file) throws IllegalStateException, IOException {
		return copyFileToServer(file, "null", "null");
	}
	public SFile copyFileToServer(@NonNull LFile file, String lastServerFileHash, String lastServerAttrHash)
			throws IllegalStateException, IOException {
		//Get the hash of the file
		String fileHash = file.filehash;

		//If it exists, copy that content to the server
		if(fileHash != null)
			copyContentToServer(fileHash);


		//Now that the content is uploaded, create/update the file metadata
		SFile fileProps = GFile.fromLocalFile(file).toServerFile();

		//And attempt to put the changes to the server
		try {
			fileProps = serverRepo.putFileProps(fileProps, lastServerFileHash, lastServerAttrHash);
		} catch (ContentsNotFoundException e) {
			Log.e(TAG, "copyFileToServer() failed! Content failed to copy!");
			throw new RuntimeException(e);
		}


		//If this method was called with "null" hashes (not actually null),
		// it was used to try to create a file for the first time
		if(Objects.equals(lastServerFileHash, "null") && Objects.equals(lastServerAttrHash, "null")) {
			//If no illegalStateException was thrown, that means the file was just CREATED in server.
			//It is now the latest sync point
			localRepo.putLastSyncedData(GFile.fromServerFile(fileProps).toLocalFile());
		}

		return fileProps;
	}


	public LFile createFileOnLocal(@NonNull SFile file) throws IllegalStateException, IOException {
		return copyFileToLocal(file, "null", "null");
	}
	public LFile copyFileToLocal(@NonNull SFile file, String lastLocalFileHash, String lastLocalAttrHash)
			throws IllegalStateException, IOException {
		//Get the hash of the file
		String fileHash = file.filehash;

		//If it exists, copy that content to local
		if(fileHash != null)
			copyContentsToLocal(fileHash);


		//Now that the content is downloaded, create/update the file metadata
		LFile fileProps = GFile.fromServerFile(file).toLocalFile();

		//And attempt to put the changes to local
		try {
			fileProps = localRepo.putFileProps(fileProps, lastLocalFileHash, lastLocalAttrHash);
		} catch (ContentsNotFoundException e) {
			Log.e(TAG, "copyFileToLocal() failed! Content failed to copy!");
			throw new RuntimeException(e);
		}


		//If this method was called with "null" hashes (not actually null),
		// it was used to try to create a file for the first time
		if(Objects.equals(lastLocalFileHash, "null") && Objects.equals(lastLocalAttrHash, "null")) {
			//If no illegalStateException was thrown, that means the file was just CREATED in server.
			//It is now the latest sync point
			localRepo.putLastSyncedData(fileProps);
		}

		return fileProps;
	}


	//---------------------------------------------------


	@Nullable
	public LContent copyContentsToLocal(@Nullable String fileHash) throws IOException {
		if(fileHash == null) return null;

		try {
			//Check if the content already exists on local
			return localRepo.contentHandler.getProps(fileHash);
		}
		catch (ContentsNotFoundException e) {
			//If it does not, download it
			Uri serverContentUri = serverRepo.getContentDownloadUri(fileHash);
			return localRepo.writeContents(fileHash, serverContentUri);
		}
	}

	public SContent copyContentToServer(@Nullable String fileHash) throws IOException {
		if(fileHash == null) return null;

		try {
			//Check if the content already exists on the server
			return serverRepo.contentConn.getProps(fileHash);
		}
		catch (ContentsNotFoundException e) {
			//If it does not, upload it
			Uri localContentUri = localRepo.getContentUri(fileHash);
			File localFile = new File(localContentUri.toString());

			//Returns filesize
			return serverRepo.uploadData(fileHash, localFile);
		}
	}


	//---------------------------------------------------


	public boolean removeFileFromLocal(@NonNull UUID fileuid) {
		localRepo.deleteFileProps(fileuid);
		localRepo.deleteLastSyncedData(fileuid);
		return true;
	}

	public boolean removeFileFromServer(@NonNull UUID fileuid) throws ConnectException {
		serverRepo.deleteFileProps(fileuid);
		localRepo.deleteLastSyncedData(fileuid);
		return true;
	}
}
