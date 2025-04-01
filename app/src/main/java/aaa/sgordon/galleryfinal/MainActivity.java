package aaa.sgordon.galleryfinal;

import android.content.Intent;
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
import androidx.documentfile.provider.DocumentFile;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.UUID;

import aaa.sgordon.galleryfinal.databinding.ActivityMainBinding;
import aaa.sgordon.galleryfinal.repository.galleryhelpers.SAFGoFuckYourself;
import aaa.sgordon.galleryfinal.repository.galleryhelpers.MainStorageHandler;
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


		//If the storage directory is not accessible...
		if(!MainStorageHandler.isStorageAccessible(this)) {
			System.out.println("Launching");
			Log.w(TAG, "Storage directory is inaccessible. Prompting user to reselect.");
			MainStorageHandler.showPickStorageDialog(this, directoryPickerLauncher);
		}
		else {
			Log.i(TAG, "Using saved directory.");
			launchEverything();
		}
	}

	private final ActivityResultLauncher<Intent> directoryPickerLauncher = registerForActivityResult(
			new ActivityResultContracts.StartActivityForResult(),
			result -> {
				MainStorageHandler.onStorageLocationPicked(this, result);
				launchEverything();
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
			catch (IOException e) { throw new RuntimeException(e); }
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



	private void testMaking() throws FileNotFoundException {
		System.out.println("Inside");
		Uri rootDocUri = MainStorageHandler.getStorageTreeUri(this);

		DocumentFile file = DocumentFile.fromTreeUri(this, rootDocUri);
		String fileName = "1:?/\\; SuperDuper File.txt";
		String sanitizedName = SAFGoFuckYourself.sanitizeFilename(fileName);
		file.createFile("*/*", sanitizedName);
		DocumentFile newFile = file.createFile("*/*", sanitizedName);

		System.out.println(newFile.getName());


		System.out.println("Finished");
		throw new RuntimeException();
	}




	private void testBullshit() throws FileNotFoundException {
		System.out.println("Inside");
		Uri rootDocUri = MainStorageHandler.getStorageTreeUri(this);

		Uri actualRoot = SAFGoFuckYourself.makeDocUriFromTreeUri(rootDocUri, ".Gallery");
		System.out.println(rootDocUri);
		System.out.println(actualRoot);


		Uri deepUri = SAFGoFuckYourself.makeDocUriFromTreeUri(rootDocUri, ".Gallery", "Superduper", "Innit");
		System.out.println(deepUri);
		System.out.println(SAFGoFuckYourself.getParentFromDocUri(deepUri));


		System.out.println("Dirs ----------------------------------------------------------------");
		//Try making a new directory
		System.out.println("Creating directory: ");
		Uri newDirUri = SAFGoFuckYourself.makeDocUriFromDocUri(rootDocUri, "NewDir");
		System.out.println(newDirUri.toString());
		System.out.println(SAFGoFuckYourself.directoryExists(this, newDirUri));
		SAFGoFuckYourself.createDirectory(this, newDirUri);
		System.out.println(SAFGoFuckYourself.directoryExists(this, newDirUri));
		assert SAFGoFuckYourself.directoryExists(this, newDirUri);


		//Try making a nested directory in a directory that doesn't exist
		System.out.println("Creating nested dir: ");
		Uri nestedDirUri = SAFGoFuckYourself.makeDocUriFromDocUri(rootDocUri, "DirParent", "Inside Dir");
		System.out.println(nestedDirUri.toString());
		System.out.println(SAFGoFuckYourself.directoryExists(this, nestedDirUri));
		SAFGoFuckYourself.createDirectory(this, nestedDirUri);
		System.out.println(SAFGoFuckYourself.directoryExists(this, nestedDirUri));
		assert SAFGoFuckYourself.directoryExists(this, nestedDirUri);


		System.out.println("Files ---------------------------------------------------------------");
		//Try making a new file
		System.out.println("Creating File: ");
		Uri newFileUri = SAFGoFuckYourself.makeDocUriFromDocUri(rootDocUri, "NewText.txt");
		System.out.println(newFileUri.toString());
		System.out.println(SAFGoFuckYourself.fileExists(this, newFileUri));
		SAFGoFuckYourself.createFile(this, newFileUri);
		System.out.println(SAFGoFuckYourself.fileExists(this, newFileUri));
		assert SAFGoFuckYourself.fileExists(this, newFileUri);


		//Try making a nested file in a directory that doesn't exist
		System.out.println("Creating nested file: ");
		Uri nestedFileUri = SAFGoFuckYourself.makeDocUriFromDocUri(rootDocUri, "FileParent", "InsideFile.jpg");
		System.out.println(nestedFileUri.toString());
		System.out.println(SAFGoFuckYourself.fileExists(this, nestedFileUri));
		SAFGoFuckYourself.createFile(this, nestedFileUri);
		System.out.println(SAFGoFuckYourself.fileExists(this, nestedFileUri));
		assert SAFGoFuckYourself.fileExists(this, nestedFileUri);




		System.out.println("Actual total success... Huh...");
		fuckShitUp();
	}
	private void fuckShitUp() {
		throw new RuntimeException();
	}
}