package aaa.sgordon.galleryfinal.gallery.components.zoning;

import android.app.Dialog;
import android.os.Bundle;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.databinding.ModalZoningBinding;
import aaa.sgordon.galleryfinal.repository.gallery.ListItem;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridAPI;
import aaa.sgordon.galleryfinal.repository.hybrid.database.HZone;

public class ZoningModal extends DialogFragment {
	private ModalZoningBinding binding;
	private LinkTargetViewModel viewModel;


	//TODO Don't allow zoning to change for Dirs
	// Also we need to make sure when a Dir is created its zoning is set correctly, but that's not done here


	private List<ListItem> tempListDoNotUse;
	public static void launch(@NonNull Fragment fragment, @NonNull List<ListItem> files) {
		ZoningModal modal = new ZoningModal();
		modal.tempListDoNotUse = files;
		modal.show(fragment.getChildFragmentManager(), "zoning");
	}


	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		viewModel = new ViewModelProvider(this,
				new LinkTargetViewModel.Factory(tempListDoNotUse))
				.get(LinkTargetViewModel.class);
	}

	@NonNull
	@Override
	public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
		binding = ModalZoningBinding.inflate(getLayoutInflater());

		AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
		builder.setView(binding.getRoot());

		String title = "Managing "+viewModel.fileList.size()+" file";
		title += (viewModel.fileList.size() > 1) ? "s:" : ":";
		builder.setTitle(title);



		builder.setPositiveButton(android.R.string.ok, null);
		builder.setNegativeButton(android.R.string.cancel, null);

		AlertDialog dialog = builder.create();
		dialog.setOnShowListener(dialogInterface -> {
			Button button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
			button.setOnClickListener(v -> {
				boolean accepted = applyZoning();
				if(accepted) dismiss();
			});
		});

		return dialog;
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		return binding.getRoot();
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		viewModel.currZoning.observe(this, zoning -> {

			int standardTotal = viewModel.currentLocal + viewModel.currentRemote + viewModel.currentBoth;
			String standardText = "Standard Items ("+standardTotal+")";
			binding.zoneTitleGeneral.setText(standardText);

			String localText = "Device Only ("+viewModel.currentLocal+")";
			if(viewModel.pendingLocal > 0) localText += " ("+viewModel.pendingLocal+" pending)";
			binding.zoneLocal.setText(localText);

			String bothText = "Device & Cloud ("+viewModel.currentBoth+")";
			if(viewModel.pendingBoth > 0) bothText += " ("+viewModel.pendingBoth+" pending)";
			binding.zoneBoth.setText(bothText);

			String remoteText = "Cloud Only ("+viewModel.currentRemote+")";
			if(viewModel.pendingRemote > 0) remoteText += " ("+viewModel.pendingRemote+" pending)";
			binding.zoneRemote.setText(remoteText);


			int dirAndLink = viewModel.numDirs + viewModel.numLinks;
			String dirLinkText = "Directories & Links ("+dirAndLink+")";
			binding.zoneTitleDl.setText(dirLinkText);

			if(dirAndLink > 0)
				binding.zoneDlGroup.setVisibility(View.VISIBLE);
			else
				binding.zoneDlGroup.setVisibility(View.GONE);
		});
	}





	private boolean applyZoning() {
		int selectedID = binding.zoneGroup.getCheckedRadioButtonId();
		if(selectedID == -1) {
			Toast.makeText(requireContext(), "Please select an option!", Toast.LENGTH_SHORT).show();
			return false;
		}

		//Parse the correct zones from the selected option
		boolean isLocal = selectedID == R.id.zone_local || selectedID == R.id.zone_both;
		boolean isRemote = selectedID == R.id.zone_remote || selectedID == R.id.zone_both;


		Thread thread = new Thread(() -> {
			HybridAPI hAPI = HybridAPI.getInstance();

			//Enqueue a zoning worker for each file (excluding dirs and links)
			for(ListItem item : viewModel.fileList) {
				if(item.isDir || item.isLink) continue;

				try {
					hAPI.putZoning(item.fileUID, isLocal, isRemote);
				} catch (Exception e) {
					//Honestly idgaf
				}
			}
		});
		thread.start();
		return true;
	}


	//---------------------------------------------------------------------------------------------
	//---------------------------------------------------------------------------------------------

	public static class LinkTargetViewModel extends ViewModel {
		public final List<ListItem> fileList;
		//Per file UUID, contains current and pending zoning
		public final MutableLiveData<Map<UUID, Pair<HZone, HZone>>> currZoning;

		int currentLocal = 0;
		int pendingLocal = 0;
		int currentRemote = 0;
		int pendingRemote = 0;
		int currentBoth = 0;
		int pendingBoth = 0;

		//Dirs and Links will always attempt to be zoned to both
		int numDirs = 0;
		int numLinks = 0;


		public LinkTargetViewModel(List<ListItem> fileList) {
			this.fileList = fileList;
			currZoning = new MutableLiveData<>();

			//Get current zoning information for each file
			new Thread(this::refreshData).start();
		}


		private void refreshData() {
			Map<UUID, Pair<HZone, HZone>> updatedZoning = new HashMap<>();

			HybridAPI hAPI = HybridAPI.getInstance();
			for(ListItem item : fileList) {
				HZone zoning = hAPI.getZoningInfo(item.fileUID);
				if(zoning == null) continue;
				HZone pending = hAPI.getPendingZoningInfo(item.fileUID);

				updatedZoning.put(item.fileUID, new Pair<>(zoning, pending));


				if(item.isDir) {
					numDirs++;
					continue;
				}
				else if(item.isLink) {
					numLinks++;
					continue;
				}


				//Consider current zoning info for the file
				if(zoning.isLocal && zoning.isRemote) currentBoth++;
				else if(zoning.isLocal) currentLocal++;
				else if(zoning.isRemote) currentRemote++;

				//Consider pending zoning info for the file
				if(pending != null) {
					if(pending.isLocal && pending.isRemote) pendingBoth++;
					else if(pending.isLocal) pendingLocal++;
					else if(pending.isRemote) pendingRemote++;
				}
			}

			currZoning.postValue(updatedZoning);
		}


		public static class Factory implements ViewModelProvider.Factory {
			private final List<ListItem> fileList;
			public Factory(List<ListItem> fileList) {
				this.fileList = fileList;
			}

			@NonNull
			@Override
			public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
				if (modelClass.isAssignableFrom(LinkTargetViewModel.class)) {
					return (T) new LinkTargetViewModel(fileList);
				}
				throw new IllegalArgumentException("Unknown ViewModel class");
			}
		}
	}
}
