package aaa.sgordon.galleryfinal.gallery.touch;

import android.annotation.SuppressLint;
import android.os.SystemClock;
import android.util.Pair;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import aaa.sgordon.galleryfinal.gallery.DirRVAdapter;

public class ItemReorderCallback extends ItemTouchHelper.Callback {
	private final RecyclerView recyclerView;
	private final DirRVAdapter adapter;

	private final ReorderCallback callback;

	private MotionEvent lastMoveEvent;

	@SuppressLint("ClickableViewAccessibility")
	public ItemReorderCallback(RecyclerView recyclerView, ReorderCallback callback) {
		this.recyclerView = recyclerView;
		this.adapter = (DirRVAdapter) recyclerView.getAdapter();

		this.callback = callback;
	}

	public void onMotionEvent(MotionEvent event) {
		if (event.getAction() == MotionEvent.ACTION_MOVE)
			lastMoveEvent = MotionEvent.obtain(event);
	}


	//Drag will be manually triggered upon a double tap drag by our gestureDetector callbacks in DirFragment
	@Override
	public boolean isLongPressDragEnabled() {
		return false;
	}
	@Override
	public boolean isItemViewSwipeEnabled() {
		return false;
	}
	@Override
	public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {

	}

	@Override
	public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
		//Disallow link ends to be dragged
		int adapterPos = recyclerView.getChildAdapterPosition(viewHolder.itemView);
		if(adapterPos == -1 || adapter.list.get(adapterPos).first.getFileName().toString().equals("END"))
			return makeMovementFlags(0, 0);

		int dragFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN | ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT;
		return makeMovementFlags(dragFlags, 0);
	}

	@Override
	public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
		return true;	//Is this a valid target to move our dragged item over? For our case, always true.
	}

	@Override
	public void onMoved(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, int fromPos,
						@NonNull RecyclerView.ViewHolder target, int toPos, int x, int y) {
		super.onMoved(recyclerView, viewHolder, fromPos, target, toPos, x, y);

		shift(adapter.list, fromPos, toPos);
		draggedItemPos = toPos;
		adapter.notifyItemMoved(fromPos, toPos);
	}
	private void shift(List<?> list, int fromPos, int toPos) {
		if(fromPos < toPos)
			for(int i = fromPos; i < toPos; i++)
				Collections.swap(list, i, i+1);
		else
			for(int i = fromPos; i > toPos; i--)
				Collections.swap(list, i, i-1);
	}

	//Scroll faster
	@Override
	public int interpolateOutOfBoundsScroll(@NonNull RecyclerView recyclerView, int viewSize, int viewSizeOutOfBounds, int totalSize, long msSinceStartScroll) {
		msSinceStartScroll *= 2;
		return 2 * super.interpolateOutOfBoundsScroll(recyclerView, viewSize, viewSizeOutOfBounds, totalSize, msSinceStartScroll);
	}


	private int draggedItemPos = -1;
	public boolean isDragging() {
		return draggedItemPos != -1;
	}

	@Override
	public void onSelectedChanged(@Nullable RecyclerView.ViewHolder viewHolder, int actionState) {
		//0 == idle, 1 == swipe, 2 == drag
		if(!isDragging() && actionState == ItemTouchHelper.ACTION_STATE_DRAG)
			draggedItemPos = viewHolder.getAdapterPosition();

		super.onSelectedChanged(viewHolder, actionState);
	}
	@Override
	public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
		super.clearView(recyclerView, viewHolder);
		onDragComplete();
		draggedItemPos = -1;
	}




	//If we have an active drag but new data has come in, we want to put our dragged item in the
	// best-fit location for continuing the drag
	public void applyReorder() {
		//Apply the reorder the easy way by leveraging the currently ongoing drag.
		//This actually turned out to be the most effective method for reordering.
		//Lost my mind on the way here though...
		recyclerView.post(() -> {
			//If we're not dragging, do nothing
			if(isDragging() && lastMoveEvent != null) {
				MotionEvent simulatedEvent = MotionEvent.obtain(
						lastMoveEvent.getDownTime(), SystemClock.uptimeMillis(),
						MotionEvent.ACTION_MOVE,
						lastMoveEvent.getX(), lastMoveEvent.getY(),
						lastMoveEvent.getMetaState()
				);

				recyclerView.onTouchEvent(simulatedEvent);
				simulatedEvent.recycle(); // Prevent memory leaks
			}
		});
	}




	public void onDragComplete() {
		Pair<Path, String> draggedItem = adapter.list.get(draggedItemPos);
		Path nextItem = draggedItemPos != adapter.list.size() - 1 ? adapter.list.get(draggedItemPos + 1).first : null;
		//The nextItem is only null if the dragged item was moved to the end of the list, and should therefore be placed in its relative root
		Path destination = (nextItem != null) ? nextItem.getParent() : draggedItem.first.getName(0);

		//To avoid *most* flickering when moving the dragged item across directories, change its parent to the destination dir
		Path newPath = destination.resolve(draggedItem.first.getFileName());
		Pair<Path, String> updatedItem = new Pair<>(newPath, draggedItem.second+" ");	//Add a space to force a DiffUtil update
		if(!newPath.equals(draggedItem.first))
			adapter.list.set(draggedItemPos, updatedItem);

		//System.out.println("OWA OWA");

		callback.onReorderComplete(destination, nextItem);
	}



	public interface ReorderCallback {
		void onReorderComplete(Path destination, Path nextItem);
	}
}
