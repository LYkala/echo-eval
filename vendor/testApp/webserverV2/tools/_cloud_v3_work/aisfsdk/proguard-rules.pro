# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# ==================== Netty 依赖保护 ====================
# 防止 minifyEnabled=true 时移除 Netty 类
-keep class io.netty.** { *; }
-dontwarn io.netty.**
-keepclassmembers class io.netty.** { *; }

# ==================== 项目代码保护 ====================

# API 入口类（App直接调用）
-keep public class com.huawei.aisfsdkv2.api.** { *; }

# API 实现类
-keep public class com.huawei.aisfsdkv2.apiimpl.** { *; }

# ==================== Android 功能模块 ====================
# 无障碍服务实现类（继承 AccessibilityService，必须完整保留）
-keep public class com.huawei.aisfsdkv2.component.androidfunc.accessibility.impl.** { *; }

# 应用管理实现类（launchApp 调用链）
-keep public class com.huawei.aisfsdkv2.component.androidfunc.app.** { *; }

# 截图服务（继承 Service，必须完整保留）
-keep public class com.huawei.aisfsdkv2.component.androidfunc.screenshot.** { *; }

# AndroidFuncManager 实现类
-keep public class com.huawei.aisfsdkv2.component.androidfunc.impl.** { *; }

# 其他 component 类（logger, netty, threadpool 等）
-keep public class com.huawei.aisfsdkv2.component.** { public *; }

# ==================== 消息处理链 ====================
# 消息处理器（GUI任务分发）
-keep public class com.huawei.aisfsdkv2.message.** { *; }

# GUI 消息处理器子类
-keep public class com.huawei.aisfsdkv2.message.handler.** { *; }
-keep public class com.huawei.aisfsdkv2.message.handler.impl.** { *; }
-keep public class com.huawei.aisfsdkv2.message.handler.impl.gui.** { *; }

# GUI 消息模型类（JSON 解析必须保留字段名）
-keep public class com.huawei.aisfsdkv2.message.model.** { *; }
-keep public class com.huawei.aisfsdkv2.message.model.gui.** { *; }

# ==================== 模仿任务模块 ====================
-keep public class com.huawei.aisfsdkv2.mimictask.** { *; }

# ==================== 其他关键模块 ====================
# 登录认证
-keep public class com.huawei.aisfsdkv2.login.** { *; }

# 定时任务
-keep public class com.huawei.aisfsdkv2.timertask.** { *; }

# 会话管理
-keep public class com.huawei.aisfsdkv2.session.** { *; }

# 工具类
-keep public class com.huawei.aisfsdkv2.util.** { *; }

# ==================== JSON 序列化保护 ====================
# 保留所有 org.json 相关字段名
-keepclassmembers class * {
    @org.json.* *;
}
-keepattributes *Annotation*

# ==================== 保留调试信息 ====================
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile