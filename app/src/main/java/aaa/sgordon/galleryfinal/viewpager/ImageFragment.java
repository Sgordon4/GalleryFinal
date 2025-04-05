package aaa.sgordon.galleryfinal.viewpager;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;

import java.util.UUID;

import aaa.sgordon.galleryfinal.databinding.FragViewpagerImageBinding;
import aaa.sgordon.galleryfinal.gallery.ListItem;

public class ImageFragment extends Fragment {
	private FragViewpagerImageBinding binding;
	private final ListItem item;
	private final UUID fileUID;

	public ImageFragment(ListItem item) {
		this.item = item;
		this.fileUID = item.fileUID;
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		binding = FragViewpagerImageBinding.inflate(inflater, container, false);
		binding.media.setTransitionName(item.filePath.toString());
		return binding.getRoot();
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		requireParentFragment().startPostponedEnterTransition();

		ImageView media = binding.media;

		//Using a custom modelLoader to handle HybridAPI FileUIDs
		Glide.with(media.getContext())
				.load(fileUID)
				.into(media);
	}
}
