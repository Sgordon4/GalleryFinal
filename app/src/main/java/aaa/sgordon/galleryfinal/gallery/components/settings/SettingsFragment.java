package aaa.sgordon.galleryfinal.gallery.components.settings;

import android.content.Context;
import android.os.Bundle;
import android.os.Looper;
import android.text.InputFilter;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.EditTextPreference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.FileNotFoundException;
import java.util.Objects;
import java.util.UUID;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridAPI;
import aaa.sgordon.galleryfinal.repository.hybrid.types.HFile;

//Note: The hidden preference will not be respected by links, who need to be hidden as well
public class SettingsFragment extends Fragment {
	private static SettingsViewModel viewModel;

	public static SettingsFragment newInstance(UUID directoryUID, JsonObject startingProps) {
		SettingsFragment fragment = new SettingsFragment();
		Bundle args = new Bundle();
		args.putString("DIRECTORYUID", directoryUID.toString());
		args.putString("STARTINGPROPS", startingProps.toString());
		fragment.setArguments(args);
		return fragment;
	}


	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Bundle args = requireArguments();
		UUID directoryUID = UUID.fromString(args.getString("DIRECTORYUID"));
		JsonObject startingProps = new Gson().fromJson(args.getString("STARTINGPROPS"), JsonObject.class);

		viewModel = new ViewModelProvider(this,
				new SettingsViewModel.Factory(directoryUID, startingProps))
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
			if(viewModel.props.has("password"))
				password.setSummary("PIN set");

			//Set the password input type to number password, and limit it to 16 digits
			password.setOnBindEditTextListener(editText -> {
				editText.setInputType(InputType.TYPE_CLASS_NUMBER);
				//editText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
				editText.setFilters(new InputFilter[] {new InputFilter.LengthFilter(16)});
			});


			//When the preference is clicked, show nothing
			password.setOnPreferenceClickListener(preference -> {
				//if(viewModel.props.has("password"))
				//	password.setText(viewModel.props.get("password").getAsString());
				password.setText("");
				return true;
			});


			//When the password is changed, save it
			password.setOnPreferenceChangeListener((preference, newValue) -> {
				if(newValue.toString().isEmpty()) {
					password.setSummary("No PIN set");
					viewModel.props.remove("password");
				}
				else {
					password.setSummary("PIN set");
					viewModel.props.addProperty("password", newValue.toString());
				}

				viewModel.persistProps(requireContext());

				return true;
			});





			//If the hidden preference is changed, save it
			final SwitchPreferenceCompat hidden = Objects.requireNonNull(findPreference("hidden"));
			if(viewModel.props.has("hidden"))
				hidden.setChecked(true);

			hidden.setOnPreferenceChangeListener((preference, newValue) -> {
				boolean isHidden = (boolean) newValue;
				if(isHidden)
					viewModel.props.addProperty("hidden", true);
				else
					viewModel.props.remove("hidden");

				viewModel.persistProps(requireContext());

				return true;
			});
		}
	}




	//---------------------------------------------------------------------------------------------


	public static class SettingsViewModel extends ViewModel {
		public final UUID dirUID;
		public final JsonObject props;

		public SettingsViewModel(@NonNull UUID dirUID, @NonNull JsonObject startingProps) {
			this.dirUID = dirUID;
			props = new JsonObject();

			//Grab only the props we care about
			if(startingProps.has("password"))
				props.addProperty("password", startingProps.get("password").getAsString());
			if(startingProps.has("hidden"))
				props.addProperty("hidden", startingProps.get("hidden").getAsBoolean());
		}

		public void persistProps(Context context) {
			Thread persist = new Thread(() -> {
				HybridAPI hAPI = HybridAPI.getInstance();
				try {
					hAPI.lockLocal(dirUID);
					HFile fileProps = hAPI.getFileProps(dirUID);
					JsonObject currentAttr = fileProps.userattr;

					//Overwrite any properties in the current attributes with our new settings
					currentAttr.remove("password");
					currentAttr.remove("hidden");
					for(String key : props.keySet())
						currentAttr.addProperty(key, props.get(key).getAsString());

					hAPI.setAttributes(dirUID, currentAttr, fileProps.attrhash);
				}
				catch (FileNotFoundException e) {
					Looper.prepare();
					Toast.makeText(context, "Could not persist properties, file not found!", Toast.LENGTH_SHORT).show();
					//We don't actually have to do anything else, like revert properties, because the file not found means it was just deleted
					//Though we should probably pop backstack...
				}
				finally {
					hAPI.unlockLocal(dirUID);
				}
			});
			persist.start();
		}


		public static class Factory implements ViewModelProvider.Factory {
			private final UUID dirUID;
			private final JsonObject startingProps;
			public Factory(UUID dirUID, JsonObject startingProps) {
				this.dirUID = dirUID;
				this.startingProps = startingProps;
			}

			@NonNull
			@Override
			public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
				if (modelClass.isAssignableFrom(SettingsViewModel.class)) {
					return (T) new SettingsViewModel(dirUID, startingProps);
				}
				throw new IllegalArgumentException("Unknown ViewModel class");
			}
		}
	}
}
