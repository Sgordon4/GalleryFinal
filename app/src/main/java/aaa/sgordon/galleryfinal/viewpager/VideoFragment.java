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
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.fragment.app.Fragment;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.LegacyPlayerControlView;
import androidx.media3.ui.PlayerControlView;
import androidx.viewpager2.widget.ViewPager2;

import java.io.FileNotFoundException;
import java.net.ConnectException;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.databinding.VpViewpageBinding;
import aaa.sgordon.galleryfinal.gallery.ListItem;
import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridAPI;
import aaa.sgordon.galleryfinal.viewpager.components.DragPage;
import aaa.sgordon.galleryfinal.viewpager.components.VideoTouchHandler;

@UnstableApi
public class VideoFragment extends Fragment {
	private VpViewpageBinding binding;
	private final ListItem item;

	private ViewPager2 viewPager;

	private DragPage dragPage;
	private VideoTouchHandler videoTouchHandler;

	private ExoPlayer player;
	private TextureView textureView;
	private LegacyPlayerControlView controls;


	public VideoFragment(ListItem item) {
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

		return binding.getRoot();
	}



	@SuppressLint("ClickableViewAccessibility")
	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		dragPage.post(() -> dragPage.onMediaReady(dragPage.getHeight()));


		videoTouchHandler = new VideoTouchHandler(requireContext(), textureView);
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

				//Figure out which half of the view this event is on
				float viewWidth = binding.viewA.getWidth();
				float touchX = e.getX();

				//Seek forward/backward
				if(touchX < viewWidth/2)
					player.seekBack();
				else
					player.seekForward();

				return true;
			}
		});




		binding.viewA.findViewById(R.id.touch_overlay).setOnTouchListener((v, event) -> {
			detector.onTouchEvent(event);

			boolean handled = videoTouchHandler.onTouch(v, event);
			handled = handled || videoTouchHandler.isScaled();
			dragPage.requestDisallowInterceptTouchEvent(handled);

			viewPager.setUserInputEnabled(event.getPointerCount() == 1);	//Stop ViewPager input if multi-touching

			return true;
		});

		initializePlayer();
	}



	@OptIn(markerClass = UnstableApi.class)
	private void initializePlayer() {
		player = new ExoPlayer.Builder(requireContext()).build();
		player.setVideoTextureView(textureView);


		controls = binding.viewA.findViewById(R.id.player_control_view);
		controls.setPlayer(player);

		controls.addVisibilityListener(visibility ->
				binding.viewA.findViewById(R.id.getthatshitouttathebottomofthescreendawg).setVisibility(visibility));



		player.addListener(new Player.Listener() {
			@Override
			public void onVideoSizeChanged(@NonNull VideoSize videoSize) {
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


				float actualWidth = viewWidth * scaleX;
				float actualHeight = viewHeight * scaleY;

				videoTouchHandler.setMediaDimens(actualWidth, actualHeight);

				//Tell DragPage the correct media height as well
				dragPage.onMediaReady(actualHeight);
			}
		});



		Thread load = new Thread(() -> {
			try {
				Uri uri = HybridAPI.getInstance().getFileContent(item.fileUID).first;

				textureView.post(() -> {
					//MediaItem mediaItem = MediaItem.fromUri(uri);
					MediaItem mediaItem = MediaItem.fromUri(Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"));
					player.setMediaItem(mediaItem);
					player.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT);
					player.prepare();
				});
			} catch (FileNotFoundException | ContentsNotFoundException | ConnectException e) {
				//TODO Load error uri
			}
			startPostponedEnterTransition();
		});
		load.start();
	}

	private void toggleControls() {
		if(controls.isVisible())
			controls.hide();
		else
			controls.show();
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
