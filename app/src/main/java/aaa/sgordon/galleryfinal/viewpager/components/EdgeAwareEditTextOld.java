package aaa.sgordon.galleryfinal.viewpager.components;

import android.content.Context;
import android.graphics.Rect;
import android.text.Layout;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.view.inputmethod.InputMethodManager;
import android.widget.OverScroller;

import androidx.appcompat.widget.AppCompatEditText;

public class EdgeAwareEditTextOld extends AppCompatEditText {

	private float startY;
	private float startX;
	private float lastY;
	private boolean gestureDetected = false;
	private boolean allowParentIntercept = false;

	private final int touchSlop;
	private final OverScroller scroller;
	private VelocityTracker velocityTracker;
	private final int minimumFlingVelocity;
	private final int maximumFlingVelocity;

	private final GestureDetector gestureDetector;

	private final Runnable flingRunnable = new Runnable() {
		@Override
		public void run() {
			if (scroller.computeScrollOffset()) {
				scrollTo(0, scroller.getCurrY());
				postOnAnimation(this);
			}
		}
	};

	public EdgeAwareEditTextOld(Context context) {
		this(context, null);
	}

	public EdgeAwareEditTextOld(Context context, AttributeSet attrs) {
		this(context, attrs, android.R.attr.editTextStyle);
	}

	public EdgeAwareEditTextOld(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		setVerticalScrollBarEnabled(true);
		setOverScrollMode(OVER_SCROLL_IF_CONTENT_SCROLLS);
		setFocusableInTouchMode(true);

		ViewConfiguration config = ViewConfiguration.get(context);
		touchSlop = config.getScaledTouchSlop();
		scroller = new OverScroller(context);
		minimumFlingVelocity = config.getScaledMinimumFlingVelocity();
		maximumFlingVelocity = config.getScaledMaximumFlingVelocity();

		gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
			@Override
			public boolean onSingleTapConfirmed(MotionEvent e) {
				System.out.println("Single tap confirmed");
				if (!isFocused()) {
					requestFocus();
					return true; // consume single tap to activate
				}
				return false;
			}
		});
	}


	@Override
	protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
		super.onFocusChanged(focused, direction, previouslyFocusedRect);
		// Enable interaction only when focused

		System.out.println("Focus changed: "+focused);

		if (focused) {
			post(() -> {
				InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
				if (imm != null) {
					imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT);
				}
			});
		}
	}



	@Override
	public boolean onTouchEvent(MotionEvent event) {
		System.out.println(event.getActionMasked());
		if (!isFocused()) {
			gestureDetector.onTouchEvent(event);
			//return true; // Let parent handle unless single tap confirmed
		}

		initVelocityTrackerIfNotExists();
		velocityTracker.addMovement(event);

		int action = event.getActionMasked();

		switch (action) {
			case MotionEvent.ACTION_DOWN:
				startX = event.getX();
				startY = lastY = event.getY();
				gestureDetected = false;
				allowParentIntercept = false;
				getParent().requestDisallowInterceptTouchEvent(true);

				if (!scroller.isFinished()) {
					scroller.abortAnimation();
				}
				break;

			case MotionEvent.ACTION_MOVE:
				float currentY = event.getY();
				float dy = currentY - startY;
				float dx = event.getX() - startX;

				if (!gestureDetected && (Math.abs(dy) > touchSlop || Math.abs(dx) > touchSlop)) {
					gestureDetected = true;

					boolean isVertical = Math.abs(dy) > Math.abs(dx);
					if (isVertical) {
						boolean scrollingUp = dy > 0;
						boolean scrollingDown = dy < 0;

						boolean atTop = !canScrollUp();
						boolean atBottom = !canScrollDown();

						if ((scrollingUp && atTop) || (scrollingDown && atBottom)) {
							System.out.println("Setting true");
							allowParentIntercept = true;
						}
					}
					else {
						if(!isFocused())
							allowParentIntercept = true;
					}
				}

				getParent().requestDisallowInterceptTouchEvent(!allowParentIntercept);
				lastY = currentY;
				break;

			case MotionEvent.ACTION_UP:
				velocityTracker.computeCurrentVelocity(1000, maximumFlingVelocity);
				float velocityY = velocityTracker.getYVelocity();

				if (Math.abs(velocityY) > minimumFlingVelocity) {
					fling((int) -velocityY);
				}

				recycleVelocityTracker();
				gestureDetected = false;
				allowParentIntercept = false;
				getParent().requestDisallowInterceptTouchEvent(false);
				break;

			case MotionEvent.ACTION_CANCEL:
				recycleVelocityTracker();
				gestureDetected = false;
				allowParentIntercept = false;
				getParent().requestDisallowInterceptTouchEvent(false);
				break;
		}

		return super.onTouchEvent(event);
	}

	private void fling(int velocityY) {
		int maxScrollY = computeVerticalScrollRange() - getHeight();
		if (maxScrollY <= 0) return;

		scroller.fling(
				0, getScrollY(),
				0, velocityY,
				0, 0,
				0, maxScrollY
		);

		postOnAnimation(flingRunnable);
	}

	@Override
	public void scrollTo(int x, int y) {
		int maxY = computeVerticalScrollRange() - getHeight();
		y = Math.max(0, Math.min(y, maxY));
		super.scrollTo(x, y);
	}

	private boolean canScrollUp() {
		return getScrollY() > 0;
	}

	private boolean canScrollDown() {
		Layout layout = getLayout();
		if (layout == null) return false;

		int scrollY = getScrollY();
		int maxScrollY = layout.getHeight() - getHeight();
		return scrollY < maxScrollY;
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
