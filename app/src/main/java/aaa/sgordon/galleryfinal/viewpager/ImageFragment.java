package aaa.sgordon.galleryfinal.viewpager;

import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

import java.io.FileNotFoundException;
import java.net.ConnectException;
import java.util.UUID;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.databinding.FragViewpagerImageBinding;
import aaa.sgordon.galleryfinal.gallery.ListItem;
import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridAPI;

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



		return binding.getRoot();
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		ImageView media = binding.media;
		media.setTransitionName(item.filePath.toString()+item.name);
		System.out.println("Page transition name: "+ item.filePath +item.name);
		startPostponedEnterTransition();


		Thread thread = new Thread(() -> {
			HybridAPI hAPI = HybridAPI.getInstance();
			try {
				Handler mainHandler = new Handler(media.getContext().getMainLooper());

				//mainHandler.post(() -> getParentFragment().startPostponedEnterTransition());

				Uri content = hAPI.getFileContent(fileUID).first;
				mainHandler.post(() ->
					Glide.with(media.getContext())
						.load(content)
							.listener(new RequestListener<Drawable>() {
								@Override
								public boolean onLoadFailed(@Nullable GlideException e, @Nullable Object model, @NonNull Target<Drawable> target, boolean isFirstResource) {
									requireParentFragment().startPostponedEnterTransition();
									return false;
								}

								@Override
								public boolean onResourceReady(@NonNull Drawable resource, @NonNull Object model, Target<Drawable> target, @NonNull DataSource dataSource, boolean isFirstResource) {
									requireParentFragment().startPostponedEnterTransition();
									return false;
								}
							})
						//.placeholder(R.drawable.ic_launcher_foreground)
						.error(R.drawable.ic_launcher_background)
						.into(media));
			}
			catch (ContentsNotFoundException | FileNotFoundException | ConnectException e) {
				//Do nothing
			}
		});
		thread.start();
	}
}
