package aaa.sgordon.galleryfinal.gallery.components.modals;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import aaa.sgordon.galleryfinal.R;

public class ZoningModal extends DialogFragment {
	private LinkTargetViewModel viewModel;


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

		builder.setPositiveButton(android.R.string.ok, null);
		builder.setNegativeButton(android.R.string.cancel, null);

		return builder.create();
	}


	//---------------------------------------------------------------------------------------------

	public static class LinkTargetViewModel extends ViewModel {
		public final List<UUID> fileUIDs;

		public LinkTargetViewModel(List<UUID> fileUIDs) {
			this.fileUIDs = fileUIDs;
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
