package aaa.sgordon.galleryfinal.gallery;

import android.os.SystemClock;
import android.util.Pair;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.lang.reflect.Array;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ItemReorderCallback extends ItemTouchHelper.Callback {
	private final RecyclerView recyclerView;
	private final DirRVAdapter adapter;


	public ItemReorderCallback(RecyclerView recyclerView) {
		this.recyclerView = recyclerView;
		this.adapter = (DirRVAdapter) recyclerView.getAdapter();
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
	public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
		int dragFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN | ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT;
		return makeMovementFlags(dragFlags, 0);
	}

	@Override
	public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
		//For our case, always true. (e.g. Is this a valid target to move our dragged item over?)
		return true;
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
		System.out.println("Moving item "+list.get(fromPos)+" from "+fromPos+" to "+toPos);

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
		draggedItemPos = -1;
	}







	public List<Pair<Path, String>> applyReorder(List<Pair<Path, String>> newList) {
		//If we're not dragging, do nothing
		if(!isDragging()) {
			System.out.println("New data, but we're not dragging. No reorder applied!");
			adapter.setList(newList);
			return newList;
		}

		System.out.println("Applying reorder");




		int oldVisible = ((LinearLayoutManager) recyclerView.getLayoutManager()).findFirstVisibleItemPosition();
		System.out.println("OldVisible: "+oldVisible);

		int oldDiff = draggedItemPos - oldVisible;
		System.out.println("OldDrag at: "+draggedItemPos);



		Path draggedItem = adapter.list.get(draggedItemPos).first;
		for(int i = 0; i < newList.size(); i++) {
			//System.out.println(i+": "+newList.get(i).second);
			if(newList.get(i).first.equals(draggedItem))
				draggedItemPos = i;
		}


		List<Pair<Path, String>> oldList = new ArrayList<>(adapter.list);



		recyclerView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
			@Override
			public boolean onPreDraw() {
				// Remove the listener after capturing the position (avoid unnecessary calls)
				recyclerView.getViewTreeObserver().removeOnPreDrawListener(this);


				// Get the LayoutManager (e.g., LinearLayoutManager)
				LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();

				// Get the current first visible item position before animation
				int newVisible = layoutManager.findFirstVisibleItemPosition();
				System.out.println("NewVisible: "+newVisible);

				int newDragPos = newVisible + oldDiff;
				System.out.println("NewDrag at: "+newDragPos);




				shift(adapter.list, draggedItemPos, newDragPos);

				for(int i = Math.min(draggedItemPos, newDragPos); i < Math.max(draggedItemPos, newDragPos); i++) {
					adapter.notifyItemChanged(i);
				}
				//adapter.notifyItemMoved(draggedItemPos, newDragPos);

				//shift(newList, draggedItemPos, newDragPos);
				//adapter.setList(newList);

				recyclerView.post(() -> {
					recyclerView.setItemAnimator(new DefaultItemAnimator());
				});




				draggedItemPos = newDragPos;


				// Return true to continue the drawing process
				return true;
			}
		});



		recyclerView.setItemAnimator(null);
		adapter.setList(newList);






		/*

		recyclerView.post(() -> {
			int newVisible = ((LinearLayoutManager) recyclerView.getLayoutManager()).findFirstVisibleItemPosition();
			System.out.println("NewVisible: "+newVisible);

			int newDragPos = newVisible + oldDiff;
			System.out.println("Move to "+newDragPos);

			shift(newList, draggedItemPos, newDragPos);
			adapter.notifyItemMoved(draggedItemPos, newDragPos);
			draggedItemPos = newDragPos;
			//adapter.setList(newList);
			System.out.println(".");
			System.out.println(".");
			System.out.println(".");

		});


		 */



		return newList;

		//If we have an active drag but new data has come in, we want to reorder our dragged item
		// before it's sent to the adapter for display
		//throw new RuntimeException("Stub!");
	}






	@Override
	public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {

	}


	public interface ReorderCallback {
		void cancelDrag();

		void onRowMoved(int fromPosition, int toPosition);
		void onRowSelected(RecyclerView.ViewHolder myViewHolder);
		void onRowClear(RecyclerView.ViewHolder myViewHolder);

	}
}
