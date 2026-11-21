package com.example.appforfunction;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * 列表界面
 */
public class ListActivity extends AppCompatActivity {

    private RecyclerView rvList;
    private ListItemAdapter adapter;
    private List<ListItem> items = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);

        rvList = findViewById(R.id.rvList);

        // 创建测试数据
        for (int i = 1; i <= 20; i++) {
            items.add(new ListItem("第" + i + "项", "这是第" + i + "个列表项的描述内容"));
        }

        adapter = new ListItemAdapter(items);
        rvList.setLayoutManager(new LinearLayoutManager(this));
        rvList.setAdapter(adapter);
    }

    static class ListItem {
        String title;
        String description;

        ListItem(String title, String description) {
            this.title = title;
            this.description = description;
        }
    }

    static class ListItemAdapter extends RecyclerView.Adapter<ListItemAdapter.ViewHolder> {

        private List<ListItem> items;

        ListItemAdapter(List<ListItem> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_list, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ListItem item = items.get(position);
            holder.tvTitle.setText(item.title);
            holder.tvDesc.setText(item.description);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvTitle;
            TextView tvDesc;

            ViewHolder(View itemView) {
                super(itemView);
                tvTitle = itemView.findViewById(R.id.tvTitle);
                tvDesc = itemView.findViewById(R.id.tvDesc);
            }
        }
    }
}