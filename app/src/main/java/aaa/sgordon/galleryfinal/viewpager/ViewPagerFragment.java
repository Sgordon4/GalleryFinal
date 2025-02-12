package aaa.sgordon.galleryfinal.viewpager;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.SharedElementCallback;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavBackStackEntry;
import androidx.navigation.fragment.NavHostFragment;
import androidx.transition.ChangeImageTransform;
import androidx.transition.Transition;

import com.google.android.material.transition.MaterialContainerTransform;

import java.nio.file.Path;
import java.util.UUID;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.databinding.FragmentViewpagerBinding;
import aaa.sgordon.galleryfinal.gallery.DirectoryViewModel;

public class ViewPagerFragment extends Fragment {
	private FragmentViewpagerBinding binding;
	private DirectoryViewModel dirViewModel;

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Transition transition = new MaterialContainerTransform();
		transition.setDuration(600);
		transition.setInterpolator(new DecelerateInterpolator());

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
		View view =  binding.getRoot();

		ViewPagerFragmentArgs args = ViewPagerFragmentArgs.fromBundle(getArguments());
		int fromPos = args.getFromPosition();

		View sharedView = binding.shared;
		sharedView.setTransitionName(args.getItemPath());

		setEnterSharedElementCallback(new SharedElementCallback() {});

		return view;
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);


	}

	@Override
	public void onResume() {
		super.onResume();
		setSharedElementReturnTransition(new ChangeImageTransform());
	}
}
