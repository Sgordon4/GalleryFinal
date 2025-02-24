package aaa.sgordon.galleryfinal.gallery;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.appbar.MaterialToolbar;

import aaa.sgordon.galleryfinal.R;

public class TagFullscreen extends DialogFragment {
	MaterialToolbar toolbar;

	public static TagFullscreen newInstance(FragmentManager fragManager) {
		TagFullscreen dialog = new TagFullscreen();
		dialog.show(fragManager, "tag_fullscreen");
		return dialog;
	}

	private TagFullscreen() {

	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_directory_tag_fullscreen, container, false);
		toolbar = view.findViewById(R.id.toolbar);
		return view;
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		toolbar.setNavigationOnClickListener(v -> dismiss());
		toolbar.setTitle("Apply Tags");
		toolbar.setOnMenuItemClickListener(item -> {
			dismiss();
			return true;
		});
	}

	@Override
	public void onStart() {
		super.onStart();
		Dialog dialog = getDialog();
		if (dialog != null) {
			int width = ViewGroup.LayoutParams.MATCH_PARENT;
			int height = ViewGroup.LayoutParams.MATCH_PARENT;
			dialog.getWindow().setLayout(width, height);
		}
	}
}
