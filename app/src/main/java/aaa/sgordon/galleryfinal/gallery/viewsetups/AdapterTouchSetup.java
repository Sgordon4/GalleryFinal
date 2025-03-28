package aaa.sgordon.galleryfinal.gallery.viewsetups;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.navigation.NavController;
import androidx.navigation.fragment.FragmentNavigator;
import androidx.recyclerview.widget.ItemTouchHelper;

import java.util.UUID;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.gallery.DirFragment;
import aaa.sgordon.galleryfinal.gallery.DirFragmentDirections;
import aaa.sgordon.galleryfinal.gallery.DirRVAdapter;
import aaa.sgordon.galleryfinal.gallery.DirectoryViewModel;
import aaa.sgordon.galleryfinal.gallery.FilterController;
import aaa.sgordon.galleryfinal.gallery.ListItem;
import aaa.sgordon.galleryfinal.gallery.components.password.PasswordModal;
import aaa.sgordon.galleryfinal.gallery.touch.DragSelectCallback;
import aaa.sgordon.galleryfinal.gallery.touch.ItemReorderCallback;
import aaa.sgordon.galleryfinal.gallery.touch.SelectionController;
import aaa.sgordon.galleryfinal.gallery.viewholders.BaseViewHolder;
import aaa.sgordon.galleryfinal.gallery.viewholders.DirectoryViewHolder;
import aaa.sgordon.galleryfinal.gallery.viewholders.GifViewHolder;
import aaa.sgordon.galleryfinal.gallery.viewholders.ImageViewHolder;
import aaa.sgordon.galleryfinal.gallery.viewholders.VideoViewHolder;

public class AdapterTouchSetup {
	public static DirRVAdapter.AdapterCallbacks setupAdapterCallbacks(DirFragment dirFragment, SelectionController selectionController,
																	  ItemReorderCallback reorderCallback, DragSelectCallback dragSelectCallback, Context context,
																	  ItemTouchHelper reorderHelper, ItemTouchHelper dragSelectHelper, NavController navController) {
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
				public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
					//If we're selecting, select/deselect the item
					if(selectionController.isSelecting())
						selectionController.toggleSelectItem(fileUID);

					//If we're not selecting, launch a new fragment

					else if(holder instanceof DirectoryViewHolder) {
						ListItem listItem = holder.getListItem();

						if(listItem.attr.has("password")) {
							String password = listItem.attr.get("password").getAsString();

							if(!password.isEmpty()) {
								PasswordModal.launch(dirFragment, listItem.name, password, () -> {
									//Upon successful password entry, launch the directory fragment
									DirFragmentDirections.ActionToDirectoryFragment action =
											DirFragmentDirections.actionToDirectoryFragment(listItem.fileUID);

									action.setDirectoryName(listItem.name);
									navController.navigate(action);
								});
							}
						}
						//If there is no password, launch the directory fragment
						else {
							DirFragmentDirections.ActionToDirectoryFragment action =
									DirFragmentDirections.actionToDirectoryFragment(listItem.fileUID);

							action.setDirectoryName(listItem.name);
							navController.navigate(action);
						}
					}


					else if(holder instanceof ImageViewHolder || holder instanceof GifViewHolder || holder instanceof VideoViewHolder) {
						//int pos = holder.getBindingAdapterPosition();		//Pos as Adapter sees it
						int pos = holder.getAbsoluteAdapterPosition();		//Pos as RecyclerView sees it

						//Transition is causing visual problems I don't like, and Google photos
						// doesn't even use an exit transition, so I'm disabling it
						/*
						//Fade out the grid when transitioning
						setExitTransition(TransitionInflater.from(getContext())
								.inflateTransition(R.transition.grid_fade_transition));

						// Exclude the clicked card from the exit transition (e.g. the card will disappear immediately
						// instead of fading out with the rest to prevent an overlapping animation of fade and move).
						((TransitionSet) getExitTransition()).excludeTarget(holder.itemView.findViewById(R.id.child), true);
						 */



						DirFragmentDirections.ActionToViewPagerFragment action = DirFragmentDirections
								.actionToViewPagerFragment(dirFragment.dirViewModel.getDirUID());
						action.setFromPosition(pos);

						View mediaView = holder.itemView.findViewById(R.id.media);

						FragmentNavigator.Extras extras = new FragmentNavigator.Extras.Builder()
								.addSharedElement(mediaView, mediaView.getTransitionName())
								.build();

						//binding.galleryAppbar.appbar.setExpanded(false, false);
						navController.navigate(action, extras);
					}
					return true;
				}

				@Override
				public boolean onDoubleTapEvent(@NonNull MotionEvent e) {
					if(e.getAction() == MotionEvent.ACTION_DOWN)
						isDoubleTapInProgress = true;

					return false;
				}

				@Override
				public boolean onDown(@NonNull MotionEvent e) {
					return true;
				}
			});
		};
	}
}
