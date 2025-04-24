package aaa.sgordon.galleryfinal.repository.hybrid;

import android.net.Uri;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Room;

import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.URL;
import java.nio.file.Files;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import aaa.sgordon.galleryfinal.repository.galleryhelpers.MainStorageHandler;
import aaa.sgordon.galleryfinal.repository.hybrid.jobs.Cleanup;
import aaa.sgordon.galleryfinal.repository.hybrid.jobs.sync.ZoningWorker;
import aaa.sgordon.galleryfinal.repository.local.database.LocalDatabase;
import aaa.sgordon.galleryfinal.utilities.MyApplication;
import aaa.sgordon.galleryfinal.utilities.Utilities;
import aaa.sgordon.galleryfinal.repository.hybrid.database.HZone;
import aaa.sgordon.galleryfinal.repository.hybrid.jobs.sync.Sync;
import aaa.sgordon.galleryfinal.repository.hybrid.jobs.sync.SyncWorkers;
import aaa.sgordon.galleryfinal.repository.hybrid.types.HFile;
import aaa.sgordon.galleryfinal.repository.local.LocalRepo;
import aaa.sgordon.galleryfinal.repository.local.types.LContent;
import aaa.sgordon.galleryfinal.repository.local.types.LFile;
import aaa.sgordon.galleryfinal.repository.local.types.LJournal;
import aaa.sgordon.galleryfinal.repository.remote.RemoteRepo;

public class HybridAPI {
	private static final String TAG = "Hyb";

	private LocalRepo localRepo;
	private final RemoteRepo remoteRepo;
	private final HybridListeners listeners;

	public LocalDatabase tempExposedDB;

	private final Sync sync;

	private UUID currentAccount = UUID.fromString("b16fe0ba-df94-4bb6-ad03-aab7e47ca8c3");


	private static HybridAPI instance;
	public static HybridAPI getInstance() {
		if (instance == null)
			throw new IllegalStateException("HybridAPI is not initialized. Call initialize() first.");
		return instance;
	}
	public static void destroyInstance() {
		LocalRepo.destroyInstance();
		instance = null;
	}
	public static synchronized void initialize(@NonNull LocalDatabase database, @NonNull Uri storageDir) {
		if (instance == null) instance = new HybridAPI(database, storageDir);
	}

	private HybridAPI(@NonNull LocalDatabase db, @NonNull Uri storageDir) {
		Log.i(TAG, "Initializing HybridAPI");
		listeners = HybridListeners.getInstance();
		tempExposedDB = db;

		//TODO Change database storage location
		//LocalDatabase db = new LocalDatabase.DBBuilder().newInstance(MyApplication.getAppContext());
		//String storageDir = MyApplication.getAppContext().getApplicationInfo().dataDir;
		//Uri storageDir = MainStorageHandler.getStorageTreeUri(MyApplication.getAppContext());

		LocalRepo.initialize(db, storageDir);

		localRepo = LocalRepo.getInstance();
		remoteRepo = RemoteRepo.getInstance();

		//Stand-in for the login system
		localRepo.setAccount(currentAccount);
		remoteRepo.setAccount(currentAccount);

		Cleanup.cleanOrphanContent(db.getContentDao());

		Sync.initialize(db, MyApplication.getAppContext());
		sync = Sync.getInstance();
	}


	public void lockLocal(@NonNull UUID fileUID) {
		localRepo.lock(fileUID);
	}
	public void unlockLocal(@NonNull UUID fileUID) {
		localRepo.unlock(fileUID);
	}


	public void addListener(HybridListeners.FileChangeListener listener) {
		listeners.addListener(listener);
	}
	public void removeListener(HybridListeners.FileChangeListener listener) {
		listeners.removeListener(listener);
	}


	public UUID getCurrentAccount() {
		return currentAccount;
	}
	public void setAccount(@NonNull UUID accountUID) {
		this.currentAccount = accountUID;
		localRepo.setAccount(currentAccount);
		remoteRepo.setAccount(currentAccount);
	}



	public void startSyncService(@NonNull UUID accountUID) {
		sync.startSyncWatcher(accountUID);
	}
	public void stopSyncService(@NonNull UUID accountUID) {
		sync.stopSyncWatcher(accountUID);
	}


	//---------------------------------------------------------------------------------------------
	// File
	//---------------------------------------------------------------------------------------------

	public HFile getFileProps(@NonNull UUID fileUID) throws FileNotFoundException, ConnectException {
		//Try to grab the file properties from local
		try { return HFile.fromLocalFile( localRepo.getFileProps(fileUID) ); }
		catch (FileNotFoundException ignored) { }

		//Try to grab the file properties from remote
		try { return HFile.fromRemoteFile( remoteRepo.getFileProps(fileUID) ); }
		catch (FileNotFoundException ignored) { }

		throw new FileNotFoundException(String.format("File not found for fileUID='%s'", fileUID));
	}

