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
	private ScaleHelper scaleHelper;
	private DragHelper dragHelper;

	private ViewGroup viewA;
	private ViewGroup viewB;
	private View dimBackground;



	public void onMediaReady(float intrinsicHeight) {
		dragHeightInitialized = true;
		dragHelper.onMediaReady(intrinsicHeight);
	}

	public boolean isActive() {
		return dragHelper.isActive() || dragHelper.isDragging() || scaleHelper.isScaling();
	}

	public void setOnDismissListener(OnDismissListener listener) {
		this.dismissListener = listener;
	}
	public interface OnDismissListener {
		void onDismiss();
	}


	//---------------------------------------------------------------------------------------------

	private boolean dragHeightInitialized = false;
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

		//Make sure DragHelper has a baseline height to work with
		viewA.post(() -> {
			if(!dragHeightInitialized)
				dragHelper.onMediaReady(viewA.getHeight());
		});
	}



	//---------------------------------------------------------------------------------------------

	@Override
	public boolean onInterceptTouchEvent(MotionEvent event) {
		switch (event.getAction()) {
			case MotionEvent.ACTION_DOWN:
			case MotionEvent.ACTION_UP:
			case MotionEvent.ACTION_CANCEL:
				scaleHelper.onInterceptTouchEvent(event);
				dragHelper.onInterceptTouchEvent(event);
				return false;
		}
		if(event.getPointerCount() > 1) return false;	//Ignore multitouch events


		boolean handled = false;
		if(!dragHelper.isActive())
			handled |= scaleHelper.onInterceptTouchEvent(event);
		if(!scaleHelper.isScaling())
			handled |= dragHelper.onInterceptTouchEvent(event);

		return handled;
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		boolean handled = false;
		if(!dragHelper.isActive())
			handled |= scaleHelper.onTouchEvent(event);
		if(!scaleHelper.isScaling())
			handled |= dragHelper.onTouchEvent(event);

		if(dragHelper.isDragging() || scaleHelper.isScaling())
			getParent().requestDisallowInterceptTouchEvent(true);

		return handled;
	}
}
































