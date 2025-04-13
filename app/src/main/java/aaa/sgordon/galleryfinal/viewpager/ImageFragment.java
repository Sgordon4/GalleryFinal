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
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

import java.io.FileNotFoundException;
import java.net.ConnectException;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.databinding.VpViewpageBinding;
import aaa.sgordon.galleryfinal.gallery.ListItem;
import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridAPI;
import aaa.sgordon.galleryfinal.viewpager.components.DragPage;
import aaa.sgordon.galleryfinal.viewpager.components.ZoomPanHandler;

public class ImageFragment extends Fragment {
	private VpViewpageBinding binding;
	private final ListItem item;

	private DragPage dragPage;
	private ZoomPanHandler zoomPanHandler;


	public ImageFragment(ListItem item) {
		this.item = item;
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

	@SuppressLint("ClickableViewAccessibility")
	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		dragPage.post(() -> dragPage.onMediaReady(dragPage.getHeight()));

		if(item.fileSize < SIZE_THRESHOLD)
			usePhotoView();
		else
			useSubsamplingScaleImageView();
	}


	//---------------------------------------------------------------------------------------------


	private int touchSlop;
	private float downX, downY;

	@SuppressLint("ClickableViewAccessibility")
	private void usePhotoView() {

		//Swap out ViewA for our PhotoView
		ViewStub mediaStub = binding.mediaStub;
		mediaStub.setLayoutResource(R.layout.vp_image);
		mediaStub.inflate();

		binding.viewA.findViewById(R.id.media).setTransitionName(item.filePath.toString());

		ImageView media = binding.viewA.findViewById(R.id.media);
		zoomPanHandler = new ZoomPanHandler(media);

		touchSlop = ViewConfiguration.get(requireContext()).getScaledTouchSlop();
		binding.viewA.setOnTouchListener((v, event) -> {
			if(event.getAction() == MotionEvent.ACTION_DOWN) {
				downX = event.getX();
				downY = event.getY();
			}

			if(!dragPage.isActive()) {
				boolean handled = zoomPanHandler.onTouch(v, event);
				handled |= zoomPanHandler.isScaled();

				if(handled) {
					float moveX = event.getX();
					float moveY = event.getY();
					float deltaX = moveX - downX;
					float deltaY = moveY - downY;

					//If we're edge swiping, allow parent to intercept
					if(Math.abs(deltaX) > touchSlop && deltaX < 0 && zoomPanHandler.isEdgeSwipingEnd())
						dragPage.requestDisallowInterceptTouchEvent(false);
					else if(Math.abs(deltaX) > touchSlop && deltaX > 0 && zoomPanHandler.isEdgeSwipingStart())
						dragPage.requestDisallowInterceptTouchEvent(false);
					//else if(Math.abs(deltaY) > touchSlop && zoomPanHandler.isEdgeSwipingY())
						//dragPage.requestDisallowInterceptTouchEvent(false);
					else
						dragPage.requestDisallowInterceptTouchEvent(true);
				}
			}

			return true;
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
						float mediaAspectRatio = intrinsicWidth / intrinsicHeight;

						// Get the view's aspect ratio
						float viewWidth = media.getWidth();
						float viewHeight = media.getHeight();
						float windowAspectRatio = viewWidth / viewHeight;


						float zoom;
						float scaleX = 1f;
						float scaleY = 1f;

						if (mediaAspectRatio > windowAspectRatio) {
							scaleY = windowAspectRatio / mediaAspectRatio;
							zoom = mediaAspectRatio / windowAspectRatio;
						} else {
							scaleX = mediaAspectRatio / windowAspectRatio;
							zoom = windowAspectRatio / mediaAspectRatio;
						}

						if(zoom < 1.3) zoom = 1.3f;
						if(zoom > 2.25) zoom = 2.25f;


						zoomPanHandler.setMediaDimensions((int) intrinsicWidth, (int) intrinsicHeight);
						zoomPanHandler.setMidScale(zoom);
						zoomPanHandler.setMaxScale(zoom * 3);


						int actualWidth = (int) (viewWidth * scaleX);
						int actualHeight = (int) (viewHeight * scaleY);

						dragPage.onMediaReady(actualHeight);


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
				boolean isScaled = newScale != media.getMinScale();

				dragPage.requestDisallowInterceptTouchEvent(isScaled);
			}
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