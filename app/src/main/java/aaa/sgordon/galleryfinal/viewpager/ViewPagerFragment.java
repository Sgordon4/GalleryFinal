package aaa.sgordon.galleryfinal.viewpager;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.SharedElementCallback;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.transition.ChangeBounds;
import androidx.transition.ChangeClipBounds;
import androidx.transition.ChangeImageTransform;
import androidx.transition.ChangeTransform;
import androidx.transition.TransitionSet;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.transition.MaterialContainerTransform;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.databinding.FragViewpagerBinding;
import aaa.sgordon.galleryfinal.gallery.DirFragment;
import aaa.sgordon.galleryfinal.gallery.DirectoryViewModel;
import aaa.sgordon.galleryfinal.gallery.ListItem;
import aaa.sgordon.galleryfinal.utilities.Utilities;

//https://github.com/android/animation-samples/tree/main/GridToPager
//https://www.thedroidsonroids.com/blog/how-to-use-shared-element-transition-with-glide-in-4-steps

public class ViewPagerFragment extends Fragment {
	private FragViewpagerBinding binding;
	private DirectoryViewModel dirViewModel;
	private int currPos = -1;


	public static ViewPagerFragment initialize(int fromPosition) {
		ViewPagerFragment fragment = new ViewPagerFragment();

		Bundle bundle = new Bundle();
		bundle.putInt("fromPosition", fromPosition);
		fragment.setArguments(bundle);

		return fragment;
	}

	@Override
	public void onStop() {
		//Pass back the current ViewPager position
		ViewPagerAdapter adapter = (ViewPagerAdapter) binding.viewpager.getAdapter();
		dirViewModel.viewPagerCurrItem = adapter.list.get(binding.viewpager.getCurrentItem());

		super.onStop();
	}


	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Fragment dirFragment = getParentFragmentManager().findFragmentByTag(DirFragment.class.getSimpleName());
		if(dirFragment == null) throw new RuntimeException("Directory fragment not found");

		dirViewModel = new ViewModelProvider(dirFragment).get(DirectoryViewModel.class);



		MaterialContainerTransform transform = new MaterialContainerTransform();
		transform.setDuration(300);
		transform.setDrawingViewId(R.id.fragment_container);
		transform.setFitMode(MaterialContainerTransform.FIT_MODE_HEIGHT); // or FIT_MODE_WIDTH
		transform.setFadeMode(MaterialContainerTransform.FADE_MODE_CROSS);
		setSharedElementEnterTransition(transform);



		setEnterSharedElementCallback(new SharedElementCallback() {
			@Override
			public void onMapSharedElements(List<String> names, Map<String, View> sharedElements) {
				if(names.isEmpty()) return;

				//Get the currently displayed ViewPage and the media view inside of it
				Fragment viewPage = getChildFragmentManager().findFragmentByTag("f"+currPos);
				View media = viewPage.requireView().findViewById(R.id.media);

				sharedElements.put(names.get(0), media);
				sharedElements.put(media.getTransitionName(), media);


				//Change the return mapping cause this shit is just constantly broken
				TransitionSet sharedElementEnterTransition = new TransitionSet();
				sharedElementEnterTransition.setOrdering(TransitionSet.ORDERING_TOGETHER);
				sharedElementEnterTransition.addTransition(new ChangeClipBounds());
				sharedElementEnterTransition.addTransition(new ChangeImageTransform());
				sharedElementEnterTransition.addTransition(new ChangeTransform());	//Culprit
				sharedElementEnterTransition.addTransition(new ChangeBounds());
				sharedElementEnterTransition.setDuration(300);
				setSharedElementEnterTransition(sharedElementEnterTransition);
			}
		});
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		binding = FragViewpagerBinding.inflate(inflater, container, false);

		//Postpone the transition until the viewPager is ready, but only if this is our first creation
		//WARNING: The ViewPage Fragment MUST call 'requireParentFragment().startPostponedEnterTransition();'
		if(savedInstanceState == null)
			postponeEnterTransition();

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
			Bundle args = requireArguments();
			int fromPos = args.getInt("fromPosition");

			currPos = mediaOnly.indexOf(newList.get(fromPos));
			binding.viewpager.setCurrentItem(currPos, false);
		}
	}
}
