package aaa.sgordon.galleryfinal.viewpager.components;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.TextureView;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

//Thanks ChatGPT for this entire class!
public class VideoTouchHandler implements View.OnTouchListener {

	private final TextureView textureView;
	private final ScaleGestureDetector scaleDetector;
	private final GestureDetector gestureDetector;

	private static final float minScale = 1f;
	private static final float maxScale = 4f;
	private float scaleFactor = 1f;

	private float translationX = 0f;
	private float translationY = 0f;

	public VideoTouchHandler(Context context, TextureView textureView) {
		this.textureView = textureView;

		scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
		gestureDetector = new GestureDetector(context, new GestureListener());

		textureView.setScaleX(scaleFactor);
		textureView.setScaleY(scaleFactor);
		textureView.setTranslationX(translationX);
		textureView.setTranslationY(translationY);
	}

	@Override
	public boolean onTouch(View v, MotionEvent event) {
		v.performClick();

		boolean scaleHandled = scaleDetector.onTouchEvent(event);
		boolean gestureHandled = gestureDetector.onTouchEvent(event);
		return scaleHandled || gestureHandled || event.getAction() == MotionEvent.ACTION_DOWN;
	}

	private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
		@Override
		public boolean onScale(ScaleGestureDetector detector) {
			float prevScale = scaleFactor;
			scaleFactor *= detector.getScaleFactor();
			scaleFactor = Math.max(minScale, Math.min(scaleFactor, maxScale));

			// Adjust scale
			textureView.setScaleX(scaleFactor);
			textureView.setScaleY(scaleFactor);

			// If scaling out, gradually recenter
			if (scaleFactor < prevScale) {
				float centerShiftFactor = (scaleFactor - minScale) / (prevScale - minScale);
				translationX *= centerShiftFactor;
				translationY *= centerShiftFactor;
			}

			applyTranslationClamp();
			return true;
		}
	}

	private class GestureListener extends GestureDetector.SimpleOnGestureListener {
		@Override
		public boolean onScroll(@Nullable MotionEvent e1, @NonNull MotionEvent e2, float distanceX, float distanceY) {
			if (scaleFactor > 1f) {
				translationX -= distanceX;
				translationY -= distanceY;
				applyTranslationClamp();
				return true;
			}
			return false;
		}
	}

	private void applyTranslationClamp() {
		float viewWidth = textureView.getWidth();
		float viewHeight = textureView.getHeight();

		float scaledWidth = viewWidth * scaleFactor;
		float scaledHeight = viewHeight * scaleFactor;

		float maxTranslateX = Math.max(0, (scaledWidth - viewWidth) / 2f);
		float maxTranslateY = Math.max(0, (scaledHeight - viewHeight) / 2f);

		translationX = Math.max(-maxTranslateX, Math.min(translationX, maxTranslateX));
		translationY = Math.max(-maxTranslateY, Math.min(translationY, maxTranslateY));

		textureView.setTranslationX(translationX);
		textureView.setTranslationY(translationY);
	}
}
