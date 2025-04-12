package aaa.sgordon.galleryfinal.viewpager.components;

import android.content.Context;
import android.graphics.Matrix;
import android.util.SizeF;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.TextureView;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

//Thanks ChatGPT for the important parts!
public class VideoTouchHandlerOld implements View.OnTouchListener {

	private final TextureView mediaView;
	private final GestureDetector gestureDetector;
	private final ScaleGestureDetector scaleDetector;

	private float lastFocusX = 0f;
	private float lastFocusY = 0f;
	public boolean currentlyScaling = false;

	private static final float minScale = 1f;
	private static final float midScale = 2.5f;
	private static final float maxScale = 10f;
	private float currentScale = 1f;

	private float translationX = 0f;
	private float translationY = 0f;

	private float mediaWidth;
	private float mediaHeight;

	private final Matrix matrix = new Matrix();
	private final float[] matrixValues = new float[9];

	// Current state
	private float currentTranslationX = 0f;
	private float currentTranslationY = 0f;

	private boolean doubleTapZoomEnabled = false;

	public VideoTouchHandlerOld(Context context, TextureView mediaView) {
		this.mediaView = mediaView;

		scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
		gestureDetector = new GestureDetector(context, new GestureListener());

		mediaView.setScaleX(currentScale);
		mediaView.setScaleY(currentScale);
		mediaView.setTranslationX(translationX);
		mediaView.setTranslationY(translationY);

		mediaWidth = mediaView.getWidth();
		mediaHeight = mediaView.getHeight();
	}

	public void setMediaDimens(float width, float height) {
		mediaWidth = width;
		mediaHeight = height;
	}

	public boolean isDoubleTapZoomEnabled() {
		return doubleTapZoomEnabled;
	}
	public void setDoubleTapZoomEnabled(boolean doubleTapZoomEnabled) {
		this.doubleTapZoomEnabled = doubleTapZoomEnabled;
	}

	public boolean isScaled() {
		return currentScale != 1f;
	}


	@Override
	public boolean onTouch(View v, MotionEvent event) {
		v.performClick();

		scaleDetector.onTouchEvent(event);
		boolean scaleHandled = currentlyScaling;
		boolean gestureHandled = gestureDetector.onTouchEvent(event);


		if (event.getPointerCount() == 2) {
			// During pinch, update position based on finger movement delta
			float focusX = scaleDetector.getFocusX();
			float focusY = scaleDetector.getFocusY();

			if (currentlyScaling) {
				float dx = focusX - lastFocusX;
				float dy = focusY - lastFocusY;
				translationX += dx;
				translationY += dy;
				applyTransform();
			}

			lastFocusX = focusX;
			lastFocusY = focusY;
		}

		if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
			currentlyScaling = false;
		}


