#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Reset cloud MobileAPK from v25 orig, then graft TestReply only."""
from __future__ import annotations

import re
import shutil
from pathlib import Path

REPO = Path(r"e:\TestAPPGUI\testApp")
ORIG = REPO / "webserverV2/tools/_orig_mobile_work/mobile/MobileAPK"
CLOUD = REPO / "webserverV2/tools/_cloud_usable_work/MobileAPK"
CLOUD_ROOT = REPO / "webserverV2/tools/_cloud_usable_work"
GUI_SRC = CLOUD / "app/src/main/java/com/example/devicesdkdemo/GuiScreenCaptureHandler.java"
GUI_FALLBACK = CLOUD_ROOT / "_bak_GuiScreenCaptureHandler.java"
FILE_PATHS_FALLBACK = CLOUD / "app/src/main/res/xml/file_paths.xml"


def copy_tree_app():
    # Preserve gradle wrapper / settings; replace app module sources from v25
    src_app = ORIG / "app"
    dst_app = CLOUD / "app"
    # wipe java/res/manifest under app/src
    for sub in ["src/main/java", "src/main/res", "src/main/AndroidManifest.xml"]:
        p = dst_app / sub
        if p.is_dir():
            shutil.rmtree(p)
        elif p.is_file():
            p.unlink()
    # copy
    shutil.copytree(src_app / "src/main/java", dst_app / "src/main/java")
    shutil.copytree(src_app / "src/main/res", dst_app / "src/main/res")
    shutil.copy2(src_app / "src/main/AndroidManifest.xml", dst_app / "src/main/AndroidManifest.xml")
    # build.gradle from orig then patch
    shutil.copy2(src_app / "build.gradle", dst_app / "build.gradle")
    # keep debug.keystore if present in either
    for ks in [src_app / "debug.keystore", dst_app / "debug.keystore"]:
        if ks.exists():
            shutil.copy2(ks, dst_app / "debug.keystore")
            break


def patch_build_gradle():
    p = CLOUD / "app/build.gradle"
    text = p.read_text(encoding="utf-8")
    text = re.sub(r"versionCode\s+\d+", "versionCode 30", text)
    text = re.sub(
        r'versionName\s+"[^"]+"',
        'versionName "1.0-v25cloud-testreply-only"',
        text,
    )
    if "testreply-sdk" not in text:
        text = text.replace(
            "implementation(project(':aisfsdk')) {",
            "implementation project(':testreply-sdk')\n"
            "    implementation(project(':aisfsdk')) {",
        )
    p.write_text(text, encoding="utf-8")


def write_demo_app():
    (CLOUD / "app/src/main/java/com/example/devicesdkdemo/DemoApp.java").write_text(
        """package com.example.devicesdkdemo;

import android.app.Application;
import android.util.Log;

import com.example.testreply.TestReplyConfig;
import com.example.testreply.TestReplySdk;

/**
 * v25 DemoApp + TestReply 对接（仅协议，无无人值守等增强配置）。
 */
public class DemoApp extends Application {
    private static final String TAG = "DemoApp";

    @Override
    public void onCreate() {
        super.onCreate();
        CrashHandler.install(this);
        TestReplySdk.init(this, new TestReplyConfig.Builder().build());
        TestReplySdk.registerCaseControlListener(this, (taskId, caseId) -> {
            Log.i(TAG, "[TestReply] case_start taskId=" + taskId + " caseId=" + caseId);
            QAActivity.onAutomationCaseStart(caseId);
        });
        TestReplySdk.registerClarifyListener(this, (text, round) -> {
            Log.i(TAG, "[TestReply] clarify round=" + round);
            QAActivity.sendClarifyMessageFromAutomation(text);
        });
        TestReplySdk.registerForceEndTakeoverListener(this, () -> {
            Log.i(TAG, "[TestReply] force end takeover");
            QAActivity.forceEndTakeoverFromAutomation();
        });
        TestReplySdk.registerGuiScreenCaptureListener(this, (requestId, stepName) -> {
            Log.i(TAG, "[TestReply] gui capture requestId=" + requestId + " step=" + stepName);
            GuiScreenCaptureHandler.handleCaptureRequest(this, requestId, stepName);
        });
    }
}
""",
        encoding="utf-8",
    )


def ensure_gui_capture():
    dst = CLOUD / "app/src/main/java/com/example/devicesdkdemo/GuiScreenCaptureHandler.java"
    src = GUI_SRC if GUI_SRC.exists() else GUI_FALLBACK
    if not src.exists():
        # try current cloud before wipe — already wiped; use fallback
        alt = (
            REPO
            / "webserverV2/tools/_cloud_usable_work/_bak_GuiScreenCaptureHandler.java"
        )
        raise SystemExit(f"GuiScreenCaptureHandler missing: {src}")
    # If we already wiped, read from a temp we save first in main
    shutil.copy2(src, dst)


