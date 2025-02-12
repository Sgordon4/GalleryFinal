package aaa.sgordon.galleryfinal.gallery;

import android.util.Pair;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import org.apache.commons.io.FilenameUtils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.gallery.viewholders.BaseViewHolder;
import aaa.sgordon.galleryfinal.gallery.viewholders.DirectoryViewHolder;
import aaa.sgordon.galleryfinal.gallery.viewholders.DividerViewHolder;
import aaa.sgordon.galleryfinal.gallery.viewholders.GifViewHolder;
import aaa.sgordon.galleryfinal.gallery.viewholders.ImageViewHolder;
import aaa.sgordon.galleryfinal.gallery.viewholders.LinkEndViewHolder;
import aaa.sgordon.galleryfinal.gallery.viewholders.LinkViewHolder;
import aaa.sgordon.galleryfinal.gallery.viewholders.UnknownViewHolder;
import aaa.sgordon.galleryfinal.gallery.viewholders.VideoViewHolder;

public class DirRVAdapter extends RecyclerView.Adapter<BaseViewHolder> {
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

		//Some items (links, linkEnds, dividers) need to span across all columns
		if(layoutManager instanceof GridLayoutManager) {
			GridLayoutManager layoutManager = (GridLayoutManager) this.layoutManager;
			layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
				@Override
				public int getSpanSize(int position) {
					int viewType = getItemViewType(position);
					if(viewType == 1 || viewType == 2)
						return layoutManager.getSpanCount();
					return 1;
				}
			});
		}

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



	@Override
	public void onBindViewHolder(@NonNull BaseViewHolder holder, int position) {
		Pair<Path, String> item = list.get(position);

		if(holder instanceof ImageViewHolder)
			holder.itemView.findViewById(R.id.image).setTransitionName(item.first.toString());


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
		Pair<Path, String> item = list.get(position);

		boolean isEnd = false;
		String UUIDString = item.first.getFileName().toString();
		if(UUIDString.equals("END")) {
			isEnd = true;
			UUIDString = item.first.getParent().getFileName().toString();
		}
		UUID fileUID = UUID.fromString(UUIDString);

		boolean isDir = touchCallback.isDir(fileUID);
		boolean isLink = touchCallback.isLink(fileUID);


		if(isDir) {
			if(!isLink)
				return 0;			//Directory
			else {
				if(!isEnd)
					return 1;		//Link to directory
				else
					return 2;		//End of link to directory
			}
		}

		//Get the filename extension, maybe we need fileNameUtils for this idk
		String extension = FilenameUtils.getExtension(item.second);
		switch (extension) {
			case "div":
				return 3;	//Divider
			case "jpg":
			case "jpeg":
				return 4;    //Image
			case "gif":
				return 5;    //Gif
			case "mp4":
				return 6;    //Video
		}

		return -1;					//Unknown
	}


	@NonNull
	@Override
	public BaseViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		LayoutInflater inflater = LayoutInflater.from(parent.getContext());

		BaseViewHolder holder;
		switch(viewType) {
			case 0: holder = new DirectoryViewHolder(inflater.inflate(R.layout.dir_vh_directory, parent, false));
				break;
			case 1: holder = new LinkViewHolder(inflater.inflate(R.layout.dir_vh_link, parent, false));
				break;
			case 2: holder = new LinkEndViewHolder(inflater.inflate(R.layout.dir_vh_link_end, parent, false));
				break;
			case 3: holder = new DividerViewHolder(inflater.inflate(R.layout.dir_vh_divider, parent, false));
				break;
			case 4: holder = new ImageViewHolder(inflater.inflate(R.layout.dir_vh_image, parent, false));
				break;
			case 5: holder = new GifViewHolder(inflater.inflate(R.layout.dir_vh_gif, parent, false));
				break;
			case 6: holder = new VideoViewHolder(inflater.inflate(R.layout.dir_vh_video, parent, false));
				break;
			case -1:
			default: holder = new UnknownViewHolder(inflater.inflate(R.layout.dir_vh_unknown, parent, false));
				break;
		}

		return holder;
	}




	public interface AdapterCallbacks {
		boolean isItemSelected(UUID fileUID);
		boolean onHolderMotionEvent(UUID fileUID, BaseViewHolder holder, MotionEvent event);
		boolean isDir(UUID fileUID);
		boolean isLink(UUID fileUID);
	}
}
