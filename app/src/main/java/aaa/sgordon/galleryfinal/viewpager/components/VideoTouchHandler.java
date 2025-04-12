package aaa.sgordon.galleryfinal.viewpager.components;

import android.content.Context;
import android.util.SizeF;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.TextureView;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

//TODO Try to get scale and translation to work at the same time

//Thanks ChatGPT for the important parts!
public class VideoTouchHandler implements View.OnTouchListener {

	private final TextureView textureView;
	private final GestureDetector gestureDetector;
	private final ScaleGestureDetector scaleDetector;

	private float lastFocusX = 0f;
	private float lastFocusY = 0f;
	public boolean currentlyScaling = false;

	private static final float minScale = 1f;
	private static final float maxScale = 12f;
	private float scaleFactor = 1f;

	private float translationX = 0f;
	private float translationY = 0f;

	private float mediaWidth;
	private float mediaHeight;

	public VideoTouchHandler(Context context, TextureView textureView) {
		this.textureView = textureView;

		scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
		gestureDetector = new GestureDetector(context, new GestureListener());

		textureView.setScaleX(scaleFactor);
		textureView.setScaleY(scaleFactor);
		textureView.setTranslationX(translationX);
		textureView.setTranslationY(translationY);

		mediaWidth = textureView.getWidth();
		mediaHeight = textureView.getHeight();
	}

	public void setMediaDimens(float width, float height) {
		mediaWidth = width;
		mediaHeight = height;
	}


	public boolean isScaled() {
		return scaleFactor != 1f;
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
			float newFactor = detector.getScaleFactor();

			float newScale = scaleFactor * newFactor;
			newScale = Math.max(minScale, Math.min(newScale, maxScale));

			// Compute scale change
			newFactor = newScale / scaleFactor;

			// Pivot (focal point of the pinch gesture)
			float focusX = detector.getFocusX();
			float focusY = detector.getFocusY();

			// Adjust translation to keep zoom centered on the fingers
			translationX = (translationX - focusX) * newFactor + focusX;
			translationY = (translationY - focusY) * newFactor + focusY;

			scaleFactor = newScale;



			/*

			float prevScale = scaleFactor;
			scaleFactor *= detector.getScaleFactor();
			scaleFactor = Math.max(minScale, Math.min(scaleFactor, maxScale));

			// Adjust scale
			textureView.setScaleX(scaleFactor);
			textureView.setScaleY(scaleFactor);

			// If scaling out and oit of bounds, gradually recenter
			if (scaleFactor < prevScale)
				maybeRecenterIfOutOfBounds();

			applyTransform();
			 */

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
			if (!currentlyScaling && scaleFactor > 1f) {
				translationX -= distanceX;
				translationY -= distanceY;
				applyTransform();
				return true;
			}
			return false;
		}
	}


	private SizeF getMaxTranslations() {
		float viewWidth = textureView.getWidth();
		float viewHeight = textureView.getHeight();

		float scaledWidth = mediaWidth * scaleFactor;
		float scaledHeight = mediaHeight * scaleFactor;

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

		textureView.setScaleX(scaleFactor);
		textureView.setScaleY(scaleFactor);
		textureView.setTranslationX(translationX);
		textureView.setTranslationY(translationY);
	}
}
