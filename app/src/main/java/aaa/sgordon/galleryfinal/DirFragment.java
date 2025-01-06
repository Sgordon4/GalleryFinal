package aaa.sgordon.galleryfinal;

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
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import aaa.sgordon.galleryfinal.databinding.FragmentDirectoryBinding;

public class DirFragment extends Fragment {
	private FragmentDirectoryBinding binding;

	UUID thisFragmentUUID = UUID.randomUUID();

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		binding = FragmentDirectoryBinding.inflate(inflater, container, false);
		return binding.getRoot();
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		MainViewModel viewModel = new ViewModelProvider(getActivity()).get(MainViewModel.class);
		System.out.println("Inside directory, Activity has been recreated "+viewModel.testInt+" times.");


		List<String> data = IntStream.rangeClosed(0, 20).mapToObj(String::valueOf).collect(Collectors.toList());

		RecyclerView recyclerView = binding.recyclerview;
		recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
		recyclerView.setAdapter(new DirRVAdapter(data));

		DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(getContext(), DividerItemDecoration.VERTICAL);
		recyclerView.addItemDecoration(dividerItemDecoration);

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

			@Override
			public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
				int fromPosition = viewHolder.getAdapterPosition();
				int toPosition = target.getAdapterPosition();
				Collections.swap(data, fromPosition, toPosition);
				recyclerView.getAdapter().notifyItemMoved(fromPosition, toPosition);
				return false;
			}

			@Override
			public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {

			}
		});
		touchHelper.attachToRecyclerView(recyclerView);



		//Loop adding an item for testing adapter notifications
		Handler handler = new Handler(getContext().getMainLooper());

		Runnable runnable = new Runnable() {
			public void run() {
				data.add(4 , "New");
				recyclerView.getAdapter().notifyItemInserted(4);

				handler.postDelayed(this, 2000);
			}
		};
		handler.postDelayed(runnable, 2000);



		//-------------------------------------------------

		binding.buttonDrilldown.setOnClickListener(view1 ->
				NavHostFragment.findNavController(DirFragment.this)
						.navigate(R.id.action_DirFragment_to_DirFragment));
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
