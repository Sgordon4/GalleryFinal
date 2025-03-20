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
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;

import org.apache.commons.io.FilenameUtils;

import java.io.FileNotFoundException;
import java.net.ConnectException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.gallery.DirFragment;
import aaa.sgordon.galleryfinal.gallery.ListItem;
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

	private List<ListItem> fullList;
	private String filterQuery;

	private MaterialToolbar toolbar;
	private EditText search;
	private ImageButton searchClear;
	private RecyclerView recyclerView;
	private Button confirmButton;

	private MCAdapter adapter;

	public static void launch(DirFragment dirFragment, Path startPath, MoveCopyCallback callback) {
		MoveCopyFullscreen dialog = new MoveCopyFullscreen(startPath, callback);

		FragmentTransaction transaction = dirFragment.getChildFragmentManager().beginTransaction();
		transaction.add(dialog, "move_copy_fullscreen");
		transaction.commitAllowingStateLoss();
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
		View view = inflater.inflate(R.layout.frag_dir_movecopy_fullscreen, container, false);
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
				UUID superPrevDir = (currPath.getNameCount() > 2) ?
						UUID.fromString(currPath.getParent().getParent().getFileName().toString()) : null;

				updateToolbar(superPrevDir, prevDir);
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

		updateToolbar(null, currDirUID);
		changeDirectory(currDirUID, currPath);
	}



	private void updateToolbar(@Nullable UUID parentDirUID, @NonNull UUID fileUID) {
		//Update the toolbar name and icon
		if(parentDirUID == null)
			toolbar.setTitle("Root");
		else {

			//Note: If we click on a link to a divider, there is simply no easy way to know what the divider's parent dir's name is
			// Therefore, if we click on a link to a divider, we're just using the divider name as the toolbar title

			try {
				LinkCache linkCache = LinkCache.getInstance();
				if(linkCache.isLink(fileUID)) {
					//Follow the link chain to the final target
					LinkCache.LinkTarget target = LinkCache.getInstance().getFinalTarget(fileUID);

					//If the target is internal...
					if(target instanceof LinkCache.InternalTarget) {
						//Use those fileUIDs to update the toolbar
						LinkCache.InternalTarget internal = (LinkCache.InternalTarget) target;
						parentDirUID = internal.getParentUID();
						fileUID = internal.getFileUID();
					}
				}
			}
			catch (FileNotFoundException ignored) {}


			final UUID fParentDirUID = parentDirUID;
			final UUID fFileUID = fileUID;
			Thread updateToolbarThread = new Thread(() -> {
				String fileName = getFileNameFromDir(fParentDirUID, fFileUID);
				Handler mainHandler = new Handler(getContext().getMainLooper());
				mainHandler.post(() -> toolbar.setTitle(fileName));
			});
			updateToolbarThread.start();
		}

		//If there is no parent, we're at relative root and going back should dismiss
		if(parentDirUID == null)
			toolbar.setNavigationIcon(R.drawable.icon_close);
		else
			toolbar.setNavigationIcon(R.drawable.icon_arrow_back);
	}

	private void changeDirectory(UUID fileUID, Path newPath) {
		currDirUID = fileUID;
		currPath = newPath;

		//Update the list itself
		Thread updateViaTraverse = new Thread(() -> {
			List<ListItem> list = traverseDir(fileUID);
			fullList = list;

			Handler mainHandler = new Handler(getContext().getMainLooper());
			mainHandler.post(() -> adapter.setList( filterList(list) ));
		});
		updateViaTraverse.start();
	}



	private List<ListItem> filterList(List<ListItem> list) {
		return list.stream()
				.filter(item -> item.name.toLowerCase().contains(filterQuery.toLowerCase()))
				.collect(Collectors.toList());
	}



	private String getFileNameFromDir(UUID parentDirUID, UUID fileUID) {
		try {
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
	private List<ListItem> traverseDir(UUID dirUID) {
		try {
			//If the dirUID is a link, we need the target dir or target parent
			dirUID = LinkCache.getInstance().getLinkDir(dirUID);

			//Grab the current list of all files in this directory from the system
			List<ListItem> newFileList = TraversalHelper.traverseDir(dirUID);

			newFileList = newFileList.stream()
					//Filter out anything that is trashed
					.filter(item -> !FilenameUtils.getExtension(item.name).startsWith("trashed_"))
					.collect(Collectors.toList());

			//Filter out anything that isn't a directory, a link to a directory/divider, or a linkEnd
			newFileList = newFileList.stream()
					.filter(item -> item.type.equals(ListItem.ListItemType.DIRECTORY)
							|| item.type.equals(ListItem.ListItemType.LINKDIRECTORY)
							|| item.type.equals(ListItem.ListItemType.LINKDIVIDER)
							//|| item.type.equals(ListItemType.LINKEND)
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
		public List<ListItem> list;

		public MCAdapter() {
			list = new ArrayList<>();
		}

		@SuppressLint("NotifyDataSetChanged")
		public void setList(List<ListItem> newList) {
			list = newList;

			//We want the full dataset to reset since we're changing dirs, even if there are common items
			notifyDataSetChanged();
		}


		@Override
		public void onBindViewHolder(@NonNull BaseViewHolder holder, int position) {
			ListItem item = list.get(position);

			holder.bind(list.get(position));
			holder.itemView.setOnClickListener(view -> {
				Thread thread = new Thread(() -> {
					updateToolbar(currDirUID, item.fileUID);

					changeDirectory(item.fileUID, currPath.resolve(item.fileUID.toString()));
				});
				thread.start();
			});
		}


		@Override
		public int getItemViewType(int position) {
			ListItem item = list.get(position);

			boolean isEnd = item.type.equals(ListItem.ListItemType.LINKEND);
			boolean isDirLink = item.type.equals(ListItem.ListItemType.LINKDIRECTORY);
			boolean isDivLink = item.type.equals(ListItem.ListItemType.LINKDIVIDER);

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
