package aaa.sgordon.galleryfinal.gallery;

import android.util.Pair;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.gallery.viewholders.DirViewHolder;
import aaa.sgordon.galleryfinal.repository.hybrid.types.HFile;

public class DirRVAdapter extends RecyclerView.Adapter<DirViewHolder> {
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
	public DirViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		View view = LayoutInflater.from(parent.getContext())
				.inflate(R.layout.dir_vh_unknown, parent, false);

		return new DirViewHolder(view);
	}


	//TODO Setup getItemViewType and actually use the new ViewHolders

	@Override
	public void onBindViewHolder(@NonNull DirViewHolder holder, int position) {
		Pair<Path, String> item = list.get(position);

		String UUIDString = list.get(position).first.getFileName().toString();
		if(UUIDString.equals("END"))
			UUIDString = list.get(position).first.getParent().getFileName().toString();
		UUID fileUID = UUID.fromString(UUIDString);


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


		holder.bind(fileUID, level + list.get(position).second);


		holder.itemView.setSelected( touchCallback.isItemSelected(fileUID) );

		holder.itemView.setOnTouchListener((view, motionEvent) -> {
			if(motionEvent.getAction() == MotionEvent.ACTION_UP)
				view.performClick();

			return touchCallback.onHolderMotionEvent(fileUID, holder, motionEvent);
		});
	}


	@Override
	public int getItemViewType(int position) {
		boolean isEnd = false;
		String UUIDString = list.get(position).first.getFileName().toString();
		if(UUIDString.equals("END")) {
			isEnd = true;
			UUIDString = list.get(position).first.getParent().getFileName().toString();
		}
		UUID fileUID = UUID.fromString(UUIDString);

		try {
			HFile fileProps = touchCallback.getProps(fileUID);

			if(fileProps.isdir) {
				if(!fileProps.islink)
					return 0;			//Directory
				else {
					if(!isEnd)
						return 1;		//Link to directory
					else
						return 2;		//End of link to directory
				}
			}


			//Get the filename extension, maybe we need fileNameUtils for this idk



		} catch (FileNotFoundException e) {
			return -1;				//Unknown
		}
		return -1;					//Unknown
	}




	public interface AdapterCallbacks {
		boolean isItemSelected(UUID fileUID);
		boolean onHolderMotionEvent(UUID fileUID, DirViewHolder holder, MotionEvent event);
		HFile getProps(UUID fileUID) throws FileNotFoundException;
	}
}
