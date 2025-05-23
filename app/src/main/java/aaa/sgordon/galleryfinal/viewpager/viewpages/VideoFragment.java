package aaa.sgordon.galleryfinal.viewpager.viewpages;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.LegacyPlayerControlView;

import java.io.FileNotFoundException;
import java.net.ConnectException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.databinding.VpViewpageBinding;
import aaa.sgordon.galleryfinal.gallery.components.movecopy.MoveCopyFragment;
import aaa.sgordon.galleryfinal.repository.gallery.ListItem;
import aaa.sgordon.galleryfinal.repository.gallery.caches.LinkCache;
import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;
import aaa.sgordon.galleryfinal.repository.hybrid.database.HZone;
import aaa.sgordon.galleryfinal.repository.hybrid.types.HFile;
import aaa.sgordon.galleryfinal.viewpager.ViewPagerFragment;
import aaa.sgordon.galleryfinal.viewpager.components.DragPage;
import aaa.sgordon.galleryfinal.viewpager.components.ZoomPanHandler;

//TODO Right now we're moving the controls out of the bottom system bar with a 20dp view.
// This will not work if the bottom bar is a different height, or in pretty much any other situation.

@UnstableApi
public class VideoFragment extends Fragment {
	private VpViewpageBinding binding;
	private ViewPageViewModel viewModel;

	private DragPage dragPage;
	private ZoomPanHandler zoomPanHandler;

	private ExoPlayer player;
	private TextureView textureView;
	private LegacyPlayerControlView controls;

