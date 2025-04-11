package aaa.sgordon.galleryfinal.viewpager.components;

import android.animation.ValueAnimator;
import android.content.Context;
import android.util.Pair;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.OverScroller;

import androidx.constraintlayout.motion.widget.MotionLayout;
import androidx.constraintlayout.widget.ConstraintSet;

import aaa.sgordon.galleryfinal.R;

public class DragHelper {
	private float mediaBottom;
	private float transitionDistance;

	private float thresholdOpen;
	private float thresholdChange;
	private float progressAtOpen;

	private float downX, downY;
	private float startProgress;

	private final VelocityTracker velocityTracker;
	private final OverScroller scroller;
	private final Choreographer choreographer;
	private final Choreographer.FrameCallback flingCallback;

	private ValueAnimator snapAnimator;

	private boolean isDragging;
	private float touchSlop;


	MotionLayout motionLayout;
	ViewGroup viewA;
	ViewGroup viewB;
	ViewGroup hider;


	public DragHelper(MotionLayout motionLayout, ViewGroup viewA, ViewGroup viewB) {
		this.motionLayout = motionLayout;
		this.viewA = viewA;
		this.viewB = viewB;
		this.hider = viewB.findViewById(R.id.hider);

		Context context = motionLayout.getContext();

		velocityTracker = VelocityTracker.obtain();
		scroller = new OverScroller(context);
		choreographer = Choreographer.getInstance();
		flingCallback = new Choreographer.FrameCallback() {
			@Override
			public void doFrame(long frameTimeNanos) {
				if (scroller.computeScrollOffset()) {
					//Convert current position to progress
					int currY = scroller.getCurrY();
					float newProgress = currY / transitionDistance;

					motionLayout.setProgress(newProgress);
					choreographer.postFrameCallback(this);
				}
			}
		};
	}

	public boolean isActive() {
		return isDragging || motionLayout.getProgress() > 0;
	}
	public boolean isDragging() {
		return isDragging;
	}
	public float getProgress() {
		return motionLayout.getProgress();
	}



	public void onViewCreated() {
		touchSlop = ViewConfiguration.get(motionLayout.getContext()).getScaledTouchSlop();
		touchSlop *= 2;
		onMediaReady(motionLayout.getHeight());
	}

	public void onMediaReady(float mediaHeight) {
		float screenHeight = motionLayout.getHeight();

		//If the media is too small (likely 0 due to error), pretend media is fullscreen
		if(mediaHeight < 4)
			mediaHeight = screenHeight;


		//Threshold Open is 1/3 from top of screen
		//Threshold Change is 3/4 down actual image
		thresholdOpen = screenHeight * (1/3f);
		thresholdChange = screenHeight/2 + mediaHeight/4;



		//We want to place the top of viewB at the bottom of the visible media
		//To do this, shift the translation in the motionConstraints
		float mediaBottomToScreenBottom = mediaHeight/2 - screenHeight/2;

		transitionDistance = viewB.getHeight() + mediaBottomToScreenBottom;

		ConstraintSet startConstraintSet = motionLayout.getConstraintSet(R.id.start);
		ConstraintSet endConstraintSet = motionLayout.getConstraintSet(R.id.end);

		float startTranslationY_B = startConstraintSet.getConstraint(R.id.view_b).transform.translationY;
		float endTranslationY_A = endConstraintSet.getConstraint(R.id.view_a).transform.translationY;

		startConstraintSet.setTranslationY(R.id.view_b, startTranslationY_B + mediaBottomToScreenBottom);
		endConstraintSet.setTranslationY(R.id.view_a, endTranslationY_A - mediaBottomToScreenBottom);
		//endConstraintSet.setTranslationY(R.id.viewB, endTranslationY_B + mediaBottomToScreenBottom - mediaBottomToScreenBottom);

		motionLayout.updateState(R.id.start, startConstraintSet);
		motionLayout.updateState(R.id.end, endConstraintSet);



		//Calculate the transition percentage that puts the bottom of Media at thresholdOpen
		mediaBottom = screenHeight/2 + mediaHeight/2;
		progressAtOpen = (mediaBottom - thresholdOpen) / transitionDistance;



		viewB.getViewTreeObserver().addOnPreDrawListener(() -> {
			float currentAlpha = viewB.getAlpha();

			// You can check or process the alpha value here
			if(currentAlpha == 0)
				hider.setVisibility(View.GONE);
			else if(hider.getVisibility() == View.GONE)
				hider.setVisibility(View.VISIBLE);

			// Return true to continue with the drawing pass, false to cancel
			return true;
		});
	}


