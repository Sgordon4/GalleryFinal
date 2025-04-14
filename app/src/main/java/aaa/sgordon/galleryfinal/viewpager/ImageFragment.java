package aaa.sgordon.galleryfinal.viewpager;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.databinding.VpViewpageBinding;
import aaa.sgordon.galleryfinal.gallery.ListItem;
import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridAPI;
import aaa.sgordon.galleryfinal.repository.hybrid.database.HZone;
import aaa.sgordon.galleryfinal.repository.hybrid.types.HFile;
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

		setBottomSheetInfo();


		dragPage = binding.motionLayout;
		dragPage.setOnDismissListener(() -> {
			requireParentFragment().getParentFragmentManager().popBackStack();
		});


		return binding.getRoot();
	}


	private void setBottomSheetInfo() {
		Thread thread = new Thread(() -> {
			try {
				HybridAPI hAPI = HybridAPI.getInstance();
				HFile fileProps = hAPI.getFileProps(item.fileUID);


				//Show the filename
				TextView filename = binding.viewB.findViewById(R.id.filename);
				filename.setText(item.name);



				//Format the creation date
				SimpleDateFormat sdf = new SimpleDateFormat("EEE, MMM d, yyyy • h:mm a");
				Date date = new Date(fileProps.createtime*1000);
				String formattedDateTime = sdf.format(date);

				TextView creationTime = binding.viewB.findViewById(R.id.creation_time);
				String timeText = getString(R.string.vp_time, formattedDateTime);
				creationTime.post(() -> creationTime.setText(timeText));



				//Format the fileSize and zoning information
				float fileSizeBytes = fileProps.filesize;
				float fileSizeMB = fileSizeBytes / 1024f / 1024f;
				String fileSizeString = String.format(Locale.getDefault(), "%.2f", fileSizeMB);

				String zone = "On Device";
				HZone zoning = hAPI.getZoningInfo(item.fileUID);
				if(zoning != null) {
					if (zoning.isLocal && zoning.isRemote)
						zone = "On Device & Cloud";
					else if (zoning.isLocal)
						zone = "On Device";
					else //if (zoning.isRemote)
						zone = "On Cloud";
				}

				TextView zoningText = binding.viewB.findViewById(R.id.zoning_with_file_size);
				String backupText = getString(R.string.vp_backup, zone, fileSizeString);
				zoningText.post(() -> zoningText.setText(backupText));





				LinearLayout share = binding.viewB.findViewById(R.id.share);
				LinearLayout move = binding.viewB.findViewById(R.id.move);
				LinearLayout copy = binding.viewB.findViewById(R.id.copy);
				LinearLayout export = binding.viewB.findViewById(R.id.export);
				LinearLayout trash = binding.viewB.findViewById(R.id.trash);
				LinearLayout backup = binding.viewB.findViewById(R.id.backup);

				share.post(() -> share.setOnClickListener(v -> System.out.println("Share")));
				move.post(() -> move.setOnClickListener(v -> System.out.println("Move")));
				copy.post(() -> copy.setOnClickListener(v -> System.out.println("Copy")));
				export.post(() -> export.setOnClickListener(v -> System.out.println("Export")));
				trash.post(() -> trash.setOnClickListener(v -> System.out.println("Trash")));
				backup.post(() -> backup.setOnClickListener(v -> System.out.println("Backup")));




				/*
				share.post(() -> share.setOnClickListener(v -> {
					v.getParent().requestDisallowInterceptTouchEvent(true);
					System.out.println("AAA");
				}));
				move.post(() -> move.setOnClickListener(v -> {
					v.getParent().requestDisallowInterceptTouchEvent(true);
					System.out.println("AAA");
				}));
				copy.post(() -> copy.setOnClickListener(v -> {
					v.getParent().requestDisallowInterceptTouchEvent(true);
					System.out.println("AAA");
				}));
				export.post(() -> export.setOnClickListener(v -> {
					v.getParent().requestDisallowInterceptTouchEvent(true);
					System.out.println("AAA");
				}));
 				*/


			} catch (FileNotFoundException | ConnectException e) {
				//Just skip setting some properties
			}
		});
		thread.start();
	}




	public static final int SIZE_THRESHOLD = 1024 * 1024 * 3;	//3MB

	@SuppressLint("ClickableViewAccessibility")
	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

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