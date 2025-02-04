package aaa.sgordon.galleryfinal.gallery;

import android.content.Context;
import android.util.Pair;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import aaa.sgordon.galleryfinal.R;

public class DirRVAdapter extends RecyclerView.Adapter<DirRVAdapter.ViewHolder> {
	public List<Pair<Path, String>> list;
	public RecyclerView.LayoutManager layoutManager;
	private final SelectionController selectionController;

	public DirRVAdapter(SelectionController controller) {
		list = new ArrayList<>();
		this.selectionController = controller;
	}
	@Override
	public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
		super.onAttachedToRecyclerView(recyclerView);
		this.layoutManager = recyclerView.getLayoutManager();
	}


	public void setList(List<Pair<Path, String>> newList) {
		//Calculate the differences between the current list and the new one
		DiffUtil.Callback diffCallback = new DiffUtil.Callback() {
			@Override
			public int getOldListSize() {
				return list.size();
			}
			@Override
			public int getNewListSize() {
				return newList.size();
			}

			@Override
			public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
				return list.get(oldItemPosition).first.equals(newList.get(newItemPosition).first);
			}
			@Override
			public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
				return list.get(oldItemPosition).second.equals(newList.get(newItemPosition).second);
			}

			//TODO Override getChangePayload if we end up using ItemAnimator
		};
		DiffUtil.DiffResult diffs = DiffUtil.calculateDiff(diffCallback);

		list.clear();
		list.addAll(newList);

		diffs.dispatchUpdatesTo(this);

	}


	@NonNull
	@Override
	public DirRVAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		View view = LayoutInflater.from(parent.getContext())
				.inflate(R.layout.directory_row_item, parent, false);

		return new ViewHolder(view);
	}

	@Override
	public void onBindViewHolder(@NonNull DirRVAdapter.ViewHolder holder, int position) {
		Pair<Path, String> item = list.get(position);

		String level = "";
		if(!(layoutManager instanceof GridLayoutManager || layoutManager instanceof StaggeredGridLayoutManager)) {
			//Put in some fancy printing to show the directory structure
			int depth = item.first.getNameCount()-1;
			level = "│   ".repeat(Math.max(0, depth-1));
			if(depth > 0) {
				if(Objects.equals(item.first.getFileName().toString(), "END"))
					level += "└─ ";
				else
					level += "├─ ";
			}
		}

		holder.getTextView().setText(level + " " + list.get(position).second);


		String UUIDString = list.get(position).first.getFileName().toString();
		if(UUIDString.equals("END"))
			UUIDString = list.get(position).first.getParent().getFileName().toString();

		UUID thisFileUID = UUID.fromString(UUIDString);
		GestureDetector gestureDetector = makeGestureDetector(holder.itemView.getContext(), thisFileUID);

		holder.itemView.setOnTouchListener((view, motionEvent) -> {
			if(motionEvent.getAction() == MotionEvent.ACTION_UP)
				view.performClick();
			return gestureDetector.onTouchEvent(motionEvent);
		});


		holder.itemView.setSelected( selectionController.isSelected(thisFileUID) );

	}

	private GestureDetector makeGestureDetector(@NonNull Context context, @NonNull UUID fileUID) {
		return new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
			@Override
			public void onLongPress(@NonNull MotionEvent e) {
				super.onLongPress(e);
				System.out.println("Longggggg");
				selectionController.startSelecting();
				selectionController.selectItem(fileUID);

			}

			@Override
			public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
				System.out.println("Singletapping");
				if(selectionController.isSelecting())
					selectionController.toggleSelectItem(fileUID);

				return super.onSingleTapConfirmed(e);
			}
		});
	}




	@Override
	public int getItemCount() {
		return list.size();
	}


	//---------------------------------------------------------------------------------------------

	public static class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
		private final TextView textView;

		public ViewHolder(@NonNull View itemView) {
			super(itemView);

			textView = itemView.findViewById(R.id.textView);
			itemView.setOnClickListener(this);
		}

		public TextView getTextView() {
			return textView;
		}

		@Override
		public void onClick(View view) {
			//System.out.println("Clicking "+getAdapterPosition());
		}
	}
}
