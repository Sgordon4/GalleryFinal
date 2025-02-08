package aaa.sgordon.galleryfinal.gallery;

import static android.os.Looper.getMainLooper;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.util.Pair;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;

import java.io.FileNotFoundException;
import java.net.ConnectException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;

public class GalTouchSetup {
	private final RecyclerView recyclerView;
	private final DirRVAdapter adapter;


	public GalTouchSetup(RecyclerView recyclerView, DirRVAdapter adapter) {
		this.recyclerView = recyclerView;
		this.adapter = adapter;
	}



	public ItemReorderCallback makeReorderCallback(DirectoryViewModel dirViewModel, SelectionController.SelectionRegistry registry) {
		return new ItemReorderCallback(recyclerView, (destination, nextItem) -> {
			Thread reorderThread = new Thread(() -> {
				//Get the selected items from the viewModel's list and pass them along
				Set<UUID> selectedItems = new HashSet<>(registry.getSelectedList());
				List<Pair<Path, String>> toMove = new ArrayList<>();

				//Grab the first instance of each selected item in the list
				List<Pair<Path, String>> currList = dirViewModel.flatList.getValue();
				for(int i = 0; i < currList.size(); i++) {
					//Get the UUID of this item
					String UUIDString = currList.get(i).first.getFileName().toString();
					if(UUIDString.equals("END"))
						UUIDString = currList.get(i).first.getParent().getFileName().toString();
					UUID itemUID = UUID.fromString(UUIDString);

					if(selectedItems.contains(itemUID)) {
						toMove.add(currList.get(i));
						selectedItems.remove(itemUID);
					}
				}


				try {
					boolean successful = dirViewModel.moveFiles(destination, nextItem, toMove);
					if(successful) return;

					//If the move was not successful, we want to return the list to how it was before we dragged
					Runnable myRunnable = () -> adapter.setList(dirViewModel.flatList.getValue());
					new Handler(getMainLooper()).post(myRunnable);

				} catch (FileNotFoundException | NotDirectoryException | ContentsNotFoundException |
						 ConnectException e) {
					throw new RuntimeException(e);
				}
			});
			reorderThread.start();
		});
	}



	//TODO Make a custom ItemTouchHelper for this later
	MotionEvent lastEvent = null;
	SelectionController selectionController;

	final Runnable scrollRunnable = new Runnable() {
		@Override
		public void run() {
			if(scrollIfNecessary()) {
				recyclerView.removeCallbacks(scrollRunnable);
				ViewCompat.postOnAnimation(recyclerView, this);

				if(selectionController.isDragSelecting())
					selectionController.dragSelect(recyclerView, lastEvent);
			}
		}
	};

	//TODO It's fine for now, but swap this shit out for a custom interpolateOutOfBoundsScroll in ItemTouchHelper
	private boolean scrollIfNecessary() {
		CoordinatorLayout parent = (CoordinatorLayout) recyclerView.getParent();
		View scrollZoneTop = parent.findViewById(R.id.scroll_zone_top);
		View scrollZoneBottom = parent.findViewById(R.id.scroll_zone_bottom);


		int[] topLocation = new int[2];
		scrollZoneTop.getLocationOnScreen(topLocation);
		int topTop = topLocation[1];
		int topBottom = topLocation[1] + scrollZoneTop.getHeight();

		int[] bottomLocation = new int[2];
		scrollZoneBottom.getLocationOnScreen(bottomLocation);
		int bottomTop = bottomLocation[1];
		int bottomBottom = bottomLocation[1] + scrollZoneBottom.getHeight();

		CustomGridLayoutManager lm = (CustomGridLayoutManager) recyclerView.getLayoutManager();
		boolean enabled = lm.isScrollEnabled();


		if(lastEvent.getRawY() < topBottom) {
			if(!enabled) lm.setScrollEnabled(true);
			recyclerView.scrollBy(0, -5);
			if(!enabled) lm.setScrollEnabled(false);
			return true;
		}
		else if(lastEvent.getRawY() > bottomTop) {
			if(!enabled) lm.setScrollEnabled(true);
			recyclerView.scrollBy(0, 5);
			if(!enabled) lm.setScrollEnabled(false);
			return true;
		}

		return false;
	}



