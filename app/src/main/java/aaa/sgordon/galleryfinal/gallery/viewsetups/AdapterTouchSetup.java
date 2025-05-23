package aaa.sgordon.galleryfinal.gallery.viewsetups;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.SharedElementCallback;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;

import com.google.android.material.transition.MaterialFadeThrough;
import com.google.gson.JsonObject;

import java.io.FileNotFoundException;
import java.net.ConnectException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.gallery.DirFragment;
import aaa.sgordon.galleryfinal.gallery.DirRVAdapter;
import aaa.sgordon.galleryfinal.gallery.FilterController;
import aaa.sgordon.galleryfinal.gallery.viewholders.PlainTextViewHolder;
import aaa.sgordon.galleryfinal.repository.gallery.ListItem;
import aaa.sgordon.galleryfinal.gallery.components.password.PasswordModal;
import aaa.sgordon.galleryfinal.gallery.touch.DragSelectCallback;
import aaa.sgordon.galleryfinal.gallery.touch.ItemReorderCallback;
import aaa.sgordon.galleryfinal.gallery.touch.SelectionController;
import aaa.sgordon.galleryfinal.gallery.viewholders.BaseViewHolder;
import aaa.sgordon.galleryfinal.gallery.viewholders.DirectoryViewHolder;
import aaa.sgordon.galleryfinal.gallery.viewholders.GifViewHolder;
import aaa.sgordon.galleryfinal.gallery.viewholders.ImageViewHolder;
import aaa.sgordon.galleryfinal.gallery.viewholders.RichTextViewHolder;
import aaa.sgordon.galleryfinal.gallery.viewholders.VideoViewHolder;
import aaa.sgordon.galleryfinal.repository.gallery.caches.AttrCache;
import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridAPI;
import aaa.sgordon.galleryfinal.repository.hybrid.types.HFile;
import aaa.sgordon.galleryfinal.texteditor.PTEditorFragment;
import aaa.sgordon.galleryfinal.texteditor.RTEditorFragment;
import aaa.sgordon.galleryfinal.utilities.Utilities;
import aaa.sgordon.galleryfinal.viewpager.ViewPagerFragment;

