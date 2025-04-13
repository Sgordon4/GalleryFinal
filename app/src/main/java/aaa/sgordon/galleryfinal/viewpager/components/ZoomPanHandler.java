package aaa.sgordon.galleryfinal.viewpager.components;

import android.animation.ValueAnimator;
import android.graphics.Matrix;
import android.os.SystemClock;
import android.util.SizeF;
import android.view.Choreographer;
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
		void onScaleChanged(float newScale);
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

	private boolean isDragging;
	private boolean isScaling;

	private float flingVelocityX;
	private float flingVelocityY;
	private boolean isFlinging;
	private long lastFrameTimeNanos;

	private OnScaleChangedListener scaleChangedListener;
	private boolean doubleTapZoomEnabled = true;



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
		currentScale = minScale;
		currentTranslationX = 0f;
		currentTranslationY = 0f;
		applyTransform();

		if (scaleChangedListener != null)
			scaleChangedListener.onScaleChanged(currentScale);
	}


	public void setMediaDimensions(int intrinsicWidth, int intrinsicHeight) {
		float viewWidth = mediaView.getWidth();
		float viewHeight = mediaView.getHeight();

		if (viewWidth == 0 || viewHeight == 0 || intrinsicWidth <= 0 || intrinsicHeight <= 0) return;

		//Compute scale
		float mediaAspectRatio = (float) intrinsicWidth / (float) intrinsicHeight;
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

				return true;
			}

			@Override
			public void onScaleEnd(@NonNull ScaleGestureDetector detector) {
				isScaling = false;
			}
		});



		gestureDetector = new GestureDetector(mediaView.getContext(), new GestureDetector.SimpleOnGestureListener() {
			boolean longPress = false;
			float initialTouchX = 0;
			float initialTouchY = 0;

			@Override
			public boolean onDown(@NonNull MotionEvent e) {
				longPress = false;
				initialTouchX = e.getX();
				initialTouchY = e.getY();
				return false;
			}


			@Override
			public void onLongPress(@NonNull MotionEvent e) {
				longPress = true;
			}
			@Override
			public boolean onDoubleTapEvent(@NonNull MotionEvent e) {
				if(!doubleTapZoomEnabled) return false;

				//Wait until the user lifts their finger
				if (e.getAction() != MotionEvent.ACTION_UP) return false;

				//If this is a DoubleTap LongPress, it's a single-pointer zoom gesture and we shouldn't zoom here
				if(longPress) return false;

				//Check if movement exceeds TouchSlop before considering this a double tap
				float deltaX = Math.abs(e.getX() - initialTouchX);
				float deltaY = Math.abs(e.getY() - initialTouchY);

				//If the movement between down and up is larger than TouchSlop, we treat it as a single-pointer zoom gesture too
				if(deltaX > touchSlop || deltaY > touchSlop) return false;


				//Cycle between 3 zoom states when double tapping
				float targetScale;
				if (Math.abs(currentScale - minScale) < 0.1f)
					targetScale = midScale;
				else if (Math.abs(currentScale - midScale) < 0.1f)
					targetScale = maxScale;
				else
					targetScale = minScale;

				float focusX = e.getX() - mediaView.getWidth() / 2f;
				float focusY = e.getY() - mediaView.getHeight() / 2f;

				animateZoom(currentScale, targetScale, focusX, focusY);

				return true;
			}

			@Override
			public boolean onFling(@Nullable MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
				startFling(velocityX / 60f, velocityY / 60f); // pixels per frame (assuming ~60fps)
				return true;
			}
		});
	}

	private int activePointerId = MotionEvent.INVALID_POINTER_ID;
	private float activePointerLastX;
	private float activePointerLastY;
	@Override
	public boolean onTouch(View v, MotionEvent event) {
		scaleDetector.onTouchEvent(event);
		boolean scaleHandled = isScaling;
		boolean gestureHandled = gestureDetector.onTouchEvent(event);


		switch (event.getAction()) {
			case MotionEvent.ACTION_DOWN: {
				activePointerId = event.getPointerId(0);
				activePointerLastX = event.getX();
				activePointerLastY = event.getY();
				isDragging = false;
				stopFling();
				break;
			}

			case MotionEvent.ACTION_POINTER_DOWN: {
				int newIndex = event.getActionIndex();
				activePointerId = event.getPointerId(newIndex);
				activePointerLastX = event.getX(newIndex);
				activePointerLastY = event.getY(newIndex);
				break;
			}


			case MotionEvent.ACTION_MOVE: {
				int pointerIndex = event.findPointerIndex(activePointerId);
				if (pointerIndex == -1) break;

				float x = event.getX(pointerIndex);
				float y = event.getY(pointerIndex);
				float dx = x - activePointerLastX;
				float dy = y - activePointerLastY;


				//if (!isDragging && isScaled() && Math.hypot(dx, dy) > touchSlop) {
				if (!isDragging && isScaled()) {
					isDragging = true;
				}

				if (isDragging) {
					currentTranslationX += dx;
					currentTranslationY += dy;
					applyTransform();
				}

				activePointerLastX = x;
				activePointerLastY = y;
				break;
			}

			case MotionEvent.ACTION_POINTER_UP: {
				int pointerIndex = event.getActionIndex();
				int pointerId = event.getPointerId(pointerIndex);

				if (pointerId == activePointerId) {
					// Switch to a different pointer
					int newIndex = pointerIndex == 0 ? 1 : 0;
					activePointerId = event.getPointerId(newIndex);
					activePointerLastX = event.getX(newIndex);
					activePointerLastY = event.getY(newIndex);
				}
				break;
			}

			case MotionEvent.ACTION_UP:
			case MotionEvent.ACTION_CANCEL: {
				isDragging = false;
				activePointerId = MotionEvent.INVALID_POINTER_ID;
				break;
			}
		}

		return scaleHandled || gestureHandled || isDragging;
	}


	private SizeF getMaxTranslations() {
		float viewWidth = mediaView.getWidth();
		float viewHeight = mediaView.getHeight();

		float scaledWidth = mediaWidth * currentScale;
		float scaledHeight = mediaHeight * currentScale;

		float maxTranslateX = Math.max(0f, (scaledWidth - viewWidth) / 2f);
		float maxTranslateY = Math.max(0f, (scaledHeight - viewHeight) / 2f);

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



	private void startFling(float velocityX, float velocityY) {
		flingVelocityX = velocityX;
		flingVelocityY = velocityY;
		isFlinging = true;
		lastFrameTimeNanos = System.nanoTime();
		Choreographer.getInstance().postFrameCallback(flingRunnable);
	}

	private void stopFling() {
		isFlinging = false;
		Choreographer.getInstance().removeFrameCallback(flingRunnable);
	}

	private final Choreographer.FrameCallback flingRunnable = new Choreographer.FrameCallback() {
		@Override
		public void doFrame(long frameTimeNanos) {
			if (!isFlinging) return;
			float elapsed = (frameTimeNanos - lastFrameTimeNanos) / 1_000_000_000f;
			lastFrameTimeNanos = frameTimeNanos;

			currentTranslationX += flingVelocityX;
			currentTranslationY += flingVelocityY;

			flingVelocityX *= 0.9f;
			flingVelocityY *= 0.9f;

			if (Math.abs(flingVelocityX) < 0.5f && Math.abs(flingVelocityY) < 0.5f) {
				stopFling();
			}

			applyTransform();

			if (isFlinging) {
				Choreographer.getInstance().postFrameCallback(this);
			}
		}
	};



	private void animateZoom(float startScale, float endScale, float focusX, float focusY) {
		final long startTime = SystemClock.uptimeMillis();
		final long duration = 200;

		final float dx = (focusX - currentTranslationX) / currentScale;
		final float dy = (focusY - currentTranslationY) / currentScale;

		Choreographer.getInstance().postFrameCallback(new Choreographer.FrameCallback() {
			@Override
			public void doFrame(long frameTimeNanos) {
				float t = (SystemClock.uptimeMillis() - startTime) / (float) duration;
				if (t >= 1f) {
					currentScale = endScale;
				} else {
					currentScale = startScale + (endScale - startScale) * t;
					Choreographer.getInstance().postFrameCallback(this);
				}

				currentTranslationX = focusX - dx * currentScale;
				currentTranslationY = focusY - dy * currentScale;

				applyTransform();

				if (scaleChangedListener != null) {
					scaleChangedListener.onScaleChanged(currentScale);
				}
			}
		});
	}
}