package aaa.sgordon.galleryfinal.gallery;

import static android.os.Looper.getMainLooper;

import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.leinardi.android.speeddial.SpeedDialView;

import java.io.FileNotFoundException;
import java.net.ConnectException;
import java.nio.file.NotDirectoryException;
import java.util.UUID;

import aaa.sgordon.galleryfinal.MainViewModel;
import aaa.sgordon.galleryfinal.databinding.FragmentDirectoryBinding;
import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;

public class DirFragment extends Fragment {
	private FragmentDirectoryBinding binding;

	DirectoryViewModel dirViewModel;


	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		binding = FragmentDirectoryBinding.inflate(inflater, container, false);
		return binding.getRoot();
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		MainViewModel mainViewModel = new ViewModelProvider(getActivity()).get(MainViewModel.class);
		System.out.println("Inside directory, Activity has been created "+mainViewModel.testInt+" times.");

		DirFragmentArgs args = DirFragmentArgs.fromBundle(getArguments());
		UUID directoryUID = UUID.fromString( args.getDirectoryUID() );
		//dirViewModel = new ViewModelProvider(this).get(DirectoryViewModel.class);
		dirViewModel = new ViewModelProvider(this,
				new DirectoryViewModel.DirectoryViewModelFactory(getActivity().getApplication(), directoryUID))
				.get(DirectoryViewModel.class);


		MaterialToolbar toolbar = binding.galleryAppbar.toolbar;
		NavController navController = Navigation.findNavController(view);
		AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder().build();
		NavigationUI.setupWithNavController(toolbar, navController, appBarConfiguration);

		//Hide the navigation icon when we're at the top-level
		navController.addOnDestinationChangedListener((navController1, navDestination, bundle) -> {
			if(navController1.getPreviousBackStackEntry() == null)
				toolbar.setNavigationIcon(null);
		});

		//Must set title after configuration
		String directoryName = args.getDirectoryName();
		binding.galleryAppbar.toolbar.setTitle(directoryName);


		SelectionController.SelectionRegistry registry = new SelectionController.SelectionRegistry();




		// Recyclerview things:
		RecyclerView recyclerView = binding.recyclerview;
		recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
		//recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 4));

		DirRVAdapter adapter = new DirRVAdapter();
		recyclerView.setAdapter(adapter);

		//DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(getContext(), DividerItemDecoration.VERTICAL);
		//recyclerView.addItemDecoration(dividerItemDecoration);

		if(savedInstanceState != null) {
			Parcelable rvState = savedInstanceState.getParcelable("rvState");
			if(rvState != null) {
				System.out.println("Parcel found: "+rvState);
				recyclerView.getLayoutManager().onRestoreInstanceState(rvState);
			}
		}


		SelectionController selectionController = new SelectionController(registry, new SelectionController.SelectionCallbacks() {
			@Override
			public void onSelectionChanged(UUID fileUID, boolean isSelected) {
				//Notify the adapter that the item selection status has been changed so it can change its appearance
				/**/
				for(int i = 0; i < adapter.list.size(); i++) {
					//Get the UUID of this item
					String UUIDString = adapter.list.get(i).first.getFileName().toString();
					if(UUIDString.equals("END"))
						UUIDString = adapter.list.get(i).first.getParent().getFileName().toString();
					UUID itemUID = UUID.fromString(UUIDString);

					if(fileUID.equals(itemUID)) {
						RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(i);
						if(holder != null)
							holder.itemView.setSelected(isSelected);
						//adapter.notifyItemChanged(i);
					}
				}
				/**/
			}

			@Override
			public void selectionStarted(int numSelected) {

			}

			@Override
			public void selectionStopped() {

			}

			@Override
			public void numberSelectedChanged(int numSelected) {

			}
		});



		ItemReorderCallback reorderCallback = new ItemReorderCallback(recyclerView, (destination, nextItem, toMove) -> {
			Thread reorderThread = new Thread(() -> {
				try {
					boolean successful = dirViewModel.moveFiles(destination, nextItem, toMove);
					if(successful) return;

					//If the move was not successful, we want to return the list to how it was before we dragged
					Runnable myRunnable = () -> adapter.setList(dirViewModel.flatList.getValue());
					new Handler(getMainLooper()).post(myRunnable);

				} catch (FileNotFoundException | NotDirectoryException | ContentsNotFoundException | ConnectException e) {
					throw new RuntimeException(e);
				}
			});
			reorderThread.start();
		});
		ItemTouchHelper reorderHelper = new ItemTouchHelper(reorderCallback);
		reorderHelper.attachToRecyclerView(recyclerView);

		dirViewModel.flatList.observe(getViewLifecycleOwner(), list -> {
			adapter.setList(list);
			reorderCallback.applyReorder();
		});


		adapter.setCallbacks(new DirRVAdapter.AdapterCallbacks() {
			@Override
			public void onLongPress(DirRVAdapter.ViewHolder holder, UUID fileUID) {
				selectionController.startSelecting();
				selectionController.selectItem(fileUID);

				reorderHelper.startDrag(holder);
			}

			@Override
			public void onSingleTap(DirRVAdapter.ViewHolder holder, UUID fileUID) {
				if(selectionController.isSelecting())
					selectionController.toggleSelectItem(fileUID);
			}

			@Override
			public boolean isItemSelected(UUID fileUID) {
				return selectionController.isSelected(fileUID);
			}
		});







		//Temporary button for testing
		binding.buttonDrilldown.setOnClickListener(view1 -> {
			DirFragmentDirections.ActionToDirectoryFragment action = DirFragmentDirections.actionToDirectoryFragment();
			action.setDirectoryUID(directoryUID.toString());
			action.setDirectoryName("AAAAAA");
			NavHostFragment.findNavController(this).navigate(action);
		});
		//Button is kinda not useful now that we've hooked things up to the database, but I'm keeping this code for ref
		//Note: Using this button will make another fragment using the same directoryID, meaning that if we import things
		// in any of the child frags those things will show up in the previous frags too.
		//This is NOT a bug, and is actually the intended use case lmao. Pretty neat that it works though.
		binding.buttonDrilldown.setVisibility(View.GONE);


		//Show/Hide the fab when scrolling:
		recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
			@Override
			public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
				SpeedDialView fab = binding.fab;
				if(dy > 0 && fab.isShown()) fab.hide();
				else if(dy < 0 && !fab.isShown()) fab.show();
			}
		});
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		System.out.println("Directory destroying ");
		binding = null;
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		System.out.println("Directory Saving: ");

		//If binding is null, extremely likely this frag is in the backstack and was destroyed (and saved) earlier
		if(binding != null) {
			Parcelable listState = binding.recyclerview.getLayoutManager().onSaveInstanceState();
			outState.putParcelable("rvState", listState);
		}

		super.onSaveInstanceState(outState);
	}
}
