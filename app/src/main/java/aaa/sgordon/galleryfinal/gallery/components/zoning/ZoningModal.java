package aaa.sgordon.galleryfinal.gallery.components.zoning;

import android.app.Dialog;
import android.os.Bundle;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridAPI;
import aaa.sgordon.galleryfinal.repository.hybrid.database.HZone;

public class ZoningModal extends DialogFragment {
	private LinkTargetViewModel viewModel;
	private RadioGroup radioGroup;


	//TODO Don't allow zoning to change for Dirs
	// Also we need to make sure when a Dir is created its zoning is set correctly, but that's not done here


	public static void launch(@NonNull Fragment fragment, @NonNull List<UUID> files) {
		ZoningModal dialog = ZoningModal.newInstance(files);
		dialog.show(fragment.getChildFragmentManager(), "zoning");
	}
	public static ZoningModal newInstance(@NonNull List<UUID> files) {
		ZoningModal fragment = new ZoningModal();

		ArrayList<String> uuidStrings = new ArrayList<>();
		for (UUID uuid : files) uuidStrings.add(uuid.toString());

		Bundle args = new Bundle();
		args.putStringArrayList("FILEUIDS", uuidStrings);
		fragment.setArguments(args);

		return fragment;
	}


	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Bundle args = getArguments();
		ArrayList<String> uuidStrings = args.getStringArrayList("FILEUIDS");
		List<UUID> fileUIDs = uuidStrings.stream().map(UUID::fromString).collect(Collectors.toList());

		viewModel = new ViewModelProvider(this,
				new LinkTargetViewModel.Factory(fileUIDs))
				.get(LinkTargetViewModel.class);
	}

	@NonNull
	@Override
	public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
		AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
		LayoutInflater inflater = getLayoutInflater();
		View view = inflater.inflate(R.layout.modal_zoning, null);
		builder.setView(view);

		String title = "Managing "+viewModel.fileUIDs.size()+" file";
		title += (viewModel.fileUIDs.size() > 1) ? "s:" : ":";
		builder.setTitle(title);

		radioGroup = view.findViewById(R.id.zone_group);

		RadioButton local = view.findViewById(R.id.zone_local);
		RadioButton both = view.findViewById(R.id.zone_both);
		RadioButton remote = view.findViewById(R.id.zone_remote);

		viewModel.currZoning.observe(this, zoning -> {
			String bothText = "Device & Cloud ("+viewModel.currentBoth+")";
			if(viewModel.pendingBoth > 0) bothText += " ("+viewModel.pendingBoth+" pending)";
			both.setText(bothText);

			String localText = "Device Only ("+viewModel.currentLocal+")";
			if(viewModel.pendingLocal > 0) localText += " ("+viewModel.pendingLocal+" pending)";
			local.setText(localText);

			String remoteText = "Cloud Only ("+viewModel.currentRemote+")";
			if(viewModel.pendingRemote > 0) remoteText += " ("+viewModel.pendingRemote+" pending)";
			remote.setText(remoteText);
		});



		builder.setPositiveButton(android.R.string.ok, null);
		builder.setNegativeButton(android.R.string.cancel, null);

		AlertDialog dialog = builder.create();
		dialog.setOnShowListener(dialogInterface -> {
			//The dialog will auto-close on successful password entry, so this button will just be to clear the text
			Button button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
			button.setOnClickListener(v -> {
				int selectedID = radioGroup.getCheckedRadioButtonId();
				if(selectedID == -1) {
					Toast.makeText(requireContext(), "Please select an option!", Toast.LENGTH_SHORT).show();
					return;
				}

				//Parse the correct zones from the selected option
				boolean isLocal = selectedID == R.id.zone_local || selectedID == R.id.zone_both;
				boolean isRemote = selectedID == R.id.zone_remote || selectedID == R.id.zone_both;


				Thread thread = new Thread(() -> {
					HybridAPI hAPI = HybridAPI.getInstance();

					//Enqueue a zoning worker for each file
					for(UUID fileUID : viewModel.fileUIDs) {
						try {
							hAPI.putZoning(fileUID, isLocal, isRemote);
						} catch (Exception e) {
							//Honestly idgaf
						}
					}
				});
				thread.start();
				dismiss();
			});
		});

		return dialog;
	}


	//---------------------------------------------------------------------------------------------

	public static class LinkTargetViewModel extends ViewModel {
		public final List<UUID> fileUIDs;
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


		public LinkTargetViewModel(List<UUID> fileUIDs) {
			this.fileUIDs = fileUIDs;
			currZoning = new MutableLiveData<>();

			//Get current zoning information for each file
			Thread thread = new Thread(() -> {
				Map<UUID, Pair<HZone, HZone>> updatedZoning = new HashMap<>();

				HybridAPI hAPI = HybridAPI.getInstance();
				for(UUID fileUID : fileUIDs) {
					HZone zoning = hAPI.getZoningInfo(fileUID);
					if(zoning == null) continue;
					HZone pending = hAPI.getPendingZoningInfo(fileUID);

					updatedZoning.put(fileUID, new Pair<>(zoning, pending));

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
			});
			thread.start();
		}


		public static class Factory implements ViewModelProvider.Factory {
			private final List<UUID> fileUIDs;
			public Factory(List<UUID> fileUIDs) {
				this.fileUIDs = fileUIDs;
			}

			@NonNull
			@Override
			public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
				if (modelClass.isAssignableFrom(LinkTargetViewModel.class)) {
					return (T) new LinkTargetViewModel(fileUIDs);
				}
				throw new IllegalArgumentException("Unknown ViewModel class");
			}
		}
	}
}
