package aaa.sgordon.galleryfinal.repository.gallery;

import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;

import com.onegravity.rteditor.utils.io.FilenameUtils;

import java.io.FileNotFoundException;
import java.net.ConnectException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import aaa.sgordon.galleryfinal.gallery.DirItem;
import aaa.sgordon.galleryfinal.gallery.ListItem;
import aaa.sgordon.galleryfinal.repository.gallery.caches.DirCache;
import aaa.sgordon.galleryfinal.repository.gallery.caches.LinkCache;
import aaa.sgordon.galleryfinal.repository.gallery.components.link.ExternalTarget;
import aaa.sgordon.galleryfinal.repository.gallery.components.link.InternalTarget;
import aaa.sgordon.galleryfinal.repository.gallery.components.link.LinkTarget;
import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;

//TODO This isn't particularly thread-safe
public class FileExplorer {
	private final static String TAG = "Gal.Traversal";

	//private final static Executor executor = Executors.newSingleThreadExecutor();
	private final static DirCache dirCache = DirCache.getInstance();
	private final static LinkCache linkCache = LinkCache.getInstance();

	private final DirCache.UpdateListener dirListener;
	private final LinkCache.UpdateListener linkListener;

	private Thread queuedUpdateThread;

	public UUID currentDirUID;
	public final MutableLiveData<List<ListItem>> list = new MutableLiveData<>();
	//Holds subordinate links and link targets the current dir depends on.
	//This is for use with the listener to refresh the dir on subordinate item updates
	private final Set<UUID> linkDependencies = new HashSet<>();
	private final Set<UUID> dirDependencies = new HashSet<>();


	public FileExplorer() {
		list.setValue(new ArrayList<>());

		dirListener = uuid -> {
			//If this update isn't for us, ignore it
			if(!dirDependencies.contains(uuid))
				return;

			refreshList();
		};
		dirCache.addListener(dirListener);

		linkListener = uuid -> {
			//If this update isn't for us, ignore it
			if(!linkDependencies.contains(uuid))
				return;

			refreshList();
		};
		linkCache.addListener(linkListener);
	}
	public void destroy() {
		dirCache.removeListener(dirListener);
		linkCache.removeListener(linkListener);
	}


	public void refreshList() {
		//This setup only queues a list update if there is not already one queued
		//It will queue one if there is an in-progress update
		if(queuedUpdateThread == null) {
			Thread thread = new Thread(() -> {
				//During a move operation (usually from reorder), the list will update after every directory change
				//This causes animation flickers, so we want to delay the visual list update just a touch
				try { Thread.sleep(50); }
				catch (InterruptedException e) { throw new RuntimeException(e); }
				this.queuedUpdateThread = null;

				try { traverseDir(currentDirUID); }
				catch (ContentsNotFoundException | FileNotFoundException | ConnectException ignored) {}
			});
			thread.start();
			queuedUpdateThread = thread;
		}
	}






	//Setup listeners for link and dir caches
	//	Upon listeners, re-fetch list
	//
	//Grab first dir
	//Loop through, look for links
	//	If LinkCache has link, recurse
	//	If LinkCache does not have link, post fetch on other thread and leave


	boolean printed = true;
	public void traverseDir(UUID dirUID) throws ContentsNotFoundException, FileNotFoundException, ConnectException {
		linkDependencies.clear();
		dirDependencies.clear();
		currentDirUID = dirUID;

		List<DirItem> dirContents = dirCache.getDirContents(dirUID);
		Set<UUID> visited = new HashSet<>();
		visited.add(dirUID);
		List<ListItem> newList = traverseDirectory(dirUID, dirContents, visited, Paths.get(dirUID.toString()));

		if(!printed) {
			System.out.println("NewFiles: ");
			for(ListItem item : newList) {
				//Print only the first 8 digits of the UUID
				Path printPath = Paths.get("");
				for(Path path : item.pathFromRoot) {
					printPath = printPath.resolve(path.getFileName().toString().substring(0, 8));
				}
				System.out.println(printPath+"   "+item.type+" "+item.getRawName());
			}

			printed = true;
		}

		list.postValue(newList);
	}

