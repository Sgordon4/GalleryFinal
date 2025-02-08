package aaa.sgordon.galleryfinal.gallery;

import android.content.Context;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;

public class CustomGridLayoutManager extends GridLayoutManager {
	private boolean isScrollEnabled = true;

	public CustomGridLayoutManager(Context context, int spanCount) {
		super(context, spanCount);
	}

	public void setScrollEnabled(boolean flag) {
		this.isScrollEnabled = flag;
	}
	public boolean isScrollEnabled() {
		return isScrollEnabled;
	}

	@Override
	public boolean canScrollVertically() {
		return isScrollEnabled && super.canScrollVertically();
	}
}
