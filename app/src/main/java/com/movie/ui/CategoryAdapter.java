package com.movie.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.movie.R;
import com.movie.data.CategoryEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView adapter that renders the Scheduler Categories list shown on
 * both the Setup page (mini preview) and the dedicated Categories page.
 */
public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.Holder> {

    public interface Listener {
        void onToggleEnabled(CategoryEntity category, boolean enabled);
        void onEdit(CategoryEntity category);
        void onDelete(CategoryEntity category);
    }

    private final Listener listener;
    private List<CategoryEntity> items = new ArrayList<>();

    public CategoryAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<CategoryEntity> next) {
        this.items = next == null ? new ArrayList<CategoryEntity>() : new ArrayList<>(next);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_category, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        final CategoryEntity c = items.get(position);
        h.name.setText(c.getName());
        h.path.setText(c.getPath());
        h.check.setChecked(c.isEnabled());
        h.check.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onToggleEnabled(c, h.check.isChecked());
            }
        });
        h.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onEdit(c);
            }
        });
        h.edit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onEdit(c);
            }
        });
        h.delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onDelete(c);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView path;
        final android.widget.CheckBox check;
        final View edit;
        final View delete;

        Holder(@NonNull View v) {
            super(v);
            name = v.findViewById(R.id.category_name);
            path = v.findViewById(R.id.category_path);
            check = v.findViewById(R.id.category_check);
            edit = v.findViewById(R.id.category_edit);
            delete = v.findViewById(R.id.category_delete);
        }
    }
}
