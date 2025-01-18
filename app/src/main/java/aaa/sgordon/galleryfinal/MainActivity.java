package aaa.sgordon.galleryfinal;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import aaa.sgordon.galleryfinal.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {
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


		//https://developer.android.com/guide/navigation/use-graph/animate-transitions
		//NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
		//appBarConfiguration = new AppBarConfiguration.Builder(navController.getGraph()).build();
		//NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);

	}

	@Override
	protected void onStart() {
		super.onStart();

		viewModel = new ViewModelProvider(this).get(MainViewModel.class);
		viewModel.testInt += 1;
	}
}