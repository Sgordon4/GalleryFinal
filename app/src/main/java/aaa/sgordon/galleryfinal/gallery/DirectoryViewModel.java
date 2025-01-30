package aaa.sgordon.galleryfinal.gallery;

import android.app.Application;
import android.os.Handler;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import aaa.sgordon.galleryfinal.utilities.MyApplication;


public class DirectoryViewModel extends AndroidViewModel {
	private DirSampleData sampleData;

	UUID currDir;
	MutableLiveData<List< Pair<UUID, String> >> data;


	public DirectoryViewModel(@NonNull Application application) {
		super(application);

		this.data = new MutableLiveData<>();
		data.setValue(new ArrayList<>());


		//Start a background thread to 'fetch data from the repository', notifying things when it finishes
		Thread backgroundThread = new Thread(() -> {
			//try { Thread.sleep(2500); }
			//catch (InterruptedException e) { throw new RuntimeException(e); }

			setCurrDir(UUID.randomUUID());
		});
		backgroundThread.start();


		//Loop adding an item for testing adapter notifications
		Handler handler = new Handler(MyApplication.getAppContext().getMainLooper());
		Runnable runnable = new Runnable() {
			public void run() {
				List<Pair<UUID, String>> toUpdate = data.getValue();
				toUpdate.add(4 , new Pair<>(UUID.randomUUID(), "New"));

				String print = Arrays.toString(toUpdate.stream().map(item -> item.second).toArray());
				System.out.println(print);

				data.postValue(toUpdate);

				handler.postDelayed(this, 5000);
			}
		};
		handler.postDelayed(runnable, 5000);
	}

	public void setCurrDir(@NonNull UUID currDir) {
		this.sampleData = new DirSampleData(currDir);
		this.currDir = currDir;

		List<Pair<UUID, String>> newData = traverse(currDir, new HashSet<>());
		System.out.println("Directory data list has "+newData.size()+" items.");

		//Add some extra shit to make the list taller for scroll testing
		for(int i = 0; i < 20; i++)
			newData.add(new Pair<>(UUID.randomUUID(), String.valueOf(i)));

		data.postValue(newData);
	}


	private List<Pair<UUID, String>> readDir(UUID dirUID) {
		return sampleData.dirMap.get(dirUID);
	}

	//Only make sure to only do this for links to directories, not directories themselves
	private List<Pair<UUID, String>> traverse(@NonNull UUID nextUUID, @NonNull Set<UUID> visited) {
		//System.out.println("Visited identity: "+visited.hashCode());
		//System.out.println("Size is "+visited.size());

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
