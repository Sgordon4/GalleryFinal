package aaa.sgordon.galleryfinal.utilities;

import androidx.annotation.NonNull;

import org.apache.commons.io.FilenameUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Utilities {

	public static boolean isFileMedia(@NonNull String fileName) {
		String extension = FilenameUtils.getExtension(fileName);
		return extension.equals("jpg") || extension.equals("jpeg") || extension.equals("png") ||
				extension.equals("gif") || extension.equals("mp4") || extension.equals("mov");
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
