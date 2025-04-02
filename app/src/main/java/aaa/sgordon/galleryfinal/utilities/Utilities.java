package aaa.sgordon.galleryfinal.utilities;

import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.apache.commons.io.FilenameUtils;

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

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


	//Return the first 4 characters of a UUID
	public static String g4ID(@NonNull UUID uuid) {
		return uuid.toString().substring(0, 4);
	}




	//Thanks ChatGPT!
	public static void showColorDebugOverlay(ViewGroup rootView, Context context) {
		int[] attrs = new int[]{
				com.google.android.material.R.attr.colorSurface,
				com.google.android.material.R.attr.colorSurfaceVariant,
				com.google.android.material.R.attr.colorSurfaceContainer,
				com.google.android.material.R.attr.colorBackgroundFloating,
				com.google.android.material.R.attr.colorOnSurface,
				com.google.android.material.R.attr.colorPrimary,
				com.google.android.material.R.attr.colorPrimaryContainer
		};

		String[] attrNames = {
				"colorSurface",
				"colorSurfaceVariant",
				"colorSurfaceContainer",
				"colorBackgroundFloating",
				"colorOnSurface",
				"colorPrimary",
				"colorPrimaryContainer"
		};

		LinearLayout overlay = new LinearLayout(context);
		overlay.setOrientation(LinearLayout.VERTICAL);
		overlay.setBackgroundColor(Color.BLACK);
		overlay.setPadding(20, 20, 20, 20);
		//overlay.setAlpha(0.8f);

		TypedValue typedValue = new TypedValue();
		for (int i = 0; i < attrs.length; i++) {
			if (context.getTheme().resolveAttribute(attrs[i], typedValue, true)) {
				int color = typedValue.data;

				TextView textView = new TextView(context);
				textView.setText(attrNames[i] + ": #" + Integer.toHexString(color));
				textView.setBackgroundColor(color);
				textView.setTextColor((Color.luminance(color) > 0.5) ? Color.BLACK : Color.WHITE);
				textView.setPadding(16, 8, 16, 8);

				overlay.addView(textView);
			}
		}

		// Add overlay to root layout
		rootView.addView(overlay, new ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
	}
}
