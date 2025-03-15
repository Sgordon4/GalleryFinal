package aaa.sgordon.galleryfinal.gallery.modals;

import android.app.Dialog;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;

import org.apache.commons.io.FilenameUtils;

import java.io.FileNotFoundException;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.gallery.DirFragment;
import aaa.sgordon.galleryfinal.gallery.DirRVAdapter;
import aaa.sgordon.galleryfinal.gallery.ListItem;
import aaa.sgordon.galleryfinal.gallery.TraversalHelper;
import aaa.sgordon.galleryfinal.gallery.viewholders.BaseViewHolder;
import aaa.sgordon.galleryfinal.repository.caches.LinkCache;
import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;

public class TrashFullscreen extends DialogFragment {
	private final TrashCallback callback;
	private final UUID dirUID;

	private MaterialToolbar toolbar;
	private RecyclerView recyclerView;
	private Button deleteButton;
	private Button restoreButton;

	private DirRVAdapter adapter;

	public static void launch(DirFragment dirFragment, UUID dirUID, TrashCallback callback) {
		TrashFullscreen dialog = new TrashFullscreen(dirUID, callback);

		FragmentTransaction transaction = dirFragment.getChildFragmentManager().beginTransaction();
		transaction.add(dialog, "trash_fullscreen");
		transaction.commitAllowingStateLoss();
	}
	private TrashFullscreen(UUID dirUID, TrashCallback callback) {
		this.callback = callback;
		this.dirUID = dirUID;
	}


	public interface TrashCallback {
		void onRestore(List<UUID> fileUID);
		void onDelete(List<UUID> fileUID);
	}


	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_directory_trash_fullscreen, container, false);
		toolbar = view.findViewById(R.id.toolbar);
		recyclerView = view.findViewById(R.id.recyclerview);
		deleteButton = view.findViewById(R.id.delete);
		restoreButton = view.findViewById(R.id.restore);

		return view;
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		toolbar.setNavigationOnClickListener(v -> dismiss());
		toolbar.setOnMenuItemClickListener(item -> {
			if(item.getItemId() == R.id.refresh)
				updateList();
			return false;
		});



		deleteButton.setOnClickListener(view1 -> callback.onDelete(new ArrayList<>()));
		restoreButton.setOnClickListener(view1 -> callback.onRestore(new ArrayList<>()));



		LinearLayoutManager layoutManager = new GridLayoutManager(getContext(), 3);
		recyclerView.setLayoutManager(layoutManager);

		adapter = new DirRVAdapter();
		recyclerView.setAdapter(adapter);

		adapter.setCallbacks(new DirRVAdapter.AdapterCallbacks() {
			@Override
			public boolean isItemSelected(UUID fileUID) {
				return false;
			}

			@Override
			public boolean onHolderMotionEvent(UUID fileUID, BaseViewHolder holder, MotionEvent event) {
				return false;
			}
		});


		//Update the list
		updateList();
	}


	private void updateList() {
		Thread traverse = new Thread(() -> {
			List<ListItem> list = traverseDir(dirUID);

			Handler mainHandler = new Handler(getContext().getMainLooper());
			mainHandler.post(() -> adapter.setList(list));
		});
		traverse.start();
	}



	@NonNull
	private List<ListItem> traverseDir(UUID dirUID) {
		try {
			//If the item is a link to a directory, follow that link
			dirUID = LinkCache.getInstance().resolvePotentialLink(dirUID);

			//Grab the current list of all files in this directory from the system
			List<ListItem> newFileList = TraversalHelper.traverseDir(dirUID);

			newFileList = newFileList.stream()
					//Filter out anything that isn't trashed
					.filter(item -> FilenameUtils.getExtension(item.name).startsWith("trashed_"))
					//Trimming the trashed extension from the name so it displays correctly
					.map(item -> new ListItem.Builder(item).setName(FilenameUtils.removeExtension(item.name)).build())
					.collect(Collectors.toList());

			return newFileList;
		}
		catch (ContentsNotFoundException | FileNotFoundException | ConnectException e) {
			//TODO Actually handle the error. Dir should be on local, but jic
			throw new RuntimeException(e);
		}
	}


	@Override
	public void onStart() {
		super.onStart();

		//Make the dialog fullscreen
		Dialog dialog = getDialog();
		if (dialog != null) {
			int width = ViewGroup.LayoutParams.MATCH_PARENT;
			int height = ViewGroup.LayoutParams.MATCH_PARENT;
			dialog.getWindow().setLayout(width, height);
		}
	}
}
