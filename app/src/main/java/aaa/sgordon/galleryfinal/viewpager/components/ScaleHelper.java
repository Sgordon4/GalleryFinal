package aaa.sgordon.galleryfinal.viewpager.components;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewPropertyAnimator;

import androidx.fragment.app.Fragment;

public class ScaleHelper extends Fragment {
	ScaleHelperCallback callback;

	View dimBackground;
	View scaleView;

	float initialX, initialY;
	float downX, downY;
	boolean isScaling = false;
	float scaleDistanceThreshold = 150f; // Max distance before scale stops decreasing
	float snapBackRadius = 100f; // Distance to snap back
	private float touchSlop;

	ViewPropertyAnimator mediaScaler;
	ViewPropertyAnimator backgroundDimmer;


	public ScaleHelper(View dimBackground, View scaleView, ScaleHelperCallback callback) {
		this.callback = callback;

		this.dimBackground = dimBackground;
		this.scaleView = scaleView;

		// Run after layout to get center point
		scaleView.post(() -> {
			touchSlop = ViewConfiguration.get(scaleView.getContext()).getScaledTouchSlop();

			initialX = scaleView.getX();
			initialY = scaleView.getY();
		});
	}
	public interface ScaleHelperCallback {
		void onDismiss();
	}

	public boolean isScaling() {
		return isScaling || scaleView.getScaleX() != 1f;
	}


	public boolean onMotionEvent(MotionEvent event) {
		switch (event.getActionMasked()) {
			case MotionEvent.ACTION_DOWN:
				downX = event.getRawX();
				downY = event.getRawY();
				isScaling = false;

				//Cancel any ongoing animations
				if(mediaScaler != null) {
					mediaScaler.cancel();
					mediaScaler = null;
				}
				if(backgroundDimmer != null) {
					backgroundDimmer.cancel();
					backgroundDimmer = null;
				}

				break;
			case MotionEvent.ACTION_MOVE:
				float moveX = event.getRawX();
				float moveY = event.getRawY();

				float deltaX = moveX - downX;
				float deltaY = moveY - downY;

				if (!isScaling()) {
					//Make sure there's enough vertical intent
					//if (Math.abs(deltaY) <= Math.abs(deltaX) || Math.abs(deltaY) < touchSlop)
					if (Math.abs(deltaY) < touchSlop)
						break;

					//If we are not swiping downwards, don't interfere with other gestures
					if(deltaY < 0)
						break;

					downX = moveX;
					downY = moveY;
				}
				isScaling = true;


				float newTranslationX = scaleView.getTranslationX() + (moveX - downX);
				float newTranslationY = scaleView.getTranslationY() + (moveY - downY);

				scaleView.setTranslationX(newTranslationX);
				scaleView.setTranslationY(newTranslationY);

				// Distance from center
				float distance = (float) Math.hypot(newTranslationX, newTranslationY);

				// Calculate scale
				float scale = 1f - Math.min(0.5f, distance / scaleDistanceThreshold * 0.5f); // Min scale 0.5
				scaleView.setScaleX(scale);
				scaleView.setScaleY(scale);


				//Set the background scrim alpha
				//float alpha = 1f - Math.min(0.7f, distance / scaleDistanceThreshold * 0.7f); //Min alpha 0.3
				float alpha = 1f - distance / scaleDistanceThreshold;
				dimBackground.setAlpha(alpha);


				downX = moveX;
				downY = moveY;
				return true;

			case MotionEvent.ACTION_UP:
			case MotionEvent.ACTION_CANCEL:
				if(!isScaling()) break;

				float finalDx = scaleView.getTranslationX();
				float finalDy = scaleView.getTranslationY();
				float totalDistance = (float) Math.hypot(finalDx, finalDy);

				if (totalDistance < snapBackRadius) {
					// Snap back
					mediaScaler = scaleView.animate()
							.translationX(0)
							.translationY(0)
							.scaleX(1f)
							.scaleY(1f)
							.setDuration(300);
					mediaScaler.start();

					backgroundDimmer = dimBackground.animate().alpha(1f).setDuration(300);
					backgroundDimmer.start();
				} else {
					callback.onDismiss();
				}

				isScaling = false;
				return true;
		}
		return false;
	}
}
