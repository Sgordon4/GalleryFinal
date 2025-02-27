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
import aaa.sgordon.galleryfinal.gallery.DirUtilities;
import aaa.sgordon.galleryfinal.gallery.DirectoryViewModel;
import aaa.sgordon.galleryfinal.gallery.touch.ItemReorderCallback;
import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;

public class ReorderSetup {
	public static ItemReorderCallback setupReorderCallback(DirectoryViewModel dirViewModel, RecyclerView recyclerView) {
		DirRVAdapter adapter = (DirRVAdapter) recyclerView.getAdapter();
		return new ItemReorderCallback(recyclerView, (destination, nextItem) -> {
			Thread reorderThread = new Thread(() -> {
				//Get the selected items from the viewModel's list and pass them along
				Set<UUID> selectedItems = new HashSet<>(dirViewModel.getSelectionRegistry().getSelectedList());
				List<Pair<Path, String>> toMove = new ArrayList<>();

				//Grab the first instance of each selected item in the list
				List<Pair<Path, String>> currList = dirViewModel.getFilterRegistry().filteredList.getValue();
				for(int i = 0; i < currList.size(); i++) {
					//Get the UUID of this item
					String UUIDString = currList.get(i).first.getFileName().toString();
					if(UUIDString.equals("END"))
						UUIDString = currList.get(i).first.getParent().getFileName().toString();
					UUID itemUID = UUID.fromString(UUIDString);

					if(selectedItems.contains(itemUID)) {
						toMove.add(currList.get(i));
						selectedItems.remove(itemUID);
					}
				}


				try {
					UUID destinationUID = UUID.fromString(destination.getFileName().toString());
					destinationUID = dirViewModel.getDirCache().resolveDirUID(destinationUID);
					if(destinationUID == null) throw new RuntimeException();


					UUID nextItemUID = null;
					if(nextItem != null && !nextItem.getFileName().toString().equals("END"))
						nextItemUID = UUID.fromString(nextItem.getFileName().toString());


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
