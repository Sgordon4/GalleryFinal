package aaa.sgordon.galleryfinal.gallery;

import android.util.Pair;

import androidx.annotation.NonNull;

import org.apache.commons.io.FilenameUtils;

import java.io.FileNotFoundException;
import java.net.ConnectException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import aaa.sgordon.galleryfinal.repository.caches.DirCache;
import aaa.sgordon.galleryfinal.repository.caches.LinkCache;
import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridAPI;
import aaa.sgordon.galleryfinal.repository.hybrid.types.HFile;

public class TraversalHelper {
	private final static DirCache dirCache = DirCache.getInstance();
	private final static LinkCache linkCache = LinkCache.getInstance();
	private final static HybridAPI hAPI = HybridAPI.getInstance();


	//Recursively read directory contents, drilling down through any links
	public static List<ListItem> traverseDir(UUID dirUID) throws ContentsNotFoundException, FileNotFoundException, ConnectException {
		List<Pair<UUID, String>> contents = dirCache.getDirContents(dirUID);
		return traverseContents(contents, Collections.singleton(dirUID), Paths.get(dirUID.toString()));
	}



	private static List<ListItem> traverseContents(List<Pair<UUID, String>> contents, Set<UUID> visited, Path currPath) {
		UUID parentUID = UUID.fromString(currPath.getFileName().toString());

		//For each file in the directory...
		List<ListItem> files = new ArrayList<>();
		for (Pair<UUID, String> entry : contents) {
			UUID fileUID = entry.first;

			//Add it to the current directory's list of files
			Path thisFilePath = currPath.resolve(entry.first.toString());

			boolean isTrashed = FilenameUtils.getExtension(entry.second).startsWith("trashed_");

			try {
				HFile fileProps = hAPI.getFileProps(fileUID);

				//If this isn't a link, we don't need to do anything special
				if (!fileProps.islink) {
					if(fileProps.isdir)
						files.add(new ListItem(thisFilePath, fileUID, parentUID, entry.second, true, false,
								ListItem.ListItemType.DIRECTORY));
					else
						files.add(new ListItem(thisFilePath, fileUID, parentUID, entry.second, false, false,
								ListItem.ListItemType.NORMAL));
					continue;
				}


				//Update any dependency lists for parent directories
				for(int i = 0; i < currPath.getNameCount(); i++) {
					UUID pathItem = UUID.fromString(currPath.getName(i).toString());
					dirCache.subLinks.putIfAbsent(pathItem, new HashSet<>());
					dirCache.subLinks.get(pathItem).add(fileUID);
				}


				//If this is a link but it's trashed, we don't want to follow it
				if(isTrashed) {
					files.add(new ListItem(currPath, fileUID, parentUID, entry.second, false, true,
							ListItem.ListItemType.LINKBROKEN));
					continue;
				}

				Set<UUID> localVisited = new HashSet<>(visited);
				if(!localVisited.add(fileUID)) {    //Prevent cycles
					files.add(new ListItem(currPath, fileUID, parentUID, entry.second, false, true,
							ListItem.ListItemType.LINKCYCLE));
					continue;
				}

				//Traverse the link
				ListItem topLink = new ListItem(currPath, fileUID, parentUID, entry.second, false, true, ListItem.ListItemType.LINKSINGLE);
				files.addAll(traverseLink(fileUID, topLink, localVisited, thisFilePath));
			}
			catch (FileNotFoundException | ConnectException e) {
				//If the file isn't found (file may be local on another device) or we just can't reach it, treat it as unreachable
				files.add(new ListItem(thisFilePath, fileUID, parentUID, entry.second, false, false,
						ListItem.ListItemType.UNREACHABLE));
				continue;
			}
			catch (ContentsNotFoundException e) {
				//If we're here, traverseLink threw this exception
				//If we can't find the link's contents, this is an issue, link is broken
				files.add(new ListItem(thisFilePath, fileUID, parentUID, entry.second, false, true,
						ListItem.ListItemType.LINKBROKEN));
				continue;
			}
		}

		return files;
	}



	//If the link is a directory, call traverse and add an end
	//If the link is a divider, grab the correct subset of files and traverse on that
	//If the link is a single item (external, single image), we are done here. Do nothing.



