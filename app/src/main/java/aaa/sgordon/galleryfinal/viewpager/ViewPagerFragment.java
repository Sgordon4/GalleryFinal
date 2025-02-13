package aaa.sgordon.galleryfinal.viewpager;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.transition.Transition;

import com.google.android.material.transition.MaterialContainerTransform;

import java.util.UUID;

import aaa.sgordon.galleryfinal.databinding.FragmentViewpagerBinding;
import aaa.sgordon.galleryfinal.gallery.DirectoryViewModel;

public class ViewPagerFragment extends Fragment {
	private FragmentViewpagerBinding binding;
	private DirectoryViewModel dirViewModel;

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Transition transition = new MaterialContainerTransform();
		transition.setDuration(200);

		setSharedElementEnterTransition(transition);
		setSharedElementReturnTransition(transition);

		ViewPagerFragmentArgs args = ViewPagerFragmentArgs.fromBundle(getArguments());
		UUID directoryUID = args.getDirectoryUID();
		dirViewModel = new ViewModelProvider(getParentFragment(),
				new DirectoryViewModel.Factory(directoryUID))
				.get(DirectoryViewModel.class);
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		binding = FragmentViewpagerBinding.inflate(inflater, container, false);
		return binding.getRoot();
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		View sharedView = binding.shared;

		ViewPagerFragmentArgs args = ViewPagerFragmentArgs.fromBundle(getArguments());
		sharedView.setTransitionName(args.getItemPath());
		System.out.println("Setting transition name to "+args.getItemPath());
	}
}
