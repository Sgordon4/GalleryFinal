package aaa.sgordon.galleryfinal.repository.local.database;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import java.util.Arrays;

import aaa.sgordon.galleryfinal.repository.hybrid.database.HSync;
import aaa.sgordon.galleryfinal.repository.hybrid.database.HSyncDAO;
import aaa.sgordon.galleryfinal.repository.hybrid.database.HZone;
import aaa.sgordon.galleryfinal.repository.hybrid.database.HZoningDAO;
import aaa.sgordon.galleryfinal.repository.local.types.LAccount;
import aaa.sgordon.galleryfinal.repository.local.types.LContent;
import aaa.sgordon.galleryfinal.repository.local.types.LFile;
import aaa.sgordon.galleryfinal.repository.local.types.LJournal;


@Database(entities = {LAccount.class, LFile.class, LJournal.class, LContent.class, HZone.class, HSync.class}, version = 1)
@TypeConverters({LocalConverters.class})
public abstract class LocalDatabase extends RoomDatabase {


	public abstract LAccountDAO getAccountDao();
	public abstract LFileDAO getFileDao();
	public abstract LJournalDAO getJournalDao();
	public abstract LContentDAO getContentDao();
	public abstract HZoningDAO getZoningDao();
	public abstract HSyncDAO getSyncDao();




	public static class DBBuilder {
		private static final String DB_NAME = "hlocal.db";

		public LocalDatabase newInstance(@NonNull Context context) {
			Builder<LocalDatabase> dbBuilder = Room.databaseBuilder(context, LocalDatabase.class, DB_NAME);

			//SQL Logging:
			QueryCallback callback = (s, list) -> {
				Log.v("Gal.SQLite", "---------------------------------------------------------");
				Log.v("Gal.SQLite", s);
				Log.v("Gal.SQLite", Arrays.toString(list.toArray()));
			};
			//dbBuilder.setQueryCallback(callback, Executors.newSingleThreadExecutor());

			return dbBuilder.build();
		}
	}
}