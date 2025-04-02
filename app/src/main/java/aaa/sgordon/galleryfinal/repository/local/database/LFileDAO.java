package aaa.sgordon.galleryfinal.repository.local.database;

import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Query;
import androidx.room.Upsert;

import java.util.List;
import java.util.UUID;

import aaa.sgordon.galleryfinal.repository.local.types.LFile;


/*
For live UI updates, see "Write Observable Queries" in
https://developer.android.com/training/data-storage/room/async-queries#guava-livedata
Or Room's InvalidationTracker

Timestamp update help:
https://medium.com/@stephenja/timestamps-with-android-room-f3fd57b48250
 */

@Dao
public interface LFileDAO {
	@Query("SELECT * FROM file WHERE accountuid IN (:accountuids) LIMIT 500")
	List<LFile> getByAccount(UUID... accountuids);
	@Query("SELECT * FROM file WHERE accountuid IN (:accountuids) LIMIT 500 OFFSET :offset")
	List<LFile> getByAccount(int offset, UUID... accountuids);


	@Nullable
	@Query("SELECT * FROM file WHERE fileuid = :fileUID")
	LFile get(UUID fileUID);
	@Query("SELECT * FROM file WHERE fileuid IN (:fileUIDs)")
	List<LFile> get(UUID... fileUIDs);


	@Query("SELECT DISTINCT f.* FROM File f " +
			"LEFT JOIN Zone z ON f.fileuid = z.fileuid " +
			"WHERE z.fileuid IS NULL OR (z.isLocal = 0 AND z.isRemote = 1);")
	List<LFile> getTempWrites();


	@Upsert
	List<Long> put(LFile... files);

	@Delete
	Integer delete(LFile file);
	@Query("DELETE FROM file WHERE fileuid = :fileUID")
	Integer delete(UUID fileUID);
}