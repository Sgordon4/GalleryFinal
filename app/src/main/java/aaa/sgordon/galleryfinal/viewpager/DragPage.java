package aaa.sgordon.galleryfinal.viewpager;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.motion.widget.MotionLayout;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.viewpager.components.DragHelper;
import aaa.sgordon.galleryfinal.viewpager.components.ScaleHelper;

public class DragPage extends MotionLayout {
	public DragPage(@NonNull Context context) {
		super(context);
	}
	public DragPage(@NonNull Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
	}
	public DragPage(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}


	private OnDismissListener dismissListener;
	private DragHelper dragHelper;
	private ScaleHelper scaleHelper;

	private ViewGroup viewA;
	private ViewGroup viewB;
	private View dimBackground;

	private boolean childRequestedDisallowTouchIntercept;



	@Override
	protected void onFinishInflate() {
		super.onFinishInflate();

		viewA = findViewById(R.id.view_a);
		viewB = findViewById(R.id.view_b);
		dimBackground = findViewById(R.id.dim_background);

		dragHelper = new DragHelper(this, viewA, viewB);
		scaleHelper = new ScaleHelper(dimBackground, viewA, () -> {
			if(dismissListener != null) dismissListener.onDismiss();
		});
		dragHelper.onViewCreated();
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent event) {
		if(event.getAction() == MotionEvent.ACTION_DOWN)
			getParent().requestDisallowInterceptTouchEvent(false);

		//Additional Intercept listener cause PhotoView's touchListener is broken
		if(interceptTouchListener != null) {
			boolean intercept = interceptTouchListener.onInterceptTouchEvent(event);
			if(intercept) return true;
		}

		if(childRequestedDisallowTouchIntercept)
			return false;

		boolean handlingTouch = false;
		if(!scaleHelper.isScaling())
			handlingTouch = dragHelper.onMotionEvent(event);
		if(!dragHelper.isDragging())
			handlingTouch = scaleHelper.onMotionEvent(event);

		if(handlingTouch)
			getParent().requestDisallowInterceptTouchEvent(true);

		return handlingTouch;
	}


	@Override
	public boolean onTouchEvent(MotionEvent event) {
		getParent().requestDisallowInterceptTouchEvent(true);

		if(!scaleHelper.isScaling())
			dragHelper.onMotionEvent(event);
		if(!dragHelper.isDragging())
			scaleHelper.onMotionEvent(event);

		if(event.getAction() != MotionEvent.ACTION_UP && event.getAction() != MotionEvent.ACTION_CANCEL)
			getParent().requestDisallowInterceptTouchEvent(false);


		return true;
	}



	private InterceptTouchListener interceptTouchListener;
	public void setExtraOnInterceptTouchListener(InterceptTouchListener listener) {
		this.interceptTouchListener = listener;
	}
	public interface InterceptTouchListener {
		boolean onInterceptTouchEvent(MotionEvent event);
	}



	@Override
	public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
		this.childRequestedDisallowTouchIntercept = disallowIntercept;
		super.requestDisallowInterceptTouchEvent(disallowIntercept);
	}

	public boolean isHandlingTouch() {
		return dragHelper.isDragging() || scaleHelper.isScaling();
	}

	public void onMediaReady(float intrinsicHeight) {
		dragHelper.onMediaReady(intrinsicHeight);
	}


	public void setOnDismissListener(OnDismissListener listener) {
		this.dismissListener = listener;
	}
	public interface OnDismissListener {
		void onDismiss();
	}
}
































