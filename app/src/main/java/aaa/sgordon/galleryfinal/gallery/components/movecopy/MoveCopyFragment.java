package aaa.sgordon.galleryfinal.gallery.components.movecopy;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.apache.commons.io.FilenameUtils;

import java.io.FileNotFoundException;
import java.net.ConnectException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.databinding.DirMovecopyBinding;
import aaa.sgordon.galleryfinal.gallery.DirItem;
import aaa.sgordon.galleryfinal.gallery.FilterController;
import aaa.sgordon.galleryfinal.gallery.ListItem;
import aaa.sgordon.galleryfinal.gallery.touch.SelectionController;
import aaa.sgordon.galleryfinal.repository.gallery.caches.DirCache;
import aaa.sgordon.galleryfinal.repository.gallery.caches.LinkCache;
import aaa.sgordon.galleryfinal.repository.gallery.components.link.InternalTarget;
import aaa.sgordon.galleryfinal.repository.gallery.components.link.LinkTarget;
import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;

public class MoveCopyFragment extends Fragment {
	private static final String TAG = "Gal.MC";
	private DirMovecopyBinding binding;
	public MCViewModel viewModel;

	private MCAdapter adapter;
	private SelectionController selectionController;


	private ListItem tempItemDoNotUse;
	public static MoveCopyFragment newInstance(ListItem startItem, boolean isMove) {
		MoveCopyFragment fragment = new MoveCopyFragment();
		fragment.tempItemDoNotUse = startItem;

		Bundle args = new Bundle();
		args.putBoolean("isMove", isMove);
		fragment.setArguments(args);

		return fragment;
	}

	private MoveCopyCallback callback;
	public void setMoveCopyCallback(MoveCopyCallback callback) {
		this.callback = callback;
	}
	public interface MoveCopyCallback {
		void onConfirm(UUID destinationUID, UUID nextItem);
	}


	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		boolean isMove = requireArguments().getBoolean("isMove");

		viewModel = new ViewModelProvider(this,
				new MCViewModel.Factory(tempItemDoNotUse, isMove))
				.get(MCViewModel.class);

		if(savedInstanceState == null) {
			try {
				viewModel.changeDirectories(tempItemDoNotUse.fileUID);
			} catch (ContentsNotFoundException | FileNotFoundException | ConnectException e) {
				getParentFragmentManager().popBackStack();
			}
		}
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		binding = DirMovecopyBinding.inflate(inflater, container, false);
		return binding.getRoot();
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		binding.toolbar.setNavigationOnClickListener(v -> {
			//If there are no more parents, dismiss the fragment
			if(viewModel.currPathFromRoot.getNameCount() <= 1)
				getParentFragmentManager().popBackStack();

			//Navigate to the previous directory
			else {
				Path parentPathFromRoot = viewModel.currPathFromRoot.getParent();
				UUID prevItem = UUID.fromString(parentPathFromRoot.getFileName().toString());
				UUID superPrevItem = (parentPathFromRoot.getParent() != null) ?
						UUID.fromString(parentPathFromRoot.getParent().getFileName().toString()) : null;

				changeDirectory(prevItem, superPrevItem, parentPathFromRoot);
			}
		});
		binding.toolbar.setOnMenuItemClickListener(item -> {
			if(item.getItemId() == R.id.exit) {
				getParentFragmentManager().popBackStack();
				return true;
			}
			return false;
		});



		FilterController filterController = buildFilterController();
		//Update the filtered list when the directory contents update
		viewModel.getFileListLiveData().observe(getViewLifecycleOwner(), filterController::onListUpdated);
		//Update the adapter when the filtered list updates
		filterController.registry.filteredList.observe(getViewLifecycleOwner(), list -> {
			//Remove duplicates
			Set<UUID> seen = new HashSet<>();
			list = list.stream().filter(item -> seen.add(item.fileUID)).collect(Collectors.toList());

			adapter.setList(list);
		});


