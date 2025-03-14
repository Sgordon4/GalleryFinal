package aaa.sgordon.galleryfinal.gallery.modals;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;

import java.io.FileNotFoundException;
import java.net.ConnectException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.gallery.DirFragment;
import aaa.sgordon.galleryfinal.gallery.TraversalHelper;
import aaa.sgordon.galleryfinal.gallery.viewholders.BaseViewHolder;
import aaa.sgordon.galleryfinal.gallery.viewholders.DirectoryViewHolder;
import aaa.sgordon.galleryfinal.gallery.viewholders.DividerViewHolder;
import aaa.sgordon.galleryfinal.gallery.viewholders.LinkEndViewHolder;
import aaa.sgordon.galleryfinal.gallery.viewholders.LinkViewHolder;
import aaa.sgordon.galleryfinal.repository.caches.DirCache;
import aaa.sgordon.galleryfinal.repository.caches.LinkCache;
import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;

public class MoveCopyFullscreen extends DialogFragment {
	private final MoveCopyCallback callback;
	private Path currPath;
	private UUID currDirUID;

	private List<TraversalHelper.ListItem> fullList;
	private String filterQuery;

	private MaterialToolbar toolbar;
	private EditText search;
	private ImageButton searchClear;
	private RecyclerView recyclerView;
	private Button confirmButton;

	private MCAdapter adapter;

	public static void launch(DirFragment dirFragment, Path startPath, MoveCopyCallback callback) {
		MoveCopyFullscreen dialog = new MoveCopyFullscreen(startPath, callback);
		dialog.show(dirFragment.getChildFragmentManager(), "move_copy_fullscreen");
	}
	private MoveCopyFullscreen(Path startPath, MoveCopyCallback callback) {
		this.callback = callback;
		this.currPath = startPath;
		this.currDirUID = UUID.fromString(startPath.getFileName().toString());

		fullList = new ArrayList<>();
		filterQuery = "";
	}


