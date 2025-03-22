package aaa.sgordon.galleryfinal.gallery.components;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class RightSpaceDecoration extends RecyclerView.ItemDecoration {
	private final Paint paint;

	public RightSpaceDecoration(int color) {
		paint = new Paint();
		paint.setColor(color);
	}

	@Override
	public void onDraw(@NonNull Canvas canvas, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
		if (parent.getChildCount() == 0) return;

		int childCount = parent.getChildCount();

		// Iterate through each visible child
		for (int i = 0; i < childCount; i++) {
			View itemView = parent.getChildAt(i);
			int itemLeft = itemView.getLeft();
			int itemRight = itemView.getRight();
			int recyclerViewRight = parent.getWidth();
			int top = itemView.getTop();
			int bottom = itemView.getBottom();

			if (itemLeft < recyclerViewRight)
				canvas.drawRect(itemLeft, top, recyclerViewRight, bottom, paint);
			//if (itemRight < recyclerViewRight)
			//	canvas.drawRect(itemRight, top, recyclerViewRight, bottom, paint);
		}
	}
}

