package aaa.sgordon.galleryfinal.gallery.components.password;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import aaa.sgordon.galleryfinal.R;

public class AppStartPassword extends Fragment {
	private PasswordViewModel viewModel;
	private PasswordViewModel.PasswordCallback callback;

	private EditText passwordField;

	public static void launch(@NonNull Fragment parent, @NonNull String fileName, @NonNull String password, PasswordViewModel.PasswordCallback callback) {
		AppStartPassword fragment = AppStartPassword.newInstance(fileName, password, callback);
		parent.getChildFragmentManager().beginTransaction()
				.add(fragment, "start_password")
				.commit();
	}
	public static AppStartPassword newInstance(@NonNull String fileName, @NonNull String password, PasswordViewModel.PasswordCallback callback) {
		if(password.isEmpty())
			throw new IllegalArgumentException("Password cannot be empty!");

		AppStartPassword fragment = new AppStartPassword();
		Bundle args = new Bundle();
		args.putString("FILENAME", fileName);
		args.putString("PASSWORD", password);
		fragment.setArguments(args);
		fragment.callback = callback;
		return fragment;
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Bundle args = getArguments();
		String fileName = args.getString("FILENAME");
		String password = args.getString("PASSWORD");

		viewModel = new ViewModelProvider(this,
				new PasswordViewModel.Factory(fileName, password, callback))
				.get(PasswordViewModel.class);
	}


	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.frag_app_password, container, false);
		passwordField = view.findViewById(R.id.password);
		return view;
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		passwordField = view.findViewById(R.id.password);
		passwordField.setText(viewModel.currentPassword);
		passwordField.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				viewModel.currentPassword = s.toString();

				//When the passwords are equivalent, close the dialog
				if(s.toString().equals(viewModel.password)) {
					viewModel.callback.onSuccess();

					//Remove the fragment
					FragmentTransaction transaction = getActivity().getSupportFragmentManager().beginTransaction();
					transaction.remove(AppStartPassword.this);
					transaction.commit();
				}
			}

			@Override
			public void afterTextChanged(Editable s) {}
		});
	}

	@Override
	public void onStart() {
		super.onStart();

		//Ensure the dialog window requests focus and opens the soft keyboard
		requireActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);

		//Try to give the password field focus
		passwordField.requestFocus();

		// Ensure the keyboard is shown
		InputMethodManager inputMethodManager = (InputMethodManager) requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
		if (inputMethodManager != null) {
			inputMethodManager.showSoftInput(passwordField, InputMethodManager.SHOW_IMPLICIT);
		}
	}
}
