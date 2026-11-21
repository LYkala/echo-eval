package com.example.devicesdkdemo;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** QA 聊天区渲染：纯净版气泡 / 完整日志。 */
final class QaChatRenderer {

    static final class ChatLine {
        final String timestamp;
        final String sender;
        final String body;

        ChatLine(String timestamp, String sender, String body) {
            this.timestamp = timestamp;
            this.sender = sender;
            this.body = body != null ? body : "";
        }
    }

    private QaChatRenderer() {
    }

    static boolean isPureModeEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(ConfigActivity.PREFS_NAME, Context.MODE_PRIVATE);
        return ConfigActivity.isPureModeEnabled(prefs);
    }

    static boolean shouldCompactIngest(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(ConfigActivity.PREFS_NAME, Context.MODE_PRIVATE);
        return ConfigActivity.isPureModeEnabled(prefs) || ConfigActivity.isFocusModeEnabled(prefs);
    }

    static boolean shouldShowInPureMode(ChatLine line) {
        if (line == null) {
            return false;
        }
        if ("我".equals(line.sender)) {
            return isPureModeUserMessage(line.body);
        }
        if ("服务端".equals(line.sender)) {
            return true;
        }
        if ("系统".equals(line.sender)) {
            return shouldShowSystemMessageInPureMode(line.body);
        }
        return false;
    }

    /** 纯净版系统灰字：停止类 + 会话类提示。 */
    static boolean shouldShowSystemMessageInPureMode(String body) {
        return isStopRelatedSystemMessage(body) || isSessionRelatedSystemMessage(body);
    }

    static boolean isSessionRelatedSystemMessage(String body) {
        if (body == null || body.isEmpty()) {
            return false;
        }
        return body.contains("创建成功")
                || body.contains("已切换到会话")
                || body.contains("恢复已有会话");
    }

    /** 纯净版「我」：仅用户输入的文字，不含 GUI/弹窗自动回包。 */
    static boolean isPureModeUserMessage(String body) {
        if (body == null || body.isEmpty()) {
            return false;
        }
        if (body.startsWith("[GUI响应]") || body.startsWith("[弹窗响应]")) {
            return false;
        }
        return true;
    }

    static boolean isStopRelatedSystemMessage(String body) {
        if (body == null || body.isEmpty()) {
            return false;
        }
        return body.contains("停止任务")
                || body.contains("算力主机")
                || body.contains("任务已停止")
                || body.contains("已向算力主机发送停止");
    }

    static String normalizeServerBody(String body) {
        if (body == null) {
            return "";
        }
        if (body.startsWith("text=")) {
            return body.substring(5);
        }
        return body;
    }

    static void render(
            Context context,
            LinearLayout container,
            ScrollView scrollView,
            List<ChatLine> lines,
            String streamingPreview
    ) {
        if (container == null) {
            return;
        }
        container.removeAllViews();
        boolean pure = isPureModeEnabled(context);
        LayoutInflater inflater = LayoutInflater.from(context);

        if (lines != null) {
            for (ChatLine line : lines) {
                if (pure && !shouldShowInPureMode(line)) {
                    continue;
                }
                container.addView(inflateLine(context, inflater, container, line, pure));
            }
        }

        if (pure && streamingPreview != null && !streamingPreview.isEmpty()) {
            View row = inflater.inflate(R.layout.item_qa_chat_server, container, false);
            TextView bubble = row.findViewById(R.id.tv_bubble);
            bubble.setText(streamingPreview);
            bindLongPressCopy(context, bubble, streamingPreview);
            container.addView(row);
        }

        if (scrollView != null) {
            scrollToBottom(scrollView);
        }
    }

    static void scrollToBottom(ScrollView scrollView) {
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
        scrollView.postDelayed(() -> scrollView.fullScroll(View.FOCUS_DOWN), 80);
    }

    private static View inflateLine(
            Context context,
            LayoutInflater inflater,
            ViewGroup parent,
            ChatLine line,
            boolean pure
    ) {
        if (!pure) {
            View row = inflater.inflate(R.layout.item_qa_chat_raw, parent, false);
            TextView tv = row.findViewById(R.id.tv_raw);
            String display = "[" + line.timestamp + "] [" + line.sender + "] " + line.body;
            tv.setText(display);
            bindLongPressCopy(context, row, display);
            return row;
        }
        if ("我".equals(line.sender)) {
            View row = inflater.inflate(R.layout.item_qa_chat_user, parent, false);
            TextView bubble = row.findViewById(R.id.tv_bubble);
            bubble.setText(line.body);
            bindLongPressCopy(context, bubble, line.body);
            return row;
        }
        if ("服务端".equals(line.sender)) {
            View row = inflater.inflate(R.layout.item_qa_chat_server, parent, false);
            TextView bubble = row.findViewById(R.id.tv_bubble);
            String display = normalizeServerBody(line.body);
            bubble.setText(display);
            bindLongPressCopy(context, bubble, display);
            return row;
        }
        View row = inflater.inflate(R.layout.item_qa_chat_system, parent, false);
        TextView tv = row.findViewById(R.id.tv_system);
        tv.setText(line.body);
        bindLongPressCopy(context, tv, line.body);
        return row;
    }

    private static PopupWindow activeCopyPopup;

    private static void bindLongPressCopy(Context context, View anchor, String copyText) {
        if (anchor == null || copyText == null || copyText.isEmpty()) {
            return;
        }
        anchor.setOnLongClickListener(v -> {
            showCopyPopup(context, v, copyText);
            return true;
        });
    }

    private static void showCopyPopup(Context context, View anchor, String copyText) {
        if (activeCopyPopup != null && activeCopyPopup.isShowing()) {
            activeCopyPopup.dismiss();
        }
        View popupView = LayoutInflater.from(context).inflate(R.layout.popup_qa_chat_copy, null);
        PopupWindow popup = new PopupWindow(
                popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
        );
        popup.setElevation(12f);
        popupView.findViewById(R.id.btn_copy).setOnClickListener(v -> {
            copyToClipboard(context, copyText);
            popup.dismiss();
        });
        popup.setOnDismissListener(() -> {
            if (activeCopyPopup == popup) {
                activeCopyPopup = null;
            }
        });
        activeCopyPopup = popup;

        popupView.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );
        int popupWidth = popupView.getMeasuredWidth();
        int xOff = Math.max(0, (anchor.getWidth() - popupWidth) / 2);
        // 气泡下方居中；y 负值可略贴近长按位置
        popup.showAsDropDown(anchor, xOff, 4, Gravity.START);
    }

    private static void copyToClipboard(Context context, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("", text));
            Toast.makeText(context, R.string.qa_chat_copied, Toast.LENGTH_SHORT).show();
        }
    }

    static String collectPlainText(List<ChatLine> lines) {
        if (lines == null || lines.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ChatLine line : lines) {
            sb.append("[").append(line.timestamp).append("] [")
                    .append(line.sender).append("] ")
                    .append(line.body).append("\n\n");
        }
        return sb.toString();
    }
}
