package aaa.sgordon.galleryfinal.viewpager.components;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewPropertyAnimator;

import androidx.fragment.app.Fragment;

public class ScaleHelper extends Fragment {
	private final ScaleHelperCallback callback;

	private final View dimBackground;
	private final View scaleView;

	private float downX, downY;
	private boolean isScaling = false;
	private static final float scaleDistanceThreshold = 150f; // Max distance before scale stops decreasing
	private static final float snapBackRadius = 50f; // Distance to snap back
	private float touchSlop;

	private ViewPropertyAnimator mediaScaler;
	private ViewPropertyAnimator backgroundDimmer;


	public ScaleHelper(View dimBackground, View scaleView, ScaleHelperCallback callback) {
		this.callback = callback;

		this.dimBackground = dimBackground;
		this.scaleView = scaleView;

		// Run after layout to get center point
		scaleView.post(() -> {
			touchSlop = ViewConfiguration.get(scaleView.getContext()).getScaledTouchSlop();
			touchSlop *= 2;
		});
	}
	public interface ScaleHelperCallback {
		void onDismiss();
	}

	public boolean isScaling() {
		return isScaling || isScaled();
	}
	public boolean isScaled() {
		return scaleView.getScaleX() != 1f;
	}


	public boolean onInterceptTouchEvent(MotionEvent event) {
		switch (event.getActionMasked()) {
			case MotionEvent.ACTION_DOWN:
				downX = event.getRawX();
				downY = event.getRawY();
				isScaling = false;

				//Cancel any ongoing animations
				if (mediaScaler != null) {
					mediaScaler.cancel();
					mediaScaler = null;
				}
				if (backgroundDimmer != null) {
					backgroundDimmer.cancel();
					backgroundDimmer = null;
				}

				return false;
			case MotionEvent.ACTION_MOVE:
				if(isScaling()) return true;

				float moveX = event.getRawX();
				float moveY = event.getRawY();
				float deltaX = moveX - downX;
				float deltaY = moveY - downY;

				//Make sure there's enough vertical intent
				if (Math.abs(deltaY) < touchSlop)
					return false;

				//If we are not swiping downwards, don't interfere with other gestures
				if (deltaY < 0 || Math.abs(deltaX) > Math.abs(deltaY))
					return false;

				downX = moveX;
				downY = moveY;

				isScaling = true;
				return true;

			case MotionEvent.ACTION_UP:
			case MotionEvent.ACTION_CANCEL:
				if (isScaled())
					animateSnapBack();
				return false;
		}
		return false;
	}


	public boolean onTouchEvent(MotionEvent event) {

		switch (event.getAction()) {
			case MotionEvent.ACTION_MOVE:
				float moveX = event.getRawX();
				float moveY = event.getRawY();

				float newTranslationX = scaleView.getTranslationX() + (moveX - downX);
				float newTranslationY = scaleView.getTranslationY() + (moveY - downY);

				scaleView.setTranslationX(newTranslationX);
				scaleView.setTranslationY(newTranslationY);


				//Distance from center
				float distance = (float) Math.hypot(newTranslationX, newTranslationY);

				//Calculate scale
				float scale = 1f - Math.min(0.5f, distance / scaleDistanceThreshold * 0.5f); // Min scale 0.5
				scaleView.setScaleX(scale);
				scaleView.setScaleY(scale);

				//Set the background scrim alpha
				float alpha = 1f - distance / scaleDistanceThreshold;
				dimBackground.setAlpha(alpha);


				downX = moveX;
				downY = moveY;
				return true;

			case MotionEvent.ACTION_UP:
			case MotionEvent.ACTION_CANCEL:
				float finalDx = scaleView.getTranslationX();
				float finalDy = scaleView.getTranslationY();
				float totalDistance = (float) Math.hypot(finalDx, finalDy);

				if (totalDistance < snapBackRadius) {
					animateSnapBack();
				} else {
					callback.onDismiss();
				}

				isScaling = false;
				return true;
		}
		//return false;
		return true;
	}

	private void animateSnapBack() {
		mediaScaler = scaleView.animate()
				.translationX(0)
				.translationY(0)
				.scaleX(1f)
				.scaleY(1f)
				.setDuration(300);
		mediaScaler.start();

		backgroundDimmer = dimBackground.animate().alpha(1f).setDuration(300);
		backgroundDimmer.start();
	}
}
