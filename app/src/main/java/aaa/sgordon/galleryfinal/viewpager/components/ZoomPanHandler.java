package aaa.sgordon.galleryfinal.viewpager.components;

import android.graphics.Matrix;
import android.util.SizeF;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.TextureView;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ZoomPanHandler implements View.OnTouchListener {

	public interface OnScaleChangedListener {
		void onScaleChanged(float scale);
	}

	private final View mediaView;
	private final ScaleGestureDetector scaleDetector;
	private final GestureDetector gestureDetector;
	private final int touchSlop;

	private float currentScale = 1f;
	private final float minScale = 1f;
	private float midScale = 2.5f;
	private float maxScale = 5f;

	private float currentTranslationX = 0f;
	private float currentTranslationY = 0f;

	private int mediaWidth = -1;
	private int mediaHeight = -1;

	private float lastTouchX;
	private float lastTouchY;
	private boolean isDragging;
	private boolean isScaling;

	private OnScaleChangedListener scaleChangedListener;
	private boolean doubleTapZoomEnabled = false;



	public boolean isDoubleTapZoomEnabled() {
		return doubleTapZoomEnabled;
	}
	public void setDoubleTapZoomEnabled(boolean doubleTapZoomEnabled) {
		this.doubleTapZoomEnabled = doubleTapZoomEnabled;
	}

	public float getMidScale() {
		return midScale;
	}
	public void setMidScale(float midScale) {
		this.midScale = midScale;
	}

	public float getMaxScale() {
		return maxScale;
	}
	public void setMaxScale(float maxScale) {
		this.maxScale = maxScale;
	}


	public void setOnScaleChangedListener(OnScaleChangedListener listener) {
		this.scaleChangedListener = listener;
	}


	public boolean isDragging() {
		return isDragging;
	}
	public boolean isScaling() {
		return isScaling;
	}


	public float getCurrentScale() {
		return currentScale;
	}
	public boolean isScaled() {
		return currentScale != minScale;
	}


	public void resetZoom() {
		currentScale = 1f;
		currentTranslationX = 0f;
		currentTranslationY = 0f;
		applyTransform();

		if (scaleChangedListener != null)
			scaleChangedListener.onScaleChanged(currentScale);
	}


	public void setMediaDimensions(int width, int height) {
		float viewWidth = mediaView.getWidth();
		float viewHeight = mediaView.getHeight();

		if (viewWidth == 0 || viewHeight == 0 || width <= 0 || height <= 0) return;

		//Compute scale
		float mediaAspectRatio = (float) width / (float) height;
		float viewAspectRatio = viewWidth / viewHeight;

		float baseScaleX = 1f;
		float baseScaleY = 1f;

		if (mediaAspectRatio > viewAspectRatio)
			baseScaleY = viewAspectRatio / mediaAspectRatio;
		else
			baseScaleX = mediaAspectRatio / viewAspectRatio;


		Matrix matrix = new Matrix();
		matrix.setScale(baseScaleX, baseScaleY, viewWidth / 2f, viewHeight / 2f);

		if (mediaView instanceof TextureView)
			((TextureView) mediaView).setTransform(matrix);
		else if (mediaView instanceof ImageView)
			((ImageView) mediaView).setImageMatrix(matrix);


		this.mediaWidth = (int) (viewWidth * baseScaleX);
		this.mediaHeight = (int) (viewHeight * baseScaleY);


		applyTransform();
	}




	//---------------------------------------------------------------------------------------------

	public ZoomPanHandler(View mediaView) {
		this.mediaView = mediaView;
		this.touchSlop = ViewConfiguration.get(mediaView.getContext()).getScaledTouchSlop();

		scaleDetector = new ScaleGestureDetector(mediaView.getContext(), new ScaleGestureDetector.SimpleOnScaleGestureListener() {
			@Override
			public boolean onScaleBegin(@NonNull ScaleGestureDetector detector) {
				isScaling = true;
				return true;
			}

			@Override
			public boolean onScale(ScaleGestureDetector detector) {
				float newScale = currentScale * detector.getScaleFactor();
				newScale = Math.max(minScale, Math.min(newScale, maxScale));


				// Compute scale change
				float newFactor = newScale / currentScale;

				// Pivot (focal point of the pinch gesture)
				float focusX = detector.getFocusX() - mediaView.getWidth() / 2f;
				float focusY = detector.getFocusY() - mediaView.getHeight() / 2f;

				// Adjust translation to keep zoom centered on the fingers
				currentTranslationX = newFactor * (currentTranslationX - focusX) + focusX;
				currentTranslationY = newFactor * (currentTranslationY - focusY) + focusY;


				currentScale = newScale;


				applyTransform();

				if (scaleChangedListener != null) {
					scaleChangedListener.onScaleChanged(currentScale);
				}
				return true;
			}

			@Override
			public void onScaleEnd(@NonNull ScaleGestureDetector detector) {
				isScaling = false;
			}
		});

		gestureDetector = new GestureDetector(mediaView.getContext(), new GestureDetector.SimpleOnGestureListener() {
			private int tapCount = 0;
			private long lastTapTime = 0;

			@Override
			public boolean onDoubleTap(@NonNull MotionEvent e) {
				if(!doubleTapZoomEnabled) return false;

				tapCount++;
				long now = System.currentTimeMillis();

				if (now - lastTapTime > 300) {
					tapCount = 1;
				}
				lastTapTime = now;

				float focusX = e.getX();
				float focusY = e.getY();

				float targetScale = (tapCount % 3 == 1) ? midScale : (tapCount % 3 == 2) ? maxScale : minScale;

				float dx = (focusX - currentTranslationX) / currentScale;
				float dy = (focusY - currentTranslationY) / currentScale;

				currentScale = targetScale;
				currentTranslationX = focusX - dx * currentScale;
				currentTranslationY = focusY - dy * currentScale;

				applyTransform();

				if (scaleChangedListener != null) {
					scaleChangedListener.onScaleChanged(currentScale);
				}
				return true;
			}


			@Override
			public boolean onFling(@Nullable MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
				if (currentScale <= 1f) return false;

				final float decay = 0.9f; // damping factor
				final int frameRate = 60; // fps

				Runnable flingRunnable = new Runnable() {
					float vx = velocityX / frameRate;
					float vy = velocityY / frameRate;

					@Override
					public void run() {
						currentTranslationX += vx;
						currentTranslationY += vy;

						// Apply decay
						vx *= decay;
						vy *= decay;

						applyTransform();

						// Stop when velocity is low
						if (Math.abs(vx) > 1f || Math.abs(vy) > 1f) {
							mediaView.postOnAnimation(this);
						}
					}
				};

				mediaView.postOnAnimation(flingRunnable);
				return true;
			}
		});
	}


	@Override
	public boolean onTouch(View v, MotionEvent event) {
		scaleDetector.onTouchEvent(event);
		gestureDetector.onTouchEvent(event);


		switch (event.getActionMasked()) {
			case MotionEvent.ACTION_DOWN:
				lastTouchX = event.getX();
				lastTouchY = event.getY();
				isDragging = false;
				break;

			//Drag, even when scaling
			case MotionEvent.ACTION_MOVE:
				float dx = event.getX() - lastTouchX;
				float dy = event.getY() - lastTouchY;

				if (!isDragging && Math.hypot(dx, dy) > touchSlop) {
					isDragging = true;
				}

				if (isDragging) {
					currentTranslationX += dx;
					currentTranslationY += dy;

					applyTransform();
				}

				lastTouchX = event.getX();
				lastTouchY = event.getY();
				break;

			case MotionEvent.ACTION_UP:
			case MotionEvent.ACTION_CANCEL:
				isDragging = false;
				break;
		}
		return true;
	}


	private SizeF getMaxTranslations() {
		float viewWidth = mediaView.getWidth();
		float viewHeight = mediaView.getHeight();

		float scaledWidth = mediaWidth * currentScale;
		float scaledHeight = mediaHeight * currentScale;

		float maxTranslateX = Math.max(0f, (scaledWidth - viewWidth) / 2f);
		float maxTranslateY = Math.max(0f, (scaledHeight - viewHeight) / 2f);


		System.out.println("Scale: "+currentScale);
		System.out.println("VW/VH: "+viewWidth+" "+viewHeight);
		System.out.println("SW/SH: "+scaledWidth+" "+scaledHeight);
		System.out.println("MTX/MTY: "+maxTranslateX+" "+maxTranslateY);

		return new SizeF(maxTranslateX, maxTranslateY);
	}

	private void applyTransform() {
		SizeF maxTranslations = getMaxTranslations();
		float maxTranslateX = maxTranslations.getWidth();
		float maxTranslateY = maxTranslations.getHeight();

		// Clamp translation to within allowed range
		currentTranslationX = Math.max(-maxTranslateX, Math.min(currentTranslationX, maxTranslateX));
		currentTranslationY = Math.max(-maxTranslateY, Math.min(currentTranslationY, maxTranslateY));

		mediaView.setScaleX(currentScale);
		mediaView.setScaleY(currentScale);
		mediaView.setTranslationX(currentTranslationX);
		mediaView.setTranslationY(currentTranslationY);
	}
}