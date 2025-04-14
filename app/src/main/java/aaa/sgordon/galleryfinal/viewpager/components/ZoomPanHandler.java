package aaa.sgordon.galleryfinal.viewpager.components;

import android.graphics.Matrix;
import android.graphics.PointF;
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

import java.util.HashMap;
import java.util.Map;

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


	public boolean isEdgeSwiping() {
		return edgeSwipingTop || edgeSwipingBottom || edgeSwipingStart || edgeSwipingEnd;
	}
	public boolean isEdgeSwipingX() {
		return edgeSwipingStart || edgeSwipingEnd;
	}
	public boolean isEdgeSwipingY() {
		return edgeSwipingTop || edgeSwipingBottom;
	}
	public boolean isEdgeSwipingTop() {
		return edgeSwipingTop;
	}
	public boolean isEdgeSwipingBottom() {
		return edgeSwipingBottom;
	}
	public boolean isEdgeSwipingStart() {
		return edgeSwipingStart;
	}
	public boolean isEdgeSwipingEnd() {
		return edgeSwipingEnd;
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
			public boolean onScale(@NonNull ScaleGestureDetector detector) {
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


	private boolean edgeSwipingTop;
	private boolean edgeSwipingBottom;
	private boolean edgeSwipingStart;
	private boolean edgeSwipingEnd;

	private final Map<Integer, PointF> activePointers = new HashMap<>();
	private final PointF lastAvgPosition = new PointF();

	@Override
	public boolean onTouch(View v, MotionEvent event) {
		scaleDetector.onTouchEvent(event);
		boolean scaleHandled = isScaling;
		boolean gestureHandled = gestureDetector.onTouchEvent(event);


		switch (event.getActionMasked()) {
			case MotionEvent.ACTION_DOWN: {
				int index = event.getActionIndex();
				int pointerId = event.getPointerId(index);
				activePointers.put(pointerId, new PointF(event.getX(index), event.getY(index)));

				updateLastAveragePosition();

				SizeF maxTranslations = getMaxTranslations();
				edgeSwipingStart = currentTranslationX == maxTranslations.getWidth();
				edgeSwipingEnd = currentTranslationX == -maxTranslations.getWidth();
				edgeSwipingTop = currentTranslationY == maxTranslations.getHeight();
				edgeSwipingBottom = currentTranslationY == -maxTranslations.getHeight();
				break;
			}

			case MotionEvent.ACTION_POINTER_DOWN: {
				int index = event.getActionIndex();
				int pointerId = event.getPointerId(index);
				activePointers.put(pointerId, new PointF(event.getX(index), event.getY(index)));

				updateLastAveragePosition();
				break;
			}


			case MotionEvent.ACTION_MOVE: {

				if (!activePointers.isEmpty()) {
					// Update all pointers' positions
					for (int i = 0; i < event.getPointerCount(); i++) {
						int id = event.getPointerId(i);
						PointF point = activePointers.get(id);
						if (point != null) {
							point.set(event.getX(i), event.getY(i));
						}
					}

					// Calculate average of all active pointers
					float sumX = 0, sumY = 0;
					int count = 0;
					for (PointF point : activePointers.values()) {
						sumX += point.x;
						sumY += point.y;
						count++;
					}

					if (count > 0) {
						float avgX = sumX / count;
						float avgY = sumY / count;

						float dx = avgX - lastAvgPosition.x;
						float dy = avgY - lastAvgPosition.y;

						currentTranslationX += dx;
						currentTranslationY += dy;
						applyTransform();
						lastAvgPosition.set(avgX, avgY);

						SizeF maxTranslations = getMaxTranslations();
						edgeSwipingStart &= currentTranslationX == maxTranslations.getWidth() && dx >= 0;
						edgeSwipingEnd &= currentTranslationX == -maxTranslations.getWidth() && dx <= 0;
						edgeSwipingTop &= currentTranslationY == maxTranslations.getHeight() && dy >= 0;
						edgeSwipingBottom &= currentTranslationY == maxTranslations.getHeight() && dy <= 0;

					}
				}

				break;
			}

			case MotionEvent.ACTION_POINTER_UP: {
				int index = event.getActionIndex();
				int pointerId = event.getPointerId(index);
				activePointers.remove(pointerId);

				updateLastAveragePosition();
				break;
			}

			case MotionEvent.ACTION_UP:
			case MotionEvent.ACTION_CANCEL: {
				isDragging = false;
				int index = event.getActionIndex();
				int pointerId = event.getPointerId(index);
				activePointers.remove(pointerId);

				updateLastAveragePosition();
				break;
			}
		}

		return scaleHandled || gestureHandled || isDragging;
	}


	private void updateLastAveragePosition() {
		if (activePointers.isEmpty()) return;

		float sumX = 0f, sumY = 0f;
		for (PointF point : activePointers.values()) {
			sumX += point.x;
			sumY += point.y;
		}

		lastAvgPosition.set(
				sumX / activePointers.size(),
				sumY / activePointers.size()
		);
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