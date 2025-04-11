package aaa.sgordon.galleryfinal.viewpager;

import android.annotation.SuppressLint;
import android.graphics.Matrix;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.fragment.app.Fragment;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerControlView;
import androidx.viewpager2.widget.ViewPager2;

import com.otaliastudios.zoom.ZoomSurfaceView;

import java.io.FileNotFoundException;
import java.net.ConnectException;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.databinding.VpViewpageBinding;
import aaa.sgordon.galleryfinal.gallery.ListItem;
import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridAPI;
import aaa.sgordon.galleryfinal.viewpager.components.DragPage;
import aaa.sgordon.galleryfinal.viewpager.components.VideoTouchHandler;

public class VideoFragment extends Fragment {
	private VpViewpageBinding binding;
	private final ListItem item;

	private ViewPagerFragment parentFrag;
	private ViewPager2 viewPager;

	private DragPage dragPage;

	private ExoPlayer player;
	private TextureView textureView;
	private View controls;


	public VideoFragment(ListItem item) {
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


		//Swap out ViewA for our PhotoView
		ViewStub mediaStub = binding.mediaStub;
		mediaStub.setLayoutResource(R.layout.vp_video);
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


		textureView = binding.viewA.findViewById(R.id.media);
		controls = binding.viewA.findViewById(R.id.videoControls);

		return binding.getRoot();
	}



	@SuppressLint("ClickableViewAccessibility")
	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		dragPage.post(() -> dragPage.onMediaReady(dragPage.getHeight()));


		VideoTouchHandler videoTouchHandler = new VideoTouchHandler(requireContext(), textureView);
		GestureDetector detector = new GestureDetector(requireContext(), new GestureDetector.SimpleOnGestureListener() {
			@Override
			public boolean onDown(@NonNull MotionEvent e) {
				longPress = false;
				return false;
			}

			@Override
			public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
				toggleControls();
				return false;
			}


			boolean longPress = false;
			@Override
			public void onLongPress(@NonNull MotionEvent e) {
				longPress = true;
			}
			@Override
			public boolean onDoubleTapEvent(@NonNull MotionEvent e) {
				//We are looking for a double tap without a longPress
				if(e.getAction() != MotionEvent.ACTION_UP || longPress) return true;

				//TODO Do something

				return true;
			}
		});


		binding.viewA.findViewById(R.id.touch_overlay).setOnTouchListener((v, event) -> {
			boolean handled = videoTouchHandler.onTouch(v, event);
			handled = handled || videoTouchHandler.isScaled();
			dragPage.requestDisallowInterceptTouchEvent(handled);

			//TODO May need to stop viewpager input when multi-touching

			return true;
		});

		initializePlayer();
	}



	@OptIn(markerClass = UnstableApi.class)
	private void initializePlayer() {
		player = new ExoPlayer.Builder(requireContext()).build();
		player.setVideoTextureView(textureView);

		player.addListener(new Player.Listener() {
			@Override
			public void onVideoSizeChanged(@NonNull VideoSize videoSize) {
				System.out.println("Size changed!");
				//Adjust the height of the media in the TextureView
				float videoWidth = videoSize.width;
				float videoHeight = videoSize.height;
				float videoAspectRatio = videoSize.pixelWidthHeightRatio * videoWidth / videoHeight;

				// Get the view's aspect ratio
				float viewWidth = textureView.getWidth();
				float viewHeight = textureView.getHeight();
				float viewAspectRatio = viewWidth / viewHeight;

				// Compute scale
				float scaleX = 1f;
				float scaleY = 1f;

				if (videoAspectRatio > viewAspectRatio) {
					scaleY = viewAspectRatio / videoAspectRatio;
				} else {
					scaleX = videoAspectRatio / viewAspectRatio;
				}

				// Apply scale to TextureView
				Matrix matrix = new Matrix();
				matrix.setScale(scaleX, scaleY, viewWidth / 2f, viewHeight / 2f);
				textureView.setTransform(matrix);


				//Tell DragPage the correct media height as well
				//dragPage.onMediaReady(videoHeight);		//TODO No correcto
			}
		});



		//Uri videoUri = Uri.parse("https://file-examples.com/storage/fee47d30d267f6756977e34/2017/04/file_example_MP4_480_1_5MG.mp4");
		Uri videoUri = Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4");
		MediaItem mediaItem = MediaItem.fromUri(videoUri);
		player.setMediaItem(mediaItem);
		player.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT);
		player.prepare();
		//player.play();

		startPostponedEnterTransition();
	}

	private void toggleControls() {
		if (controls.getVisibility() == View.VISIBLE) {
			controls.animate().alpha(0f).setDuration(200).withEndAction(() -> controls.setVisibility(View.GONE)).start();
		} else {
			controls.setAlpha(0f);
			controls.setVisibility(View.VISIBLE);
			controls.animate().alpha(1f).setDuration(200).start();
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		player.play();
	}

	@Override
	public void onPause() {
		player.pause();
		super.onPause();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (player != null) {
			player.release();
			player = null;
		}
	}
}
