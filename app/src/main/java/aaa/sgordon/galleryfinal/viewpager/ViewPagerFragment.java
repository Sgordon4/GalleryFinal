package aaa.sgordon.galleryfinal.viewpager;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.transition.ChangeBounds;

import com.google.android.material.transition.MaterialContainerTransform;

import aaa.sgordon.galleryfinal.databinding.FragmentViewpagerBinding;

public class ViewPagerFragment extends Fragment {
	private FragmentViewpagerBinding binding;

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		MaterialContainerTransform transform = new MaterialContainerTransform();
		transform.setDuration(300L);

		setSharedElementEnterTransition(transform);
		setSharedElementReturnTransition(transform);
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		binding = FragmentViewpagerBinding.inflate(inflater, container, false);
		View view =  binding.getRoot();

		ViewPagerFragmentArgs args = ViewPagerFragmentArgs.fromBundle(getArguments());
		int fromPos = args.getFromPosition();

		ImageView imageView = binding.shared;
		imageView.setTransitionName("rv_shared_image_"+fromPos);

		return view;
	}
}
