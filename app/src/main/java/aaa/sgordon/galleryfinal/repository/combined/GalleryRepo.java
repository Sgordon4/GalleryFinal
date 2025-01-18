package aaa.sgordon.galleryfinal.repository.combined;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import aaa.sgordon.galleryfinal.repository.local.file.LFile;
import aaa.sgordon.galleryfinal.utilities.MyApplication;
import aaa.sgordon.galleryfinal.repository.combined.combinedtypes.ContentsNotFoundException;
import aaa.sgordon.galleryfinal.repository.combined.combinedtypes.GAccount;
import aaa.sgordon.galleryfinal.repository.combined.combinedtypes.GFile;
import aaa.sgordon.galleryfinal.repository.combined.jobs.domain_movement.DomainAPI;
import aaa.sgordon.galleryfinal.repository.combined.jobs.sync.SyncHandler;
import aaa.sgordon.galleryfinal.repository.combined.jobs.writestalling.WriteStallWorkers;
import aaa.sgordon.galleryfinal.repository.combined.jobs.writestalling.WriteStalling;
import aaa.sgordon.galleryfinal.repository.local.LocalRepo;
import aaa.sgordon.galleryfinal.repository.local.account.LAccount;
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

	private DomainAPI domainAPI;
	private SyncHandler syncHandler;

	private WriteStalling writeStalling;
	private WriteStallWorkers writeStallWorkers;

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

		/*
		//Configure the WorkManager for this application to use fewer threads
		//Note: MUST disable the default initializer in App Manifest for this to take effect
		//Note: This must be done in onCreate of the Application
		Configuration config = new Configuration.Builder()
				.setExecutor(Executors.newFixedThreadPool(10)).build();
		WorkManager.initialize(MyApplication.getAppContext(), config);
		*/
	}
	public void initialize() {
		observers = new GFileUpdateObservers();

		domainAPI = DomainAPI.getInstance();
		syncHandler = SyncHandler.getInstance();

		writeStalling = WriteStalling.getInstance();
		writeStallWorkers = WriteStallWorkers.getInstance();


		observers.attachListeners(localRepo, serverRepo);

		writeStallWorkers.startJobs();
		syncHandler.catchUpOnSyncing();
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

	//TODO Doesn't actually work
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


	public void putAccountProps(@NonNull GAccount gAccount) {
		throw new RuntimeException("Stub!");
	}

	protected void putAccountPropsLocal(@NonNull GAccount gAccount) {
		LAccount account = new Gson().fromJson(gAccount.toJson(), LAccount.class);
		localRepo.putAccountProps(account);
	}

	protected void putAccountPropsServer(@NonNull GAccount gAccount) throws ConnectException {
		SAccount account = new Gson().fromJson(gAccount.toJson(), SAccount.class);
		serverRepo.putAccountProps(account);
	}


	//---------------------------------------------------------------------------------------------
	// File Props
	//---------------------------------------------------------------------------------------------



	public long requestWriteLock(UUID fileUID) {
		return writeStalling.requestWriteLock(fileUID);
	}
	public void releaseWriteLock(UUID fileUID, long lockStamp) {
		writeStalling.releaseWriteLock(fileUID, lockStamp);
	}


	//Actually writes to a temp file, which needs to be persisted later
	//Optimistically assumes the file exists in one of the repos. If not, this temp file will be deleted when we try to persist.
	public String writeFile(UUID fileUID, byte[] contents, String lastHash, long lockStamp) throws IOException {
		if(!writeStalling.isStampValid(fileUID, lockStamp))
			throw new IllegalStateException("Invalid lock stamp! FileUID='"+fileUID+"'");

		//Write to the stall file
		String fileHash = writeStalling.write(fileUID, contents, lastHash);

		//Launch a worker to persist the file
		writeStallWorkers.launch(fileUID);
		return fileHash;
	}
	public void writeFile(UUID fileUID, Uri contents, String lastHash, long lockStamp) {
		throw new RuntimeException("Stub!");
	}



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

	public GFile createFilePropsLocal(@NonNull GFile gFile) throws IllegalStateException, ContentsNotFoundException {
		return putFilePropsLocal(gFile, null, null);
	}
	public GFile putFilePropsLocal(@NonNull GFile gFile, @Nullable String prevFileHash, @Nullable String prevAttrHash) throws ContentsNotFoundException {
		LFile file = gFile.toLocalFile();
		try {
			LFile retFile = localRepo.putFileProps(file, prevFileHash, prevAttrHash);
			return GFile.fromLocalFile(retFile);
		} catch (IllegalStateException e) {
			throw new IllegalStateException("PrevHashes do not match in putFileProps", e);
		} catch (ContentsNotFoundException e) {
			throw new ContentsNotFoundException("Cannot put props, Local is missing content!", e);
		}
	}

	public GFile createFilePropsServer(@NonNull GFile gFile) throws IllegalStateException, ContentsNotFoundException, ConnectException {
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


	public boolean doesContentExistLocal(@NonNull String name) {
		try {
			localRepo.getContentProps(name);
			return true;
		} catch (ContentsNotFoundException e) {
			return false;
		}
	}
	public boolean doesContentExistServer(@NonNull String name) throws ConnectException {
		try {
			serverRepo.getContentProps(name);
			return true;
		} catch (ContentsNotFoundException e) {
			return false;
		}
	}

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
	public int putContentsServer(@NonNull String name, @NonNull File source) throws FileNotFoundException, ConnectException {
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
	// Domain Movements
	//---------------------------------------------------------------------------------------------


	public void queueCopyFileToLocal(@NonNull UUID fileuid) {
		domainAPI.enqueue(fileuid, DomainAPI.COPY_TO_LOCAL);
	}
	public void queueCopyFileToServer(@NonNull UUID fileuid) {
		domainAPI.enqueue(fileuid, DomainAPI.COPY_TO_SERVER);
	}


	public void queueRemoveFileFromLocal(@NonNull UUID fileuid) {
		domainAPI.enqueue(fileuid, DomainAPI.REMOVE_FROM_LOCAL);
	}
	public void queueRemoveFileFromServer(@NonNull UUID fileuid) {
		domainAPI.enqueue(fileuid, DomainAPI.REMOVE_FROM_SERVER);
	}
}



















