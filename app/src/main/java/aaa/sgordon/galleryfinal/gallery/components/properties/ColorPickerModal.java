package aaa.sgordon.galleryfinal.gallery.components.properties;

import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import aaa.sgordon.galleryfinal.databinding.FragDirEditColorpickerBinding;

public class ColorPickerModal extends DialogFragment {
	private FragDirEditColorpickerBinding binding;
	private ColorPickerViewModel viewModel;
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
		binding = FragDirEditColorpickerBinding.inflate(getLayoutInflater());
		View view = binding.getRoot();

		Bundle args = requireArguments();
		int color = args.getInt("color");

		viewModel = new ViewModelProvider(this,
				new ColorPickerViewModel.Factory(color, tempCallbackDoNotUse))
				.get(ColorPickerViewModel.class);


		AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
		builder.setView(view);
		builder.setTitle("Custom Color");

		AlertDialog dialog = builder.create();
		dialog.setCanceledOnTouchOutside(false);
		return dialog;
	}



	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		return binding.getRoot();
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		ColorPickerView colorPicker = binding.colorPicker;
		EditText colorHex = binding.colorHex;


		colorPicker.setColor(viewModel.color);
		colorPicker.setOnColorChangedListener(newColor -> {
			viewModel.color = newColor | 0xFF000000;		//Set alpha to 100%
			manuallySetColorHex(viewModel.color);
		});


		Drawable checkerboard = AlphaCheckerboard.createCheckerboardDrawable(20, Color.LTGRAY, Color.WHITE);
		binding.alphaCheckerboard.setBackground(checkerboard);
		binding.alphaCheckerboard.setOnClickListener(v -> {
			viewModel.color = Color.TRANSPARENT;
			manuallySetColorHex(viewModel.color);
		});


		textWatcher = new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}
			@Override
			public void afterTextChanged(Editable editable) {}
			@Override
			public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
				String text = charSequence.toString().toUpperCase();

				if(text.length() == 8) {
					int hex = (int) Long.parseLong(text, 16);
					viewModel.color = hex | 0xFF000000;		//Set alpha to 100%
					colorPicker.setColor(viewModel.color, false);
					text = String.format("%08X", viewModel.color);
				}

				int cursorPosition = colorHex.getSelectionStart();
				colorHex.removeTextChangedListener(this);
				colorHex.setText(text);
				colorHex.setSelection(cursorPosition);
				colorHex.addTextChangedListener(this);
			}
		};
		colorHex.addTextChangedListener(textWatcher);
		manuallySetColorHex(viewModel.color);


		Button confirm = view.findViewById(R.id.confirm);
		confirm.setOnClickListener(v -> {
			viewModel.callback.onColorChanged(viewModel.color);
			dismiss();
		});

		Button cancel = view.findViewById(R.id.cancel);
		cancel.setOnClickListener(v -> dismiss());
	}


	private void manuallySetColorHex(int color) {
		//String hex = String.format("%06X", (0xFFFFFF & color));
		String hex = String.format("%08X", color);
		EditText colorHex = binding.colorHex;

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
