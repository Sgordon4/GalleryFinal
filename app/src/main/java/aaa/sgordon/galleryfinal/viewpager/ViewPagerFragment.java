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
import androidx.lifecycle.ViewModelProvider;
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
import aaa.sgordon.galleryfinal.gallery.DirectoryViewModel;
import aaa.sgordon.galleryfinal.gallery.ListItem;
import aaa.sgordon.galleryfinal.utilities.Utilities;

//https://github.com/android/animation-samples/tree/main/GridToPager

public class ViewPagerFragment extends Fragment {
	private FragViewpagerBinding binding;
	private DirectoryViewModel dirViewModel;
	private int currPos = -1;

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		System.out.println("AAAAAAAAAA");

		List<Fragment> fragments = getParentFragmentManager().getFragments();
		System.out.println(Arrays.toString(fragments.toArray()));
		System.out.println(fragments.size());

		System.out.println(getParentFragment());
		System.out.println(getParentFragment().getChildFragmentManager().getFragments());



		/*
		Transition transition = TransitionInflater.from(getContext())
				.inflateTransition(R.transition.image_shared_element_transition);
		setSharedElementEnterTransition(transition);
		 */

		requireActivity().getOnBackPressedDispatcher().addCallback(new OnBackPressedCallback(true) {
			@Override
			public void handleOnBackPressed() {
				Bundle result = new Bundle();
				result.putInt("viewpage", currPos);
				getParentFragmentManager().setFragmentResult("viewpage", result);
			}
		});


		ViewPagerFragmentArgs args = ViewPagerFragmentArgs.fromBundle(getArguments());
		UUID directoryUID = args.getDirectoryUID();
		dirViewModel = new ViewModelProvider(requireParentFragment(),
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


		//Transition transition = TransitionInflater.from(requireContext()).inflateTransition(R.transition.image_shared_element_transition);
		//setSharedElementEnterTransition(transition);

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