public class AdapterTouchSetup {
	public static DirRVAdapter.AdapterCallbacks setupAdapterCallbacks(DirFragment dirFragment, SelectionController selectionController,
																	  ItemReorderCallback reorderCallback, DragSelectCallback dragSelectCallback, Context context,
																	  ItemTouchHelper reorderHelper, ItemTouchHelper dragSelectHelper) {
		return new DirRVAdapter.AdapterCallbacks() {
			@Override
			public boolean isItemSelected(UUID fileUID) {
				return selectionController.isSelected(fileUID);
			}

			UUID fileUID = null;
			BaseViewHolder holder;
			@Override
			public boolean onHolderMotionEvent(UUID fileUID, BaseViewHolder holder, MotionEvent event) {
				if(event.getAction() == MotionEvent.ACTION_DOWN) {
					this.fileUID = fileUID;
					this.holder = holder;
					isDoubleTapInProgress = false;
				}
				else if(event.getAction() == MotionEvent.ACTION_UP) {
					isDoubleTapInProgress = false;
				}

				reorderCallback.onMotionEvent(event);
				dragSelectCallback.onMotionEvent(event);


				return detector.onTouchEvent(event);
			}



			boolean isDoubleTapInProgress = false;
			final GestureDetector detector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {

				@Override
				public boolean onSingleTapUp(@NonNull MotionEvent e) {
					if(selectionController.isSelecting())
						return false;

					//If we're not selecting, launch a new fragment
					//Launching fragments needs to be super snappy, so it must occur on a single tap up or it will feel slow

					if(holder instanceof DirectoryViewHolder) {
						launchDirectory(dirFragment, holder);
					}
					else if(holder instanceof ImageViewHolder || holder instanceof GifViewHolder || holder instanceof VideoViewHolder) {
						launchViewPager(dirFragment, holder);
					}
					else if(holder instanceof RichTextViewHolder) {
						launchTextEditor(dirFragment, holder, true);
					}
					else if(holder instanceof PlainTextViewHolder) {
						launchTextEditor(dirFragment, holder, false);
					}

					return true;
				}


				@Override
				public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
					//If we're selecting, select/deselect the item
					if(selectionController.isSelecting()) {
						selectionController.toggleSelectItem(fileUID);
						return true;
					}

					return false;
				}


				@Override
				public boolean onDoubleTapEvent(@NonNull MotionEvent e) {
					if(!selectionController.isSelecting())
						return false;

					//If we double tap while selecting, allow a drag to start
					if(e.getAction() == MotionEvent.ACTION_DOWN) {
						isDoubleTapInProgress = true;
					}
					//If we release the tap and we aren't reordering, toggle selection
					else if(e.getAction() == MotionEvent.ACTION_UP && !reorderCallback.isDragging()) {
						selectionController.toggleSelectItem(fileUID);
					}

					return false;
				}


				@Override
				public void onLongPress(@NonNull MotionEvent e) {
					selectionController.startSelecting();
					selectionController.selectItem(fileUID);

					if(isDoubleTapInProgress) {
						//DoubleTap LongPress triggers a reorder
						isDoubleTapInProgress = false;

						//If the list is not currently filtered, the user is free to drag
						FilterController.FilterRegistry fRegistry = dirFragment.dirViewModel.getFilterRegistry();
						if(fRegistry.activeQuery.getValue().isEmpty() && fRegistry.activeTags.getValue().isEmpty())
							reorderHelper.startDrag(holder);
						else
							Toast.makeText(context, "Cannot reorder while filtering.", Toast.LENGTH_SHORT).show();
					}
					else {
						//SingleTap LongPress triggers drag selection
						dragSelectHelper.startDrag(holder);
					}
				}


				@Override
				public boolean onDown(@NonNull MotionEvent e) {
					return true;
				}
			});
		};
	}



	private static void launchTextEditor(DirFragment dirFragment, BaseViewHolder holder, boolean isRichText) {
		dirFragment.setExitTransition(null);
		dirFragment.setExitSharedElementCallback(null);


		//Note: This current setup won't work if the note is a link to a note.
		//We don't have those atm, but if we add them later (doubt it) we'll need to change this.
		Thread launch = new Thread(() -> {
			try {
				ListItem listItem = holder.getListItem();
				UUID parentUID = listItem.parentUID;

				HybridAPI hAPI = HybridAPI.getInstance();

				HFile fileProps = hAPI.getFileProps(listItem.fileUID);
				Uri contentUri = hAPI.getFileContent(fileProps).first;
				String content = Utilities.readFile(contentUri);

				Handler handler = new Handler(Looper.getMainLooper());
				handler.post(() -> {
					Fragment fragment;
					if(isRichText)
						fragment = RTEditorFragment.initialize(content, listItem.getRawName(), parentUID, fileProps);
					else
						fragment = PTEditorFragment.initialize(content, listItem.getRawName(), parentUID, fileProps);

					dirFragment.getParentFragmentManager().beginTransaction()
							.replace(R.id.fragment_container, fragment, DirFragment.class.getSimpleName())
							.addToBackStack(null)
							.commit();
				});
			}
			catch (FileNotFoundException e) {
				Toast.makeText(dirFragment.requireContext(), "The file is not accessible from this device!", Toast.LENGTH_SHORT).show();
			}
			catch (ConnectException e) {
				Toast.makeText(dirFragment.requireContext(), "Could not connect to the server!", Toast.LENGTH_SHORT).show();
			}
			catch (ContentsNotFoundException e) {
				Toast.makeText(dirFragment.requireContext(), "The file contents could not be found!", Toast.LENGTH_SHORT).show();
			}
		});
		launch.start();
	}


	private static void launchDirectory(DirFragment dirFragment, BaseViewHolder holder) {
		ListItem listItem = holder.getListItem();

		dirFragment.setExitTransition(null);
		dirFragment.setExitSharedElementCallback(null);

		AttrCache.getInstance().getAttrAsync(listItem.fileUID, new AttrCache.AttrCallback() {
			@Override
			public void onAttrReady(@NonNull JsonObject attr) {
				String password = "";
				if(attr.has("password"))
					password = attr.get("password").getAsString();

				//If there is a password, launch the password modal first
				if(!password.isEmpty())
					PasswordModal.launch(dirFragment, listItem.getPrettyName(), password, () -> launchDirFragment(dirFragment, listItem));
				else
					launchDirFragment(dirFragment, listItem);
			}
			@Override
			public void onConnectException() {
				new Handler(Looper.getMainLooper()).post(() -> {
					Toast.makeText(dirFragment.requireContext(), "The file is not accessible from this device!", Toast.LENGTH_SHORT).show();
				});
			}
			@Override
			public void onFileNotFoundException() {
				new Handler(Looper.getMainLooper()).post(() -> {
					Toast.makeText(dirFragment.requireContext(), "Could not connect to the server!", Toast.LENGTH_SHORT).show();
				});
			}
		});
	}

	private static void launchDirFragment(DirFragment dirFragment, ListItem listItem) {
		dirFragment.setExitTransition(null);
		dirFragment.setExitSharedElementCallback(null);

		DirFragment fragment = DirFragment.initialize(listItem);

		dirFragment.getParentFragmentManager().beginTransaction()
				.replace(R.id.fragment_container, fragment, DirFragment.class.getSimpleName())
				.addToBackStack(null)
				.commit();
	}


	private static void launchViewPager(DirFragment dirFragment, BaseViewHolder holder) {
		dirFragment.setExitTransition(null);
		dirFragment.setExitSharedElementCallback(null);

		int pos = holder.getBindingAdapterPosition();		//Pos as Adapter sees it
		//int pos = holder.getAbsoluteAdapterPosition();		//Pos as RecyclerView sees it

		//Fade out the grid when exiting
		dirFragment.setExitTransition(new MaterialFadeThrough());

		ViewPagerFragment fragment = ViewPagerFragment.initialize(pos, dirFragment.dirViewModel.getFilterRegistry().filteredList, currItem -> {
			dirFragment.setExitSharedElementCallback(new SharedElementCallback() {
				@Override
				public void onMapSharedElements(List<String> names, Map<String, View> sharedElements) {
					if(currItem == null) return;

					//Get the adapter position of the ViewPager item
					DirRVAdapter adapter = (DirRVAdapter) dirFragment.binding.recyclerview.getAdapter();
					int adapterPos = -1;
					for(int i = 0; i < adapter.list.size(); i++) {
						if(adapter.list.get(i).pathFromRoot.equals(currItem.pathFromRoot)) {
							adapterPos = i;
							break;
						}
					}
					if(adapterPos == -1) return;

					dirFragment.binding.recyclerview.scrollToPosition(adapterPos);

					//Get the child for the adapter position
					BaseViewHolder holder = (BaseViewHolder) dirFragment.binding.recyclerview.findViewHolderForAdapterPosition(adapterPos);
					if(holder == null) return;
					View media = holder.itemView.findViewById(R.id.media);
					if(media == null) return;

					sharedElements.put(names.get(0), media);
				}
			});
		});


		View media = holder.itemView.findViewById(R.id.media);
		dirFragment.getParentFragmentManager().beginTransaction()
				.setReorderingAllowed(true)
				.addSharedElement(media, media.getTransitionName())
				.hide(dirFragment)
				.add(R.id.fragment_container, fragment)
				.addToBackStack(null)
				.commit();
	}
}