def patch_qa_activity():
    p = CLOUD / "app/src/main/java/com/example/devicesdkdemo/QAActivity.java"
    text = p.read_text(encoding="utf-8")

    if "import com.example.testreply.TestReplySdk;" not in text:
        text = text.replace(
            "import com.huawei.aisfsdkv2.api.AISFSDKClient;",
            "import com.example.testreply.TestReplyContract;\n"
            "import com.example.testreply.TestReplySdk;\n"
            "import com.huawei.aisfsdkv2.api.AISFSDKClient;",
        )

    # After DIALOG showDialog block — add onDialogReceived
    old_dialog = """                        if (data.getOutputType() == OutputTypeEnum.DIALOG && data.getDialogInfo() != null) {
                            ReplyMetrics.onFirstSignal(sid, "DIALOG",
                                    data.getDialogInfo().getTitle() != null
                                            ? data.getDialogInfo().getTitle() : "dialog");
                            if (activity != null && !activity.isFinishing()) {
                                activity.showDialog(data.getDialogInfo());
                            }
                        }"""
    new_dialog = """                        if (data.getOutputType() == OutputTypeEnum.DIALOG && data.getDialogInfo() != null) {
                            ReplyMetrics.onFirstSignal(sid, "DIALOG",
                                    data.getDialogInfo().getTitle() != null
                                            ? data.getDialogInfo().getTitle() : "dialog");
                            String dialogTitle = data.getDialogInfo().getTitle() != null
                                    ? data.getDialogInfo().getTitle() : "dialog";
                            Context replyCtx = activity != null ? activity : appContext;
                            if (replyCtx != null) {
                                TestReplySdk.onDialogReceived(replyCtx, sid, dialogTitle);
                            }
                            if (activity != null && !activity.isFinishing()) {
                                activity.showDialog(data.getDialogInfo());
                            }
                        }"""
    if old_dialog not in text:
        raise SystemExit("DIALOG block not found")
    text = text.replace(old_dialog, new_dialog, 1)

    # TASK_STATUS completed
    old_task = """                            if (data.getOutputType() == OutputTypeEnum.TASK_STATUS
                                    && data.getTaskInfo() != null
                                    && data.getTaskInfo().isTaskCompleted()) {
                                ReplyMetrics.onTaskCompleted(sid);
                            }"""
    new_task = """                            if (data.getOutputType() == OutputTypeEnum.TASK_STATUS
                                    && data.getTaskInfo() != null
                                    && data.getTaskInfo().isTaskCompleted()) {
                                ReplyMetrics.onTaskCompleted(sid);
                                Context replyCtx = activity != null ? activity : appContext;
                                if (replyCtx != null) {
                                    TestReplySdk.onTaskCompleted(
                                            replyCtx, sid, data.getTaskInfo().getTaskStatusCode());
                                }
                            }"""
    if old_task not in text:
        raise SystemExit("TASK_STATUS block not found")
    text = text.replace(old_task, new_task, 1)

    # focus TEXT chunk
    old_focus = """                            } else if (focusMode && data.getOutputType() == OutputTypeEnum.TEXT && data.getTextInfo() != null) {
                                // 聚焦模式：流式响应拼接，将多个TEXT chunk合并为一条显示
                                String textContent = data.getTextInfo().getText();
                                ReplyMetrics.onTextChunk(sid, textContent != null ? textContent : "");
"""
    new_focus = """                            } else if (focusMode && data.getOutputType() == OutputTypeEnum.TEXT && data.getTextInfo() != null) {
                                // 聚焦模式：流式响应拼接，将多个TEXT chunk合并为一条显示
                                String textContent = data.getTextInfo().getText();
                                ReplyMetrics.onTextChunk(sid, textContent != null ? textContent : "");
                                TestReplySdk.onReplyChunk(sid, textContent != null ? textContent : "");
"""
    if old_focus not in text:
        raise SystemExit("focus TEXT block not found")
    text = text.replace(old_focus, new_focus, 1)

    # non-focus TEXT
    old_nf = """                            } else if (!focusMode && data.getOutputType() == OutputTypeEnum.TEXT && data.getTextInfo() != null) {
                                // 非聚焦模式：逐条显示每个TEXT chunk
                                String textContent = data.getTextInfo().getText();
                                ReplyMetrics.onTextChunk(sid, textContent != null ? textContent : "");
                                appendMessageToSession(sid, "服务端", "text=" + textContent);
"""
    new_nf = """                            } else if (!focusMode && data.getOutputType() == OutputTypeEnum.TEXT && data.getTextInfo() != null) {
                                // 非聚焦模式：逐条显示每个TEXT chunk
                                String textContent = data.getTextInfo().getText();
                                ReplyMetrics.onTextChunk(sid, textContent != null ? textContent : "");
                                Context replyCtx = activity != null ? activity : appContext;
                                if (replyCtx != null) {
                                    TestReplySdk.publishTextReply(replyCtx, sid, textContent);
                                }
                                appendMessageToSession(sid, "服务端", "text=" + textContent);
"""
    if old_nf not in text:
        raise SystemExit("non-focus TEXT block not found")
    text = text.replace(old_nf, new_nf, 1)

    # sendMessage success
    old_send = """        if (!success) {
            appendMessage("错误", "发送失败，会话可能已断开");
        } else {
            ReplyMetrics.onUserSend(currentSessionId, message);
        }
    }"""
    new_send = """        if (!success) {
            appendMessage("错误", "发送失败，会话可能已断开");
        } else {
            ReplyMetrics.onUserSend(currentSessionId, message);
            TestReplySdk.onUserMessageSent(currentSessionId, message);
        }
    }"""
    if old_send not in text:
        raise SystemExit("sendMessage success block not found")
    text = text.replace(old_send, new_send, 1)

    # finalizeStreaming end
    old_fin = """        ReplyMetrics.finalizeStream(sid);
    }

    /**
     * 取 sessionId 前8位用于显示
     */
    private String shortId(String sid) {"""
    new_fin = """        ReplyMetrics.finalizeStream(sid);
        Context replyCtx = appContext;
        QAActivity act = currentActivityRef != null ? currentActivityRef.get() : null;
        if (act != null) {
            replyCtx = act;
        }
        if (replyCtx != null) {
            TestReplySdk.finalizeReply(replyCtx, sid, TestReplyContract.OUTPUT_TEXT);
            Log.i(TAG, "[TestReply] finalizeReply 已广播, session=" + sid
                    + ", len=" + (accumulated != null ? accumulated.length() : 0));
        }
    }

    /**
     * 取 sessionId 前8位用于显示
     */
    private String shortId(String sid) {"""
    if old_fin not in text:
        raise SystemExit("finalizeStreaming end not found")
    text = text.replace(old_fin, new_fin, 1)

    # Ensure appContext field exists — orig may use different name
    if "appContext" not in text:
        # inject static context
        text = text.replace(
            "private static WeakReference<QAActivity> currentActivityRef;",
            "private static WeakReference<QAActivity> currentActivityRef;\n"
            "    private static Context appContext;",
        )
        if "appContext = getApplicationContext()" not in text:
            text = text.replace(
                "currentActivityRef = new WeakReference<>(this);",
                "currentActivityRef = new WeakReference<>(this);\n"
                "        appContext = getApplicationContext();",
            )

    # Add automation helpers before showDialog
    helpers = '''
    /** TestApp 新用例开始：清流式缓冲。 */
    public static void onAutomationCaseStart(String caseId) {
        String sid = currentSessionId;
        if (sid != null) {
            streamingTextMap.remove(sid);
            streamingTimestampMap.remove(sid);
        }
        Log.i(TAG, "[TestReply] automation case start caseId=" + caseId);
    }

    /** TestApp 澄清发送。 */
    public static void sendClarifyMessageFromAutomation(String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        QAActivity activity = currentActivityRef != null ? currentActivityRef.get() : null;
        if (activity != null && !activity.isFinishing()) {
            activity.runOnUiThread(() -> activity.sendClarifyMessage(text));
            return;
        }
        if (currentSessionId == null || sessionApi == null) {
            Log.w(TAG, "澄清无法发送：会话未就绪");
            return;
        }
        String message = text.trim();
        SessionRequest request = new SessionRequest.Builder().userInputText(message).build();
        if (sessionApi.sendMessage(currentSessionId, request)) {
            TestReplySdk.sendClarify(currentSessionId, message);
        }
    }

    private void sendClarifyMessage(String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        if (currentSessionId == null || sessionApi == null) {
            appendMessage("错误", "会话未创建，无法发送澄清消息");
            return;
        }
        String message = text.trim();
        SessionRequest request = new SessionRequest.Builder().userInputText(message).build();
        if (sessionApi.sendMessage(currentSessionId, request)) {
            TestReplySdk.sendClarify(currentSessionId, message);
            appendMessage("我", "[澄清] " + message);
            if (etInput != null) {
                etInput.setText("");
            }
        } else {
            appendMessage("错误", "澄清消息发送失败");
        }
    }

    /** TestApp 强制结束 GUI 接管。 */
    public static void forceEndTakeoverFromAutomation() {
        QAActivity activity = currentActivityRef != null ? currentActivityRef.get() : null;
        if (activity != null && !activity.isFinishing()) {
            activity.runOnUiThread(() -> {
                Map<String, String> params = new HashMap<>();
                if (sessionApi != null && currentSessionId != null) {
                    sessionApi.sendMessage(currentSessionId, GuiTaskEnum.STOP_TAKEOVER, params);
                }
                activity.hideFloatingBall();
                Context ctx = activity.getApplicationContext();
                TestReplySdk.publishGuiTakeoverForceComplete(ctx, "testapp_force");
            });
            return;
        }
        if (currentSessionId != null && sessionApi != null) {
            Map<String, String> params = new HashMap<>();
            sessionApi.sendMessage(currentSessionId, GuiTaskEnum.STOP_TAKEOVER, params);
        }
        if (appContext != null) {
            TestReplySdk.publishGuiTakeoverForceComplete(appContext, "testapp_force_no_activity");
        }
    }

'''
    if "onAutomationCaseStart" not in text:
        marker = "    /**\n     * 显示弹窗\n     */\n    private void showDialog(DialogInfo dialogInfo) {"
        if marker not in text:
            raise SystemExit("showDialog marker not found for helpers")
        text = text.replace(marker, helpers + marker, 1)

    # hideFloatingBall may be private instance — forceEnd uses activity.hideFloatingBall OK

    p.write_text(text, encoding="utf-8")


