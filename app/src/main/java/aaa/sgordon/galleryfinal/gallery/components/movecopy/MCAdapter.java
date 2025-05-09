package aaa.sgordon.galleryfinal.gallery.components.movecopy;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.repository.gallery.ListItem;
import aaa.sgordon.galleryfinal.gallery.viewholders.BaseViewHolder;
import aaa.sgordon.galleryfinal.gallery.viewholders.DirectoryViewHolder;
import aaa.sgordon.galleryfinal.gallery.viewholders.DividerViewHolder;
import aaa.sgordon.galleryfinal.gallery.viewholders.LinkViewHolder;

public class MCAdapter extends RecyclerView.Adapter<BaseViewHolder> {
	private final MoveCopyFragment fragment;
	private final MCAdapterCallbacks callbacks;

	public List<ListItem> list;
	private final ListItem newItemPlaceholder;
	private final ListItem nothingHerePlaceholder;
	public boolean showCreateNewDir;

	public MCAdapter(@NonNull MCAdapterCallbacks callbacks, @NonNull MoveCopyFragment fragment, boolean showCreateNewDir) {
		this.fragment = fragment;
		this.showCreateNewDir = showCreateNewDir;
		this.callbacks = callbacks;
		list = new ArrayList<>();

		newItemPlaceholder = new ListItem(UUID.randomUUID(), null, false, false,
				"Create a New Directory", Paths.get(""), ListItem.Type.UNREACHABLE);
		nothingHerePlaceholder = new ListItem(UUID.randomUUID(), null, false, false,
				"There's Nothing Here...", Paths.get(""), ListItem.Type.LINKCYCLE);

	}


	public interface MCAdapterCallbacks {
		void onItemClicked(ListItem item);
	}


	@SuppressLint("NotifyDataSetChanged")
	public void setList(List<ListItem> newList) {
		list = newList;

		//Add a Create New Directory item
		if(showCreateNewDir) {
			list.add(0, newItemPlaceholder);
		}
		//Add a Nothing Here item
		else if(list.isEmpty()) {
			list.add(0, nothingHerePlaceholder);
		}

		//When changing dirs, we want the full dataset to reset, even if there are common items
		//Dir content updates also change the list, but we should be displaying so few items that idc
		notifyDataSetChanged();
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

		holder.itemView.setOnClickListener(view -> callbacks.onItemClicked(item));
		holder.bind(list.get(position), parent);
	}


	@Override
	public int getItemViewType(int position) {
		ListItem item = list.get(position);

		switch (item.type) {
			case DIRECTORY:
				return 0;
			case DIVIDER:
				return 1;
			case LINKDIRECTORY:
				return 2;
			case LINKDIVIDER:
				return 3;
			case UNREACHABLE:		//This will only be our "Create Directory" item
				return 4;
			case LINKCYCLE:			//This will only be our "Nothing Here" item
				return 5;
			default:
				return -1;
		}
	}


	@NonNull
	@Override
	public BaseViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		LayoutInflater inflater = LayoutInflater.from(parent.getContext());

		BaseViewHolder holder;
		switch(viewType) {
			case 0: holder = new DirectoryViewHolder(inflater.inflate(R.layout.dir_mc_vh_directory, parent, false));
				break;
			case 1: holder = new DividerViewHolder(inflater.inflate(R.layout.dir_mc_vh_divider, parent, false));
				break;
			case 2: holder = new LinkViewHolder(inflater.inflate(R.layout.dir_mc_vh_link, parent, false));
				break;
			case 3: holder = new DividerViewHolder(inflater.inflate(R.layout.dir_mc_vh_divider, parent, false));
				break;
			case 4: holder = new NewDirViewHolder(inflater.inflate(R.layout.dir_mc_vh_newitem, parent, false), fragment, fragment.viewModel.currDirUID);
				break;
			case 5: holder = new NothingViewHolder(inflater.inflate(R.layout.dir_mc_vh_nothing_here, parent, false));
				break;
			case -1:
			default: throw new RuntimeException("Unknown view type in Move/Copy adapter!");
		}

		return holder;
	}


	@Override
	public int getItemCount() {
		return list.size();
	}
}