	private List<ListItem> traverseDirectory(UUID dirUID, List<DirItem> contents, Set<UUID> visited, Path currPath) {
		dirDependencies.add(dirUID);


		//For each file in the directory...
		List<ListItem> files = new ArrayList<>();
		for (DirItem entry : contents) {
			UUID fileUID = entry.fileUID;

			//Add it to the current directory's list of files
			Path thisFilePath = currPath.resolve(entry.fileUID.toString());

			//If this isn't a link, we don't need to do anything special
			if(!entry.isLink) {
				if(entry.isDir)
					files.add(new ListItem(fileUID, dirUID, entry.isDir, entry.isLink, entry.name, thisFilePath,
							ListItem.Type.DIRECTORY));
				else {
					//If the file extension is ".div", this is a divider
					if(FilenameUtils.getExtension(entry.name).equals("div")) {
						files.add(new ListItem(fileUID, dirUID, entry.isDir, entry.isLink, entry.name, thisFilePath,
								ListItem.Type.DIVIDER));
					}
					//Otherwise, this is a normal file
					else {
						files.add(new ListItem(fileUID, dirUID, entry.isDir, entry.isLink, entry.name, thisFilePath,
								ListItem.Type.NORMAL));
					}
				}
				continue;
			}


			try {
				//Traverse the link
				ListItem topLink = new ListItem(fileUID, dirUID, entry.isDir, entry.isLink, entry.name, thisFilePath,
						ListItem.Type.LINKSINGLE);
				files.addAll(traverseLink(fileUID, topLink, new HashSet<>(visited), thisFilePath));
			}
			catch (ContentsNotFoundException e) {
				//If we can't find the link's contents, this is an issue, link is broken
				files.add(new ListItem(fileUID, dirUID, entry.isDir, entry.isLink, entry.name, thisFilePath,
						ListItem.Type.LINKBROKEN));
			}
			catch (FileNotFoundException | ConnectException e) {
				//If the file isn't found (file may be local on another device) or we just can't reach it, treat it as unreachable
				files.add(new ListItem(fileUID, dirUID, entry.isDir, entry.isLink, entry.name, thisFilePath,
						ListItem.Type.UNREACHABLE));
			}
		}

		return files;
	}