	//Returns a Uri with the file checksum
	public Pair<Uri, String> getFileContent(@NonNull UUID fileUID) throws FileNotFoundException, ContentsNotFoundException, ConnectException {
		//Grab the file properties, which also makes sure the file exists
		HFile props = getFileProps(fileUID);

		return getFileContent(props);
	}
	public Pair<Uri, String> getFileContent(@NonNull HFile props) throws ContentsNotFoundException, ConnectException {
		//Try to get the file contents from local. If they exist, return that.
		try { return new Pair<>(localRepo.getContentUri(props.checksum), props.checksum); }
		catch (ContentsNotFoundException ignored) { }

		//If the contents don't exist locally, try to get it from the server.
		try { return new Pair<>(remoteRepo.getContentDownloadUri(props.checksum), props.checksum); }
		catch (ContentsNotFoundException ignored) { }

		//If the contents don't exist in either, throw an exception
		throw new ContentsNotFoundException(String.format("Contents not found for fileUID='%s'", props.fileuid));
	}


	//Returns the FileUID of the new file
	public UUID createFile(@NonNull UUID accountUID, boolean isDir, boolean isLink) throws IOException {
		//Note: Locking for this file doesn't really matter, since nothing can know about it yet
		UUID fileUID = UUID.randomUUID();
		LFile newFile = new LFile(fileUID, accountUID);

		newFile.isdir = isDir;
		newFile.islink = isLink;

		//Create blank file contents (will do nothing if they already exist)
		localRepo.writeContents(LFile.defaultChecksum, "".getBytes());

		try {
			lockLocal(fileUID);
			//Put the properties themselves
			newFile = localRepo.putFileProps(newFile, "", "");
		}
		finally {
			unlockLocal(fileUID);
		}


		//Add a journal entry
		JsonObject changes = new JsonObject();
		changes.addProperty("checksum", newFile.checksum);
		changes.addProperty("attrhash", newFile.attrhash);
		changes.addProperty("changetime", newFile.changetime);
		changes.addProperty("createtime", newFile.createtime);
		LJournal journal = new LJournal(fileUID, accountUID, changes);
		localRepo.putJournalEntry(journal);

		//Set zoning information.
		HZone newZoningInfo = new HZone(fileUID, true, false);
		Sync.getInstance().zoningDAO.put(newZoningInfo);

		return newFile.fileuid;
	}

	public UUID copyFile(@NonNull UUID toCopyUID, @NonNull UUID accountUID) throws FileNotFoundException, ConnectException {
		//Grab the properties for the item to copy
		LFile newFile = getFileProps(toCopyUID).toLocalFile();

		//Generate a new UUID, update the accountUID, and edit timestamps, but keep all other properties the same
		UUID fileUID = UUID.randomUUID();
		newFile.fileuid = fileUID;
		newFile.accountuid = accountUID;
		newFile.createtime = Instant.now().getEpochSecond();
		newFile.changetime = Instant.now().getEpochSecond();


		//Note: Locking for this file doesn't really matter, since nothing can know about it yet
		try {
			lockLocal(fileUID);
			//Put the properties themselves
			newFile = localRepo.putFileProps(newFile, "", "");
		}
		finally {
			unlockLocal(fileUID);
		}

		//Add a journal entry
		JsonObject changes = new JsonObject();
		changes.addProperty("checksum", newFile.checksum);
		changes.addProperty("attrhash", newFile.attrhash);
		changes.addProperty("changetime", newFile.changetime);
		changes.addProperty("createtime", newFile.createtime);
		LJournal journal = new LJournal(fileUID, newFile.accountuid, changes);
		localRepo.putJournalEntry(journal);

		//Set zoning information.
		//Zoning information should not match the copied file, the larger application should take handle movements.
		HZone newZoningInfo = new HZone(fileUID, true, false);
		Sync.getInstance().zoningDAO.put(newZoningInfo);

		return newFile.fileuid;
	}


