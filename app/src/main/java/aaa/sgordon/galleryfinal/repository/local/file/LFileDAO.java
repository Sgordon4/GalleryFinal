package aaa.sgordon.galleryfinal.repository.local.file;

import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Query;
import androidx.room.Upsert;

import java.util.List;
import java.util.UUID;


/*
For live UI updates, see "Write Observable Queries" in
https://developer.android.com/training/data-storage/room/async-queries#guava-livedata

Timestamp update help:
https://medium.com/@stephenja/timestamps-with-android-room-f3fd57b48250
 */

@Dao
public interface LFileDAO {
	//Mostly for testing
	@Query("SELECT * FROM file LIMIT 500")
	List<LFile> loadAll();
	@Query("SELECT * FROM file LIMIT 500  OFFSET :offset")
	List<LFile> loadAll(int offset);

	@Query("SELECT * FROM file WHERE accountuid IN (:accountuids) LIMIT 500")
	List<LFile> loadAllByAccount(UUID... accountuids);
	@Query("SELECT * FROM file WHERE accountuid IN (:accountuids) LIMIT 500 OFFSET :offset")
	List<LFile> loadAllByAccount(int offset, UUID... accountuids);


	@Nullable
	@Query("SELECT * FROM file WHERE fileuid = :fileUID")
	LFile loadByUID(UUID fileUID);
	@Query("SELECT * FROM file WHERE fileuid IN (:fileUIDs)")
	List<LFile> loadByUID(UUID... fileUIDs);



	@Upsert
	List<Long> put(LFile... files);


	@Delete
	Integer delete(LFile... files);
	@Query("DELETE FROM file WHERE fileuid IN (:fileUIDs)")
	Integer delete(UUID... fileUIDs);
}