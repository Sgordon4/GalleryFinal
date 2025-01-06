package aaa.sgordon.galleryfinal.repository.combined;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import aaa.sgordon.galleryfinal.MyApplication;
import aaa.sgordon.galleryfinal.repository.combined.combinedtypes.GAccount;
import aaa.sgordon.galleryfinal.repository.combined.combinedtypes.GFile;
import aaa.sgordon.galleryfinal.repository.combined.domain.DomainAPI;
import aaa.sgordon.galleryfinal.repository.combined.sync.SyncHandler;
import aaa.sgordon.galleryfinal.repository.local.LocalRepo;
import aaa.sgordon.galleryfinal.repository.local.account.LAccount;
import aaa.sgordon.galleryfinal.repository.local.file.LFile;
import aaa.sgordon.galleryfinal.repository.server.ServerRepo;
import aaa.sgordon.galleryfinal.repository.server.connectors.ContentConnector;
import aaa.sgordon.galleryfinal.repository.server.servertypes.SAccount;
import aaa.sgordon.galleryfinal.repository.server.servertypes.SFile;
import com.google.gson.Gson;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ConnectException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

public class GalleryRepo {

	private static final String TAG = "Gal.GRepo";

	private final LocalRepo localRepo;
	private final ServerRepo serverRepo;

	private final DomainAPI domainAPI;
	private final SyncHandler syncHandler;

	private GFileUpdateObservers observers;



	public static GalleryRepo getInstance() {
		return SingletonHelper.INSTANCE;
	}
	private static class SingletonHelper {
		private static final GalleryRepo INSTANCE = new GalleryRepo();
	}
	private GalleryRepo() {
		localRepo = LocalRepo.getInstance();
		serverRepo = ServerRepo.getInstance();

		domainAPI = DomainAPI.getInstance();
		syncHandler = SyncHandler.getInstance();

		observers = new GFileUpdateObservers();
	}

	public void initializeSyncing() {
		syncHandler.catchUpOnSyncing();
		observers.attachListeners(localRepo, serverRepo);
	}

	//---------------------------------------------------------------------------------------------

	public void addObserver(GFileUpdateObservers.GFileObservable observer) {
		observers.addObserver(observer);
	}
	public void removeObserver(GFileUpdateObservers.GFileObservable observer) {
		observers.removeObserver(observer);
	}

	public void notifyObservers(GFile file) {
		observers.notifyObservers(file);
	}

