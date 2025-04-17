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


	@Override
	public boolean requestRectangleOnScreen(Rect rectangle, boolean immediate) {
		//Because we use a FrameLayout + top padding to make room for a title (which avoids hiding the title every tap),
		// the top is always too high when selecting the first line. This shit really gotta consider padding, dog.
		if(rectangle.top == 0)
			rectangle.top = getPaddingTop();

		//Because we add a ton of extra padding to the bottom to allow scrolling past the last line,
		// tapping on the last line requests a rectangle with max height, which causes a jump.
		rectangle.bottom = rectangle.top + getLineHeight();


		//Add two line height's worth of space to the bottom to make things look nicer.
		rectangle.bottom += getLineHeight() * 2;


		return super.requestRectangleOnScreen(rectangle, immediate);
	}
}
