package aaa.sgordon.galleryfinal.gallery;

import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.apache.commons.io.FilenameUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.gallery.viewholders.BaseViewHolder;
import aaa.sgordon.galleryfinal.gallery.viewholders.DirectoryViewHolder;
import aaa.sgordon.galleryfinal.gallery.viewholders.DividerViewHolder;
import aaa.sgordon.galleryfinal.gallery.viewholders.GifViewHolder;
import aaa.sgordon.galleryfinal.gallery.viewholders.ImageViewHolder;
import aaa.sgordon.galleryfinal.gallery.viewholders.LinkEndViewHolder;
import aaa.sgordon.galleryfinal.gallery.viewholders.LinkViewHolder;
import aaa.sgordon.galleryfinal.gallery.viewholders.LinkViewHolderSmall;
import aaa.sgordon.galleryfinal.gallery.viewholders.RichTextViewHolder;
import aaa.sgordon.galleryfinal.gallery.viewholders.TextViewHolder;
import aaa.sgordon.galleryfinal.gallery.viewholders.UnknownViewHolder;
import aaa.sgordon.galleryfinal.gallery.viewholders.VideoViewHolder;

public class DirRVAdapter extends RecyclerView.Adapter<BaseViewHolder> {
	public List<ListItem> list;
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
	public void setList(List<ListItem> newList) {
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
				return list.get(oldItemPosition).name.equals(newList.get(newItemPosition).name) &&
						list.get(oldItemPosition).pathFromRoot.equals(newList.get(newItemPosition).pathFromRoot) &&
						list.get(oldItemPosition).type.equals(newList.get(newItemPosition).type) &&
						list.get(oldItemPosition).fileProps.userattr.equals(newList.get(newItemPosition).fileProps.userattr);
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
		ListItem item = list.get(position);
		ListItem parent = null;

		//If the item comes from a link, find the parent
		if(item.pathFromRoot.getNameCount() > 2) {
			for(int i = position-1; i >= 0; i--) {
				if(item.parentUID.equals( list.get(i).fileUID) ) {
					parent = list.get(i);
					break;
				}
			}
		}


		if(holder instanceof ImageViewHolder || holder instanceof GifViewHolder || holder instanceof VideoViewHolder)
			holder.itemView.findViewById(R.id.media).setTransitionName(item.pathFromRoot.toString());

		holder.bind(item, parent);

		UUID fileUID = item.fileUID;
		holder.itemView.setSelected( touchCallback.isItemSelected(fileUID) );

		holder.itemView.setOnTouchListener((view, motionEvent) -> {
			if(motionEvent.getAction() == MotionEvent.ACTION_UP)
				view.performClick();

			return touchCallback.onHolderMotionEvent(fileUID, holder, motionEvent);
		});
	}




	private boolean isFullSpan(int viewType) {
		return viewType == 1 || viewType == 2 || viewType == 6 ||
				viewType == 12;
	}
	@Override
	public int getItemViewType(int position) {
		ListItem item = list.get(position);

		if(item.type.equals(ListItem.ListItemType.LINKDIRECTORY))
			return 1;
		else if(item.type.equals(ListItem.ListItemType.LINKEND))
			return 2;
		else if(item.type.equals(ListItem.ListItemType.LINKBROKEN))
			return 3;
		else if(item.type.equals(ListItem.ListItemType.LINKCYCLE))
			return 4;
		else if(item.type.equals(ListItem.ListItemType.LINKUNREACHABLE))
			return 5;
		else if(item.type.equals(ListItem.ListItemType.LINKDIVIDER))
			return 6;

		if(item.fileProps.isdir)
			return 0;			//Directory


		//Get the filename extension
		String extension = FilenameUtils.getExtension(item.name);
		switch (extension) {
			case "txt":
				return 7;		//Text
			case "rtf":
				return 8;		//Rich Text
			case "jpg":
			case "jpeg":
				return 9;		//Image
			case "gif":
				return 10;		//Gif
			case "mp4":
				return 11;		//Video
			case "div":
				return 12;		//Divider
		}

		return -1;				//Unknown
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
			//case 3: holder = new LinkViewHolder(inflater.inflate(R.layout.dir_vh_link_broken, parent, false));
			case 3: holder = new LinkViewHolderSmall(inflater.inflate(R.layout.dir_vh_link_broken_small, parent, false));
				break;
			//case 4: holder = new LinkViewHolder(inflater.inflate(R.layout.dir_vh_link_cycle, parent, false));
			case 4: holder = new LinkViewHolderSmall(inflater.inflate(R.layout.dir_vh_link_cycle_small, parent, false));
				break;
			//case 5: holder = new LinkViewHolder(inflater.inflate(R.layout.dir_vh_link_cycle, parent, false));
			case 5: holder = new LinkViewHolderSmall(inflater.inflate(R.layout.dir_vh_link_cycle_small, parent, false));
				break;
			case 6: holder = new LinkViewHolder(inflater.inflate(R.layout.dir_vh_link_divider, parent, false));
				break;


			case 7: holder = new TextViewHolder(inflater.inflate(R.layout.dir_vh_text, parent, false));
				break;
			case 8: holder = new RichTextViewHolder(inflater.inflate(R.layout.dir_vh_rich_text, parent, false));
				break;


			case 9: holder = new ImageViewHolder(inflater.inflate(R.layout.dir_vh_image, parent, false));
				break;
			case 10: holder = new GifViewHolder(inflater.inflate(R.layout.dir_vh_gif, parent, false));
				break;
			case 11: holder = new VideoViewHolder(inflater.inflate(R.layout.dir_vh_video, parent, false));
				break;


			case 12: holder = new DividerViewHolder(inflater.inflate(R.layout.dir_vh_divider, parent, false));
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
