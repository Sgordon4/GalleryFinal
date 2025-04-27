package aaa.sgordon.galleryfinal.viewpager.components;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.XmlResourceParser;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.OverScroller;

import androidx.annotation.XmlRes;
import androidx.constraintlayout.motion.widget.MotionLayout;
import androidx.constraintlayout.motion.widget.MotionScene;
import androidx.constraintlayout.widget.ConstraintSet;

import org.xmlpull.v1.XmlPullParser;

import java.util.HashMap;
import java.util.Map;

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


	private final MotionLayout motionLayout;
	private final ViewGroup viewA;
	private final ViewGroup viewB;


	public DragHelper(MotionLayout motionLayout, ViewGroup viewA, ViewGroup viewB) {
		this.motionLayout = motionLayout;
		this.viewA = viewA;
		this.viewB = viewB;

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

		ViewGroup.MarginLayoutParams marginParams = (ViewGroup.MarginLayoutParams) viewB.getLayoutParams();
		transitionDistance = viewB.getHeight() + marginParams.topMargin + marginParams.bottomMargin + mediaBottomToScreenBottom;

		ConstraintSet startConstraintSet = new ConstraintSet();
		ConstraintSet endConstraintSet = new ConstraintSet();

		//Get the default constraint sets from xml
		startConstraintSet.load(motionLayout.getContext(), R.xml.drag_constraints_start);
		endConstraintSet.load(motionLayout.getContext(), R.xml.drag_constraints_end);
		//startConstraintSet.clone(motionLayout.getConstraintSet(R.id.start));
		//endConstraintSet.clone(motionLayout.getConstraintSet(R.id.end));

		float startTranslationY_B = startConstraintSet.getConstraint(R.id.view_b).transform.translationY;
		float endTranslationY_A = endConstraintSet.getConstraint(R.id.view_a).transform.translationY;


		startConstraintSet.setTranslationY(R.id.view_b, startTranslationY_B + mediaBottomToScreenBottom);
		endConstraintSet.setTranslationY(R.id.view_a, endTranslationY_A - mediaBottomToScreenBottom);
		//endConstraintSet.setTranslationY(R.id.viewB, endTranslationY_B + mediaBottomToScreenBottom - mediaBottomToScreenBottom);

		motionLayout.updateState(R.id.start, startConstraintSet);
		motionLayout.updateState(R.id.end, endConstraintSet);
		//startConstraintSet.applyTo(motionLayout);
		//endConstraintSet.applyTo(motionLayout);


		//Calculate the transition percentage that puts the bottom of Media at thresholdOpen
		mediaBottom = screenHeight/2 + mediaHeight/2;
		progressAtOpen = (mediaBottom - thresholdOpen) / transitionDistance;
	}


	public Map<Integer, ConstraintSet> loadConstraintSetsFromMotionScene(Context context, @XmlRes int motionSceneResId) {
		Map<Integer, ConstraintSet> constraintSetMap = new HashMap<>();

		try {
			XmlResourceParser parser = context.getResources().getXml(motionSceneResId);

			int eventType = parser.getEventType();
			while (eventType != XmlPullParser.END_DOCUMENT) {
				if (eventType == XmlPullParser.START_TAG && "ConstraintSet".equals(parser.getName())) {
					String idStr = parser.getAttributeValue(null, "id");
					if (idStr != null && idStr.startsWith("@+id/")) {
						int resId = context.getResources().getIdentifier(idStr.substring(5), "id", context.getPackageName());
						ConstraintSet set = new ConstraintSet();
						set.load(context, parser);
						constraintSetMap.put(resId, set);
					}
				}
				eventType = parser.next();
			}
			parser.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

		return constraintSetMap;
	}



	//---------------------------------------------------------------------------------------------

	public boolean onInterceptTouchEvent(MotionEvent event) {
		switch (event.getActionMasked()) {
			case MotionEvent.ACTION_DOWN:
				downX = event.getX();
				downY = event.getY();
				startProgress = motionLayout.getProgress();

				//Stop any current fling
				choreographer.removeFrameCallback(flingCallback);
				scroller.abortAnimation();

				//Stop any current snap
				if (snapAnimator != null && snapAnimator.isRunning())
					snapAnimator.cancel();

				velocityTracker.clear();
				velocityTracker.addMovement(event);

				isDragging = false;

				return false;
			case MotionEvent.ACTION_MOVE:
				float moveX = event.getX();
				float moveY = event.getY();
				float deltaX = moveX - downX;
				float deltaY = moveY - downY;

				//Make sure there's enough vertical intent
				if (Math.abs(deltaY) < touchSlop || Math.abs(deltaX) > Math.abs(deltaY)) {
					velocityTracker.addMovement(event);
					return false;
				}

				downX = moveX;
				downY = moveY;

				isDragging = true;
				return true;
			case MotionEvent.ACTION_UP:
			case MotionEvent.ACTION_CANCEL:
				snapToClosestThreshold();
		}
		return false;
	}


	public boolean onTouchEvent(MotionEvent event) {
		float currMediaBottom = mediaBottom + viewA.getTranslationY();

		//Drag the views based on touch
		switch (event.getActionMasked()) {
			case MotionEvent.ACTION_MOVE:
				//If we aren't dragging yet, use the setup logic in onInterceptTouchEvent's ACTION_MOVE case.
				//This can happen if we don't intercept, then the touch event loops back up from bottom because a child didn't take it.
				if(!isDragging()) return onInterceptTouchEvent(event);

				velocityTracker.addMovement(event);

				float dy = downY - event.getY();
				float dPercent = dy / transitionDistance;

				float newProgress = startProgress + dPercent;
				newProgress = Math.max(0f, Math.min(1f, newProgress));
				motionLayout.setProgress(newProgress);

				return true;
			case MotionEvent.ACTION_UP:
			case MotionEvent.ACTION_CANCEL:
				isDragging = false;
		}



		//Fling logic
		switch (event.getActionMasked()) {
			case MotionEvent.ACTION_UP:
			//case MotionEvent.ACTION_CANCEL:	//Don't fling on cancel
				velocityTracker.addMovement(event);

				velocityTracker.computeCurrentVelocity(1000);
				float velocityY = velocityTracker.getYVelocity();

				//If this is not a fling, continue
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
			case MotionEvent.ACTION_UP:
			case MotionEvent.ACTION_CANCEL:
				boolean snapped = snapToClosestThreshold();
				if(snapped) return true;
		}

		return true;
	}




	private boolean snapToClosestThreshold() {
		float currMediaBottom = mediaBottom + viewA.getTranslationY();

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
