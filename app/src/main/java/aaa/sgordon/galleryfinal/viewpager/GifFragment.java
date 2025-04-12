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
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.github.chrisbanes.photoview.PhotoView;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.databinding.VpViewpageBinding;
import aaa.sgordon.galleryfinal.gallery.ListItem;
import aaa.sgordon.galleryfinal.viewpager.components.DragPage;

public class GifFragment extends Fragment {
	private VpViewpageBinding binding;
	private final ListItem item;

	private ViewPager2 viewPager;
	private DragPage dragPage;


	public GifFragment(ListItem item) {
		this.item = item;
	}


	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		viewPager = requireParentFragment().requireView().findViewById(R.id.viewpager);
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		binding = VpViewpageBinding.inflate(inflater, container, false);


		//Swap out ViewA for our PhotoView
		ViewStub mediaStub = binding.mediaStub;
		mediaStub.setLayoutResource(R.layout.vp_image_photoview);
		mediaStub.inflate();

		binding.viewA.findViewById(R.id.media).setTransitionName(item.filePath.toString());


		ViewStub bottomSliderStub = binding.bottomSliderStub;
		bottomSliderStub.setLayoutResource(R.layout.vp_bottom);
		bottomSliderStub.inflate();

		binding.viewB.findViewById(R.id.test_button).setOnClickListener(v -> {
			Toast.makeText(requireContext(), "Test Button Clicked", Toast.LENGTH_SHORT).show();
		});


		dragPage = binding.motionLayout;
		dragPage.setOnDismissListener(() -> {
			getParentFragment().getParentFragmentManager().popBackStack();
		});

		return binding.getRoot();
	}


	@SuppressLint("ClickableViewAccessibility")
	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		dragPage.post(() -> dragPage.onMediaReady(dragPage.getHeight()));

		PhotoView media = binding.viewA.findViewById(R.id.media);
		media.setOnScaleChangeListener((scaleFactor, focusX, focusY) -> {
			boolean isScaled = media.getScale() * scaleFactor != 1;

			dragPage.requestDisallowInterceptTouchEvent(isScaled);
			media.setAllowParentInterceptOnEdge(!isScaled);
		});


		dragPage.setInterceptForPhotoViewsBitchAss(event -> {
			//Shit straight up isn't working unless I filter to these
			//ACTION_POINTER_DOWN/UP aren't firing on my emulator :(
			switch (event.getActionMasked()) {
				case MotionEvent.ACTION_POINTER_DOWN:
				case MotionEvent.ACTION_MOVE:
				case MotionEvent.ACTION_POINTER_UP:
					viewPager.setUserInputEnabled(event.getPointerCount() == 1);    //Stop ViewPager input if multi-touching
					break;
				case MotionEvent.ACTION_UP:
				case MotionEvent.ACTION_CANCEL:
					viewPager.setUserInputEnabled(true);
			}
		});


		//Using a custom modelLoader to handle HybridAPI FileUIDs
		Glide.with(media.getContext())
				.load(item.fileUID)
				.listener(new RequestListener<Drawable>() {
					@Override
					public boolean onLoadFailed(@Nullable GlideException e, @Nullable Object model, @NonNull Target<Drawable> target, boolean isFirstResource) {
						requireParentFragment().startPostponedEnterTransition();
						return false;
					}

					@Override
					public boolean onResourceReady(@NonNull Drawable resource, @NonNull Object model, Target<Drawable> target, @NonNull DataSource dataSource, boolean isFirstResource) {
						float intrinsicHeight = resource.getIntrinsicHeight();
						float intrinsicWidth = resource.getIntrinsicWidth();

						float windowHeight = dragPage.getHeight();
						float windowWidth = dragPage.getWidth();

						//Set zoom scaling to better match image
						float windowAspectRatio = windowWidth / windowHeight;
						float imageAspectRatio = intrinsicWidth / intrinsicHeight;
						float zoom;
						if(imageAspectRatio > windowAspectRatio)
							zoom = imageAspectRatio / windowAspectRatio;
						else
							zoom = windowAspectRatio / imageAspectRatio;

						if(zoom < 1.3) zoom = 1.3f;
						if(zoom > 2.25) zoom = 2.25f;

						media.setMaximumScale(zoom * 3);
						media.setMediumScale(zoom);
						media.setMinimumScale(1);


						dragPage.onMediaReady(intrinsicHeight);

						requireParentFragment().startPostponedEnterTransition();
						return false;
					}
				})
				.into(media);
	}
}
