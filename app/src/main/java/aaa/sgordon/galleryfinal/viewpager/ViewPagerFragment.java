package aaa.sgordon.galleryfinal.viewpager;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.SharedElementCallback;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.transition.ChangeBounds;
import androidx.transition.ChangeClipBounds;
import androidx.transition.ChangeImageTransform;
import androidx.transition.ChangeTransform;
import androidx.transition.TransitionSet;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.transition.MaterialContainerTransform;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.databinding.FragViewpagerBinding;
import aaa.sgordon.galleryfinal.gallery.DirFragment;
import aaa.sgordon.galleryfinal.repository.gallery.ListItem;
import aaa.sgordon.galleryfinal.gallery.components.movecopy.MoveCopyFragment;
import aaa.sgordon.galleryfinal.utilities.Utilities;

//https://github.com/android/animation-samples/tree/main/GridToPager
//https://www.thedroidsonroids.com/blog/how-to-use-shared-element-transition-with-glide-in-4-steps

public class ViewPagerFragment extends Fragment {
	private FragViewpagerBinding binding;
	private VPViewModel viewModel;

	private ViewPagerAdapter adapter;
	public VPMenuItemHelper menuItemHelper;


	private MutableLiveData<List<ListItem>> tempDoNotUse;
	private VPViewModel.VPCallback tempCBDoNotUse;
	public static ViewPagerFragment initialize(int fromPosition, MutableLiveData<List<ListItem>> list, VPViewModel.VPCallback callback) {
		ViewPagerFragment fragment = new ViewPagerFragment();
		fragment.tempDoNotUse = list;
		fragment.tempCBDoNotUse = callback;

		Bundle bundle = new Bundle();
		bundle.putInt("fromPosition", fromPosition);
		fragment.setArguments(bundle);

		return fragment;
	}


	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		viewModel = new ViewModelProvider(this,
				new VPViewModel.Factory(tempDoNotUse, tempCBDoNotUse))
				.get(VPViewModel.class);

		DirFragment dirFragment = (DirFragment) getParentFragmentManager().findFragmentByTag(DirFragment.class.getSimpleName());
		menuItemHelper = new VPMenuItemHelper(dirFragment, dirFragment.dirViewModel.listItem, requireContext(), new VPMenuItemHelper.VPMenuItemHelperCallback() {
			@Override
			public ListItem getCurrentItem() {
				return adapter.list.get(viewModel.currPos);
			}
		});


		MaterialContainerTransform transform = new MaterialContainerTransform();
		transform.setDuration(300);
		transform.setScrimColor(Color.TRANSPARENT);
		transform.setDrawingViewId(R.id.fragment_container);
		transform.setFitMode(MaterialContainerTransform.FIT_MODE_HEIGHT); // or FIT_MODE_WIDTH
		transform.setFadeMode(MaterialContainerTransform.FADE_MODE_CROSS);
		setSharedElementEnterTransition(transform);


		//Postpone the transition until the viewPager is ready, but only if this is our first creation
		//WARNING: The ViewPage Fragment MUST call 'requireParentFragment().startPostponedEnterTransition();'
		if(savedInstanceState == null)
			postponeEnterTransition();

		setEnterSharedElementCallback(new SharedElementCallback() {
			@Override
			public void onMapSharedElements(List<String> names, Map<String, View> sharedElements) {
				if(names.isEmpty()) return;
				if(adapter.list.isEmpty()) return;

				//Get the currently displayed ViewPage and the media view inside of it
				long id = adapter.getItemId(viewModel.currPos);
				Fragment viewPage = getChildFragmentManager().findFragmentByTag("f"+id);
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
				sharedElementEnterTransition.setDuration(200);
				setSharedElementEnterTransition(sharedElementEnterTransition);
			}
		});
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

		requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
			@Override
			public void handleOnBackPressed() {
				//If a modal is open, close it rather than leaving
				Fragment mcFrag = getChildFragmentManager().findFragmentByTag(MoveCopyFragment.class.getSimpleName());
				if(mcFrag != null)
					getChildFragmentManager().popBackStack();
				else
					getParentFragmentManager().popBackStack();
			}
		});

		adapter = new ViewPagerAdapter(this);
		binding.viewpager.setAdapter(adapter);
		binding.viewpager.setOffscreenPageLimit(1);

		viewModel.list.observe(getViewLifecycleOwner(), this::updateList);


		binding.viewpager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
			@Override
			public void onPageSelected(int position) {
				super.onPageSelected(position);
				viewModel.currPos = position;
			}
		});
	}

	private void updateList(List<ListItem> newList) {
		//The ViewPager should only display media items
		List<ListItem> mediaOnly = newList.stream()
				.filter(item -> Utilities.isFileMedia( item.getPrettyName() )).collect(Collectors.toList());

		//If there are no media items, leave
		if(mediaOnly.isEmpty())
			getParentFragmentManager().popBackStack();


		adapter.setList(mediaOnly);

		if(viewModel.currPos == -1) {
			Bundle args = requireArguments();
			int fromPos = args.getInt("fromPosition");
			ListItem launchItem = newList.get(fromPos);

			viewModel.currPos = mediaOnly.indexOf(launchItem);
			binding.viewpager.setCurrentItem(viewModel.currPos, false);
		}
	}


	@Override
	public void onStop() {
		if(!adapter.list.isEmpty()) {
			//Pass back the current item
			ListItem currItem = adapter.list.get(binding.viewpager.getCurrentItem());
			viewModel.callback.onClose(currItem);
		}
		else
			viewModel.callback.onClose(null);


		super.onStop();
	}
}
