package aaa.sgordon.galleryfinal.gallery;

import android.app.Application;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridAPI;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridListeners;
import aaa.sgordon.galleryfinal.utilities.MyApplication;

public class DirectoryViewModel extends AndroidViewModel {
	private final HybridAPI hAPI;

	private final UUID dirUID;
	private String currChecksum;
	private final HybridListeners.FileChangeListener fileChangeListener;

	MutableLiveData<List< Pair<UUID, String> >> data;



	public DirectoryViewModel(@NonNull Application application, @NonNull UUID dirUID) {
		super(application);
		this.dirUID = dirUID;


		hAPI = HybridAPI.getInstance();

		this.data = new MutableLiveData<>();
		data.setValue(new ArrayList<>());


		//Whenever the directory is updated, update our data
		fileChangeListener = uuid -> getUpdatedDirList();
		hAPI.addListener(fileChangeListener, dirUID);

		//Fetch the directory list and update our livedata
		Thread thread = new Thread(this::getUpdatedDirList);
		thread.start();



		//Loop importing items for testing adapter notifications
		HandlerThread handlerThread = new HandlerThread("BackgroundThread");
		handlerThread.start();
		Looper looper = handlerThread.getLooper();
		Handler handler = new Handler(looper);
		Runnable runnable = new Runnable() {
			public void run() {
				fakeImportFiles(2);

				handler.postDelayed(this, 3000);
			}
		};
		handler.postDelayed(runnable, 3000);
	}
	@Override
	protected void onCleared() {
		super.onCleared();
		if(fileChangeListener != null)
			hAPI.removeListener(fileChangeListener);
	}



	public void getUpdatedDirList() {
		try {
			Pair<Uri, String> dirContent = hAPI.getFileContent(dirUID);
			Uri dirUri = dirContent.first;
			String dirChecksum = dirContent.second;

			//If the data is the same as what we have, do nothing
			if(dirChecksum.equals(currChecksum))
				return;

			List<Pair<UUID, String>> dirList = readDir( dirUri );
			System.out.println("Directory data list has "+dirList.size()+" items.");
			data.postValue(dirList);

			currChecksum = dirChecksum;

		} catch (FileNotFoundException | ContentsNotFoundException | ConnectException e) {
			throw new RuntimeException(e);
		}
	}


	private List<Pair<UUID, String>> readDir(Uri dirUri) {
		//Read the directory into a list of UUID::FileName pairs
		ArrayList<Pair<UUID, String>> dirList = new ArrayList<>();
		try (InputStream inputStream = new URL(dirUri.toString()).openStream();
			 BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {

			String line;
			while ((line = reader.readLine()) != null) {
				//Split each line into UUID::FileName and add it to our list
				String[] parts = line.trim().split(" ", 2);
				Pair<UUID, String> entry = new Pair<>(UUID.fromString(parts[0]), parts[1]);
				dirList.add(entry);
			}
		}
		catch (IOException e) { throw new RuntimeException(e); }
		return dirList;
	}


	public void fakeImportFiles(int numImported) {
		HybridAPI hAPI = HybridAPI.getInstance();

		try {
			hAPI.lockLocal(dirUID);

			Pair<Uri, String> dirContent = hAPI.getFileContent(dirUID);
			Uri dirUri = dirContent.first;
			String dirChecksum = dirContent.second;

			List<Pair<UUID, String>> dirList = readDir(dirUri);


			//Add our new 'imported' files to the beginning
			for(int i = 0; i < numImported; i++) {
				UUID fileUID = UUID.randomUUID();
				String fileName = "File number "+dirList.size();
				dirList.add(0, new Pair<>(fileUID, fileName));
			}


			//Write the list back to the directory
			List<String> newLines = dirList.stream().map(pair -> pair.first+" "+pair.second)
					.collect(Collectors.toList());
			byte[] newContent = String.join("\n", newLines).getBytes();
			hAPI.writeFile(dirUID, newContent, dirChecksum);

		} catch (ContentsNotFoundException | FileNotFoundException | ConnectException e) {
			throw new RuntimeException(e);
		} finally {
			hAPI.unlockLocal(dirUID);
		}
	}


//=================================================================================================
//=================================================================================================

	public static class DirectoryViewModelFactory implements ViewModelProvider.Factory {
		private final Application application;
		private final UUID dirUID;
		public DirectoryViewModelFactory(Application application, UUID dirUID) {
			this.application = application;
			this.dirUID = dirUID;
		}

		@NonNull
		@Override
		public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
			if (modelClass.isAssignableFrom(DirectoryViewModel.class)) {
				DirectoryViewModel viewModel = new DirectoryViewModel(application, dirUID);
				return (T) viewModel;
			}
			throw new IllegalArgumentException("Unknown ViewModel class");
		}
	}



	/*
	//Only make sure to only traverse links to directories, not directories themselves
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
	 */



	/*
	//Loop adding an item for testing adapter notifications
		Handler handler = new Handler(MyApplication.getAppContext().getMainLooper());
		Runnable runnable = new Runnable() {
			public void run() {
				fakeImportFiles(2);

				handler.postDelayed(this, 5000);
			}
		};
		handler.postDelayed(runnable, 5000);
	 */
}
