package aaa.sgordon.galleryfinal.gallery.modals;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;

import java.io.FileNotFoundException;
import java.net.ConnectException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.gallery.DirFragment;
import aaa.sgordon.galleryfinal.gallery.TraversalHelper;
import aaa.sgordon.galleryfinal.gallery.viewholders.BaseViewHolder;
import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;

public class MoveCopyFullscreen extends DialogFragment {
	private final DirFragment dirFragment;
	private Path currPath;

	private MaterialToolbar toolbar;
	private EditText search;
	private ImageButton searchClear;
	private RecyclerView recyclerView;

	public static void launch(DirFragment fragment, Path startPath) {
		MoveCopyFullscreen dialog = new MoveCopyFullscreen(fragment, startPath);
		dialog.show(fragment.getChildFragmentManager(), "move_copy_fullscreen");
	}
	private MoveCopyFullscreen(DirFragment dirFragment, Path startPath) {
		this.dirFragment = dirFragment;
		this.currPath = startPath;
	}


	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_directory_movecopy_fullscreen, container, false);
		toolbar = view.findViewById(R.id.toolbar);
		search = view.findViewById(R.id.search);
		searchClear = view.findViewById(R.id.search_clear);
		recyclerView = view.findViewById(R.id.recyclerview);

		//TODO Add a refresh button in the toolbar menu

		return view;
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		//If there is only one item in the path, we're at our root (possibly relative root, not true root)
		if(currPath.getNameCount() == 1) {
			toolbar.setNavigationOnClickListener(v -> dismiss());
			toolbar.setNavigationIcon(R.drawable.icon_close);
		}
		else {
			UUID prevDir = UUID.fromString(currPath.getFileName().toString());
			changeDirectory(prevDir, currPath.getParent());
		}


		search.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
				String newQuery = charSequence.toString();
			}

			@Override
			public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}
			@Override
			public void afterTextChanged(Editable editable) {}
		});
		searchClear.setOnClickListener(view2 -> search.setText(""));
	}


	private void changeDirectory(UUID dirUID, Path newPath) {
		currPath = newPath;
	}

	@NonNull
	private List<TraversalHelper.ListItem> traverseDir(UUID dirUID) {
		try {
			//Grab the current list of all files in this directory from the system
			List<TraversalHelper.ListItem> newFileList = TraversalHelper.traverseDir(dirUID);

			//Filter out anything that isn't a directory, a link to a directory/divider, or a linkEnd
		}
		catch (ContentsNotFoundException | FileNotFoundException | ConnectException e) {
			//TODO Actually handle the error. Dir should be on local, but jic
			throw new RuntimeException(e);
		}

		return new ArrayList<>();
	}


	private static class MCAdapter extends RecyclerView.Adapter<BaseViewHolder> {
		public List<TraversalHelper.ListItem> list;

		public MCAdapter() {
			list = new ArrayList<>();
		}

		public void setList(List<TraversalHelper.ListItem> newList) {
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
		public BaseViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			return null;
		}

		@Override
		public void onBindViewHolder(@NonNull BaseViewHolder holder, int position) {

		}

		@Override
		public int getItemViewType(int position) {
			TraversalHelper.ListItem item = list.get(position);
			return -1;
		}

		@Override
		public int getItemCount() {
			return list.size();
		}
	}
}