	public boolean onMotionEvent(MotionEvent event) {
		float currMediaBottom = mediaBottom + viewA.getTranslationY();

		//Drag the views based on touch
		switch (event.getActionMasked()) {
			case MotionEvent.ACTION_DOWN:
				downX = event.getX();
				downY = event.getY();
				startProgress = motionLayout.getProgress();

				break;
			case MotionEvent.ACTION_MOVE:
				velocityTracker.addMovement(event);

				float moveX = event.getX();
				float moveY = event.getY();

				float deltaX = moveX - downX;
				float deltaY = moveY - downY;

				//If we're not currently dragging...
				if(!isDragging()) {
					//Make sure there's enough vertical intent
					if(Math.abs(deltaY) < touchSlop)// || Math.abs(deltaY) > Math.abs(deltaX))
						return false;

					//If we're at the bottom and dragging down, don't interfere with other gestures
					if (motionLayout.getProgress() == 0f && deltaY > touchSlop)
						return false;


					downX = moveX;
					downY = moveY;

					isDragging = true;
				}


				float dy = downY - event.getY();
				float dPercent = dy / transitionDistance;

				float newProgress = startProgress + dPercent;
				newProgress = Math.max(0f, Math.min(1f, newProgress));
				motionLayout.setProgress(newProgress);

				return true;
			case MotionEvent.ACTION_UP:
			case MotionEvent.ACTION_CANCEL:
				//if(!isDragging) return false;
				isDragging = false;
		}



		//Fling logic
		switch (event.getActionMasked()) {
			case MotionEvent.ACTION_DOWN:
				//Stop any current fling
				choreographer.removeFrameCallback(flingCallback);
				scroller.abortAnimation();

				//Stop any current snap
				if (snapAnimator != null && snapAnimator.isRunning())
					snapAnimator.cancel();

				velocityTracker.clear();
				velocityTracker.addMovement(event);

				break;
			case MotionEvent.ACTION_UP:
			case MotionEvent.ACTION_CANCEL:
				velocityTracker.addMovement(event);

				velocityTracker.computeCurrentVelocity(1000);
				float velocityY = velocityTracker.getYVelocity();

				boolean isFling = Math.abs(velocityY) > ViewConfiguration.get(motionLayout.getContext()).getScaledMinimumFlingVelocity();
				if(!isFling) break;


				//Bottom above threshold Open, perform a normal fling
				if(currMediaBottom < thresholdOpen) {
					//Distance between thresholdOpen and the top of viewB and/or mediaBottom
					float thresholdOpenToViewBTop = mediaBottom - thresholdOpen;

					scroller.fling(
							0,
							(int) -viewA.getTranslationY(),
							0,
							(int) -velocityY,
							0,
							0,
							(int) thresholdOpenToViewBTop,
							(int) transitionDistance
					);

					choreographer.postFrameCallback(flingCallback);
					return true;
				}
				//Bottom below threshold Open
				else if (currMediaBottom > thresholdOpen) {
					boolean flingUp = velocityY < 0;

					if(flingUp) {
						//Snap to Open
						animateSnapTo(progressAtOpen);
					}
					else {
						//Snap to Closed
						animateSnapTo(0);
					}

					return true;
				}
		}



		//Snap between open/closed based on drag ending position
		switch (event.getActionMasked()) {
			case MotionEvent.ACTION_DOWN:
				//If a snap is already in progress, cancel it
				if (snapAnimator != null && snapAnimator.isRunning())
					snapAnimator.cancel();
				break;
			case MotionEvent.ACTION_UP:
			case MotionEvent.ACTION_CANCEL:

				//Bottom below threshold Change
				if (currMediaBottom >= thresholdChange) {
					//Snap to Closed
					animateSnapTo(0);
					return true;
				}
				//Bottom above threshold Change, but below threshold Open
				else if (currMediaBottom < thresholdChange && currMediaBottom > thresholdOpen) {
					//Snap to Open
					animateSnapTo(progressAtOpen);
					return true;
				}
				//Bottom above threshold Open
				//else {//if(mediaBottom <= thresholdOpen) {
					//Do nothing
				//}
		}


		return false;
	}


	private void animateSnapTo(float targetProgress) {
		if (snapAnimator != null && snapAnimator.isRunning())
			snapAnimator.cancel();

		float currentProgress = motionLayout.getProgress();
		snapAnimator = ValueAnimator.ofFloat(currentProgress, targetProgress);
		snapAnimator.setDuration(150);
		snapAnimator.setInterpolator(new DecelerateInterpolator());
		snapAnimator.addUpdateListener(anim -> {
			float p = (float) anim.getAnimatedValue();
			motionLayout.setProgress(p);
		});
		snapAnimator.start();
	}

	public void snapToOpen() {
		animateSnapTo(progressAtOpen);
	}
	public void snapToClosed() {
		animateSnapTo(0);
	}
}
