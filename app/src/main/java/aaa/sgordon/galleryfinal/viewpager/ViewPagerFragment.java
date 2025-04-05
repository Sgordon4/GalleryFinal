package aaa.sgordon.galleryfinal.viewpager;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.SharedElementCallback;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;
import androidx.recyclerview.widget.RecyclerView;
import androidx.transition.Transition;
import androidx.transition.TransitionInflater;
import androidx.viewpager2.widget.ViewPager2;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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


	public static ViewPagerFragment initialize(UUID dirUID, int fromPosition) {
		ViewPagerFragment fragment = new ViewPagerFragment();

		Bundle bundle = new Bundle();
		bundle.putSerializable("directoryUID", dirUID);
		bundle.putInt("fromPosition", fromPosition);
		fragment.setArguments(bundle);

		return fragment;
	}


	@Override
	public void onStop() {
		ViewPagerAdapter adapter = (ViewPagerAdapter) binding.viewpager.getAdapter();
		dirViewModel.viewPagerCurrItem = adapter.list.get(binding.viewpager.getCurrentItem());

		System.out.println("Setting data");
		super.onStop();
	}


	/*
	@Override
	public void onDestroyView() {
		System.out.println("Destroying view");
		super.onDestroyView();
	}

	@Override
	public void onResume() {
		super.onResume();
		System.out.println("Resuming");
	}

	@Override
	public void onStart() {
		super.onStart();
		System.out.println("Starting");
	}

	@Override
	public void onPause() {
		System.out.println("Pausing");
		super.onPause();
	}

	 */

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		DirFragment dirFragment = null;
		List<Fragment> fragments = getParentFragmentManager().getFragments();
		for(Fragment fragment : fragments) {
			if(fragment instanceof DirFragment) {
				dirFragment = (DirFragment) fragment;
				break;
			}
		}
		if(dirFragment == null) throw new RuntimeException("Directory fragment not found");


		ViewPagerFragmentArgs args = ViewPagerFragmentArgs.fromBundle(getArguments());
		UUID directoryUID = args.getDirectoryUID();

		dirViewModel = new ViewModelProvider(dirFragment,
				new DirectoryViewModel.Factory(directoryUID))
				.get(DirectoryViewModel.class);
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		binding = FragViewpagerBinding.inflate(inflater, container, false);

		//Postpone the transition until the viewPager is ready, but only if this is our first creation
		//WARNING: The ViewPage Fragment MUST call 'requireParentFragment().startPostponedEnterTransition();'
		if(savedInstanceState == null)
			postponeEnterTransition();

		setEnterSharedElementCallback(new SharedElementCallback() {
			@Override
			public void onMapSharedElements(List<String> names, Map<String, View> sharedElements) {
				System.out.println("Mapping in viewpager");
				if(names.isEmpty()) return;

				//Get the currently displayed ViewPage and the media view inside of it
				Fragment viewPage = getChildFragmentManager().findFragmentByTag("f"+currPos);
				View media = viewPage.getView().findViewById(R.id.media);

				sharedElements.put(names.get(0), media);
				sharedElements.put(media.getTransitionName(), media);
			}
		});

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
