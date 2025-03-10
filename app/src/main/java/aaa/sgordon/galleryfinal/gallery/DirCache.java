package aaa.sgordon.galleryfinal.gallery;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.apache.commons.io.FilenameUtils;

import java.io.FileNotFoundException;
import java.net.ConnectException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridAPI;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridListeners;
import aaa.sgordon.galleryfinal.repository.hybrid.types.HFile;
import aaa.sgordon.galleryfinal.utilities.LinkUtilities;


//WARNING: This object should live as long as the Application is running. Keep in Activity ViewModel.
public class DirCache {
	private final static String TAG = "Gal.DirCache";
	private final HybridAPI hAPI;

	private final Map<UUID, List<Pair<UUID, String>>> cDirContents;		//Holds directory contents
	private final Map<UUID, LinkUtilities.LinkTarget> cLinkTarget;		//Holds link targets

	//Per directory, holds links and link targets. This is for use with the listener to refresh the dir on item change
	private final Map<UUID, Set<UUID>> cDirChildTargets;


	//In our current implementation, files can't change their nature (directory/link).
	//Therefore, we can append only and not worry about concurrency or conflicting values with these.
	private final Set<UUID> isDirCache;
	private final Set<UUID> isLinkCache;

	private final HybridListeners.FileChangeListener fileChangeListener;
	private final UpdateListeners updateListeners;


	public static DirCache getInstance() {
		return SingletonHelper.INSTANCE;
	}
	private static class SingletonHelper {
		private static final DirCache INSTANCE = new DirCache();
	}
	private DirCache() {
		this.hAPI = HybridAPI.getInstance();

		this.cDirContents = new HashMap<>();
		this.cLinkTarget = new HashMap<>();
		this.cDirChildTargets = new HashMap<>();

		this.isDirCache = new HashSet<>();
		this.isLinkCache = new HashSet<>();

		this.updateListeners = new UpdateListeners();


		//Whenever any file we have cached is changed, update our data
		fileChangeListener = uuid -> {
			//If we have this directory cached...
			if(cDirContents.containsKey(uuid)) {
				//Remove the cached contents
				cDirContents.remove(uuid);
				//Since we need to re-cache anyway, remove the list of dependencies
				cDirChildTargets.remove(uuid);

				updateListeners.notifyDataChanged(uuid);
			}

			//For each cached directory, look at each link it depends on...
			for(Map.Entry<UUID, Set<UUID>> entry : cDirChildTargets.entrySet()) {
				UUID dirUID = entry.getKey();
				Set<UUID> dependencies = entry.getValue();

				//If the dir relies on this file, notify listeners
				if(dependencies.contains(uuid)) {
					if(cDirContents.containsKey(dirUID))
						updateListeners.notifyDataChanged(dirUID);
				}
			}
		};
		hAPI.addListener(fileChangeListener);



		//Loop importing items for testing notifications
		HandlerThread handlerThread = new HandlerThread("BackgroundThread");
		handlerThread.start();
		Looper looper = handlerThread.getLooper();
		Handler handler = new Handler(looper);
		Runnable runnable = new Runnable() {
			public void run() {
				//Get a random fileUID from our list
				Random random = new Random();
				int randomIndex = random.nextInt(cDirContents.size());
				UUID randomDirUID = (UUID) cDirContents.keySet().toArray()[randomIndex];

				//randomDirUID = currDirUID;

				//Import to that directory
				DirSampleData.fakeImportFiles(randomDirUID, 2);

				//Do it again in a few seconds
				handler.postDelayed(this, 3000);
			}
		};
		//handler.postDelayed(runnable, 3000);
	}

	public boolean isDir(UUID fileUID) {
		return isDirCache.contains(fileUID);
	}
	public boolean isLink(UUID fileUID) {
		return isLinkCache.contains(fileUID);
	}

	public void addListener(@NonNull UpdateListener listener, @NonNull UUID dirUID) {
		updateListeners.addListener(listener, dirUID);
	}
	public void removeListener(@NonNull UpdateListener listener) {
		updateListeners.removeListener(listener);
	}











	//For each file
	//Check if link
	//Follow link
	//	Read link
	//	Follow link
	//Given link target, check link target type and name
	//Decide what to do