def patch_manifest_fileprovider():
    """GuiScreenCapture may need FileProvider; add if missing."""
    manifest = CLOUD / "app/src/main/AndroidManifest.xml"
    text = manifest.read_text(encoding="utf-8")
    xml_dir = CLOUD / "app/src/main/res/xml"
    xml_dir.mkdir(parents=True, exist_ok=True)
    fp = xml_dir / "file_paths.xml"
    if not fp.exists():
        src = FILE_PATHS_FALLBACK
        if src.exists():
            shutil.copy2(src, fp)
        else:
            fp.write_text(
                """<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="cache" path="." />
    <external-cache-path name="ext_cache" path="." />
</paths>
""",
                encoding="utf-8",
            )
    if "fileprovider" not in text.lower():
        text = text.replace(
            "</application>",
            """        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>
    </application>""",
        )
        manifest.write_text(text, encoding="utf-8")


def write_cloud_txt():
    (CLOUD_ROOT / "CLOUD.txt").write_text(
        """DeviceSDK Demo — 云上基础工程（严格 v25 底座 + TestReply）
========================================================
Date: 2026-08-30
versionCode: 30
versionName: 1.0-v25cloud-testreply-only
APK: DeviceSDK-Demo-cloud.apk

底座（不得混入增强 Demo）
----
- SDK：_cloud_usable_work/aisfsdk（v25 assume-ready）
- Demo UI/配置：从 _orig_mobile_work（v25）整树拷贝
- 仅追加：testreply-sdk 挂钩（回复广播 / case_start / 澄清 / 强制结束接管 / GUI 截图请求）
- 不含：无人值守自动确认、WebServer 直报、增强 Config 开关

对接
----
AIS 回复：Demo TestReplySdk → TestApp → WebServer
截图：TestApp 自截 / MediaProjection 请求 Demo

重建
----
  powershell -File rebuild.ps1
""",
        encoding="utf-8",
    )


