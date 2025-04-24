package aaa.sgordon.galleryfinal.gallery;

import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;

import com.onegravity.rteditor.utils.io.FilenameUtils;

import java.io.FileNotFoundException;
import java.net.ConnectException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import aaa.sgordon.galleryfinal.repository.gallery.caches.DirCache;
import aaa.sgordon.galleryfinal.repository.gallery.caches.LinkCache;
import aaa.sgordon.galleryfinal.repository.gallery.components.link.ExternalTarget;
import aaa.sgordon.galleryfinal.repository.gallery.components.link.InternalTarget;
import aaa.sgordon.galleryfinal.repository.gallery.components.link.LinkTarget;
import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;

public class FileExplorer {
	private final static Executor executor = Executors.newSingleThreadExecutor();
	private final static MutableLiveData<List<UUID>> fullList = new MutableLiveData<>();

	private final static DirCache dirCache = DirCache.getInstance();
	private final static LinkCache linkCache = LinkCache.getInstance();


	//Setup listeners for link and dir caches
	//	Upon listeners, re-fetch list
	//
	//Grab first dir
	//Loop through, look for links
	//	If LinkCache has link, recurse
	//	If LinkCache does not have link, post fetch on other thread and leave


	public void traverseDir(UUID dirUID) throws ContentsNotFoundException, FileNotFoundException, ConnectException {
		List<DirItem> dirContents = dirCache.getDirContents(dirUID);

	}

	private static List<ListItem> traverseDirectory(List<DirItem> contents, Set<UUID> visited, Path currPath) {
		UUID parentUID = UUID.fromString(currPath.getFileName().toString());

		//For each file in the directory...
		List<ListItem> files = new ArrayList<>();
		for (DirItem entry : contents) {
			UUID fileUID = entry.fileUID;

			//Add it to the current directory's list of files
			Path thisFilePath = currPath.resolve(entry.fileUID.toString());
			boolean isTrashed = FilenameUtils.getExtension(entry.name).startsWith("trashed_");

			//If this isn't a link, we don't need to do anything special
			if(!entry.isLink) {
				//Make normal files
				continue;
			}







		}
		throw new RuntimeException("Stub!");
	}



	private static List<ListItem> traverseLink(UUID linkUID, ListItem topLink, Set<UUID> visited, Path currPath) {
		LinkTarget linkTarget = linkCache.getCachedTarget(linkUID);

		//If the target is not cached...
		if(linkTarget == null) {
			//Add link to loading item to file list

			//Post a fetch and leave
			executor.execute(() -> {
				try { linkCache.getLinkTarget(linkUID); }
				catch (ContentsNotFoundException | FileNotFoundException | ConnectException ignored) {}
			});

			//Return a 'loading' link
			return List.of(new ListItem.Builder(topLink).setType(ListItem.ListItemType.LINKUNREACHABLE).build());
		}
		//Now that we know the target is cached...



		//If the target is an External link...
		if(linkTarget instanceof ExternalTarget)
			return List.of(new ListItem.Builder(topLink).setType(ListItem.ListItemType.LINKEXTERNAL).build());



		//If the target is an Internal link...
		InternalTarget target = (InternalTarget) linkTarget;

		//TODO SubLink additions

		if(!visited.add(target.fileUID))	//Prevent cycles
			return List.of(new ListItem.Builder(topLink).setType(ListItem.ListItemType.LINKCYCLE).build());



		List<DirItem> parentContents = dirCache.getCachedContents(target.parentUID);

		//If parent is not cached, post a fetch and leave
		if(parentContents == null) {
			executor.execute(() -> {
				try { dirCache.getDirContents(target.parentUID); }
				catch (ContentsNotFoundException | FileNotFoundException | ConnectException ignored) {}
			});

			//Return a 'loading' link
			return List.of(new ListItem.Builder(topLink).setType(ListItem.ListItemType.LINKUNREACHABLE).build());
		}
		//Now that we know the directory contents are cached...

		boolean exists = false;
		for(DirItem item : parentContents) {
			//If we find the targeted file...
			if(item.fileUID.equals( target.fileUID )) {
				//Make sure it's not trashed. If it is, mark as broken
				exists = FilenameUtils.getExtension(item.name).startsWith("trashed_");
				break;
			}
		}
		//If the targeted file doesn't exist in its parent directory, mark the link as broken
		if(!exists)
			return List.of(new ListItem.Builder(topLink).setType(ListItem.ListItemType.LINKBROKEN).build());




		List<ListItem> files = new ArrayList<>();
		switch (target.type) {
			//If the link target is a normal item, end things here
			case NORMAL:
				return List.of(new ListItem.Builder(topLink).setType(ListItem.ListItemType.LINKSINGLE).build());

			//If the link target is another link, continue traversing down the tunnel
			case LINK:
				return traverseLink(target.fileUID, topLink, new HashSet<>(visited), currPath);

			//If the link target is a directory, we want to display all directory contents, including any links contained within
			case DIRECTORY:
				List<DirItem> targetDirContents = dirCache.getCachedContents(target.fileUID);

				//If not cached, post a fetch and leave
				if(targetDirContents == null) {
					executor.execute(() -> {
						try { dirCache.getDirContents(target.fileUID); }
						catch (ContentsNotFoundException | FileNotFoundException | ConnectException ignored) {}
					});

					//Return a 'loading' link
					return List.of(new ListItem.Builder(topLink).setType(ListItem.ListItemType.LINKUNREACHABLE).build());
				}
				//Now that we know the directory contents are cached...


				//Add the top link as a starter
				files.add(new ListItem.Builder(topLink).setType(ListItem.ListItemType.LINKDIRECTORY).build());

				//Traverse the directory
				files.addAll(traverseDirectory(targetDirContents, new HashSet<>(visited), currPath));

				//Add a link end
				files.add(new ListItem.Builder(topLink).setName("END of "+topLink.name).setType(ListItem.ListItemType.LINKEND).build());

				return files;

			//If the link target is a divider item, we want to display only the "divider's items", including any links contained within
			case DIVIDER:
				//Get only the divider's items from the parent
				List<DirItem> dividerItems = sublistDividerItems(parentContents, target.fileUID);
				if(dividerItems == null)
					return List.of(new ListItem.Builder(topLink).setType(ListItem.ListItemType.LINKBROKEN).build());

				//Add the top link as a starter
				files.add(new ListItem.Builder(topLink).setType(ListItem.ListItemType.LINKDIVIDER).build());

				//Traverse the divider's segment of the directory
				files.addAll(traverseDirectory(dividerItems, new HashSet<>(visited), currPath));

				//Add a link end
				files.add(new ListItem.Builder(topLink).setName("END of "+topLink.name).setType(ListItem.ListItemType.LINKEND).build());

				return files;

			default:
				throw new RuntimeException("Invalid link target type: "+target.type);
		}
	}


	//When displaying links to dividers, we want to show all items after the divider up until the next divider (or dir end)
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


		//Find the next divider after the given dividerUID, or the end of the list
		//Note: Divider's only stop at other '.div' files. This includes skipping links unless they're named 'Something.div'
		int nextDividerIndex;
		for(nextDividerIndex = dividerIndex+1; nextDividerIndex < parentContents.size(); nextDividerIndex++) {
			if(FilenameUtils.getExtension( parentContents.get(nextDividerIndex).name ).equals("div"))
				break;
		}

		//Return the items between the given divider and the next divider (or end of list)
		return parentContents.subList(dividerIndex + 1, nextDividerIndex);
	}
}
