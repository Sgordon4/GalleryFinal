package aaa.sgordon.galleryfinal.gallery;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.UUID;

import aaa.sgordon.galleryfinal.R;

public class SettingsFragment extends Fragment {
	private SettingsViewModel viewModel;

	public static SettingsFragment newInstance(UUID directoryUID, JsonObject startingProps) {
		System.out.println("New instance");
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
		System.out.println("Creating");

		Bundle args = getArguments();
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


			final SwitchPreferenceCompat passwordEnabled = (SwitchPreferenceCompat) findPreference("password_enabled");
			final Preference passwordChange = findPreference("password_change");
			passwordChange.setDependency("password_enabled");


			/*
			passwordEnabled.setOnPreferenceChangeListener((preference, newValue) -> {
			});
			 */
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
			if(startingProps.has("password")) {
				props.addProperty("password", startingProps.get("password").getAsString());
				props.addProperty("password_enabled", true);
			}
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
