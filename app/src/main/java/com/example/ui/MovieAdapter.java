package com.example.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.R;
import com.example.data.MovieRecord;
import com.example.util.UiUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Java / RecyclerView replacement for the Compose `MovieCardItem` used inside the
 * LazyColumn of DashboardScreen.kt.
 *
 * Compose card behaviour reproduced here:
 *  - CircularStatusIndicator toggle (radio-style check)
 *  - expandable panel: Path, Copy Page Link (Col F), Delete Movie Database Entry
 *  - always-visible: Copy Link (Col E) and "Open in Ulaa"
 *  - status text: category contains "2026" -> Completed (green) else In process (orange)
 */
public class MovieAdapter extends RecyclerView.Adapter<MovieAdapter.MovieViewHolder> {

    public interface Listener {
        void onToggleCheck(MovieRecord movie, boolean checked);

        void onDelete(MovieRecord movie);
    }

    private final Context context;
    private final Listener listener;
    private final List<MovieRecord> items = new ArrayList<>();
    private Set<Integer> selectedIds = new HashSet<>();

    public MovieAdapter(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void submit(List<MovieRecord> movies) {
        items.clear();
        if (movies != null) items.addAll(movies);
        notifyDataSetChanged();
    }

    public void setSelectedIds(Set<Integer> ids) {
        this.selectedIds = ids == null ? new HashSet<Integer>() : ids;
    }

    @NonNull
    @Override
    public MovieViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_movie_card, parent, false);
        return new MovieViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MovieViewHolder h, int position) {
        final MovieRecord movie = items.get(position);
        final boolean isChecked = selectedIds.contains(movie.getId());

        h.name.setText(movie.getName());

        boolean isTamilYear = movie.getCategory().contains("2026");
        h.statusText.setText(isTamilYear ? "Completed" : "In process");
        h.statusText.setTextColor(ContextCompat.getColor(context,
                isTamilYear ? R.color.bubble_green : R.color.bubble_orange));
        h.statusDot.setBackgroundResource(isTamilYear
                ? R.drawable.bg_dot_filled : R.drawable.bg_dot_empty);
        h.statusDot.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(context, isTamilYear ? R.color.bubble_green : R.color.bubble_orange)));

        h.categoryText.setText(movie.getCategory().isEmpty() ? "Uncategorized" : movie.getCategory());

        // CircularStatusIndicator
        h.selectCircle.setBackgroundResource(
                isChecked ? R.drawable.bg_select_circle_on : R.drawable.bg_select_circle_off);
        h.selectIcon.setVisibility(isChecked ? View.VISIBLE : View.GONE);

        // header toggles expansion
        h.header.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean expanded = h.expandedPanel.getVisibility() == View.VISIBLE;
                h.expandedPanel.setVisibility(expanded ? View.GONE : View.VISIBLE);
                h.chevron.setText(expanded ? "chevron_right" : "expand_less");
            }
        });

        h.selectCircle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onToggleCheck(movie, !isChecked);
            }
        });

        // Path row
        if (movie.getSublink().isEmpty()) {
            h.pathRow.setVisibility(View.GONE);
        } else {
            h.pathRow.setVisibility(View.VISIBLE);
            h.pathText.setText("Path: " + movie.getSublink());
        }

        // Copy Page Link (Col F)
        if (movie.getPageUrl().isEmpty()) {
            h.copyPageRow.setVisibility(View.GONE);
        } else {
            h.copyPageRow.setVisibility(View.VISIBLE);
            h.copyPageRow.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    UiUtils.copyToClipboard(context, movie.getPageUrl(), "Col F Page");
                    Toast.makeText(context, "Copied index URL!", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // Delete
        h.deleteRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onDelete(movie);
            }
        });

        // Copy Link (Col E) with fallback to pageUrl / sublink
        h.btnCopyLink.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!movie.getLink().isEmpty()) {
                    UiUtils.copyToClipboard(context, movie.getLink(), "Movie Link");
                    Toast.makeText(context, "Link (Col E) copied directly!", Toast.LENGTH_SHORT).show();
                } else {
                    String fallback = !movie.getPageUrl().isEmpty()
                            ? movie.getPageUrl() : movie.getSublink();
                    if (!fallback.isEmpty()) {
                        UiUtils.copyToClipboard(context, fallback, "Fallback Link");
                        Toast.makeText(context, "Copied page fallback link!", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(context, "No destination link URL available!",
                                Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });

        // Open in Ulaa
        h.btnOpenUlaa.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String targetUrl = !movie.getLink().isEmpty()
                        ? movie.getLink() : movie.getPageUrl();
                if (!targetUrl.isEmpty()) {
                    UiUtils.launchUrlInUlaa(context, targetUrl);
                } else {
                    Toast.makeText(context, "No URL link available to expand!",
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class MovieViewHolder extends RecyclerView.ViewHolder {
        final LinearLayout header;
        final View selectCircle;
        final TextView selectIcon;
        final TextView name;
        final View statusDot;
        final TextView statusText;
        final TextView categoryText;
        final TextView chevron;
        final LinearLayout expandedPanel;
        final View pathRow;
        final TextView pathText;
        final View copyPageRow;
        final View deleteRow;
        final View btnCopyLink;
        final View btnOpenUlaa;

        MovieViewHolder(View v) {
            super(v);
            header = v.findViewById(R.id.card_header);
            selectCircle = v.findViewById(R.id.select_circle);
            selectIcon = v.findViewById(R.id.select_check);
            name = v.findViewById(R.id.movie_name);
            statusDot = v.findViewById(R.id.status_dot);
            statusText = v.findViewById(R.id.status_text);
            categoryText = v.findViewById(R.id.category_text);
            chevron = v.findViewById(R.id.expand_chevron);
            expandedPanel = v.findViewById(R.id.expanded_panel);
            pathRow = v.findViewById(R.id.path_row);
            pathText = v.findViewById(R.id.path_text);
            copyPageRow = v.findViewById(R.id.copy_page_row);
            deleteRow = v.findViewById(R.id.delete_row);
            btnCopyLink = v.findViewById(R.id.btn_copy_link);
            btnOpenUlaa = v.findViewById(R.id.btn_open_ulaa);
        }
    }
}
