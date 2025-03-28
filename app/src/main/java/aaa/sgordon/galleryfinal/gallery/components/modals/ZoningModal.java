package aaa.sgordon.galleryfinal.gallery.components.modals;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ZoningModal extends DialogFragment {
	private ZoningCallback zoningCallback;


	public static void launch(@NonNull Fragment fragment, @NonNull List<UUID> files, @NonNull ZoningCallback zoningCallback) {

	}
	public static ZoningModal newInstance(@NonNull List<UUID> files, @NonNull ZoningCallback zoningCallback) {
		ZoningModal fragment = new ZoningModal();
		fragment.zoningCallback = zoningCallback;

		


		ArrayList<String> UUIDList = files.stream().map(uuid -> uuid.toString()).collect(Collectors.toList());

		Bundle args = new Bundle();
		args.putParcelableArrayList("FILES", new ArrayList<>(files));
		fragment.setArguments(args);

		return fragment;
	}


	public interface ZoningCallback {

	}
}