def main():
    if not ORIG.exists():
        raise SystemExit(f"missing v25 orig: {ORIG}")
    # preserve GuiScreenCapture before wipe
    gui_bak = CLOUD_ROOT / "_bak_GuiScreenCaptureHandler.java"
    if GUI_SRC.exists():
        shutil.copy2(GUI_SRC, gui_bak)
    elif GUI_FALLBACK.exists():
        shutil.copy2(GUI_FALLBACK, gui_bak)

    copy_tree_app()
    patch_build_gradle()
    write_demo_app()
    # restore gui handler
    dst_gui = CLOUD / "app/src/main/java/com/example/devicesdkdemo/GuiScreenCaptureHandler.java"
    if gui_bak.exists():
        shutil.copy2(gui_bak, dst_gui)
    else:
        ensure_gui_capture()
    patch_qa_activity()
    patch_manifest_fileprovider()
    write_cloud_txt()
    # sanity: no auto_confirm in config
    cfg = (CLOUD / "app/src/main/java/com/example/devicesdkdemo/ConfigActivity.java").read_text(
        encoding="utf-8"
    )
    if "AUTO_CONFIRM" in cfg or "无人值守" in cfg:
        raise SystemExit("ERROR: auto_confirm still in ConfigActivity")
    layout = (CLOUD / "app/src/main/res/layout/activity_config.xml").read_text(encoding="utf-8")
    if "auto_confirm" in layout or "无人值守" in layout:
        raise SystemExit("ERROR: auto_confirm still in layout")
    print("OK: cloud MobileAPK reset from v25 + TestReply only")


if __name__ == "__main__":
    main()
