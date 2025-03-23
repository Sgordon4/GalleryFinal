package aaa.sgordon.galleryfinal.gallery.components.password;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

public class PasswordViewModel extends ViewModel {
	public final String fileName;
	public final String password;
	public String currentPassword;
	public final PasswordCallback callback;

	public interface PasswordCallback {
		void onSuccess();
	}

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