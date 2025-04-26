package aaa.sgordon.galleryfinal.gallery.touch;

import android.annotation.SuppressLint;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import aaa.sgordon.galleryfinal.gallery.DirRVAdapter;
import aaa.sgordon.galleryfinal.repository.gallery.ListItem;

public class ItemReorderCallback extends ItemTouchHelper.Callback {
	private static final String TAG = "Gal.Reorder";
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
		if(adapterPos == -1 || adapter.list.get(adapterPos).type.equals(ListItem.Type.LINKEND))
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
		ListItem draggedItem = adapter.list.get(draggedItemPos);
		ListItem nextItem = draggedItemPos != adapter.list.size() - 1 ? adapter.list.get(draggedItemPos + 1) : null;


		//Path destination = (nextItem != null) ? nextItem.filePath.getParent() : draggedItem.filePath.getName(0);
		Path destination;
		if(nextItem != null) {
			//If the nextItem is a link end, we want to put draggedItem at the end of the link
			if(nextItem.type.equals(ListItem.Type.LINKEND)) {
				destination = nextItem.pathFromRoot;
				nextItem = null;
			}
			//If nextItem is just a normal item, we want to put draggedItem before nextItem in nextItem's parent
			else {
				destination = nextItem.pathFromRoot.getParent();
			}
		}
		else {
			//The nextItem is only null if draggedItem was moved to the very end of the list,
			// and should therefore be placed in its relative root
			destination = draggedItem.pathFromRoot.getName(0);
		}


		Log.d(TAG, String.format("Dragged item '%s'::'%s'", draggedItem.getPrettyName(), draggedItem.fileUID));
		Log.d(TAG, String.format("Destination %s", destination));
		if(nextItem == null) Log.d("Gal.Reorder", "Next item is null");
		else Log.d(TAG, String.format("Next item '%s'::'%s'", nextItem.getPrettyName(), nextItem.fileUID));


		//We do not want to move links directly inside themselves or things will visually disappear. Exclude any.
		if(destination.startsWith(draggedItem.pathFromRoot)) {
			Log.d(TAG, "Drag failed, not allowed to move items inside themselves");
			//Toast.makeText(recyclerView.getContext(), "Not allowed to move links inside themselves!", Toast.LENGTH_SHORT).show();
			callback.onReorderFailed();
			return;
		}
		//WARNING: If we have links A and B, and link B targets link A, moving link A into link B is allowed,
		// even though it is technically being moved inside of itself.
		//Checking this would require checking the target of draggedItem and destination, which would require network calls,
		// which requires a thread. That would delay the reorder, so I'm just going to allow it.



		//To avoid *most* flickering when moving the dragged item across directories, change its parent to the destination dir
		Path newPath = destination.resolve(draggedItem.pathFromRoot.getFileName());
		ListItem updatedItem = new ListItem.Builder(draggedItem)
				.setFilePath(newPath)
				.setRawName(draggedItem.getRawName()+" ")	//Add a space to force a DiffUtil update	TODO This sucks
				.build();

		if(!newPath.equals(draggedItem.pathFromRoot))
			adapter.list.set(draggedItemPos, updatedItem);

		//System.out.println("OWA OWA");

		callback.onReorderComplete(destination, nextItem);
	}



	public interface ReorderCallback {
		void onReorderComplete(Path destination, ListItem nextItem);
		void onReorderFailed();
	}
}
