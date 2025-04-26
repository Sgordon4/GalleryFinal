package aaa.sgordon.galleryfinal.viewpager;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DiffUtil;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.adapter.FragmentViewHolder;

import org.apache.commons.io.FilenameUtils;

import java.util.ArrayList;
import java.util.List;

import aaa.sgordon.galleryfinal.repository.gallery.ListItem;
import aaa.sgordon.galleryfinal.viewpager.viewpages.GifFragment;
import aaa.sgordon.galleryfinal.viewpager.viewpages.ImageFragment;
import aaa.sgordon.galleryfinal.viewpager.viewpages.VideoFragment;

public class ViewPagerAdapter extends FragmentStateAdapter {
	public List<ListItem> list;

	public ViewPagerAdapter(@NonNull Fragment fragment) {
		super(fragment);
		list = new ArrayList<>();
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
				boolean equal = list.get(oldItemPosition).pathFromRoot.equals(newList.get(newItemPosition).pathFromRoot);
				return equal;
			}
			@Override
			public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
				return true;
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
	public Fragment createFragment(int position) {
		ListItem item = list.get(position);
		String fileExtension = FilenameUtils.getExtension( item.getPrettyName() );

		switch (fileExtension) {
			case "gif":
				return GifFragment.initialize(item);
			case "mp4":
				return VideoFragment.initialize(item);
			default:
				return ImageFragment.initialize(item);
		}
	}

	@Override
	public void onBindViewHolder(@NonNull FragmentViewHolder holder, int position, @NonNull List<Object> payloads) {
		super.onBindViewHolder(holder, position, payloads);
	}

	@Override
	public int getItemCount() {
		return list.size();
	}


	@Override
	public long getItemId(int position) {
		//We're using pathFromRoot since 'duplicate' fileUIDs may be present thanks to links
		return list.get(position).pathFromRoot.hashCode();
	}
	@Override
	public boolean containsItem(long itemId) {
		for(int i = 0; i < list.size(); i++) {
			if(getItemId(i) == itemId)
				return true;
		}
		return false;
	}
}
