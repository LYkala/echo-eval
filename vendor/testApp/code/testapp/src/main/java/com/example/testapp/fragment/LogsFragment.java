package com.example.testapp.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.testapp.LogViewerActivity;
import com.example.testapp.R;
import com.example.testapp.util.Logger;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 日志列表Fragment
 */
public class LogsFragment extends Fragment {

    private RecyclerView recyclerView;
    private LogAdapter adapter;
    private List<LogItem> logItems = new ArrayList<>();
    private Logger logger;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_file_list, container, false);

        logger = Logger.getInstance(requireContext());
        recyclerView = view.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        adapter = new LogAdapter(logItems, this::onLogClick);
        recyclerView.setAdapter(adapter);

        loadLogs();

        return view;
    }

    private void loadLogs() {
        logItems.clear();
        File logDir = logger.getLogDir();
        if (logDir.exists() && logDir.isDirectory()) {
            File[] files = logDir.listFiles((dir, name) -> name.endsWith(".log"));
            if (files != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                for (File file : files) {
                    LogItem item = new LogItem();
                    item.fileName = file.getName();
                    item.testId = file.getName().replace(".log", "");
                    item.lastModified = sdf.format(new Date(file.lastModified()));
                    logItems.add(item);
                }
            }
        }

        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void onLogClick(int position) {
        LogItem item = logItems.get(position);
        Intent intent = new Intent(requireContext(), LogViewerActivity.class);
        intent.putExtra("testId", item.testId);
        startActivity(intent);
    }

    static class LogItem {
        String fileName;
        String testId;
        String lastModified;
    }

    static class LogAdapter extends RecyclerView.Adapter<LogAdapter.ViewHolder> {

        private List<LogItem> items;
        private OnLogClickListener listener;

        interface OnLogClickListener {
            void onLogClick(int position);
        }

        LogAdapter(List<LogItem> items, OnLogClickListener listener) {
            this.items = items;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_2, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            LogItem item = items.get(position);
            holder.text1.setText(item.testId);
            holder.text2.setText(item.lastModified);

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onLogClick(position);
                }
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView text1;
            TextView text2;

            ViewHolder(View itemView) {
                super(itemView);
                text1 = itemView.findViewById(android.R.id.text1);
                text2 = itemView.findViewById(android.R.id.text2);
            }
        }
    }
}