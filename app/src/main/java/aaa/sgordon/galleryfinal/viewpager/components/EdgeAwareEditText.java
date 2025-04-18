package aaa.sgordon.galleryfinal.viewpager.components;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.text.Layout;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.view.inputmethod.InputMethodManager;
import android.widget.OverScroller;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatEditText;

import aaa.sgordon.galleryfinal.R;

public class EdgeAwareEditText extends AppCompatEditText {

	private float downY;
	private float downX;
	private boolean gestureDetected = false;
	private boolean allowParentIntercept = false;

	private boolean edgeScrollCatch = false;

	private final int touchSlop;
	private final OverScroller scroller;
	private VelocityTracker velocityTracker;
	private final int minimumFlingVelocity;
	private final int maximumFlingVelocity;

	private final GestureDetector enabler;
	private final GestureDetector longPressDetector;

	private final Runnable flingRunnable;

	public EdgeAwareEditText(Context context) {
		this(context, null);
	}

	public EdgeAwareEditText(Context context, AttributeSet attrs) {
		this(context, attrs, android.R.attr.editTextStyle);
	}

	public boolean longPress = false;
	public EdgeAwareEditText(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		setVerticalScrollBarEnabled(true);
		setOverScrollMode(OVER_SCROLL_IF_CONTENT_SCROLLS);
		setFocusableInTouchMode(true);

		//Grab the custom edgeScrollCatch param
		if (attrs != null) {
			try (TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.EdgeAwareEditText)) {
				edgeScrollCatch = a.getBoolean(R.styleable.EdgeAwareEditText_edgeScrollCatch, false);
			}
		}


		ViewConfiguration config = ViewConfiguration.get(context);
		touchSlop = config.getScaledTouchSlop();
		scroller = new OverScroller(context);
		minimumFlingVelocity = config.getScaledMinimumFlingVelocity();
		maximumFlingVelocity = config.getScaledMaximumFlingVelocity();

		flingRunnable= new Runnable() {
			@Override
			public void run() {
				if (scroller.computeScrollOffset()) {
					if(isSingleLine())
						scrollTo(scroller.getCurrX(), 0);
					else
						scrollTo(0, scroller.getCurrY());
					postOnAnimation(this);
				}
			}
		};