	private ListItem tempItemDoNotUse;
	public static VideoFragment initialize(ListItem listItem) {
		VideoFragment fragment = new VideoFragment();
		fragment.tempItemDoNotUse = listItem;

		return fragment;
	}


	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		viewModel = new ViewModelProvider(this,
				new ViewPageViewModel.Factory(tempItemDoNotUse))
				.get(ViewPageViewModel.class);
	}


	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		binding = VpViewpageBinding.inflate(inflater, container, false);

		ViewStub mediaStub = binding.mediaStub;
		mediaStub.setLayoutResource(R.layout.vp_video);
		mediaStub.inflate();

		textureView = binding.viewA.findViewById(R.id.media);
		textureView.setTransitionName(viewModel.pathFromRoot.toString());


		ViewStub bottomSliderStub = binding.bottomSliderStub;
		bottomSliderStub.setLayoutResource(R.layout.vp_bottom);
		bottomSliderStub.inflate();


		dragPage = binding.motionLayout;
		dragPage.setOnDismissListener(() -> {
			requireParentFragment().getParentFragmentManager().popBackStack();
		});


		setupCarousel();
		//setBottomSheetInfo();

		return binding.getRoot();
	}


	private int touchSlop;
	private float downX, downY;

	@SuppressLint("ClickableViewAccessibility")
	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		viewModel.setDataReadyListener(new ViewPageViewModel.DataRefreshedListener() {
			@Override
			public void onConnectException() {}
			@Override
			public void onFileNotFoundException() {}
			@Override
			public void onDataReady(HFile fileProps, HZone zoning) {
				setBottomSheetInfo();
			}
		});

		setupTitle();


		zoomPanHandler = new ZoomPanHandler(textureView);
		zoomPanHandler.setDoubleTapZoomEnabled(false);
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



		touchSlop = ViewConfiguration.get(requireContext()).getScaledTouchSlop();
		binding.viewA.setOnTouchListener((v, event) -> {
			unfocusEditTextOnTapOutside(event);
			detector.onTouchEvent(event);

			if(event.getAction() == MotionEvent.ACTION_DOWN) {
				downX = event.getX();
				downY = event.getY();
			}

			if(dragPage.isActive())
				hideControls();

			if(!dragPage.isActive()) {
				boolean handled = zoomPanHandler.onTouch(v, event);
				handled |= zoomPanHandler.isScaled();

				if(handled) {
					float moveX = event.getX();
					float moveY = event.getY();
					float deltaX = moveX - downX;
					float deltaY = moveY - downY;

					//If we're edge swiping, allow parent to intercept
					if(Math.abs(deltaX) > touchSlop && deltaX < 0 && Math.abs(deltaX) > Math.abs(deltaY) && zoomPanHandler.isEdgeSwipingEnd())
						dragPage.requestDisallowInterceptTouchEvent(false);
					else if(Math.abs(deltaX) > touchSlop && deltaX > 0 && Math.abs(deltaX) > Math.abs(deltaY) && zoomPanHandler.isEdgeSwipingStart())
						dragPage.requestDisallowInterceptTouchEvent(false);
						//else if(Math.abs(deltaY) > touchSlop && zoomPanHandler.isEdgeSwipingY())
						//dragPage.requestDisallowInterceptTouchEvent(false);
					else
						dragPage.requestDisallowInterceptTouchEvent(true);
				}
			}

			return true;
		});
		binding.viewB.setOnTouchListener((v, event) -> {
			unfocusEditTextOnTapOutside(event);
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


				int actualWidth = (int) (viewWidth * scaleX);
				int actualHeight = (int) (viewHeight * scaleY);


				//zoomPanHandler.setMediaDimensions(actualWidth, actualHeight);
				zoomPanHandler.setMediaDimensions((int) videoWidth, (int) videoHeight);

				//Tell DragPage the correct media height as well
				dragPage.onMediaReady(actualHeight);
			}
		});



		Thread load = new Thread(() -> {
			try {
				Uri uri = LinkCache.getInstance().getContentInfo(viewModel.fileUID).first;

				textureView.post(() -> {
					MediaItem mediaItem = MediaItem.fromUri(uri);
					player.setMediaItem(mediaItem);
					player.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT);
					player.prepare();
				});
			} catch (FileNotFoundException | ContentsNotFoundException | ConnectException e) {
				//TODO Load error uri
			}
			requireParentFragment().startPostponedEnterTransition();
		});
		load.start();
	}



	private void toggleControls() {
		if(controls.isVisible())
			hideControls();
		else
			showControls();
	}
	private void hideControls() {
		controls.hide();
	}
	private void showControls() {
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
		viewModel.persistFileNameImmediately();
		viewModel.persistDescriptionImmediately();
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


	//---------------------------------------------------------------------------------------------

	private void setupTitle() {
		//Show the filename in the BottomSheet
		EditText filename = binding.viewB.findViewById(R.id.filename);
		TextView extension = binding.viewB.findViewById(R.id.extension);
		filename.setText(viewModel.fileName);
		filename.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
			@Override
			public void afterTextChanged(Editable s) {}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				//Move the extension visually to the end of the filename
				String text = (s == null) ? "" : s.toString();
				updateExtensionTranslation(text);

				//Persist the filename
				viewModel.fileName = text;
				viewModel.persistFileName();
			}
		});
		extension.setText(viewModel.fileExtension);
		extension.post(() -> {
			updateExtensionTranslation(viewModel.fileName);
		});
	}

	private void setBottomSheetInfo() {
		EditText description = binding.viewB.findViewById(R.id.description);
		description.setText(viewModel.description);
		description.setEnabled(true);
		description.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
			@Override
			public void afterTextChanged(Editable s) {}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				viewModel.description = (s == null) ? "" : s.toString();
				viewModel.persistDescription();
			}
		});


		//Format the creation date
		SimpleDateFormat sdf = new SimpleDateFormat("EEE, MMM d, yyyy • h:mm a");
		Date date = new Date(viewModel.fileProps.createtime*1000);
		String formattedDateTime = sdf.format(date);

		TextView creationTime = binding.viewB.findViewById(R.id.creation_time);
		String timeText = getString(R.string.vp_time, formattedDateTime);
		creationTime.setText(timeText);


		TextView zoningText = binding.viewB.findViewById(R.id.zoning_with_file_size);
		//Format the fileSize and zoning information
		float fileSizeBytes = viewModel.fileProps.filesize;
		float fileSizeMB = fileSizeBytes / 1024f / 1024f;
		String fileSizeString = String.format(Locale.getDefault(), "%.2f", fileSizeMB);

		String zone = "On Device";
		if(viewModel.zoning != null) {
			if (viewModel.zoning.isLocal && viewModel.zoning.isRemote)
				zone = "On Device & Cloud";
			else if (viewModel.zoning.isLocal)
				zone = "On Device";
			else //if (zoning.isRemote)
				zone = "On Cloud";
		}

		String backupText = getString(R.string.vp_backup, zone, fileSizeString);
		zoningText.setText(backupText);
	}

	private void updateExtensionTranslation(String text) {
		EditText filename = binding.viewB.findViewById(R.id.filename);
		TextView extension = binding.viewB.findViewById(R.id.extension);

		if(text.isEmpty())
			text = filename.getHint().toString();

		TextPaint paint = filename.getPaint();
		int textWidth = (int) paint.measureText(text);

		textWidth = Math.min(textWidth, filename.getWidth());

		int parentWidth = ((View) filename.getParent()).getWidth();
		int extensionWidth = extension.getWidth();
		int translationX = (parentWidth - textWidth - extensionWidth);
		extension.setTranslationX(-translationX);
	}


	private void unfocusEditTextOnTapOutside(MotionEvent event) {
		if (event.getAction() == MotionEvent.ACTION_DOWN) {
			View focused = binding.motionLayout.findFocus();
			if (focused instanceof EditText) {
				Rect rect = new Rect();
				focused.getGlobalVisibleRect(rect);
				if (!rect.contains((int) event.getRawX(), (int) event.getRawY())) {
					focused.clearFocus();

					InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
					if (imm != null) {
						imm.hideSoftInputFromWindow(focused.getWindowToken(), 0);
					}
				}
			}
		}
	}


	private void setupCarousel() {
		ViewPagerFragment parent = (ViewPagerFragment) requireParentFragment();
		View share = binding.viewB.findViewById(R.id.share);
		View move = binding.viewB.findViewById(R.id.move);
		View copy = binding.viewB.findViewById(R.id.copy);
		View export = binding.viewB.findViewById(R.id.export);
		View trash = binding.viewB.findViewById(R.id.trash);
		View backup = binding.viewB.findViewById(R.id.backup);

		share.setOnClickListener(v -> {
			parent.menuItemHelper.onShare();
		});
		move.setOnClickListener(v -> {
			MoveCopyFragment frag = parent.menuItemHelper.buildMoveCopy(true);
			parent.getChildFragmentManager().beginTransaction()
					.replace(R.id.viewpager_options_container, frag, MoveCopyFragment.class.getSimpleName())
					.addToBackStack(null)
					.commit();
		});
		copy.setOnClickListener(v -> {
			MoveCopyFragment frag = parent.menuItemHelper.buildMoveCopy(false);
			parent.getChildFragmentManager().beginTransaction()
					.replace(R.id.viewpager_options_container, frag, MoveCopyFragment.class.getSimpleName())
					.addToBackStack(null)
					.commit();
		});
		export.setOnClickListener(v -> {
			parent.menuItemHelper.onExport();
		});
		trash.setOnClickListener(v -> {
			parent.menuItemHelper.onTrash();
		});
		backup.setOnClickListener(v -> {
			parent.menuItemHelper.onBackup();
		});
	}
}
