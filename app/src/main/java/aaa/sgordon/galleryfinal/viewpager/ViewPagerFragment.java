package aaa.sgordon.galleryfinal.viewpager;

import android.os.Bundle;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.SharedElementCallback;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.transition.Transition;
import androidx.transition.TransitionInflater;
import androidx.viewpager2.widget.ViewPager2;

import org.apache.commons.io.FilenameUtils;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.databinding.FragmentViewpagerBinding;
import aaa.sgordon.galleryfinal.gallery.DirectoryViewModel;

//https://github.com/android/animation-samples/tree/main/GridToPager

public class ViewPagerFragment extends Fragment {
	private FragmentViewpagerBinding binding;
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


		/*

		//Rather than shared element transitioning to this fragment, we want to transition to a page fragment
		//This is used in conjunction with postponeEnterTransition() to wait for the ViewPager to be ready
		setEnterSharedElementCallback(new SharedElementCallback() {
			@Override
			public void onMapSharedElements(List<String> names, Map<String, View> sharedElements) {
				System.out.println("VIEWPAGER ENTER");
				System.out.println(Arrays.asList(names.toArray()));
				System.out.println(Arrays.asList(sharedElements.keySet().toArray()));

				Fragment currentFragment = getChildFragmentManager()
						.findFragmentByTag("f" + binding.viewpager.getCurrentItem());
				View newSharedElement = currentFragment.getView().findViewById(R.id.media);

				if (newSharedElement != null) {
					names.clear();
					//sharedElements.clear();
					names.add(newSharedElement.getTransitionName());
					sharedElements.put(newSharedElement.getTransitionName(), newSharedElement);
				}
				System.out.println("VIEWPAGER NOWWWWW");
				System.out.println(Arrays.asList(names.toArray()));
				System.out.println(Arrays.asList(sharedElements.keySet().toArray()));
			}
		});

		 */

	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		binding = FragmentViewpagerBinding.inflate(inflater, container, false);

		setEnterSharedElementCallback(
				new SharedElementCallback() {
					@Override
					public void onMapSharedElements(List<String> names, Map<String, View> sharedElements) {
						System.out.println("Inside");

						Fragment currentFragment = getChildFragmentManager()
								.findFragmentByTag("f" + binding.viewpager.getCurrentItem());
						System.out.println("Frags: "+Arrays.toString(getChildFragmentManager().getFragments().toArray()));
						System.out.println("CurrItem: "+binding.viewpager.getCurrentItem());
						System.out.println("CurrFrag: "+currentFragment);

						for(Fragment frag : getChildFragmentManager().getFragments()) {
							System.out.println("Setting "+frag.getTag());
							frag.getView().findViewById(R.id.media).setTransitionName(frag.getTag());
						}

						View view = currentFragment.getView();
						if (view == null) return;

						// Map the first shared element name to the child ImageView.
						sharedElements.put(names.get(0), view.findViewById(R.id.media));
						view.findViewById(R.id.media).setTransitionName(names.get(0));

						System.out.println("View: "+view);
						System.out.println("Media: "+view.findViewById(R.id.media));
						System.out.println("Transition: "+view.findViewById(R.id.media).getTransitionName());


						/*
						names.clear();
						//sharedElements.clear();
						names.add(view.findViewById(R.id.media).getTransitionName());
						sharedElements.put(view.findViewById(R.id.media).getTransitionName(), view.findViewById(R.id.media));
						 */
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

		dirViewModel.flatList.observe(getViewLifecycleOwner(), this::updateList);

		binding.viewpager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
			@Override
			public void onPageSelected(int position) {
				super.onPageSelected(position);
				currPos = position;
				System.out.println("NEW: "+adapter.list.get(position).first);

				NavController navController = Navigation.findNavController(getView());
				navController.getPreviousBackStackEntry().getSavedStateHandle()
						.set("lastPath", adapter.list.get(position).first.toString());


				/*
				Fragment currentFragment = getChildFragmentManager()
						.findFragmentByTag("f" + binding.viewpager.getCurrentItem());
				if (currentFragment != null && getView() != null) {
					View sharedElement = currentFragment.getView().findViewById(R.id.media);
					if (sharedElement != null) {
						System.out.println("Changing to "+sharedElement.getTransitionName());
						requireActivity().getSupportFragmentManager()
								.beginTransaction()
								.setReorderingAllowed(true)
								.addSharedElement(sharedElement, sharedElement.getTransitionName())
								.commit();
					}
				}
				 */
			}
		});






	}

	private void updateList(List<Pair<Path, String>> newList) {
		//The ViewPager should only display media items
		List<Pair<Path, String>> mediaOnly = newList.stream().filter(pathStringPair -> {
			String extension = FilenameUtils.getExtension(pathStringPair.second);
			return extension.equals("jpg") || extension.equals("png") || extension.equals("gif") || extension.equals("mp4");
		}).collect(Collectors.toList());

		//TODO If there are 0 items (all removed), leave

		((ViewPagerAdapter) binding.viewpager.getAdapter()).setList(mediaOnly);

		if(currPos == -1) {
			ViewPagerFragmentArgs args = ViewPagerFragmentArgs.fromBundle(getArguments());
			int fromPos = args.getFromPosition();

			currPos = mediaOnly.indexOf(newList.get(fromPos));
			binding.viewpager.setCurrentItem(currPos, false);
		}
	}

	@Override
	public void onStop() {
		super.onStop();
		System.out.println("Stopping");

	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		System.out.println("Destroying");
	}
}
