package aaa.sgordon.galleryfinal.texteditor;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;

import com.onegravity.rteditor.RTEditText;

public class ObservableRTEditText extends RTEditText {
	public ObservableRTEditText(Context context) {
		super(context);
	}
	public ObservableRTEditText(Context context, AttributeSet attrs) {
		super(context, attrs);
	}
	public ObservableRTEditText(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}


	public interface OnSelectionChangedListener {
		void onSelectionChanged(int selStart, int selEnd);
	}

	private OnSelectionChangedListener selectionListener;

	public void setOnSelectionChangedListener(OnSelectionChangedListener listener) {
		this.selectionListener = listener;
	}

	@Override
	protected void onSelectionChanged(int selStart, int selEnd) {
		super.onSelectionChanged(selStart, selEnd);
		if (selectionListener != null) {
			selectionListener.onSelectionChanged(selStart, selEnd);
		}
	}


	//Because we add a ton of extra padding to the bottom to allow scrolling past the last line,
	// tapping on the last line requests a rectangle with max height, which causes a jump.
	@Override
	public boolean requestRectangleOnScreen(Rect rectangle, boolean immediate) {
		int heightMinusPadding = getHeight() - getPaddingBottom();
		if(rectangle.bottom > heightMinusPadding)
			rectangle.bottom = heightMinusPadding;

		return super.requestRectangleOnScreen(rectangle, immediate);
	}
}
