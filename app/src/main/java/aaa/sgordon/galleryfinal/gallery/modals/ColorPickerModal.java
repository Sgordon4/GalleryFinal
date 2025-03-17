package aaa.sgordon.galleryfinal.gallery.modals;

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

import com.jaredrummler.android.colorpicker.ColorPickerView;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.gallery.DirFragment;

public class ColorPickerModal extends DialogFragment {
	private final ColorPickerCallback callback;
	private int color;
	private ColorPickerView colorPicker;
	private EditText colorHex;
	private TextWatcher textWatcher;


	public static void launch(@NonNull DirFragment fragment, int startColor, @NonNull ColorPickerCallback callback) {
		ColorPickerModal dialog = new ColorPickerModal(startColor, callback);
		dialog.show(fragment.getChildFragmentManager(), "color_picker");
	}
	private ColorPickerModal(int startColor, @NonNull ColorPickerCallback callback) {
		this.color = startColor;
		this.callback = callback;
	}


	@NonNull
	@Override
	public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
		AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
		builder.setView(R.layout.fragment_directory_edit_colorpicker);
		builder.setTitle("Custom Color");

		LayoutInflater inflater = getLayoutInflater();
		View view = inflater.inflate(R.layout.fragment_directory_edit_colorpicker, null);
		builder.setView(view);

		colorPicker = view.findViewById(R.id.color_picker);
		colorHex = view.findViewById(R.id.color_hex);


		colorPicker.setColor(color);
		colorPicker.setOnColorChangedListener(newColor -> {
			color = newColor | 0xFF000000;		//Set alpha to 100%
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
					color = hex | 0xFF000000;		//Set alpha to 100%
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
			callback.onColorChanged(color);
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


	public interface ColorPickerCallback {
		void onColorChanged(int color);
	}
}
