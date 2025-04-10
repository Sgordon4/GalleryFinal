package aaa.sgordon.galleryfinal.viewpager;

import android.annotation.SuppressLint;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
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
import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.github.chrisbanes.photoview.PhotoView;

import java.io.FileNotFoundException;
import java.net.ConnectException;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.databinding.VpViewpageBinding;
import aaa.sgordon.galleryfinal.gallery.ListItem;
import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridAPI;
import aaa.sgordon.galleryfinal.viewpager.components.DragPage;

public class ImageFragment extends Fragment {
	private VpViewpageBinding binding;
	private final ListItem item;

	private ViewPagerFragment parentFrag;
	private ViewPager2 viewPager;

	private DragPage dragPage;


	public ImageFragment(ListItem item) {
		this.item = item;
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




	public static final int SIZE_THRESHOLD = 1024 * 1024 * 3;	//3MB
	private float touchSlop;

	@SuppressLint("ClickableViewAccessibility")
	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		touchSlop = ViewConfiguration.get(requireContext()).getScaledTouchSlop();


		if(item.fileSize < SIZE_THRESHOLD)
			usePhotoView();
		else
			useSubsamplingScaleImageView();
	}


	//---------------------------------------------------------------------------------------------


	//Using this instead of checking for scale == 1 or whatever in case user is still holding, touches on scale==1, and continues scaling
	boolean photoScaling = false;
	private float downX, downY;
	private boolean vpAllowed;

	private void usePhotoView() {

		//Swap out ViewA for our PhotoView
		ViewStub mediaStub = binding.mediaStub;
		mediaStub.setLayoutResource(R.layout.vp_image_photoview);
		mediaStub.inflate();

		binding.viewA.findViewById(R.id.media).setTransitionName(item.filePath.toString());


		PhotoView media = binding.viewA.findViewById(R.id.media);
		media.setOnScaleChangeListener((scaleFactor, focusX, focusY) -> {
			photoScaling = true;
			dragPage.requestDisallowInterceptTouchEvent(true);
			media.setAllowParentInterceptOnEdge(false);
		});

		dragPage.setExtraOnInterceptTouchListener(event -> {
			if (event.getAction() == MotionEvent.ACTION_DOWN) {
				photoScaling = false;

				downX = event.getX();
				downY = event.getY();
				vpAllowed = false;

				boolean mediaScaled = media.getScale() != 1;
				media.setAllowParentInterceptOnEdge(!mediaScaled);	//TODO Or we've downed when on the left/right edge
				dragPage.requestDisallowInterceptTouchEvent(mediaScaled);
			}

			//Don't allow viewpager to take control until twice the normal horizontal touch slop
			//This makes the viewpage vertical dragging more forgiving, feels better
			vpAllowed = vpAllowed || Math.abs(event.getX() - downX) > 2*touchSlop;
			viewPager.setUserInputEnabled(!dragPage.isHandlingTouch() && vpAllowed && !photoScaling);

			return false;
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



	private void useSubsamplingScaleImageView() {

		//Swap out ViewA for our SubsamplingScaleImageView
		ViewStub mediaStub = binding.mediaStub;
		mediaStub.setLayoutResource(R.layout.vp_image_subsampling);
		mediaStub.inflate();

		binding.viewA.findViewById(R.id.media).setTransitionName(item.filePath.toString());


		SubsamplingScaleImageView media = binding.viewA.findViewById(R.id.media);
		media.setOnImageEventListener(new SubsamplingScaleImageView.OnImageEventListener() {
			@Override
			public void onReady() {

			}
			@Override
			public void onPreviewLoadError(Exception e) {
				requireParentFragment().startPostponedEnterTransition();
			}
			@Override
			public void onImageLoadError(Exception e) {
				requireParentFragment().startPostponedEnterTransition();
			}
			@Override
			public void onTileLoadError(Exception e) {
				requireParentFragment().startPostponedEnterTransition();
			}
			@Override
			public void onPreviewReleased() {

			}
			@Override
			public void onImageLoaded() {
				float intrinsicHeight = media.getSHeight() * media.getScale();
				dragPage.onMediaReady(intrinsicHeight);
				requireParentFragment().startPostponedEnterTransition();
			}
		});

		media.setOnStateChangedListener(new SubsamplingScaleImageView.OnStateChangedListener() {
			@Override
			public void onCenterChanged(PointF newCenter, int origin) {

			}
			@Override
			public void onScaleChanged(float newScale, int origin) {
				photoScaling = true;
				dragPage.requestDisallowInterceptTouchEvent(true);
			}
		});

		dragPage.setExtraOnInterceptTouchListener(event -> {
			if (event.getAction() == MotionEvent.ACTION_DOWN) {
				photoScaling = false;

				downX = event.getX();
				downY = event.getY();
				vpAllowed = false;

				boolean mediaScaled = media.getScale() != media.getMinScale();
				dragPage.requestDisallowInterceptTouchEvent(mediaScaled);
			}

			//Don't allow viewpager to take control until twice the normal horizontal touch slop
			//This makes the viewpage vertical dragging more forgiving, feels better
			vpAllowed = vpAllowed || Math.abs(event.getX() - downX) > 2*touchSlop;
			viewPager.setUserInputEnabled(!dragPage.isHandlingTouch() && vpAllowed && !photoScaling);

			return false;
		});



		Thread load = new Thread(() -> {
			try {
				Uri uri = HybridAPI.getInstance().getFileContent(item.fileUID).first;

				media.post(() -> {
					media.setImage(ImageSource.uri(uri));

				});
			} catch (FileNotFoundException | ContentsNotFoundException | ConnectException e) {
				//TODO Load error uri
			}
		});
		load.start();
	}
}