	//Get file props,
	// Is link? Send it off
	//  Get target, return List<UUID>
	//  Target could be single item, or could be dir or divider




	//Recursively read directory contents, drilling down only through links to directories
	public List<Pair<Path, String>> getDirList(UUID dirUID) throws ContentsNotFoundException, FileNotFoundException, ConnectException {
		List<Pair<UUID, String>> contents = readDir(dirUID);
		return traverseContents(contents, new HashSet<>(), Paths.get(dirUID.toString()));
	}

	private List<Pair<Path, String>> traverseContents(List<Pair<UUID, String>> contents, Set<UUID> visited, Path currPath) {
		//For each file in the directory...
		List<Pair<Path, String>> files = new ArrayList<>();
		for (Pair<UUID, String> entry : contents) {
			UUID fileUID = entry.first;

			//Add it to the current directory's list of files
			Path thisFilePath = currPath.resolve(entry.first.toString());
			files.add(new Pair<>(thisFilePath, entry.second));


			try {
				HFile fileProps = hAPI.getFileProps(fileUID);

				//If this isn't a link, we don't care
				if (!fileProps.islink)
					continue;

				Set<UUID> localVisited = new HashSet<>(visited);
				files.addAll(traverseLink(fileUID, localVisited, thisFilePath));
			}
			catch (FileNotFoundException | ConnectException e) {
				//If the file isn't found or we just can't reach it, skip it
				continue;
			}
			catch (ContentsNotFoundException e) {
				//If we can't find the link's contents, this is an issue, but skip it
				continue;
			}
		}

		return files;
	}


	//When displaying links to dividers, we want to show all items after the divider up until the next divider (or dir end)
	private List<Pair<UUID, String>> sublistDividerItems(List<Pair<UUID, String>> parentContents, UUID dividerUID)
			throws ContentsNotFoundException, FileNotFoundException, ConnectException {

		//Find the index of the divider within the parent directory contents
		int dividerIndex = -1;
		for (int i = 0; i < parentContents.size(); i++) {
			if (parentContents.get(i).first.equals(dividerUID)) {
				dividerIndex = i;
				break;
			}
		}

		//If no divider is found, the link is broken. We should return an empty list.
		//However, This should never happen, since we check this in traverseLink before calling this method.
		if (dividerIndex == -1) throw new RuntimeException();


		//Find the next divider after the given dividerUID, or the end of the list
		int nextDividerIndex = dividerIndex + 1;
		while (nextDividerIndex < parentContents.size()) {
			if(!FilenameUtils.getExtension( parentContents.get(nextDividerIndex).second ).equals("div"))
				break;

			nextDividerIndex++;
		}


		//Return the items between the given divider and the next divider (or end of list)
		return parentContents.subList(dividerIndex + 1, nextDividerIndex);
	}






	//Read link contents to get target
	//Find if item is directory
	//  If directory, pass to traverse
	//Find if item is link
	//  If link, pass to traverseLink
	//If item is not dir or link, pass to normal item handling
	//  If link or normal item, just pass back single item
	//  If divider, do a sort of traverse


	//If the link is a directory, call traverse and add an end
	//If the link is a divider, grab the correct subset of files and traverse on that
	//If the link is a single item (external, single image), we are done here. Do nothing.



