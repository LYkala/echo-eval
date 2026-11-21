package com.example.testapp.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.testapp.R;

import java.util.List;

/**
 * 测试文件列表适配器
 */
public class TestFileAdapter extends RecyclerView.Adapter<TestFileAdapter.ViewHolder> {

    private List<String> files;
    private OnFileClickListener listener;
    private int selectedPosition = -1;

    public interface OnFileClickListener {
        void onFileClick(String fileName, int position);
    }

    public TestFileAdapter(List<String> files, OnFileClickListener listener) {
        this.files = files;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(android.R.layout.simple_list_item_1, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String fileName = files.get(position);
        holder.textView.setText(fileName);

        // 设置选中状态背景
        if (position == selectedPosition) {
            holder.itemView.setBackgroundColor(0xFFE0E0E0);
        } else {
            holder.itemView.setBackgroundColor(0xFFFFFFFF);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onFileClick(fileName, position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return files.size();
    }

    public void setSelectedPosition(int position) {
        int oldPosition = selectedPosition;
        selectedPosition = position;
        notifyItemChanged(oldPosition);
        notifyItemChanged(position);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textView;

        ViewHolder(View itemView) {
            super(itemView);
            textView = itemView.findViewById(android.R.id.text1);
            textView.setPadding(16, 16, 16, 16);
        }
    }
}