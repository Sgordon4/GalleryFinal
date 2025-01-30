package aaa.sgordon.galleryfinal.gallery;

import android.os.Bundle;
import android.os.Parcelable;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import aaa.sgordon.galleryfinal.MainViewModel;
import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.databinding.FragmentDirectoryBinding;

public class DirFragment extends Fragment {
	private FragmentDirectoryBinding binding;

	UUID thisFragmentUUID = UUID.randomUUID();

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

		dirViewModel = new ViewModelProvider(this).get(DirectoryViewModel.class);

		// Get arguments safely using Safe Args
		DirFragmentArgs args = DirFragmentArgs.fromBundle(getArguments());
		String inputData = args.getInputData();
		System.out.println("Input data: ");
		System.out.println(inputData);

		binding.buttonDrilldown.setText(inputData);
		binding.buttonDrilldown.setOnClickListener(view1 ->
				NavHostFragment.findNavController(DirFragment.this)
						.navigate(R.id.action_toDirectoryFragment));



		// Recyclerview things:
		RecyclerView recyclerView = binding.recyclerview;
		recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

		DirRVAdapter adapter = new DirRVAdapter();
		recyclerView.setAdapter(adapter);
		dirViewModel.data.observe(getViewLifecycleOwner(), adapter::setData);

		DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(getContext(), DividerItemDecoration.VERTICAL);
		recyclerView.addItemDecoration(dividerItemDecoration);


		//Restore the recyclerview state:
		if(savedInstanceState != null) {
			Parcelable rvState = savedInstanceState.getParcelable("rvState_"+thisFragmentUUID.toString());

			if(rvState != null) {
				System.out.println("Parcel found: "+rvState);
				recyclerView.getLayoutManager().onRestoreInstanceState(rvState);
			}
		}


		ItemTouchHelper touchHelper = new ItemTouchHelper(new ItemTouchHelper.Callback() {

			@Override
			public boolean isLongPressDragEnabled() {
				return true;
			}

			@Override
			public boolean isItemViewSwipeEnabled() {
				return false;
			}

			@Override
			public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
				int dragFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN;
				return makeMovementFlags(dragFlags, 0);
			}

			//TODO When we update the list, if we're dragging an item we need to move that item
			// to its dragPos in the new list and THEN use DiffUtil, or it will move back
			@Override
			public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
				List<Pair<UUID, String>> list = dirViewModel.data.getValue();
				int fromPosition = viewHolder.getAdapterPosition();
				int toPosition = target.getAdapterPosition();
				Collections.swap(list, fromPosition, toPosition);
				dirViewModel.data.postValue(list);
				recyclerView.getAdapter().notifyItemMoved(fromPosition, toPosition);
				return false;
			}

			@Override
			public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {

			}
		});
		//touchHelper.attachToRecyclerView(recyclerView);
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		System.out.println("Directory destroying "+thisFragmentUUID.toString());
		binding = null;
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		System.out.println("Directory Saving: "+thisFragmentUUID.toString());

		//If binding is null, extremely likely this frag is in the backstack and was destroyed (and saved) earlier
		if(binding != null) {
			Parcelable listState = binding.recyclerview.getLayoutManager().onSaveInstanceState();
			outState.putParcelable("rvState_"+thisFragmentUUID.toString(), listState);
		}

		super.onSaveInstanceState(outState);
	}
}
