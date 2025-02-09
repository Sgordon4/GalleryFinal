package aaa.sgordon.galleryfinal.gallery;

import android.graphics.Canvas;
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
	public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
		return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN | ItemTouchHelper.LEFT| ItemTouchHelper.RIGHT, 0);
	}


	@Override
	public void onSelectedChanged(@Nullable RecyclerView.ViewHolder viewHolder, int actionState) {
		System.out.println("SELECTED CHANGED");
		if(viewHolder != null) {
			int pos = recyclerView.getChildAdapterPosition(viewHolder.itemView);
			startPath = adapter.list.get(pos).first;
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
		//int pos = recyclerView.getChildAdapterPosition(target.itemView);
		//dragSelectTo(pos);

		/*

		String UUIDString = adapter.list.get(pos).first.getFileName().toString();
		if(UUIDString.equals("END"))
			UUIDString = adapter.list.get(pos).first.getParent().getFileName().toString();
		UUID thisFileUID = UUID.fromString(UUIDString);

		selectionController.selectItem(thisFileUID);

		 */

		return false;
	}

	@Override
	public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {}


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

		System.out.println("OnChildDraw");

		/**/
		if (isCurrentlyActive && lastMoveEvent != null) { // Check if dragging

			View target = findTopmostChildUnder(recyclerView, lastMoveEvent.getX(), lastMoveEvent.getY(), viewHolder);
			if(target != null) {
				int pos = recyclerView.getChildAdapterPosition(target);
				System.out.println("Pos = "+pos);
				dragSelectTo(pos);
			}
			else {
				int pos = recyclerView.getChildAdapterPosition(viewHolder.itemView);
				dragSelectTo(pos);
			}
		}
		/**/
	}

	private View findTopmostChildUnder(RecyclerView recyclerView, float x, float y, RecyclerView.ViewHolder draggedViewHolder) {
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
			Path path = adapter.list.get(i).first;
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
			Path path = adapter.list.get(i).first;
			String UUIDString = path.getFileName().toString();
			if(UUIDString.equals("END"))
				UUIDString = path.getParent().getFileName().toString();
			UUID thisFileUID = UUID.fromString(UUIDString);

			selectionController.selectItem(thisFileUID);
			justSelected.add(thisFileUID);
		}

		// Deselect items outside the new range but within the last selected range
		int lastFrom = Math.min(startPos, lastPos);
		int lastTo = Math.max(startPos, lastPos);
		for (int i = lastFrom; i <= lastTo; i++) {
			if (i < from || i > to) {
				Path path = adapter.list.get(i).first;
				String UUIDString = path.getFileName().toString();
				if(UUIDString.equals("END"))
					UUIDString = path.getParent().getFileName().toString();
				UUID thisFileUID = UUID.fromString(UUIDString);

				//Don't deselect an item that was just selected in case there are duplicates (from links)
				if(!justSelected.contains(thisFileUID))
					selectionController.deselectItem(thisFileUID);
			}
		}

		lastPath = adapter.list.get(pos).first;
	}



	//CanDropOver appears to oscillate between the nearest holders, not just the one underneath, so we can't use that
}
