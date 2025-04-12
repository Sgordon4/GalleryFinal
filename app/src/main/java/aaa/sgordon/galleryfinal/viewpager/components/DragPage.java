package aaa.sgordon.galleryfinal.viewpager.components;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.motion.widget.MotionLayout;

import aaa.sgordon.galleryfinal.R;

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
		if(interceptor != null) interceptor.onInterceptTouchEvent(event);

		if(childRequestedDisallowInterceptTouchEvent) return false;
		if(event.getPointerCount() > 1) return false;	//Ignore multitouch events

		System.out.println("Intercepting!");
		if(!scaleHelper.isScaling())
			dragHelper.onMotionEvent(event);
		if(!dragHelper.isDragging())
			scaleHelper.onMotionEvent(event);

		return dragHelper.isDragging() || scaleHelper.isScaling();
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		System.out.println("Touching!");
		if(!scaleHelper.isScaling())
			dragHelper.onMotionEvent(event);
		if(!dragHelper.isDragging())
			scaleHelper.onMotionEvent(event);

		if(dragHelper.isDragging() || scaleHelper.isScaling())
			getParent().requestDisallowInterceptTouchEvent(true);

		if(event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL)
			getParent().requestDisallowInterceptTouchEvent(false);

		return true;
	}


	boolean childRequestedDisallowInterceptTouchEvent = false;
	@Override
	public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
		childRequestedDisallowInterceptTouchEvent = disallowIntercept;
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



	private Interceptor interceptor;
	public void setInterceptForPhotoViewsBitchAss(Interceptor interceptor) {
		this.interceptor = interceptor;
	}
	public interface Interceptor {
		void onInterceptTouchEvent(MotionEvent event);
	}
}
































