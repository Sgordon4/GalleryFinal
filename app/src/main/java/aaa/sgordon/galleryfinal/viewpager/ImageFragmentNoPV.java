package aaa.sgordon.galleryfinal.viewpager;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
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

public class ImageFragmentNoPV extends Fragment {
	private VpViewpageBinding binding;
	private final ListItem item;

	private ViewPager2 viewPager;
	private DragPage dragPage;


	public ImageFragmentNoPV(ListItem item) {
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


		//Swap out ViewA for our SubsamplingScaleImageView
		ViewStub mediaStub = binding.mediaStub;
		mediaStub.setLayoutResource(R.layout.vp_image_subsampling);
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




	public static final int SIZE_THRESHOLD = 1024 * 1024 * 3;	//3MB

	@SuppressLint("ClickableViewAccessibility")
	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		dragPage.post(() -> dragPage.onMediaReady(dragPage.getHeight()));

		useSubsamplingScaleImageView();
	}


	//---------------------------------------------------------------------------------------------



	private void useSubsamplingScaleImageView() {

		SubsamplingScaleImageView media = binding.viewA.findViewById(R.id.media);
		media.setDoubleTapZoomDuration(150);
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
				float intrinsicWidth = media.getSWidth() * media.getScale();
				float intrinsicHeight = media.getSHeight() * media.getScale();

				System.out.println(media.getSHeight());
				System.out.println(media.getScale());

				float windowWidth = dragPage.getWidth();
				float windowHeight = dragPage.getHeight();

				System.out.println(intrinsicHeight);
				System.out.println(windowHeight);

				//Set zoom scaling to better match image
				float windowAspectRatio = windowWidth / windowHeight;
				float imageAspectRatio = intrinsicWidth / intrinsicHeight;
				System.out.println(windowAspectRatio+" vs "+imageAspectRatio);
				float zoom;
				if(imageAspectRatio > windowAspectRatio)
					zoom = imageAspectRatio / windowAspectRatio;
				else
					zoom = windowAspectRatio / imageAspectRatio;

				if(zoom < 1.3) zoom = 1.3f;
				if(zoom > 2.25) zoom = 2.25f;

				zoom *= media.getScale();
				media.setDoubleTapZoomScale(zoom);

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
					//Load larger images straight into the SubsamplingScaleImageView
					if(item.fileSize > SIZE_THRESHOLD) {
						media.setImage(ImageSource.uri(uri));
					}
					//Load smaller images with Glide so it can cache things
					else {
						Glide.with(requireContext())
								.asBitmap()
								.load(uri)
								.into(new CustomTarget<Bitmap>() {
									@Override
									public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
										media.setImage(ImageSource.bitmap(resource));
									}

									@Override
									public void onLoadCleared(@Nullable Drawable placeholder) {

									}
								});
					}
				});
			} catch (FileNotFoundException | ContentsNotFoundException | ConnectException e) {
				//TODO Load error uri
			}
		});
		load.start();
	}
}