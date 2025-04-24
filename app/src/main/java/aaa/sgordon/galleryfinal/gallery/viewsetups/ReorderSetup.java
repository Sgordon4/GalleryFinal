package aaa.sgordon.galleryfinal.gallery.viewsetups;

import static android.os.Looper.getMainLooper;

import android.os.Handler;

import androidx.recyclerview.widget.RecyclerView;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ConnectException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import aaa.sgordon.galleryfinal.gallery.DirRVAdapter;
import aaa.sgordon.galleryfinal.gallery.ListItem;
import aaa.sgordon.galleryfinal.utilities.DirUtilities;
import aaa.sgordon.galleryfinal.gallery.DirectoryViewModel;
import aaa.sgordon.galleryfinal.gallery.touch.ItemReorderCallback;
import aaa.sgordon.galleryfinal.repository.gallery.caches.LinkCache;
import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;

public class ReorderSetup {
	public static ItemReorderCallback setupReorderCallback(DirectoryViewModel dirViewModel, RecyclerView recyclerView) {
		DirRVAdapter adapter = (DirRVAdapter) recyclerView.getAdapter();
		return new ItemReorderCallback(recyclerView, new ItemReorderCallback.ReorderCallback() {
			@Override
			public void onReorderComplete(Path destination, ListItem nextItem) {
				Thread reorderThread = new Thread(() -> {
					//Get the selected items from the viewModel's list and pass them along
					Set<UUID> selectedItems = new HashSet<>(dirViewModel.getSelectionRegistry().getSelectedList());
					List<ListItem> toMove = new ArrayList<>();

					//Grab the first instance of each selected item in the list
					List<ListItem> currList = dirViewModel.getFilterRegistry().filteredList.getValue();
					for(int i = 0; i < currList.size(); i++) {
						UUID itemUID = currList.get(i).fileUID;

						if(selectedItems.contains(itemUID)) {
							toMove.add(currList.get(i));
							selectedItems.remove(itemUID);
						}
					}


					try {
						UUID destinationUID = UUID.fromString(destination.getFileName().toString());

						LinkCache linkCache = LinkCache.getInstance();

						//If the item is a link, follow that link
						if(linkCache.isLink(destinationUID)) {
							LinkCache.LinkTarget target = LinkCache.getInstance().getFinalTarget(destinationUID);

							//If the link is to an internal file...
							if(target instanceof LinkCache.InternalTarget) {
								LinkCache.InternalTarget internalTarget = (LinkCache.InternalTarget) target;

								//If the link is to a directory, use the target fileUID
								if(linkCache.isDir(internalTarget.getFileUID()))
									destinationUID = internalTarget.getFileUID();
									//If the link is to a single file (like an image/divider), use the target parentUID
								else
									destinationUID = internalTarget.getParentUID();
							}
						}


						UUID nextItemUID = null;
						if(nextItem != null && !LinkCache.isLinkEnd(nextItem))
							nextItemUID = nextItem.fileUID;


						boolean successful = DirUtilities.moveFiles(toMove, destinationUID, nextItemUID);
						if(successful) return;

						//If the move was not successful, we want to return the list to how it was before we dragged
						Runnable myRunnable = () -> adapter.setList(dirViewModel.getFilterRegistry().filteredList.getValue());
						new Handler(getMainLooper()).post(myRunnable);

					} catch (FileNotFoundException | ContentsNotFoundException | ConnectException | NotDirectoryException e) {
						throw new RuntimeException(e);
					}
					catch (IOException e) {
						throw new RuntimeException(e);
					}
				});
				reorderThread.start();
			}

			@Override
			public void onReorderFailed() {
				//Reset the list, putting the dragged item back where it should be
				adapter.setList(dirViewModel.getFilterRegistry().filteredList.getValue());
			}
		});
	}
}
