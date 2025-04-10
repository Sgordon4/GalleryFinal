package aaa.sgordon.galleryfinal.viewpager;

import android.annotation.SuppressLint;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.motion.widget.MotionLayout;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

import java.util.UUID;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.databinding.VpViewpageBinding;
import aaa.sgordon.galleryfinal.gallery.ListItem;
import aaa.sgordon.galleryfinal.viewpager.components.DragHelper;
import aaa.sgordon.galleryfinal.viewpager.components.ScaleHelper;

public class ImageFragment extends Fragment {
	private VpViewpageBinding binding;
	private final ListItem item;
	private final UUID fileUID;

	private DragHelper dragHelper;
	private ScaleHelper scaleHelper;

	private ViewPagerFragment parentFrag;
	private ViewPager2 viewPager;


	public ImageFragment(ListItem item) {
		this.item = item;
		this.fileUID = item.fileUID;
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		parentFrag = (ViewPagerFragment) requireParentFragment();
		viewPager = parentFrag.requireView().findViewById(R.id.viewpager);
	}


	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		binding = VpViewpageBinding.inflate(inflater, container, false);

		ViewStub mediaStub = binding.mediaStub;
		mediaStub.setLayoutResource(R.layout.vp_image);
		mediaStub.inflate();

		ViewStub bottomSliderStub = binding.bottomSliderStub;
		bottomSliderStub.setLayoutResource(R.layout.vp_bottom);
		bottomSliderStub.inflate();

		binding.viewB.findViewById(R.id.test_button).setOnClickListener(v -> {
			Toast.makeText(requireContext(), "Test Button Clicked", Toast.LENGTH_SHORT).show();
		});

		dragHelper = new DragHelper(binding.motionLayout, binding.viewA, binding.viewB);
		scaleHelper = new ScaleHelper(binding.dimBackground, binding.viewA, () -> {
			System.out.println("OnDismiss");
			getParentFragment().getParentFragmentManager().popBackStack();
		});


		binding.viewA.findViewById(R.id.media).setTransitionName(item.filePath.toString());

		return binding.getRoot();
	}


	private float downX, downY;
	private float touchSlop;
	private boolean vpAllowed;
	@SuppressLint("ClickableViewAccessibility")
	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		MotionLayout motionLayout = binding.motionLayout;

		motionLayout.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
			@Override
			public boolean onPreDraw() {
				motionLayout.getViewTreeObserver().removeOnPreDrawListener(this);

				dragHelper.onViewCreated();
				return true;
			}
		});


		touchSlop = ViewConfiguration.get(requireContext()).getScaledTouchSlop();
		motionLayout.setOnTouchListener((v, event) -> {
			v.performClick();

			switch (event.getActionMasked()) {
				case MotionEvent.ACTION_DOWN:
					downX = event.getX();
					downY = event.getY();
					vpAllowed = false;
					break;
			}
			//Don't allow viewpager to take control until twice the normal horizontal touch slop
			//This makes the viewpage vertical dragging more forgiving, feels better
			vpAllowed = vpAllowed || Math.abs(event.getX() - downX) > 2*touchSlop;


			viewPager.setUserInputEnabled(!scaleHelper.isActive() && !dragHelper.isDragging() && vpAllowed);



			if(!scaleHelper.isActive())
				dragHelper.onMotionEvent(event);
			if(!dragHelper.isActive())
				scaleHelper.onMotionEvent(event);

			return true;
		});



		ImageView media = binding.viewA.findViewById(R.id.media);

		//Using a custom modelLoader to handle HybridAPI FileUIDs
		Glide.with(media.getContext())
				.load(fileUID)
				.listener(new RequestListener<Drawable>() {
					@Override
					public boolean onLoadFailed(@Nullable GlideException e, @Nullable Object model, @NonNull Target<Drawable> target, boolean isFirstResource) {
						System.out.println("Media failed");
						requireParentFragment().startPostponedEnterTransition();
						return false;
					}

					@Override
					public boolean onResourceReady(@NonNull Drawable resource, @NonNull Object model, Target<Drawable> target, @NonNull DataSource dataSource, boolean isFirstResource) {
						System.out.println("Media loaded");

						int intrinsicHeight = resource.getIntrinsicHeight();
						dragHelper.onMediaReady(intrinsicHeight);
						requireParentFragment().startPostponedEnterTransition();
						return false;
					}
				})
				.into(media);
	}
}
