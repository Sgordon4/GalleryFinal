package aaa.sgordon.galleryfinal.gallery.components.modals;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
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
			local.setText("Device Only ("+viewModel.countLocalOnly+")");
			both.setText("Device & Cloud ("+viewModel.countRemoteOnly+")");
			remote.setText("Cloud Only ("+viewModel.countBoth+")");
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
							hAPI.setZoning(fileUID, isLocal, isRemote);
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
		public final MutableLiveData<Map<UUID, HZone>> currZoning;

		int countLocalOnly = 0;
		int countRemoteOnly = 0;
		int countBoth = 0;


		public LinkTargetViewModel(List<UUID> fileUIDs) {
			this.fileUIDs = fileUIDs;
			currZoning = new MutableLiveData<>();

			//Get current zoning information for each file
			Thread thread = new Thread(() -> {
				Map<UUID, HZone> updatedZoning = new HashMap<>();
				HybridAPI hAPI = HybridAPI.getInstance();
				for(UUID fileUID : fileUIDs) {
					HZone zoning = hAPI.getZoningInfo(fileUID);
					if(zoning == null) continue;

					updatedZoning.put(fileUID, zoning);

					if(zoning.isLocal && zoning.isRemote) countBoth++;
					else if(zoning.isLocal) countLocalOnly++;
					else if(zoning.isRemote) countRemoteOnly++;
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
