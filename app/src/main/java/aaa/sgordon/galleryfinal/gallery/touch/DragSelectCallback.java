package aaa.sgordon.galleryfinal.gallery.touch;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.gallery.DirRVAdapter;
import aaa.sgordon.galleryfinal.repository.caches.LinkCache;

public class DragSelectCallback extends ItemTouchHelper.Callback {
	RecyclerView recyclerView;
	DirRVAdapter adapter;
	SelectionController selectionController;

	private MotionEvent lastMoveEvent = null;
	private Path startPath = null;
	private Path lastPath = null;


	public DragSelectCallback(RecyclerView recyclerView, DirRVAdapter adapter, SelectionController controller) {
		this.recyclerView = recyclerView;
		this.adapter = adapter;
		this.selectionController = controller;
	}

	public void onMotionEvent(MotionEvent event) {
		if (event.getAction() == MotionEvent.ACTION_MOVE)
			lastMoveEvent = MotionEvent.obtain(event);
		else
			lastMoveEvent = null;
	}

	@Override
	public boolean isLongPressDragEnabled() {
		return false;
	}
	@Override
	public boolean isItemViewSwipeEnabled() {
		return false;
	}
	@Override
	public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {}

	@Override
	public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
		return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN | ItemTouchHelper.LEFT| ItemTouchHelper.RIGHT, 0);
	}


	@Override
	public void onSelectedChanged(@Nullable RecyclerView.ViewHolder viewHolder, int actionState) {
		if(viewHolder != null) {
			int pos = recyclerView.getChildAdapterPosition(viewHolder.itemView);
			startPath = adapter.list.get(pos).filePath;
			lastPath = startPath;
		}
		else {
			lastMoveEvent = null;
			startPath = null;
			lastPath = null;
		}

		super.onSelectedChanged(viewHolder, actionState);
	}

	@Override
	public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
		return false;
	}

	//Scroll faster
	@Override
	public int interpolateOutOfBoundsScroll(@NonNull RecyclerView recyclerView, int viewSize, int viewSizeOutOfBounds, int totalSize, long msSinceStartScroll) {
		msSinceStartScroll *= 2;
		return 2 * super.interpolateOutOfBoundsScroll(recyclerView, viewSize, viewSizeOutOfBounds, totalSize, msSinceStartScroll);
	}

	@Override
	public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView,
							@NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY,
							int actionState, boolean isCurrentlyActive) {

		//Call the superclass method to ensure the normal drawing behavior is preserved
		super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);

		//Get the current view being dragged
		View itemView = viewHolder.itemView;
		View dragActual = itemView.findViewById(R.id.child);

		//Move the child of the dragged view to its original position to visually pretend nothing is being dragged
		dragActual.setTranslationX(-dX);
		dragActual.setTranslationY(-dY);


		/**/
		if (isCurrentlyActive && lastMoveEvent != null) { // Check if dragging
			View target = findTopmostChildUnder(recyclerView, lastMoveEvent.getX(), lastMoveEvent.getY(), viewHolder);
			if(target != null) {
				int pos = recyclerView.getChildAdapterPosition(target);
				dragSelectTo(pos);
			}
		}
		/**/
	}

	private View findTopmostChildUnder(RecyclerView recyclerView, float x, float y, RecyclerView.ViewHolder draggedViewHolder) {
		//Check if we're over our original location
		View itemView = draggedViewHolder.itemView;
		if (x >= itemView.getLeft() && x <= itemView.getRight() &&
				y >= itemView.getTop() && y <= itemView.getBottom()) {
			return draggedViewHolder.itemView;
		}

		//Look at each child to find which (if any) we're over
		for (int i = recyclerView.getChildCount() - 1; i >= 0; i--) { // Iterate from topmost to bottommost
			View child = recyclerView.getChildAt(i);

			if (child == draggedViewHolder.itemView) {
				continue; // Ignore the dragged item
			}
			if (x >= child.getLeft() && x <= child.getRight() &&
					y >= child.getTop() && y <= child.getBottom()) {
				return child; // Found the topmost view under touch
			}
		}

		return null;
	}

	private void dragSelectTo(int pos) {
		int startPos = -1;
		int lastPos = -1;

		for(int i = 0; i < adapter.list.size(); i++) {
			Path path = adapter.list.get(i).filePath;
			if(path.equals(startPath))
				startPos = i;
			if(path.equals(lastPath))
				lastPos = i;
		}
		if(startPos == -1 || lastPos == -1)
			return;


		int from = Math.min(startPos, pos);
		int to = Math.max(startPos, pos);

		Set<UUID> justSelected = new HashSet<>();

		// Select range between 'from' and 'to'
		for (int i = from; i <= to; i++) {
			UUID thisFileUID = adapter.list.get(i).fileUID;
			selectionController.selectItem(thisFileUID);
			justSelected.add(thisFileUID);
		}

		// Deselect items outside the new range but within the last selected range
		int lastFrom = Math.min(startPos, lastPos);
		int lastTo = Math.max(startPos, lastPos);
		for (int i = lastFrom; i <= lastTo; i++) {
			if (i < from || i > to) {
				UUID thisFileUID = adapter.list.get(i).fileUID;

				//Don't deselect an item that was just selected in case there are duplicates (from links)
				if(!justSelected.contains(thisFileUID))
					selectionController.deselectItem(thisFileUID);
			}
		}

		lastPath = adapter.list.get(pos).filePath;
	}



	//CanDropOver appears to oscillate between the nearest holders, not just the one underneath, so we can't use that
}