	@SuppressLint("ClickableViewAccessibility")
	public void setupRVListener(SelectionController selectionController, ItemReorderCallback reorderCallback) {
		this.selectionController = selectionController;

		CustomGridLayoutManager lm = (CustomGridLayoutManager) recyclerView.getLayoutManager();
		assert lm != null;

		recyclerView.setOnTouchListener((v, event) -> {
			if(event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
				selectionController.stopDragSelecting();
				recyclerView.removeCallbacks(scrollRunnable);
				lm.setScrollEnabled(true);
			}

			if(event.getAction() == MotionEvent.ACTION_MOVE) {
				if(selectionController.isDragSelecting() || reorderCallback.isDragging()) {
					lastEvent = MotionEvent.obtain(event);
					recyclerView.removeCallbacks(scrollRunnable);
					scrollRunnable.run();
					recyclerView.invalidate();
				}
			}



			return false;
		});
	}



	public DirRVAdapter.AdapterCallbacks makeAdapterCallback(SelectionController selectionController, ItemTouchHelper reorderHelper,
															 MaterialToolbar toolbar, MaterialToolbar selectionToolbar) {
		this.selectionController = selectionController;
		CustomGridLayoutManager lm = (CustomGridLayoutManager) recyclerView.getLayoutManager();
		assert lm != null;

		return new DirRVAdapter.AdapterCallbacks() {
			@Override
			public boolean isItemSelected(UUID fileUID) {
				return selectionController.isSelected(fileUID);
			}


			UUID fileUID = null;
			DirRVAdapter.GalViewHolder holder = null;
			boolean isDoubleTapInProgress = false;

			@Override
			public boolean onHolderMotionEvent(UUID fileUID, DirRVAdapter.GalViewHolder holder, MotionEvent event) {
				//System.out.println("Inside: "+event.getAction());
				if(event.getAction() == MotionEvent.ACTION_DOWN) {
					this.fileUID = fileUID;
					this.holder = holder;
				}

				if(event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
					selectionController.stopDragSelecting();
					recyclerView.removeCallbacks(scrollRunnable);
					lm.setScrollEnabled(true);
				}
				if(event.getAction() == MotionEvent.ACTION_MOVE) {
					if(selectionController.isDragSelecting()) {
						selectionController.dragSelect(recyclerView, event);

						lastEvent = MotionEvent.obtain(event);
						recyclerView.removeCallbacks(scrollRunnable);
						scrollRunnable.run();
						recyclerView.invalidate();
					}
				}


				return detector.onTouchEvent(event);
			}

			final GestureDetector detector = new GestureDetector(recyclerView.getContext(), new GestureDetector.SimpleOnGestureListener() {
				@Override
				public void onLongPress(@NonNull MotionEvent e) {
					selectionController.startSelecting();
					selectionController.selectItem(fileUID);

					toolbar.setVisibility(View.GONE);
					selectionToolbar.setVisibility(View.VISIBLE);

					lm.setScrollEnabled(false);

					if(isDoubleTapInProgress) {
						System.out.println("Double longpress");
						//DoubleTap LongPress triggers a reorder
						isDoubleTapInProgress = false;
						reorderHelper.startDrag(holder);
					}
					else {
						System.out.println("Single longpress");
						//SingleTap LongPress triggers a dragging selection
						selectionController.startDragSelecting(holder.getAdapterPosition());
					}
				}

				@Override
				public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
					if(selectionController.isSelecting())
						selectionController.toggleSelectItem(fileUID);
					return true;
				}

				@Override
				public boolean onDoubleTapEvent(@NonNull MotionEvent e) {
					System.out.println("Doubling");
					if(e.getAction() == MotionEvent.ACTION_DOWN)
						isDoubleTapInProgress = true;

					System.out.println("OOOgha: "+e.getAction());
					return false;
				}

				@Override
				public boolean onDown(@NonNull MotionEvent e) {
					return true;
				}
			});
		};
	}
}
