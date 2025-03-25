package aaa.sgordon.galleryfinal;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.DocumentsContract;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import java.io.FileNotFoundException;
import java.util.UUID;

import aaa.sgordon.galleryfinal.databinding.ActivityMainBinding;
import aaa.sgordon.galleryfinal.repository.SAFGoFuckYourself;
import aaa.sgordon.galleryfinal.repository.StorageHandler;
import aaa.sgordon.galleryfinal.utilities.DirSampleData;

//LogCat filter:
//((level:debug & tag:Hyb) | (tag:System | is:stacktrace )) & package:mine & -line:ClassLoaderContext & name:hybridRepo

//https://developer.android.com/guide/navigation/use-graph/animate-transitions

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



		if(StorageHandler.getStorageTreeUri(this) == null) {
			System.out.println("Storage Uri is null!");
			StorageHandler.showPickStorageDialog(this, directoryPickerLauncher);
		}
		else {
			System.out.println("Storage Uri is NOT null!");
			try {
				testBullshit();
			} catch (FileNotFoundException e) {
				throw new RuntimeException(e);
			}
		}


		//If the storage directory is not accessible...
		if(!StorageHandler.isStorageAccessible(this)) {
			System.out.println("Launching");
			Log.w(TAG, "Storage directory is inaccessible. Prompting user to reselect.");

			StorageHandler.showPickStorageDialog(this, directoryPickerLauncher);
		} else {
			Log.i(TAG, "Using saved directory.");

			launchEverything();
		}
	}

	private final ActivityResultLauncher<Intent> directoryPickerLauncher = registerForActivityResult(
			new ActivityResultContracts.StartActivityForResult(),
			result -> {
				StorageHandler.onStorageLocationPicked(this, result);
				try {
					testBullshit();
				} catch (FileNotFoundException e) {
					throw new RuntimeException(e);
				}
				//launchEverything();
			});



	private void launchEverything() {
		//Go off main thread to setup the database and root dir
		// Later this will be done in a login activity before this one, so this won't be necessary
		Thread thread = new Thread(() -> {
			try {
				//WARNING: While testing, this MUST be the first thing used related to HybridAPI,
				// or an actual database will be created.
				UUID rootDirectoryUID = DirSampleData.setupDatabase(getApplicationContext());

				viewModel = new ViewModelProvider(this).get(MainViewModel.class);
				viewModel.testInt += 1;

				//Use the directoryUID returned to start the first fragment
				Handler mainHandler = new Handler(getMainLooper());
				mainHandler.post(() -> {
					/*
					AppStartPassword passwordFrag = AppStartPassword.newInstance("Root", "1234", () -> {
						launchNavGraph(rootDirectoryUID);
					});
					FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
					transaction.replace(R.id.app_password_container, passwordFrag);
					transaction.commit();
					/**/

					launchNavGraph(rootDirectoryUID);
				});
			}
			catch (FileNotFoundException e) { throw new RuntimeException(e); }
		});
		thread.start();
	}


	private void launchNavGraph(UUID rootDirectoryUID) {
		NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
				.findFragmentById(R.id.nav_host_fragment);
		NavController navController = navHostFragment.getNavController();

		Bundle bundle = new Bundle();
		bundle.putSerializable("directoryUID", rootDirectoryUID);
		bundle.putString("directoryName", rootDirectoryUID.toString());
		navController.setGraph(R.navigation.nav_graph, bundle);
	}




	private void testBullshit() throws FileNotFoundException {
		System.out.println("Inside");
		SharedPreferences prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
		String uriString = prefs.getString("device_storage_location", null);
		Uri rootTreeUri = Uri.parse(Uri.decode(uriString));
		System.out.println("RootTreeUri: "+rootTreeUri);
		String rootTreeDocID = DocumentsContract.getTreeDocumentId(rootTreeUri);
		System.out.println("RootTreeDoc: "+rootTreeDocID);

		Uri rootDocUri = SAFGoFuckYourself.makeDocUriFromRoot(rootTreeUri);
		System.out.println(rootDocUri.toString());


		System.out.println("Dirs ----------------------------------------------------------------");
		//Try making a new directory
		System.out.println("Creating directory: ");
		Uri newDirUri = SAFGoFuckYourself.makeDocUriFromRoot(rootTreeUri, "NewDir");
		System.out.println(newDirUri.toString());
		System.out.println(SAFGoFuckYourself.directoryExists(this, newDirUri));
		SAFGoFuckYourself.createDirectory(this, newDirUri);
		System.out.println(SAFGoFuckYourself.directoryExists(this, newDirUri));
		assert SAFGoFuckYourself.directoryExists(this, newDirUri);


		//Try making a nested directory in a directory that doesn't exist
		System.out.println("Creating nested dir: ");
		Uri nestedDirUri = SAFGoFuckYourself.makeDocUriFromRoot(rootTreeUri, "DirParent", "Inside Dir");
		System.out.println(nestedDirUri.toString());
		System.out.println(SAFGoFuckYourself.directoryExists(this, nestedDirUri));
		SAFGoFuckYourself.createDirectory(this, nestedDirUri);
		System.out.println(SAFGoFuckYourself.directoryExists(this, nestedDirUri));
		assert SAFGoFuckYourself.directoryExists(this, nestedDirUri);




		System.out.println("Files ---------------------------------------------------------------");
		//Try making a new file
		System.out.println("Creating File: ");
		Uri newFileUri = SAFGoFuckYourself.makeDocUriFromRoot(rootTreeUri, "NewText.txt");
		System.out.println(newFileUri.toString());
		System.out.println(SAFGoFuckYourself.fileExists(this, newFileUri));
		SAFGoFuckYourself.createFile(this, newFileUri);
		System.out.println(SAFGoFuckYourself.fileExists(this, newFileUri));
		assert SAFGoFuckYourself.fileExists(this, newFileUri);


		//Try making a nested file in a directory that doesn't exist
		System.out.println("Creating nested file: ");
		Uri nestedFileUri = SAFGoFuckYourself.makeDocUriFromRoot(rootTreeUri, "FileParent", "InsideFile.jpg");
		System.out.println(nestedFileUri.toString());
		System.out.println(SAFGoFuckYourself.fileExists(this, nestedFileUri));
		SAFGoFuckYourself.createFile(this, nestedFileUri);
		System.out.println(SAFGoFuckYourself.fileExists(this, nestedFileUri));
		assert SAFGoFuckYourself.fileExists(this, nestedFileUri);











		//Uri parentDir = SAFGoFuckYourself.findOrCreateDirectory(rootTreeUri, "Parent", this);
		//Uri childDir = SAFGoFuckYourself.findOrCreateDirectory(parentDir, "Child", this);


		System.out.println("Actual total success... Huh...");
		fuckShitUp();
	}
	private void fuckShitUp() {
		throw new RuntimeException();
	}
}