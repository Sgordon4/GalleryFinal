package aaa.sgordon.galleryfinal.viewpager.components;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.HorizontalScrollView;
import android.widget.OverScroller;

//Thanks ChatGPT!
public class EdgeAwareHorizontalScrollView extends HorizontalScrollView {

	private float downX, downY;
	private boolean allowParentIntercept = false;
	private boolean gestureDetected = false;
	private final int touchSlop;
	private final OverScroller scroller;
	private VelocityTracker velocityTracker;
	private final int minimumFlingVelocity;
	private final int maximumFlingVelocity;

	private final Runnable flingRunnable = new Runnable() {
		@Override
		public void run() {
			if (scroller.computeScrollOffset()) {
				scrollTo(scroller.getCurrX(), 0);
				postOnAnimation(this);
			}
		}
	};

	public EdgeAwareHorizontalScrollView(Context context) {
		this(context, null);
	}

	public EdgeAwareHorizontalScrollView(Context context, AttributeSet attrs) {
		this(context, attrs, android.R.attr.scrollViewStyle);
	}

	public EdgeAwareHorizontalScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		//setHorizontalScrollBarEnabled(true);

		ViewConfiguration config = ViewConfiguration.get(context);
		touchSlop = config.getScaledTouchSlop();
		scroller = new OverScroller(context);
		minimumFlingVelocity = config.getScaledMinimumFlingVelocity();
		maximumFlingVelocity = config.getScaledMaximumFlingVelocity();
	}


	@Override
	public boolean onInterceptTouchEvent(MotionEvent event) {
		switch (event.getActionMasked()) {
			case MotionEvent.ACTION_DOWN:
				downX = event.getX();
				downY = event.getY();

				if (!scroller.isFinished())
					scroller.abortAnimation();

				initVelocityTrackerIfNotExists();

				gestureDetected = false;
				allowParentIntercept = false;
				getParent().requestDisallowInterceptTouchEvent(true);

				break;
			case MotionEvent.ACTION_MOVE:
				float moveX = event.getX();
				float moveY = event.getY();
				float deltaX = moveX - downX;
				float deltaY = moveY - downY;

				if(Math.abs(deltaY) > touchSlop) {
					getParent().requestDisallowInterceptTouchEvent(false);
				}

				//Make sure there's enough horizontal intent
				if (Math.abs(deltaX) < touchSlop || Math.abs(deltaY) > Math.abs(deltaX)) {
					velocityTracker.addMovement(event);
					return false;
				}

				downX = moveX;
				downY = moveY;

				return true;
		}
		return super.onInterceptTouchEvent(event);
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		velocityTracker.addMovement(event);

		switch (event.getActionMasked()) {
			case MotionEvent.ACTION_MOVE:
				float moveX = event.getX();
				float moveY = event.getY();
				float deltaX = moveX - downX;
				float deltaY = moveY - downY;


				if (!gestureDetected && (Math.abs(deltaX) > touchSlop || Math.abs(deltaY) > touchSlop)) {
					gestureDetected = true;

					boolean isHorizontal = Math.abs(deltaX) > Math.abs(deltaY);
					if (isHorizontal) {
						boolean goingLeft = deltaX < 0;
						boolean goingRight = deltaX > 0;

						boolean atStart = !canScrollLeft();
						boolean atEnd = !canScrollRight();

						if ((goingRight && atStart) || (goingLeft && atEnd)) {
							allowParentIntercept = true;
						}
					}
					else {
						allowParentIntercept = true;
					}
				}

				getParent().requestDisallowInterceptTouchEvent(!allowParentIntercept);
				break;

			case MotionEvent.ACTION_UP:
				velocityTracker.computeCurrentVelocity(1000, maximumFlingVelocity);
				float velocityX = velocityTracker.getXVelocity();

				if (Math.abs(velocityX) > minimumFlingVelocity) {
					fling((int) -velocityX); // -velocity: right fling is positive scroll
				}

			case MotionEvent.ACTION_CANCEL:
				recycleVelocityTracker();
				gestureDetected = false;
				allowParentIntercept = false;
				getParent().requestDisallowInterceptTouchEvent(false);
				break;
		}

		return super.onTouchEvent(event);
	}

	public void fling(int velocityX) {
		int maxScrollX = computeHorizontalScrollRange() - getWidth();
		if (maxScrollX <= 0) return;

		scroller.fling(
				getScrollX(), 0,
				velocityX, 0,
				0, maxScrollX,
				0, 0
		);

		postOnAnimation(flingRunnable);
	}

	@Override
	public void scrollTo(int x, int y) {
		int maxX = computeHorizontalScrollRange() - getWidth();
		x = Math.max(0, Math.min(x, maxX));
		super.scrollTo(x, y);
	}

	private boolean canScrollLeft() {
		return getScrollX() > 0;
	}

	private boolean canScrollRight() {
		View child = getChildAt(0);
		if (child == null) return false;

		int scrollX = getScrollX();
		int childWidth = child.getWidth();
		int viewWidth = getWidth();
		return scrollX < (childWidth - viewWidth);
	}


	private void initVelocityTrackerIfNotExists() {
		if (velocityTracker == null) {
			velocityTracker = VelocityTracker.obtain();
		}
	}
	private void recycleVelocityTracker() {
		if (velocityTracker != null) {
			velocityTracker.recycle();
			velocityTracker = null;
		}
	}
}
