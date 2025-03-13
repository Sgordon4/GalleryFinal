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
import aaa.sgordon.galleryfinal.repository.caches.LinkCache;

public class DirRVAdapter extends RecyclerView.Adapter<BaseViewHolder> {
	public List<TraversalHelper.ListItem> list;
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
					if(isFullSpan(viewType))
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
	public void setList(List<TraversalHelper.ListItem> newList) {
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
				return list.get(oldItemPosition).fileUID.equals(newList.get(newItemPosition).fileUID);
			}
			@Override
			public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
				return list.get(oldItemPosition).name.equals(newList.get(newItemPosition).name);
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
		TraversalHelper.ListItem item = list.get(position);

		if(holder instanceof ImageViewHolder || holder instanceof GifViewHolder || holder instanceof VideoViewHolder)
			holder.itemView.findViewById(R.id.media).setTransitionName(item.filePath.toString());

		UUID fileUID = item.fileUID;

		String level = "";
		if(!(layoutManager instanceof GridLayoutManager || layoutManager instanceof StaggeredGridLayoutManager)) {
			//Put in some fancy printing to show the directory structure
			int depth = item.filePath.getNameCount()-1;
			level = "│   ".repeat(Math.max(0, depth-1));
			if(depth > 0) {
				if(item.type.equals(TraversalHelper.ListItemType.LINKEND))
					level += "└─ ";
				else
					level += "├─ ";
			}
		}


		holder.bind(fileUID, level + list.get(position).name);


		holder.itemView.setSelected( touchCallback.isItemSelected(fileUID) );

		holder.itemView.setOnTouchListener((view, motionEvent) -> {
			if(motionEvent.getAction() == MotionEvent.ACTION_UP)
				view.performClick();

			return touchCallback.onHolderMotionEvent(fileUID, holder, motionEvent);
		});
	}


	private boolean isFullSpan(int viewType) {
		return viewType == 5 || viewType == 6;
	}
	@Override
	public int getItemViewType(int position) {
		TraversalHelper.ListItem item = list.get(position);
		UUID fileUID = item.fileUID;

		boolean isEnd = item.type.equals(TraversalHelper.ListItemType.LINKEND);

		boolean isDir = item.isDir;
		boolean isLink = item.isLink;






		if(isLink) {
			if(isEnd)
				return 6;		//End of link to directory
			else
				return 5;		//Link to directory
		}

		if(isDir)
			return 0;			//Directory

		//Get the filename extension, maybe we need fileNameUtils for this idk
		String extension = FilenameUtils.getExtension(item.name);
		switch (extension) {
			case "jpg":
			case "jpeg":
				return 1;    //Image
			case "gif":
				return 2;    //Gif
			case "mp4":
				return 3;    //Video
			case "div":
				return 4;	//Divider
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
			case 1: holder = new ImageViewHolder(inflater.inflate(R.layout.dir_vh_image, parent, false));
				break;
			case 2: holder = new GifViewHolder(inflater.inflate(R.layout.dir_vh_gif, parent, false));
				break;
			case 3: holder = new VideoViewHolder(inflater.inflate(R.layout.dir_vh_video, parent, false));
				break;
			case 4: holder = new DividerViewHolder(inflater.inflate(R.layout.dir_vh_divider, parent, false));
				break;
			case 5: holder = new LinkViewHolder(inflater.inflate(R.layout.dir_vh_link, parent, false));
				break;
			case 6: holder = new LinkEndViewHolder(inflater.inflate(R.layout.dir_vh_link_end, parent, false));
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
	}
}
