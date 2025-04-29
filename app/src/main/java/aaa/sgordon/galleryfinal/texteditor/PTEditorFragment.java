package aaa.sgordon.galleryfinal.texteditor;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.onegravity.rteditor.api.format.RTFormat;

import java.util.UUID;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.repository.hybrid.types.HFile;

public class PTEditorFragment extends RTEditorFragment {

	public static PTEditorFragment initialize(@NonNull String content, @NonNull String name, @NonNull UUID parentUID, @NonNull HFile fileProps) {
		PTEditorFragment fragment = new PTEditorFragment();
		fragment.editorFormat = RTFormat.PLAIN_TEXT;

		Bundle bundle = new Bundle();
		bundle.putString("content", content);
		bundle.putString("name", name);
		bundle.putString("parentUID", parentUID.toString());
		fragment.setArguments(bundle);
		fragment.tempDoNotUse = fileProps;

		return fragment;
	}


	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		//Hide the bottom toolbars
		toolbarBottom1.setVisibility(View.GONE);
		toolbarBottom2.setVisibility(View.GONE);

		//Remove the bullet/numbering options from the top toolbar
		toolbarTop.getMenu().findItem(R.id.action_bullet).setVisible(false);
		toolbarTop.getMenu().findItem(R.id.action_number).setVisible(false);

		rtEditText.setRichTextEditing(false, false);
	}
}
