package aaa.sgordon.galleryfinal.gallery;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Pair;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import aaa.sgordon.galleryfinal.R;

public class DirRVAdapter extends RecyclerView.Adapter<DirRVAdapter.GalViewHolder> {
	public List<Pair<Path, String>> list;
	public RecyclerView.LayoutManager layoutManager;
	private AdapterCallbacks touchCallback;

	public DirRVAdapter() {
		list = new ArrayList<>();
	}
	@Override
	public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
		super.onAttachedToRecyclerView(recyclerView);
		this.layoutManager = recyclerView.getLayoutManager();
	}
	public void setCallbacks(AdapterCallbacks touchCallback) {
		this.touchCallback = touchCallback;
	}


	@Override
	public int getItemCount() {
		return list.size();
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
	public GalViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		View view = LayoutInflater.from(parent.getContext())
				.inflate(R.layout.directory_row_item, parent, false);

		return new GalViewHolder(view);
	}

	@Override
	public void onBindViewHolder(@NonNull GalViewHolder holder, int position) {
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

		holder.fileUID = thisFileUID;

		holder.itemView.setSelected( touchCallback.isItemSelected(thisFileUID) );

		holder.itemView.setOnTouchListener((view, motionEvent) -> {
			System.out.println("At the bottom...");
			if(motionEvent.getAction() == MotionEvent.ACTION_UP)
				view.performClick();

			return touchCallback.onHolderMotionEvent(thisFileUID, holder, motionEvent);
		});
	}


	public interface AdapterCallbacks {
		boolean isItemSelected(UUID fileUID);
		boolean onHolderMotionEvent(UUID fileUID, GalViewHolder holder, MotionEvent event);
	}


	//---------------------------------------------------------------------------------------------

	public static class GalViewHolder extends RecyclerView.ViewHolder {
		private UUID fileUID;
		private final TextView textView;

		public GalViewHolder(@NonNull View itemView) {
			super(itemView);

			textView = itemView.findViewById(R.id.textView);
		}

		public TextView getTextView() {
			return textView;
		}
	}
}
