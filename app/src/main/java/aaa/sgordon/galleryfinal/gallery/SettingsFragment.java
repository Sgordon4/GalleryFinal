package aaa.sgordon.galleryfinal.gallery;

import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import java.util.UUID;

import aaa.sgordon.galleryfinal.R;

public class SettingsFragment extends PreferenceFragmentCompat {
	private UUID directoryUID;


	@Override
	public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
		setPreferencesFromResource(R.xml.dir_settings, rootKey);


		final SwitchPreferenceCompat passwordEnabled = (SwitchPreferenceCompat) findPreference("password_enabled");
		final Preference passwordChange = findPreference("password_change");
		passwordChange.setDependency("password_enabled");


		/*
		passwordEnabled.setOnPreferenceChangeListener((preference, newValue) -> {
		});
		 */
	}
}
