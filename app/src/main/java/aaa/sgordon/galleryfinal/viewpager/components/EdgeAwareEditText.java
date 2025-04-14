package aaa.sgordon.galleryfinal.viewpager.components;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.appcompat.widget.AppCompatEditText;
import androidx.core.view.ViewCompat;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.widget.EditText;
import android.widget.OverScroller;

import androidx.core.view.ViewCompat;

public class EdgeAwareEditText extends AppCompatEditText {

	private float lastY;
	private final OverScroller scroller;
	private VelocityTracker velocityTracker;
	private final int minimumFlingVelocity;
	private final int maximumFlingVelocity;

	private final Runnable flingRunnable = new Runnable() {
		@Override
		public void run() {
			if (scroller.computeScrollOffset()) {
				scrollTo(0, scroller.getCurrY());
				ViewCompat.postOnAnimation(EdgeAwareEditText.this, this);
			}
		}
	};

	public EdgeAwareEditText(Context context) {
		this(context, null);
	}

	public EdgeAwareEditText(Context context, AttributeSet attrs) {
		this(context, attrs, android.R.attr.editTextStyle);
	}

	public EdgeAwareEditText(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		scroller = new OverScroller(context);
		ViewConfiguration config = ViewConfiguration.get(context);
		minimumFlingVelocity = config.getScaledMinimumFlingVelocity();
		maximumFlingVelocity = config.getScaledMaximumFlingVelocity();
		setVerticalScrollBarEnabled(true);
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (canScrollVertically()) {
			initVelocityTrackerIfNotExists();
			velocityTracker.addMovement(event);

			switch (event.getActionMasked()) {
				case MotionEvent.ACTION_DOWN:
					lastY = event.getY();
					getParent().requestDisallowInterceptTouchEvent(true);
					if (!scroller.isFinished()) {
						scroller.abortAnimation(); // Stop ongoing fling
					}
					break;

				case MotionEvent.ACTION_MOVE:
					float currentY = event.getY();
					float dy = currentY - lastY;

					boolean scrollingUp = dy > 0;
					boolean scrollingDown = dy < 0;
					boolean atTop = !canScrollVertically(-1);
					boolean atBottom = !canScrollVertically(1);

					if ((scrollingUp && atTop) || (scrollingDown && atBottom)) {
						getParent().requestDisallowInterceptTouchEvent(false);
					} else {
						getParent().requestDisallowInterceptTouchEvent(true);
					}

					lastY = currentY;
					break;

				case MotionEvent.ACTION_UP:
					velocityTracker.computeCurrentVelocity(1000, maximumFlingVelocity);
					float velocityY = velocityTracker.getYVelocity();

					if (Math.abs(velocityY) > minimumFlingVelocity) {
						fling((int) -velocityY); // Negative because Android scrolls down with negative
					}

					recycleVelocityTracker();
					getParent().requestDisallowInterceptTouchEvent(false);
					break;

				case MotionEvent.ACTION_CANCEL:
					recycleVelocityTracker();
					getParent().requestDisallowInterceptTouchEvent(false);
					break;
			}
		}

		return super.onTouchEvent(event);
	}

	private void fling(int velocityY) {
		int maxScrollY = getLayout() != null
				? getLayout().getHeight() - (getHeight() - getPaddingTop() - getPaddingBottom())
				: 0;

		if (maxScrollY <= 0) return;

		scroller.fling(
				0, getScrollY(),         // startX, startY
				0, velocityY,            // velocityX, velocityY
				0, 0,                    // minX, maxX
				0, maxScrollY            // minY, maxY
		);
		ViewCompat.postOnAnimation(this, flingRunnable);
	}

	private boolean canScrollVertically() {
		return ViewCompat.canScrollVertically(this, 1) || ViewCompat.canScrollVertically(this, -1);
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

	@Override
	public void scrollTo(int x, int y) {
		// Clamp scroll range manually
		int maxY = getLayout() != null
				? getLayout().getHeight() - (getHeight() - getPaddingTop() - getPaddingBottom())
				: 0;
		y = Math.max(0, Math.min(y, maxY));
		super.scrollTo(x, y);
	}
}
