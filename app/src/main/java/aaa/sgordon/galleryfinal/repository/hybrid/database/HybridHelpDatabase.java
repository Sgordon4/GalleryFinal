package aaa.sgordon.galleryfinal.repository.hybrid.database;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import java.util.Arrays;

@Database(entities = {HZone.class}, version = 1)
@TypeConverters({HybridDBConverters.class})
public abstract class HybridHelpDatabase extends RoomDatabase {

	public abstract HZoningDAO getZoningDao();


	public static class DBBuilder {
		private static final String DB_NAME = "hybrid.db";

		public HybridHelpDatabase newInstance(@NonNull Context context) {
			Builder<HybridHelpDatabase> dbBuilder = Room.databaseBuilder(context, HybridHelpDatabase.class, DB_NAME);

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