	@NonNull
	private static List<ListItem> traverseLink(UUID linkUID, ListItem topLink, Set<UUID> visited, Path currPath)
			throws ContentsNotFoundException, FileNotFoundException, ConnectException {
		//Read the link contents into a target
		LinkCache.LinkTarget linkTarget = LinkCache.getInstance().getLinkTarget(linkUID);

		//If the target is external, mark this as an external link and return
		if(linkTarget instanceof LinkCache.ExternalTarget) {
			return List.of(new ListItem(currPath, topLink.fileUID, topLink.parentUID, topLink.name, false, true,
					ListItem.ListItemType.LINKEXTERNAL));
		}



		//If the target is internal, we need more info
		LinkCache.InternalTarget target = (LinkCache.InternalTarget) linkTarget;

		//Update any dependency lists for parent directories
		for(int i = 0; i < currPath.getNameCount(); i++) {
			UUID pathItem = UUID.fromString(currPath.getName(i).toString());
			dirCache.subLinks.putIfAbsent(pathItem, new HashSet<>());
			dirCache.subLinks.get(pathItem).add(target.getFileUID());
		}

		if(!visited.add(target.getFileUID()))	//Prevent cycles
			return List.of(new ListItem(currPath, topLink.fileUID, topLink.parentUID, topLink.name, false, true,
					ListItem.ListItemType.LINKCYCLE));


		//If the target is trashed, we don't want to follow it
		List<Pair<UUID, String>> dirContents = dirCache.getDirContents(target.getParentUID());
		boolean exists = false;
		for(Pair<UUID, String> pair : dirContents) {
			//If we find the targeted file...
			if(pair.first.equals(target.getFileUID())) {
				exists = true;

				//...and it's a trashed file, mark as broken
				if(FilenameUtils.getExtension(pair.second).startsWith("trashed_"))
					return List.of(new ListItem(currPath, topLink.fileUID, topLink.parentUID, topLink.name, false, true,
							ListItem.ListItemType.LINKBROKEN));
			}
		}

		//If the targeted file doesn't exist in its parent directory, mark the link as broken
		if(!exists)
			return List.of(new ListItem(currPath, topLink.fileUID, topLink.parentUID, topLink.name, false, true,
					ListItem.ListItemType.LINKBROKEN));



		try {
			HFile fileProps = hAPI.getFileProps(target.getFileUID());

			//If the link target is another link, continue traversing down the tunnel
			if(fileProps.islink) {
				return traverseLink(target.getFileUID(), topLink, new HashSet<>(visited), currPath);
			}
			//If the link is a directory, traverse it and append a link end
			else if(fileProps.isdir) {
				List<ListItem> files = new ArrayList<>();
				files.add(new ListItem(currPath, topLink.fileUID, topLink.parentUID, topLink.name, false, true,
						ListItem.ListItemType.LINKDIRECTORY));

				//Traverse the directory
				List<Pair<UUID, String>> contents = dirCache.getDirContents(target.getFileUID());
				files.addAll(traverseContents(contents, new HashSet<>(visited), currPath));

				//Ad a bookend for the link
				files.add(new ListItem(currPath, topLink.fileUID, topLink.parentUID, "END of "+topLink.name, false, true,
						ListItem.ListItemType.LINKEND));

				return files;
			}
			//If the link target is neither a link or a dir, we need to check if it's a 'divider' (by file extension)
			else {
				List<ListItem> files = new ArrayList<>();

				//Find the first item in the target's parent directory that matches the target UUID
				List<Pair<UUID, String>> targetParentContents = dirCache.getDirContents(target.getParentUID());
				Optional<Pair<UUID, String>> targetItem = targetParentContents.stream()
						.filter(pair -> pair.first.equals( target.getFileUID() )).findFirst();

				//If the target is no longer in the parent directory, the link is broken and we're done here
				if(!targetItem.isPresent()) {
					return List.of(new ListItem(currPath, topLink.fileUID, topLink.parentUID, topLink.name, false, true,
							ListItem.ListItemType.LINKBROKEN));
				}

				//If the target does not refer to a divider item, this link points to a single item
				String targetName = targetItem.get().second;
				if(!FilenameUtils.getExtension(targetName).equals("div")) {
					return List.of(new ListItem(currPath, topLink.fileUID, topLink.parentUID, topLink.name, false, true,
							ListItem.ListItemType.LINKSINGLE));
				}


				//--------------------------------------------

				//If we're here, the target is a divider item.
				files.add(new ListItem(currPath, topLink.fileUID, topLink.parentUID, topLink.name, false, true,
						ListItem.ListItemType.LINKDIVIDER));

				//Since the target is a divider item, we need to traverse only the "divider's items"
				List<Pair<UUID, String>> dividerItems = sublistDividerItems(targetParentContents, target.getFileUID());
				files.addAll(traverseContents(dividerItems, new HashSet<>(visited), currPath));

				//Ad a bookend for the link
				files.add(new ListItem(currPath, topLink.fileUID, topLink.parentUID, "END of "+topLink.name, false, true,
						ListItem.ListItemType.LINKEND));

				return files;
			}
		}
		catch (FileNotFoundException e) {
			//If the target isn't found, mark as unreachable (file may be local on another device)
			return List.of(new ListItem(currPath, topLink.fileUID, topLink.parentUID, topLink.name, false, true,
					ListItem.ListItemType.LINKUNREACHABLE));
		}
		catch (ConnectException e) {
			//If we can't reach the target, mark as unreachable
			return List.of(new ListItem(currPath, topLink.fileUID, topLink.parentUID, topLink.name, false, true,
					ListItem.ListItemType.LINKUNREACHABLE));
		}
		catch (ContentsNotFoundException e) {
			//If we can't find the link's contents, this is an issue, link is broken
			return List.of(new ListItem(currPath, topLink.fileUID, topLink.parentUID, topLink.name, false, true,
					ListItem.ListItemType.LINKBROKEN));
		}
	}



	//When displaying links to dividers, we want to show all items after the divider up until the next divider (or dir end)
	private static List<Pair<UUID, String>> sublistDividerItems(List<Pair<UUID, String>> parentContents, UUID dividerUID)
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
}
