package aaa.sgordon.galleryfinal.gallery.components.properties;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.jaredrummler.android.colorpicker.ColorPickerView;

import aaa.sgordon.galleryfinal.R;

public class ColorPickerModal extends DialogFragment {
	private ColorPickerViewModel viewModel;
	private ColorPickerView colorPicker;
	private EditText colorHex;
	private TextWatcher textWatcher;


	private ColorPickerCallback tempCallbackDoNotUse;
	public static void launch(@NonNull Fragment fragment, int startColor, @NonNull ColorPickerCallback callback) {
		ColorPickerModal dialog = new ColorPickerModal();
		dialog.tempCallbackDoNotUse = callback;

		Bundle bundle = new Bundle();
		bundle.putInt("color", startColor);
		dialog.setArguments(bundle);

		dialog.show(fragment.getChildFragmentManager(), "color_picker");
	}

	public interface ColorPickerCallback {
		void onColorChanged(int color);
	}


	@NonNull
	@Override
	public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {

		Bundle args = requireArguments();
		int color = args.getInt("color");

		viewModel = new ViewModelProvider(this,
				new ColorPickerViewModel.Factory(color, tempCallbackDoNotUse))
				.get(ColorPickerViewModel.class);


		AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
		builder.setView(R.layout.frag_dir_edit_colorpicker);
		builder.setTitle("Custom Color");

		LayoutInflater inflater = getLayoutInflater();
		View view = inflater.inflate(R.layout.frag_dir_edit_colorpicker, null);
		builder.setView(view);

		colorPicker = view.findViewById(R.id.color_picker);
		colorHex = view.findViewById(R.id.color_hex);


		colorPicker.setColor(color);
		colorPicker.setOnColorChangedListener(newColor -> {
			viewModel.color = newColor | 0xFF000000;		//Set alpha to 100%
			manuallySetColorHex(newColor);
		});


		textWatcher = new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

			}

			@Override
			public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
				int cursorPosition = colorHex.getSelectionStart();
				colorHex.removeTextChangedListener(this);
				colorHex.setText(charSequence.toString().toUpperCase());
				colorHex.setSelection(cursorPosition);
				colorHex.addTextChangedListener(this);

				if(charSequence.length() == 6) {
					int hex = Integer.parseInt(charSequence.toString(), 16);
					colorPicker.setColor(hex, false);
					viewModel.color = hex | 0xFF000000;		//Set alpha to 100%
				}
			}

			@Override
			public void afterTextChanged(Editable editable) {

			}
		};
		colorHex.addTextChangedListener(textWatcher);
		manuallySetColorHex(color);




		Button confirm = view.findViewById(R.id.confirm);
		confirm.setOnClickListener(v -> {
			viewModel.callback.onColorChanged(color);
			dismiss();
		});

		Button cancel = view.findViewById(R.id.cancel);
		cancel.setOnClickListener(v -> dismiss());


		AlertDialog dialog = builder.create();
		dialog.setCanceledOnTouchOutside(false);
		return dialog;
	}

	private void manuallySetColorHex(int color) {
		String hex = String.format("%06X", (0xFFFFFF & color));

		int cursorPosition = colorHex.getSelectionStart();
		colorHex.removeTextChangedListener(textWatcher);
		colorHex.setText(hex);
		colorHex.setSelection(cursorPosition);
		colorHex.addTextChangedListener(textWatcher);
	}


	//---------------------------------------------------------------------------------------------

	private static class ColorPickerViewModel extends ViewModel {
		private final ColorPickerCallback callback;
		private int color;

		public ColorPickerViewModel(int startColor, @NonNull ColorPickerCallback callback) {
			this.color = startColor;
			this.callback = callback;
		}


		public static class Factory implements ViewModelProvider.Factory {
			private final ColorPickerCallback callback;
			private final int color;

			public Factory(int color, @NonNull ColorPickerCallback callback) {
				this.color = color;
				this.callback = callback;
			}

			@NonNull
			@Override
			public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
				if (modelClass.isAssignableFrom(ColorPickerViewModel.class)) {
					return (T) new ColorPickerViewModel(color, callback);
				}
				throw new IllegalArgumentException("Unknown ViewModel class");
			}
		}
	}
}
