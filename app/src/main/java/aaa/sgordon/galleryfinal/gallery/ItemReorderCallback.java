package aaa.sgordon.galleryfinal.gallery;

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
import java.util.UUID;

import aaa.sgordon.galleryfinal.repository.hybrid.HybridAPI;

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


		recyclerView.setOnTouchListener((v, event) -> {
			if (event.getAction() == MotionEvent.ACTION_UP) {
				v.performClick(); // Ensure accessibility compliance
			}
			if (event.getAction() == MotionEvent.ACTION_MOVE) {
				lastMoveEvent = MotionEvent.obtain(event);
			}
			return false; 	//Do not consume the event. We only want to spy.
		});
	}


	@Override
	public boolean isLongPressDragEnabled() {
		return true;
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
		//If we're not dragging, do nothing
		if(!isDragging())
			return;


		//Apply the reorder the easy way by leveraging the currently ongoing drag.
		//This actually turned out to be the most effective method for reordering.
		//Lost my mind on the way here though...
		if(lastMoveEvent != null) {
			recyclerView.post(() -> {
				MotionEvent simulatedEvent = MotionEvent.obtain(
						lastMoveEvent.getDownTime(), SystemClock.uptimeMillis(),
						MotionEvent.ACTION_MOVE,
						lastMoveEvent.getX(), lastMoveEvent.getY(),
						lastMoveEvent.getMetaState()
				);

				recyclerView.onTouchEvent(simulatedEvent);
				simulatedEvent.recycle(); // Prevent memory leaks
			});
		}
	}




	public void onDragComplete() {
		Path nextItem = draggedItemPos != adapter.list.size() - 1 ? adapter.list.get(draggedItemPos + 1).first : null;
		Path destination = (nextItem != null) ? nextItem.getParent() : null;

		//This is set up to allow multiple files to be moved for when we add selection
		//When we set that up we might also want to check if filenames are the same before sending them off
		callback.onReorderComplete(destination, nextItem, Collections.singletonList(adapter.list.get(draggedItemPos)));
	}



	public interface ReorderCallback {
		void onReorderComplete(Path destination, Path nextItem, List<Pair<Path, String>> toMove);

		//void cancelDrag();

		//void onRowMoved(int fromPosition, int toPosition);
		//void onRowSelected(RecyclerView.ViewHolder myViewHolder);
		//void onRowClear(RecyclerView.ViewHolder myViewHolder);

	}
}
