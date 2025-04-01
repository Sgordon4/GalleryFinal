package aaa.sgordon.galleryfinal.repository.hybrid.jobs;

public class Cleanup {
	private static final String TAG = "Hyb.Cleanup";

	public static void cleanOrphanContent() {
		/*
		Sql:
		- Get content not in use in file table
		- content cannot be in use in sync table

		*/
	}


	public static void cleanSyncedTempFiles() {

	}
}