		return scaleHandled || gestureHandled;
	}

	private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
		@Override
		public boolean onScaleBegin(@NonNull ScaleGestureDetector detector) {
			currentlyScaling = true;
			lastFocusX = detector.getFocusX();
			lastFocusY = detector.getFocusY();
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
			translationX = newFactor * (translationX - focusX) + focusX;
			translationY = newFactor * (translationY - focusY) + focusY;


			currentScale = newScale;

			maybeRecenterIfOutOfBounds();
			applyTransform();

			return true;
		}

		@Override
		public void onScaleEnd(@NonNull ScaleGestureDetector detector) {
			currentlyScaling = false;
		}
	}

	private class GestureListener extends GestureDetector.SimpleOnGestureListener {
		@Override
		public boolean onScroll(@Nullable MotionEvent e1, @NonNull MotionEvent e2, float distanceX, float distanceY) {
			if (!currentlyScaling && currentScale > 1f) {
				translationX -= distanceX;
				translationY -= distanceY;
				applyTransform();

				/*
				EdgeDirection edgeDir = detectEdge(distanceX, distanceY);
				if (edgeDir != EdgeDirection.NONE)
					System.out.println("Scrolling toward edge: " + edgeDir);
				 */

				return true;
			}
			return false;
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
					translationX += vx;
					translationY += vy;

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

		@Override
		public boolean onDoubleTap(@NonNull MotionEvent e) {
			if(!doubleTapZoomEnabled) return false;

			//Cycle between 3 zoom states when double tapping
			float targetScale;
			if (Math.abs(currentScale - minScale) < 0.1f) {
				targetScale = midScale;
			} else if (Math.abs(currentScale - midScale) < 0.1f) {
				targetScale = maxScale;
			} else {
				targetScale = minScale;
			}

			float focusX = e.getX() - mediaView.getWidth() / 2f;
			float focusY = e.getY() - mediaView.getHeight() / 2f;

			float scaleFactor = targetScale / currentScale;

			translationX = scaleFactor * (translationX - focusX) + focusX;
			translationY = scaleFactor * (translationY - focusY) + focusY;
			currentScale = targetScale;

			maybeRecenterIfOutOfBounds();
			applyTransform();
			return true;
		}
	}


	public enum EdgeDirection {
		NONE, UP, DOWN, LEFT, RIGHT
	}

	public EdgeDirection detectEdge(float dx, float dy) {
		SizeF maxTranslations = getMaxTranslations();

		//If scrolling horizontally...
		if(dx > dy) {
			boolean atLeftEdge = translationX == 0;
			boolean atRightEdge = translationX == maxTranslations.getWidth();
		}

		boolean atLeftEdge = translationX >= maxTranslations.getWidth();
		boolean atRightEdge = translationX <= maxTranslations.getWidth();
		boolean atTopEdge = translationY >= maxTranslations.getHeight();
		boolean atBottomEdge = translationY <= maxTranslations.getHeight();

		if (atLeftEdge && dx < 0 && dx > dy) return EdgeDirection.LEFT;
		if (atRightEdge && dx > 0 && dx > dy) return EdgeDirection.RIGHT;
		if (atTopEdge && dy < 0 && dx < dy) return EdgeDirection.UP;
		if (atBottomEdge && dy > 0 && dx < dy) return EdgeDirection.DOWN;

		return EdgeDirection.NONE;
	}

	public EdgeDirection detectScrollEdgeOld(float dx, float dy) {
		SizeF maxTranslations = getMaxTranslations();
		boolean atLeftEdge = translationX >= maxTranslations.getWidth();
		boolean atRightEdge = translationX <= maxTranslations.getWidth();
		boolean atTopEdge = translationY >= maxTranslations.getHeight();
		boolean atBottomEdge = translationY <= maxTranslations.getHeight();

		if (atLeftEdge && dx < 0) return EdgeDirection.LEFT;
		if (atRightEdge && dx > 0) return EdgeDirection.RIGHT;
		if (atTopEdge && dy < 0) return EdgeDirection.UP;
		if (atBottomEdge && dy > 0) return EdgeDirection.DOWN;

		return EdgeDirection.NONE;
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
	

	private void maybeRecenterIfOutOfBounds() {
		SizeF maxTranslations = getMaxTranslations();
		float maxTranslateX = maxTranslations.getWidth();
		float maxTranslateY = maxTranslations.getHeight();

		boolean outOfBoundsX = Math.abs(translationX) > maxTranslateX;
		boolean outOfBoundsY = Math.abs(translationY) > maxTranslateY;

		if (outOfBoundsX) {
			translationX = Math.signum(translationX) * maxTranslateX;
		}
		if (outOfBoundsY) {
			translationY = Math.signum(translationY) * maxTranslateY;
		}
	}

	private void applyTransform() {
		SizeF maxTranslations = getMaxTranslations();
		float maxTranslateX = maxTranslations.getWidth();
		float maxTranslateY = maxTranslations.getHeight();

		// Clamp translation to within allowed range
		translationX = Math.max(-maxTranslateX, Math.min(translationX, maxTranslateX));
		translationY = Math.max(-maxTranslateY, Math.min(translationY, maxTranslateY));

		mediaView.setScaleX(currentScale);
		mediaView.setScaleY(currentScale);
		mediaView.setTranslationX(translationX);
		mediaView.setTranslationY(translationY);

	}
}
