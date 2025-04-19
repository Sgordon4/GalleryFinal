package aaa.sgordon.galleryfinal.repository.caches;

import android.net.Uri;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import aaa.sgordon.galleryfinal.gallery.ListItem;
import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridAPI;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridListeners;
import aaa.sgordon.galleryfinal.repository.hybrid.types.HFile;
import aaa.sgordon.galleryfinal.utilities.MyApplication;

//WARNING: This object should live as long as the Application is running. Keep in Activity ViewModel.
public class LinkCache {
	private final static String TAG = "Gal.LinkCache";
	private final HybridAPI hAPI;
	private final HybridListeners.FileChangeListener fileChangeListener;
	private final UpdateListeners updateListeners;

	private final Map<UUID, LinkTarget> linkTargets;

	//Since, in our current implementation, files cannot change their nature (isDir/isLink), this works well
	private final Set<UUID> isLink;
	private final Set<UUID> isDir;
	private final Set<UUID> isNormal;


	public static LinkCache getInstance() {
		return SingletonHelper.INSTANCE;
	}
	private static class SingletonHelper {
		private static final LinkCache INSTANCE = new LinkCache();
	}
	private LinkCache() {
		this.hAPI = HybridAPI.getInstance();
		this.updateListeners = new UpdateListeners();

		this.linkTargets = new HashMap<>();

		this.isLink = new HashSet<>();
		this.isDir = new HashSet<>();
		this.isNormal = new HashSet<>();


		//Whenever any file we have cached is changed, update our data
		fileChangeListener = uuid -> {
			if(linkTargets.containsKey(uuid)) {
				linkTargets.remove(uuid);
				updateListeners.notifyDataChanged(uuid);
			}
		};
		hAPI.addListener(fileChangeListener);
	}


	public boolean isLink(UUID fileUID) throws FileNotFoundException, ConnectException {
		if(isLink.contains(fileUID))
			return true;
		if(isDir.contains(fileUID))
			return false;
		if(isNormal.contains(fileUID))
			return false;

		HFile fileProps = hAPI.getFileProps(fileUID);
		if(fileProps.islink)
			isLink.add(fileUID);
		else if(fileProps.isdir)
			isDir.add(fileUID);
		else
			isNormal.add(fileUID);

		return fileProps.islink;
	}
	public boolean isDir(UUID fileUID) throws FileNotFoundException, ConnectException {
		isLink(fileUID);	//Get the above method to do the caching
		return isDir.contains(fileUID);
	}


	public static boolean isLinkEnd(ListItem item) {
		return item.type.equals(ListItem.ListItemType.LINKEND);
	}


	@NonNull
	public LinkTarget getLinkTarget(UUID fileUID) throws ContentsNotFoundException, FileNotFoundException, ConnectException {
		if(!isLink(fileUID))
			throw new IllegalArgumentException("File is not a link!");

		//If we have the target cached, just use that
		if(linkTargets.containsKey(fileUID))
			return linkTargets.get(fileUID);

		//Grab the target from the repository and cache it
		LinkTarget target = readLink(fileUID);
		linkTargets.put(fileUID, target);
		return target;
	}


	@NonNull
	public Pair<Uri, String> getContentInfo(UUID uuid) throws ContentsNotFoundException, FileNotFoundException, ConnectException {
		//If the item is a link, the content uri is accessed differently
		LinkCache.LinkTarget target = getFinalTarget(uuid);


		//If the target is null, the item is not a link. Get the content uri from the fileUID's content
		if (target == null) {
			return hAPI.getFileContent(uuid);
		}
		//If the target is internal, get the content uri from that fileUID's content
		else if (target instanceof LinkCache.InternalTarget) {
			return hAPI.getFileContent(((LinkCache.InternalTarget) target).getFileUID());
		}
		//If the target is external, get the content uri from the target
		else {//if(target instanceof LinkCache.ExternalTarget) {
			Uri content = ((LinkCache.ExternalTarget) target).getUri();
			return new Pair<>(content, content.toString());
		}
	}


	//---------------------------------------------------------------------------------------------


	@Nullable
	public LinkTarget getFinalTarget(UUID linkUID) {
		try {
			UUID finalLink = getFinalLink(linkUID);
			return getLinkTarget(finalLink);
		}
		catch (Exception e) {
			//If this fails for any reason, just pretend the link is broken
			return null;
		}
	}



	//UUID could be a normal file, or it could be a link.
	//If its a link, we want to follow it down a potential link chain until the final target
	//Thanks Sophia for the naming suggestion
	@NonNull
	public UUID getFinalLink(UUID bartholomew) {
		try {
			if(!isLink(bartholomew))
				throw new IllegalArgumentException("File is not a link!");

			LinkTarget newTarget = getLinkTarget(bartholomew);

			//If the final link in the chain points to an external file, just return the last item
			if(newTarget instanceof ExternalTarget)
				return bartholomew;

			InternalTarget internalTarget = (InternalTarget) newTarget;

			//If the internal target is a link, follow it
			if(isLink(internalTarget.fileUID))
				return getFinalLink(internalTarget.fileUID);
				//Else, return this link
			else
				return bartholomew;
		}
		catch (FileNotFoundException | ConnectException e) {
			//If the file isn't found or we just can't reach it, just pretend the link is broken
			return bartholomew;
		}
		catch (ContentsNotFoundException e) {
			//If we can't find the link's contents, just pretend the link is broken
			return bartholomew;
		}
	}


