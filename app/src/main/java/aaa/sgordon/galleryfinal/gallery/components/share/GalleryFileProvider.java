package aaa.sgordon.galleryfinal.gallery.components.share;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

public class GalleryFileProvider extends ContentProvider {
	private static final String TAG = "Gal.Share";
	private static final String AUTHORITY = "sgordon.gallery.shared";
	private static final int FILE_CODE = 1;
	private static final UriMatcher uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

	static {
		//Should match "shared_files/{fileUID}/{fileName}"
		uriMatcher.addURI(AUTHORITY, "shared_files/*/*", FILE_CODE);
	}

	@Override
	public boolean onCreate() {
		return true;
	}
	@Nullable
	@Override
	public String getType(@NonNull Uri uri) {
		return "application/octet-stream";
	}


	@Override
	public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode) throws FileNotFoundException {
		if (uriMatcher.match(uri) != FILE_CODE) {
			throw new FileNotFoundException("Invalid URI: " + uri);
		}

		List<String> pathSegments = uri.getPathSegments();
		UUID fileUID = UUID.fromString(pathSegments.get(pathSegments.size() - 2));
		String fileName = pathSegments.get(pathSegments.size()-1);

		if (fileName == null || fileName.contains(".."))
			throw new FileNotFoundException("Invalid filename for share: "+uri);


		//There can be duplicate filenames being shared at the same time, so each file is separated by UUID
		File targetFile = Paths.get(requireContext().getCacheDir().getPath(), "shared_files", fileUID.toString(), fileName).toFile();

		if (!targetFile.exists())
			throw new FileNotFoundException("Cached file not found for share: "+uri);

		return ParcelFileDescriptor.open(targetFile, ParcelFileDescriptor.MODE_READ_ONLY);
	}


	//---------------------------------------------------------------------------------------------

	@Nullable
	@Override
	public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
						@Nullable String[] selectionArgs, @Nullable String sortOrder) {
		return null;
	}
	@Nullable
	@Override
	public Uri insert(@NonNull Uri uri, ContentValues values) {
		throw new UnsupportedOperationException("Insert not supported");
	}
	@Override
	public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
		return 0;
	}
	@Override
	public int update(@NonNull Uri uri, ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
		return 0;
	}
}
