package aaa.sgordon.galleryfinal.gallery.viewsetups;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.transition.Transition;
import androidx.transition.TransitionInflater;
import androidx.transition.TransitionSet;

import com.google.android.material.transition.MaterialContainerTransform;
import com.google.android.material.transition.MaterialFadeThrough;

import java.util.UUID;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.gallery.DirFragment;
import aaa.sgordon.galleryfinal.gallery.DirRVAdapter;
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
						//dragSelectHelper.startDrag(holder);
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
									DirFragment fragment = DirFragment.initialize(listItem.fileUID, listItem.name);
									dirFragment.getParentFragmentManager().beginTransaction()
											.replace(R.id.fragment_container, fragment)
											.addToBackStack(null)
											.commit();
								});
							}
						}
						//If there is no password, launch the directory fragment
						else {
							DirFragment fragment = DirFragment.initialize(listItem.fileUID, listItem.name);
							dirFragment.getParentFragmentManager().beginTransaction()
									.replace(R.id.fragment_container, fragment)
									.addToBackStack(null)
									.commit();
						}
					}


					else if(holder instanceof ImageViewHolder || holder instanceof GifViewHolder || holder instanceof VideoViewHolder) {
						//int pos = holder.getBindingAdapterPosition();		//Pos as Adapter sees it
						int pos = holder.getAbsoluteAdapterPosition();		//Pos as RecyclerView sees it

						ViewPagerFragment fragment = ViewPagerFragment.initialize(dirFragment.dirViewModel.getDirUID(), pos);

						View media = holder.itemView.findViewById(R.id.media);


						//Fade out the grid when exiting
						dirFragment.setExitTransition(new MaterialFadeThrough());




						//fragment.setSharedElementEnterTransition(new MaterialContainerTransform());

						Transition transition = TransitionInflater.from(dirFragment.requireContext())
								.inflateTransition(R.transition.image_shared_element_transition);
						//fragment.setSharedElementEnterTransition(transition);

						MaterialContainerTransform transform = new MaterialContainerTransform();
						transform.setDuration(300);
						transform.setDrawingViewId(R.id.fragment_container);
						transform.setScrimColor(Color.TRANSPARENT);
						transform.setAllContainerColors(Color.TRANSPARENT);
						transform.setFitMode(MaterialContainerTransform.FIT_MODE_AUTO); // or FIT_MODE_WIDTH
						transform.setFadeMode(MaterialContainerTransform.FADE_MODE_THROUGH);
						fragment.setSharedElementEnterTransition(transform);




						dirFragment.getParentFragmentManager().beginTransaction()
								.setReorderingAllowed(true)
								.addSharedElement(media, media.getTransitionName())
								.hide(dirFragment)
								.add(R.id.fragment_container, fragment)
								.addToBackStack(null)
								.commit();



						Handler handler = new Handler(dirFragment.requireActivity().getMainLooper());
						handler.postDelayed(() -> {
							media.setScaleX(1f);
							media.setScaleY(1f);
							media.setRotation(0f);
							//dirFragment.binding.recyclerview.getAdapter().notifyItemChanged(pos);
						}, 1000);

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
