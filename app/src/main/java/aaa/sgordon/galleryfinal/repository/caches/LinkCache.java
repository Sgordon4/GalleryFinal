package aaa.sgordon.galleryfinal.repository.caches;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.URL;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridAPI;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridListeners;
import aaa.sgordon.galleryfinal.repository.hybrid.types.HFile;

//WARNING: This object should live as long as the Application is running. Keep in Activity ViewModel.
public class LinkCache {
	private final static String TAG = "Gal.LinkCache";
	private final HybridAPI hAPI;
	private final HybridListeners.FileChangeListener fileChangeListener;
	private final UpdateListeners updateListeners;

	private final Map<UUID, LinkTarget> linkTargets;


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


		//Whenever any file we have cached is changed, update our data
		fileChangeListener = uuid -> {
			if(linkTargets.containsKey(uuid)) {
				linkTargets.remove(uuid);
				updateListeners.notifyDataChanged(uuid);
			}
		};
		hAPI.addListener(fileChangeListener);
	}



	public LinkTarget getLinkTarget(UUID fileUID) throws ContentsNotFoundException, FileNotFoundException, ConnectException {
		//If we have the target cached, just use that
		if(linkTargets.containsKey(fileUID))
			return linkTargets.get(fileUID);

		//Grab the target from the repository and cache it
		LinkTarget target = readLink(fileUID);
		linkTargets.put(fileUID, target);
		return target;
	}

	private static LinkTarget readLink(UUID linkUID) throws ContentsNotFoundException, FileNotFoundException, ConnectException {
		Uri uri = HybridAPI.getInstance().getFileContent(linkUID).first;

		try (InputStream inputStream = new URL(uri.toString()).openStream();
			 BufferedReader reader = new BufferedReader( new InputStreamReader(inputStream) )) {
			String firstLine = reader.readLine();

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
		catch (IOException e) { throw new RuntimeException(e); }
	}



	@NonNull
	public UUID resolvePotentialLink(UUID fileUID) throws FileNotFoundException {
		LinkTarget target = followLinkChain(fileUID);

		if(target == null)
			return fileUID;
		if(target instanceof ExternalTarget)
			return fileUID;
		else
			return ((InternalTarget) target).getFileUID();
	}


	//UUID could be a normal file, or it could be a link.
	//If its a link, we want to follow it down a potential link chain until the final target
	//Thanks Sophia for the naming suggestion
	@Nullable
	public LinkTarget followLinkChain(UUID linkUID) {
		LinkTarget bartholemew = null;
		try {
			HFile fileProps = hAPI.getFileProps(linkUID);

			while (fileProps.islink) {
				LinkTarget newTarget = getLinkTarget(linkUID);

				//If the final link in the chain points to an external file, just return the last target
				if(newTarget instanceof ExternalTarget)
					break;

				bartholemew = newTarget;

				//If the link points to an internal file, follow it
				linkUID = ((InternalTarget) bartholemew).getFileUID();
				fileProps = hAPI.getFileProps(linkUID);
			}

			//Once we've reached the end of the link chain, return the last UUID
			return bartholemew;
		}
		catch (FileNotFoundException | ConnectException e) {
			//If the file isn't found or we just can't reach it, just pretend the link is broken
			return bartholemew;
		}
		catch (ContentsNotFoundException e) {
			//If we can't find the link's contents, just pretend the link is broken
			return bartholemew;
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
