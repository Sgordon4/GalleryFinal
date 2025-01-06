package aaa.sgordon.galleryfinal.gallery;

import android.app.Application;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

//TODO When we update the list, if we're dragging an item we need to move that item
// to its dragPos in the new list and THEN use DiffUtil, or it will move back

public class DirectoryViewModel extends AndroidViewModel {
	private DirSampleData sampleData;

	UUID currDir;
	List<Pair<UUID, String>> completeList;

	public DirectoryViewModel(@NonNull Application application) {
		super(application);

		this.completeList = new ArrayList<>();
	}

	public void setCurrDir(@NonNull UUID currDir) {
		this.sampleData = new DirSampleData(currDir);
		this.currDir = currDir;

		List<Pair<UUID, String>> newData = traverse(currDir, new HashSet<>());

		completeList.clear();
		completeList.addAll(newData);
	}


	private List<Pair<UUID, String>> readDir(UUID dirUID) {
		return sampleData.dirMap.get(dirUID);
	}

	//Only make sure to only do this for links to directories, not directories themselves
	private List<Pair<UUID, String>> traverse(@NonNull UUID nextUUID, @NonNull Set<UUID> visited) {
		System.out.println("Visited identity: "+visited.hashCode());
		System.out.println("Size is "+visited.size());

		//If we've already touched this directory, skip it so we don't loop forever
		if(visited.contains(nextUUID))
			return new ArrayList<>();
		visited.add(nextUUID);

		//Get the list of files in this directory
		List<Pair<UUID, String>> thisList = readDir(nextUUID);
		//And make an empty list for results
		List<Pair<UUID, String>> retList = new ArrayList<>();


		//For each file in the directory
		for (Pair<UUID, String> pair : thisList) {
			retList.add(pair);

			//If this item is a link to a directory, traverse it
			if(sampleData.isLinkToDir(pair.first)) {
				Set<UUID> shallowCopy = new HashSet<>(visited);
				retList.addAll(traverse(pair.first, shallowCopy));
			}
		}

		return retList;
	}
}