		enabler = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
			@Override
			public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
				if (!isFocused()) {
					setFocusable(true);               // Make sure it's focusable
					setFocusableInTouchMode(true);    // Allow it to receive touch-based focus
					requestFocus();                   // Request focus
					return true;
				}
				return false;
			}
		});
		longPressDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
			@Override
			public boolean onDown(MotionEvent e) {
				longPress = false;
				return false;
			}
			@Override
			public void onLongPress(MotionEvent e) {
				longPress = true;
			}
		});

		setFocusableInTouchMode(false);
		setFocusable(false);

	}


	@Override
	protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
		super.onFocusChanged(focused, direction, previouslyFocusedRect);
		// Enable interaction only when focused

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
		if (!isFocused())
			enabler.onTouchEvent(event);

		//If LongPressing and EditText has focus, user is selecting text. Do not let parent handle it.
		longPressDetector.onTouchEvent(event);
		if(isFocused() && longPress) {
			getParent().requestDisallowInterceptTouchEvent(true);
			return super.onTouchEvent(event);
		}


		switch (event.getActionMasked()) {
			case MotionEvent.ACTION_DOWN:
				downX = event.getX();
				downY = event.getY();

				//Cancel any currently running fling animation
				if (!scroller.isFinished())
					scroller.abortAnimation();

				initVelocityTrackerIfNotExists();
				velocityTracker.addMovement(event);

				gestureDetected = false;
				allowParentIntercept = false;
				getParent().requestDisallowInterceptTouchEvent(true);
				break;

			case MotionEvent.ACTION_MOVE:
				velocityTracker.addMovement(event);

				float deltaX = event.getX() - downX;
				float deltaY = event.getY() - downY;
				boolean isHorizontal = Math.abs(deltaX) > Math.abs(deltaY);

				boolean swipingLeft = deltaX > 0;
				boolean swipingRight = deltaX < 0;
				boolean swipingUp = deltaY > 0;
				boolean swipingDown = deltaY < 0;

				boolean atStart = !canScrollLeft();
				boolean atEnd = !canScrollRight();
				boolean atTop = !canScrollUp();
				boolean atBottom = !canScrollDown();


				//If a gesture has already been registered, and it was decided that parent should handle it, do nothing
				if(gestureDetected && allowParentIntercept) {
					break;
				}

				//Once we've exceeded touch slop, if we're not catching on edge scroll...
				if(gestureDetected && !edgeScrollCatch) {
					//If we're horizontal, horizontally dragging past an edge, let parent take over
					if(isSingleLine() && isHorizontal && ((atStart && swipingLeft) || (atEnd && swipingRight)) && Math.abs(deltaX) > touchSlop) {
						//allowParentIntercept = true;
					}
					//If we're vertical, vertically dragging past an edge, let parent take over
					else if(!isSingleLine() && !isHorizontal && ((atTop && swipingUp) || (atBottom && swipingDown)) && Math.abs(deltaY) > touchSlop) {
						allowParentIntercept = true;
					}
				}

				//Wait until the drag exceeds touch slop at least once to decide anything
				if(!gestureDetected && (Math.abs(deltaX) > touchSlop || Math.abs(deltaY) > touchSlop)) {
					gestureDetected = true;

					//If the EditText is a horizontal scroller...
					if(isSingleLine()) {
						//Vertical swiping does nothing in a horizontal EditText, allow the parent to take over
						if(!isHorizontal) {
							allowParentIntercept = true;
						}
						//If we've started at an edge and are scrolling past it, let parent take over
						else if ((atStart && swipingLeft) || (atEnd && swipingRight)) {
							allowParentIntercept = !isFocused();
						}
					}
					//If the EditText is a vertical scroller...
					else {
						//Horizontal swiping can select text if the EditText is focused, don't allow parent intercept unless unfocused
						if(isHorizontal) {
							allowParentIntercept = !isFocused();
						}
						//If we've started at an edge and are scrolling past it, let parent take over
						else if ((atTop && swipingUp) || (atBottom && swipingDown)) {
							allowParentIntercept = true;
						}
					}
				}


				getParent().requestDisallowInterceptTouchEvent(!allowParentIntercept);
				break;
			case MotionEvent.ACTION_UP:
				velocityTracker.computeCurrentVelocity(1000, maximumFlingVelocity);


				//if(isSingleLine()) {
				if(isSingleLine() && !isFocused()) {
					float velocityX = velocityTracker.getXVelocity();

					if (Math.abs(velocityX) > minimumFlingVelocity)
						flingHorizontal((int) -velocityX);
				}
				else {
					float velocityY = velocityTracker.getYVelocity();

					if (Math.abs(velocityY) > minimumFlingVelocity)
						flingVertical((int) -velocityY);
				}


				//Don't break, run the following code on UP && CANCEL
			case MotionEvent.ACTION_CANCEL:
				recycleVelocityTracker();
				gestureDetected = false;
				allowParentIntercept = false;
				getParent().requestDisallowInterceptTouchEvent(false);
				break;
		}


		return super.onTouchEvent(event);
	}

	private void flingVertical(int velocityY) {
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

	private void flingHorizontal(int velocityX) {
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
		if (isSingleLine()) {
			int maxX = computeHorizontalScrollRange() - getWidth();
			x = Math.max(0, Math.min(x, maxX));
			super.scrollTo(x, 0);
		} else {
			int maxY = computeVerticalScrollRange() - getHeight();
			y = Math.max(0, Math.min(y, maxY));
			super.scrollTo(0, y);
		}
	}


	private boolean canScrollLeft() {
		return getScrollX() > 0;
	}

	private boolean canScrollRight() {
		Layout layout = getLayout();
		if (layout == null) return false;

		int scrollX = getScrollX();
		int maxScrollX = (int) (layout.getLineWidth(0) - getWidth());
		return scrollX < maxScrollX;
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
