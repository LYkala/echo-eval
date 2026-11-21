package com.example.testapp.fragment;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.testapp.R;
import com.example.testapp.util.Logger;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 截图列表Fragment
 */
public class ScreenshotsFragment extends Fragment {

    private RecyclerView recyclerView;
    private TextView tvEmpty;
    private ScreenshotAdapter adapter;
    private List<ScreenshotItem> screenshotItems = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_file_list, container, false);

        recyclerView = view.findViewById(R.id.recyclerView);
        tvEmpty = view.findViewById(R.id.tvEmpty);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        adapter = new ScreenshotAdapter(screenshotItems, this::onScreenshotClick);
        recyclerView.setAdapter(adapter);

        loadScreenshots();

        return view;
    }

    private void loadScreenshots() {
        screenshotItems.clear();

        // 主要从应用私有目录加载截图
        File privateScreenshotsDir = new File(
                requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                "screenshots"
        );

        Logger.safeDebug("ScreenshotsFragment", "截图目录: " + privateScreenshotsDir.getAbsolutePath());
        loadScreenshotsFromDir(privateScreenshotsDir);

        // 从公共下载目录加载
        File publicScreenshotsDir = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "TestApp/Screenshots"
        );
        loadScreenshotsFromDir(publicScreenshotsDir);

        // 更新UI
        if (screenshotItems.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            if (tvEmpty != null) {
                tvEmpty.setVisibility(View.VISIBLE);
                tvEmpty.setText("暂无截图\n测试完成后截图将保存在应用私有目录");
            }
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            if (tvEmpty != null) {
                tvEmpty.setVisibility(View.GONE);
            }
        }

        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void loadScreenshotsFromDir(File dir) {
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }

        File[] testDirs = dir.listFiles(File::isDirectory);
        if (testDirs != null) {
            for (File testDir : testDirs) {
                File[] images = testDir.listFiles((d, name) ->
                        name.toLowerCase().endsWith(".png") || name.toLowerCase().endsWith(".jpg"));

                if (images != null && images.length > 0) {
                    for (File image : images) {
                        ScreenshotItem item = new ScreenshotItem();
                        item.testId = testDir.getName();
                        item.fileName = image.getName();
                        item.path = image.getAbsolutePath();
                        item.lastModified = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                                .format(new Date(image.lastModified()));
                        item.fileSize = formatFileSize(image.length());
                        screenshotItems.add(item);
                    }
                }
            }
        }
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0);
        return String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024));
    }

    private void onScreenshotClick(int position) {
        ScreenshotItem item = screenshotItems.get(position);
        File file = new File(item.path);

        if (file.exists()) {
            try {
                // 使用FileProvider获取URI
                Uri uri = FileProvider.getUriForFile(
                        requireContext(),
                        requireContext().getPackageName() + ".fileprovider",
                        file
                );

                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(uri, "image/png");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                startActivity(Intent.createChooser(intent, "查看截图"));
            } catch (Exception e) {
                Logger.safeError("ScreenshotsFragment", "无法打开截图", e);
                Toast.makeText(requireContext(), "无法打开截图: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(requireContext(), "截图文件不存在", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        loadScreenshots();
    }

    static class ScreenshotItem {
        String testId;
        String fileName;
        String path;
        String lastModified;
        String fileSize;
    }

    static class ScreenshotAdapter extends RecyclerView.Adapter<ScreenshotAdapter.ViewHolder> {

        private List<ScreenshotItem> items;
        private OnScreenshotClickListener listener;

        interface OnScreenshotClickListener {
            void onScreenshotClick(int position);
        }

        ScreenshotAdapter(List<ScreenshotItem> items, OnScreenshotClickListener listener) {
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
            ScreenshotItem item = items.get(position);
            holder.text1.setText(item.fileName);
            holder.text2.setText(item.testId + " | " + item.lastModified + " | " + item.fileSize);

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onScreenshotClick(position);
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