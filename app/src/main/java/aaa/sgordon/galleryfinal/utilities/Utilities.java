package aaa.sgordon.galleryfinal.utilities;

import android.util.Pair;

import androidx.annotation.NonNull;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Utilities {

	public static List<UUID> getUUIDsFromPaths(@NonNull List<Pair<Path, String>> paths) {
		//Grab the UUIDs of all the files in the new list
		List<UUID> fileUIDs = new ArrayList<>();
		for(Pair<Path, String> file : paths) {
			String UUIDString = file.first.getFileName().toString();
			if(UUIDString.equals("END"))	//Don't consider ends, we already considered their parent
				continue;
			UUID thisFileUID = UUID.fromString(UUIDString);
			fileUIDs.add(thisFileUID);
		}
		return fileUIDs;
	}

	public static String computeChecksum(@NonNull byte[] data) {
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(data);
			return bytesToHex(hash);
		} catch (NoSuchAlgorithmException e) { throw new RuntimeException(e); }
	}

	//https://stackoverflow.com/a/9855338
	private static final byte[] HEX_ARRAY = "0123456789ABCDEF".getBytes(StandardCharsets.US_ASCII);
	public static String bytesToHex(@NonNull byte[] bytes) {
		byte[] hexChars = new byte[bytes.length * 2];
		for (int j = 0; j < bytes.length; j++) {
			int v = bytes[j] & 0xFF;
			hexChars[j * 2] = HEX_ARRAY[v >>> 4];
			hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
		}
		return new String(hexChars, StandardCharsets.UTF_8);
	}
}
