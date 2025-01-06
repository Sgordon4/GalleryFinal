package aaa.sgordon.galleryfinal;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class DirRVAdapter extends RecyclerView.Adapter<DirRVAdapter.ViewHolder> {
	private List<String> localDataSet;

	public DirRVAdapter(List<String> localDataSet) {
		this.localDataSet = localDataSet;
	}

	@NonNull
	@Override
	public DirRVAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		View view = LayoutInflater.from(parent.getContext())
				.inflate(R.layout.directory_row_item, parent, false);

		return new ViewHolder(view);
	}

	@Override
	public void onBindViewHolder(@NonNull DirRVAdapter.ViewHolder holder, int position) {
		holder.getTextView().setText(localDataSet.get(position));
	}

	@Override
	public int getItemCount() {
		return localDataSet.size();
	}


	//---------------------------------------------------------------------------------------------

	public static class ViewHolder extends RecyclerView.ViewHolder {
		private final TextView textView;

		public ViewHolder(@NonNull View itemView) {
			super(itemView);

			textView = itemView.findViewById(R.id.textView);
		}

		public TextView getTextView() {
			return textView;
		}
	}
}
