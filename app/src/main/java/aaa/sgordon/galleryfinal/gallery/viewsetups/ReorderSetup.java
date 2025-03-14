package aaa.sgordon.galleryfinal.gallery.viewsetups;

import static android.os.Looper.getMainLooper;

import android.os.Handler;
import android.util.Pair;

import androidx.recyclerview.widget.RecyclerView;

import java.io.FileNotFoundException;
import java.net.ConnectException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import aaa.sgordon.galleryfinal.gallery.DirRVAdapter;
import aaa.sgordon.galleryfinal.gallery.TraversalHelper;
import aaa.sgordon.galleryfinal.utilities.DirUtilities;
import aaa.sgordon.galleryfinal.gallery.DirectoryViewModel;
import aaa.sgordon.galleryfinal.gallery.touch.ItemReorderCallback;
import aaa.sgordon.galleryfinal.repository.caches.LinkCache;
import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;

public class ReorderSetup {
	public static ItemReorderCallback setupReorderCallback(DirectoryViewModel dirViewModel, RecyclerView recyclerView) {
		DirRVAdapter adapter = (DirRVAdapter) recyclerView.getAdapter();
		return new ItemReorderCallback(recyclerView, (destination, nextItem) -> {
			Thread reorderThread = new Thread(() -> {
				//Get the selected items from the viewModel's list and pass them along
				Set<UUID> selectedItems = new HashSet<>(dirViewModel.getSelectionRegistry().getSelectedList());
				List<TraversalHelper.ListItem> toMove = new ArrayList<>();

				//Grab the first instance of each selected item in the list
				List<TraversalHelper.ListItem> currList = dirViewModel.getFilterRegistry().filteredList.getValue();
				for(int i = 0; i < currList.size(); i++) {
					UUID itemUID = currList.get(i).fileUID;

					if(selectedItems.contains(itemUID)) {
						toMove.add(currList.get(i));
						selectedItems.remove(itemUID);
					}
				}


				try {
					UUID destinationUID = UUID.fromString(destination.getFileName().toString());

					System.out.println("Reordering: ");
					System.out.println(destination);
					System.out.println(destinationUID);
					System.out.println(nextItem);


					UUID nextItemUID = null;
					if(nextItem != null && !nextItem.type.equals(TraversalHelper.ListItemType.LINKEND))
						nextItemUID = nextItem.fileUID;


					boolean successful = DirUtilities.moveFiles(toMove, destinationUID, nextItemUID);
					if(successful) return;

					//If the move was not successful, we want to return the list to how it was before we dragged
					Runnable myRunnable = () -> adapter.setList(dirViewModel.getFilterRegistry().filteredList.getValue());
					new Handler(getMainLooper()).post(myRunnable);

				} catch (FileNotFoundException | ContentsNotFoundException | ConnectException | NotDirectoryException e) {
					throw new RuntimeException(e);
				}
			});
			reorderThread.start();
		});
	}
}