	//File can also be not-found
	public UUID getLinkDir(UUID fileUID) {
		try {
			//If the item is a link, follow that link
			if(isLink(fileUID)) {
				LinkTarget target = LinkCache.getInstance().getFinalTarget(fileUID);

				//If the link is to an internal file...
				if(target instanceof InternalTarget) {
					InternalTarget internalTarget = (InternalTarget) target;

					//If the link is to a directory, use the target fileUID
					if(isDir(internalTarget.getFileUID()))
						fileUID = internalTarget.getFileUID();
						//If the link is to a single file (like an image/divider), use the target parentUID
					else
						fileUID = internalTarget.getParentUID();
				}
			}
		} catch (FileNotFoundException | ConnectException e) {
			//Do nothing
		}

		return fileUID;
	}


	//---------------------------------------------------------------------------------------------


	private static LinkTarget readLink(UUID linkUID) throws ContentsNotFoundException, FileNotFoundException, ConnectException {
		Uri uri = HybridAPI.getInstance().getFileContent(linkUID).first;

		InputStream in = null;
		try {
			//If the file can be opened using ContentResolver, do that. Otherwise, open using URL's openStream
			try {
				in = MyApplication.getAppContext().getContentResolver().openInputStream(uri);
			} catch (FileNotFoundException e) {
				in = new URL(uri.toString()).openStream();
			}

			try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
				String firstLine = reader.readLine();

				//TODO Handle empty links and malformed link targets.
				Uri linkUri = Uri.parse(firstLine);

				//If the uri scheme starts with "gallery", it's an internal link
				//TODO We're currently doing "gallery:/" instead of "gallery://"
				if ("gallery".equals(linkUri.getScheme())) {
					String[] uuidParts = linkUri.getPath().split("/");
					UUID dirUID = UUID.fromString(uuidParts[1]);
					UUID fileUID = UUID.fromString(uuidParts[2]);

					return new InternalTarget(dirUID, fileUID);
				}
				//Otherwise this points to somewhere on the internet
				else {
					return new ExternalTarget(Uri.parse(firstLine));
				}
			}
		}
		catch (IOException e) { throw new RuntimeException(e); }
		finally {
			try {
				if(in != null) in.close();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}





	/*
	@NonNull
	public UUID getDirFromPath(@NonNull Path path) throws FileNotFoundException, NotDirectoryException {

		//Thanks Sophia for the naming suggestion
		UUID bartholomew = UUID.fromString(path.getFileName().toString());

		//If this is a link UUID, get the directory it points to
		while(dirLinkCache.containsKey(bartholomew))
			bartholomew = dirLinkCache.get(bartholomew);
		assert bartholomew != null;

		HFile dirProps = hAPI.getFileProps(bartholomew);
		if(!dirProps.isdir) throw new NotDirectoryException(bartholomew.toString());

		return bartholomew;
	}
	 */




	//---------------------------------------------------------------------------------------------

	public void addListener(@NonNull UpdateListener listener) {
		updateListeners.addListener(listener);
	}
	public void removeListener(@NonNull UpdateListener listener) {
		updateListeners.removeListener(listener);
	}

	private static class UpdateListeners {
		private final Set<UpdateListener> listeners = new HashSet<>();

		public void addListener(@NonNull UpdateListener listener) {
			listeners.add(listener);
		}
		public void removeListener(@NonNull UpdateListener listener) {
			listeners.remove(listener);
		}

		public void notifyDataChanged(@NonNull UUID uuid) {
			for(UpdateListener listener : listeners)
				listener.onDirContentsChanged(uuid);
		}
	}
	public interface UpdateListener {
		void onDirContentsChanged(UUID uuid);
	}


	//---------------------------------------------------------------------------------------------

	public interface LinkTarget {
		@NonNull
		Uri toUri();
	}


	public static class InternalTarget implements LinkTarget {
		@NonNull
		private final UUID parentUID;
		@NonNull
		private final UUID fileUID;

		public InternalTarget(@NonNull UUID parentUID, @NonNull UUID fileUID) {
			this.parentUID = parentUID;
			this.fileUID = fileUID;
		}
		@NonNull
		public UUID getParentUID() { return parentUID; }
		@NonNull
		public UUID getFileUID() { return fileUID; }

		@NonNull
		@Override
		public String toString() {
			return toUri().toString();
		}

		@NonNull
		public Uri toUri() {
			Uri.Builder builder = new Uri.Builder();
			builder.scheme("gallery").appendPath(parentUID.toString()).appendPath(fileUID.toString());
			return builder.build();
		}
	}


	public static class ExternalTarget implements LinkTarget {
		@NonNull
		private final Uri uri;

		public ExternalTarget(@NonNull Uri uri) {
			this.uri = uri;
		}
		@NonNull
		public Uri getUri() { return uri; }

		@NonNull
		@Override
		public String toString() {
			return toUri().toString();
		}

		@NonNull
		public Uri toUri() {
			return uri;
		}
	}
}
