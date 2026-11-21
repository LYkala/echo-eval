package com.example.appforfunction;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * 聊天界面
 */
public class ChatActivity extends AppCompatActivity implements View.OnClickListener {

    private RecyclerView rvMessages;
    private EditText etMessage;
    private Button btnSend;
    private MessageAdapter adapter;
    private List<String> messages = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        rvMessages = findViewById(R.id.rvMessages);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);

        adapter = new MessageAdapter(messages);
        rvMessages.setLayoutManager(new LinearLayoutManager(this));
        rvMessages.setAdapter(adapter);

        btnSend.setOnClickListener(this);

        // 添加欢迎消息
        messages.add("欢迎来到聊天室！");
        adapter.notifyItemInserted(messages.size() - 1);
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.btnSend) {
            sendMessage();
        }
    }

    private void sendMessage() {
        String message = etMessage.getText().toString().trim();
        if (message.isEmpty()) {
            return;
        }

        messages.add("我: " + message);
        adapter.notifyItemInserted(messages.size() - 1);
        rvMessages.smoothScrollToPosition(messages.size() - 1);
        etMessage.setText("");

        // 模拟自动回复
        rvMessages.postDelayed(() -> {
            messages.add("系统: 已收到您的消息 \"" + message + "\"");
            adapter.notifyItemInserted(messages.size() - 1);
            rvMessages.smoothScrollToPosition(messages.size() - 1);
        }, 500);
    }

    static class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.ViewHolder> {

        private List<String> messages;

        MessageAdapter(List<String> messages) {
            this.messages = messages;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView textView = new TextView(parent.getContext());
            textView.setPadding(20, 15, 20, 15);
            textView.setTextSize(16);
            return new ViewHolder(textView);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.textView.setText(messages.get(position));
        }

        @Override
        public int getItemCount() {
            return messages.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView textView;

            ViewHolder(TextView itemView) {
                super(itemView);
                textView = itemView;
            }
        }
    }
}