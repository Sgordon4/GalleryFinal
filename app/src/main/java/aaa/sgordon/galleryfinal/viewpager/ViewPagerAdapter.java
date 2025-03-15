package aaa.sgordon.galleryfinal.viewpager;

import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DiffUtil;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.adapter.FragmentViewHolder;

import org.apache.commons.io.FilenameUtils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.gallery.ListItem;
import aaa.sgordon.galleryfinal.gallery.TraversalHelper;

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

	@NonNull
	@Override
	public Fragment createFragment(int position) {
		String fileExtension = FilenameUtils.getExtension( list.get(position).name );

		switch (fileExtension) {
			case "gif":
				return new GifFragment( list.get(position) );
			case "mp4":
				return new VideoFragment( list.get(position) );
			default:
				return new ImageFragment( list.get(position) );
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
}
