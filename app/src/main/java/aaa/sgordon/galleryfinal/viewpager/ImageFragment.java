package aaa.sgordon.galleryfinal.viewpager;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

import java.io.FileNotFoundException;
import java.net.ConnectException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.databinding.VpViewpageBinding;
import aaa.sgordon.galleryfinal.gallery.ListItem;
import aaa.sgordon.galleryfinal.repository.caches.LinkCache;
import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridAPI;
import aaa.sgordon.galleryfinal.repository.hybrid.database.HZone;
import aaa.sgordon.galleryfinal.repository.hybrid.types.HFile;
import aaa.sgordon.galleryfinal.viewpager.components.DragPage;
import aaa.sgordon.galleryfinal.viewpager.components.ZoomPanHandler;

public class ImageFragment extends Fragment {
	private VpViewpageBinding binding;
	private ViewPageViewModel viewModel;

	private DragPage dragPage;
	private ZoomPanHandler zoomPanHandler;

	private ListItem tempItemDoNotUse;
	public static ImageFragment initialize(ListItem listItem) {
		ImageFragment fragment = new ImageFragment();
		fragment.tempItemDoNotUse = listItem;

		return fragment;
	}


	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		viewModel = new ViewModelProvider(this,
				new ViewPageViewModel.Factory(tempItemDoNotUse))
				.get(ViewPageViewModel.class);

		viewModel.setDataReadyListener(new ViewPageViewModel.DataRefreshedListener() {
			@Override
			public void onDataReady(HFile fileProps, HZone zoning) {
				setBottomSheetInfo();
			}

			@Override
			public void onConnectException() {

			}

			@Override
			public void onFileNotFoundException() {

			}
		});
	}


	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		binding = VpViewpageBinding.inflate(inflater, container, false);

		ViewStub bottomSliderStub = binding.bottomSliderStub;
		bottomSliderStub.setLayoutResource(R.layout.vp_bottom);
		bottomSliderStub.inflate();


		dragPage = binding.motionLayout;
		dragPage.setOnDismissListener(() -> {
			requireParentFragment().getParentFragmentManager().popBackStack();
		});


		setBottomSheetInfo();

		return binding.getRoot();
	}


	public static final int SIZE_THRESHOLD = 1024 * 1024 * 6;	//3MB

	@SuppressLint("ClickableViewAccessibility")
	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		setupTitleAndDescription();

		if(viewModel.fileProps.filesize < SIZE_THRESHOLD)
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

		binding.viewA.findViewById(R.id.media).setTransitionName(viewModel.pathFromRoot.toString());

		ImageView media = binding.viewA.findViewById(R.id.media);
		zoomPanHandler = new ZoomPanHandler(media);

		touchSlop = ViewConfiguration.get(requireContext()).getScaledTouchSlop();
		binding.viewA.setOnTouchListener((v, event) -> {
			unfocusEditTextOnTapOutside(event);

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


		requireParentFragment().startPostponedEnterTransition();

		//Using a custom modelLoader to handle HybridAPI FileUIDs
		Glide.with(media.getContext())
				.load(viewModel.fileUID)
				.listener(new RequestListener<Drawable>() {
					@Override
					public boolean onLoadFailed(@Nullable GlideException e, @Nullable Object model, @NonNull Target<Drawable> target, boolean isFirstResource) {
						//try { requireParentFragment().startPostponedEnterTransition(); }
						//catch (IllegalStateException ignored) {}
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
						zoomPanHandler.setMaxScale(zoom * 4);


						int actualWidth = (int) (viewWidth * scaleX);
						int actualHeight = (int) (viewHeight * scaleY);

						dragPage.onMediaReady(actualHeight);


						//requireParentFragment().startPostponedEnterTransition();
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

		binding.viewA.findViewById(R.id.media).setTransitionName(viewModel.pathFromRoot.toString());


		SubsamplingScaleImageView media = binding.viewA.findViewById(R.id.media);
		media.setOnImageEventListener(new SubsamplingScaleImageView.OnImageEventListener() {
			@Override
			public void onReady() {}
			@Override
			public void onTileLoadError(Exception e) {}
			@Override
			public void onPreviewReleased() {}

			@Override
			public void onPreviewLoadError(Exception e) {
				requireParentFragment().startPostponedEnterTransition();
			}
			@Override
			public void onImageLoadError(Exception e) {
				requireParentFragment().startPostponedEnterTransition();
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
				Uri uri = LinkCache.getInstance().getContentInfo(viewModel.fileUID).first;

				media.post(() -> {
					media.setImage(ImageSource.uri(uri));
				});
			} catch (FileNotFoundException | ContentsNotFoundException | ConnectException e) {
				//TODO Load error uri
			}
		});
		load.start();
	}



	private void setupTitleAndDescription() {
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

		EditText description = binding.viewB.findViewById(R.id.description);
		description.setText(viewModel.description);
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
	}

	private void setBottomSheetInfo() {
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
}