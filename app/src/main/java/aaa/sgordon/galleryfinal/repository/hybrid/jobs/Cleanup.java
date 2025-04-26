package aaa.sgordon.galleryfinal.repository.hybrid.jobs;

import android.util.Log;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import aaa.sgordon.galleryfinal.repository.local.database.LContentDAO;
import aaa.sgordon.galleryfinal.repository.local.database.LFileDAO;
import aaa.sgordon.galleryfinal.repository.local.database.LJournalDAO;
import aaa.sgordon.galleryfinal.repository.local.types.LContent;
import aaa.sgordon.galleryfinal.repository.local.types.LFile;
import aaa.sgordon.galleryfinal.repository.local.types.LJournal;

public class Cleanup {
	private static final String TAG = "Hyb.Cleanup";

	public static void cleanOrphanContent(LContentDAO contentDAO) {
		//This could be done with a single sql query, but I have them split for now
		List<LContent> orphanedContent = contentDAO.getOrphans();

		Log.v(TAG, "Cleaning up orphaned content:");
		for (LContent content : orphanedContent)
			Log.v(TAG, content.checksum);

		for (LContent content : orphanedContent)
			contentDAO.delete(content);
	}

	//TODO Clean up any content that doesn't have an entry in the content db
	public static void defragContent(LContentDAO contentDAO) {

	}


	public static void cleanSyncedTempFiles(LFileDAO fileDAO, LJournalDAO journalDAO, UUID accountUID, int lastSyncLocalID) {
		//Get all temporary files, ones with remote zoning only or (erroneously) no zoning data at all
		List<LFile> tempFiles = fileDAO.getTempWrites();

		//Get the list of file changes since the last sync
		List<LJournal> filesWithChanges = journalDAO.getAllChangesFor(accountUID, lastSyncLocalID);


		Set<UUID> filesChangedSinceLastSync = new HashSet<>();
		for (LJournal journal : filesWithChanges)
			filesChangedSinceLastSync.add(journal.fileuid);

		Log.v(TAG, "Cleaning up synced files:");
		for (LFile file : tempFiles)
			Log.v(TAG, file.fileuid.toString());

		//If a temporary file has had no changes since the last sync, delete it
		for (LFile file : tempFiles)
			if (!filesChangedSinceLastSync.contains(file.fileuid))
				fileDAO.delete(file);
	}
}
