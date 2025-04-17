package aaa.sgordon.galleryfinal;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;

import java.io.IOException;
import java.util.UUID;

import aaa.sgordon.galleryfinal.databinding.ActivityMainBinding;
import aaa.sgordon.galleryfinal.gallery.DirFragment;
import aaa.sgordon.galleryfinal.repository.galleryhelpers.MainStorageHandler;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridAPI;
import aaa.sgordon.galleryfinal.repository.local.database.LocalDatabase;
import aaa.sgordon.galleryfinal.utilities.DirSampleData;

//LogCat filter:
//(level:verbose ) & (tag:Hyb & -tag:Hyb | tag:Gal. | System.out | (tag:AndroidRuntime & level:error ))

public class MainActivity extends AppCompatActivity {
	private final String TAG = "Gal.Main";
	private ActivityMainBinding binding;
	private MainViewModel viewModel;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		EdgeToEdge.enable(this);

		binding = ActivityMainBinding.inflate(getLayoutInflater());
		setContentView(binding.getRoot());


		ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
			Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
			//v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
			v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);	//Shows behind bottom navBar
			return insets;
		});



		//If the storage directory is not accessible...
		if(!MainStorageHandler.isStorageAccessible(this)) {
			Log.w(TAG, "Storage directory is inaccessible. Prompting user to reselect.");
			MainStorageHandler.showPickStorageDialog(this, directoryPickerLauncher);
		}
		else {
			Log.i(TAG, "Using saved directory.");
			if (savedInstanceState == null) {
				launch();
			}
		}
	}
	private final ActivityResultLauncher<Intent> directoryPickerLauncher = registerForActivityResult(
			new ActivityResultContracts.StartActivityForResult(),
			result -> {
				MainStorageHandler.onStorageLocationPicked(this, result);
				launch();
			});


	//---------------------------------------------------------------------------------------------

	//Go off main thread to setup the database and root dir
	//Later this will be done in a login activity before this one, so this won't be necessary
	private void launch() {
		Thread launchThread = new Thread(() -> {
			//Get the root directory UID from the shared preferences if it already exists, or create the test setup if not
			SharedPreferences prefs = getSharedPreferences("gallery.rootUIDForTesting", Context.MODE_PRIVATE);
			String rootUIDString = prefs.getString("UUID", null);
			if(rootUIDString == null)
				rootUIDString = createTestSetup();

			UUID rootDirectoryUID = UUID.fromString(rootUIDString);


			//Make sure the storage directory was created before we launch
			Uri storageDir = MainStorageHandler.getStorageTreeUri(this);
			if(storageDir == null) throw new RuntimeException("Storage directory is null!");

			//Initialize the Hybrid API
			LocalDatabase db = new LocalDatabase.DBBuilder().newInstance(this);
			HybridAPI.initialize(db, storageDir);


			//For funsies
			viewModel = new ViewModelProvider(this).get(MainViewModel.class);
			viewModel.testInt += 1;


			//Use the rootDirectoryUID to start the first fragment
			Handler mainHandler = new Handler(getMainLooper());
			mainHandler.post(() -> {
				DirFragment fragment = DirFragment.initialize(rootDirectoryUID, "Gallery App");

				getSupportFragmentManager()
						.beginTransaction()
						.replace(R.id.fragment_container, fragment, DirFragment.class.getSimpleName())
						.addToBackStack(null)
						.commit();
			});

		});
		launchThread.start();
	}


	private String createTestSetup() {
		try {
			//WARNING: While testing, this MUST be the first thing used related to HybridAPI,
			// or an actual database will be created.
			UUID rootDirectoryUID = DirSampleData.setupDatabase(this);
			//UUID rootDirectoryUID = DirSampleData.setupDatabaseSmall(this);

			SharedPreferences prefs = getSharedPreferences("gallery.rootUIDForTesting", Context.MODE_PRIVATE);
			prefs.edit().putString("UUID", rootDirectoryUID.toString()).apply();
			return rootDirectoryUID.toString();
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}


	//---------------------------------------------------------------------------------------------

	@Override
	protected void onStart() {
		super.onStart();

		SharedPreferences prefs = getSharedPreferences("gallery.syncPointers", Context.MODE_PRIVATE);
		prefs.edit().putInt("lastSyncLocal", 50).apply();

		new Thread(() -> Glide.get(this).clearDiskCache()).start();
		Glide.get(this).clearMemory();
	}

	@Override
	protected void onStop() {
		super.onStop();

		try {
			HybridAPI hAPI = HybridAPI.getInstance();
			hAPI.stopSyncService(hAPI.getCurrentAccount());
		}
		catch (IllegalStateException ignored) { }
	}
}