		binding.search.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
				filterController.onActiveQueryChanged(charSequence.toString(), viewModel.getFileList());
			}

			@Override
			public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}
			@Override
			public void afterTextChanged(Editable editable) {}
		});
		binding.searchClear.setOnClickListener(view2 -> binding.search.setText(""));




		LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
		RecyclerView recyclerView = binding.recyclerview;
		recyclerView.setLayoutManager(layoutManager);

		//Set what happens when clicking on an item
		adapter = new MCAdapter(item -> {
			if(item.type == ListItem.Type.DIRECTORY || item.type == ListItem.Type.LINKDIRECTORY) {
				Path newPathFromRoot = viewModel.currPathFromRoot.resolve(item.pathFromRoot.subpath(1, item.pathFromRoot.getNameCount()));
				changeDirectory(item.fileUID, item.parentUID, newPathFromRoot);
			}
			else {
				selectionController.toggleSelectItem(item.fileUID);
			}
		}, this);
		recyclerView.setAdapter(adapter);

		//In DirFragment, we listen for attribute updates for things like color, and then update adapter items
		//Here, we don't care at all



		selectionController = buildSelectionController(recyclerView);
		selectionController.startSelecting();	//For Move/Copy, start selecting and never stop



		String text = (viewModel.isMove) ? "Move Here" : "Copy Here";
		binding.confirm.setText(text);
		binding.confirm.setOnClickListener(v -> {
			if(callback != null) {
				Thread doThings = new Thread(() -> {
					//Passing a fake item to move/copy will place the items at the start when the function can't find it
					UUID nextItem = UUID.randomUUID();

					if(selectionController.getNumSelected() > 0) {
						UUID selected = selectionController.getSelectedList().iterator().next();

						//If the item is a link to a divider, get the internal target...
						LinkTarget target = LinkCache.getInstance().getFinalTarget(selected);
						if (target instanceof InternalTarget) {
							InternalTarget internalTarget = (InternalTarget) target;

							try {
								nextItem = getNextItem(internalTarget.parentUID, internalTarget.fileUID);
							}
							catch (FileNotFoundException | ContentsNotFoundException | ConnectException e) {
								//If anything goes wrong, just don't update the next item
							}

							callback.onConfirm(internalTarget.parentUID, nextItem);
						}
						//If not a link to a divider, the item is an actual divider
						else {
							try {
								nextItem = getNextItem(viewModel.currDirUID, selected);
							}
							catch (FileNotFoundException | ContentsNotFoundException | ConnectException e) {
								//If anything goes wrong, just don't update the next item
							}
							callback.onConfirm(viewModel.currDirUID, nextItem);
						}
					}
					else
						callback.onConfirm(viewModel.currDirUID, nextItem);
				});
				doThings.start();
			}
			getParentFragmentManager().popBackStack();
		});


		changeDirectory(viewModel.currDirUID, viewModel.currParentUID, viewModel.currPathFromRoot);
	}

	private UUID getNextItem(UUID parentDirUID, UUID targetUID)
			throws ContentsNotFoundException, FileNotFoundException, ConnectException {

		//For each item in the parent directory...
		List<DirItem> dirList = DirCache.getInstance().getDirContents(parentDirUID);
		for(int i = 0; i < dirList.size(); i++) {
			//If we find the target, return the next item (or null)
			if (dirList.get(i).fileUID.equals(targetUID))
				return (i+1 < dirList.size()) ? dirList.get(i+1).fileUID : null;
		}
		throw new FileNotFoundException("Target not found! \nParent='"+parentDirUID+"', \nTarget='"+targetUID+"'");
	}



	@NonNull
	private FilterController buildFilterController() {
		FilterController filterController = new FilterController(viewModel.filterRegistry);
		filterController.addExtraQueryFilter(listItem -> {
			//Exclude all but the following item types
			return listItem.type.equals(ListItem.Type.DIRECTORY)
				|| listItem.type.equals(ListItem.Type.DIVIDER)
				|| listItem.type.equals(ListItem.Type.LINKDIRECTORY)
				|| listItem.type.equals(ListItem.Type.LINKDIVIDER);
		});
		filterController.addExtraQueryFilter(listItem -> {
			//Exclude trashed items
			return !listItem.isTrashed();
		});
		filterController.addExtraQueryFilter(listItem -> {
			//Exclude hidden Directory items if the active query doesn't match their name exactly
			if(!listItem.isHidden()) return true;

			String activeQuery = filterController.registry.activeQuery.getValue();
			return listItem.getPrettyName().equalsIgnoreCase(activeQuery);
		});
		return filterController;
	}

	@NonNull
	private SelectionController buildSelectionController(RecyclerView recyclerView) {
		SelectionController.SelectionCallbacks selectionCallbacks = new SelectionController.SelectionCallbacks() {
			@Override
			public void onSelectionStarted() {}

			@Override
			public void onSelectionStopped() {}

			@Override
			public void onNumSelectedChanged(int numSelected) {
				if(numSelected == 0) {
					String text = (viewModel.isMove) ? "Move Here" : "Copy Here";
					binding.confirm.post(() -> binding.confirm.setText(text));
				}
			}

			@Override
			public UUID getUUIDAtPos(int pos) {
				return adapter.list.get(pos).fileUID;
			}

			@Override
			public void onSelectionChanged(UUID fileUID, boolean isSelected) {
				if(isSelected) {
					ListItem selectedItem = adapter.list.stream().filter(item -> item.fileUID.equals(fileUID)).findFirst().orElse(null);
					if(selectedItem != null){
						String text = (viewModel.isMove) ? "Move to " + selectedItem.getPrettyName() : "Copy to " + selectedItem.getPrettyName();
						binding.confirm.post(() -> binding.confirm.setText(text));
					}
					//Should never really happen, but jic
					else
						selectionController.deselectItem(fileUID);
				}


				Handler handler = new Handler(Looper.getMainLooper());
				handler.post(() -> {
					//For any visible item, update the item selection status to change its appearance
					//There may be more than one item in the list with the same fileUID due to links
					//Non-visible items will have their selection status set later when they are bound by the adapter
					//TODO This isn't catching items just off the screen, fix this garbage
					for(int i = 0; i < recyclerView.getChildCount(); i++) {
						View itemView = recyclerView.getChildAt(i);
						if(itemView != null) {
							int adapterPos = recyclerView.getChildAdapterPosition(itemView);
							if(adapterPos == -1) continue;

							if(fileUID.equals( getUUIDAtPos(adapterPos)) )
								itemView.setSelected(isSelected);
						}
					}
				});
			}
		};
		SelectionController controller = new SelectionController(viewModel.selectionRegistry, selectionCallbacks);
		controller.setSingleItemMode(true);

		return controller;
	}


	//---------------------------------------------------------------------------------------------

	private void changeDirectory(UUID newFileUID, UUID newParentUID, Path newPathFromRoot) {
		Thread change = new Thread(() -> {
			Pair<UUID, UUID> trueDirAndParent = getTrueDirAndParent(newFileUID, newParentUID);
			if(trueDirAndParent == null) {
				Toast.makeText(getContext(), "Could not reach directory!", Toast.LENGTH_SHORT).show();
				return;
			}

			//Fetch the directory list and update our livedata
			try {
				viewModel.changeDirectories(trueDirAndParent.first);
			} catch (ContentsNotFoundException | FileNotFoundException | ConnectException e) {
				Looper.prepare();
				Toast.makeText(getContext(), "Could not reach directory!", Toast.LENGTH_SHORT).show();
				Looper.loop();
				return;
			}
			selectionController.deselectAll();

			viewModel.currDirUID = trueDirAndParent.first;
			viewModel.currParentUID = trueDirAndParent.second;
			viewModel.currPathFromRoot = newPathFromRoot;

			updateToolbar(trueDirAndParent.first, trueDirAndParent.second);

			binding.search.post(() -> binding.search.setText(""));
		});
		change.start();
	}



	private void updateToolbar(@NonNull UUID fileUID, @Nullable UUID parentDirUID) {
		//Update the toolbar name and icon
		if(parentDirUID == null)
			binding.toolbar.post(() -> binding.toolbar.setTitle("Root"));
		else {
			String fileName = getFileNameFromDir(fileUID, parentDirUID);
			binding.toolbar.post(() ->
					binding.toolbar.setTitle((fileName == null) ? "Unknown" : fileName));
		}


		if(viewModel.currPathFromRoot.getNameCount() <= 1)
			binding.toolbar.post(() -> binding.toolbar.setNavigationIcon(R.drawable.icon_close));
		else
			binding.toolbar.post(() -> binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back));



		binding.breadcrumbs.post(() -> binding.breadcrumbs.removeAllViews());
		LayoutInflater inflater = LayoutInflater.from(requireContext());

		//Add a breadcrumb for each item in the path
		for(int i = 0; i < viewModel.currPathFromRoot.getNameCount(); i++) {
			Path currPath = viewModel.currPathFromRoot.subpath(0, i+1);
			UUID currentCrumb = UUID.fromString(viewModel.currPathFromRoot.getName(i).toString());
			UUID previousCrumb = (i > 0) ? UUID.fromString(viewModel.currPathFromRoot.getName(i-1).toString()) : null;


			try {
				//If the previous item is a link, get the target dir
				LinkCache linkCache = LinkCache.getInstance();
				if (previousCrumb != null && linkCache.isLink(previousCrumb))
					previousCrumb = linkCache.getLinkDir(previousCrumb);
			}
			catch (FileNotFoundException | ConnectException e) {
				//If there was an issue getting the parent, skip adding this breadcrumb
				continue;
			}


			String name;
			if (previousCrumb == null) name = "Root";
			else name = getFileNameFromDir(currentCrumb, previousCrumb);

			int index = i;
			UUID finalPreviousCrumb = previousCrumb;
			binding.breadcrumbs.post(() -> {
				//Add a spacer if this is not the first item
				if(index > 0) inflater.inflate(R.layout.dir_mc_breadcrumb_spacer, binding.breadcrumbs);

				TextView breadCrumb = (TextView) inflater.inflate(R.layout.dir_mc_breadcrumb, binding.breadcrumbs, false);
				binding.breadcrumbs.addView(breadCrumb);
				breadCrumb.setText(name);

				//If this is not the last item, allow the breadcrumb to be selected
				if(index < viewModel.currPathFromRoot.getNameCount()-1)
					breadCrumb.setOnClickListener(v -> changeDirectory(currentCrumb, finalPreviousCrumb, currPath));
			});

			//After all other posts have completed
			binding.breadcrumbs.post(() -> {
				//Scroll to the end of the breadcrumbs whenever the layout changes
				binding.breadcrumbsScroll.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
					@Override
					public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
						binding.breadcrumbsScroll.removeOnLayoutChangeListener(this);
						binding.breadcrumbsScroll.fullScroll(View.FOCUS_RIGHT);
					}
				});
			});
		}
	}



	@Nullable
	private String getFileNameFromDir(UUID fileUID, UUID parentDirUID) {
		try {
			return DirCache.getInstance().getDirContents(parentDirUID).stream()
					.filter(item -> item.fileUID.equals(fileUID))
					.map(item -> item.name)
					.findFirst()
					.orElse(null);
		} catch (ContentsNotFoundException | FileNotFoundException | ConnectException e) {
			return null;
		}
	}

	@Nullable
	private Pair<UUID, UUID> getTrueDirAndParent(UUID maybeDirUID, UUID maybeParentUID) {
		//We are trying to find the true directory/parent combo using what we were given
		LinkCache linkCache = LinkCache.getInstance();
		UUID fileUID, parentUID;

		try {
			//If the previous item is a link...
			if (linkCache.isLink(maybeDirUID)) {
				//The fileUID and parentUID are both in the link target
				InternalTarget target = (InternalTarget) linkCache.getFinalTarget(maybeDirUID);
				return new Pair<>(target.fileUID, target.parentUID);
			}

			//If prevItem was not a link, we need to find its parent
			fileUID = maybeDirUID;

			//If the super previous item is a link, get the actual parent dir
			if (maybeParentUID != null && linkCache.isLink(maybeParentUID))
				parentUID = linkCache.getLinkDir(maybeParentUID);
			else
				parentUID = maybeParentUID;

			return new Pair<>(fileUID, parentUID);
		} catch (Exception e) {
			return null;
		}
	}
}
