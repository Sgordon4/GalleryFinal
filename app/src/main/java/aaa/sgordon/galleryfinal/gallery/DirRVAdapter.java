package aaa.sgordon.galleryfinal.gallery;

import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import aaa.sgordon.galleryfinal.R;

public class DirRVAdapter extends RecyclerView.Adapter<DirRVAdapter.ViewHolder> {
	private List<Pair<UUID, String>> data;

	public DirRVAdapter() {
		data = new ArrayList<>();
	}

	public void setData(List<Pair<UUID, String>> data) {
		this.data = data;
		//Do some DiffUtil stuff here
		notifyDataSetChanged();
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
		holder.getTextView().setText(data.get(position).second);
	}

	@Override
	public int getItemCount() {
		return data.size();
	}


	//---------------------------------------------------------------------------------------------

	public static class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
		private final TextView textView;

		public ViewHolder(@NonNull View itemView) {
			super(itemView);

			textView = itemView.findViewById(R.id.textView);
			itemView.setOnClickListener(this);
		}

		public TextView getTextView() {
			return textView;
		}

		@Override
		public void onClick(View view) {
			System.out.println("Clicking "+getAdapterPosition());
		}
	}
}