	public void deleteFile(@NonNull UUID fileUID) throws FileNotFoundException {

		//Delete the file from local if it exists
		try {
			localRepo.deleteFileProps(fileUID);
		} catch (FileNotFoundException ignored) { }

		//Whether or not the file was deleted from local, add a deletion journal entry.
		//This will tell the the next sync to delete the remote file if it exists.
		//This technically means that the file still exists and can be accessed from remote,
		// but delete usually follows things like removal from directory listings so it should be fine.
		JsonObject changes = new JsonObject();
		changes.addProperty("isdeleted", true);
		LJournal journal = new LJournal(fileUID, currentAccount, changes);
		localRepo.putJournalEntry(journal);

		//Remove zoning information
		Sync.getInstance().zoningDAO.delete(fileUID);

		//All we need to do is delete the file properties and zoning information here in local.
		//Cleanup will remove this file's contents if they're not being used by another file.
		//If a remote file exists, Sync will now handle the delete from Remote using the new journal entry.


		listeners.notifyDataChanged(fileUID);
	}


	//---------------------------------------------------------------------------------------------


	//Returns SHA-256 hash of the given attributes. Referenced as attrHash in file properties
	public String setAttributes(@NonNull UUID fileUID, @NonNull JsonObject attributes, @NonNull String prevAttrHash) throws FileNotFoundException, ConnectException {
		localRepo.ensureLockHeld(fileUID);

		HFile props = null;

		//Try to get the file properties from local
		try {
			props = HFile.fromLocalFile( localRepo.getFileProps(fileUID) );

			//Check that the current attribute checksum matches what we were given to ensure we won't be overwriting any data
			if(!Objects.equals(props.attrhash, prevAttrHash))
				throw new IllegalStateException(String.format("Cannot set attributes, attrHashes don't match! FileUID='%s'", fileUID));
		} catch (FileNotFoundException ignored) { }

		//If the file properties don't exist locally, try to get them from the server
		if(props == null)
			props = HFile.fromRemoteFile( remoteRepo.getFileProps(fileUID) );



		//Get the checksum of the attributes
		String newAttrHash;
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(attributes.toString().getBytes());
			newAttrHash = Utilities.bytesToHex(hash);
		} catch (NoSuchAlgorithmException e) { throw new RuntimeException(e); }

		//Update the properties with the new info received
		String oldAttrHash = props.attrhash;
		props.userattr = attributes;
		props.attrhash = newAttrHash;
		props.changetime = Instant.now().getEpochSecond();
		props = HFile.fromLocalFile( localRepo.putFileProps(props.toLocalFile(), props.checksum, oldAttrHash) );


		//Add a journal entry
		JsonObject changes = new JsonObject();
		changes.addProperty("attrhash", props.checksum);
		changes.addProperty("changetime", props.changetime);
		LJournal journal = new LJournal(fileUID, currentAccount, changes);
		localRepo.putJournalEntry(journal);

		listeners.notifyDataChanged(fileUID);

