package aaa.sgordon.galleryfinal.texteditor;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsAnimationCompat;
import androidx.core.view.WindowInsetsCompat;

import com.onegravity.rteditor.RTEditText;

import java.util.List;

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


	public void setOnSelectionChangedListener(OnSelectionChangedListener listener) {
		this.selectionListener = listener;
	}
	private OnSelectionChangedListener selectionListener;
	public interface OnSelectionChangedListener {
		void onSelectionChanged(int selStart, int selEnd);
	}

	@Override
	protected void onSelectionChanged(int selStart, int selEnd) {
		super.onSelectionChanged(selStart, selEnd);
		if (selectionListener != null) {
			selectionListener.onSelectionChanged(selStart, selEnd);
		}
	}


	//---------------------------------------------------------------------------------------------

	//Sole purpose is to listen to whether the keyboard is opening or closing
	private boolean isKeyboardOpen = false;
	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();

		ViewCompat.setWindowInsetsAnimationCallback(this, new WindowInsetsAnimationCompat
				.Callback(WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
			@NonNull
			@Override
			public WindowInsetsCompat onProgress(@NonNull WindowInsetsCompat insets, @NonNull List<WindowInsetsAnimationCompat> runningAnimations) {
				//Look for an IME animation
				for (WindowInsetsAnimationCompat animation : runningAnimations) {
					if (animation.getTypeMask() == WindowInsetsCompat.Type.ime()) {
						//Wait for the animation to finish
						float progress = animation.getInterpolatedFraction();
						if(progress != 1) break;

						//If the IME bottom is > 0, assume the soft keyboard is open
						Insets imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime());
						isKeyboardOpen = imeInsets.bottom > 0;

						break;
					}
				}
				return insets;
			}
		});
	}

	//Controls panning for windowSoftInputMode
	@Override
	public boolean requestRectangleOnScreen(Rect rectangle, boolean immediate) {
		//Skip panning when the keyboard is closing
		if (isKeyboardOpen) return false;

		//Because we use a FrameLayout + top padding to make room for a title (which avoids hiding the title every tap),
		// the top is always too high when selecting the first line. This shit really gotta consider padding, dog.
		if(rectangle.top == 0)
			rectangle.top = getPaddingTop();

		//Because we add a ton of extra padding to the bottom to allow scrolling past the last line,
		// tapping on the last line requests a rectangle with max height, which causes a jump.
		rectangle.bottom = rectangle.top + getLineHeight();


		//Add three line height's worth of space to the bottom to give more context when auto-panning.
		rectangle.bottom += getLineHeight() * 3;


		return super.requestRectangleOnScreen(rectangle, immediate);
	}
}