	//TODO Use this with DomainAPI and SyncHandler's doSomething() methods. Also fix this up, doesn't work.
	public boolean doesDeviceHaveInternet() {
		ConnectivityManager connectivityManager = (ConnectivityManager)
				MyApplication.getAppContext().getSystemService(Context.CONNECTIVITY_SERVICE);

		Network nw = connectivityManager.getActiveNetwork();
		if(nw == null) return false;

		NetworkCapabilities cap = connectivityManager.getNetworkCapabilities(nw);
		return cap != null && (
				cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
				cap.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
				cap.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
				cap.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) );
	}

	//---------------------------------------------------------------------------------------------
	// Account
	//---------------------------------------------------------------------------------------------

	@Nullable
	public GAccount getAccountProps(@NonNull UUID accountuid) throws FileNotFoundException, ConnectException {
		//Try to get the account data from local. If it exists, return that.
		try {
			LAccount local = localRepo.getAccountProps(accountuid);
			return new Gson().fromJson(local.toJson(), GAccount.class);
		}
		catch (FileNotFoundException e) {
			//Do nothing
		}

		//If the account doesn't exist locally, try to get it from the server.
		try {
			SAccount server = serverRepo.getAccountProps(accountuid);
			return new Gson().fromJson(server.toJson(), GAccount.class);
		} catch (FileNotFoundException e) {
			//Do nothing
		}

		//If the file doesn't exist in either, throw an exception
		throw new FileNotFoundException(String.format("Account not found with accountUID='%s'", accountuid));
	}


	public boolean putAccountPropsLocal(@NonNull GAccount gAccount) {
		LAccount account = new Gson().fromJson(gAccount.toJson(), LAccount.class);
		localRepo.putAccountProps(account);
		return true;
	}

	public boolean putAccountPropsServer(@NonNull GAccount gAccount) throws ConnectException {
		SAccount account = new Gson().fromJson(gAccount.toJson(), SAccount.class);
		serverRepo.putAccountProps(account);
		return true;
	}


	//---------------------------------------------------------------------------------------------
	// File Props
	//---------------------------------------------------------------------------------------------


	@NonNull
	public GFile getFileProps(@NonNull UUID fileUID) throws FileNotFoundException, ConnectException {
		//Try to get the file data from local. If it exists, return that.
		try {
			LFile local = localRepo.getFileProps(fileUID);
			return GFile.fromLocalFile(local);
		}
		catch (FileNotFoundException e) {
			//Do nothing
		}

		//If the file doesn't exist locally, try to get it from the server.
		try {
			SFile server = serverRepo.getFileProps(fileUID);
			return GFile.fromServerFile(server);
		} catch (FileNotFoundException e) {
			//Do nothing
		}

		//If the file doesn't exist in either, throw an exception
		throw new FileNotFoundException(String.format("File not found with fileUID='%s'", fileUID));
	}



	//TODO Considering caching the UUIDs of the files on server to help speed this up
	// Or at least for another similar method.

	public boolean isFileLocal(@NonNull UUID fileUID) {
		try {
			localRepo.getFileProps(fileUID);
			return true;
		} catch (FileNotFoundException e) {
			return false;
		}
	}

	public boolean isFileServer(@NonNull UUID fileUID) throws ConnectException {
		try {
			serverRepo.getFileProps(fileUID);
			return true;
		} catch (FileNotFoundException e) {
			return false;
		}
	}



	//TODO Private these, and just give a "putFileProps" option that figures out where to put things and errors if out of date
	// Maybe not private them idk

	public GFile createFilePropsLocal(@NonNull GFile gFile) throws IllegalStateException, ContentsNotFoundException, ConnectException {
		return putFilePropsLocal(gFile, "null", "null");
	}
	public GFile putFilePropsLocal(@NonNull GFile gFile) throws ContentsNotFoundException {
		return putFilePropsLocal(gFile, null, null);
	}
	public GFile putFilePropsLocal(@NonNull GFile gFile, @Nullable String prevFileHash, @Nullable String prevAttrHash) throws ContentsNotFoundException {
		LFile file = gFile.toLocalFile();
		try {
			LFile retFile = localRepo.putFileProps(file, prevFileHash, prevAttrHash);
			return GFile.fromLocalFile(retFile);
		} catch (ContentsNotFoundException e) {
			throw new ContentsNotFoundException("Cannot put props, Local is missing content!", e);
		}
	}

	public GFile createFilePropsServer(@NonNull GFile gFile) throws IllegalStateException, ContentsNotFoundException, ConnectException {
		return putFilePropsServer(gFile, "null", "null");
	}
	public GFile putFilePropsServer(@NonNull GFile gFile) throws IllegalStateException, ContentsNotFoundException, ConnectException {
		return putFilePropsServer(gFile, null, null);
	}
	public GFile putFilePropsServer(@NonNull GFile gFile, @Nullable String prevFileHash, @Nullable String prevAttrHash)
			throws IllegalStateException, ContentsNotFoundException, ConnectException {
		SFile file = gFile.toServerFile();
		try {
			SFile retFile = serverRepo.putFileProps(file, prevFileHash, prevAttrHash);
			return GFile.fromServerFile(retFile);
		} catch (IllegalStateException e) {
			throw new IllegalStateException("PrevHashes do not match in putFileProps", e);
		} catch (ContentsNotFoundException e) {
			throw new ContentsNotFoundException("Cannot put props, Server is missing content!", e);
		} catch (ConnectException e) {
			throw e;
		}
	}



	public void deleteFilePropsLocal(@NonNull UUID fileUID) {
		localRepo.deleteFileProps(fileUID);
	}
	public void deleteFilePropsServer(@NonNull UUID fileUID) throws ConnectException {
		serverRepo.deleteFileProps(fileUID);
	}


	//---------------------------------------------------------------------------------------------
	// File Contents
	//---------------------------------------------------------------------------------------------


	public Uri getContentUri(@NonNull String name) throws ContentsNotFoundException, ConnectException {
		//Try to get the file contents from local. If they exist, return that.
		try {
			return localRepo.getContentUri(name);
		}
		catch (ContentsNotFoundException e) {
			//Do nothing
		}

		//If the contents don't exist locally, try to get it from the server.
		try {
			return serverRepo.getContentDownloadUri(name);
		} catch (ContentsNotFoundException e) {
			//Do nothing
		}

		//If the contents don't exist in either, throw an exception
		throw new ContentsNotFoundException(String.format("Contents not found with name='%s'", name));
	}


	//Note for future me, UnknownHostException can be thrown for web sources, not in this method though
	//System.out.println("BAD SOURCE BAD SOURCE	(or no internet idk)");

	//Helper method
	//WARNING: DOES NOT UPDATE FILE PROPERTIES ON LOCAL
	public int putContentsLocal(@NonNull String name, @NonNull Uri source) throws FileNotFoundException {
		return localRepo.writeContents(name, source).size;
	}
	//Helper method
	//WARNING: DOES NOT UPDATE FILE PROPERTIES ON SERVER
	//WARNING: Source file must be on-disk
	public int putContentsServer(@NonNull String name, @NonNull File source) throws FileNotFoundException {
		return serverRepo.uploadData(name, source).size;
	}


	private String calculateFileHash(@NonNull File file) {
		try (FileInputStream fis = new FileInputStream(file.getPath());
			 DigestInputStream dis = new DigestInputStream(fis, MessageDigest.getInstance("SHA-256"))) {

			byte[] buffer = new byte[8192];
			while (dis.read(buffer) != -1) {
				//Reading through the file updates the digest
			}

			return ContentConnector.bytesToHex( dis.getMessageDigest().digest() );

		} catch (IOException | NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}


	//---------------------------------------------------------------------------------------------
	// Block
	//---------------------------------------------------------------------------------------------

	/*
	@Nullable
	public GContent getBlockProps(@NonNull String blockHash) throws FileNotFoundException, ConnectException {
		//Try to get the block data from local. If it exists, return that.
		try {
			LContent local = localRepo.getBlockProps(blockHash);
			return new Gson().fromJson(local.toJson(), GContent.class);
		}
		catch (FileNotFoundException e) {
			//Do nothing
		}

		//If the block doesn't exist locally, try to get it from the server.
		try {
			SContent server = serverRepo.getBlockProps(blockHash);
			return new Gson().fromJson(server.toJson(), GContent.class);
		} catch (FileNotFoundException e) {
			//Do nothing
		}

		//If the file doesn't exist in either, throw an exception
		throw new FileNotFoundException(String.format("Block not found with blockHash='%s'", blockHash));
	}
	 */

	/*
	public byte[] getBlockContents(@NonNull String blockHash) throws FileNotFoundException, ConnectException {
		//Try to get the block data from local. If it exists, return that.
		try {
			return localRepo.getBlockContents(blockHash);
		} catch (ContentsNotFoundException e) {
			//Do nothing
		}

		//If the block doesn't exist locally, try to get it from the server.
		try {
			return serverRepo.getBlockContents(blockHash);
		} catch (ContentsNotFoundException e) {
			//Do nothing
		}

		//If the block doesn't exist in either, throw an exception
		throw new FileNotFoundException(String.format("Block not found with blockHash='%s'", blockHash));
	}
	 */



	/*
	public boolean isBlockLocal(@NonNull String blockHash) {
		try {
			localRepo.getBlockProps(blockHash);
			return true;
		} catch (FileNotFoundException e) {
			return false;
		}
	}

	public boolean isBlockServer(@NonNull String blockHash) throws ConnectException {
		try {
			serverRepo.getBlockProps(blockHash);
			return true;
		} catch (FileNotFoundException e) {
			return false;
		} catch (ConnectException e) {
			throw e;
		}
	}
	 */



	//---------------------------------------------------------------------------------------------
	// Import/Export
	//---------------------------------------------------------------------------------------------

	/*

	private final String IMPORT_GROUP = "import";
	private final String EXPORT_GROUP = "export";

	//Note: External links are not imported to the system, and should not be handled with this method.
	// Instead, their link file should be created and edited through the file creation/edit modals.


	//Launch a WorkManager to import an external uri to the system.
	public void importFile(@NonNull Uri source, @NonNull UUID accountuid, @NonNull UUID parent) {
		//Compile the information we'll need for the import
		Data.Builder builder = new Data.Builder();
		builder.putString("OPERATION", "IMPORT");
		builder.putString("TARGET_URI", source.toString());
		builder.putString("PARENTUID", parent.toString());
		builder.putString("ACCOUNTUID", accountuid.toString());

		OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(ImportExportWorker.class)
				.setInputData(builder.build())
				.build();

		//Create the work request that will handle the import
		WorkManager workManager = WorkManager.getInstance(MyApplication.getAppContext());

		//Add the work request to the queue so that the imports run in order
		WorkContinuation continuation = workManager.beginUniqueWork(IMPORT_GROUP, ExistingWorkPolicy.APPEND, request);
		continuation.enqueue();
	}

	public void exportFile(@NonNull UUID fileuid, @NonNull UUID parent, @NonNull Uri destination) {
		//Compile the information we'll need for the export
		Data.Builder builder = new Data.Builder();
		builder.putString("OPERATION", "EXPORT");
		builder.putString("TARGET_URI", destination.toString());
		builder.putString("PARENTUID", parent.toString());
		builder.putString("FILEUID", fileuid.toString());

		OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(ImportExportWorker.class)
				.setInputData(builder.build())
				.build();

		//Create the work request that will handle the import
		WorkManager workManager = WorkManager.getInstance(MyApplication.getAppContext());

		//Add the work request to the queue so that the imports run in order
		WorkContinuation continuation = workManager.beginUniqueWork(EXPORT_GROUP, ExistingWorkPolicy.APPEND, request);
		continuation.enqueue();
	}

	 */


	//---------------------------------------------------------------------------------------------
	// Domain Movements
	//---------------------------------------------------------------------------------------------


	public void queueCopyFileToLocal(@NonNull UUID fileuid) {
		try {
			domainAPI.enqueue(fileuid, DomainAPI.COPY_TO_LOCAL);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}
	public void queueCopyFileToServer(@NonNull UUID fileuid) {
		try {
			domainAPI.enqueue(fileuid, DomainAPI.COPY_TO_SERVER);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}


	public void queueRemoveFileFromLocal(@NonNull UUID fileuid) {
		try {
			domainAPI.enqueue(fileuid, DomainAPI.REMOVE_FROM_LOCAL);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}
	public void queueRemoveFileFromServer(@NonNull UUID fileuid) {
		try {
			domainAPI.enqueue(fileuid, DomainAPI.REMOVE_FROM_SERVER);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}
}



