		return props.attrhash;
	}



	//Returns SHA-256 checksum of the given contents. Referenced as checksum in the file properties
	public String writeFile(@NonNull UUID fileUID, @NonNull byte[] content, @NonNull String prevChecksum) throws FileNotFoundException, ConnectException, IOException {
		localRepo.ensureLockHeld(fileUID);

		HFile props = null;

		//Try to get the file properties from local
		try {
			props = HFile.fromLocalFile( localRepo.getFileProps(fileUID) );

			//Check that the current file checksum matches what we were given to ensure we won't be overwriting any data
			if(!Objects.equals(props.checksum, prevChecksum))
				throw new IllegalStateException(String.format("Cannot write, checksums don't match! FileUID='%s'", fileUID));
		} catch (FileNotFoundException ignored) { }

		//If the file properties don't exist locally, try to get them from the server
		if(props == null)
			props = HFile.fromRemoteFile( remoteRepo.getFileProps(fileUID) );



		//Get the checksum of the contents
		String newChecksum;
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(content);
			newChecksum = Utilities.bytesToHex(hash);
		} catch (NoSuchAlgorithmException e) { throw new RuntimeException(e); }


		//Actually write the contents
		LContent contentProps = localRepo.writeContents(newChecksum, content);

		//Update the properties with the new info received
		String oldChecksum = props.checksum;
		props.checksum = contentProps.checksum;
		props.filesize = contentProps.size;
		props.changetime = Instant.now().getEpochSecond();
		props.modifytime = Instant.now().getEpochSecond();
		props = HFile.fromLocalFile( localRepo.putFileProps(props.toLocalFile(), oldChecksum, props.attrhash) );

		//Add a journal entry
		JsonObject changes = new JsonObject();
		changes.addProperty("checksum", props.checksum);
		changes.addProperty("changetime", props.changetime);
		changes.addProperty("modifytime", props.createtime);
		LJournal journal = new LJournal(fileUID, currentAccount, changes);
		localRepo.putJournalEntry(journal);

		listeners.notifyDataChanged(fileUID);

		return props.checksum;
	}



	//Returns SHA-256 checksum of the given contents. Referenced as checksum in the file properties
	public String writeFile(@NonNull UUID fileUID, @NonNull Uri content, @NonNull String checksum, @NonNull String prevChecksum) throws FileNotFoundException, ConnectException, IOException {
		localRepo.ensureLockHeld(fileUID);

		HFile props = null;

		//Try to get the file properties from local
		try {
			props = HFile.fromLocalFile( localRepo.getFileProps(fileUID) );

			//Check that the current file checksum matches what we were given to ensure we won't be overwriting any data
			if(!Objects.equals(props.checksum, prevChecksum))
				throw new IllegalStateException(String.format("Cannot write, checksums don't match! FileUID='%s'", fileUID));
		} catch (FileNotFoundException ignored) { }

		//If the file properties don't exist locally, try to get them from the server
		if(props == null)
			props = HFile.fromRemoteFile( remoteRepo.getFileProps(fileUID) );



		//Actually write the contents
		LContent contentProps = localRepo.writeContents(checksum, content);


		//Update the properties with the new info received
		String oldChecksum = props.checksum;
		props.checksum = contentProps.checksum;
		props.filesize = contentProps.size;
		props.changetime = Instant.now().getEpochSecond();
		props.modifytime = Instant.now().getEpochSecond();
		props = HFile.fromLocalFile( localRepo.putFileProps(props.toLocalFile(), oldChecksum, props.attrhash) );

		//Add a journal entry
		JsonObject changes = new JsonObject();
		changes.addProperty("checksum", props.checksum);
		changes.addProperty("changetime", props.changetime);
		changes.addProperty("modifytime", props.createtime);
		LJournal journal = new LJournal(fileUID, currentAccount, changes);
		localRepo.putJournalEntry(journal);

		listeners.notifyDataChanged(fileUID);

		return props.checksum;
	}



	//TODO Make an import that grabs the files timestamps
	//Returns new FileUID for imported file
	public UUID importFile(@NonNull Uri content) throws IOException {
		//Write the source to a temp file so we can get the checksum
		File appCacheDir = MyApplication.getAppContext().getCacheDir();
		File tempFile = Files.createTempFile(appCacheDir.toPath(), UUID.randomUUID().toString(), null).toFile();

		String checksum;
		try (InputStream in = new URL(content.toString()).openStream();
			 FileOutputStream out = new FileOutputStream(tempFile);
			 DigestOutputStream dos = new DigestOutputStream(out, MessageDigest.getInstance("SHA-256"))) {

			byte[] dataBuffer = new byte[1024];
			int bytesRead;
			while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
				dos.write(dataBuffer, 0, bytesRead);
			}

			checksum = Utilities.bytesToHex(dos.getMessageDigest().digest());
		}
		catch (NoSuchAlgorithmException e) { throw new RuntimeException(e); }


		//Write the temp file contents to the content repo
		LContent contentProps = localRepo.writeContents(checksum, Uri.fromFile(tempFile));

		//Create a new file
		UUID fileUID = createFile(currentAccount, false, false);

		//Update the file props with the updated content properties
		LFile fileProps = localRepo.getFileProps(fileUID);
		fileProps.checksum = contentProps.checksum;
		fileProps.filesize = contentProps.size;
		fileProps.changetime = Instant.now().getEpochSecond();
		fileProps.modifytime = Instant.now().getEpochSecond();
		LFile newFile = localRepo.putFileProps(fileProps, fileProps.checksum, fileProps.attrhash);

		//Add another journal entry after the create entry
		JsonObject changes = new JsonObject();
		changes.addProperty("checksum", newFile.checksum);
		changes.addProperty("changetime", newFile.changetime);
		changes.addProperty("modifytime", newFile.createtime);
		LJournal journal = new LJournal(fileUID, currentAccount, changes);
		localRepo.putJournalEntry(journal);

		return newFile.fileuid;
	}

	public void exportFile(@NonNull UUID fileUID, @NonNull Uri destination) {
		throw new RuntimeException("Stub!");
	}


	//---------------------------------------------------------------------------------------------


	@Nullable
	public HZone getZoningInfo(@NonNull UUID fileUID) {
		//Since this method is really just to show the user what zones a file is in,
		// try to get zoning data from any enqueued workers first
		HZone zoning = ZoningWorker.getActiveWorkZoning(fileUID);
		if(zoning != null) return zoning;

		//Get the true zoning data from our DB
		return Sync.getInstance().zoningDAO.get(fileUID);
	}

	public void setZoning(@NonNull UUID fileUID, boolean shouldBeLocal, boolean shouldBeRemote) {
		ZoningWorker.enqueue(fileUID, shouldBeLocal, shouldBeRemote);
	}


}
