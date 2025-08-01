package aaa.sgordon.galleryfinal.gallery.components.properties;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.EditTextPreference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.FileNotFoundException;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.UUID;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.repository.gallery.ListItem;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridAPI;
import aaa.sgordon.galleryfinal.repository.hybrid.types.HFile;

//Note: The hidden preference will not be respected by links, who need to be hidden as well
public class SettingsFragment extends Fragment {
	private static SettingsViewModel viewModel;

	public static SettingsFragment newInstance(UUID directoryUID, UUID parentUID, String name, JsonObject startingProps) {
		SettingsFragment fragment = new SettingsFragment();
		Bundle args = new Bundle();
		args.putString("DIRECTORYUID", directoryUID.toString());
		args.putString("PARENTUID", parentUID.toString());
		args.putString("NAME", name);
		args.putString("STARTINGPROPS", startingProps.toString());
		fragment.setArguments(args);
		return fragment;
	}


	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Bundle args = requireArguments();
		UUID directoryUID = UUID.fromString(args.getString("DIRECTORYUID"));
		UUID parentUID = UUID.fromString(args.getString("PARENTUID"));
		String name = args.getString("NAME");
		JsonObject startingProps = new Gson().fromJson(args.getString("STARTINGPROPS"), JsonObject.class);

		viewModel = new ViewModelProvider(this,
				new SettingsViewModel.Factory(directoryUID, parentUID, name, startingProps))
				.get(SettingsViewModel.class);
	}


	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.frag_dir_settings, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		MaterialToolbar toolbar = view.findViewById(R.id.toolbar);
		toolbar.setTitle(viewModel.listItem.getPrettyName());
		toolbar.setNavigationOnClickListener(v -> {
			requireActivity().getOnBackPressedDispatcher().onBackPressed();
		});

		getChildFragmentManager()
				.beginTransaction()
				.replace(R.id.settings_container, new SettingsPrefs())
				.commit();
	}

	//---------------------------------------------------------------------------------------------

	public static class SettingsPrefs extends PreferenceFragmentCompat {
		@Override
		public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
			setPreferencesFromResource(R.xml.dir_settings, rootKey);
		}

		@Override
		public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
			super.onViewCreated(view, savedInstanceState);


			final EditTextPreference password = Objects.requireNonNull(findPreference("password"));
			if(viewModel.password != null)
				password.setSummary("PIN set");

			//Set the password input type to number password, and limit it to 16 digits
			password.setOnBindEditTextListener(editText -> {
				editText.setInputType(InputType.TYPE_CLASS_NUMBER);
				//editText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
				editText.setFilters(new InputFilter[] {new InputFilter.LengthFilter(16)});
			});


			//When the preference is clicked, show nothing
			password.setOnPreferenceClickListener(preference -> {
				//if(viewModel.password = null)
				//	password.setText(viewModel.props.get("password").getAsString());
				password.setText("");
				return true;
			});


			//When the password is changed, save it
			password.setOnPreferenceChangeListener((preference, newValue) -> {
				if(newValue.toString().isEmpty()) {
					password.setSummary("No PIN set");
					viewModel.password = null;
				}
				else {
					password.setSummary("PIN set");
					viewModel.password = newValue.toString();
				}

				viewModel.persistProps(requireContext());

				return true;
			});





			//If the hidden preference is changed, save it
			final SwitchPreferenceCompat hidden = Objects.requireNonNull(findPreference("hidden"));
			hidden.setChecked(viewModel.isHidden);

			hidden.setOnPreferenceChangeListener((preference, newValue) -> {
				if((boolean) newValue) {
					//Launch a confirmation dialog first
					AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
					builder.setTitle("Hide Directory?");
					builder.setMessage("To reveal a hidden Directory, you must filter for its full name:\n"+viewModel.listItem.getPrettyName());

					builder.setPositiveButton("Yes", (dialogInterface, which) -> {
						viewModel.isHidden = true;
						viewModel.persistProps(requireContext());

						hidden.setChecked(true);
					});
					builder.setNegativeButton("No", null);

					AlertDialog dialog = builder.create();
					dialog.show();

					return false;
				}
				else {
					viewModel.isHidden = false;
					viewModel.persistProps(requireContext());
					return true;
				}
			});
		}
	}




	//---------------------------------------------------------------------------------------------


	public static class SettingsViewModel extends ViewModel {
		public final ListItem listItem;
		public String password = null;
		public boolean isHidden;

		public SettingsViewModel(@NonNull UUID directoryUID, @NonNull UUID parentUID, @NonNull String name, @NonNull JsonObject startingProps) {
			listItem = new ListItem(directoryUID, parentUID, true, false, name, Paths.get(""), ListItem.Type.DIRECTORY);

			//We only care about hidden and password
			isHidden = listItem.isHidden();
			if(startingProps.has("password"))
				password = startingProps.get("password").getAsString();
		}

		public void persistProps(Context context) {
			//Persist password
			Thread persist = new Thread(() -> {
				HybridAPI hAPI = HybridAPI.getInstance();
				try {
					hAPI.lockLocal(listItem.fileUID);
					HFile fileProps = hAPI.getFileProps(listItem.fileUID);
					JsonObject currentAttr = fileProps.userattr;

					//Password is stored in attributes
					JsonElement currPass = currentAttr.get("password");
					//If nothing changed, don't overwrite (if both are null or both are identical)
					if(!(currPass == null && password == null) && !(currPass != null && currPass.getAsString().equals(password))) {
						currentAttr.remove("password");
						if(password != null) currentAttr.addProperty("password", password);
						hAPI.setAttributes(listItem.fileUID, currentAttr, fileProps.attrhash);
					}
				}
				catch (FileNotFoundException e) {
					new Handler(Looper.getMainLooper()).post(() ->
							Toast.makeText(context, "Could not persist properties, file not found!", Toast.LENGTH_SHORT).show());
					//We don't actually have to do anything else, like revert properties, because the file not found means it was just deleted
					//Though we should probably pop backstack...
				}
				catch (Exception e) {
					new Handler(Looper.getMainLooper()).post(() ->
							Toast.makeText(context, "Property update failed, could not connect to server!", Toast.LENGTH_SHORT).show());

					//TODO Remove this in prod, this is for making sure I remember to make dirs zone to both local and remote
					throw new RuntimeException();
				}
				finally {
					hAPI.unlockLocal(listItem.fileUID);
				}
			});
			persist.start();

			//Persist isHidden
			if(isHidden != listItem.isHidden())
				listItem.setHidden(isHidden);
		}


		public static class Factory implements ViewModelProvider.Factory {
			private final UUID directoryUID;
			private final UUID parentUID;
			private final String name;
			private final JsonObject startingProps;

			public Factory(UUID directoryUID, UUID parentUID, String name, JsonObject startingProps) {
				this.directoryUID = directoryUID;
				this.parentUID = parentUID;
				this.name = name;
				this.startingProps = startingProps;
			}

			@NonNull
			@Override
			public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
				if (modelClass.isAssignableFrom(SettingsViewModel.class)) {
					return (T) new SettingsViewModel(directoryUID, parentUID, name, startingProps);
				}
				throw new IllegalArgumentException("Unknown ViewModel class");
			}
		}
	}
}
