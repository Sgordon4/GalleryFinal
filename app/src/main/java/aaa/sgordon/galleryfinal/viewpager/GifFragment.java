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
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

import java.io.FileNotFoundException;
import java.net.ConnectException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.databinding.VpViewpageBinding;
import aaa.sgordon.galleryfinal.gallery.ListItem;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridAPI;
import aaa.sgordon.galleryfinal.repository.hybrid.database.HZone;
import aaa.sgordon.galleryfinal.repository.hybrid.types.HFile;
import aaa.sgordon.galleryfinal.viewpager.components.DragPage;
import aaa.sgordon.galleryfinal.viewpager.components.ZoomPanHandler;

public class GifFragment extends Fragment {
	private VpViewpageBinding binding;
	private final ListItem item;

	private DragPage dragPage;
	private ZoomPanHandler zoomPanHandler;


	public GifFragment(ListItem item) {
		this.item = item;
	}


	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		binding = VpViewpageBinding.inflate(inflater, container, false);

		ViewStub mediaStub = binding.mediaStub;
		mediaStub.setLayoutResource(R.layout.vp_image);
		mediaStub.inflate();

		binding.viewA.findViewById(R.id.media).setTransitionName(item.filePath.toString());


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
				creationTime.setText(timeText);



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
				zoningText.setText(backupText);




			} catch (FileNotFoundException | ConnectException e) {
				//Just skip setting some properties
			}
		});
		thread.start();
	}



	private int touchSlop;
	private float downX, downY;

	@SuppressLint("ClickableViewAccessibility")
	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

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
}
