package aaa.sgordon.galleryfinal;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.NavDirections;
import androidx.navigation.NavGraph;
import androidx.navigation.NavOptions;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.appbar.AppBarLayout;

import java.util.UUID;

import aaa.sgordon.galleryfinal.databinding.ActivityMainBinding;
import aaa.sgordon.galleryfinal.gallery.DirFragmentDirections;

//LogCat filter:
//((level:debug & tag:Hyb) | (tag:System | is:stacktrace )) & package:mine & -line:ClassLoaderContext & name:hybridRepo

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




		NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
				.findFragmentById(R.id.nav_host_fragment);
		NavController navController = navHostFragment.getNavController();

		Bundle bundle = new Bundle();
		bundle.putString("directoryUID", UUID.randomUUID().toString());
		navController.setGraph(R.navigation.nav_graph, bundle);


		/*
		navController.setGraph(R.navigation.nav_graph);
		DirFragmentDirections.ActionToDirectoryFragment action = DirFragmentDirections.actionToDirectoryFragment();
		action.setDirectoryUID(UUID.randomUUID().toString());
		NavOptions navOptions = new NavOptions.Builder().setPopUpTo(R.id.DirectoryFragment, true).build();
		navController.navigate(action, navOptions);
		 */
	}
}



/*
		//https://developer.android.com/guide/navigation/use-graph/animate-transitions
		NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
		//appBarConfiguration = new AppBarConfiguration.Builder(navController.getGraph()).build();
		//NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
 */