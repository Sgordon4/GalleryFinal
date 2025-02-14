package aaa.sgordon.galleryfinal.viewpager;

import android.os.Bundle;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.SharedElementCallback;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.transition.Transition;
import androidx.transition.TransitionInflater;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.transition.MaterialContainerTransform;

import org.apache.commons.io.FilenameUtils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.databinding.FragmentViewpagerBinding;
import aaa.sgordon.galleryfinal.gallery.DirectoryViewModel;

public class ViewPagerFragment extends Fragment {
	private FragmentViewpagerBinding binding;
	private DirectoryViewModel dirViewModel;

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		//Postpone the transition until the viewPager is ready
		//WARNING: The ViewPage Fragment MUST call 'getParentFragment().startPostponedEnterTransition();'
		postponeEnterTransition();

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

		Transition transition = TransitionInflater.from(getContext())
				.inflateTransition(R.transition.image_shared_element_transition);
		setSharedElementEnterTransition(transition);

		return binding.getRoot();
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		ViewPagerAdapter adapter = new ViewPagerAdapter(this);
		binding.viewpager.setAdapter(adapter);

		dirViewModel.flatList.observe(getViewLifecycleOwner(), this::updateList);


		//Rather than shared element transitioning to this fragment, we want to transition to a page fragment
		//This is used in conjunction with postponeEnterTransition() to wait for the ViewPager to be ready
		setEnterSharedElementCallback(new SharedElementCallback() {
			@Override
			public void onMapSharedElements(List<String> names, Map<String, View> sharedElements) {
				//Grab the currently displayed ViewPager Fragment
				FragmentManager fragmentManager = getChildFragmentManager();
				Fragment fragment = fragmentManager.findFragmentByTag("f" + binding.viewpager.getCurrentItem());

				if (fragment instanceof ImageFragment) {
					View firstImageView = fragment.getView() != null ? fragment.getView().findViewById(R.id.image) : null;

					if (firstImageView != null) {
						//Re-map the shared element to point to the imageView inside that fragment
						sharedElements.put(names.get(0), firstImageView);
					}
				}
			}
		});
	}

	private void updateList(List<Pair<Path, String>> newList) {
		//The ViewPager should only display media items
		List<Pair<Path, String>> mediaOnly = newList.stream().filter(pathStringPair -> {
			String extension = FilenameUtils.getExtension(pathStringPair.second);
			return extension.equals("jpg") || extension.equals("png");
		}).collect(Collectors.toList());

		((ViewPagerAdapter) binding.viewpager.getAdapter()).setList(mediaOnly);
		System.out.println("List updating");
	}
}