	public interface MoveCopyCallback {
		void onConfirm(UUID destinationUID);
	}



	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_directory_movecopy_fullscreen, container, false);
		toolbar = view.findViewById(R.id.toolbar);
		search = view.findViewById(R.id.search);
		searchClear = view.findViewById(R.id.search_clear);
		recyclerView = view.findViewById(R.id.recyclerview);
		confirmButton = view.findViewById(R.id.confirm);

		return view;
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		//If there is only one item in the path, we're at our root (possibly relative root, not true root)
		toolbar.setNavigationOnClickListener(v -> {
			if(currPath.getNameCount() <= 1)
				dismiss();
			else {
				UUID prevDir = UUID.fromString(currPath.getParent().getFileName().toString());
				changeDirectory(prevDir, currPath.getParent());
			}
		});

		if(currPath.getNameCount() <= 1)
			toolbar.setNavigationIcon(R.drawable.icon_close);
		else
			toolbar.setNavigationIcon(R.drawable.icon_arrow_back);

		toolbar.setOnMenuItemClickListener(item -> {
			if(item.getItemId() == R.id.refresh)
				changeDirectory(currDirUID, currPath);
			return false;
		});


		search.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
				filterQuery = charSequence.toString();
				adapter.setList( filterList(fullList) );
			}

			@Override
			public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}
			@Override
			public void afterTextChanged(Editable editable) {}
		});
		searchClear.setOnClickListener(view2 -> search.setText(""));


		confirmButton.setOnClickListener(view2 -> {
			callback.onConfirm(currDirUID);
			dismiss();
		});



		LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
		recyclerView.setLayoutManager(layoutManager);

		adapter = new MCAdapter();
		recyclerView.setAdapter(adapter);

		changeDirectory(currDirUID, currPath);
	}


	private void changeDirectory(UUID fileUID, Path newPath) {
		currDirUID = fileUID;
		currPath = newPath;

		//Update the toolbar name and icon
		if(currPath.getNameCount() <= 1)
			updateToolbar("Root", true);
		else {
			Thread updateToolbarThread = new Thread(() -> {
				UUID parentDirUID = UUID.fromString(newPath.getParent().getFileName().toString());
				String fileName = getFileNameFromDir(parentDirUID, fileUID);
				Handler mainHandler = new Handler(getContext().getMainLooper());
				mainHandler.post(() -> updateToolbar(fileName, false));
			});
			updateToolbarThread.start();
		}

		//Update the list itself
		Thread updateViaTraverse = new Thread(() -> {
			List<TraversalHelper.ListItem> list = traverseDir(fileUID);
			fullList = list;

			Handler mainHandler = new Handler(getContext().getMainLooper());
			mainHandler.post(() -> adapter.setList( filterList(list) ));
		});
		updateViaTraverse.start();
	}

	private void updateToolbar(String newTitle, boolean isRoot) {
		if(isRoot)
			toolbar.setNavigationIcon(R.drawable.icon_close);
		else
			toolbar.setNavigationIcon(R.drawable.icon_arrow_back);

		toolbar.setTitle(newTitle);
	}



	private List<TraversalHelper.ListItem> filterList(List<TraversalHelper.ListItem> list) {
		return list.stream()
				.filter(item -> item.name.toLowerCase().contains(filterQuery.toLowerCase()))
				.collect(Collectors.toList());
	}



	private String getFileNameFromDir(UUID parentDirUID, UUID fileUID) {
		try {
			//TODO I bet with the new LinkCache.followLinkChain we could get the correct filename
			return DirCache.getInstance().getDirContents(parentDirUID).stream()
					.filter(item -> item.first.equals(fileUID))
					.map(item -> item.second)
					.findFirst()
					.orElse("Unknown");
		} catch (ContentsNotFoundException | FileNotFoundException | ConnectException e) {
			return "Unknown";
		}
	}



	@NonNull
	private List<TraversalHelper.ListItem> traverseDir(UUID dirUID) {
		try {
			//If the item is a link to a directory, follow that link
			dirUID = LinkCache.getInstance().resolvePotentialLink(dirUID);

			//Grab the current list of all files in this directory from the system
			List<TraversalHelper.ListItem> newFileList = TraversalHelper.traverseDir(dirUID);

			//Filter out anything that isn't a directory, a link to a directory/divider, or a linkEnd
			newFileList = newFileList.stream()
					.filter(item -> item.type.equals(TraversalHelper.ListItemType.DIRECTORY)
							|| item.type.equals(TraversalHelper.ListItemType.LINKDIRECTORY)
							|| item.type.equals(TraversalHelper.ListItemType.LINKDIVIDER)
							//|| item.type.equals(TraversalHelper.ListItemType.LINKEND)
					).collect(Collectors.toList());

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





	//---------------------------------------------------------------------------------------------

	private class MCAdapter extends RecyclerView.Adapter<BaseViewHolder> {
		public List<TraversalHelper.ListItem> list;

		public MCAdapter() {
			list = new ArrayList<>();
		}

		@SuppressLint("NotifyDataSetChanged")
		public void setList(List<TraversalHelper.ListItem> newList) {
			list = newList;

			//We want the full dataset to reset since we're changing dirs, even if there are common items
			notifyDataSetChanged();
		}


		@Override
		public void onBindViewHolder(@NonNull BaseViewHolder holder, int position) {
			TraversalHelper.ListItem item = list.get(position);

			holder.bind(item.fileUID, list.get(position).name);
			holder.itemView.setOnClickListener(view -> {
				Thread thread = new Thread(() -> {
					if(item.type.equals(TraversalHelper.ListItemType.LINKDIVIDER)) {
						LinkCache.InternalTarget target = (LinkCache.InternalTarget)
								LinkCache.getInstance().followLinkChain(item.fileUID);

						changeDirectory(target.getParentUID(), currPath.resolve(target.getParentUID().toString()));
					}
					else {
						changeDirectory(item.fileUID, currPath.resolve(item.fileUID.toString()));
					}
				});
				thread.start();
			});
		}


		@Override
		public int getItemViewType(int position) {
			TraversalHelper.ListItem item = list.get(position);

			boolean isEnd = item.type.equals(TraversalHelper.ListItemType.LINKEND);
			boolean isDirLink = item.type.equals(TraversalHelper.ListItemType.LINKDIRECTORY);
			boolean isDivLink = item.type.equals(TraversalHelper.ListItemType.LINKDIVIDER);

			boolean isDir = item.isDir;
			boolean isLink = item.isLink;


			if(isDirLink)
				return 0;
			else if(isDivLink)
				return 1;
			else if(isEnd)
				return 2;
			else if(isDir)
				return 3;
			return -1;
		}


		@NonNull
		@Override
		public BaseViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			LayoutInflater inflater = LayoutInflater.from(parent.getContext());

			BaseViewHolder holder;
			switch(viewType) {
				case 0: holder = new LinkViewHolder(inflater.inflate(R.layout.dir_mc_link, parent, false));
					break;
				case 1: holder = new DividerViewHolder(inflater.inflate(R.layout.dir_vh_divider, parent, false));
					break;
				case 2: holder = new LinkEndViewHolder(inflater.inflate(R.layout.dir_vh_link_end, parent, false));
					break;
				case 3: holder = new DirectoryViewHolder(inflater.inflate(R.layout.dir_mc_directory, parent, false));
					break;
				case -1:
				default: throw new RuntimeException("Unknown view type in Move/Copy adapter!");
			}

			return holder;
		}


		@Override
		public int getItemCount() {
			return list.size();
		}
	}
}
