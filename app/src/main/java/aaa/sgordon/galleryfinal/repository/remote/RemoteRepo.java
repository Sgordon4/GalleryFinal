package aaa.sgordon.galleryfinal.repository.remote;

import android.content.Context;
import android.net.Uri;
import android.os.Looper;
import android.os.NetworkOnMainThreadException;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import aaa.sgordon.galleryfinal.repository.galleryhelpers.SAFGoFuckYourself;
import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;
import aaa.sgordon.galleryfinal.repository.remote.connectors.AccountConnector;
import aaa.sgordon.galleryfinal.repository.remote.connectors.ContentConnector;
import aaa.sgordon.galleryfinal.repository.remote.connectors.FileConnector;
import aaa.sgordon.galleryfinal.repository.remote.connectors.JournalConnector;
import aaa.sgordon.galleryfinal.repository.remote.types.RAccount;
import aaa.sgordon.galleryfinal.repository.remote.types.RContent;
import aaa.sgordon.galleryfinal.repository.remote.types.RFile;
import aaa.sgordon.galleryfinal.repository.remote.types.RJournal;
import aaa.sgordon.galleryfinal.utilities.MyApplication;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class RemoteRepo {
	private static final String TAG = "Hyb.Rem";
	private static final String baseServerUrl = "https://sgallery.ddns.net";
	//private static final String baseServerUrl = "http://10.0.2.2:3306";
	//private static final String baseServerUrl = "http://localhost:3306";

	private UUID deviceUID;
	private UUID currentAccount;

	public final AccountConnector accountConn;
	public final FileConnector fileConn;
	public final ContentConnector contentConn;
	public final JournalConnector journalConn;

	private final ScheduledExecutorService scheduler;
	private ScheduledFuture<?> noConnectionTimeout;
	private boolean hasNoConnection = false;


	public static RemoteRepo getInstance() {
		return SingletonHelper.INSTANCE;
	}
	private static class SingletonHelper {
		private static final RemoteRepo INSTANCE = new RemoteRepo();
	}
	private RemoteRepo() {
		//TODO Store this in a file
		//deviceUID = UUID.fromString("fee96d0e-3953-4a51-aff3-55ce65a70bcc");			//ID for Sean
		//deviceUID = UUID.fromString("6597c6b7-9f17-4c93-aad8-e9570affdbb9");			//ID for Marco
		deviceUID = UUID.fromString("4d086537-5b27-40e6-83f2-12677cb4d1c9");			//ID for Bryce

		OkHttpClient client = new OkHttpClient().newBuilder()
				.addInterceptor(new LogInterceptor())
				.followRedirects(true)
				.connectTimeout(2, TimeUnit.SECONDS)
				.readTimeout(5, TimeUnit.SECONDS)
				.writeTimeout(5, TimeUnit.SECONDS)
				.followSslRedirects(true)
				.build();

		accountConn = new AccountConnector(baseServerUrl, client);
		fileConn = new FileConnector(baseServerUrl, client, deviceUID);
		contentConn = new ContentConnector(baseServerUrl, client);
		journalConn = new JournalConnector(baseServerUrl, client, deviceUID);

		scheduler = Executors.newSingleThreadScheduledExecutor();
	}

	private boolean isOnMainThread() {
		return Thread.currentThread().equals(Looper.getMainLooper().getThread());
	}

	public UUID getCurrentAccount() {
		return currentAccount;
	}
	public void setAccount(@NonNull UUID accountUID) {
		this.currentAccount = accountUID;
	}


	private boolean isServerKnownUnreachable() {
		return hasNoConnection;
	}
	private void onConnectException() {
		hasNoConnection = true;
		if(noConnectionTimeout != null && !noConnectionTimeout.isDone())
			noConnectionTimeout.cancel(true);

		noConnectionTimeout = scheduler.schedule(() -> hasNoConnection = false, 15, TimeUnit.SECONDS);
	}
	private void cancelNoConnectionTimeout() {
		if(noConnectionTimeout != null && !noConnectionTimeout.isDone())
			noConnectionTimeout.cancel(true);
	}


	//---------------------------------------------------------------------------------------------
	// Account
	//---------------------------------------------------------------------------------------------

	public RAccount getAccountProps(@NonNull UUID accountUID) throws FileNotFoundException, ConnectException {
		Log.i(TAG, String.format("REMOTE GET ACCOUNT PROPS called with accountUID='%s'", accountUID));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		if(isServerKnownUnreachable())
			throw new ConnectException("Server is known to be unreachable.");

		JsonObject accountProps;
		try {
			accountProps = accountConn.getProps(accountUID);
		} catch (ConnectException e) {
			onConnectException();
			throw e;
		} catch (SocketTimeoutException | SocketException e) {
			onConnectException();
			throw new ConnectException();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		if(accountProps == null) throw new FileNotFoundException("Account not found! ID: '"+accountUID);
		return new Gson().fromJson(accountProps, RAccount.class);
	}


	public void putAccountProps(@NonNull RAccount accountProps) throws ConnectException {
		Log.i(TAG, String.format("REMOTE PUT ACCOUNT PROPS called with accountUID='%s'", accountProps.accountuid));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		if(isServerKnownUnreachable())
			throw new ConnectException("Server is known to be unreachable.");

		try {
			accountConn.updateEntry(accountProps.toJson());
		} catch (ConnectException e) {
			onConnectException();
			throw e;
		} catch (SocketTimeoutException | SocketException e) {
			onConnectException();
			throw new ConnectException();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}


	//---------------------------------------------------------------------------------------------
	// File
	//---------------------------------------------------------------------------------------------







	//Create file(accountUID, isDir, isLink)
	//Create should actually just take an RFile or whatever, throw if file exists

	//Update accesstime?
	// vs
	//Update timestamps?	<-- This one

	//putChecksum, putAttrs
	//Write: checksum, filesize, modifytime, changetime (do we want to fetch filesize?)
	//SetAttr: attrs, changetime


	//Delete



	//---------------------------------------------------------------------------------------------


	@NonNull
	public RFile getFileProps(@NonNull UUID fileUID) throws FileNotFoundException, ConnectException {
		Log.v(TAG, String.format("REMOTE GET FILE PROPS called with fileUID='%s'", fileUID));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		if(isServerKnownUnreachable())
			throw new ConnectException("Server is known to be unreachable.");

		try {
			return fileConn.getProps(fileUID);
		} catch (FileNotFoundException e) {
			throw e;
		} catch (ConnectException e) {
			onConnectException();
			throw e;
		} catch (SocketTimeoutException | SocketException e) {
			onConnectException();
			throw new ConnectException();
		} catch (IOException e) {
			throw new RuntimeException();
		}
	}
	public boolean doesFileExist(@NonNull UUID fileUID) throws ConnectException {
		Log.v(TAG, String.format("REMOTE DOES FILE EXIST called with fileUID='%s'", fileUID));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		if(isServerKnownUnreachable())
			throw new ConnectException("Server is known to be unreachable.");

		try {
			getFileProps(fileUID);
			return true;
		} catch (FileNotFoundException e) {
			return false;
		} catch (ConnectException e) {
			onConnectException();
			throw e;
		}
	}



	public RFile createFile(@NonNull RFile fileProps) throws ContentsNotFoundException, FileAlreadyExistsException, ConnectException {
		Log.i(TAG, String.format("REMOTE CREATE FILE PROPS called with fileUID='%s'", fileProps.fileuid));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		if(isServerKnownUnreachable())
			throw new ConnectException("Server is known to be unreachable.");

		try {
			//Check if the server is missing the file contents. If so, we shouldn't commit the file changes
			contentConn.getProps(fileProps.checksum);

			//Now that we've confirmed the contents exist, create the file metadata
			return fileConn.create(fileProps);
		}
		catch (ContentsNotFoundException e) {
			throw new ContentsNotFoundException("Cannot create props, remote is missing file contents!");
		} catch (FileAlreadyExistsException e) {
			throw e;
		} catch (ConnectException e) {
			onConnectException();
			throw e;
		} catch (SocketTimeoutException | SocketException e) {
			onConnectException();
			throw new ConnectException();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}


	public RFile putContentProps(@NonNull RFile fileProps, @NonNull String prevChecksum)
			throws ContentsNotFoundException, FileNotFoundException, IllegalStateException, ConnectException {
		Log.i(TAG, String.format("REMOTE PUT FILE CONTENT PROPS called with fileUID='%s'", fileProps.fileuid));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		if(isServerKnownUnreachable())
			throw new ConnectException("Server is known to be unreachable.");

		try {
			//Check if the server is missing the file contents. If so, we shouldn't commit the file changes
			contentConn.getProps(fileProps.checksum);

			//Now that we've confirmed the contents exist, update the file metadata
			return fileConn.putContentProps(fileProps, prevChecksum);
		}
		catch (ContentsNotFoundException e) {
			throw new ContentsNotFoundException("Cannot update content props, remote is missing file contents!");
		} catch (FileNotFoundException e) {
			throw e;
		} catch (ConnectException e) {
			onConnectException();
			throw e;
		} catch (SocketTimeoutException | SocketException e) {
			onConnectException();
			throw new ConnectException();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}


	public RFile putAttributeProps(@NonNull RFile fileProps, @NonNull String prevAttrHash)
			throws FileNotFoundException, IllegalStateException, ConnectException {
		Log.i(TAG, String.format("REMOTE PUT FILE ATTRIBUTE PROPS called with fileUID='%s'", fileProps.fileuid));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		if(isServerKnownUnreachable())
			throw new ConnectException("Server is known to be unreachable.");

		try {
			return fileConn.putAttributeProps(fileProps, prevAttrHash);
		}
		catch (FileNotFoundException e) {
			throw e;
		} catch (ConnectException e) {
			onConnectException();
			throw e;
		} catch (SocketTimeoutException | SocketException e) {
			onConnectException();
			throw new ConnectException();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}


	//TODO This currently just overwrites all timestamps. We want it to take maxes and, like, actually be useful.
	public RFile putTimestamps(@NonNull RFile fileProps) throws FileNotFoundException, ConnectException {
		Log.i(TAG, String.format("REMOTE PUT FILE TIMESTAMP PROPS called with fileUID='%s'", fileProps.fileuid));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		if(isServerKnownUnreachable())
			throw new ConnectException("Server is known to be unreachable.");

		try {
			return fileConn.putTimestamps(fileProps);
		}
		catch (FileNotFoundException e) {
			throw e;
		} catch (ConnectException e) {
			onConnectException();
			throw e;
		} catch (SocketTimeoutException | SocketException e) {
			onConnectException();
			throw new ConnectException();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}



	public void deleteFileProps(@NonNull UUID fileUID) throws FileNotFoundException, ConnectException {
		Log.i(TAG, String.format("REMOTE DELETE FILE called with fileUID='%s'", fileUID));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		if(isServerKnownUnreachable())
			throw new ConnectException("Server is known to be unreachable.");

		try {
			fileConn.delete(fileUID, currentAccount);
		} catch (FileNotFoundException e) {
			throw e;
		} catch (ConnectException e) {
			onConnectException();
			throw e;
		} catch (SocketTimeoutException | SocketException e) {
			onConnectException();
			throw new ConnectException();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}


	//---------------------------------------------------------------------------------------------
	// Contents
	//---------------------------------------------------------------------------------------------


	public RContent getContentProps(@NonNull String name) throws ContentsNotFoundException, ConnectException {
		Log.v(TAG, String.format("\nREMOTE GET CONTENT PROPS called with name='%s'", name));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		if(isServerKnownUnreachable())
			throw new ConnectException("Server is known to be unreachable.");

		try {
			return contentConn.getProps(name);
		} catch (ContentsNotFoundException e) {
			throw e;
		} catch (ConnectException e) {
			onConnectException();
			throw e;
		} catch (SocketTimeoutException | SocketException e) {
			onConnectException();
			throw new ConnectException();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}


	public Uri getContentDownloadUri(@NonNull String name) throws ContentsNotFoundException, ConnectException {
		Log.v(TAG, String.format("\nREMOTE GET CONTENT URI called with name='"+name+"'"));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		if(isServerKnownUnreachable())
			throw new ConnectException("Server is known to be unreachable.");

		try {
			//Throws a ContentsNotFound exception if the content properties don't exist
			getContentProps(name);

			//Now that we know the properties exist, return the content uri
			return Uri.parse(contentConn.getDownloadUrl(name));
		} catch (ContentsNotFoundException e) {
			throw e;
		} catch (ConnectException e) {
			onConnectException();
			throw e;
		} catch (SocketTimeoutException | SocketException e) {
			onConnectException();
			throw new ConnectException();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}



	//No delete method, we want that to be done by automatic jobs on the server, not here



	//Helper method
	//Source file must be on-disk
	//Returns the fileSize of the provided source
	//WARNING: DOES NOT UPDATE FILE PROPERTIES
	public RContent uploadData(@NonNull String name, @NonNull Uri source) throws FileNotFoundException, ConnectException {
		Log.i(TAG, "\nREMOTE PUT CONTENTS called with source='"+source+"'");
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		if(isServerKnownUnreachable())
			throw new ConnectException("Server is known to be unreachable.");

		Context context = MyApplication.getAppContext();

		if (!SAFGoFuckYourself.fileExists(context, source)) throw new FileNotFoundException("Source file not found! Path: '"+source+"'");
		int fileSize = SAFGoFuckYourself.getFileSize(context, source);

		try {
			//If the file is small enough, upload it to one url
			if(fileSize <= ContentConnector.MIN_PART_SIZE) {
				Log.i(TAG, "Source is <= 5MB, uploading directly.");
				Log.i(TAG, "... Getting content upload URL");
				String uploadUrl = contentConn.getUploadUrl(name);

				byte[] buffer = new byte[fileSize];
				try (BufferedInputStream in = new BufferedInputStream( context.getContentResolver().openInputStream(source) )) {
					int bytesRead = in.read(buffer);
					Log.i(TAG, "... Uploading to URL");
					String ETag = contentConn.uploadToUrl(buffer, uploadUrl);
				}
				Log.i(TAG, "... Direct upload complete!");
			}
			//Otherwise, we need to multipart upload
			else {
				Log.i(TAG, "Source is > 5MB, uploading via multipart.");

				//Get the individual components needed for a multipart upload
				Pair<UUID, List<Uri>> multipart = contentConn.initializeMultipart(name, fileSize);
				UUID uploadID = multipart.first;
				List<Uri> uris = multipart.second;


				//Upload the file in parts to each url, receiving an ETag for each one
				List<ContentConnector.ETag> ETags = new ArrayList<>();
				try (BufferedInputStream in = new BufferedInputStream( context.getContentResolver().openInputStream(source) )) {
					//WARNING: For if this code is converted to parallel, each loop uses 5MB of memory for the buffer
					for(int i = 0; i < uris.size(); i++) {
						int remaining = fileSize - (ContentConnector.MIN_PART_SIZE * i);
						int partSize = Math.min(ContentConnector.MIN_PART_SIZE, remaining);

						byte[] buffer = new byte[partSize];
						int bytesRead = in.read(buffer);

						String uri = uris.get(i).toString();
						Log.i(TAG, "... Uploading to URL");
						String ETag = contentConn.uploadToUrl(buffer, uri);

						ETags.add(new ContentConnector.ETag(i+1, ETag));
					}
				}


				//Confirm the multipart upload is completed, passing the information we've gathered thus far
				contentConn.completeMultipart(name, uploadID, ETags);
				Log.i(TAG, "... Multipart upload complete!");
			}


			//Now that the data has been written, create a new entry in the content table
			Log.i(TAG, "... Putting content props");
			return contentConn.putProps(name, fileSize);

		} catch (ConnectException e) {
			onConnectException();
			throw e;
		} catch (SocketTimeoutException | SocketException e) {
			onConnectException();
			throw new ConnectException();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}


	//Note: This isn't intended to be used outside of testing
	public void deleteContentProps(@NonNull String name) throws ContentsNotFoundException, ConnectException {
		Log.v(TAG, String.format("\nREMOTE DELETE CONTENT PROPS called with name='%s'", name));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		if(isServerKnownUnreachable())
			throw new ConnectException("Server is known to be unreachable.");

		try {
			contentConn.deleteProps(name);
		} catch (ContentsNotFoundException e) {
			throw e;
		} catch (ConnectException e) {
			onConnectException();
			throw e;
		} catch (SocketTimeoutException | SocketException e) {
			onConnectException();
			throw new ConnectException();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}


	//---------------------------------------------------------------------------------------------
	// Journal
	//---------------------------------------------------------------------------------------------

	@NonNull
	public List<RJournal> getLatestChangesFor(int journalID, @Nullable UUID accountUID, @Nullable UUID[] fileUIDs) throws ConnectException {
		Log.v(TAG, String.format("REMOTE JOURNAL GET LATEST called with journalID='%s', accountUID='%s'", journalID, accountUID));
		if (isOnMainThread()) throw new NetworkOnMainThreadException();

		if(isServerKnownUnreachable())
			throw new ConnectException("Server is known to be unreachable.");

		try {
			return journalConn.getLatestChangesOnly(journalID, accountUID, fileUIDs);
		} catch (ConnectException e) {
			onConnectException();
			throw e;
		} catch (SocketTimeoutException | SocketException e) {
			onConnectException();
			throw new ConnectException();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}


	@NonNull
	public List<RJournal> getAllChangesFor(int journalID, @Nullable UUID accountUID, @Nullable UUID[] fileUIDs) throws ConnectException {
		Log.v(TAG, String.format("REMOTE JOURNAL GET ALL called with journalID='%s', accountUID='%s'", journalID, accountUID));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		if(isServerKnownUnreachable())
			throw new ConnectException("Server is known to be unreachable.");

		try {
			return journalConn.getAllChanges(journalID, accountUID, fileUIDs);
		} catch (ConnectException e) {
			onConnectException();
			throw e;
		} catch (SocketTimeoutException | SocketException e) {
			onConnectException();
			throw new ConnectException();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}


	/*
	public List<SJournal> longpollJournalEntriesAfter(int journalID) throws ConnectException, TimeoutException, SocketTimeoutException {
		Log.i(TAG, String.format("LONGPOLL SERVER JOURNALS AFTER ID called with journalID='%s'", journalID));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		try {
			return journalConn.longpollJournalEntriesAfter(journalID);
		} catch (ConnectException e) {
			throw e;
		} catch (TimeoutException | SocketException e) {
			throw new TimeoutException();
		} catch (SocketTimeoutException e) {
			throw e;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	 */


	//---------------------------------------------------------------------------------------------

	//TODO Figure out how to log timeouts
	private static class LogInterceptor implements Interceptor {
		@NonNull
		@Override
		public Response intercept(Chain chain) throws IOException {
			Request request = chain.request();
			Log.d(TAG, String.format("	OKHTTP: %s --> %s", request.method(), request.url()));
			//if(request.body() != null)	//Need another method to print body, this no worky
				//Log.d(TAG, String.format("OKHTTP: Sending with body - %s", request.body()));

			long t1 = System.nanoTime();
			//Response response = chain.proceed(request);
			Response response = chain
					.withConnectTimeout(chain.connectTimeoutMillis(), TimeUnit.MILLISECONDS)
					.withReadTimeout(chain.readTimeoutMillis(), TimeUnit.MILLISECONDS)
					.withWriteTimeout(chain.writeTimeoutMillis(), TimeUnit.MILLISECONDS).proceed(request);
			long t2 = System.nanoTime();

			Log.d(TAG, String.format("	OKHTTP: Received response %s for %s in %.1fms",
					response.code(), response.request().url(), (t2 - t1) / 1e6d));

			//Log.v(TAG, String.format("%s", response.headers()));
			if(response.body() != null)
				Log.d(TAG, "	OKHTTP: Returned with body length of "+response.body().contentLength());
			else
				Log.d(TAG, "	OKHTTP: Returned with null body");

			return response;
		}
	}
}
