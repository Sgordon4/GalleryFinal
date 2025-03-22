package aaa.sgordon.galleryfinal.gallery.components.password;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import aaa.sgordon.galleryfinal.R;

public class PasswordModal extends DialogFragment {
	private PasswordViewModel viewModel;
	private PasswordCallback callback;
	private EditText passwordField;


	public static void launch(@NonNull Fragment fragment, @NonNull String fileName, @NonNull String password, PasswordCallback callback) {
		PasswordModal dialog = PasswordModal.newInstance(fileName, password, callback);
		dialog.show(fragment.getChildFragmentManager(), "password");
	}
	public static PasswordModal newInstance(@NonNull String fileName, @NonNull String password, PasswordCallback callback) {
		if(password.isEmpty())
			throw new IllegalArgumentException("Password cannot be empty!");

		PasswordModal fragment = new PasswordModal();
		Bundle args = new Bundle();
		args.putString("FILENAME", fileName);
		args.putString("PASSWORD", password);
		fragment.setArguments(args);
		fragment.callback = callback;
		return fragment;
	}
	public interface PasswordCallback {
		void onSuccess();
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

	@NonNull
	@Override
	public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
		AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
		LayoutInflater inflater = getLayoutInflater();
		View view = inflater.inflate(R.layout.modal_password, null);
		builder.setView(view);

		builder.setTitle("Opening "+viewModel.fileName);


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
					dismiss();
				}
			}

			@Override
			public void afterTextChanged(Editable s) {}
		});



		builder.setPositiveButton("Clear", null);
		builder.setNegativeButton(android.R.string.cancel, null);

		AlertDialog dialog = builder.create();
		dialog.setOnShowListener(dialogInterface -> {
			//The dialog will auto-close on successful password entry, so this button will just be to clear the text
			Button button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
			button.setOnClickListener(v -> passwordField.setText(""));
		});

		return dialog;
	}

	@Override
	public void onStart() {
		super.onStart();

		//Ensure the dialog window requests focus and opens the soft keyboard
		if (getDialog() != null && getDialog().getWindow() != null) {
			getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
		}

		//Try to give the password field focus
		passwordField.requestFocus();
	}


	//---------------------------------------------------------------------------------------------


	public static class PasswordViewModel extends ViewModel {
		public final String fileName;
		public final String password;
		public String currentPassword;
		public final PasswordCallback callback;

		public PasswordViewModel(@NonNull String fileName, @NonNull String password, @NonNull PasswordCallback callback) {
			this.fileName = fileName;
			this.password = password;
			this.callback = callback;
			currentPassword = "";
		}

		public static class Factory implements ViewModelProvider.Factory {
			private final String fileName;
			private final String password;
			private final PasswordCallback callback;
			public Factory(String fileName, String password, PasswordCallback callback) {
				this.fileName = fileName;
				this.password = password;
				this.callback = callback;
			}

			@NonNull
			@Override
			public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
				if (modelClass.isAssignableFrom(PasswordViewModel.class)) {
					return (T) new PasswordViewModel(fileName, password, callback);
				}
				throw new IllegalArgumentException("Unknown ViewModel class");
			}
		}
	}

}
