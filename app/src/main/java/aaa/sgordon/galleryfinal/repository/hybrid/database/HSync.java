package aaa.sgordon.galleryfinal.repository.hybrid.database;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.UUID;

@Entity(tableName = "sync")
public class HSync {
	@PrimaryKey
	@NonNull
	public UUID fileuid;

	@NonNull
	public String lastSyncChecksum;

	@Nullable
	public JsonObject lastSyncAttr;


	public HSync(@NonNull UUID fileuid, @NonNull String lastSyncChecksum, @Nullable JsonObject lastSyncAttr) {
		this.fileuid = fileuid;
		this.lastSyncChecksum = lastSyncChecksum;
		this.lastSyncAttr = lastSyncAttr;
	}


	public JsonObject toJson() {
		return new Gson().toJsonTree(this).getAsJsonObject();
	}

	@NonNull
	@Override
	public String toString() {
		JsonObject json = toJson();
		return json.toString();
	}
}
