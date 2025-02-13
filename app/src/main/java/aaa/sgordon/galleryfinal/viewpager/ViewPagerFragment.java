package aaa.sgordon.galleryfinal.viewpager;

import android.os.Bundle;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.transition.Transition;

import com.google.android.material.transition.MaterialContainerTransform;

import org.apache.commons.io.FilenameUtils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import aaa.sgordon.galleryfinal.databinding.FragmentViewpagerBinding;
import aaa.sgordon.galleryfinal.gallery.DirectoryViewModel;

public class ViewPagerFragment extends Fragment {
	private FragmentViewpagerBinding binding;
	private DirectoryViewModel dirViewModel;

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Transition transition = new MaterialContainerTransform();
		transition.setDuration(400);

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

		ViewPagerAdapter adapter = new ViewPagerAdapter(this);
		binding.viewpager.setAdapter(adapter);

		//Pause the enter transition so the shared element transition can complete
		postponeEnterTransition();
		binding.viewpager.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
			@Override
			public boolean onPreDraw() {
				binding.viewpager.getViewTreeObserver().removeOnPreDrawListener(this);
				startPostponedEnterTransition(); // Resume transition once layout is ready
				return true;
			}
		});

		dirViewModel.flatList.observe(getViewLifecycleOwner(), newList -> {
			//The ViewPager should only display media items
			List<Pair<Path, String>> mediaOnly = newList.stream().filter(pathStringPair -> {
				String extension = FilenameUtils.getExtension(pathStringPair.second);
				return extension.equals("jpg") || extension.equals("png");
			}).collect(Collectors.toList());

			adapter.setList(mediaOnly);
		});
	}
}
