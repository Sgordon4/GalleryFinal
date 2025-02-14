package aaa.sgordon.galleryfinal.viewpager;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.transition.Transition;

import com.bumptech.glide.Glide;
import com.google.android.material.transition.MaterialContainerTransform;

import java.io.FileNotFoundException;
import java.net.ConnectException;
import java.nio.file.Path;
import java.util.UUID;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.databinding.FragmentDirectoryBinding;
import aaa.sgordon.galleryfinal.databinding.FragmentViewpagerImageBinding;
import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridAPI;

public class ImageFragment extends Fragment {
	private FragmentViewpagerImageBinding binding;
	private final Pair<Path, String> item;
	private final UUID fileUID;

	public ImageFragment(Pair<Path, String> item) {
		this.item = item;

		String UUIDString = item.first.getFileName().toString();
		if(UUIDString.equals("END"))
			UUIDString = item.first.getParent().getFileName().toString();

		this.fileUID = UUID.fromString(UUIDString);
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		binding = FragmentViewpagerImageBinding.inflate(inflater, container, false);
		return binding.getRoot();
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		View sharedElementTarget = binding.sharedElementTarget;
		ImageView image = binding.image;

		sharedElementTarget.setTransitionName(item.first.toString());


		//We can't call getFileContent without a thread, so just Load a placeholder here
		Glide.with(image.getContext())
				.load(R.drawable.ic_launcher_foreground)
				.into(image);


		Thread thread = new Thread(() -> {
			HybridAPI hAPI = HybridAPI.getInstance();
			try {
				Uri content = hAPI.getFileContent(fileUID).first;

				Handler mainHandler = new Handler(image.getContext().getMainLooper());
				mainHandler.post(() ->
						Glide.with(image.getContext())
								.load(content)

								.placeholder(R.drawable.ic_launcher_foreground)
								.error(R.drawable.ic_launcher_background)
								.into(image));
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

		View sharedElementTarget = binding.sharedElementTarget;
		ImageView image = binding.image;
		image.setTransitionName(sharedElementTarget.getTransitionName());
		sharedElementTarget.setTransitionName(null);
	}
}
