package aaa.sgordon.galleryfinal.repository.local.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Query;
import androidx.room.Upsert;

import java.util.List;

import aaa.sgordon.galleryfinal.repository.local.types.LContent;

@Dao
public interface LContentDAO {
	@Query("SELECT * FROM content WHERE name = :name")
	LContent get(String name);

	@Query("SELECT c.* FROM Content c " +
			"LEFT JOIN file f ON c.checksum = f.checksum " +
			"LEFT JOIN sync s ON c.checksum = s.lastSyncChecksum " +
			"WHERE f.checksum IS NULL AND s.lastSyncChecksum IS NULL;")
	List<LContent> getOrphans();

	@Upsert
	void put(LContent... contents);

	@Delete
	Integer delete(LContent... contents);
	@Query("DELETE FROM content WHERE name = :fileHash")
	Integer delete(String fileHash);
}