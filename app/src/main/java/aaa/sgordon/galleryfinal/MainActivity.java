package aaa.sgordon.galleryfinal;

import android.os.Bundle;
import android.os.Handler;

import androidx.activity.EdgeToEdge;
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
import aaa.sgordon.galleryfinal.utilities.DirSampleData;

//LogCat filter:
//((level:debug & tag:Hyb) | (tag:System | is:stacktrace )) & package:mine & -line:ClassLoaderContext & name:hybridRepo

//https://developer.android.com/guide/navigation/use-graph/animate-transitions

public class MainActivity extends AppCompatActivity {
	private ActivityMainBinding binding;
	private MainViewModel viewModel;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		viewModel = new ViewModelProvider(this).get(MainViewModel.class);
		viewModel.testInt += 1;


		EdgeToEdge.enable(this);

		binding = ActivityMainBinding.inflate(getLayoutInflater());
		setContentView(binding.getRoot());

		ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
			Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
			//v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
			v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);	//Shows behind bottom navBar
			return insets;
		});



		//Go off main thread to setup the database and root dir
		// Later this will be done in a login activity before this one, so this won't be necessary
		Thread thread = new Thread(() -> {
			try {
				UUID rootDirectoryUID = DirSampleData.setupDatabase(getApplicationContext());
				//UUID rootDirectoryUID = DirSampleData.setupDatabase_Small(getApplicationContext());

				//Use the directoryUID returned to start the first fragment
				Runnable myRunnable = () -> {
					NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
							.findFragmentById(R.id.nav_host_fragment);
					NavController navController = navHostFragment.getNavController();

					Bundle bundle = new Bundle();
					bundle.putSerializable("directoryUID", rootDirectoryUID);
					bundle.putString("directoryName", rootDirectoryUID.toString());
					navController.setGraph(R.navigation.nav_graph, bundle);
				};
				Handler mainHandler = new Handler(getMainLooper());
				mainHandler.post(myRunnable);
			}
			catch (FileNotFoundException e) { throw new RuntimeException(e); }
		});
		thread.start();


		/*
		navController.setGraph(R.navigation.nav_graph);
		DirFragmentDirections.ActionToDirectoryFragment action = DirFragmentDirections.actionToDirectoryFragment();
		action.setDirectoryUID(UUID.randomUUID().toString());
		NavOptions navOptions = new NavOptions.Builder().setPopUpTo(R.id.DirectoryFragment, true).build();
		navController.navigate(action, navOptions);
		 */
	}
}