	private List<ListItem> traverseLink(UUID linkUID, ListItem topLink, Set<UUID> visited, Path currPath)
			throws ContentsNotFoundException, FileNotFoundException, ConnectException {
		linkDependencies.add(linkUID);


		//Note: This is potentially a call to server, and skips the below section
		LinkTarget linkTarget = linkCache.getLinkTarget(linkUID);
		/*
		LinkTarget linkTarget = linkCache.getCachedTarget(linkUID);

		//If the target is not cached...
		if(linkTarget == null) {
			//Post a fetch and leave
			executor.execute(() -> {
				try { linkCache.getLinkTarget(linkUID); }
				catch (ContentsNotFoundException | FileNotFoundException | ConnectException ignored) {}
			});

			//Return a 'loading' link
			return List.of(new ListItem.Builder(topLink).setType(ListItem.Type.LINKUNREACHABLE).build());
		}
		//Now that we know the target is cached...
		 */



		//If the target is an External link...
		if(linkTarget instanceof ExternalTarget)
			return List.of(new ListItem.Builder(topLink).setType(ListItem.Type.LINKEXTERNAL).build());



		//If the target is an Internal link...
		InternalTarget target = (InternalTarget) linkTarget;

		if(!visited.add(target.fileUID))	//Prevent cycles
			return List.of(new ListItem.Builder(topLink).setType(ListItem.Type.LINKCYCLE).build());


		//Note: This is potentially a call to server, and skips the below section
		List<DirItem> parentContents = dirCache.getDirContents(target.parentUID);
		/*
		List<DirItem> parentContents = dirCache.getCachedContents(target.parentUID);

		//If parent is not cached, post a fetch and leave
		if(parentContents == null) {
			executor.execute(() -> {
				try { dirCache.getDirContents(target.parentUID); }
				catch (ContentsNotFoundException | FileNotFoundException | ConnectException ignored) {}
			});

			//Return a 'loading' link
			return List.of(new ListItem.Builder(topLink).setType(ListItem.Type.LINKUNREACHABLE).build());
		}
		//Now that we know the directory contents are cached...
		 */

		DirItem targetItem = null;
		for(DirItem item : parentContents) {
			//If we find the targeted file...
			if(item.fileUID.equals( target.fileUID )) {
				//Make sure it's not trashed. If it is, mark as broken
				if(FilenameUtils.getExtension(item.name).startsWith("trashed_"))
					break;
				targetItem = item;
				break;
			}
		}
		//If the targeted file doesn't exist in its parent directory, mark the link as broken
		if(targetItem == null)
			return List.of(new ListItem.Builder(topLink).setType(ListItem.Type.LINKBROKEN).build());



		//If the link target is another link, continue traversing down the tunnel
		if(targetItem.isLink) {
			return traverseLink(target.fileUID, topLink, new HashSet<>(visited), currPath);
		}
		//If the link target is a directory, we want to display all directory contents, including any links contained within
		else if(targetItem.isDir) {
			dirDependencies.add(target.fileUID);

			//Note: This is potentially a call to server, and skips the below section
			List<DirItem> targetDirContents = dirCache.getDirContents(target.fileUID);
				/*
				List<DirItem> targetDirContents = dirCache.getCachedContents(target.fileUID);

				//If not cached, post a fetch and leave
				if(targetDirContents == null) {
					executor.execute(() -> {
						try { dirCache.getDirContents(target.fileUID); }
						catch (ContentsNotFoundException | FileNotFoundException | ConnectException ignored) {}
					});

					//Return a 'loading' link
					return List.of(new ListItem.Builder(topLink).setType(ListItem.Type.LINKUNREACHABLE).build());
				}
				//Now that we know the directory contents are cached...
				 */


			//Add the top link as a starter
			List<ListItem> files = new ArrayList<>();
			files.add(new ListItem.Builder(topLink).setType(ListItem.Type.LINKDIRECTORY).build());

			//Traverse the directory
			files.addAll(traverseDirectory(topLink.fileUID, targetDirContents, new HashSet<>(visited), currPath));

			//Add a link end
			files.add(new ListItem.Builder(topLink).setRawName("END of "+topLink.getRawName()).setType(ListItem.Type.LINKEND).build());

			return files;
		}
		else {
			//If the link target is a divider item, we want to display only the "divider's items", including any links contained within
			if(FilenameUtils.getExtension(targetItem.name).equals("div")) {
				dirDependencies.add(target.parentUID);

				//Get only the divider's items from the parent
				List<DirItem> dividerItems = sublistDividerItems(parentContents, target.fileUID);
				if(dividerItems == null)
					return List.of(new ListItem.Builder(topLink).setType(ListItem.Type.LINKBROKEN).build());


				//Add the top link as a starter
				List<ListItem> files = new ArrayList<>();
				files.add(new ListItem.Builder(topLink).setType(ListItem.Type.LINKDIVIDER).build());

				//Traverse the divider's segment of the directory
				files.addAll(traverseDirectory(topLink.fileUID, dividerItems, new HashSet<>(visited), currPath));

				//Add a link end
				files.add(new ListItem.Builder(topLink).setRawName("END of "+topLink.getPrettyName()).setType(ListItem.Type.LINKEND).build());

				return files;
			}
			//If the link target is a normal item, end things here
			else {
				return List.of(new ListItem.Builder(topLink).setType(ListItem.Type.LINKSINGLE).build());
			}
		}
	}


	//When displaying links to dividers, we want to show all items after the divider up until the next link or divider (or dir end)
	@Nullable
	private static List<DirItem> sublistDividerItems(List<DirItem> parentContents, UUID dividerUID) {
		//Find the index of the divider within the parent directory contents
		int dividerIndex = -1;
		for (int i = 0; i < parentContents.size(); i++) {
			if (parentContents.get(i).fileUID.equals(dividerUID)) {
				dividerIndex = i;
				break;
			}
		}
		//If no divider is found, the link is broken
		if (dividerIndex == -1) return null;


		//Find the next link or divider after the given dividerUID, or the end of the list
		int nextIndex;
		for(nextIndex = dividerIndex+1; nextIndex < parentContents.size(); nextIndex++) {
			DirItem nextItem = parentContents.get(nextIndex);
			if(nextItem.isLink || FilenameUtils.getExtension( nextItem.name ).equals("div"))
				break;
		}

		//Return the items between the given divider and the next divider (or end of list)
		return parentContents.subList(dividerIndex + 1, nextIndex);
	}
}
