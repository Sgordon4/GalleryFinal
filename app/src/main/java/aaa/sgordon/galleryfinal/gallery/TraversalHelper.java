package aaa.sgordon.galleryfinal.gallery;

import android.util.Pair;

import androidx.annotation.NonNull;

import org.apache.commons.io.FilenameUtils;

import java.io.FileNotFoundException;
import java.net.ConnectException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
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
	public static List<Pair<Path, String>> traverseDir(UUID dirUID) throws ContentsNotFoundException, FileNotFoundException, ConnectException {
		List<Pair<UUID, String>> contents = dirCache.getDirContents(dirUID);
		dirCache.markAsDir(dirUID);

		return traverseContents(contents, Collections.singleton(dirUID), Paths.get(dirUID.toString()));
	}



	private static List<Pair<Path, String>> traverseContents(List<Pair<UUID, String>> contents, Set<UUID> visited, Path currPath) {
		//For each file in the directory...
		List<Pair<Path, String>> files = new ArrayList<>();
		for (Pair<UUID, String> entry : contents) {
			UUID fileUID = entry.first;

			//Add it to the current directory's list of files
			Path thisFilePath = currPath.resolve(entry.first.toString());
			files.add(new Pair<>(thisFilePath, entry.second));


			try {
				HFile fileProps = hAPI.getFileProps(fileUID);
				if(fileProps.islink) linkCache.markAsLink(fileUID);
				else if(fileProps.isdir) dirCache.markAsDir(fileUID);

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



	//If the link is a directory, call traverse and add an end
	//If the link is a divider, grab the correct subset of files and traverse on that
	//If the link is a single item (external, single image), we are done here. Do nothing.

	@NonNull
	private static List<Pair<Path, String>> traverseLink(UUID linkUID, Set<UUID> visited, Path currPath) throws ContentsNotFoundException, FileNotFoundException, ConnectException {
		if(!visited.add(linkUID))	//Prevent cycles
			return new ArrayList<>();


		//Read the link contents into a target
		LinkCache.LinkTarget linkTarget = LinkCache.getInstance().getLinkTarget(linkUID);

		//If the target is external, we're done here
		if(linkTarget instanceof LinkCache.ExternalTarget)
			return new ArrayList<>();


		//If the target is internal, we need more info
		LinkCache.InternalTarget target = (LinkCache.InternalTarget) linkTarget;
		Set<UUID> localVisited = new HashSet<>(visited);

		try {
			HFile fileProps = hAPI.getFileProps(target.getFileUID());
			if(fileProps.islink) linkCache.markAsLink(linkUID);
			else if(fileProps.isdir) dirCache.markAsDir(linkUID);

			//If the link target is another link, continue traversing down the tunnel
			if(fileProps.islink) {
				return traverseLink(target.getFileUID(), localVisited, currPath);
			}
			//If the link is a directory, traverse it and append a link end
			else if(fileProps.isdir) {
				if(!visited.add(target.getFileUID()))	//Prevent cycles
					return new ArrayList<>();

				//Traverse the directory
				List<Pair<UUID, String>> contents = dirCache.getDirContents(target.getFileUID());
				List<Pair<Path, String>> files = traverseContents(contents, localVisited, currPath);

				//Ad a bookend for the link
				Path linkEnd = currPath.resolve("END");
				files.add(new Pair<>(linkEnd, "END"));

				return files;
			}
			//If the link target is neither a link or a dir, we need to check if it's a 'divider' (by file extension)
			else {
				//Find the first item in the target's parent directory that matches the target UUID
				List<Pair<UUID, String>> targetParentContents = dirCache.getDirContents(target.getParentUID());
				Optional<Pair<UUID, String>> targetItem = targetParentContents.stream()
						.filter(pair -> pair.first.equals( target.getFileUID() )).findFirst();

				if(!visited.add(target.getFileUID()))	//Prevent cycles
					return new ArrayList<>();

				//If the target is no longer in the parent directory, the link is broken and we're done here
				if(!targetItem.isPresent()) return new ArrayList<>();

				//If the target does not refer to a divider item, we're done here
				String targetName = targetItem.get().second;
				if(!FilenameUtils.getExtension(targetName).equals("div"))
					return new ArrayList<>();


				//Since the target is a divider item, we need to traverse only the "divider's items"
				List<Pair<UUID, String>> dividerItems = sublistDividerItems(targetParentContents, target.getFileUID());
				List<Pair<Path, String>> files =  traverseContents(dividerItems, localVisited, currPath);

				//Ad a bookend for the link
				Path linkEnd = currPath.resolve("END");
				files.add(new Pair<>(linkEnd, "END"));

				return files;
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