	@NonNull
	private List<Pair<Path, String>> traverseLink(UUID linkUID, Set<UUID> visited, Path currPath) throws ContentsNotFoundException, FileNotFoundException, ConnectException {
		if(!visited.add(linkUID))	//Prevent cycles
			return new ArrayList<>();


		//Read the link contents into a target
		LinkUtilities.LinkTarget linkTarget = LinkUtilities.readLink(linkUID);

		//If the target is external, we're done here
		if(linkTarget instanceof LinkUtilities.ExternalTarget)
			return new ArrayList<>();


		//If the target is internal, we need more info
		LinkUtilities.InternalTarget target = (LinkUtilities.InternalTarget) linkTarget;
		Set<UUID> localVisited = new HashSet<>(visited);

		try {
			HFile fileProps = hAPI.getFileProps(target.getFileUID());

			//If the link target is another link, continue traversing down the tunnel
			if(fileProps.islink) {
				return traverseLink(target.getFileUID(), localVisited, currPath);
			}
			//If the link is a directory, traverse it and append a link end
			else if(fileProps.isdir) {
				//Traverse the directory
				List<Pair<UUID, String>> contents = readDir(target.getFileUID());
				List<Pair<Path, String>> files = traverseContents(contents, localVisited, currPath);

				//Ad a bookend for the link
				Path linkEnd = currPath.resolve("END");
				files.add(new Pair<>(linkEnd, "END"));

				return files;
			}
			//If the link target is neither a link or a dir, we need to check if it's a 'divider' (by file extension)
			else {
				//Find the first item in the target's parent directory that matches the target UUID
				List<Pair<UUID, String>> targetParentContents = readDir(target.getParentUID());
				Optional<Pair<UUID, String>> targetItem = targetParentContents.stream()
						.filter(pair -> pair.first.equals( target.getFileUID() )).findFirst();

				//If the target is no longer in the parent directory, the link is broken and we're done here
				if(!targetItem.isPresent()) return new ArrayList<>();

				//If the target does not refer to a divider item, we're done here
				String targetName = targetItem.get().second;
				if(!FilenameUtils.getExtension(targetName).equals("div"))
					return new ArrayList<>();


				//Since the target is a divider item, we need to traverse only the "divider's items"
				List<Pair<UUID, String>> dividerItems = sublistDividerItems(targetParentContents, target.getFileUID());
				return traverseContents(dividerItems, localVisited, currPath);
			}
		}
		catch (FileNotFoundException | ConnectException e) {
			//If the file isn't found or we just can't reach it, skip it
			return new ArrayList<>();
		}
		catch (ContentsNotFoundException e) {
			//If we can't find the link's contents, this is an issue, but skip it
			return new ArrayList<>();
		}
	}
























	private List<Pair<UUID, String>> readDir(@NonNull UUID dirUID) throws ContentsNotFoundException, FileNotFoundException, ConnectException {
		//If we have the directory contents cached, just return that
		if(cDirContents.containsKey(dirUID))
			return cDirContents.get(dirUID);

		List<Pair<UUID, String>> dirList = DirUtilities.readDir(dirUID);
		cDirContents.put(dirUID, dirList);
		return dirList;
	}


	//Only use with directory links, not normal links
	private UUID readDirLink(UUID linkUID) throws ContentsNotFoundException, FileNotFoundException, ConnectException {
		//If we have the link destination directory cached, just return that
		if(cLinkTarget.containsKey(linkUID))
			return cLinkTarget.get(linkUID);

		UUID linkDestination = DirUtilities.readDirLink(linkUID);
		cLinkTarget.put(linkUID, linkDestination);
		return linkDestination;
	}



	//UUID could be a dir, or it could be a link to a dir
	//Thanks Sophia for the naming suggestion
	@Nullable
	public UUID resolveDirUID(UUID bartholomew) throws FileNotFoundException, NotDirectoryException, ConnectException {
		HFile fileProps = hAPI.getFileProps(bartholomew);
		if(!fileProps.isdir) throw new NotDirectoryException(bartholomew.toString());

		try {
			while(fileProps.islink) {
				bartholomew = readDirLink(bartholomew);
				fileProps = hAPI.getFileProps(bartholomew);
			}
		} catch (ContentsNotFoundException | FileNotFoundException e) {
			//If we can't follow the link, assume it's broken
			return null;
		}

		return bartholomew;
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



	private static class UpdateListeners {
		private final Map<UpdateListener, UUID> listeners = new HashMap<>();

		public void addListener(@NonNull UpdateListener listener, @NonNull UUID dirUID) {
			listeners.put(listener, dirUID);
		}
		public void removeListener(@NonNull UpdateListener listener) {
			listeners.remove(listener);
		}

		public void notifyDataChanged(@NonNull UUID uuid) {
			for(Map.Entry<UpdateListener, UUID> entry : listeners.entrySet()) {
				UpdateListener listener = entry.getKey();
				UUID dirUID = entry.getValue();

				if(dirUID.equals(uuid))
					listener.onDirContentsChanged(uuid);
			}
		}
	}
	public interface UpdateListener {
		void onDirContentsChanged(UUID uuid);
	}
}
