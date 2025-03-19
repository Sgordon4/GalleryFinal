package aaa.sgordon.galleryfinal.viewpager;

import android.os.Bundle;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.transition.Transition;
import androidx.transition.TransitionInflater;
import androidx.viewpager2.widget.ViewPager2;

import org.apache.commons.io.FilenameUtils;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.databinding.FragViewpagerBinding;
import aaa.sgordon.galleryfinal.gallery.DirectoryViewModel;
import aaa.sgordon.galleryfinal.gallery.ListItem;
import aaa.sgordon.galleryfinal.gallery.TraversalHelper;
import aaa.sgordon.galleryfinal.utilities.Utilities;

//https://github.com/android/animation-samples/tree/main/GridToPager

public class ViewPagerFragment extends Fragment {
	private FragViewpagerBinding binding;
	private DirectoryViewModel dirViewModel;
	private int currPos = -1;

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Transition transition = TransitionInflater.from(getContext())
				.inflateTransition(R.transition.image_shared_element_transition);
		setSharedElementEnterTransition(transition);

		//Postpone the transition until the viewPager is ready, but only if this is our first creation
		//WARNING: The ViewPage Fragment MUST call 'getParentFragment().startPostponedEnterTransition();'
		if(savedInstanceState == null)
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
		binding = FragViewpagerBinding.inflate(inflater, container, false);
		return binding.getRoot();
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		ViewPagerAdapter adapter = new ViewPagerAdapter(this);
		binding.viewpager.setAdapter(adapter);
		binding.viewpager.setOffscreenPageLimit(1);

		dirViewModel.getFilterRegistry().filteredList.observe(getViewLifecycleOwner(), this::updateList);

		binding.viewpager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
			@Override
			public void onPageSelected(int position) {
				super.onPageSelected(position);
				currPos = position;
			}
		});






	}

	private void updateList(List<ListItem> newList) {
		//The ViewPager should only display media items
		List<ListItem> mediaOnly = newList.stream()
				.filter(item -> Utilities.isFileMedia(item.name)).collect(Collectors.toList());

		//TODO If there are 0 items (all removed), leave

		((ViewPagerAdapter) binding.viewpager.getAdapter()).setList(mediaOnly);

		if(currPos == -1) {
			ViewPagerFragmentArgs args = ViewPagerFragmentArgs.fromBundle(getArguments());
			int fromPos = args.getFromPosition();

			currPos = mediaOnly.indexOf(newList.get(fromPos));
			binding.viewpager.setCurrentItem(currPos, false);
		}
	}
}
