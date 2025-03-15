package aaa.sgordon.galleryfinal.viewpager;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import java.io.FileNotFoundException;
import java.net.ConnectException;
import java.nio.file.Path;
import java.util.UUID;

import aaa.sgordon.galleryfinal.databinding.FragmentViewpagerGifBinding;
import aaa.sgordon.galleryfinal.databinding.FragmentViewpagerVideoBinding;
import aaa.sgordon.galleryfinal.gallery.ListItem;
import aaa.sgordon.galleryfinal.gallery.TraversalHelper;
import aaa.sgordon.galleryfinal.repository.caches.LinkCache;
import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridAPI;
import pl.droidsonroids.gif.GifImageView;

public class VideoFragment extends Fragment {
	private FragmentViewpagerVideoBinding binding;
	private final ListItem item;
	private final UUID fileUID;

	private ExoPlayer player;

	public VideoFragment(ListItem item) {
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
		binding = FragmentViewpagerVideoBinding.inflate(inflater, container, false);
		return binding.getRoot();
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);


		PlayerView playerView = binding.media;
		player = new ExoPlayer.Builder(getContext()).build();
		playerView.setPlayer(player);

		playerView.setTransitionName(item.filePath.toString());


		Thread thread = new Thread(() -> {
			HybridAPI hAPI = HybridAPI.getInstance();
			try {
				Handler mainHandler = new Handler(getContext().getMainLooper());

				mainHandler.post(() -> getParentFragment().startPostponedEnterTransition());

				Uri content = hAPI.getFileContent(fileUID).first;
				MediaItem mediaItem = MediaItem.fromUri(content);

				mainHandler.post(() -> {
					player.setMediaItem(mediaItem);
					player.prepare();
					player.setPlayWhenReady(true);
				});
			}
			catch (ContentsNotFoundException | FileNotFoundException | ConnectException e) {
				//Do nothing
			}
		});
		thread.start();
	}

	@Override
	public void onStop() {
		super.onStop();
		if (player != null) {
			player.release();
			player = null;
		}
	}
}
