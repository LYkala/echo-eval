/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.androidfunc.permission;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;

/**
 * 权限请求Activity
 *
 * <p>透明Activity，用于处理需要UI交互的权限请求。</p>
 *
 * <p>该Activity以透明主题启动，完成权限请求后自动关闭。</p>
 *
 * <h3>支持的请求类型</h3>
 * <ol>
 *   <li>{@link #REQUEST_TYPE_ACTIVITY} - Activity结果请求（如MediaProjection）</li>
 *   <li>{@link #REQUEST_TYPE_LOCATION} - 定位权限请求</li>
 * </ol>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * PermissionRequestActivity.start(context, PermissionRequestActivity.REQUEST_TYPE_ACTIVITY,
 *     new PermissionRequestActivity.PermissionCallback() {
 *         @Override
 *         public void onActivityResult(int resultCode, Intent data) {
 *             // 处理Activity返回结果
 *         }
 *         @Override
 *         public Intent getRequestIntent() {
 *             // 返回需要启动的Intent（如MediaProjection授权Intent）
 *             return mediaProjectionManager.createScreenCaptureIntent();
 *         }
 *         @Override
 *         public void onRequestPermissionsResult(boolean isSuccess) {
 *             // 处理权限请求结果
 *         }
 *     });
 * }</pre>
 *
 * <h3>注意事项</h3>
 * <ol>
 *   <li>该Activity需要在AndroidManifest.xml中注册</li>
 *   <li>使用透明主题，用户无感知</li>
 *   <li>权限请求完成后Activity会自动关闭</li>
 * </ol>
 *
 * @author l00504561
 * @since 2026-04-10
 */
public class PermissionRequestActivity extends Activity {
    private static final Logger LOGGER = LoggerFactory.getLogger("PermissionRequestActivity");
    private static final String TAG = "PermissionRequestActivity";
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static PermissionCallback callback;
    private static int requestType;

    /**
     * 权限请求类型：Activity结果请求
     *
     * <p>用于需要启动另一个Activity并获取结果的场景，如MediaProjection授权。</p>
     * <p>使用此类型时，需要实现{@link PermissionCallback#getRequestIntent()}方法返回要启动的Intent。</p>
     */
    public static final int REQUEST_TYPE_ACTIVITY = 1;

    /**
     * 权限请求类型：定位权限请求
     *
     * <p>用于请求定位权限的场景。</p>
     * <p>会同时请求ACCESS_FINE_LOCATION和ACCESS_COARSE_LOCATION权限。</p>
     */
    public static final int REQUEST_TYPE_LOCATION = 2;

    /**
     * 权限请求回调接口
     *
     * <p>用于接收权限请求结果的回调。</p>
     */
    public interface PermissionCallback {
        /**
         * Activity结果回调
         *
         * <p>当请求类型为{@link #REQUEST_TYPE_ACTIVITY}时，在目标Activity返回结果后调用。</p>
         *
         * @param resultCode 结果码，如{@link Activity#RESULT_OK}或{@link Activity#RESULT_CANCELED}
         * @param data 返回的Intent数据，可能为null
         */
        void onActivityResult(int resultCode, Intent data);

        /**
         * 获取请求Intent
         *
         * <p>当请求类型为{@link #REQUEST_TYPE_ACTIVITY}时，需要返回要启动的Intent。</p>
         *
         * @return 要启动的Activity的Intent，返回null将导致请求失败
         */
        Intent getRequestIntent();

        /**
         * 权限请求结果回调
         *
         * <p>当请求类型为{@link #REQUEST_TYPE_LOCATION}时，在权限请求结果返回后调用。</p>
         *
         * @param isSuccess true表示权限已授予，false表示权限被拒绝
         */
        void onRequestPermissionsResult(boolean isSuccess);
    }

    /**
     * 启动权限请求
     *
     * <p>以透明方式启动Activity进行权限请求。</p>
     *
     * @param context 上下文，用于启动Activity
     * @param type 请求类型，取值为{@link #REQUEST_TYPE_ACTIVITY}或{@link #REQUEST_TYPE_LOCATION}
     * @param cb 权限请求回调，不能为null
     */
    public static void start(Context context, int type, PermissionCallback cb) {
        PermissionRequestActivity.callback = cb;
        PermissionRequestActivity.requestType = type;
        Intent intent = new Intent(context, PermissionRequestActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LOGGER.info("onCreate enter");
        super.onCreate(savedInstanceState);
        if (requestType == REQUEST_TYPE_ACTIVITY) {
            requestActivityPermission();
            return;
        }
        if (requestType == REQUEST_TYPE_LOCATION) {
            String[] permissions = new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            };
            requestPermissions(permissions, PERMISSION_REQUEST_CODE);
            return;
        }
        finish();
    }

    /**
     * 请求Activity结果权限
     *
     * <p>通过{@link PermissionCallback#getRequestIntent()}获取Intent并启动Activity。</p>
     */
    private void requestActivityPermission() {
        if (callback == null) {
            LOGGER.error("onCreate callback is null");
            finish();
            return;
        }
        Intent requestIntent = callback.getRequestIntent();
        if (requestIntent == null) {
            LOGGER.error("onCreate requestIntent is null");
            finish();
            return;
        }
        startActivityForResult(requestIntent, PERMISSION_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        LOGGER.info("onActivityResult, requestCode={}, resultCode={}", requestCode, resultCode);
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PERMISSION_REQUEST_CODE) {
            LOGGER.warn("onActivityResult unknown request code: {}", requestCode);
            finish();
            return;
        }
        if (callback != null) {
            callback.onActivityResult(resultCode, data);
        }
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean granted = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (!granted) {
                LOGGER.warn("permission denied, type: {}", requestType);
            }
            if (callback != null) {
                callback.onRequestPermissionsResult(granted);
            }
        }
        finish();
    }
}
