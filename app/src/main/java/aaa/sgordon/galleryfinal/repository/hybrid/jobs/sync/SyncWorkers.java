package aaa.sgordon.galleryfinal.repository.hybrid.jobs.sync;

import android.content.Context;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.ListenableWorker;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.WorkQuery;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.net.ConnectException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import aaa.sgordon.galleryfinal.repository.hybrid.jobs.Cleanup;
import aaa.sgordon.galleryfinal.utilities.MyApplication;
import aaa.sgordon.galleryfinal.repository.local.LocalRepo;
import aaa.sgordon.galleryfinal.repository.local.types.LJournal;
import aaa.sgordon.galleryfinal.repository.remote.RemoteRepo;
import aaa.sgordon.galleryfinal.repository.remote.types.RJournal;
import aaa.sgordon.galleryfinal.utilities.Utilities;

public class SyncWorkers {

	private static final String TAG = "Hyb.Sync.Watcher";

	//Returns updated local::remote sync IDs
	@Nullable
	public static Pair<Integer, Integer> lookForSync(UUID accountUID, int lastSyncLocal, int lastSyncRemote) {
		Log.i(TAG, "Journal Watcher looking for files to sync after "+lastSyncLocal+":"+lastSyncRemote+" for AccountUID='"+accountUID+"'");

		LocalRepo localRepo = LocalRepo.getInstance();
		RemoteRepo remoteRepo = RemoteRepo.getInstance();


		//Get all the files with changes since the journalIDs specified
		List<LJournal> localFilesChanged = localRepo.getLatestChangesFor(lastSyncLocal, accountUID, null);
		List<RJournal> remoteFilesChanged;
		try {
			remoteFilesChanged = remoteRepo.getLatestChangesFor(lastSyncRemote, accountUID, null);
		} catch (ConnectException e) {
			Log.w(TAG, "Journal Watcher requeueing due to connection issues!");
			return null;
		}


		//And grab the data we need from them
		Set<UUID> filesChanged = new HashSet<>();
		int newSyncLocal = lastSyncLocal;
		int newSyncRemote = lastSyncRemote;

		for(LJournal lJournal : localFilesChanged) {
			filesChanged.add(lJournal.fileuid);
			newSyncLocal = Math.max(newSyncLocal, lJournal.journalid);
		}
		for(RJournal rJournal : remoteFilesChanged) {
			filesChanged.add(rJournal.fileuid);
			newSyncRemote = Math.max(newSyncRemote, rJournal.journalid);
		}

		Log.i(TAG, "Journal Watcher found "+filesChanged.size()+" files to sync");

		//For each file with changes, start a worker to sync it
		for(UUID fileUID : filesChanged) {
			SyncWorker.enqueue(fileUID, lastSyncLocal, lastSyncRemote);
		}


		return new Pair<>(newSyncLocal, newSyncRemote);
	}


	//---------------------------------------------------------------------------------------------
	//---------------------------------------------------------------------------------------------


	public static class SyncWorker extends Worker {
		private static final String TAG = "Hyb.Sync.Wrk";

		public SyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
			super(context, workerParams);
		}


		//Enqueue a worker to sync a file
		public static void enqueue(@NonNull UUID fileUID, int localSyncID, int remoteSyncID) {
			Data.Builder data = new Data.Builder();
			data.putString("FILEUID", fileUID.toString());
			data.putString("LOCAL_JID", String.valueOf(localSyncID));
			data.putString("REMOTE_JID", String.valueOf(remoteSyncID));

			OneTimeWorkRequest worker = new OneTimeWorkRequest.Builder(SyncWorker.class)
					.setConstraints(new Constraints.Builder()
							.setRequiredNetworkType(NetworkType.UNMETERED)
							.setRequiresStorageNotLow(true)
							.build())
					.addTag(fileUID.toString()).addTag("SYNC")
					.setInputData(data.build()).build();


			WorkManager workManager = WorkManager.getInstance(MyApplication.getAppContext());

			//If there is any enqueued (NOT RUNNING) sync job for this fileUID, do nothing
			try {
				WorkQuery workQuery = WorkQuery.Builder
						.fromUniqueWorkNames(Collections.singletonList("sync_"+fileUID))
						.addStates(Collections.singletonList(WorkInfo.State.ENQUEUED))
						.build();
				List<WorkInfo> existingInfo = workManager.getWorkInfos(workQuery).get();

				if(!existingInfo.isEmpty())
					return;
			} catch (ExecutionException | InterruptedException e) { throw new RuntimeException(e); }

			//Enqueue the sync job, keeping any currently running job operational
			workManager.enqueueUniqueWork("sync_"+fileUID, ExistingWorkPolicy.APPEND_OR_REPLACE, worker);
		}


		public static void dequeue(@NonNull UUID fileuid) {
			WorkManager workManager = WorkManager.getInstance(MyApplication.getAppContext());
			workManager.cancelUniqueWork("sync_"+fileuid);
		}



		@NonNull
		@Override
		public Result doWork() {
			String fileUIDString = getInputData().getString("FILEUID");
			assert fileUIDString != null;
			UUID fileUID = UUID.fromString(fileUIDString);

			String localString = getInputData().getString("LOCAL_JID");
			assert localString != null;
			int localSyncID = Integer.parseInt(localString);

			String remoteString = getInputData().getString("REMOTE_JID");
			assert remoteString != null;
			int remoteSyncID = Integer.parseInt(remoteString);

			String SUBTAG = TAG + "." + Utilities.g4ID(fileUID);
			Log.i(SUBTAG, "SyncWorker syncing for FileUID='"+fileUID+"'");



			try {
				Pair<Integer, Integer> syncIDs = Sync.getInstance().sync(fileUID, localSyncID, remoteSyncID);

				Data.Builder data = new Data.Builder();
				data.putString("LOCAL_JID", String.valueOf(syncIDs.first));
				data.putString("REMOTE_JID", String.valueOf(syncIDs.second));
				return Result.success(data.build());
			}
			//If the sync fails due to another update happening before we could finish the sync, requeue it for later
			catch (IllegalStateException e) {
				Log.w(SUBTAG, "SyncWorker requeueing due to conflicting update!");
				return Result.retry();
			}
			//If the sync fails due to server connection issues, requeue it for later
			catch (ConnectException e) {
				Log.w(SUBTAG, "SyncWorker requeueing due to connection issues!");
				return Result.retry();
			}
			//If the sync fails to write to local, we're hosed
			catch (Exception e) {
				Log.e(SUBTAG, "SyncWorker failed to write to local for FileUID='"+fileUID+"'", e);
				return Result.failure();
			}
		}
	}
}
