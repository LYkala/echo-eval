"""
TestApp Web Server V2
Version: 1.0.0
Port: 9527
"""

import os

from proxy_config import apply_proxy_from_config

_PROXY_STATE = apply_proxy_from_config()

import io
import json
import base64
import queue
import re
import shutil
import socket
import subprocess
import time
import zipfile
import uuid
import threading
import logging
import fnmatch
import urllib.parse
from collections import deque
from concurrent.futures import ThreadPoolExecutor, TimeoutError as FuturesTimeoutError
from datetime import datetime, timedelta
from pathlib import Path
from flask import Flask, Response, render_template, request, jsonify, send_from_directory, send_file, redirect, make_response
from flask_socketio import SocketIO, emit, join_room, leave_room
from flask_sock import Sock
from werkzeug.utils import secure_filename, safe_join

EMU_BRIDGE = None
_EMU_STATE = {
    'lock': __import__('threading').Lock(),
    'last_png': None,
    'last_jpeg': None,
    'frame_seq': 0,
    'frame_event': threading.Event(),
    'viewers': 0,
    'producer_started': False,
    'error': '',
    'connecting': False,
    'busy': False,
    'busy_msg': '',
    'progress_pct': 0,
    'progress_phase': '',
    'progress_pkg': '',
}

# Shared MJPEG: one screencap producer for all browser clients.
_EMU_STREAM_MAX_WIDTH = int(os.environ.get('EMU_STREAM_MAX_WIDTH', '540') or 540)
_EMU_STREAM_JPEG_QUALITY = int(os.environ.get('EMU_STREAM_JPEG_QUALITY', '55') or 55)
_EMU_STREAM_INTERVAL = float(os.environ.get('EMU_STREAM_INTERVAL', '0.12') or 0.12)
_EMU_STREAM_IDLE_SEC = float(os.environ.get('EMU_STREAM_IDLE_SEC', '180') or 180)

# Configure logging
LOG_DIR = os.path.join(os.path.dirname(__file__), 'logs')
os.makedirs(LOG_DIR, exist_ok=True)
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler(os.path.join(LOG_DIR, 'webserver.log')),
        logging.StreamHandler()
    ]
)
logger = logging.getLogger(__name__)

if _PROXY_STATE.get('enabled'):
    logger.info(
        'Proxy active: %s (mode=%s, config=%s)',
        _PROXY_STATE.get('proxy_url'),
        _PROXY_STATE.get('mode'),
        _PROXY_STATE.get('config_path'),
    )
elif _PROXY_STATE.get('mode') == 'auto':
    logger.info(
        'Proxy adaptive: disabled (%s, config=%s)',
        _PROXY_STATE.get('adaptive_reason') or 'direct_ok',
        _PROXY_STATE.get('config_path'),
    )
elif _PROXY_STATE.get('config_path'):
    logger.info('Proxy disabled by config: %s', _PROXY_STATE.get('config_path'))

def normalize_base_path(value):
    value = (value or '').strip()
    if not value or value == '/':
        return ''
    if not value.startswith('/'):
        value = '/' + value
    return value.rstrip('/')

def build_ui_path(path):
    path = (path or '').strip()
    if not path.startswith('/'):
        path = '/' + path
    base_path = app.config.get('BASE_PATH', '')
    return f'{base_path}{path}' if base_path else path

app = Flask(__name__)

# 网盘搜索过程（侧栏「网盘搜索」+ /api/netdisk/*，ADB 支持 USB 桥 :15037）
try:
    from netdisk_search import register_netdisk
    register_netdisk(app)
except Exception as _netdisk_ex:
    import logging as _logging
    _logging.getLogger(__name__).warning('netdisk_search register failed: %s', _netdisk_ex)

# 移动网盘搜索过程（侧栏「移动网盘搜索」+ /api/mcloud/*，独立数据集与结果）
try:
    from mcloud_search import register_mcloud
    register_mcloud(app)
except Exception as _mcloud_ex:
    import logging as _logging
    _logging.getLogger(__name__).warning('mcloud_search register failed: %s', _mcloud_ex)

# 远程 ADB（侧栏「远程 ADB」+ /api/remote-adb/prepare/* 电脑助手）
try:
    from remote_adb import register_remote_adb
    register_remote_adb(app)
except Exception as _remote_adb_ex:
    import logging as _logging
    _logging.getLogger(__name__).warning('remote_adb register failed: %s', _remote_adb_ex)

# Bump when results.html screenshot UI changes (shown in modal title for cache verification).
RESULTS_UI_VERSION = '20261105-gui-progress-segments'

@app.after_request
def disable_html_cache(response):
    """避免结果页模板/脚本被浏览器强缓存。"""
    ct = (response.content_type or '')
    if 'text/html' in ct:
        response.headers['Cache-Control'] = 'no-store, no-cache, must-revalidate, max-age=0'
        response.headers['Pragma'] = 'no-cache'
    return response

app.config['SECRET_KEY'] = 'testapp-secret-key-v2'
app.config['UPLOAD_FOLDER'] = os.path.join(os.path.dirname(__file__), 'testfiles')
app.config['EXCEL_DATASETS_FOLDER'] = os.path.join(os.path.dirname(__file__), 'excel_datasets')
app.config['TASKS_FOLDER'] = os.path.join(os.path.dirname(__file__), 'tasks')
EXCEL_DATASET_FILENAME = 'dataset.xlsx'
RUNTIME_TESTSET_PREFIX = '.runtime'
app.config['TESTRESULTS_FOLDER'] = os.path.join(os.path.dirname(__file__), 'testresults')
app.config['OUTPUT_FOLDER'] = os.path.join(os.path.dirname(__file__), '..', 'output')
app.config['DEVICESDKDEMO_APK_FOLDER'] = os.path.join(os.path.dirname(__file__), 'apks', 'devicesdkdemo')
app.config['DEVICESDKDEMO_APK_FILENAME'] = 'devicesdkdemo.apk'
app.config['SOFTWARE_APKS_ROOT'] = os.path.join(os.path.dirname(__file__), 'apks')
app.config['SHARED_FILES_FOLDER'] = os.path.join(os.path.dirname(__file__), 'shared_files')
_TESTAPP_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
_ANDROID_EMU_ROOT = os.path.join(_TESTAPP_ROOT, 'android-emulator')
app.config['BASE_PATH'] = normalize_base_path(os.getenv('GUI_EVAL_BASE_PATH', ''))

# Ensure directories exist
os.makedirs(app.config['UPLOAD_FOLDER'], exist_ok=True)
os.makedirs(app.config['EXCEL_DATASETS_FOLDER'], exist_ok=True)
os.makedirs(app.config['TASKS_FOLDER'], exist_ok=True)
os.makedirs(app.config['TESTRESULTS_FOLDER'], exist_ok=True)
os.makedirs(app.config['DEVICESDKDEMO_APK_FOLDER'], exist_ok=True)
os.makedirs(app.config['SHARED_FILES_FOLDER'], exist_ok=True)
os.makedirs(os.path.join(app.config['SOFTWARE_APKS_ROOT'], 'testapp'), exist_ok=True)
os.makedirs(os.path.join(app.config['SOFTWARE_APKS_ROOT'], 'aisf_voice'), exist_ok=True)
os.makedirs(os.path.join(app.config['SOFTWARE_APKS_ROOT'], 'amap'), exist_ok=True)
os.makedirs(os.path.join(app.config['SOFTWARE_APKS_ROOT'], 'iqiyi'), exist_ok=True)
os.makedirs(os.path.join(app.config['SOFTWARE_APKS_ROOT'], 'youku'), exist_ok=True)
os.makedirs(os.path.join(app.config['SOFTWARE_APKS_ROOT'], 'albummemory'), exist_ok=True)
os.makedirs(os.path.join(app.config['SOFTWARE_APKS_ROOT'], 'testapp-guangzhong'), exist_ok=True)
os.makedirs(os.path.join(app.config['SOFTWARE_APKS_ROOT'], 'devicesdkdemo-guangzhong'), exist_ok=True)

socketio = SocketIO(app, cors_allowed_origins="*", ping_timeout=60, ping_interval=25, async_mode='threading')
sock = Sock(app)

# ========== Data Structures ==========

# Online devices: {device_id: {name, info, last_seen, files}}
devices = {}
devices_lock = threading.Lock()

# 首页 WS 设备控制台日志（按 device_id）
DEVICE_CONSOLE_DIR = os.path.join(LOG_DIR, 'device_console')
os.makedirs(DEVICE_CONSOLE_DIR, exist_ok=True)
DEVICE_CONSOLE_MAX_LINES = 8000
device_console_logs = {}  # device_id -> {'seq': int, 'lines': deque}
device_console_lock = threading.Lock()

# Tasks: {task_id: {id, devices, testset, start_time, status, results}}
tasks = {}
tasks_lock = threading.Lock()
case_post_analysis_lock = threading.Lock()
# 后台评判并行度（默认 3）；可用环境变量 CASE_EVAL_PARALLEL 覆盖
CASE_EVAL_PARALLEL = max(1, int(os.environ.get('CASE_EVAL_PARALLEL', '3') or 3))
case_eval_semaphore = threading.Semaphore(CASE_EVAL_PARALLEL)
case_eval_progress = {}  # f"{task_id}/{case_id}" -> progress dict
case_eval_progress_lock = threading.Lock()
task_io_lock = threading.Lock()
_tasks_mem_cache = {'data': None, 'sig': None}
_tasks_cache_lock = threading.Lock()
EMU_LIST_CACHE_SEC = float(os.environ.get('EMU_LIST_CACHE_SEC', '8') or 8)
_emu_list_cache = {'at': 0.0, 'payload': None}
_emu_list_cache_lock = threading.Lock()
cancelled_tasks = set()
cancelled_tasks_lock = threading.Lock()
cancelled_cases = {}
cancelled_cases_lock = threading.Lock()
executing_tasks = set()
executing_tasks_lock = threading.Lock()
# 任务执行中追加的重跑用例队列：当前批次结束后再跑，不打断正在跑的用例
task_run_queues = {}
task_run_queues_lock = threading.Lock()
TERMINAL_CASE_STATUSES = ('completed', 'failed', 'cancelled', 'skipped', 'takeover')

# Test sets (folders): {testset_name: {create_time, files: []}}
testsets = {}
testsets_lock = threading.Lock()

# Excel datasets: {name: {name, create_time, has_file, size, last_modified, filename}}
excel_datasets = {}
excel_datasets_lock = threading.Lock()

# Server configuration
SERVER_HEARTBEAT_INTERVAL = 25  # Send ping every 25 seconds
DEVICE_TIMEOUT = 300  # Device timeout in seconds (5 minutes)
# GUI 跨 App（爱奇艺等）时 TestApp 可能被系统冻结网络，心跳停几分钟属常见；
# 忙设备用更长宽限，避免任务中途被踢导致截图/状态丢失。
DEVICE_BUSY_TIMEOUT = 1800  # 忙设备 30 分钟无心跳仍保留连接（不踢）
WS_RECEIVE_IDLE_TIMEOUT = 300  # WebSocket 无消息时的空闲等待（秒），与设备超时一致
SCHEDULED_TASK_POLL_SEC = 5  # 定时任务轮询间隔
CASE_TIMEOUT_BASE_BUFFER_SEC = 15  # 启动、最终截图、退出等固定缓冲
CASE_TIMEOUT_PER_STEP_BUFFER_SEC = 1  # 每步截图与调度开销
CASE_SCREENSHOT_DELAY_SEC = 0.3  # 与 Android SCREENSHOT_DELAY_MS 一致
CASE_CROSS_APP_STEP_OVERHEAD_SEC = 4  # 与 Android 跨 App 轮询每步 +4000ms 一致
CASE_MEDIA_DIALOG_BUFFER_SEC = 30  # DeviceSDK 录屏弹窗处理额外缓冲
CASE_COMPLETION_POLL_SEC = 1
# 旧代码在 wait 与下一用例之间有日志下载/VL；此处补足手机 finishTest→退出回 testApp（约5s）
CASE_BATCH_GAP_SEC = 3
LOG_DOWNLOAD_TIMEOUT_SEC = 60
VL_EVAL_TIMEOUT_SEC = 120
# 手机已上报 test_completed 后，不应长时间卡在「分析中」等图
RESULT_FINAL_WAIT_SEC = 20
RESULT_FINAL_STEP_STABLE_SEC = 2  # step 序号不再变化后视为上传完成
LOG_SERVICE_URL = os.getenv(
    'LOG_SERVICE_URL',
    'http://115.120.249.45:40096/upload_and_get_log',
)
ENABLE_CLOUD_LOG_DOWNLOAD = os.getenv('ENABLE_CLOUD_LOG_DOWNLOAD', 'false').lower() in (
    '1', 'true', 'yes',
)

def print_device_diagnostic_log(filepath):
    """把设备上传的测试日志摘要打印到 Web 服务控制台。"""
    try:
        with open(filepath, 'r', encoding='utf-8', errors='replace') as f:
            lines = f.readlines()
        shot_lines = [ln for ln in lines if 'ScreenshotDiag' in ln or '[截图]' in ln
                      or 'screenshot_diag' in ln or '步骤未保存' in ln]
        if shot_lines:
            logger.info('[设备截图诊断] %s', filepath)
            for ln in shot_lines[:80]:
                logger.info('[设备截图诊断] %s', ln.rstrip())
            if len(shot_lines) > 80:
                logger.info('[设备截图诊断] ... 另有 %d 行', len(shot_lines) - 80)
            return
        preview = ''.join(lines[:40]).rstrip()
        if preview:
            logger.info('[设备日志] %s\n%s', filepath, preview)
        else:
            logger.info('[设备日志] 日志已保存: %s', filepath)
    except Exception as e:
        logger.warning('读取设备日志用于打印失败: %s', e)

# ========== Heartbeat Functions ==========

def send_heartbeat_to_device(device_id):
    """Send ping to a device to keep connection alive."""
    with devices_lock:
        if device_id not in devices:
            return False
        ws = devices[device_id].get('ws')
    if not ws:
        return False
    try:
        ws.send(json.dumps({'type': 'ping'}))
        logger.debug(f"Sent ping to device {device_id}")
        return True
    except Exception as e:
        logger.warning(f"Failed to send heartbeat to {device_id}: {e}")
        return False

def check_device_health():
    """Check all devices and remove stale connections."""
    current_time = time.time()
    stale_devices = []
    busy_map = get_device_busy_map()

    with devices_lock:
        for device_id, device_data in devices.items():
            last_seen = device_data.get('last_seen', 0)
            time_since_seen = current_time - last_seen
            busy_info = busy_map.get(device_id)
            # 正在跑任务：不因短时无心跳踢掉（跨 App GUI 时手机侧网络常被冻结）
            if busy_info:
                if time_since_seen > DEVICE_TIMEOUT:
                    logger.warning(
                        f"Device {device_id} heartbeat quiet {time_since_seen:.0f}s "
                        f"but busy on {busy_info.get('task_id')} "
                        f"({busy_info.get('status')}), keep registered"
                    )
                if time_since_seen > DEVICE_BUSY_TIMEOUT:
                    stale_devices.append(device_id)
                    logger.warning(
                        f"Device {device_id} busy but silent {time_since_seen:.0f}s "
                        f"(>{DEVICE_BUSY_TIMEOUT}s), removing"
                    )
                continue

            if time_since_seen > DEVICE_TIMEOUT:
                stale_devices.append(device_id)
                logger.warning(f"Device {device_id} is stale (no activity for {time_since_seen:.0f}s), removing")

    # Remove stale devices
    for device_id in stale_devices:
        with devices_lock:
            if device_id in devices:
                del devices[device_id]
                logger.info(f"Removed stale device: {device_id}")

def server_heartbeat_thread():
    """Background thread to send heartbeat and check device health."""
    while True:
        time.sleep(SERVER_HEARTBEAT_INTERVAL)

        # Send ping to all devices
        with devices_lock:
            device_ids = list(devices.keys())

        for device_id in device_ids:
            send_heartbeat_to_device(device_id)

        # Check for stale devices
        check_device_health()

# Start heartbeat thread
heartbeat_thread = threading.Thread(target=server_heartbeat_thread, daemon=True)
heartbeat_thread.start()

@app.context_processor
def inject_gui_eval_context():
    try:
        _eval_port = int(os.getenv('GUI_EVAL_PORT', '9527') or '9527')
    except (TypeError, ValueError):
        _eval_port = 9527
    # 网盘/移动网盘搜索只在 9526 侧栏展示；9527 用远程 ADB
    return {
        'gui_base_path': app.config.get('BASE_PATH', ''),
        'gui_eval_port': _eval_port,
        'show_netdisk_nav': _eval_port == 9526,
    }

# ========== WebSocket Device Endpoint ==========

@sock.route('/ws/device')
def device_websocket(ws):
    """WebSocket connection for device communication (compatible with original webserver)."""
    device_id = None
    logger.info("New WebSocket connection established")

    try:
        while True:
            message = ws.receive(timeout=WS_RECEIVE_IDLE_TIMEOUT)

            if message is None:
                # 空闲超时：截图改走 HTTP 时 WS 可能较长时间无业务消息，不断开连接
                logger.debug(
                    f"WebSocket idle for {WS_RECEIVE_IDLE_TIMEOUT}s, "
                    f"device={device_id or 'unknown'}"
                )
                continue

            try:
                data = json.loads(message)
                msg_type = data.get('type', 'unknown')

                if msg_type == 'register':
                    # Device registration
                    requested_id = (data.get('deviceId') or '').strip() or (
                        'device_' + uuid.uuid4().hex[:8]
                    )
                    device_info = data.get('deviceInfo', {}) or {}
                    device_files = data.get('files', [])
                    avd_name = extract_avd_name(data.get('deviceName'), device_info)
                    device_name = avd_name or data.get('deviceName') or 'Unknown Device'

                    with devices_lock:
                        # 同 deviceId 重连直接覆盖。旧逻辑在旧 WS 未清理时会生成
                        # device_xxx__2，导致任务仍按原 ID 判定「全离线」。
                        device_id = requested_id
                        if (
                            device_id in devices
                            and devices[device_id].get('ws') is not ws
                        ):
                            logger.info(
                                'Device %s reconnected; replacing previous websocket',
                                device_id,
                            )
                        device_info = dict(device_info)
                        device_info['requestedDeviceId'] = requested_id
                        if avd_name:
                            device_info['avdName'] = avd_name
                        devices[device_id] = {
                            'id': device_id,
                            'name': device_name,
                            'info': device_info,
                            'files': device_files,
                            'last_seen': time.time(),
                            'ws': ws,
                        }

                    if avd_name:
                        remember_device_avd(device_id, avd_name)
                    logger.info(f"Device registered: {device_id} - {device_name}")

                    # Send registration confirmation
                    ws.send(json.dumps({
                        'type': 'registered',
                        'success': True,
                        'deviceId': device_id,
                        'message': f'Device {device_id} registered successfully'
                    }))

                elif msg_type == 'heartbeat':
                    # 连接级 device_id 优先，避免克隆机撞号后仍用客户端原始 ID
                    active_id = device_id or data.get('deviceId')
                    files = data.get('files', [])
                    with devices_lock:
                        if active_id and active_id in devices:
                            devices[active_id]['last_seen'] = time.time()
                            devices[active_id]['files'] = files

                    ws.send(json.dumps({'type': 'heartbeat_ack'}))

                elif msg_type == 'pong':
                    if device_id:
                        with devices_lock:
                            if device_id in devices:
                                devices[device_id]['last_seen'] = time.time()
                    logger.debug(f"Pong received from device {device_id}")

                elif msg_type == 'file_list':
                    active_id = device_id or data.get('deviceId')
                    files = data.get('files', [])
                    with devices_lock:
                        if active_id and active_id in devices:
                            devices[active_id]['files'] = files

                elif msg_type == 'file_deleted':
                    # File deletion confirmation
                    filename = data.get('filename')
                    success = data.get('success', False)
                    logger.info(f"File {filename} deleted: {success}")

                elif msg_type == 'screenshot_upload':
                    # 优先 testId（拍摄/入队时的用例），避免延迟上传时 caseId 已被切到下一用例
                    case_id = data.get('testId') or data.get('caseId')
                    task_id = data.get('taskId')
                    filename = data.get('filename')
                    img_data = data.get('data', '')
                    upload_device_id = device_id or data.get('deviceId')

                    if not task_id:
                        task_id, fallback_case = get_device_current_task(upload_device_id)
                        if not case_id:
                            case_id = fallback_case

                    if img_data and task_id and case_id:
                        if filename and not _is_gui_poll_filename(filename):
                            logger.info(
                                f"跳过非 gui_poll 截图(WebSocket): task={task_id} "
                                f"case={case_id} file={filename}"
                            )
                        elif should_reject_task_upload(task_id):
                            logger.info(
                                f"忽略已停止任务的截图(WebSocket): task={task_id} "
                                f"case={case_id} file={filename}"
                            )
                        else:
                            try:
                                import base64
                                img_bytes = base64.b64decode(img_data.split(',')[1])
                                result_path = os.path.join(
                                    app.config['TESTRESULTS_FOLDER'], task_id, case_id
                                )
                                os.makedirs(result_path, exist_ok=True)
                                filepath = os.path.join(result_path, filename)
                                with open(filepath, 'wb') as f:
                                    f.write(img_bytes)
                                logger.info(f"Screenshot saved: {filepath}")
                                if filename and _is_result_final_filename(filename):
                                    logger.info(f"result_final 已保存: {filepath}")
                            except Exception as e:
                                logger.error(f"Error saving screenshot: {e}")
                    elif img_data:
                        logger.warning(
                            f"截图未保存(缺少 taskId/caseId): filename={filename}, "
                            f"taskId={task_id}, caseId={case_id}, device={upload_device_id}"
                        )

                elif msg_type == 'ws_shot_upload':
                    upload_device_id = device_id or data.get('deviceId')
                    _ws_shot_handle_upload(upload_device_id, data)

                elif msg_type == 'ws_shot_error':
                    upload_device_id = device_id or data.get('deviceId')
                    _ws_shot_handle_error(upload_device_id, data)

                elif msg_type == 'step_progress':
                    task_id = data.get('taskId')
                    case_id = data.get('caseId') or data.get('testId')
                    device_ref = device_id or data.get('deviceId')
                    if not task_id:
                        task_id, fallback_case = get_device_current_task(device_ref)
                        if not case_id:
                            case_id = fallback_case
                    step_index = data.get('stepIndex')
                    step_total = data.get('stepTotal')
                    try:
                        step_index = int(step_index) if step_index is not None else None
                    except (TypeError, ValueError):
                        step_index = None
                    try:
                        step_total = int(step_total) if step_total is not None else None
                    except (TypeError, ValueError):
                        step_total = None
                    if task_id and case_id and step_index is not None and step_total:
                        step_id = data.get('stepId')
                        action = data.get('action')
                        if action in ('finishing', 'finish'):
                            logger.info(
                                f"step_progress task={task_id} case={case_id} "
                                f"step={step_index}/{step_total} action={action} "
                                f"stepId={step_id or '-'}"
                            )
                        update_case_step_progress(
                            task_id,
                            case_id,
                            step_index,
                            step_total,
                            step_id=step_id,
                            action=action,
                        )

                elif msg_type == 'test_completed':
                    case_id = data.get('testId')
                    task_id = data.get('taskId')
                    success = data.get('success', False)
                    complete_device_id = device_id or data.get('deviceId')
                    complete_message = (data.get('message') or '').strip()

                    if not task_id:
                        task_id, fallback_case = get_device_current_task(complete_device_id)
                        if not case_id:
                            case_id = fallback_case

                    logger.info(
                        f"Test completed: task={task_id}, case={case_id}, "
                        f"success={success}, message={complete_message[:80]}"
                    )

                    if task_id and case_id:
                        finishing_started = None
                        tasks_dir = load_tasks()
                        if task_id in tasks_dir:
                            for item in tasks_dir[task_id].get('case_progress', []):
                                if (item.get('case_id') == case_id
                                        or item.get('filename', '').startswith(case_id)):
                                    finishing_started = item.get('finishing_started_at')
                                    break
                        if finishing_started:
                            delta = time.time() - finishing_started
                            logger.info(
                                f"Test completed 收尾耗时: task={task_id} case={case_id} "
                                f"since_finishing={delta:.1f}s"
                            )
                        else:
                            logger.info(
                                f"Test completed 无收尾起点记录: task={task_id} case={case_id}"
                            )

                    if task_id and case_id:
                        with tasks_lock:
                            if task_id in tasks:
                                outcome = None
                                if '人工接管' in complete_message:
                                    outcome = 'takeover'
                                tasks[task_id]['results'].append({
                                    'test_id': case_id,
                                    'status': 'completed',
                                    'success': success,
                                    'message': complete_message,
                                    'outcome': outcome,
                                    'device_id': complete_device_id,
                                    'timestamp': time.time()
                                })

                elif msg_type == 'case_reply':
                    ingest_case_reply(data, device_id)

                elif msg_type == 'round_start':
                    ingest_round_start(data, device_id)

                elif msg_type == 'gui_step_latency':
                    ingest_gui_step_latency(data, device_id)

                elif msg_type == 'console_log':
                    # 首页设备控制台：Demo 进程 logcat 实时上报
                    console_device = device_id or data.get('deviceId') or ''
                    ingest_device_console_log(console_device, data)

                elif msg_type == 'log_chunk':
                    chunk_device = device_id or data.get('deviceId')
                    task_id = (data.get('taskId') or '').strip()
                    case_id = (data.get('caseId') or '').strip()
                    if not task_id or not case_id:
                        ft, fc = get_device_current_task(chunk_device)
                        task_id = task_id or (ft or '')
                        case_id = case_id or (fc or '')
                    text = data.get('text') or ''
                    reset = bool(data.get('reset'))
                    if task_id and case_id:
                        append_device_live_log(task_id, case_id, text, reset=reset)
                    elif text:
                        logger.info(f"[DeviceLive] (no task/case) {text[:200]}")
                    # 无任务时也写入首页控制台，便于看 GUI 接管等日志
                    live_console_id = device_id or data.get('deviceId') or chunk_device or ''
                    if live_console_id and text:
                        ingest_device_console_log(live_console_id, {
                            'text': text,
                            'seq': data.get('seq'),
                        })

                elif msg_type == 'log':
                    # Log message from device
                    log_msg = data.get('message', '')
                    level = data.get('level', 'info')
                    logger.info(f"[Device] {log_msg}")
                    task_id = (data.get('taskId') or '').strip()
                    case_id = (data.get('caseId') or '').strip()
                    if not task_id or not case_id:
                        ft, fc = get_device_current_task(device_id or data.get('deviceId'))
                        task_id = task_id or (ft or '')
                        case_id = case_id or (fc or '')
                    if task_id and case_id and log_msg:
                        append_device_live_log(
                            task_id, case_id, f"[{level}] {log_msg}", reset=False
                        )
                    if (device_id or data.get('deviceId')) and log_msg:
                        ingest_device_console_log(
                            device_id or data.get('deviceId'),
                            {'text': f"[{level}] {log_msg}\n"},
                        )

            except json.JSONDecodeError:
                logger.warning(f"Invalid JSON received: {message[:100]}")

    except Exception as e:
        logger.error(f"WebSocket error: {e}")
    finally:
        # Clean up device on disconnect
        if device_id:
            with devices_lock:
                # 仅清理本连接，避免旧 WS 的 finally 把重连后的新 WS 删掉
                cur = devices.get(device_id)
                if cur is not None and cur.get('ws') is ws:
                    del devices[device_id]
                    logger.info(f"Device disconnected: {device_id}")
                elif cur is not None:
                    logger.info(
                        f"Device {device_id} stale socket closed; keep newer websocket"
                    )

# ========== Helper Functions ==========

RESULT_METADATA_FILES = {'latency.json', 'evaluation.json'}


def generate_task_id():
    """生成唯一任务 ID（创建任务时由服务端分配）。"""
    existing = load_tasks()
    for _ in range(20):
        task_id = f"task_{datetime.now().strftime('%Y%m%d_%H%M%S')}_{uuid.uuid4().hex[:6]}"
        if task_id not in existing:
            return task_id
    return f"task_{uuid.uuid4().hex}"


def list_task_ids():
    """汇总所有任务 ID（tasks 目录 + testresults 目录）。"""
    task_ids = set()
    tasks_folder = app.config['TASKS_FOLDER']
    if os.path.exists(tasks_folder):
        for name in os.listdir(tasks_folder):
            if name.endswith('.json'):
                task_ids.add(name[:-5])
    results_dir = app.config['TESTRESULTS_FOLDER']
    if os.path.exists(results_dir):
        for name in os.listdir(results_dir):
            if os.path.isdir(os.path.join(results_dir, name)):
                task_ids.add(name)
    return sorted(task_ids, reverse=True)


def is_case_directory(task_path, name):
    if name in RESULT_METADATA_FILES:
        return False
    return os.path.isdir(os.path.join(task_path, name))


def _normalize_image_timestamp_ms(raw_ts):
    """统一为毫秒：旧版秒级(10位)或新版毫秒(13位)时间戳。"""
    if not raw_ts:
        return 0
    try:
        ts = int(raw_ts)
    except (TypeError, ValueError):
        return 0
    if ts < 1_000_000_000_000:
        return ts * 1000
    return ts


def _is_result_final_filename(filename):
    """识别用例收尾截图：final_result / result_final（含 {ms}_final_result.*）。"""
    base = os.path.basename(filename or '')
    return bool(re.search(r'(?:^|[_.-])(?:final_result|result_final)(?:[_.-]|$)', base, re.I))


def _is_gui_poll_only_filename(filename):
    """仅操控过程 gui_poll_*（不含收尾 final_result）。"""
    base = os.path.basename(filename or '').lower()
    return 'gui_poll_' in base


def _is_gui_poll_filename(filename):
    """用例截图页：操控后 gui_poll_*，以及执行结束等待 3 秒后的 final_result。"""
    return _is_gui_poll_only_filename(filename) or _is_result_final_filename(filename)


def _extract_image_timestamp(filename):
    """从截图文件名提取拍摄时刻（毫秒）。支持 {ms}_{tag}.ext 与旧版 {tag}_{ms}.ext。"""
    base = os.path.basename(filename or '')
    match = re.match(r'^(\d{10,13})_', base)
    if match:
        return _normalize_image_timestamp_ms(match.group(1))
    match = re.search(r'_(\d{10,13})\.(png|jpe?g)$', base, re.I)
    if match:
        return _normalize_image_timestamp_ms(match.group(1))
    return 0


def _assign_screenshot_rounds(task_id, case_id, screenshots):
    """按 qa_round_latency.json 的每轮时间窗，把截图归到对应轮次。

    截图常在 completed_at 之后稍晚落盘，窗口右侧留 1s、左侧留 2s 容差；
    落在两轮之间的截图归到最近的上一条轮次（仍早于首轮则归首轮）。

    final_result / result_final 是用例结束后的收尾图，不属于任何问答轮次，
    round 固定为 None，避免被末轮时间窗「兜底」挂到最后一轮。
    """
    rounds = load_case_round_latency(task_id, case_id) or []
    windows = []
    for item in rounds:
        rnd = item.get('round')
        sent = item.get('sent_at_ms')
        if rnd is None or not sent:
            continue
        try:
            sent_ms = int(float(sent))
        except (TypeError, ValueError):
            continue
        done_ms = None
        done = item.get('completed_at_ms')
        if done is not None:
            try:
                done_ms = int(float(done))
            except (TypeError, ValueError):
                done_ms = None
        if done_ms is None:
            try:
                lat = item.get('latency_ms')
                done_ms = sent_ms + int(float(lat)) if lat else sent_ms
            except (TypeError, ValueError):
                done_ms = sent_ms
        windows.append({
            'round': int(rnd),
            'start': sent_ms,
            'end': max(sent_ms, done_ms),
        })
    windows.sort(key=lambda w: w['start'])
    if not windows:
        for ss in screenshots:
            ss['round'] = None
        return screenshots
    for ss in screenshots:
        name = ss.get('name') or ''
        if _is_result_final_filename(name):
            ss['round'] = None
            continue
        ts = ss.get('captured_at') or 0
        target = None
        for w in windows:
            if w['start'] - 2000 <= ts <= w['end'] + 1000:
                target = w['round']
                break
        if target is None:
            for w in windows:
                if ts >= w['start']:
                    target = w['round']
            if target is None:
                target = windows[0]['round']
        ss['round'] = target
    return screenshots


def collect_case_screenshots(task_id, case_id):
    case_path = os.path.join(app.config['TESTRESULTS_FOLDER'], task_id, case_id)
    screenshots = []
    if not os.path.exists(case_path):
        return screenshots
    for fname in os.listdir(case_path):
        lower = fname.lower()
        if not lower.endswith(('.png', '.jpg', '.jpeg')):
            continue
        if not _is_gui_poll_filename(fname):
            continue
        fpath = os.path.join(case_path, fname)
        if not os.path.isfile(fpath):
            continue
        rel_name = f'{case_id}/{fname}'
        captured_at_ms = _extract_image_timestamp(fname)
        if not captured_at_ms:
            captured_at_ms = int(os.path.getmtime(fpath) * 1000)
        screenshots.append({
            'name': rel_name,
            'captured_at': captured_at_ms,
            'size': os.path.getsize(fpath),
            'url': build_ui_path(f'/api/results/{task_id}/{case_id}/{fname}')
        })
    screenshots.sort(
        key=lambda item: (item.get('captured_at') or 0, item['name']),
        reverse=True,
    )
    return _assign_screenshot_rounds(task_id, case_id, screenshots)


def scan_task_cases(task_id):
    """扫描任务下的用例目录：testresults/{task_id}/{case_id}/"""
    task_path = os.path.join(app.config['TESTRESULTS_FOLDER'], task_id)
    if not os.path.exists(task_path):
        return []
    cases = []
    for item in sorted(os.listdir(task_path)):
        if not is_case_directory(task_path, item):
            continue
        case_path = os.path.join(task_path, item)
        screenshots = collect_case_screenshots(task_id, item)
        log_count = sum(1 for f in os.listdir(case_path) if f.endswith('.log'))
        cases.append({
            'case_id': item,
            'screenshot_count': len(screenshots),
            'log_count': log_count,
            'has_latency': os.path.exists(os.path.join(case_path, 'latency.json')),
            'has_qa_latency': os.path.exists(
                os.path.join(case_path, 'qa_round_latency.json')),
            'has_replies': os.path.exists(
                os.path.join(case_path, 'replies.json')),
            'has_evaluation': os.path.exists(os.path.join(case_path, 'evaluation.json')),
            'screenshots': screenshots
        })
    return cases


def infer_case_completed_at(task_id, case_id):
    """从用例结果目录最新文件修改时间推断结束时间（无 completed_at 时的兜底）。"""
    case_path = os.path.join(app.config['TESTRESULTS_FOLDER'], task_id, case_id)
    if not os.path.isdir(case_path):
        return None
    latest = 0.0
    for fname in os.listdir(case_path):
        fpath = os.path.join(case_path, fname)
        if os.path.isfile(fpath):
            latest = max(latest, os.path.getmtime(fpath))
    return latest if latest > 0 else None


def resolve_case_completed_at(task_id, case_id, progress):
    """仅对终态用例返回结束时间；执行/收尾/分析中一律不显示。"""
    progress = progress or {}
    status = (progress.get('status') or '').strip().lower()
    if status in ('running', 'pending', 'queued', 'finishing', 'analyzing'):
        return None
    completed_at = progress.get('completed_at')
    if completed_at:
        return completed_at
    if status in ('completed', 'failed', 'cancelled', 'skipped', 'takeover'):
        return infer_case_completed_at(task_id, case_id)
    return None


def peek_webtest_accuracy(eval_data):
    """从 evaluation.json 取出 WebTest 综合分（没有则 None）。"""
    if not isinstance(eval_data, dict):
        return None
    accuracy = eval_data.get('webtest_accuracy')
    if accuracy is None:
        scores = eval_data.get('webtest_scores') or {}
        if isinstance(scores, dict):
            accuracy = scores.get('accuracy')
    if accuracy is None:
        return None
    return accuracy


def peek_webtest_metrics(eval_data):
    """表格用的四维分摘要。"""
    if not isinstance(eval_data, dict):
        return []
    scores = eval_data.get('webtest_scores') or {}
    if not isinstance(scores, dict):
        return []
    briefs = []
    for item in scores.get('metrics') or []:
        if not isinstance(item, dict):
            continue
        label = (item.get('label') or item.get('key') or '').strip()
        if not label and item.get('score') is None:
            continue
        briefs.append({
            'label': label,
            'score': item.get('score'),
        })
    return briefs


def load_hydrated_case_evaluation(task_id, case_id):
    """读取并补全 webtest_scores，文件不存在返回 None。"""
    if not task_id or not case_id:
        return None
    eval_file = os.path.join(
        app.config['TESTRESULTS_FOLDER'], task_id, case_id, 'evaluation.json',
    )
    if not os.path.exists(eval_file):
        return None
    try:
        with open(eval_file, 'r', encoding='utf-8') as f:
            eval_data = json.load(f)
    except Exception:
        return None
    try:
        from webtest_eval import hydrate_webtest_evaluation
        eval_data = hydrate_webtest_evaluation(eval_data)
    except Exception:
        pass
    return eval_data


def load_case_eval_summary(task_id, case_id, progress_item=None):
    """表格用：是否通过 + WebTest 综合分 + 四维分 + 文字/截图通过标记。"""
    passed = None
    if progress_item and 'evaluation_passed' in progress_item:
        passed = bool(progress_item['evaluation_passed'])
    accuracy = None
    metrics = []
    text_passed = None
    screenshot_passed = None
    eval_file = os.path.join(
        app.config['TESTRESULTS_FOLDER'], task_id, case_id, 'evaluation.json',
    )
    if os.path.exists(eval_file):
        try:
            with open(eval_file, 'r', encoding='utf-8') as f:
                eval_data = json.load(f)
            if eval_data.get('source') == 'webtest' and not eval_data.get('webtest_scores'):
                try:
                    from webtest_eval import hydrate_webtest_evaluation
                    eval_data = hydrate_webtest_evaluation(eval_data)
                except Exception:
                    pass
            if passed is None:
                passed = parse_evaluation_passed(eval_data)
            accuracy = peek_webtest_accuracy(eval_data)
            metrics = peek_webtest_metrics(eval_data)
            mode = (eval_data.get('mode') or '').strip()
            if mode == 'text_and_gui_poll':
                if eval_data.get('text_passed') is not None:
                    text_passed = bool(eval_data.get('text_passed'))
                if eval_data.get('screenshot_passed') is not None:
                    screenshot_passed = bool(eval_data.get('screenshot_passed'))
            elif mode in ('qa_rounds', 'gui_text_replies') or (
                eval_data.get('source') in ('webtest', 'model_replies')
                and not eval_data.get('screenshot_evaluation')
            ):
                text_passed = parse_evaluation_passed(eval_data)
                screenshot_passed = None
            elif mode == 'vl_image' or eval_data.get('source') == 'screenshot':
                text_passed = None
                screenshot_passed = parse_evaluation_passed(eval_data)
            else:
                # 兼容：有 screenshot_evaluation 则拆开读
                if eval_data.get('text_passed') is not None:
                    text_passed = bool(eval_data.get('text_passed'))
                elif eval_data.get('text_evaluation') is not None:
                    text_passed = parse_evaluation_passed(eval_data.get('text_evaluation'))
                if eval_data.get('screenshot_passed') is not None:
                    screenshot_passed = bool(eval_data.get('screenshot_passed'))
                elif eval_data.get('screenshot_evaluation') is not None:
                    screenshot_passed = parse_evaluation_passed(
                        eval_data.get('screenshot_evaluation'))
        except Exception:
            pass
    return passed, accuracy, metrics, text_passed, screenshot_passed


def load_case_evaluation_passed(task_id, case_id, progress_item=None):
    """读取用例评判是否通过：优先 case_progress，其次 evaluation.json。"""
    passed, _accuracy, _metrics, _tp, _sp = load_case_eval_summary(
        task_id, case_id, progress_item)
    return passed


def peek_case_model_answer(task_id, case_id, max_len=120):
    """表格用：读取 replies.json 中第一条可用模型回答（不做日志回填）。"""
    if not task_id or not case_id:
        return ''
    path = os.path.join(
        app.config['TESTRESULTS_FOLDER'], task_id, case_id, 'replies.json')
    if not os.path.isfile(path):
        return ''
    try:
        with open(path, 'r', encoding='utf-8') as f:
            payload = json.load(f)
    except Exception:
        return ''
    replies = payload.get('replies') if isinstance(payload, dict) else payload
    for item in replies or []:
        if not isinstance(item, dict):
            continue
        text = (item.get('reply_text') or item.get('replyText') or '').strip()
        if not text or text.startswith('[强制结束]'):
            continue
        if text in ('testapp_force', '(空)', 'null', 'None'):
            continue
        text = ' '.join(text.split())
        if len(text) > max_len:
            return text[:max_len] + '…'
        return text
    return ''


def peek_case_device_id(task_id, case_id):
    """从 replies.json 取上报过的 device_id（进度丢失时回填展示）。"""
    if not task_id or not case_id:
        return ''
    path = os.path.join(
        app.config['TESTRESULTS_FOLDER'], task_id, case_id, 'replies.json')
    if not os.path.isfile(path):
        return ''
    try:
        with open(path, 'r', encoding='utf-8') as f:
            payload = json.load(f)
    except Exception:
        return ''
    replies = payload.get('replies') if isinstance(payload, dict) else payload
    for item in replies or []:
        if not isinstance(item, dict):
            continue
        did = (item.get('device_id') or item.get('deviceId') or '').strip()
        if did:
            return did
    return ''


def resolve_case_assigned_device(task_id, case_id, progress_item=None):
    """解析用例执行设备：进度字段 → replies → 在线设备当前任务。"""
    progress = progress_item or {}
    assigned = (progress.get('assigned_device') or '').strip()
    if assigned:
        return assigned
    from_reply = peek_case_device_id(task_id, case_id)
    if from_reply:
        return from_reply
    if not task_id or not case_id:
        return ''
    with devices_lock:
        matches = [
            did for did, device in devices.items()
            if device.get('current_task_id') == task_id
            and device.get('current_case_id') == case_id
        ]
    if len(matches) == 1:
        return matches[0]
    if matches:
        return ','.join(matches)
    return ''


def resolve_case_started_at(task_id, case_id, progress_item=None):
    """开始时间：进度 started_at，否则用收尾/分析时间兜底。"""
    progress = progress_item or {}
    started = progress.get('started_at')
    if started:
        return started
    for key in ('finishing_started_at', 'analyzing_started_at', 'step_updated_at'):
        val = progress.get(key)
        if val:
            return val
    return None


_GENERIC_DEVICE_NAME_RE = re.compile(
    r'(sdk_gphone|generic_arm|emulator|unknown device)',
    re.IGNORECASE,
)
_device_avd_map_cache = None
_device_avd_map_mtime = None


def device_avd_map_path():
    return os.path.join(os.path.dirname(__file__), 'data', 'device_avd_map.json')


def load_device_avd_map():
    """deviceId -> 虚拟机/AVD 名（持久化，供结果表展示）。"""
    global _device_avd_map_cache, _device_avd_map_mtime
    path = device_avd_map_path()
    try:
        mtime = os.path.getmtime(path) if os.path.isfile(path) else None
    except OSError:
        mtime = None
    if _device_avd_map_cache is not None and mtime == _device_avd_map_mtime:
        return _device_avd_map_cache
    mapping = {}
    if mtime is not None:
        try:
            with open(path, 'r', encoding='utf-8-sig') as f:
                raw = json.load(f) or {}
            if isinstance(raw, dict):
                mapping = {
                    str(k).strip(): str(v).strip()
                    for k, v in raw.items()
                    if str(k).strip() and str(v).strip()
                }
        except Exception as e:
            logger.warning(f'读取 device_avd_map 失败: {e}')
    _device_avd_map_cache = mapping
    _device_avd_map_mtime = mtime
    return mapping


def remember_device_avd(device_id, avd_name):
    """记住 deviceId 对应的虚拟机名。"""
    did = (device_id or '').strip()
    avd = (avd_name or '').strip()
    if not did or not avd or _GENERIC_DEVICE_NAME_RE.search(avd):
        return
    # 去掉 collision 后缀再存一份主 id
    base_id = did.split('__', 1)[0]
    mapping = dict(load_device_avd_map())
    if mapping.get(did) == avd and mapping.get(base_id) == avd:
        return
    mapping[did] = avd
    mapping[base_id] = avd
    path = device_avd_map_path()
    try:
        os.makedirs(os.path.dirname(path), exist_ok=True)
        tmp = path + '.tmp'
        with open(tmp, 'w', encoding='utf-8') as f:
            json.dump(mapping, f, indent=2, ensure_ascii=False)
            f.flush()
            os.fsync(f.fileno())
        os.replace(tmp, path)
        global _device_avd_map_cache, _device_avd_map_mtime
        _device_avd_map_cache = mapping
        try:
            _device_avd_map_mtime = os.path.getmtime(path)
        except OSError:
            _device_avd_map_mtime = None
    except Exception as e:
        logger.warning(f'写入 device_avd_map 失败: {e}')


def extract_avd_name(device_name=None, device_info=None):
    """从注册信息中提取虚拟机/AVD 名。"""
    info = device_info or {}
    for key in ('avdName', 'avd_name', 'avd'):
        val = (info.get(key) or '').strip()
        if val and not _GENERIC_DEVICE_NAME_RE.search(val):
            return val
    name = (device_name or '').strip()
    if name and not _GENERIC_DEVICE_NAME_RE.search(name):
        # "avd_1 (device_xxx)" collision 标注
        base = re.sub(r'\s*\([^)]*\)\s*$', '', name).strip()
        if base and not _GENERIC_DEVICE_NAME_RE.search(base):
            return base
    return ''


def format_device_display(device_id):
    """表格展示用：优先虚拟机/AVD 名，避免各机相同的 Build.MODEL。"""
    raw = (device_id or '').strip()
    if not raw:
        return ''
    parts = [p.strip() for p in raw.split(',') if p.strip()]
    avd_map = load_device_avd_map()
    labels = []
    with devices_lock:
        for did in parts:
            device = devices.get(did) or {}
            info = device.get('info') or {}
            avd = extract_avd_name(device.get('name'), info)
            if not avd:
                avd = (avd_map.get(did) or avd_map.get(did.split('__', 1)[0]) or '').strip()
            if avd:
                labels.append(avd)
                continue
            name = (device.get('name') or '').strip()
            if name and not _GENERIC_DEVICE_NAME_RE.search(name):
                labels.append(re.sub(r'\s*\([^)]*\)\s*$', '', name).strip() or name)
            else:
                labels.append(did)
    return ', '.join(labels)


def build_case_result_row(task_id, case_scan, progress_item=None):
    """汇总单条用例结果行（表格展示用，不含截图列表）。"""
    case_id = case_scan['case_id']
    progress = progress_item or {}
    started_at = resolve_case_started_at(task_id, case_id, progress)
    completed_at = resolve_case_completed_at(task_id, case_id, progress)
    preview = peek_case_model_answer(task_id, case_id)
    assigned_device = resolve_case_assigned_device(task_id, case_id, progress)
    evaluation_passed, webtest_accuracy, webtest_metrics, text_passed, screenshot_passed = (
        load_case_eval_summary(task_id, case_id, progress)
    )
    return {
        'case_id': case_id,
        'case_name': case_id,
        'filename': progress.get('filename') or f'{case_id}.json',
        'status': progress.get('status', 'unknown'),
        'fail_reason': progress.get('fail_reason') or None,
        'started_at': started_at,
        'completed_at': completed_at,
        'step_index': progress.get('step_index'),
        'step_total': progress.get('step_total'),
        'timeout_sec': progress.get('timeout_sec'),
        'analyzing_started_at': progress.get('analyzing_started_at'),
        'finishing_started_at': progress.get('finishing_started_at'),
        'assigned_device': assigned_device,
        'device_label': format_device_display(assigned_device),
        'evaluation_passed': evaluation_passed,
        'webtest_accuracy': webtest_accuracy,
        'webtest_metrics': webtest_metrics,
        'text_passed': text_passed,
        'screenshot_passed': screenshot_passed,
        'screenshot_count': case_scan.get('screenshot_count', 0),
        'log_count': case_scan.get('log_count', 0),
        'has_latency': case_scan.get('has_latency', False),
        'has_qa_latency': case_scan.get('has_qa_latency', False),
        'has_replies': bool(preview) or case_scan.get('has_replies', False),
        'has_usable_reply': bool(preview),
        'model_answer_preview': preview,
        'has_evaluation': case_scan.get('has_evaluation', False),
        'eval_progress': get_case_eval_progress(task_id, case_id),
    }


def build_case_result_row_from_progress(task_id, progress_item):
    """仅有任务进度、尚无结果目录时的用例行。"""
    case_id = progress_item.get('case_id') or os.path.splitext(
        progress_item.get('filename', ''),
    )[0]
    if not case_id:
        return None
    case_path = os.path.join(app.config['TESTRESULTS_FOLDER'], task_id, case_id)
    has_dir = os.path.isdir(case_path)
    screenshot_count = 0
    log_count = 0
    has_latency = False
    has_qa_latency = False
    has_replies = False
    has_evaluation = False
    if has_dir:
        screenshots = collect_case_screenshots(task_id, case_id)
        screenshot_count = len(screenshots)
        log_count = sum(
            1 for f in os.listdir(case_path) if f.endswith('.log')
        )
        has_latency = os.path.exists(os.path.join(case_path, 'latency.json'))
        has_qa_latency = os.path.exists(
            os.path.join(case_path, 'qa_round_latency.json'))
        has_replies = os.path.exists(os.path.join(case_path, 'replies.json'))
        has_evaluation = os.path.exists(os.path.join(case_path, 'evaluation.json'))
    preview = peek_case_model_answer(task_id, case_id)
    started_at = resolve_case_started_at(task_id, case_id, progress_item)
    completed_at = resolve_case_completed_at(task_id, case_id, progress_item)
    assigned_device = resolve_case_assigned_device(task_id, case_id, progress_item)
    evaluation_passed, webtest_accuracy, webtest_metrics, text_passed, screenshot_passed = (
        load_case_eval_summary(task_id, case_id, progress_item)
    )
    return {
        'case_id': case_id,
        'case_name': case_id,
        'filename': progress_item.get('filename') or f'{case_id}.json',
        'status': progress_item.get('status', 'pending'),
        'fail_reason': progress_item.get('fail_reason') or None,
        'started_at': started_at,
        'completed_at': completed_at,
        'step_index': progress_item.get('step_index'),
        'step_total': progress_item.get('step_total'),
        'timeout_sec': progress_item.get('timeout_sec'),
        'analyzing_started_at': progress_item.get('analyzing_started_at'),
        'finishing_started_at': progress_item.get('finishing_started_at'),
        'assigned_device': assigned_device,
        'device_label': format_device_display(assigned_device),
        'evaluation_passed': evaluation_passed,
        'webtest_accuracy': webtest_accuracy,
        'webtest_metrics': webtest_metrics,
        'text_passed': text_passed,
        'screenshot_passed': screenshot_passed,
        'screenshot_count': screenshot_count,
        'log_count': log_count,
        'has_latency': has_latency,
        'has_qa_latency': has_qa_latency,
        'has_replies': bool(preview) or has_replies,
        'has_usable_reply': bool(preview),
        'model_answer_preview': preview,
        'has_evaluation': has_evaluation,
        'eval_progress': get_case_eval_progress(task_id, case_id),
    }


def list_task_case_rows(task_id):
    """合并 testresults 扫描与 tasks 内 case_progress，得到完整用例列表。"""
    task = load_tasks().get(task_id) or {}
    progress_map = {}
    for item in task.get('case_progress') or []:
        case_id = item.get('case_id') or os.path.splitext(item.get('filename', ''))[0]
        if case_id:
            progress_map[case_id] = item

    rows = []
    seen = set()
    for case_scan in scan_task_cases(task_id):
        case_id = case_scan['case_id']
        seen.add(case_id)
        rows.append(build_case_result_row(task_id, case_scan, progress_map.get(case_id)))

    for case_id, item in progress_map.items():
        if case_id in seen:
            continue
        row = build_case_result_row_from_progress(task_id, item)
        if row:
            rows.append(row)
            seen.add(case_id)  # 必须标记，否则下面 files 循环会再插一行重复

    for filename in task.get('files') or []:
        case_id = os.path.splitext(filename)[0]
        if not case_id or case_id in seen:
            continue
        seen.add(case_id)
        row = build_case_result_row_from_progress(
            task_id,
            progress_map.get(case_id, {'case_id': case_id, 'filename': filename}),
        )
        if row:
            rows.append(row)

    # 按任务 files 顺序展示（Excel 导入顺序），避免按名称字母排序造成「看起来没按序执行」
    file_order = []
    for filename in task.get('files') or []:
        cid = os.path.splitext(filename)[0]
        if cid and cid not in file_order:
            file_order.append(cid)
    if file_order:
        order_index = {cid: i for i, cid in enumerate(file_order)}
        rows.sort(key=lambda r: (
            order_index.get(r.get('case_id') or '', 10 ** 9),
            r.get('case_name') or '',
        ))
    else:
        rows.sort(key=lambda r: r.get('case_name') or '')
    return rows


def _parse_filter_values(raw):
    """解析筛选参数：支持 all / 单值 / 逗号分隔 / 列表。"""
    if raw is None:
        return set()
    if isinstance(raw, (list, tuple, set)):
        parts = []
        for item in raw:
            parts.extend(str(item or '').split(','))
    else:
        parts = str(raw).split(',')
    values = {p.strip().lower() for p in parts if p and str(p).strip()}
    values.discard('')
    if not values or 'all' in values:
        return set()
    return values


def filter_case_result_rows(rows, query='', pass_filter='all', status_filter='all'):
    """按关键字、评判结果、执行状态筛选用例行（状态/评判均支持多选）。"""
    q = (query or '').strip().lower()
    filtered = rows
    if q:
        filtered = [
            r for r in filtered
            if q in (r.get('case_name') or '').lower()
            or q in (r.get('case_id') or '').lower()
        ]

    pass_vals = _parse_filter_values(pass_filter)
    if pass_vals:
        def _pass_match(row):
            passed = row.get('evaluation_passed')
            if passed is True and 'pass' in pass_vals:
                return True
            if passed is False and 'fail' in pass_vals:
                return True
            if passed is None and pass_vals & {'pending', 'none', 'unknown'}:
                return True
            return False
        filtered = [r for r in filtered if _pass_match(r)]

    status_vals = _parse_filter_values(status_filter)
    if status_vals:
        active = {'running', 'finishing', 'analyzing'}
        want_active = 'active' in status_vals
        concrete = status_vals - {'active'}

        def _status_match(row):
            st = (row.get('status') or '').lower()
            if want_active and st in active:
                return True
            if st and st in concrete:
                return True
            # 「等待中」筛选也包含排队中
            if st == 'queued' and 'pending' in concrete:
                return True
            return False

        filtered = [r for r in filtered if _status_match(r)]
    return filtered


def build_case_filter_facets(rows):
    """根据实际用例行生成状态下拉 / 评判下拉（含数量，仅含出现过的项）。"""
    status_counts = {}
    pass_counts = {'pass': 0, 'fail': 0, 'pending': 0}
    for row in rows or []:
        st = str(row.get('status') or 'unknown').strip().lower() or 'unknown'
        status_counts[st] = status_counts.get(st, 0) + 1
        passed = row.get('evaluation_passed')
        if passed is True:
            pass_counts['pass'] += 1
        elif passed is False:
            pass_counts['fail'] += 1
        else:
            pass_counts['pending'] += 1

    preferred_status = (
        'completed', 'takeover', 'failed', 'running', 'finishing', 'analyzing',
        'pending', 'queued', 'cancelled', 'skipped',
    )
    status_options = []
    seen = set()
    for key in preferred_status:
        cnt = status_counts.get(key) or 0
        if not cnt:
            continue
        status_options.append({
            'value': key,
            'label': case_status_display_text(key),
            'count': cnt,
        })
        seen.add(key)
    for key, cnt in sorted(status_counts.items(), key=lambda x: (-x[1], x[0])):
        if key in seen or not cnt:
            continue
        status_options.append({
            'value': key,
            'label': case_status_display_text(key) or key,
            'count': cnt,
        })

    pass_labels = {'pass': '通过', 'fail': '未通过', 'pending': '未评判'}
    pass_options = []
    for key in ('pass', 'fail', 'pending'):
        cnt = pass_counts.get(key) or 0
        if not cnt:
            continue
        pass_options.append({
            'value': key,
            'label': pass_labels[key],
            'count': cnt,
        })

    return {
        'status': status_options,
        'pass': pass_options,
        'status_counts': status_counts,
        'pass_counts': pass_counts,
    }


def set_device_current_task(device_id, task_id, case_id):
    if not device_id:
        return
    with devices_lock:
        if device_id in devices:
            devices[device_id]['current_task_id'] = task_id
            devices[device_id]['current_case_id'] = case_id


def get_device_current_task(device_id):
    if not device_id:
        return None, None
    with devices_lock:
        device = devices.get(device_id, {})
        return device.get('current_task_id'), device.get('current_case_id')


def _device_console_safe_id(device_id):
    raw = (device_id or '').strip() or 'unknown'
    safe = re.sub(r'[^A-Za-z0-9._-]+', '_', raw)[:120]
    return safe or 'unknown'


def _device_console_file(device_id):
    return os.path.join(DEVICE_CONSOLE_DIR, f'{_device_console_safe_id(device_id)}.log')


def ingest_device_console_log(device_id, data):
    """接收 Demo WS console_log，写入环形缓冲 + 文件，并推给浏览器。"""
    device_id = (device_id or '').strip()
    if not device_id:
        return
    lines = []
    if isinstance(data, dict):
        arr = data.get('lines')
        if isinstance(arr, list):
            lines = [str(x).rstrip('\n') for x in arr if x is not None and str(x).strip() != '']
        text = data.get('text') or ''
        if not lines and text:
            lines = [ln.rstrip('\n') for ln in str(text).splitlines() if ln.strip()]
        seq = data.get('seq')
    else:
        seq = None
    if not lines:
        return
    stamped = []
    now = time.strftime('%H:%M:%S')
    for ln in lines:
        # logcat threadtime 自带时间则不再加前缀
        if len(ln) > 18 and ln[2] == '-' and ln[5] == ' ':
            stamped.append(ln)
        else:
            stamped.append(f'{now} {ln}')
    with device_console_lock:
        bucket = device_console_logs.get(device_id)
        if bucket is None:
            bucket = {'seq': 0, 'lines': deque(maxlen=DEVICE_CONSOLE_MAX_LINES)}
            device_console_logs[device_id] = bucket
        try:
            if seq is not None:
                bucket['seq'] = max(int(bucket.get('seq') or 0), int(seq))
            else:
                bucket['seq'] = int(bucket.get('seq') or 0) + 1
        except (TypeError, ValueError):
            bucket['seq'] = int(bucket.get('seq') or 0) + 1
        for ln in stamped:
            bucket['lines'].append(ln)
        out_seq = bucket['seq']
    try:
        with open(_device_console_file(device_id), 'a', encoding='utf-8', errors='replace') as f:
            for ln in stamped:
                f.write(ln + '\n')
    except Exception as e:
        logger.warning('device console file write failed: %s', e)
    try:
        socketio.emit(
            'device_console_log',
            {'deviceId': device_id, 'lines': stamped, 'seq': out_seq},
            room=f'device_console:{device_id}',
        )
    except Exception:
        pass


def get_device_console_lines(device_id, since_seq=0, limit=1500):
    device_id = (device_id or '').strip()
    if not device_id:
        return [], 0
    with device_console_lock:
        bucket = device_console_logs.get(device_id)
        if not bucket:
            return [], 0
        seq = int(bucket.get('seq') or 0)
        lines = list(bucket.get('lines') or [])
    if since_seq and since_seq > 0:
        # 简化：since_seq 只用于前端增量轮询；缓冲无逐行 seq，返回尾部
        lines = lines[-limit:]
    else:
        lines = lines[-limit:]
    return lines, seq


def send_device_console_control(device_id, enable=True):
    """通知 Demo 开/关 logcat 上报。"""
    device_id = (device_id or '').strip()
    if not device_id:
        return False, '缺少 deviceId'
    with devices_lock:
        device = devices.get(device_id)
        ws = device.get('ws') if device else None
    if ws is None:
        return False, '设备不在线'
    try:
        ws.send(json.dumps({
            'type': 'console_log_control',
            'enable': bool(enable),
            'deviceId': device_id,
        }))
        return True, 'ok'
    except Exception as e:
        return False, str(e)


def append_device_live_log(task_id, case_id, text, reset=False):
    """追加设备实时日志到用例目录，并推送给正在观看的浏览器。"""
    if not task_id or not case_id:
        return None
    result_path = os.path.join(app.config['TESTRESULTS_FOLDER'], task_id, case_id)
    os.makedirs(result_path, exist_ok=True)
    filepath = os.path.join(result_path, 'device_live.log')
    mode = 'w' if reset else 'a'
    chunk = text or ''
    try:
        with open(filepath, mode, encoding='utf-8') as f:
            if chunk:
                f.write(chunk)
                if not chunk.endswith('\n'):
                    f.write('\n')
        socketio.emit(
            'device_live_log',
            {
                'taskId': task_id,
                'caseId': case_id,
                'text': chunk,
                'reset': bool(reset),
            },
            room=f'task:{task_id}',
        )
        return filepath
    except Exception as e:
        logger.warning(f'写入实时日志失败: {e}')
        return None


def get_log_lines(hours=24):
    """Get last N hours of log lines."""
    log_file = os.path.join(LOG_DIR, 'webserver.log')
    if not os.path.exists(log_file):
        return []

    try:
        with open(log_file, 'r', encoding='utf-8') as f:
            lines = f.readlines()

        # Filter by time
        cutoff_time = datetime.now() - timedelta(hours=hours)
        filtered_lines = []
        for line in lines:
            try:
                # Parse log line: "2026-04-11 10:00:00,123 - INFO - message"
                log_time_str = line.split(' - ')[0].strip()
                log_time = datetime.strptime(log_time_str, '%Y-%m-%d %H:%M:%S,%f')
                if log_time >= cutoff_time:
                    filtered_lines.append(line.strip())
            except:
                filtered_lines.append(line.strip())

        return filtered_lines[-500:]  # Last 500 lines
    except Exception as e:
        logger.error(f"Error reading log file: {e}")
        return []

def resolve_testset_path(testset_name, *parts):
    """Resolve path under testfiles, supporting nested runtime folders."""
    segments = [app.config['UPLOAD_FOLDER']]
    if testset_name:
        segments.extend(testset_name.replace('\\', '/').split('/'))
    segments.extend(parts)
    return os.path.normpath(os.path.join(*segments))


def list_testset_json_files(testset_name):
    """List JSON filenames in a testset folder (including runtime subfolders)."""
    testset_path = resolve_testset_path(testset_name)
    if not os.path.isdir(testset_path):
        return []
    return sorted(
        f for f in os.listdir(testset_path)
        if f.endswith('.json') and os.path.isfile(os.path.join(testset_path, f))
    )


def scan_excel_datasets():
    """Scan excel_datasets directory."""
    global excel_datasets
    datasets_dir = app.config['EXCEL_DATASETS_FOLDER']
    new_datasets = {}
    if os.path.exists(datasets_dir):
        for item in os.listdir(datasets_dir):
            if item.startswith('.'):
                continue
            item_path = os.path.join(datasets_dir, item)
            if not os.path.isdir(item_path):
                continue
            xlsx_path = os.path.join(item_path, EXCEL_DATASET_FILENAME)
            stored_name = EXCEL_DATASET_FILENAME
            if not os.path.isfile(xlsx_path):
                xlsx_files = sorted(
                    f for f in os.listdir(item_path)
                    if f.lower().endswith(('.xlsx', '.xlsm'))
                    and os.path.isfile(os.path.join(item_path, f))
                )
                if not xlsx_files:
                    new_datasets[item] = {
                        'name': item,
                        'create_time': os.path.getctime(item_path),
                        'has_file': False,
                        'filename': '',
                        'size': 0,
                        'last_modified': None,
                        'case_count': 0,
                    }
                    continue
                stored_name = xlsx_files[0]
                xlsx_path = os.path.join(item_path, stored_name)
            new_datasets[item] = {
                'name': item,
                'create_time': os.path.getctime(item_path),
                'has_file': True,
                'filename': stored_name,
                'size': os.path.getsize(xlsx_path),
                'last_modified': os.path.getmtime(xlsx_path),
                'case_count': count_excel_dataset_cases(xlsx_path),
            }
    with excel_datasets_lock:
        excel_datasets = new_datasets
    return excel_datasets


def get_excel_dataset_file_path(dataset_name):
    """Return on-disk Excel file path for a dataset, or None."""
    safe_name = secure_filename(dataset_name)
    if not safe_name or safe_name != dataset_name:
        return None
    item_path = os.path.join(app.config['EXCEL_DATASETS_FOLDER'], safe_name)
    if not os.path.isdir(item_path):
        return None
    primary = os.path.join(item_path, EXCEL_DATASET_FILENAME)
    if os.path.isfile(primary):
        return primary
    xlsx_files = sorted(
        f for f in os.listdir(item_path)
        if f.lower().endswith(('.xlsx', '.xlsm'))
        and os.path.isfile(os.path.join(item_path, f))
    )
    if not xlsx_files:
        return None
    return os.path.join(item_path, xlsx_files[0])


def convert_excel_dataset_to_runtime(task_id, dataset_name, config):
    """Convert stored Excel dataset to runtime JSON testset for a task."""
    xlsx_path = get_excel_dataset_file_path(dataset_name)
    if not xlsx_path:
        return None, 'Excel 数据集不存在或未上传 Excel 文件'

    runtime_testset = f'{RUNTIME_TESTSET_PREFIX}/{task_id}'
    runtime_path = resolve_testset_path(runtime_testset)
    if os.path.exists(runtime_path):
        shutil.rmtree(runtime_path)
    os.makedirs(runtime_path, exist_ok=True)

    with open(xlsx_path, 'rb') as f:
        data = f.read()
    try:
        result = import_excel_bytes_to_testset(runtime_path, data, config=config)
    except ImportError:
        shutil.rmtree(runtime_path, ignore_errors=True)
        return None, '服务端缺少 openpyxl，请执行 pip install openpyxl'
    except Exception as e:
        shutil.rmtree(runtime_path, ignore_errors=True)
        logger.error(f"Excel convert failed for task {task_id}: {e}")
        return None, f'Excel 转换失败: {e}'

    if not result.get('success'):
        shutil.rmtree(runtime_path, ignore_errors=True)
        return None, result.get('message') or 'Excel 转换失败'

    created = result.get('created') or []
    if not created:
        shutil.rmtree(runtime_path, ignore_errors=True)
        return None, 'Excel 中未生成有效用例'
    return runtime_testset, created


def scan_testfiles():
    """Scan testfiles directory and build testsets structure."""
    global testsets
    testfiles_dir = app.config['UPLOAD_FOLDER']

    new_testsets = {}
    if os.path.exists(testfiles_dir):
        for item in os.listdir(testfiles_dir):
            if item.startswith('.'):
                continue
            item_path = os.path.join(testfiles_dir, item)
            if os.path.isdir(item_path):
                # This is a testset (folder)
                files = []
                for f in os.listdir(item_path):
                    if f.endswith('.json'):
                        fpath = os.path.join(item_path, f)
                        files.append({
                            'name': f,
                            'size': os.path.getsize(fpath),
                            'last_modified': os.path.getmtime(fpath)
                        })
                new_testsets[item] = {
                    'name': item,
                    'create_time': os.path.getctime(item_path),
                    'files': sorted(files, key=lambda x: x['name'])
                }
            elif item.endswith('.json'):
                # Root level JSON files go to 'default' testset
                if 'default' not in new_testsets:
                    new_testsets['default'] = {
                        'name': 'default',
                        'create_time': time.time(),
                        'files': []
                    }
                fpath = os.path.join(testfiles_dir, item)
                new_testsets['default']['files'].append({
                    'name': item,
                    'size': os.path.getsize(fpath),
                    'last_modified': os.path.getmtime(fpath)
                })

    # Sort files in each testset
    for ts in new_testsets:
        new_testsets[ts]['files'] = sorted(new_testsets[ts]['files'], key=lambda x: x['name'])

    with testsets_lock:
        testsets = new_testsets

    return testsets

def get_device_by_id(device_id):
    """Get device info by ID."""
    with devices_lock:
        return devices.get(device_id)

ACTIVE_TASK_STATUSES = ('pending', 'running', 'finishing', 'analyzing')
DEVICE_BUSY_STATUSES = ('running', 'finishing', 'analyzing')


def parse_schedule_time(value):
    """解析前端 datetime-local 或常见时间字符串，返回 Unix 时间戳（秒）。"""
    if not value:
        return None
    text = str(value).strip()
    for fmt in ('%Y-%m-%dT%H:%M:%S', '%Y-%m-%dT%H:%M', '%Y-%m-%d %H:%M:%S', '%Y-%m-%d %H:%M'):
        try:
            return datetime.strptime(text, fmt).timestamp()
        except ValueError:
            continue
    return None


def get_task_sort_key(task):
    """任务列表排序键：已开始用 start_time，未开始的定时任务用 schedule_time。"""
    start_time = task.get('start_time')
    if isinstance(start_time, (int, float)) and start_time > 0:
        return float(start_time)
    if task.get('start_type') == 'scheduled':
        schedule_ts = parse_schedule_time(task.get('schedule_time'))
        if schedule_ts is not None:
            return float(schedule_ts)
    created_at = task.get('created_at')
    if isinstance(created_at, (int, float)):
        return float(created_at)
    return 0.0


def get_device_busy_map(exclude_task_id=None):
    """返回正在执行任务的设备占用信息（不含等待中的定时任务）。"""
    busy = {}
    for task_id, task in load_tasks().items():
        if exclude_task_id and task_id == exclude_task_id:
            continue
        if task.get('status') not in DEVICE_BUSY_STATUSES:
            continue
        for device_id in task.get('devices', []):
            busy[device_id] = {
                'task_id': task_id,
                'status': task.get('status'),
            }
    return busy


def is_device_online(device_id):
    """设备是否仍在线（已注册且近期有心跳）。忙设备允许更长静默。"""
    with devices_lock:
        device = devices.get(device_id)
        if not device:
            return False
        age = time.time() - device.get('last_seen', 0)
    if age < DEVICE_TIMEOUT:
        return True
    busy_map = get_device_busy_map()
    if device_id in busy_map and age < DEVICE_BUSY_TIMEOUT:
        return True
    return False


def is_lab_known_device(device_id):
    """实验室已映射 AVD 的设备，即使 TestApp 暂时掉线也可交给前置准备拉起。"""
    if not device_id:
        return False
    if is_device_online(device_id):
        return True
    try:
        avd_map = load_device_avd_map() or {}
        if avd_map.get(device_id):
            return True
    except Exception:
        pass
    with devices_lock:
        return device_id in devices


def is_adb_visible_device(device_id):
    """USB/本机 ADB 可见（含跳板机 :15037 桥接），不要求 TestApp WS。"""
    if not device_id:
        return False
    try:
        for d in get_adb_visible_devices() or []:
            if d.get('device_id') == device_id:
                return True
    except Exception:
        pass
    return False


def check_devices_available(
    device_ids, exclude_task_id=None, require_online=True, allow_adb=False
):
    """检查设备是否可被新任务/重启任务使用。

    require_online=False：允许 TestApp 暂时离线（DeviceSDK 任务由前置准备自动 wake）。
    allow_adb=True：DeviceSDK ADB 路径，USB 桥可见即可，不要求实验室 AVD 映射。
    """
    if not device_ids:
        return False, '请选择至少一个设备'
    if require_online:
        offline = [device_id for device_id in device_ids if not is_device_online(device_id)]
        if offline:
            return False, f'以下设备未在线或不存在，无法使用: {", ".join(offline)}'
    else:
        unknown = []
        for device_id in device_ids:
            if is_lab_known_device(device_id):
                continue
            if allow_adb and is_adb_visible_device(device_id):
                continue
            unknown.append(device_id)
        if unknown:
            return False, (
                f'以下设备未知（未映射实验室 AVD 且从未上线），无法自动拉起: '
                f'{", ".join(unknown)}'
            )
    busy_map = get_device_busy_map(exclude_task_id)
    conflicts = []
    for device_id in device_ids:
        if device_id in busy_map:
            info = busy_map[device_id]
            conflicts.append(f'{device_id}（任务 {info["task_id"]}）')
    if conflicts:
        return False, f'以下设备正在执行任务，不可选用: {", ".join(conflicts)}'
    return True, ''


def get_online_devices():
    """Get list of online devices (TestApp WebSocket)."""
    busy_map = get_device_busy_map()
    avd_map = load_device_avd_map()
    with devices_lock:
        online = []
        current_time = time.time()
        for device_id, device in devices.items():
            age = current_time - device.get('last_seen', 0)
            busy_info = busy_map.get(device_id)
            # 空闲：5 分钟；忙任务：最长 DEVICE_BUSY_TIMEOUT，避免 GUI 跨 App 时列表消失
            limit = DEVICE_BUSY_TIMEOUT if busy_info else DEVICE_TIMEOUT
            if age < limit:
                info = device.get('info') or {}
                client_app = (
                    info.get('clientApp') or info.get('client_app') or ''
                ).strip().lower()
                avd_name = extract_avd_name(device.get('name'), info) or avd_map.get(
                    device_id, ''
                ) or avd_map.get(device_id.split('__', 1)[0], '')
                display_name = avd_name or device.get('name') or 'Unknown'
                if display_name and _GENERIC_DEVICE_NAME_RE.search(display_name):
                    display_name = avd_name or device_id
                if client_app == 'devicesdkdemo':
                    kind = 'devicesdk'
                    kind_label = 'DeviceSDK Demo'
                else:
                    kind = 'testapp'
                    kind_label = 'TestApp'
                online.append({
                    'device_id': device_id,
                    'device_name': display_name,
                    'avd_name': avd_name or '',
                    'manufacturer': info.get('manufacturer', ''),
                    'model': info.get('model', ''),
                    'android_version': info.get('android_version', '') or info.get('androidVersion', ''),
                    'last_seen': device.get('last_seen', 0),
                    'busy': bool(busy_info),
                    'busy_task_id': busy_info.get('task_id') if busy_info else None,
                    'busy_status': busy_info.get('status') if busy_info else None,
                    'client_app': client_app,
                    'kind': kind,
                    'kind_label': kind_label,
                    'channel': 'websocket',
                    'channel_label': 'WebSocket /ws/device',
                })
        return online


def _resolve_adb_bin():
    """Locate platform-tools adb on jump host / local."""
    candidates = [
        os.environ.get('ADB_BIN') or '',
        os.environ.get('ADB') or '',
        '/opt/android-sdk/platform-tools/adb',
        str(Path(__file__).resolve().parent / 'tools' / 'platform-tools' / 'adb'),
        shutil.which('adb') or '',
    ]
    for c in candidates:
        if c and os.path.isfile(c) and os.access(c, os.X_OK):
            return c
    return None


def _parse_adb_devices_l(text):
    """Parse `adb devices -l` into list of dicts."""
    rows = []
    for line in (text or '').splitlines():
        line = line.strip()
        if not line or line.lower().startswith('list of devices'):
            continue
        parts = line.split()
        if len(parts) < 2:
            continue
        serial, state = parts[0], parts[1]
        if state != 'device':
            continue
        props = {}
        for token in parts[2:]:
            if ':' in token:
                k, v = token.split(':', 1)
                props[k] = v
        model = props.get('model') or ''
        product = props.get('product') or ''
        device = props.get('device') or ''
        rows.append({
            'serial': serial,
            'state': state,
            'model': model.replace('_', ' '),
            'product': product,
            'device': device,
            'transport_id': props.get('transport_id', ''),
        })
    return rows


def _adb_devices_via(adb_bin, host=None, port=None, timeout=8):
    cmd = [adb_bin]
    if host:
        cmd.extend(['-H', str(host), '-P', str(port)])
    cmd.extend(['devices', '-l'])
    try:
        proc = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            timeout=timeout,
            check=False,
        )
    except (OSError, subprocess.TimeoutExpired) as e:
        logger.debug('adb devices failed (%s:%s): %s', host, port, e)
        return [], str(e)
    out = (proc.stdout or '') + (proc.stderr or '')
    if proc.returncode != 0 and 'devices' not in out.lower():
        return [], out.strip()[:200]
    return _parse_adb_devices_l(proc.stdout or ''), ''


_DEVICESDK_CAPTURE_PKG = 'com.example.devicesdkdemo'
_mp_cache = {}
_mp_cache_lock = threading.Lock()


def _adb_bridge_ports_listening():
    """USB 反向桥端口。光中屏可能不在默认 15037 上，顺带看 15036–15040。"""
    ports = []
    seen = set()
    try:
        env_port = int(os.environ.get('ADB_USB_BRIDGE_PORT', '15037') or '15037')
    except (TypeError, ValueError):
        env_port = 15037
    for port in [env_port, *range(15036, 15041)]:
        if port <= 0 or port in seen:
            continue
        seen.add(port)
        if _remote_adb_port_listening(port):
            ports.append(port)
    return ports


def _adb_locate_serial(serial):
    """在本机 adb 和已监听的 USB 桥上找序列号。返回 (adb, host, port)。"""
    adb_bin = _resolve_adb_bin()
    if not adb_bin or not serial:
        return None
    endpoints = [(None, None)]
    for port in _adb_bridge_ports_listening():
        endpoints.append(('127.0.0.1', port))
    for host, port in endpoints:
        rows, _err = _adb_devices_via(adb_bin, host=host, port=port, timeout=6)
        for row in rows:
            if row.get('serial') == serial:
                return adb_bin, host, port
    return None


def _adb_media_projection_text(adb_bin, serial, host, port, timeout=8):
    cmd = [adb_bin]
    if host:
        cmd.extend(['-H', str(host), '-P', str(port)])
    cmd.extend(['-s', serial, 'shell', 'dumpsys', 'media_projection'])
    try:
        proc = subprocess.run(
            cmd, capture_output=True, text=True, timeout=timeout, check=False,
        )
    except (OSError, subprocess.TimeoutExpired) as ex:
        logger.warning('dumpsys media_projection failed serial=%s: %s', serial, ex)
        return ''
    return (proc.stdout or '') + (proc.stderr or '')


def _devicesdk_projection_active(text):
    for line in (text or '').splitlines():
        if _DEVICESDK_CAPTURE_PKG in line and 'TYPE_SCREEN_CAPTURE' in line:
            return True
    return False


def _devicesdk_projection_state(serial, force=False):
    """on：录屏会话在；off：设备在但没开会话；missing：ADB 看不到这台设备。"""
    now = time.time()
    if not force:
        with _mp_cache_lock:
            hit = _mp_cache.get(serial)
        if hit:
            ts, state = hit
            ttl = 8 if state == 'on' else 20
            if now - ts < ttl:
                return state
    located = _adb_locate_serial(serial)
    if not located:
        state = 'missing'
    else:
        adb_bin, host, port = located
        text = _adb_media_projection_text(adb_bin, serial, host, port)
        state = 'on' if _devicesdk_projection_active(text) else 'off'
    with _mp_cache_lock:
        _mp_cache[serial] = (time.time(), state)
    return state


def devicesdk_screen_capture_problem(device_ids, force=False):
    """9527 不因 DeviceSDK 录屏未开而拦截建任务、定时启动、重启或重跑用例。"""
    return None


def _screen_capture_block_response(device_ids):
    msg = devicesdk_screen_capture_problem(device_ids, force=True)
    if not msg:
        return None
    logger.warning('DeviceSDK screen capture blocked: %s', msg.replace('\n', ' | '))
    return jsonify({
        'success': False,
        'screen_capture_ok': False,
        'message': msg,
    }), 400


def _adb_shell_getprop(adb_bin, serial, prop, host=None, port=None, timeout=4):
    cmd = [adb_bin]
    if host:
        cmd.extend(['-H', str(host), '-P', str(port)])
    if serial:
        cmd.extend(['-s', serial])
    cmd.extend(['shell', 'getprop', prop])
    try:
        proc = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout, check=False)
    except (OSError, subprocess.TimeoutExpired):
        return ''
    return ((proc.stdout or '') + (proc.stderr or '')).strip().splitlines()[0].strip() if (proc.stdout or proc.stderr) else ''


def _adb_shell_settings_get(adb_bin, serial, namespace, key, host=None, port=None, timeout=4):
    cmd = [adb_bin]
    if host:
        cmd.extend(['-H', str(host), '-P', str(port)])
    if serial:
        cmd.extend(['-s', serial])
    cmd.extend(['shell', 'settings', 'get', namespace, key])
    try:
        proc = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout, check=False)
    except (OSError, subprocess.TimeoutExpired):
        return ''
    val = ((proc.stdout or '')).strip().splitlines()
    if not val:
        return ''
    s = val[0].strip()
    if s.lower() in ('null', 'none', ''):
        return ''
    return s


_ADB_NAME_CACHE = {}  # serial -> (expire_ts, info_dict)
_ADB_NAME_CACHE_TTL = 60.0


def _adb_enrich_device_info(adb_bin, serial, host=None, port=None, fallback_model=''):
    """拉手机可读名称：营销名 / 用户设备名 / 品牌+型号。"""
    now = time.time()
    cached = _ADB_NAME_CACHE.get(serial)
    if cached and cached[0] > now:
        return cached[1]

    market = _adb_shell_getprop(adb_bin, serial, 'ro.product.marketname', host=host, port=port)
    model = _adb_shell_getprop(adb_bin, serial, 'ro.product.model', host=host, port=port) or fallback_model
    brand = _adb_shell_getprop(adb_bin, serial, 'ro.product.brand', host=host, port=port)
    manufacturer = _adb_shell_getprop(adb_bin, serial, 'ro.product.manufacturer', host=host, port=port)
    device_name = _adb_shell_settings_get(
        adb_bin, serial, 'global', 'device_name', host=host, port=port
    )
    # 优先：用户设置的设备名 → 营销名 → 品牌+型号 → 型号
    display = device_name or market
    if not display:
        if brand and model and brand.lower() not in model.lower():
            display = f'{brand} {model}'.strip()
        else:
            display = model or brand or serial
    info = {
        'device_name': display,
        'model': model or fallback_model or '',
        'manufacturer': manufacturer or brand or '',
        'market_name': market or '',
        'user_device_name': device_name or '',
    }
    _ADB_NAME_CACHE[serial] = (now + _ADB_NAME_CACHE_TTL, info)
    return info


def get_adb_visible_devices():
    """Enumerate ADB devices on jump-host local adb + USB reverse bridge."""
    adb_bin = _resolve_adb_bin()
    if not adb_bin:
        return []

    seen = {}
    # 1) 本机默认 adb server（云机 / 直接插在跳板机上的 USB）
    local_rows, _ = _adb_devices_via(adb_bin)
    for row in local_rows:
        serial = row['serial']
        info = _adb_enrich_device_info(
            adb_bin, serial, fallback_model=row.get('model') or ''
        )
        seen[serial] = {
            'device_id': serial,
            'device_name': info['device_name'],
            'manufacturer': info.get('manufacturer') or '',
            'model': info.get('model') or row['model'] or row['device'] or '',
            'android_version': '',
            'market_name': info.get('market_name') or '',
            'last_seen': time.time(),
            'busy': False,
            'kind': 'adb',
            'kind_label': 'ADB本机',
            'channel': 'local',
            'channel_label': '跳板机 adb',
            'product': row.get('product') or '',
            'adb_host': '',
            'adb_port': 0,
        }

    # 2) USB 桥接：本机电脑 USB → SSH 反向到跳板机 :15037
    bridge_port = int(os.environ.get('ADB_USB_BRIDGE_PORT', '15037') or '15037')
    if bridge_port > 0:
        bridge_rows, err = _adb_devices_via(
            adb_bin, host='127.0.0.1', port=bridge_port, timeout=5
        )
        if err and not bridge_rows:
            logger.debug('USB bridge adb :%s unreachable: %s', bridge_port, err)
        for row in bridge_rows:
            serial = row['serial']
            info = _adb_enrich_device_info(
                adb_bin,
                serial,
                host='127.0.0.1',
                port=bridge_port,
                fallback_model=row.get('model') or '',
            )
            entry = {
                'device_id': serial,
                'device_name': info['device_name'],
                'manufacturer': info.get('manufacturer') or '',
                'model': info.get('model') or row['model'] or row['device'] or '',
                'android_version': '',
                'market_name': info.get('market_name') or '',
                'last_seen': time.time(),
                'busy': False,
                'kind': 'adb_usb_bridge',
                'kind_label': 'USB桥接',
                'channel': f'127.0.0.1:{bridge_port}',
                'channel_label': f'USB→本机→跳板机:{bridge_port}',
                'product': row.get('product') or '',
                'adb_host': '127.0.0.1',
                'adb_port': bridge_port,
            }
            prev = seen.get(serial)
            if prev and prev.get('kind') == 'adb':
                entry['also_local'] = True
            seen[serial] = entry

    return list(seen.values())


def _remote_adb_bridge_port():
    try:
        return int(os.environ.get('ADB_USB_BRIDGE_PORT', '15037') or '15037')
    except (TypeError, ValueError):
        return 15037


def _remote_adb_port_listening(port, host='127.0.0.1'):
    """True if something accepts TCP on host:port (bridge reverse listener)."""
    if not port or port <= 0:
        return False
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.settimeout(0.8)
    try:
        return sock.connect_ex((host, int(port))) == 0
    except OSError:
        return False
    finally:
        try:
            sock.close()
        except OSError:
            pass


def _remote_adb_status_payload():
    """Bridge + device visibility for the remote-ADB page."""
    bridge_port = _remote_adb_bridge_port()
    adb_bin = _resolve_adb_bin()
    listening = _remote_adb_port_listening(bridge_port) if bridge_port > 0 else False
    devices = []
    errors = []
    agents = []
    agent_devices = []
    if not adb_bin:
        errors.append('未找到 adb（请安装 /opt/android-sdk/platform-tools/adb）')
    else:
        try:
            devices = get_adb_visible_devices()
        except Exception as e:
            errors.append(f'枚举设备失败: {e}')
    try:
        from remote_adb import agent_registry as _radb_reg
        agents = _radb_reg.online_agents()
        agent_devices = _radb_reg.registered_devices()
    except Exception as e:
        errors.append(f'电脑助手注册表不可用: {e}')
    # Prefer agent-published devices for the page device_count when present
    shown = agent_devices or devices
    bridge_devices = [d for d in devices if d.get('kind') == 'adb_usb_bridge']
    local_devices = [d for d in devices if d.get('kind') == 'adb']
    agent_listening = any(
        _remote_adb_port_listening(int(a.get('bridge_port') or 0))
        for a in agents
        if a.get('bridge_port')
    )
    any_listening = listening or agent_listening
    hint = ''
    if not agents:
        hint = (
            '无在线电脑助手。请在插着手机的电脑运行 '
            'static/netdisk/start_prepare_agent_9527.bat，再点页面「刷新 / 一键准备」。'
        )
    elif not shown:
        hint = '助手已上线但未上报 device。请点「一键准备」并确认 USB 调试已授权。'
    return {
        'success': True,
        'adb_bin': adb_bin or '',
        'bridge_port': bridge_port,
        'bridge_listening': any_listening,
        'device_count': len(shown),
        'bridge_device_count': len(bridge_devices),
        'local_device_count': len(local_devices),
        'agent_count': len(agents),
        'agents': agents,
        'devices': shown,
        'errors': errors,
        'hint': hint,
        'bridge_command': (
            'REMOTE_ADB_SERVER=http://124.71.69.102:9527 '
            'python tools/prepare_agent.py'
        ),
    }


def _remote_adb_find_device(serial):
    serial = (serial or '').strip()
    if not serial:
        return None
    for d in get_adb_visible_devices() or []:
        if d.get('device_id') == serial or d.get('serial') == serial:
            return d
    return None


# ========== ADB 实时截图测试（首页按钮，每 3 秒一帧，历史+压缩） ==========
_ADB_SHOT = {
    'lock': threading.Lock(),
    'running': False,
    'thread': None,
    'serial': '',
    'channel': '',  # 'local' | '127.0.0.1:15037'
    'host': None,
    'port': None,
    'interval_sec': 3.0,
    'seq': 0,
    'jpeg': None,  # latest compressed frame bytes
    'last_ok_at': 0.0,
    'last_error': '',
    'last_bytes': 0,       # compressed size
    'last_raw_bytes': 0,   # raw png size from adb
    'captures': 0,
    'frames': [],          # [{seq, path, bytes, ts, raw_bytes}]
    'max_frames': 80,
    'max_width': 720,
    'jpeg_quality': 70,
    'dir': '',
}


def _adb_shot_dir():
    d = _ADB_SHOT.get('dir') or ''
    if d and os.path.isdir(d):
        return d
    base = os.path.join(
        app.config.get('TESTRESULTS_FOLDER') or os.path.join(os.path.dirname(__file__), 'testresults'),
        '_adb_shot',
    )
    os.makedirs(base, exist_ok=True)
    _ADB_SHOT['dir'] = base
    return base


def _adb_shot_compress(png_bytes, max_width=720, quality=70):
    """PNG → JPEG 缩放压缩，控制文件大小。无 Pillow 时回退原 PNG。"""
    try:
        from PIL import Image
    except Exception:
        return png_bytes, 'image/png', '.png'
    img = Image.open(io.BytesIO(png_bytes))
    if img.mode not in ('RGB', 'L'):
        img = img.convert('RGB')
    elif img.mode == 'L':
        img = img.convert('RGB')
    w, h = img.size
    mw = int(max_width or 0)
    if mw > 0 and w > mw:
        nh = max(1, int(h * (mw / float(w))))
        img = img.resize((mw, nh), Image.Resampling.LANCZOS if hasattr(Image, 'Resampling') else Image.LANCZOS)
    out = io.BytesIO()
    q = max(30, min(int(quality or 70), 95))
    img.save(out, format='JPEG', quality=q, optimize=True)
    return out.getvalue(), 'image/jpeg', '.jpg'


def _adb_shot_status_unlocked():
    frames = list(_ADB_SHOT.get('frames') or [])
    return {
        'running': bool(_ADB_SHOT['running']),
        'serial': _ADB_SHOT.get('serial') or '',
        'channel': _ADB_SHOT.get('channel') or '',
        'interval_sec': float(_ADB_SHOT.get('interval_sec') or 3),
        'seq': int(_ADB_SHOT.get('seq') or 0),
        'last_ok_at': float(_ADB_SHOT.get('last_ok_at') or 0),
        'last_error': _ADB_SHOT.get('last_error') or '',
        'last_bytes': int(_ADB_SHOT.get('last_bytes') or 0),
        'last_raw_bytes': int(_ADB_SHOT.get('last_raw_bytes') or 0),
        'captures': int(_ADB_SHOT.get('captures') or 0),
        'has_frame': bool(_ADB_SHOT.get('jpeg') or frames),
        'max_width': int(_ADB_SHOT.get('max_width') or 720),
        'jpeg_quality': int(_ADB_SHOT.get('jpeg_quality') or 70),
        'frame_count': len(frames),
        'frames': [
            {
                'seq': f['seq'],
                'bytes': f['bytes'],
                'raw_bytes': f.get('raw_bytes') or 0,
                'ts': f['ts'],
                'url': f"/api/adb-shot/frame/{f['seq']}.jpg",
            }
            for f in frames
        ],
    }


def _adb_shot_pick_target(serial: str = ''):
    """Prefer USB bridge device; fall back to local adb."""
    devices = get_adb_visible_devices()
    if not devices:
        return None, '当前没有 ADB 可见设备（检查 USB 隧道 :15037 或跳板机 adb）'
    serial = (serial or '').strip()
    chosen = None
    if serial:
        for d in devices:
            if d.get('device_id') == serial:
                chosen = d
                break
        if not chosen:
            return None, f'指定序列号不在 ADB 列表: {serial}'
    else:
        for d in devices:
            if d.get('kind') == 'adb_usb_bridge':
                chosen = d
                break
        if not chosen:
            chosen = devices[0]
    channel = chosen.get('channel') or 'local'
    host, port = None, None
    if channel != 'local' and ':' in channel:
        host, port_s = channel.rsplit(':', 1)
        try:
            port = int(port_s)
        except ValueError:
            host, port = '127.0.0.1', int(os.environ.get('ADB_USB_BRIDGE_PORT', '15037') or 15037)
    return {
        'serial': chosen['device_id'],
        'channel': channel,
        'host': host,
        'port': port,
        'label': chosen.get('kind_label') or '',
        'name': chosen.get('device_name') or chosen['device_id'],
    }, ''


def _adb_shot_capture_once(adb_bin, serial, host=None, port=None, timeout=25):
    cmd = [adb_bin]
    if host:
        cmd.extend(['-H', str(host), '-P', str(port)])
    if serial:
        cmd.extend(['-s', serial])
    cmd.extend(['exec-out', 'screencap', '-p'])
    proc = subprocess.run(
        cmd,
        capture_output=True,
        timeout=timeout,
        check=False,
    )
    data = proc.stdout or b''
    if proc.returncode != 0 or len(data) < 100 or data[:8] != b'\x89PNG\r\n\x1a\n':
        err = (proc.stderr or b'').decode('utf-8', 'replace').strip()
        if not err and data:
            err = f'bad png header size={len(data)}'
        if not err:
            err = f'adb screencap failed rc={proc.returncode}'
        raise RuntimeError(err)
    return data


def _adb_shot_store_frame(png_bytes):
    """Compress, persist to disk, update latest + history. Caller holds no lock."""
    with _ADB_SHOT['lock']:
        max_w = int(_ADB_SHOT.get('max_width') or 720)
        quality = int(_ADB_SHOT.get('jpeg_quality') or 70)
        max_frames = int(_ADB_SHOT.get('max_frames') or 80)
        seq = int(_ADB_SHOT.get('seq') or 0) + 1
    compressed, mime, ext = _adb_shot_compress(png_bytes, max_width=max_w, quality=quality)
    out_dir = _adb_shot_dir()
    path = os.path.join(out_dir, f'{seq:05d}{ext}')
    with open(path, 'wb') as f:
        f.write(compressed)
    meta = {
        'seq': seq,
        'path': path,
        'bytes': len(compressed),
        'raw_bytes': len(png_bytes),
        'ts': time.time(),
        'mime': mime,
    }
    with _ADB_SHOT['lock']:
        _ADB_SHOT['jpeg'] = compressed
        _ADB_SHOT['seq'] = seq
        _ADB_SHOT['last_ok_at'] = meta['ts']
        _ADB_SHOT['last_bytes'] = meta['bytes']
        _ADB_SHOT['last_raw_bytes'] = meta['raw_bytes']
        _ADB_SHOT['captures'] = int(_ADB_SHOT.get('captures') or 0) + 1
        _ADB_SHOT['last_error'] = ''
        frames = list(_ADB_SHOT.get('frames') or [])
        frames.append(meta)
        while len(frames) > max_frames:
            old = frames.pop(0)
            try:
                if old.get('path') and os.path.isfile(old['path']):
                    os.remove(old['path'])
            except OSError:
                pass
        _ADB_SHOT['frames'] = frames
    return meta


def _adb_shot_loop():
    adb_bin = _resolve_adb_bin()
    if not adb_bin:
        with _ADB_SHOT['lock']:
            _ADB_SHOT['running'] = False
            _ADB_SHOT['last_error'] = '找不到 adb 可执行文件'
        return
    logger.info(
        'ADB shot loop start serial=%s channel=%s max_w=%s q=%s',
        _ADB_SHOT.get('serial'),
        _ADB_SHOT.get('channel'),
        _ADB_SHOT.get('max_width'),
        _ADB_SHOT.get('jpeg_quality'),
    )
    while True:
        with _ADB_SHOT['lock']:
            if not _ADB_SHOT['running']:
                break
            serial = _ADB_SHOT.get('serial') or ''
            host = _ADB_SHOT.get('host')
            port = _ADB_SHOT.get('port')
            interval = float(_ADB_SHOT.get('interval_sec') or 3)
        t0 = time.time()
        try:
            png = _adb_shot_capture_once(adb_bin, serial, host=host, port=port)
            _adb_shot_store_frame(png)
        except Exception as ex:
            logger.warning('ADB shot capture failed: %s', ex)
            with _ADB_SHOT['lock']:
                _ADB_SHOT['last_error'] = str(ex)[:300]
        elapsed = time.time() - t0
        sleep_for = max(0.2, interval - elapsed)
        end = time.time() + sleep_for
        while time.time() < end:
            with _ADB_SHOT['lock']:
                if not _ADB_SHOT['running']:
                    break
            time.sleep(0.2)
    logger.info('ADB shot loop stopped')


def adb_shot_start(
    serial: str = '',
    interval_sec: float = 3.0,
    max_width: int = 720,
    jpeg_quality: int = 70,
    clear_history: bool = False,
):
    target, err = _adb_shot_pick_target(serial)
    if err:
        return False, err, _adb_shot_status_unlocked()
    with _ADB_SHOT['lock']:
        if _ADB_SHOT['running']:
            return True, 'already running', _adb_shot_status_unlocked()
        _ADB_SHOT['serial'] = target['serial']
        _ADB_SHOT['channel'] = target['channel']
        _ADB_SHOT['host'] = target['host']
        _ADB_SHOT['port'] = target['port']
        _ADB_SHOT['interval_sec'] = float(interval_sec or 3)
        _ADB_SHOT['max_width'] = max(240, min(int(max_width or 720), 1440))
        _ADB_SHOT['jpeg_quality'] = max(30, min(int(jpeg_quality or 70), 95))
        _ADB_SHOT['running'] = True
        _ADB_SHOT['last_error'] = ''
        if clear_history:
            for f in list(_ADB_SHOT.get('frames') or []):
                try:
                    if f.get('path') and os.path.isfile(f['path']):
                        os.remove(f['path'])
                except OSError:
                    pass
            _ADB_SHOT['frames'] = []
            _ADB_SHOT['jpeg'] = None
            _ADB_SHOT['seq'] = 0
            _ADB_SHOT['captures'] = 0
        th = threading.Thread(target=_adb_shot_loop, daemon=True, name='adb-shot-loop')
        _ADB_SHOT['thread'] = th
        th.start()
        st = _adb_shot_status_unlocked()
    return True, f"started {target['name']} via {target['channel']}", st


def adb_shot_stop():
    with _ADB_SHOT['lock']:
        _ADB_SHOT['running'] = False
        st = _adb_shot_status_unlocked()
    return True, 'stopped', st


def adb_shot_clear_history():
    with _ADB_SHOT['lock']:
        for f in list(_ADB_SHOT.get('frames') or []):
            try:
                if f.get('path') and os.path.isfile(f['path']):
                    os.remove(f['path'])
            except OSError:
                pass
        _ADB_SHOT['frames'] = []
        _ADB_SHOT['jpeg'] = None
        st = _adb_shot_status_unlocked()
    return True, 'cleared', st


# ========== WS 实时截图测试（首页按钮，优先 TestApp MediaProjection /ws/device） ==========
_WS_SHOT = {
    'lock': threading.Lock(),
    'running': False,
    'thread': None,
    'device_id': '',
    'device_name': '',
    'client_app': '',
    'interval_sec': 3.0,
    'seq': 0,
    'jpeg': None,
    'last_ok_at': 0.0,
    'last_error': '',
    'last_bytes': 0,
    'last_raw_bytes': 0,
    'captures': 0,
    'frames': [],
    'max_frames': 80,
    'max_width': 720,
    'jpeg_quality': 70,
    'dir': '',
    'pending': {},  # requestId -> threading.Event
    'pending_payload': {},  # requestId -> upload dict
}


def _ws_shot_dir():
    d = _WS_SHOT.get('dir') or ''
    if d and os.path.isdir(d):
        return d
    base = os.path.join(
        app.config.get('TESTRESULTS_FOLDER') or os.path.join(os.path.dirname(__file__), 'testresults'),
        '_ws_shot',
    )
    os.makedirs(base, exist_ok=True)
    _WS_SHOT['dir'] = base
    return base


def _ws_shot_status_unlocked():
    frames = list(_WS_SHOT.get('frames') or [])
    return {
        'running': bool(_WS_SHOT['running']),
        'device_id': _WS_SHOT.get('device_id') or '',
        'device_name': _WS_SHOT.get('device_name') or '',
        'client_app': _WS_SHOT.get('client_app') or '',
        'interval_sec': float(_WS_SHOT.get('interval_sec') or 3),
        'seq': int(_WS_SHOT.get('seq') or 0),
        'last_ok_at': float(_WS_SHOT.get('last_ok_at') or 0),
        'last_error': _WS_SHOT.get('last_error') or '',
        'last_bytes': int(_WS_SHOT.get('last_bytes') or 0),
        'last_raw_bytes': int(_WS_SHOT.get('last_raw_bytes') or 0),
        'captures': int(_WS_SHOT.get('captures') or 0),
        'has_frame': bool(_WS_SHOT.get('jpeg') or frames),
        'max_width': int(_WS_SHOT.get('max_width') or 720),
        'jpeg_quality': int(_WS_SHOT.get('jpeg_quality') or 70),
        'frame_count': len(frames),
        'frames': [
            {
                'seq': f['seq'],
                'bytes': f['bytes'],
                'raw_bytes': f.get('raw_bytes') or 0,
                'ts': f['ts'],
                'url': f"/api/ws-shot/frame/{f['seq']}.jpg",
            }
            for f in frames
        ],
    }


def _ws_shot_pick_target(device_id: str = ''):
    """Prefer TestApp WS client (MediaProjection); fall back to any online WS device."""
    with devices_lock:
        candidates = []
        for did, dev in devices.items():
            if not dev.get('ws'):
                continue
            info = dev.get('info') or {}
            client_app = (
                info.get('clientApp') or info.get('client_app') or ''
            ).strip().lower()
            candidates.append({
                'device_id': did,
                'name': dev.get('name') or did,
                'client_app': client_app,
                'is_testapp': client_app == 'testapp',
            })
    if not candidates:
        return None, '当前没有 WebSocket 在线设备（请打开 TestApp、开启录屏权限并保持 WS 连接）'
    device_id = (device_id or '').strip()
    if device_id:
        for c in candidates:
            if c['device_id'] == device_id:
                return c, ''
        return None, f'指定 deviceId 不在 WS 列表: {device_id}'
    for c in candidates:
        if c['is_testapp']:
            return c, ''
    return candidates[0], ''


def _ws_shot_decode_upload(data):
    import base64

    img_data = data.get('data') or ''
    if not img_data:
        return None
    if ',' in img_data:
        img_data = img_data.split(',', 1)[1]
    try:
        return base64.b64decode(img_data)
    except Exception as ex:
        logger.warning('WS shot decode failed: %s', ex)
        return None


def _ws_shot_store_bytes(raw_bytes):
    if not raw_bytes:
        return False
    max_w = int(_WS_SHOT.get('max_width') or 720)
    quality = int(_WS_SHOT.get('jpeg_quality') or 70)
    compressed, mime, ext = _adb_shot_compress(raw_bytes, max_width=max_w, quality=quality)
    out_dir = _ws_shot_dir()
    with _WS_SHOT['lock']:
        seq = int(_WS_SHOT.get('seq') or 0) + 1
        fname = f'frame_{seq:05d}{ext}'
        path = os.path.join(out_dir, fname)
        with open(path, 'wb') as f:
            f.write(compressed)
        frame = {
            'seq': seq,
            'path': path,
            'bytes': len(compressed),
            'raw_bytes': len(raw_bytes),
            'ts': time.time(),
            'mime': mime,
        }
        frames = list(_WS_SHOT.get('frames') or [])
        frames.append(frame)
        max_frames = int(_WS_SHOT.get('max_frames') or 80)
        while len(frames) > max_frames:
            old = frames.pop(0)
            try:
                if old.get('path') and os.path.isfile(old['path']):
                    os.remove(old['path'])
            except OSError:
                pass
        _WS_SHOT['frames'] = frames
        _WS_SHOT['seq'] = seq
        _WS_SHOT['jpeg'] = compressed
        _WS_SHOT['last_ok_at'] = time.time()
        _WS_SHOT['last_error'] = ''
        _WS_SHOT['last_bytes'] = len(compressed)
        _WS_SHOT['last_raw_bytes'] = len(raw_bytes)
        _WS_SHOT['captures'] = int(_WS_SHOT.get('captures') or 0) + 1
    return True


def _ws_shot_handle_upload(upload_device_id, data):
    request_id = (data.get('requestId') or '').strip()
    with _WS_SHOT['lock']:
        target_id = _WS_SHOT.get('device_id') or ''
        running = bool(_WS_SHOT.get('running'))
    if upload_device_id:
        with devices_lock:
            dev = devices.get(upload_device_id)
            if dev is not None:
                dev['last_seen'] = time.time()
    if request_id:
        with _WS_SHOT['lock']:
            ev = _WS_SHOT['pending'].get(request_id)
            if ev is not None:
                _WS_SHOT['pending_payload'][request_id] = data
                ev.set()
    if not running:
        return
    if target_id and upload_device_id and upload_device_id != target_id:
        return
    raw = _ws_shot_decode_upload(data)
    if raw:
        _ws_shot_store_bytes(raw)
        logger.info(
            'WS shot frame stored from %s request=%s bytes=%d',
            upload_device_id or '?',
            request_id or '-',
            len(raw),
        )


def _ws_shot_handle_error(upload_device_id, data):
    request_id = (data.get('requestId') or '').strip()
    message = (data.get('message') or data.get('error') or 'WS 截图失败').strip()
    if upload_device_id:
        with devices_lock:
            dev = devices.get(upload_device_id)
            if dev is not None:
                dev['last_seen'] = time.time()
    with _WS_SHOT['lock']:
        if _WS_SHOT.get('running'):
            _WS_SHOT['last_error'] = message
        ev = _WS_SHOT['pending'].get(request_id) if request_id else None
        if ev is not None:
            ev.set()
    logger.info(
        'WS shot error from %s request=%s: %s',
        upload_device_id or '?',
        request_id or '-',
        message,
    )


def _ws_shot_send_capture(device_id, request_id):
    with devices_lock:
        dev = devices.get(device_id)
        ws = dev.get('ws') if dev else None
    if not ws:
        return False, 'device websocket missing'
    try:
        payload = {
            'type': 'ws_shot_capture',
            'requestId': request_id,
            'maxWidth': int(_WS_SHOT.get('max_width') or 720),
            'jpegQuality': int(_WS_SHOT.get('jpeg_quality') or 70),
        }
        ws.send(json.dumps(payload))
        return True, ''
    except Exception as ex:
        return False, str(ex)


def _ws_shot_wait_response(request_id, timeout_sec):
    """Legacy helper; loop registers pending before send."""
    ev = threading.Event()
    with _WS_SHOT['lock']:
        _WS_SHOT['pending'][request_id] = ev
    ok = ev.wait(timeout=max(0.5, float(timeout_sec or 2.5)))
    with _WS_SHOT['lock']:
        _WS_SHOT['pending'].pop(request_id, None)
        _WS_SHOT['pending_payload'].pop(request_id, None)
    return ok


def _ws_shot_loop():
    logger.info('WS shot loop started')
    while True:
        with _WS_SHOT['lock']:
            if not _WS_SHOT['running']:
                break
            device_id = _WS_SHOT.get('device_id') or ''
            interval = float(_WS_SHOT.get('interval_sec') or 3)
        if not device_id:
            with _WS_SHOT['lock']:
                _WS_SHOT['last_error'] = '未选择 WS 设备'
            time.sleep(1.0)
            continue
        request_id = uuid.uuid4().hex[:12]
        with _WS_SHOT['lock']:
            caps_before = int(_WS_SHOT.get('captures') or 0)
            _WS_SHOT['pending'][request_id] = threading.Event()
        ev = _WS_SHOT['pending'][request_id]
        try:
            sent, err = _ws_shot_send_capture(device_id, request_id)
            if not sent:
                with _WS_SHOT['lock']:
                    _WS_SHOT['last_error'] = err or '发送 ws_shot_capture 失败（WS 可能已断开）'
                logger.warning('WS shot capture send failed: %s', err)
            else:
                wait_sec = max(4.0, min(interval + 2.0, 12.0))
                ev.wait(timeout=wait_sec)
                with _WS_SHOT['lock']:
                    caps_after = int(_WS_SHOT.get('captures') or 0)
                    if caps_after <= caps_before and not _WS_SHOT.get('last_error'):
                        _WS_SHOT['last_error'] = (
                            '等待 ws_shot_upload 超时：请保持 TestApp 在线并已授权录屏'
                        )
        finally:
            with _WS_SHOT['lock']:
                _WS_SHOT['pending'].pop(request_id, None)
                _WS_SHOT['pending_payload'].pop(request_id, None)
        end = time.time() + interval
        while time.time() < end:
            with _WS_SHOT['lock']:
                if not _WS_SHOT['running']:
                    break
            time.sleep(0.2)
    logger.info('WS shot loop stopped')


def ws_shot_start(
    device_id: str = '',
    interval_sec: float = 3.0,
    max_width: int = 720,
    jpeg_quality: int = 70,
    clear_history: bool = False,
):
    target, err = _ws_shot_pick_target(device_id)
    if err:
        return False, err, _ws_shot_status_unlocked()
    with _WS_SHOT['lock']:
        if _WS_SHOT['running']:
            return True, 'already running', _ws_shot_status_unlocked()
        _WS_SHOT['device_id'] = target['device_id']
        _WS_SHOT['device_name'] = target['name']
        _WS_SHOT['client_app'] = target.get('client_app') or ''
        _WS_SHOT['interval_sec'] = float(interval_sec or 3)
        _WS_SHOT['max_width'] = max(240, min(int(max_width or 720), 1440))
        _WS_SHOT['jpeg_quality'] = max(30, min(int(jpeg_quality or 70), 95))
        _WS_SHOT['running'] = True
        _WS_SHOT['last_error'] = ''
        if clear_history:
            for f in list(_WS_SHOT.get('frames') or []):
                try:
                    if f.get('path') and os.path.isfile(f['path']):
                        os.remove(f['path'])
                except OSError:
                    pass
            _WS_SHOT['frames'] = []
            _WS_SHOT['jpeg'] = None
            _WS_SHOT['seq'] = 0
            _WS_SHOT['captures'] = 0
        th = threading.Thread(target=_ws_shot_loop, daemon=True, name='ws-shot-loop')
        _WS_SHOT['thread'] = th
        th.start()
        st = _ws_shot_status_unlocked()
    label = target.get('client_app') or 'ws'
    return True, f"started {target['name']} ({label}) via WebSocket", st


def ws_shot_stop():
    with _WS_SHOT['lock']:
        _WS_SHOT['running'] = False
        if int(_WS_SHOT.get('captures') or 0) > 0:
            _WS_SHOT['last_error'] = ''
        st = _ws_shot_status_unlocked()
    return True, 'stopped', st


def ws_shot_clear_history():
    with _WS_SHOT['lock']:
        for f in list(_WS_SHOT.get('frames') or []):
            try:
                if f.get('path') and os.path.isfile(f['path']):
                    os.remove(f['path'])
            except OSError:
                pass
        _WS_SHOT['frames'] = []
        _WS_SHOT['jpeg'] = None
        st = _ws_shot_status_unlocked()
    return True, 'cleared', st


# ========== 任务用例执行期间 WS 截图（写入 testresults/{task}/{case}/） ==========
CASE_WS_SHOT_INTERVAL_SEC = 3.0
CASE_WS_SHOT_MAX_WIDTH = 720
CASE_WS_SHOT_JPEG_QUALITY = 70


def _case_ws_shot_capture_once(
    device_id,
    max_width=CASE_WS_SHOT_MAX_WIDTH,
    jpeg_quality=CASE_WS_SHOT_JPEG_QUALITY,
    timeout_sec=8.0,
):
    """向 TestApp（优先）发 ws_shot_capture，等待 ws_shot_upload，返回原始/JPEG 字节。"""
    request_id = 'case_' + uuid.uuid4().hex[:10]
    ev = threading.Event()
    with _WS_SHOT['lock']:
        _WS_SHOT['pending'][request_id] = ev
        _WS_SHOT['pending_payload'].pop(request_id, None)
    try:
        with devices_lock:
            dev = devices.get(device_id)
            ws = dev.get('ws') if dev else None
        if not ws:
            return None, 'device websocket missing'
        try:
            ws.send(json.dumps({
                'type': 'ws_shot_capture',
                'requestId': request_id,
                'maxWidth': int(max_width),
                'jpegQuality': int(jpeg_quality),
            }))
        except Exception as ex:
            return None, str(ex)
        if not ev.wait(timeout=max(2.0, float(timeout_sec))):
            return None, '等待 ws_shot_upload 超时'
        with _WS_SHOT['lock']:
            payload = _WS_SHOT['pending_payload'].pop(request_id, None)
        if not payload:
            return None, '无截图数据（可能 TestApp 上报了 ws_shot_error）'
        raw = _ws_shot_decode_upload(payload)
        if not raw:
            return None, '截图解码失败'
        return raw, ''
    finally:
        with _WS_SHOT['lock']:
            _WS_SHOT['pending'].pop(request_id, None)
            _WS_SHOT['pending_payload'].pop(request_id, None)


def _case_ws_shot_save_frame(result_dir, raw_bytes, seq):
    """压缩并写入用例结果目录，返回保存路径。"""
    if not raw_bytes:
        return None
    os.makedirs(result_dir, exist_ok=True)
    compressed, _mime, ext = _adb_shot_compress(
        raw_bytes,
        max_width=CASE_WS_SHOT_MAX_WIDTH,
        quality=CASE_WS_SHOT_JPEG_QUALITY,
    )
    ts_ms = int(time.time() * 1000)
    fname = f'{ts_ms}_ws_shot_{seq:03d}{ext}'
    path = os.path.join(result_dir, fname)
    with open(path, 'wb') as f:
        f.write(compressed)
    return path


def _case_ws_shot_write_result_final(result_dir, last_path):
    """用最后一帧生成 result_final，供 GUI VL 使用。"""
    if not last_path or not os.path.isfile(last_path):
        return None
    if _list_result_final_images(result_dir):
        return None
    ext = os.path.splitext(last_path)[1] or '.jpg'
    dest = os.path.join(result_dir, f'{int(time.time() * 1000)}_result_final{ext}')
    try:
        import shutil
        shutil.copy2(last_path, dest)
        logger.info('case WS shot wrote result_final: %s', dest)
        return dest
    except Exception as ex:
        logger.warning('case WS shot result_final failed: %s', ex)
        return None


def _start_case_ws_shot_loop(task_id, case_id, device_ids, stop_event, interval_sec=None):
    """
    用例执行期间后台每 interval_sec 秒向 TestApp（优先）要一帧，写入 testresults。
    返回 (stop_event, thread)；调用方在用例结束时 set stop_event 并 join。
    """
    interval = float(interval_sec if interval_sec is not None else CASE_WS_SHOT_INTERVAL_SEC)
    interval = max(1.0, interval)
    result_dir = os.path.join(app.config['TESTRESULTS_FOLDER'], task_id, case_id)
    os.makedirs(result_dir, exist_ok=True)
    # 截图目标与执行设备解耦：优先 TestApp MediaProjection
    preferred, _ = _ws_shot_pick_target('')
    if preferred:
        targets = [preferred['device_id']]
    else:
        demo_devices = [d for d in (device_ids or []) if _is_devicesdk_demo_ws_device(d)]
        targets = demo_devices or list(device_ids or [])
    if not targets:
        return stop_event, None

    def _loop():
        seq = 0
        last_path = None
        logger.info(
            'case WS shot loop start task=%s case=%s devices=%s interval=%.1fs',
            task_id, case_id, targets, interval,
        )
        while not stop_event.is_set():
            if is_task_cancelled(task_id):
                break
            # 每轮重新选路，避免 TestApp 重连后仍打到 Demo
            picked, _ = _ws_shot_pick_target('')
            if picked:
                device_id = picked['device_id']
            else:
                device_id = targets[0]
                for did in targets:
                    with devices_lock:
                        if devices.get(did, {}).get('ws'):
                            device_id = did
                            break
            raw, err = _case_ws_shot_capture_once(device_id)
            if raw:
                seq += 1
                try:
                    path = _case_ws_shot_save_frame(result_dir, raw, seq)
                    if path:
                        last_path = path
                        logger.info(
                            'case WS shot saved %s (%d bytes) task=%s case=%s',
                            os.path.basename(path), os.path.getsize(path),
                            task_id, case_id,
                        )
                except Exception as ex:
                    logger.warning('case WS shot save failed: %s', ex)
            else:
                logger.debug(
                    'case WS shot miss task=%s case=%s device=%s: %s',
                    task_id, case_id, device_id, err,
                )
            # 间隔等待，可被 stop 打断
            end = time.time() + interval
            while time.time() < end:
                if stop_event.is_set() or is_task_cancelled(task_id):
                    break
                time.sleep(0.2)
        if last_path:
            _case_ws_shot_write_result_final(result_dir, last_path)
        logger.info(
            'case WS shot loop stop task=%s case=%s frames=%d',
            task_id, case_id, seq,
        )

    th = threading.Thread(
        target=_loop,
        daemon=True,
        name=f'case-ws-shot-{case_id[:24]}',
    )
    th.start()
    return stop_event, th


def is_task_cancelled(task_id):
    with cancelled_tasks_lock:
        return task_id in cancelled_tasks


def should_reject_task_upload(task_id):
    """任务已停止/取消时拒绝设备继续上传截图。"""
    if not task_id:
        return False
    if is_task_cancelled(task_id):
        return True
    task = load_tasks().get(task_id)
    return bool(task and task.get('status') == 'cancelled')


def cancel_task(task_id):
    with cancelled_tasks_lock:
        cancelled_tasks.add(task_id)


def clear_task_cancelled(task_id):
    with cancelled_tasks_lock:
        cancelled_tasks.discard(task_id)


def is_case_cancelled(task_id, filename):
    with cancelled_cases_lock:
        return filename in cancelled_cases.get(task_id, set())


def cancel_case(task_id, filename):
    with cancelled_cases_lock:
        cancelled_cases.setdefault(task_id, set()).add(filename)


def clear_case_cancelled(task_id, filename):
    with cancelled_cases_lock:
        case_set = cancelled_cases.get(task_id)
        if not case_set:
            return
        case_set.discard(filename)
        if not case_set:
            cancelled_cases.pop(task_id, None)


def clear_all_case_cancelled(task_id):
    with cancelled_cases_lock:
        cancelled_cases.pop(task_id, None)


def try_acquire_task_execution(task_id):
    with executing_tasks_lock:
        if task_id in executing_tasks:
            return False
        executing_tasks.add(task_id)
        return True


def release_task_execution(task_id):
    with executing_tasks_lock:
        executing_tasks.discard(task_id)


def is_task_executing(task_id):
    with executing_tasks_lock:
        return task_id in executing_tasks


def enqueue_task_rerun(task_id, filenames):
    """将用例追加到任务重跑队列（去重，保持入队顺序）。返回新加入的文件名列表。"""
    added = []
    with task_run_queues_lock:
        q = task_run_queues.setdefault(task_id, [])
        for f in filenames or []:
            if f and f not in q:
                q.append(f)
                added.append(f)
    return added


def pop_all_queued_reruns(task_id):
    with task_run_queues_lock:
        return list(task_run_queues.pop(task_id, None) or [])


def clear_task_run_queue(task_id):
    with task_run_queues_lock:
        task_run_queues.pop(task_id, None)


def peek_queued_reruns(task_id):
    with task_run_queues_lock:
        return list(task_run_queues.get(task_id) or [])


def remove_from_task_rerun_queue(task_id, filenames):
    filename_set = set(filenames or [])
    if not filename_set:
        return
    with task_run_queues_lock:
        q = task_run_queues.get(task_id) or []
        if not q:
            return
        task_run_queues[task_id] = [f for f in q if f not in filename_set]


def case_ids_to_filenames(task, case_ids):
    """将 case_id 列表映射为任务内的 json 文件名。"""
    files = task.get('files') or []
    file_map = {os.path.splitext(f)[0]: f for f in files}
    result = []
    for case_id in case_ids:
        cid = (case_id or '').strip()
        if not cid:
            continue
        if cid in file_map:
            result.append(file_map[cid])
        elif cid.endswith('.json') and cid in files:
            result.append(cid)
    return result


def get_running_case_in_task(task):
    """返回任务中正在执行的用例进度项。"""
    for item in task.get('case_progress') or []:
        if item.get('status') in ('running', 'finishing', 'analyzing'):
            return item
    return None


def all_task_cases_terminal(task):
    cases = task.get('case_progress') or []
    if not cases:
        return True
    return all(c.get('status') in TERMINAL_CASE_STATUSES for c in cases)


def persist_task_stop(task_id):
    """将任务及未完成用例标记为已取消并持久化（忽略 cancelled 保护）。"""
    tasks_dir = load_tasks()
    if task_id not in tasks_dir:
        return
    task = tasks_dir[task_id]
    if task.get('status') in ('completed', 'cancelled'):
        return
    task['status'] = 'cancelled'
    task['end_time'] = time.time()
    for item in task.get('case_progress', []):
        if item.get('status') in (
            'pending', 'queued', 'running', 'finishing', 'analyzing'
        ):
            item['status'] = 'cancelled'
    apply_task_progress(task)
    task_file = os.path.join(app.config['TASKS_FOLDER'], f"{task_id}.json")
    with open(task_file, 'w', encoding='utf-8') as f:
        json.dump(task, f, indent=2, ensure_ascii=False)


def finalize_task_stop(task_id, device_ids=None):
    """用户停止：立即持久化为 cancelled，释放内存跟踪；保留 cancel 标志供 execute 线程退出。"""
    clear_task_run_queue(task_id)
    persist_task_stop(task_id)
    release_task_execution(task_id)
    with tasks_lock:
        tasks.pop(task_id, None)
    if device_ids:
        with devices_lock:
            for device_id in device_ids:
                if device_id in devices and devices[device_id].get('current_task_id') == task_id:
                    devices[device_id].pop('current_task_id', None)
                    devices[device_id].pop('current_case_id', None)
    logger.info(f"Task {task_id} stop persisted (execute thread may still be winding down)")


def abort_task_execution(task_id):
    """execute 线程结束或 pending 任务停止：持久化取消状态并清理内存跟踪。"""
    clear_task_run_queue(task_id)
    persist_task_stop(task_id)
    release_task_execution(task_id)
    with tasks_lock:
        tasks.pop(task_id, None)
    clear_task_cancelled(task_id)
    logger.info(f"Task {task_id} aborted")


def sleep_interruptible(seconds, task_id=None):
    """可中断 sleep；若 task_id 已取消则提前返回 False。"""
    end = time.time() + seconds
    while time.time() < end:
        if task_id and is_task_cancelled(task_id):
            return False
        time.sleep(min(1.0, end - time.time()))
    return True


def send_stop_test_to_devices(task_id, device_ids, reason='用户停止'):
    """向在线设备下发停止当前测试指令。

    除 task.devices 外，凡 current_task_id 指向本任务的在线 WS 设备也一并通知，
    避免 Demo 重连换 id 后收不到 stop_test。
    """
    targets = []
    seen = set()
    for device_id in list(device_ids or []):
        if device_id and device_id not in seen:
            seen.add(device_id)
            targets.append(device_id)
    with devices_lock:
        for did, info in devices.items():
            if not info.get('ws'):
                continue
            if info.get('current_task_id') != task_id:
                continue
            if did not in seen:
                seen.add(did)
                targets.append(did)

    msg = {'type': 'stop_test', 'taskId': task_id, 'reason': reason}
    payload = json.dumps(msg)
    if not targets:
        logger.warning(f"无在线设备可下发 stop_test: task={task_id}")
        return
    for device_id in targets:
        with devices_lock:
            device_ws = devices.get(device_id, {}).get('ws') if device_id in devices else None
        if not device_ws:
            logger.warning(f"设备 {device_id} 不在线，无法下发 stop_test")
            continue
        try:
            device_ws.send(payload)
            logger.info(f"Sent stop_test to {device_id} for task {task_id} ({reason})")
        except Exception as e:
            logger.error(f"Error sending stop_test to {device_id}: {e}")


def release_stale_task_execution_if_needed(task_id):
    """磁盘任务已结束但 executing_tasks 残留时清锁，便于单条重启。"""
    tasks_dir = load_tasks()
    task = tasks_dir.get(task_id) or {}
    status = task.get('status')
    if status in ACTIVE_TASK_STATUSES:
        return False
    if not is_task_executing(task_id):
        return False
    logger.warning(
        f"Task {task_id} status={status} but still in executing_tasks; releasing stale lock"
    )
    release_task_execution(task_id)
    clear_task_cancelled(task_id)
    return True


def wait_task_execution_released(task_id, timeout_sec=45.0):
    """等待 execute 线程释放执行锁；超时则强制释放。"""
    deadline = time.time() + max(1.0, timeout_sec)
    while time.time() < deadline:
        if not is_task_executing(task_id):
            return True
        time.sleep(0.25)
    if is_task_executing(task_id):
        logger.warning(f"Task {task_id} execute lock not released in {timeout_sec}s; force release")
        release_task_execution(task_id)
        return False
    return True


def interrupt_task_execution_for_rerun(task_id, device_ids, reason='改跑选中用例'):
    """打断当前整批执行，等待线程退出，供随后单条/选中重跑。"""
    cancel_task(task_id)
    send_stop_test_to_devices(task_id, device_ids, reason=reason)
    released = wait_task_execution_released(task_id, timeout_sec=45.0)
    # 旧线程可能已 abort 并 clear cancel；确保重跑前干净
    clear_task_cancelled(task_id)
    clear_all_case_cancelled(task_id)
    return released

def save_task(task):
    """Save task to file (atomic + locked for shard concurrency)."""
    task_id = task['id']
    if is_task_cancelled(task_id):
        return
    task_file = os.path.join(app.config['TASKS_FOLDER'], f"{task_id}.json")
    tmp_file = task_file + '.tmp'
    with task_io_lock:
        with open(tmp_file, 'w', encoding='utf-8') as f:
            json.dump(task, f, indent=2, ensure_ascii=False)
            f.flush()
            os.fsync(f.fileno())
        os.replace(tmp_file, task_file)


def _tasks_dir_signature(tasks_dir):
    """Cheap cache key: per-task file name + mtime."""
    if not os.path.isdir(tasks_dir):
        return ()
    try:
        sig = []
        for name in os.listdir(tasks_dir):
            if not name.endswith('.json'):
                continue
            path = os.path.join(tasks_dir, name)
            sig.append((name, os.path.getmtime(path)))
        return tuple(sorted(sig))
    except OSError:
        return None


def load_tasks():
    """Load all tasks from files (in-memory cache keyed by tasks dir signature)."""
    tasks_dir = app.config['TASKS_FOLDER']
    sig = _tasks_dir_signature(tasks_dir)
    with _tasks_cache_lock:
        if _tasks_mem_cache['data'] is not None and _tasks_mem_cache['sig'] == sig:
            return _tasks_mem_cache['data']

    task_files = {}
    if os.path.exists(tasks_dir):
        with task_io_lock:
            for f in os.listdir(tasks_dir):
                if f.endswith('.json'):
                    try:
                        with open(os.path.join(tasks_dir, f), 'r', encoding='utf-8') as fp:
                            task = json.load(fp)
                            task_files[task['id']] = task
                    except Exception as e:
                        logger.error(f"Error loading task {f}: {e}")

    with _tasks_cache_lock:
        _tasks_mem_cache['data'] = task_files
        _tasks_mem_cache['sig'] = sig
    return task_files

def _is_devicesdk_cross_app_polling_step(step):
    """与 Android isDeviceSdkCrossAppPollingStep 对齐。"""
    if not isinstance(step, dict):
        return False
    step_id = step.get('stepId') or ''
    if step_id.startswith('wait-screenshot'):
        return True
    return step.get('action') == 'screenshot'


def _compute_devicesdk_cross_app_wait_sec(testcase=None):
    """DeviceSDK 跨 App wait-screenshot 轮询阶段额外耗时（秒）。"""
    total = 0.0
    if not isinstance(testcase, dict):
        return total
    for step in testcase.get('steps') or []:
        if not _is_devicesdk_cross_app_polling_step(step):
            continue
        duration_ms = 500
        params = step.get('params') or {}
        try:
            if params.get('duration'):
                duration_ms = int(params['duration'])
        except (TypeError, ValueError):
            pass
        total += (
            duration_ms / 1000.0
            + CASE_SCREENSHOT_DELAY_SEC
            + CASE_CROSS_APP_STEP_OVERHEAD_SEC
        )
    return total


def get_case_timeout_buffer_sec(testcase=None, target_package=None):
    """根据用例步骤数动态计算缓冲时间（秒），与手机端超时公式对齐。"""
    step_count = 0
    cross_app_sec = 0.0
    media_dialog_sec = 0.0
    if isinstance(testcase, dict):
        steps = testcase.get('steps', [])
        if isinstance(steps, list):
            step_count = len(steps)
        if target_package == 'com.example.devicesdkdemo':
            cross_app_sec = _compute_devicesdk_cross_app_wait_sec(testcase)
            media_dialog_sec = CASE_MEDIA_DIALOG_BUFFER_SEC
    return (
        CASE_TIMEOUT_BASE_BUFFER_SEC
        + step_count * CASE_TIMEOUT_PER_STEP_BUFFER_SEC
        + cross_app_sec
        + media_dialog_sec
    )


def get_case_timeout_sec(testset, filename, target_package=None):
    """读取用例 JSON 的 timeout（秒）并加上动态缓冲时间（按目标应用适配后计算）。

    必须与下发给手机的 adapted JSON 使用同一套 timeout 校正
    （_ensure_timeout_covers_wait_steps），否则会出现：
    手机还在跑 wait-screenshot，服务器已按磁盘原始 90s 判定「用例等待超时」。
    """
    default_ms = 60000
    filepath = resolve_testset_path(testset, filename)
    timeout_ms = default_ms
    testcase = None
    adapted = None
    pkg = target_package or EXCEL_DEFAULT_PACKAGE
    if pkg not in EXCEL_PACKAGE_PRESETS:
        pkg = EXCEL_DEFAULT_PACKAGE
    if os.path.exists(filepath):
        try:
            # 与 execute_task 下发路径一致，避免「手机 timeout 已抬高、服务端仍按短时等待」
            adapted = load_adapted_testcase(testset, filename, pkg)
            testcase = adapted
            timeout_ms = int(adapted.get('timeout', default_ms))
            if timeout_ms <= 0:
                timeout_ms = default_ms
        except (ValueError, TypeError, json.JSONDecodeError, FileNotFoundError) as e:
            logger.warning(f"读取用例 timeout 失败 {filename}: {e}")
    base_sec = timeout_ms / 1000
    buffer_sec = get_case_timeout_buffer_sec(adapted or testcase, pkg)
    step_count = len((adapted or testcase or {}).get('steps', []))
    cross_app_sec = (
        _compute_devicesdk_cross_app_wait_sec(adapted or testcase)
        if pkg == 'com.example.devicesdkdemo' else 0.0
    )
    total_sec = base_sec + buffer_sec
    buffer_detail = f"15s + {step_count}步×1s"
    if cross_app_sec > 0:
        buffer_detail += f" + 跨App轮询{cross_app_sec:.0f}s + 录屏弹窗{CASE_MEDIA_DIALOG_BUFFER_SEC}s"
    logger.info(
        f"用例 {filename} 等待超时: {base_sec:.0f}s(JSON/已按wait校正"
        f"{', 含DeviceSDK前置' if pkg == 'com.example.devicesdkdemo' else ''}) + "
        f"{buffer_sec:.0f}s(缓冲: {buffer_detail}) = {total_sec:.0f}s"
    )
    return total_sec


def clear_task_results(task_id):
    """清空任务在 testresults 下的截图、日志与分析结果。"""
    results_dir = os.path.join(app.config['TESTRESULTS_FOLDER'], task_id)
    if os.path.exists(results_dir):
        import shutil
        shutil.rmtree(results_dir)


def clear_case_results(task_id, case_ids):
    """清空指定用例在 testresults 下的结果目录。"""
    import shutil
    for case_id in case_ids:
        case_dir = os.path.join(app.config['TESTRESULTS_FOLDER'], task_id, case_id)
        if os.path.isdir(case_dir):
            shutil.rmtree(case_dir)


def get_case_replies_path(task_id, case_id):
    return os.path.join(app.config['TESTRESULTS_FOLDER'], task_id, case_id, 'replies.json')


def _read_case_replies_file(task_id, case_id):
    path = get_case_replies_path(task_id, case_id)
    if not os.path.isfile(path):
        return []
    try:
        with open(path, 'r', encoding='utf-8') as f:
            data = json.load(f)
        replies = data.get('replies') or []
        return [_normalize_case_reply_item(r) for r in replies]
    except Exception as e:
        logger.warning(f"读取 replies.json 失败 {path}: {e}")
        return []


def load_case_replies(task_id, case_id):
    """读取用例已上报的 Device 回复；缺失时从 TestApp 日志回填。"""
    replies = _read_case_replies_file(task_id, case_id)
    if not case_has_usable_model_replies(replies):
        if ensure_case_replies_from_device_logs(task_id, case_id):
            replies = _read_case_replies_file(task_id, case_id)
    return replies


def _case_reply_text(item):
    """兼容 reply_text / replyText（聚焦流式多次上报）。"""
    if not item:
        return ''
    return (item.get('reply_text') or item.get('replyText') or '').strip()


def _is_usable_model_reply_text(text):
    """是否可作为文本评判依据的模型回答（排除强制结束等占位）。"""
    t = (text or '').strip()
    if not t:
        return False
    if t.startswith('[强制结束]'):
        return False
    if t in ('testapp_force', '(空)', 'null', 'None'):
        return False
    return True


def case_has_usable_model_replies(replies):
    """用例是否已上报可用的模型文本回答。"""
    for item in replies or []:
        if _is_usable_model_reply_text(_case_reply_text(item)):
            return True
    return False


def _usable_replies_in_order(replies):
    """可用回复按时间/轮次排序（Demo 会话 round 可能跨用例累加）。"""
    items = [
        r for r in (replies or [])
        if isinstance(r, dict) and _is_usable_model_reply_text(_case_reply_text(r))
    ]
    items.sort(key=lambda r: (
        r.get('sent_at_ms') or r.get('captured_at') or 0,
        r.get('round') or 0,
    ))
    return items


def pick_reply_for_eval_round(round_no, reply_by_round, usable_ordered, used_ids):
    """优先精确 round；对不上时按顺序把仅有的回复配给本轮预期。

    Demo 复用同一 AISF session 时 round 会 1,2,3… 跨用例累加，导致第 2 条
    用例只有 round=2 的回复，WebTest 按 round=1 取到空串后强制打 0 分。
    """
    rep = reply_by_round.get(round_no) if reply_by_round else None
    if isinstance(rep, dict) and _is_usable_model_reply_text(_case_reply_text(rep)):
        used_ids.add(id(rep))
        return rep
    for item in usable_ordered or []:
        marker = id(item)
        if marker in used_ids:
            continue
        used_ids.add(marker)
        return item
    return {}


_AISF_USER_RE = re.compile(
    r'\[AISF\] 用户 round=(\d+)\s+type=TEXT\s+text=(.*)$'
)
_AISF_FINAL_RE = re.compile(
    r'\[AISF\] 最终回复 round=(\d+)\s+type=(\S+)\s+final=\S+\s+latencyMs=(\d+)\s+text=(.*)$'
)
_AISF_TASK_RE = re.compile(
    r'\[AISF\] 任务完成 round=(\d+)\s+type=(\S+)\s+latencyMs=(\d+)(?:\s+text=(.*))?$'
)


def parse_aisf_replies_from_case_logs(case_dir):
    """从 TestApp device_*.log 解析 AISF 最终回复（WebSocket 断连时仍会写进日志）。"""
    if not case_dir or not os.path.isdir(case_dir):
        return []
    users = {}
    finals = {}
    case_id = os.path.basename(case_dir.rstrip(os.sep))
    for name in os.listdir(case_dir):
        if not name.endswith('.log'):
            continue
        # 不要读 device_live.log：同一台设备会串进上一用例的 AISF 文本
        if name.startswith('device_live'):
            continue
        if name.startswith('device_') and case_id not in name:
            continue
        path = os.path.join(case_dir, name)
        try:
            with open(path, 'r', encoding='utf-8', errors='replace') as f:
                for raw in f:
                    line = raw.rstrip('\n')
                    m = _AISF_USER_RE.search(line)
                    if m:
                        rnd = int(m.group(1))
                        users[rnd] = (m.group(2) or '').strip()
                        finals.pop(rnd, None)
                        continue
                    m = _AISF_FINAL_RE.search(line)
                    if m:
                        finals[int(m.group(1))] = {
                            'round': int(m.group(1)),
                            'output_type': m.group(2),
                            'latency_ms': int(m.group(3)),
                            'reply_text': (m.group(4) or '').strip(),
                            'user_message': '',
                            'is_final': True,
                            'task_completed': False,
                        }
                        continue
                    m = _AISF_TASK_RE.search(line)
                    if m:
                        rnd = int(m.group(1))
                        text = (m.group(4) or '').strip()
                        if rnd not in finals and _is_usable_model_reply_text(text):
                            finals[rnd] = {
                                'round': rnd,
                                'output_type': m.group(2),
                                'latency_ms': int(m.group(3)),
                                'reply_text': text,
                                'user_message': '',
                                'is_final': True,
                                'task_completed': True,
                            }
        except OSError as e:
            logger.warning(f"读取用例日志失败 {path}: {e}")
    items = []
    for rnd, item in sorted(finals.items()):
        item['user_message'] = users.get(rnd, '')
        if _is_usable_model_reply_text(item.get('reply_text')):
            items.append(item)
    return items


def ensure_case_replies_from_device_logs(task_id, case_id):
    """replies.json 无可用文本时，用 TestApp 日志回填。"""
    if not task_id or not case_id:
        return False
    case_dir = os.path.join(app.config['TESTRESULTS_FOLDER'], task_id, case_id)
    parsed = parse_aisf_replies_from_case_logs(case_dir)
    if not parsed:
        return False
    try:
        task = load_tasks().get(task_id) or {}
        testset = task.get('testset')
        if testset:
            testcase = load_adapted_testcase(
                testset, f'{case_id}.json', task.get('target_package'))
            expected = [
                (r.get('user_message') or '').strip()
                for r in extract_case_chat_rounds(testcase)
                if (r.get('user_message') or '').strip()
            ]
            if expected:
                matched = []
                for item in parsed:
                    um = (item.get('user_message') or '').strip()
                    if any(e in um or um in e for e in expected if e):
                        matched.append(item)
                parsed = matched
    except Exception as e:
        logger.warning(f"日志回填匹配提问失败 {task_id}/{case_id}: {e}")
    if not parsed:
        return False
    existing = _read_case_replies_file(task_id, case_id)
    if case_has_usable_model_replies(existing):
        return False
    saved = False
    for item in parsed:
        if save_case_reply(task_id, case_id, item):
            saved = True
    if saved:
        logger.info(
            f"已从 TestApp 日志回填模型回答: task={task_id} case={case_id} rounds={len(parsed)}"
        )
    return saved


def _normalize_case_reply_item(reply_item):
    """统一 replies.json 字段为 snake_case，适配聚焦模式多段合并。"""
    if not reply_item:
        return {}
    text = _case_reply_text(reply_item)
    latency = reply_item.get('latency_ms')
    if latency is None:
        latency = reply_item.get('latencyMs')
    first_token_latency = reply_item.get('first_token_latency_ms')
    if first_token_latency is None:
        first_token_latency = reply_item.get('firstTokenLatencyMs')
    sent_at = reply_item.get('sent_at_ms')
    if sent_at is None:
        sent_at = reply_item.get('sentAtMs')
    raw_segments = reply_item.get('segments')
    segments = []
    if isinstance(raw_segments, list):
        segments = [
            str(seg or '').strip() for seg in raw_segments
            if str(seg or '').strip()
        ]
    return {
        'round': reply_item.get('round'),
        'user_message': (
            reply_item.get('user_message')
            or reply_item.get('userMessage')
            or ''
        ),
        'reply_text': text,
        'output_type': (
            reply_item.get('output_type')
            or reply_item.get('outputType')
            or 'TEXT'
        ),
        'is_final': bool(
            reply_item.get('is_final', reply_item.get('isFinal', True))
        ),
        'task_completed': bool(
            reply_item.get('task_completed', reply_item.get('taskCompleted', False))
        ),
        'session_id': (
            reply_item.get('session_id')
            or reply_item.get('sessionId')
            or ''
        ),
        'device_id': reply_item.get('device_id') or reply_item.get('deviceId'),
        'sent_at_ms': sent_at,
        'latency_ms': latency,
        'first_token_latency_ms': first_token_latency,
        'segments': segments,
        'captured_at': reply_item.get('captured_at') or time.time(),
    }


def _append_unseen_reply_text(old_text, new_text):
    """同轮后到的 AISF 文本：已包含则取更完整的一份，否则追加。"""
    old_text = old_text or ''
    new_text = new_text or ''
    if not new_text:
        return old_text
    if not old_text or old_text in new_text:
        return new_text
    if new_text in old_text:
        return old_text
    return old_text.rstrip() + '\n' + new_text.lstrip()


def _merge_reply_segments(old_segments, new_segments):
    """分段同样追加。新列表已覆盖旧分段时用新列表，否则把没出现过的段接到后面。"""
    old_clean = [str(s).strip() for s in (old_segments or []) if str(s).strip()]
    new_clean = [str(s).strip() for s in (new_segments or []) if str(s).strip()]
    if not new_clean:
        return old_clean
    if not old_clean:
        return new_clean
    new_blob = '\n'.join(new_clean)
    if all(seg in new_blob for seg in old_clean):
        return new_clean
    out = list(old_clean)
    for seg in new_clean:
        if any(seg == prev or seg in prev for prev in out):
            continue
        out.append(seg)
    return out


def _merge_case_reply_items(old_item, new_item):
    """同轮合并：后续 AISF 回复追加保留，叠加 task_completed，取更大时延。"""
    old = _normalize_case_reply_item(old_item)
    new = _normalize_case_reply_item(new_item)
    old_text = old.get('reply_text') or ''
    new_text = new.get('reply_text') or ''
    merged = dict(old)
    merged.update(new)
    # 同轮后到的 AISF 回复必须留下：新文本已包含旧文本则用新的；
    # 旧文本已包含新文本则保持；两边都不是子串时把新回复追加到末尾。
    merged['reply_text'] = _append_unseen_reply_text(old_text, new_text)
    merged['task_completed'] = bool(old.get('task_completed')) or bool(
        new.get('task_completed')
    )
    merged['is_final'] = bool(old.get('is_final')) or bool(new.get('is_final'))
    if not merged.get('user_message') and old.get('user_message'):
        merged['user_message'] = old['user_message']
    old_lat = old.get('latency_ms')
    new_lat = new.get('latency_ms')
    if old_lat is not None and new_lat is not None:
        try:
            merged['latency_ms'] = max(int(old_lat), int(new_lat))
        except (TypeError, ValueError):
            merged['latency_ms'] = new_lat if new_lat is not None else old_lat
    elif new_lat is None and old_lat is not None:
        merged['latency_ms'] = old_lat
    old_ft = old.get('first_token_latency_ms')
    new_ft = new.get('first_token_latency_ms')
    if old_ft is not None and new_ft is not None:
        try:
            # 首 Token 取更早（更小）的有效值
            old_i, new_i = int(old_ft), int(new_ft)
            positives = [v for v in (old_i, new_i) if v > 0]
            merged['first_token_latency_ms'] = min(positives) if positives else 0
        except (TypeError, ValueError):
            merged['first_token_latency_ms'] = (
                new_ft if new_ft is not None else old_ft
            )
    elif new_ft is None and old_ft is not None:
        merged['first_token_latency_ms'] = old_ft
    if not merged.get('sent_at_ms') and old.get('sent_at_ms'):
        merged['sent_at_ms'] = old['sent_at_ms']
    merged['segments'] = _merge_reply_segments(
        old.get('segments') or [], new.get('segments') or []
    )
    # 清理历史 camelCase 残留
    for k in ('replyText', 'userMessage', 'outputType', 'isFinal',
              'taskCompleted', 'sessionId', 'deviceId', 'sentAtMs', 'latencyMs',
              'firstTokenLatencyMs'):
        merged.pop(k, None)
    return merged


_PROGRESS_REPLY_RE = re.compile(r'^(?:正在执行|正在总结)[:：]\s*')


def _strip_reply_json_echo(text):
    """正文后面若再贴一份 JSON，且其中的 text 已在正文里，丢掉这份回声。"""
    text = (text or '').strip()
    if '\n' not in text:
        return text
    head, tail = text.rsplit('\n', 1)
    tail = tail.strip()
    if not (tail.startswith('{') and tail.endswith('}')):
        return text
    try:
        obj = json.loads(tail)
    except (TypeError, ValueError):
        return text
    if not isinstance(obj, dict):
        return text
    echoed = obj.get('text') or obj.get('UserOutputText') or ''
    if isinstance(echoed, str) and echoed.strip() and echoed.strip() in head:
        return head.strip()
    return text


def _progress_echo_body(text):
    """「正在执行/正在总结」去掉前缀后的步骤原文。不是进度句则返回 None。"""
    match = _PROGRESS_REPLY_RE.match((text or '').strip())
    if not match:
        return None
    body = (text or '').strip()[match.end():].strip()
    return body or None


_CONTROL_REPLY_RE = re.compile(
    r'^(?:START_GUI|STOP_GUI|GUI_TASK|status\s*=.*)$',
    re.IGNORECASE,
)


def _is_control_reply(item):
    """操控开始/结束信号不是一轮对话。"""
    if not isinstance(item, dict):
        return False
    output_type = str(item.get('output_type') or '').strip().upper()
    text = (item.get('reply_text') or '').strip()
    if output_type in ('GUI_TASK', 'TASK_STATUS'):
        return True
    return bool(text and _CONTROL_REPLY_RE.match(text))


def _append_reply_bubble(item, text):
    """同一轮提问里追加一条模型气泡。规划变长则替换原气泡，进度句另起一条。"""
    text = (text or '').strip()
    if not text or not isinstance(item, dict):
        return
    segments = [str(seg).strip() for seg in (item.get('segments') or []) if str(seg).strip()]
    if not segments:
        old = (item.get('reply_text') or '').strip()
        if old:
            segments = [old]
    replaced = False
    for idx, seg in enumerate(segments):
        if seg == text or seg in text:
            segments[idx] = text
            replaced = True
            break
        if text in seg:
            replaced = True
            break
    if not replaced:
        segments.append(text)
    item['segments'] = segments
    item['reply_text'] = '\n'.join(segments)


def _next_case_dialogue_round(replies):
    """一条用例里的轮次从 1 开始，不沿用语音进程里的累计号。"""
    numbers = []
    for item in replies or []:
        try:
            numbers.append(int(item.get('round')))
        except (TypeError, ValueError):
            continue
    return max(numbers) + 1 if numbers else 1


def _dialogue_target_index(replies, normalized):
    """同一句用户提问的后续回复，并进已有轮次，不再新开一轮。"""
    user = (normalized.get('user_message') or '').strip()
    if user:
        for idx in range(len(replies) - 1, -1, -1):
            if (replies[idx].get('user_message') or '').strip() == user:
                return idx
    if _progress_echo_body(normalized.get('reply_text') or '') is not None and replies:
        return len(replies) - 1
    return None


def ingest_case_reply(data, device_id=None):
    """写入一条 Device/TestApp 上报的模型回复。WebSocket 与 HTTP 共用。"""
    if not isinstance(data, dict):
        return False, 'invalid payload'
    task_id = str(data.get('taskId') or data.get('task_id') or '').strip()
    case_id = str(
        data.get('caseId') or data.get('testId') or data.get('case_id') or ''
    ).strip()
    device_ref = device_id or data.get('deviceId') or data.get('device_id')
    if not task_id or not case_id:
        fallback_task, fallback_case = get_device_current_task(device_ref)
        task_id = task_id or (fallback_task or '')
        case_id = case_id or (fallback_case or '')
    reply_text = (data.get('replyText') or data.get('reply_text') or '').strip()
    task_completed = bool(data.get('taskCompleted', data.get('task_completed', False)))
    latency_ms = data.get('latencyMs')
    if latency_ms is None:
        latency_ms = data.get('latency_ms')
    first_token_latency_ms = data.get('firstTokenLatencyMs')
    if first_token_latency_ms is None:
        first_token_latency_ms = data.get('first_token_latency_ms')
    if not task_id or not case_id:
        return False, 'missing taskId/caseId'
    if not reply_text and not task_completed:
        return False, 'empty reply'
    if should_reject_task_upload(task_id):
        logger.info(f"忽略已停止任务的回复: task={task_id} case={case_id}")
        return False, 'task stopped'
    reply_item = {
        'round': data.get('round'),
        'user_message': data.get('userMessage') or data.get('user_message') or '',
        'reply_text': reply_text,
        'output_type': data.get('outputType') or data.get('output_type') or 'TEXT',
        'is_final': bool(data.get('isFinal', data.get('is_final', True))),
        'task_completed': task_completed,
        'session_id': data.get('sessionId') or data.get('session_id') or '',
        'device_id': device_ref,
        'sent_at_ms': data.get('sentAtMs') or data.get('sent_at_ms'),
        'latency_ms': latency_ms,
        'first_token_latency_ms': first_token_latency_ms,
        'segments': data.get('segments') if isinstance(data.get('segments'), list) else [],
        'captured_at': data.get('capturedAt') or data.get('captured_at') or time.time(),
    }
    stored = save_case_reply(task_id, case_id, reply_item)
    if stored == 'folded':
        logger.info(
            f"并入同一轮提问: task={task_id} case={case_id} "
            f"round={reply_item.get('round')}"
        )
        return True, 'folded'
    if stored is not True:
        logger.info(
            f"忽略非对话回复: task={task_id} case={case_id} "
            f"round={reply_item.get('round')} type={reply_item.get('output_type')}"
        )
        return True, 'skipped'
    if device_ref:
        ensure_case_assigned_device(task_id, case_id, device_ref)
    if latency_ms is not None:
        save_case_round_latency(task_id, case_id, reply_item)
    logger.info(
        f"Case reply saved: task={task_id} case={case_id} "
        f"round={reply_item.get('round')} latency_ms={latency_ms} "
        f"first_token_latency_ms={first_token_latency_ms}"
    )
    return True, 'saved'


def save_case_reply(task_id, case_id, reply_item):
    """追加或更新单条 Device 回复（按 round 覆盖；聚焦流式同轮合并）。"""
    if not task_id or not case_id:
        return False
    case_dir = os.path.join(app.config['TESTRESULTS_FOLDER'], task_id, case_id)
    os.makedirs(case_dir, exist_ok=True)
    path = get_case_replies_path(task_id, case_id)
    payload = {'case_id': case_id, 'replies': []}
    if os.path.isfile(path):
        try:
            with open(path, 'r', encoding='utf-8') as f:
                payload = json.load(f)
        except Exception:
            payload = {'case_id': case_id, 'replies': []}
    replies = payload.get('replies') or []
    normalized = _normalize_case_reply_item(reply_item)
    normalized['reply_text'] = _strip_reply_json_echo(normalized.get('reply_text') or '')
    if _is_control_reply(normalized):
        if normalized.get('task_completed'):
            target = _dialogue_target_index(replies, normalized)
            if target is not None:
                replies[target]['task_completed'] = True
                reply_item['round'] = replies[target].get('round')
                payload['replies'] = replies
                payload['updated_at'] = time.time()
                with open(path, 'w', encoding='utf-8') as f:
                    json.dump(payload, f, indent=2, ensure_ascii=False)
                return 'folded'
        return False
    target = _dialogue_target_index(replies, normalized)
    if target is not None:
        _append_reply_bubble(replies[target], normalized.get('reply_text') or '')
        if normalized.get('task_completed'):
            replies[target]['task_completed'] = True
        if not replies[target].get('user_message') and normalized.get('user_message'):
            replies[target]['user_message'] = normalized.get('user_message')
        reply_item['round'] = replies[target].get('round')
        payload['replies'] = replies
        payload['updated_at'] = time.time()
        with open(path, 'w', encoding='utf-8') as f:
            json.dump(payload, f, indent=2, ensure_ascii=False)
        return 'folded'
    if normalized.get('reply_text') and not normalized.get('segments'):
        normalized['segments'] = [normalized['reply_text']]
    round_no = normalized.get('round')
    replaced = False
    if round_no is not None:
        for idx, item in enumerate(replies):
            if item.get('round') == round_no:
                replies[idx] = _merge_case_reply_items(item, normalized)
                replaced = True
                break
    if not replaced:
        normalized['round'] = _next_case_dialogue_round(replies)
        reply_item['round'] = normalized['round']
        replies.append(normalized)
    replies.sort(key=lambda r: r.get('round') or 0)
    payload['case_id'] = case_id
    payload['replies'] = replies
    payload['updated_at'] = time.time()
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(payload, f, indent=2, ensure_ascii=False)
    return True


def get_qa_round_latency_path(task_id, case_id):
    return os.path.join(app.config['TESTRESULTS_FOLDER'], task_id, case_id, 'qa_round_latency.json')


def load_case_round_latency(task_id, case_id):
    """读取问答轮次时延（DeviceAPP 事件驱动采集）。"""
    path = get_qa_round_latency_path(task_id, case_id)
    if not os.path.isfile(path):
        return []
    try:
        with open(path, 'r', encoding='utf-8') as f:
            data = json.load(f)
        return data.get('rounds') or []
    except Exception as e:
        logger.warning(f"读取 qa_round_latency.json 失败 {path}: {e}")
        return []


def save_case_round_latency(task_id, case_id, reply_item):
    """保存单轮问答时延（按 round 覆盖；聚焦模式取更大时延）。"""
    normalized = _normalize_case_reply_item(reply_item)
    latency_ms = normalized.get('latency_ms')
    if latency_ms is None:
        return False
    case_dir = os.path.join(app.config['TESTRESULTS_FOLDER'], task_id, case_id)
    os.makedirs(case_dir, exist_ok=True)
    path = get_qa_round_latency_path(task_id, case_id)
    payload = {'case_id': case_id, 'rounds': []}
    if os.path.isfile(path):
        try:
            with open(path, 'r', encoding='utf-8') as f:
                payload = json.load(f)
        except Exception:
            payload = {'case_id': case_id, 'rounds': []}
    rounds = payload.get('rounds') or []
    first_token_ms = normalized.get('first_token_latency_ms')
    round_item = {
        'round': normalized.get('round'),
        'latency_ms': latency_ms,
        'latency_sec': round(float(latency_ms) / 1000.0, 3),
        'first_token_latency_ms': first_token_ms,
        'first_token_latency_sec': (
            round(float(first_token_ms) / 1000.0, 3)
            if first_token_ms is not None else None
        ),
        'sent_at_ms': normalized.get('sent_at_ms'),
        'completed_at_ms': (
            int(normalized.get('sent_at_ms')) + int(latency_ms)
            if normalized.get('sent_at_ms') else None
        ),
        'task_completed': bool(normalized.get('task_completed')),
        'output_type': normalized.get('output_type') or 'TEXT',
        'device_id': normalized.get('device_id'),
    }
    round_no = round_item.get('round')
    replaced = False
    if round_no is not None:
        for idx, item in enumerate(rounds):
            if item.get('round') == round_no:
                try:
                    old_lat = int(item.get('latency_ms') or 0)
                    new_lat = int(latency_ms)
                    if old_lat > new_lat:
                        round_item['latency_ms'] = old_lat
                        round_item['latency_sec'] = round(old_lat / 1000.0, 3)
                        if item.get('sent_at_ms') and not round_item.get('sent_at_ms'):
                            round_item['sent_at_ms'] = item.get('sent_at_ms')
                        if round_item.get('sent_at_ms'):
                            round_item['completed_at_ms'] = (
                                int(round_item['sent_at_ms']) + old_lat
                            )
                except (TypeError, ValueError):
                    pass
                old_ft = item.get('first_token_latency_ms')
                new_ft = round_item.get('first_token_latency_ms')
                if old_ft is not None and new_ft is not None:
                    try:
                        positives = [v for v in (int(old_ft), int(new_ft)) if v > 0]
                        if positives:
                            best = min(positives)
                            round_item['first_token_latency_ms'] = best
                            round_item['first_token_latency_sec'] = round(
                                best / 1000.0, 3)
                    except (TypeError, ValueError):
                        pass
                elif new_ft is None and old_ft is not None:
                    round_item['first_token_latency_ms'] = old_ft
                    if item.get('first_token_latency_sec') is not None:
                        round_item['first_token_latency_sec'] = item.get(
                            'first_token_latency_sec')
                round_item['task_completed'] = bool(item.get('task_completed')) or bool(
                    round_item.get('task_completed')
                )
                rounds[idx] = round_item
                replaced = True
                break
    if not replaced:
        rounds.append(round_item)
    rounds.sort(key=lambda r: r.get('round') or 0)
    payload['case_id'] = case_id
    payload['rounds'] = rounds
    payload['updated_at'] = time.time()
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(payload, f, indent=2, ensure_ascii=False)
    return True


def save_case_round_start(task_id, case_id, round_no, sent_at_ms=None,
                          output_type=None, device_id=None):
    """记录轮次开始（如收到 GUI_TASK），先建立截图归轮时间窗。

    轮次时延要等回复到达才落盘，GUI 长轮进行中若没有这条记录，
    截图会被兜底归到上一轮；开始即建窗可避免这段窗口期误归轮。
    """
    if not task_id or not case_id:
        return False
    try:
        round_no = int(round_no)
    except (TypeError, ValueError):
        return False
    sent_ms = None
    if sent_at_ms:
        try:
            sent_ms = int(float(sent_at_ms))
        except (TypeError, ValueError):
            sent_ms = None
    case_dir = os.path.join(app.config['TESTRESULTS_FOLDER'], task_id, case_id)
    os.makedirs(case_dir, exist_ok=True)
    path = get_qa_round_latency_path(task_id, case_id)
    payload = {'case_id': case_id, 'rounds': []}
    if os.path.isfile(path):
        try:
            with open(path, 'r', encoding='utf-8') as f:
                payload = json.load(f)
        except Exception:
            payload = {'case_id': case_id, 'rounds': []}
    rounds = payload.get('rounds') or []
    existing = next((it for it in rounds if it.get('round') == round_no), None)
    if existing is not None:
        if sent_ms and not existing.get('sent_at_ms'):
            existing['sent_at_ms'] = sent_ms
        if output_type and not existing.get('output_type'):
            existing['output_type'] = output_type
    else:
        rounds.append({
            'round': round_no,
            'latency_ms': None,
            'latency_sec': None,
            'first_token_latency_ms': None,
            'first_token_latency_sec': None,
            'sent_at_ms': sent_ms,
            'completed_at_ms': None,
            'task_completed': False,
            'output_type': output_type or 'GUI_TASK',
            'device_id': device_id,
            'started_only': True,
        })
    rounds.sort(key=lambda r: r.get('round') or 0)
    payload['case_id'] = case_id
    payload['rounds'] = rounds
    payload['updated_at'] = time.time()
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(payload, f, indent=2, ensure_ascii=False)
    return True


def ingest_round_start(data, device_id=None):
    """入库轮次开始事件（WS / HTTP）。"""
    if not isinstance(data, dict):
        return False, 'invalid payload'
    task_id = (data.get('taskId') or data.get('task_id') or '').strip()
    case_id = (data.get('caseId') or data.get('case_id')
               or data.get('testId') or data.get('test_id') or '').strip()
    device_ref = (device_id or data.get('deviceId') or data.get('device_id') or '').strip()
    if not task_id or not case_id:
        ft, fc = get_device_current_task(device_ref)
        task_id = task_id or (ft or '')
        case_id = case_id or (fc or '')
    if not task_id or not case_id:
        return False, 'missing taskId/caseId'
    round_no = data.get('round')
    if round_no is None:
        return False, 'missing round'
    sent_at_ms = data.get('sentAtMs') or data.get('sent_at_ms')
    output_type = data.get('outputType') or data.get('output_type') or 'GUI_TASK'
    ok = save_case_round_start(
        task_id, case_id, round_no, sent_at_ms, output_type, device_ref or None)
    if not ok:
        return False, 'save failed'
    logger.info(
        f"Case round start saved: task={task_id} case={case_id} "
        f"round={round_no} type={output_type} sent_at_ms={sent_at_ms}"
    )
    return True, 'saved'


def get_gui_step_latency_path(task_id, case_id):
    return os.path.join(
        app.config['TESTRESULTS_FOLDER'], task_id, case_id, 'gui_step_latency.json')


def load_gui_step_latency(task_id, case_id, round_no=None):
    """读取 GUI 单步时延（按 round；未指定则返回全部）。"""
    path = get_gui_step_latency_path(task_id, case_id)
    if not os.path.isfile(path):
        return [] if round_no is None else None
    try:
        with open(path, 'r', encoding='utf-8') as f:
            data = json.load(f)
        rounds = data.get('rounds') or []
        if round_no is None:
            return rounds
        for item in rounds:
            if item.get('round') == round_no:
                return item
        return None
    except Exception as e:
        logger.warning(f"读取 gui_step_latency.json 失败 {path}: {e}")
        return [] if round_no is None else None


def _parse_gui_step_payload(raw):
    if isinstance(raw, dict):
        return raw
    if isinstance(raw, str) and raw.strip():
        try:
            parsed = json.loads(raw)
            return parsed if isinstance(parsed, dict) else {'raw': raw}
        except Exception:
            return {'raw': raw}
    return {}


def _gui_step_latency_ms(row):
    if not isinstance(row, dict):
        return None
    if row.get('latency_ms') is not None:
        try:
            return float(row.get('latency_ms'))
        except (TypeError, ValueError):
            return None
    start_ms = row.get('start_ms')
    end_ms = row.get('end_ms')
    if start_ms is None or end_ms is None:
        return None
    try:
        return max(0.0, float(end_ms) - float(start_ms))
    except (TypeError, ValueError):
        return None


def compute_gui_step_avg_latencies_sec(device_steps, cloud_steps):
    """平均单步时延（秒）：序号>=1 的端/云均值，平均单步=两者之和。

    云侧展示序号从 0 起，求平均时跳过序号 0（即列表首行）。
    端侧序号不变（通常从 1 起），取序号>=1 的行。
    """
    def _avg_sec(rows, skip_first=False):
        items = list(rows or [])
        if skip_first:
            items = items[1:]
        else:
            filtered = []
            for idx, row in enumerate(items):
                seq = row.get('seq') if isinstance(row, dict) else None
                try:
                    seq_n = int(seq) if seq is not None else (idx + 1)
                except (TypeError, ValueError):
                    seq_n = idx + 1
                if seq_n >= 1:
                    filtered.append(row)
            items = filtered
        vals = []
        for row in items:
            ms = _gui_step_latency_ms(row)
            if ms is not None:
                vals.append(ms / 1000.0)
        if not vals:
            return None
        return round(sum(vals) / len(vals), 3)

    avg_device = _avg_sec(device_steps, skip_first=False)
    avg_cloud = _avg_sec(cloud_steps, skip_first=True)
    avg_step = None
    if avg_device is not None or avg_cloud is not None:
        avg_step = round((avg_device or 0.0) + (avg_cloud or 0.0), 3)
    return {
        'avg_device_step_sec': avg_device,
        'avg_cloud_step_sec': avg_cloud,
        'avg_step_sec': avg_step,
    }


def save_gui_step_latency(task_id, case_id, round_no, payload, device_id=None):
    """保存 GUI 单步时延（按 round 覆盖）。"""
    if not task_id or not case_id:
        return False
    case_dir = os.path.join(app.config['TESTRESULTS_FOLDER'], task_id, case_id)
    os.makedirs(case_dir, exist_ok=True)
    path = get_gui_step_latency_path(task_id, case_id)
    store = {'case_id': case_id, 'rounds': []}
    if os.path.isfile(path):
        try:
            with open(path, 'r', encoding='utf-8') as f:
                store = json.load(f)
        except Exception:
            store = {'case_id': case_id, 'rounds': []}
    rounds = store.get('rounds') or []
    parsed = _parse_gui_step_payload(payload)
    item = {
        'round': round_no if round_no is not None else 0,
        'device_id': device_id,
        'device_steps': parsed.get('device_steps') or [],
        'cloud_steps': parsed.get('cloud_steps') or [],
        'reason': parsed.get('reason') or '',
        'captured_at_ms': parsed.get('captured_at_ms'),
        'updated_at': time.time(),
    }
    replaced = False
    for idx, old in enumerate(rounds):
        if old.get('round') == item['round']:
            # 保留更长的步骤表（后到的 STOP_GUI / task_completed 可能重复上报）
            old_dev = len(old.get('device_steps') or [])
            old_cloud = len(old.get('cloud_steps') or [])
            new_dev = len(item['device_steps'])
            new_cloud = len(item['cloud_steps'])
            if new_dev + new_cloud >= old_dev + old_cloud:
                rounds[idx] = item
            replaced = True
            break
    if not replaced:
        rounds.append(item)
    rounds.sort(key=lambda r: r.get('round') or 0)
    store['case_id'] = case_id
    store['rounds'] = rounds
    store['updated_at'] = time.time()
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(store, f, indent=2, ensure_ascii=False)
    return True


def ingest_gui_step_latency(data, device_id=None):
    """入库 GUI 单步时延（WS / HTTP）。"""
    if not isinstance(data, dict):
        return False, 'invalid payload'
    task_id = (data.get('taskId') or data.get('task_id') or '').strip()
    case_id = (data.get('caseId') or data.get('case_id')
               or data.get('testId') or data.get('test_id') or '').strip()
    device_ref = (device_id or data.get('deviceId') or data.get('device_id') or '').strip()
    if not task_id or not case_id:
        ft, fc = get_device_current_task(device_ref)
        task_id = task_id or (ft or '')
        case_id = case_id or (fc or '')
    if not task_id or not case_id:
        return False, 'missing taskId/caseId'
    try:
        round_no = int(data.get('round') or 0)
    except (TypeError, ValueError):
        round_no = 0
    payload = data.get('payload')
    if payload is None:
        payload = {
            'device_steps': data.get('device_steps') or [],
            'cloud_steps': data.get('cloud_steps') or [],
            'reason': data.get('reason') or '',
            'captured_at_ms': data.get('captured_at_ms'),
        }
    ok = save_gui_step_latency(task_id, case_id, round_no, payload, device_ref or None)
    if not ok:
        return False, 'save failed'
    logger.info(
        f"GUI step latency saved: task={task_id} case={case_id} round={round_no} "
        f"device={device_ref}"
    )
    return True, 'saved'


def reset_cases_progress(task_id, filenames):
    """将选定用例的进度重置为 pending。"""
    tasks_dir = load_tasks()
    if task_id not in tasks_dir:
        return
    task = tasks_dir[task_id]
    filename_set = set(filenames)
    for item in task.get('case_progress') or []:
        if item.get('filename') not in filename_set:
            continue
        item['status'] = 'pending'
        for key in (
            'started_at', 'completed_at', 'step_index', 'step_total',
            'analyzing_started_at', 'finishing_started_at', 'timeout_sec',
            'step_id', 'step_action', 'evaluation_passed', 'fail_reason',
        ):
            item.pop(key, None)
    apply_task_progress(task)
    save_task(task)


def mark_cases_queued(task_id, filenames):
    """将选定用例标为排队中（保留历史结果与评判，真正开跑前再清）。"""
    tasks_dir = load_tasks()
    if task_id not in tasks_dir:
        return
    task = tasks_dir[task_id]
    filename_set = set(filenames)
    for item in task.get('case_progress') or []:
        if item.get('filename') not in filename_set:
            continue
        if item.get('status') in ('running', 'finishing', 'analyzing'):
            continue
        item['status'] = 'queued'
    apply_task_progress(task)
    save_task(task)


def prepare_cases_for_immediate_rerun(task_id, filenames):
    """队列出队后准备下一批：仅重置进度为 pending，历史结果目录留到真正开跑时再删。"""
    reset_cases_progress(task_id, filenames)
    tasks_dir = load_tasks()
    task = tasks_dir.get(task_id)
    if not task:
        return
    task['status'] = 'running'
    task.pop('end_time', None)
    if not task.get('start_time'):
        task['start_time'] = time.time()
    apply_task_progress(task)
    save_task(task)


def start_case_execution(task_id, filenames):
    """执行指定用例（不清空未选用例的结果）。

    若整批正在跑：将选中用例加入排队，当前用例结束后再跑（不打断）。
    历史结果目录在该用例真正开始执行时再删除。
    """
    tasks_dir = load_tasks()
    if task_id not in tasks_dir:
        return False, '任务不存在'

    release_stale_task_execution_if_needed(task_id)

    task = tasks_dir[task_id]
    task_files = set(task.get('files') or [])
    filenames = [f for f in filenames if f in task_files]
    if not filenames:
        return False, '未找到有效用例'

    device_ids = task.get('devices', [])

    if is_task_executing(task_id):
        progress_by_file = {
            item.get('filename'): item
            for item in (task.get('case_progress') or [])
            if item.get('filename')
        }
        already_waiting = peek_queued_reruns(task_id)
        to_queue = []
        for f in filenames:
            item = progress_by_file.get(f) or {}
            st = item.get('status')
            # 本批尚未跑到的 pending：不必再入队，避免跑两遍
            if st == 'pending':
                continue
            if st == 'queued' or f in already_waiting:
                continue
            to_queue.append(f)
        if not to_queue:
            return True, '所选用例已在执行队列中，无需重复加入'
        added = enqueue_task_rerun(task_id, to_queue)
        if not added:
            return True, '所选用例已在执行队列中，无需重复加入'
        mark_cases_queued(task_id, added)
        logger.info(
            f"Task {task_id}: queued {len(added)} case(s) for rerun "
            f"(no interrupt): {added}"
        )
        return True, f'已加入排队（{len(added)} 个），当前用例结束后执行'

    target_package = task.get('target_package') or EXCEL_DEFAULT_PACKAGE
    allow_offline = False
    try:
        from task_preflight import should_run_preflight
        allow_offline = should_run_preflight(target_package)
    except Exception:
        allow_offline = False
    available, busy_message = check_devices_available(
        device_ids, exclude_task_id=task_id, require_online=not allow_offline
    )
    if not available:
        return False, busy_message

    # 不在此处 clear_case_results：等每个用例真正开跑时再删历史
    reset_cases_progress(task_id, filenames)
    clear_task_cancelled(task_id)
    clear_all_case_cancelled(task_id)
    clear_task_run_queue(task_id)

    task = load_tasks()[task_id]
    task['status'] = 'running'
    task.pop('end_time', None)
    if not task.get('start_time'):
        task['start_time'] = time.time()
    apply_task_progress(task)
    save_task(task)

    with tasks_lock:
        tasks[task_id] = {
            'id': task_id,
            'devices': device_ids,
            'testset': task.get('testset'),
            'files': task.get('files'),
            'status': 'running',
            'results': [],
        }

    threading.Thread(
        target=execute_task,
        args=(task_id,),
        kwargs={'files_to_run': filenames},
        daemon=True,
    ).start()
    return True, f'已开始执行 {len(filenames)} 个用例'


def delete_task_data(task_id):
    """Delete task data including test results."""
    # Delete task file
    task_file = os.path.join(app.config['TASKS_FOLDER'], f"{task_id}.json")
    if os.path.exists(task_file):
        os.remove(task_file)

    clear_task_results(task_id)

def get_latest_apk():
    """获取 output 目录中最新的 testapp APK 文件名。"""
    output_dir = app.config['OUTPUT_FOLDER']
    if not os.path.isdir(output_dir):
        return None

    apks = [
        name for name in os.listdir(output_dir)
        if name.endswith('.apk') and name.startswith('testapp')
    ]
    if not apks:
        return None

    apks.sort(
        key=lambda name: os.path.getmtime(os.path.join(output_dir, name)),
        reverse=True,
    )
    return apks[0]


def _newest_glob(directory, pattern):
    if not os.path.isdir(directory):
        return None
    matches = []
    for name in os.listdir(directory):
        if not fnmatch.fnmatch(name, pattern):
            continue
        path = os.path.join(directory, name)
        if os.path.isfile(path):
            matches.append(path)
    if not matches:
        return None
    matches.sort(key=lambda p: os.path.getmtime(p), reverse=True)
    return matches[0]


# Managed software packages shown on /download (one row each).
SOFTWARE_PACKAGES = [
    {
        'id': 'testapp',
        'name': 'TestApp',
        'package_name': 'com.example.testapp',
        'managed_dir': 'testapp',
        'managed_filename': 'testapp.apk',
        'seed_resolver': lambda: (
            os.path.join(app.config['OUTPUT_FOLDER'], get_latest_apk())
            if get_latest_apk() else None
        ),
        'uploadable': True,
        'installable': True,
    },
    {
        'id': 'devicesdkdemo_cloud_v3',
        'name': 'DeviceSDK Demo 云端v3（直连 webserver）',
        'package_name': 'com.example.devicesdkdemo',
        'managed_dir': 'devicesdkdemo-cloud-v3',
        # 托管目录内按 devicesdk-yyyyMMdd-HHmmss.apk 取最新
        'managed_glob': 'devicesdk-*.apk',
        'managed_filename': 'devicesdkdemo.apk',  # 兼容旧文件
        'seed_resolver': lambda: (
            _newest_glob(
                os.path.join(os.path.dirname(__file__), 'tools', '_cloud_v3_work', 'dist'),
                'devicesdk-*.apk',
            )
            or _newest_glob(
                os.path.join(os.path.dirname(__file__), 'apks'),
                'devicesdk-*.apk',
            )
            or _newest_glob(
                os.path.join(os.path.dirname(__file__), 'apks'),
                'DeviceSDK-Demo-cloud-v3.apk',
            )
        ),
        'uploadable': True,
        'installable': True,
    },
    {
        'id': 'testapp_guangzhong',
        'name': 'TestApp（光中屏）',
        'package_name': 'com.example.testapp',
        'managed_dir': 'testapp-guangzhong',
        'managed_glob': 'testapp-guangzhong-*.apk',
        'managed_filename': 'testapp-guangzhong.apk',
        'seed_resolver': lambda: None,
        'uploadable': True,
        'installable': True,
    },
    {
        'id': 'devicesdkdemo_guangzhong',
        'name': 'DeviceSDK Demo（光中屏）',
        'package_name': 'com.example.devicesdkdemo',
        'managed_dir': 'devicesdkdemo-guangzhong',
        'managed_glob': 'devicesdk-guangzhong-*.apk',
        'managed_filename': 'devicesdk-guangzhong.apk',
        'seed_resolver': lambda: None,
        'uploadable': True,
        'installable': True,
    },
    {
        'id': 'aisf_voice',
        'name': 'AISF 语音助手',
        'package_name': 'com.huawei.aisftvassistant',
        'managed_dir': 'aisf_voice',
        'managed_filename': 'aisf_voice_assistant.apk',
        'seed_resolver': lambda: _newest_glob(
            _ANDROID_EMU_ROOT, 'aisf_voice_assistant_tv-phoneRelease-*.apk'
        ),
        'uploadable': True,
        'installable': True,
    },
    {
        'id': 'amap',
        'name': '高德地图',
        'package_name': 'com.autonavi.minimap',
        'managed_dir': 'amap',
        'managed_filename': 'amap.apk',
        'seed_resolver': lambda: None,
        'uploadable': True,
        'installable': True,
    },
    {
        'id': 'iqiyi',
        'name': '爱奇艺',
        'package_name': 'com.qiyi.video',
        'managed_dir': 'iqiyi',
        'managed_filename': 'iqiyi.apk',
        'seed_resolver': lambda: _newest_glob(_ANDROID_EMU_ROOT, '爱奇艺.apk')
        or _newest_glob(_ANDROID_EMU_ROOT, 'iqiyi*.apk'),
        'uploadable': True,
        'installable': True,
    },
    {
        'id': 'youku',
        'name': '优酷',
        'package_name': 'com.youku.phone',
        'managed_dir': 'youku',
        'managed_filename': 'youku.apk',
        'seed_resolver': lambda: _newest_glob(_ANDROID_EMU_ROOT, '优酷.apk')
        or _newest_glob(_ANDROID_EMU_ROOT, 'youku*.apk')
        or _newest_glob(_ANDROID_EMU_ROOT, 'com.youku.phone*.apk'),
        'uploadable': True,
        'installable': True,
    },
    {
        'id': 'albummemory',
        'name': '相册记忆',
        'package_name': 'com.albummemory.app',
        'managed_dir': 'albummemory',
        'managed_glob': 'AlbumMemory-*.apk',
        'managed_filename': 'AlbumMemory.apk',
        'seed_resolver': lambda: None,
        'uploadable': True,
        'installable': True,
    },
]


def _software_pkg(pkg_id):
    for pkg in SOFTWARE_PACKAGES:
        if pkg['id'] == pkg_id:
            return pkg
    return None


def _software_managed_path(pkg):
    return os.path.join(
        app.config['SOFTWARE_APKS_ROOT'],
        pkg['managed_dir'],
        pkg['managed_filename'],
    )


def resolve_software_apk_path(pkg):
    """Prefer uploaded/managed copy; fall back to seed (e.g. output / android-emulator)."""
    managed_dir = os.path.join(
        app.config['SOFTWARE_APKS_ROOT'], pkg.get('managed_dir') or '',
    )
    managed_glob = pkg.get('managed_glob')
    if managed_glob:
        newest = _newest_glob(managed_dir, managed_glob)
        if newest and os.path.getsize(newest) > 0:
            return newest, 'managed'
    managed = _software_managed_path(pkg)
    if os.path.isfile(managed) and os.path.getsize(managed) > 0:
        return managed, 'managed'
    seed = pkg['seed_resolver']() if callable(pkg.get('seed_resolver')) else None
    if seed and os.path.isfile(seed) and os.path.getsize(seed) > 0:
        return seed, 'seed'
    return None, None


def get_software_apk_info(pkg):
    path, source = resolve_software_apk_path(pkg)
    if not path:
        return {
            'id': pkg['id'],
            'name': pkg['name'],
            'package_name': pkg['package_name'],
            'available': False,
            'filename': None,
            'size': 0,
            'modified': None,
            'source': None,
            'uploadable': pkg['uploadable'],
            'installable': pkg['installable'],
            'download_url': build_ui_path(f"/api/download/packages/{pkg['id']}/file"),
        }
    return {
        'id': pkg['id'],
        'name': pkg['name'],
        'package_name': pkg['package_name'],
        'available': True,
        'filename': os.path.basename(path),
        'size': os.path.getsize(path),
        'modified': os.path.getmtime(path),
        'source': source,
        'uploadable': pkg['uploadable'],
        'installable': pkg['installable'],
        'download_url': build_ui_path(f"/api/download/packages/{pkg['id']}/file"),
    }


def get_latest_apk_info():
    """获取最新 APK 的展示信息。"""
    pkg = _software_pkg('testapp')
    info = get_software_apk_info(pkg) if pkg else None
    if not info or not info.get('available'):
        return None
    return {
        'filename': info['filename'],
        'size': info['size'],
        'modified': info['modified'],
        'download_url': build_ui_path('/api/download/apk'),
    }


def get_devicesdkdemo_apk_path():
    """获取 DeviceSDK Demo APK 路径（云端v3）。"""
    pkg = _software_pkg('devicesdkdemo_cloud_v3')
    path, _ = resolve_software_apk_path(pkg) if pkg else (None, None)
    return path


def get_devicesdkdemo_apk_info():
    """获取 DeviceSDK Demo APK 的展示信息（云端v3）。"""
    pkg = _software_pkg('devicesdkdemo_cloud_v3')
    info = get_software_apk_info(pkg) if pkg else None
    if not info or not info.get('available'):
        return None
    return {
        'filename': info['filename'],
        'package_name': info['package_name'],
        'size': info['size'],
        'modified': info['modified'],
        'download_url': build_ui_path('/api/download/packages/devicesdkdemo_cloud_v3/file'),
    }




# ========== ARM Emulator viewer (SSH + mini_adb) ==========

def _emu_bridge_enabled() -> bool:
    """Set EMU_BRIDGE_DISABLED=1 to stop SSH connect, warm-start, and watchdog."""
    return os.environ.get("EMU_BRIDGE_DISABLED", "").strip().lower() not in (
        "1", "true", "yes", "on",
    )


def _get_emu_bridge():
    global EMU_BRIDGE
    if EMU_BRIDGE is None:
        try:
            from emu_adb_bridge import EMU_BRIDGE as _b
            EMU_BRIDGE = _b
        except Exception as ex:
            _EMU_STATE['error'] = str(ex)
            logger.warning('emu bridge import failed: %s', ex)
            return None
    return EMU_BRIDGE


def ensure_emu_bridge(force: bool = False):
    if not _emu_bridge_enabled():
        return None
    bridge = _get_emu_bridge()
    if bridge is None:
        return None
    alive = bool(bridge.ready and getattr(bridge, 'is_alive', lambda: True)())
    if alive and not force:
        return bridge
    with _EMU_STATE['lock']:
        if _EMU_STATE.get('connecting') and not force:
            return bridge
        _EMU_STATE['connecting'] = True
    try:
        bridge.connect()
        _EMU_STATE['error'] = ''
        logger.info('EMU bridge connected %sx%s', bridge.width, bridge.height)
        # Prefetch frames so the control panel opens without a cold screencap wait.
        try:
            _emu_ensure_frame_producer()
        except NameError:
            pass
    except Exception as ex:
        _EMU_STATE['error'] = _emu_friendly_error(ex)
        logger.warning('EMU bridge connect failed: %s', ex)
    finally:
        _EMU_STATE['connecting'] = False
    return bridge


def _emu_friendly_error(ex) -> str:
    """Map raw paramiko/SSH noise to a short UI message."""
    msg = str(ex or '').strip()
    low = msg.lower()
    if any(
        x in low
        for x in (
            'not active',
            'not connected',
            'socket is closed',
            'connection reset',
            'connection lost',
            'key-exchange timed out',
            'no existing session',
            'ssh',
        )
    ):
        return '宿主机连接已断开，正在自动重连…'
    if 'emulator offline' in low:
        return '模拟器离线'
    return msg or '未知错误'


def _emu_ssh_dead(ex) -> bool:
    try:
        from emu_adb_bridge import TargetAdb
        return TargetAdb._is_ssh_dead_error(ex)
    except Exception:
        low = str(ex or '').lower()
        return any(x in low for x in ('not active', 'ssh', 'not connected', 'socket is closed'))


def _emu_schedule_reconnect(reason: str = '') -> None:
    """Non-blocking reconnect if not already connecting."""
    if not _emu_bridge_enabled():
        return
    if _EMU_STATE.get('connecting'):
        return

    def _run():
        try:
            logger.info('EMU auto-reconnect: %s', reason or 'scheduled')
            ensure_emu_bridge(force=True)
        except Exception as ex:
            logger.warning('EMU auto-reconnect failed: %s', ex)

    threading.Thread(target=_run, daemon=True, name='emu-auto-reconnect').start()


def _emu_bridge_watchdog() -> None:
    """Periodically detect dead SSH and reconnect without user clicking 重连."""
    if not _emu_bridge_enabled():
        logger.info("EMU bridge watchdog disabled (EMU_BRIDGE_DISABLED=1)")
        return
    interval = float(os.environ.get('EMU_BRIDGE_WATCHDOG_SEC', '20') or 20)
    interval = max(8.0, interval)
    # Let warm-connect finish first.
    time.sleep(8.0)
    while True:
        try:
            if _EMU_STATE.get('busy') or _EMU_STATE.get('connecting'):
                time.sleep(interval)
                continue
            bridge = _get_emu_bridge()
            if bridge is None:
                ensure_emu_bridge()
                time.sleep(interval)
                continue
            alive = bool(getattr(bridge, 'is_alive', lambda: False)())
            if not alive:
                logger.warning('EMU watchdog: SSH not alive, reconnecting')
                ensure_emu_bridge(force=True)
            else:
                # Half-open sessions still report is_active; probe heals via _exec.
                ping = getattr(bridge, 'ping', None)
                if callable(ping) and not ping(timeout=12.0):
                    logger.warning('EMU watchdog: ping failed, reconnecting')
                    ensure_emu_bridge(force=True)
        except Exception as ex:
            logger.warning('EMU watchdog error: %s', ex)
            _emu_schedule_reconnect('watchdog-error')
        time.sleep(interval)


def _emu_png_to_jpeg(png: bytes) -> bytes:
    """Downscale + JPEG for smaller MJPEG payloads. Falls back to PNG bytes if Pillow missing."""
    try:
        from PIL import Image
    except ImportError:
        return png
    try:
        img = Image.open(io.BytesIO(png))
        if img.mode not in ('RGB', 'L'):
            img = img.convert('RGB')
        max_w = max(160, int(_EMU_STREAM_MAX_WIDTH))
        if img.width > max_w:
            new_h = max(1, int(img.height * (max_w / float(img.width))))
            try:
                resample = Image.Resampling.BILINEAR
            except AttributeError:
                resample = Image.BILINEAR
            img = img.resize((max_w, new_h), resample)
        buf = io.BytesIO()
        q = max(20, min(90, int(_EMU_STREAM_JPEG_QUALITY)))
        img.save(buf, format='JPEG', quality=q, optimize=True)
        return buf.getvalue()
    except Exception as ex:
        logger.debug('emu jpeg convert failed: %s', ex)
        return png


def _emu_screencap_png():
    # During long install/SFTP, keep serving last frame so MJPEG does not stall/reconnect.
    if _EMU_STATE.get('busy'):
        return _EMU_STATE.get('last_png')
    bridge = ensure_emu_bridge()
    if bridge is None or not bridge.ready:
        _emu_schedule_reconnect('screencap-offline')
        return _EMU_STATE.get('last_png')
    try:
        data = bridge.screencap()
        jpeg = _emu_png_to_jpeg(data)
        with _EMU_STATE['lock']:
            _EMU_STATE['last_png'] = data
            _EMU_STATE['last_jpeg'] = jpeg
            _EMU_STATE['frame_seq'] = int(_EMU_STATE.get('frame_seq') or 0) + 1
            _EMU_STATE['error'] = ''
        _EMU_STATE['frame_event'].set()
        return data
    except Exception as ex:
        _EMU_STATE['error'] = _emu_friendly_error(ex)
        if _emu_ssh_dead(ex):
            try:
                bridge.ready = False
            except Exception:
                pass
            _emu_schedule_reconnect('screencap-ssh-dead')
        return _EMU_STATE.get('last_png')


def _emu_publish_frame(png: bytes, jpeg: bytes) -> None:
    with _EMU_STATE['lock']:
        _EMU_STATE['last_png'] = png
        _EMU_STATE['last_jpeg'] = jpeg
        _EMU_STATE['frame_seq'] = int(_EMU_STATE.get('frame_seq') or 0) + 1
        _EMU_STATE['error'] = ''
    _EMU_STATE['frame_event'].set()


def _emu_frame_producer_loop() -> None:
    """Single screencap loop shared by all /api/emulator/stream clients."""
    idle_deadline = time.time() + _EMU_STREAM_IDLE_SEC
    while True:
        viewers = int(_EMU_STATE.get('viewers') or 0)
        if viewers <= 0:
            if time.time() > idle_deadline:
                time.sleep(0.4)
                continue
        else:
            idle_deadline = time.time() + _EMU_STREAM_IDLE_SEC

        if _EMU_STATE.get('busy'):
            # Keep last frame; avoid contending with install/SFTP SSH.
            time.sleep(1.0)
            continue

        bridge = ensure_emu_bridge()
        if bridge is None or not bridge.ready:
            _emu_schedule_reconnect('screencap-offline')
            time.sleep(0.8)
            continue

        t0 = time.time()
        try:
            png = bridge.screencap()
            jpeg = _emu_png_to_jpeg(png)
            _emu_publish_frame(png, jpeg)
        except Exception as ex:
            _EMU_STATE['error'] = _emu_friendly_error(ex)
            if _emu_ssh_dead(ex):
                try:
                    bridge.ready = False
                except Exception:
                    pass
                _emu_schedule_reconnect('screencap-ssh-dead')
            time.sleep(0.6)
            continue

        elapsed = time.time() - t0
        interval = max(0.05, float(_EMU_STREAM_INTERVAL))
        if _EMU_STATE.get('busy'):
            interval = max(interval, 1.0)
        sleep_for = interval - elapsed
        if sleep_for > 0:
            time.sleep(sleep_for)


def _emu_ensure_frame_producer() -> None:
    with _EMU_STATE['lock']:
        if _EMU_STATE.get('producer_started'):
            return
        _EMU_STATE['producer_started'] = True
    threading.Thread(
        target=_emu_frame_producer_loop,
        daemon=True,
        name='emu-frame-producer',
    ).start()


def _emu_mjpeg_generator():
    """Serve shared JPEG frames; no per-client screencap."""
    _emu_ensure_frame_producer()
    with _EMU_STATE['lock']:
        _EMU_STATE['viewers'] = int(_EMU_STATE.get('viewers') or 0) + 1
    boundary = b'frame'
    last_seq = -1
    last_sent = 0.0
    try:
        # Kick one capture if stream opens with no cached frame.
        if not _EMU_STATE.get('last_jpeg') and not _EMU_STATE.get('last_png'):
            _emu_screencap_png()
        while True:
            evt = _EMU_STATE['frame_event']
            evt.wait(timeout=1.0)
            evt.clear()
            with _EMU_STATE['lock']:
                seq = int(_EMU_STATE.get('frame_seq') or 0)
                frame = _EMU_STATE.get('last_jpeg') or _EMU_STATE.get('last_png')
            if not frame:
                time.sleep(0.3)
                continue
            now = time.time()
            if seq == last_seq and (now - last_sent) < 2.0:
                continue
            last_seq = seq
            last_sent = now
            ctype = b'image/jpeg' if frame[:2] == b'\xff\xd8' else b'image/png'
            yield (
                b'--' + boundary + b'\r\n'
                b'Content-Type: ' + ctype + b'\r\n'
                b'Content-Length: ' + str(len(frame)).encode() + b'\r\n\r\n'
                + frame + b'\r\n'
            )
    finally:
        with _EMU_STATE['lock']:
            _EMU_STATE['viewers'] = max(0, int(_EMU_STATE.get('viewers') or 0) - 1)


# Warm-connect + shared frame producer + SSH watchdog
def _emu_warm_start():
    if not _emu_bridge_enabled():
        logger.info("EMU bridge warm-start skipped (EMU_BRIDGE_DISABLED=1)")
        return
    ensure_emu_bridge()
    _emu_ensure_frame_producer()


threading.Thread(target=_emu_warm_start, daemon=True, name='emu-warm-start').start()
threading.Thread(target=_emu_bridge_watchdog, daemon=True, name='emu-bridge-watchdog').start()


# ========== Routes ==========

@app.route('/')
def index():
    """Home page - logs and online devices."""
    return render_template('index.html', page='index')

@app.route('/testcases')
def testcases_page():
    """Test case management page."""
    scan_excel_datasets()
    with excel_datasets_lock:
        datasets = sorted(
            excel_datasets.values(),
            key=lambda x: float(x.get('last_modified') or x.get('create_time') or 0),
            reverse=True,
        )
    resp = make_response(render_template(
        'testcases.html', page='testcases', excel_datasets=datasets,
    ))
    resp.headers['Cache-Control'] = 'no-store, no-cache, must-revalidate, max-age=0'
    resp.headers['Pragma'] = 'no-cache'
    return resp

@app.route('/dispatch')
def dispatch_page():
    """测试任务管理页面。"""
    return render_template('dispatch.html', page='dispatch')

@app.route('/task-results/<task_id>')
def task_results_page(task_id):
    """单任务用例结果页（从任务列表「用例详情」进入）。"""
    task_id = (task_id or '').strip()
    if not task_id:
        return redirect(build_ui_path('/dispatch'))
    resp = make_response(render_template(
        'results.html',
        page='dispatch',
        single_task_id=task_id,
        results_ui_version=RESULTS_UI_VERSION,
    ))
    resp.headers['Cache-Control'] = 'no-store, no-cache, must-revalidate, max-age=0'
    resp.headers['Pragma'] = 'no-cache'
    resp.headers['Expires'] = '0'
    return resp

@app.route('/results')
def results_page():
    """旧入口重定向到任务下发页。"""
    return redirect(build_ui_path('/dispatch'))

@app.route('/download')
def download_page():
    """Software download page."""
    return render_template('download.html', page='download')


@app.route('/emulator')
def emulator_page():
    """ARM Android emulator MJPEG viewer + controls."""
    bridge = ensure_emu_bridge()
    ready = bool(bridge and bridge.ready)
    width = bridge.width if bridge else 720
    height = bridge.height if bridge else 1280
    err = _EMU_STATE.get('error') or (bridge.error if bridge else 'bridge unavailable')
    resp = make_response(render_template(
        'emulator.html',
        page='emulator',
        ready=ready,
        error=err if not ready else '',
        width=width,
        height=height,
        active_avd=getattr(bridge, 'active_avd', '') if bridge else '',
        adb_port=getattr(bridge, 'adb_port', 5555) if bridge else 5555,
    ))
    resp.headers['Cache-Control'] = 'no-store, no-cache, must-revalidate, max-age=0'
    resp.headers['Pragma'] = 'no-cache'
    return resp


@app.route('/emulator-manage')
def emulator_manage_page():
    """Android emulator CRUD / lifecycle management."""
    return render_template('emulator_manage.html', page='emu-manage')


@app.route('/remote-adb')
def remote_adb_page():
    """USB reverse-bridge ADB status (no screen control / no netdisk)."""
    return render_template('remote_adb.html', page='remote-adb')


def _emu_manage_async(job_name: str, worker):
    """Run long emulator manage job under _EMU_STATE busy lock."""
    if _EMU_STATE.get('busy'):
        return jsonify(ok=False, error=_EMU_STATE.get('busy_msg') or 'emulator busy'), 409
    bridge = ensure_emu_bridge()
    if not bridge:
        return jsonify(ok=False, error='bridge unavailable'), 503

    with _EMU_STATE['lock']:
        _EMU_STATE['busy'] = True
        _EMU_STATE['busy_msg'] = job_name
        _EMU_STATE['progress_pct'] = 5
        _EMU_STATE['progress_phase'] = 'manage'
        _EMU_STATE['error'] = ''

    def _run():
        stop_poll = threading.Event()

        def _poll_progress():
            while not stop_poll.wait(1.0):
                try:
                    prog = bridge.read_manage_progress() or {}
                    pct = prog.get('pct')
                    msg = (prog.get('msg') or '').strip()
                    if pct is None and not msg:
                        continue
                    with _EMU_STATE['lock']:
                        if not _EMU_STATE.get('busy'):
                            break
                        if pct is not None:
                            try:
                                _EMU_STATE['progress_pct'] = max(
                                    0, min(100, int(pct))
                                )
                            except Exception:
                                pass
                        if msg:
                            _EMU_STATE['busy_msg'] = msg
                except Exception:
                    pass

        poller = threading.Thread(target=_poll_progress, daemon=True)
        poller.start()
        try:
            with _EMU_STATE['lock']:
                _EMU_STATE['progress_pct'] = 8
                _EMU_STATE['busy_msg'] = job_name
            result = worker(bridge)
            with _EMU_STATE['lock']:
                _EMU_STATE['progress_pct'] = 100
                _EMU_STATE['busy_msg'] = f'{job_name}完成'
                _EMU_STATE['error'] = ''
                _EMU_STATE['last_manage_result'] = result
        except Exception as ex:
            with _EMU_STATE['lock']:
                _EMU_STATE['error'] = str(ex)
                _EMU_STATE['busy_msg'] = f'{job_name}失败: {ex}'
        finally:
            stop_poll.set()
            with _EMU_STATE['lock']:
                _EMU_STATE['busy'] = False
                _EMU_STATE['progress_pct'] = 0
                _EMU_STATE['progress_phase'] = ''
                if str(_EMU_STATE.get('busy_msg') or '').endswith('完成'):
                    _EMU_STATE['busy_msg'] = ''

    threading.Thread(target=_run, daemon=True).start()
    return jsonify(ok=True, started=True, message=job_name)


@app.route('/api/emulator-manage/list')
def api_emulator_manage_list():
    now = time.time()
    busy = bool(_EMU_STATE.get('busy'))
    if not busy:
        with _emu_list_cache_lock:
            cached = _emu_list_cache.get('payload')
            cached_at = float(_emu_list_cache.get('at') or 0)
            if cached and (now - cached_at) < EMU_LIST_CACHE_SEC:
                return jsonify(cached)

    bridge = ensure_emu_bridge()
    if not bridge:
        return jsonify(ok=False, error='bridge unavailable', emulators=[]), 503
    try:
        items = bridge.list_emulators()

        def _emu_name_sort_key(it):
            name = str((it or {}).get('name') or '')
            parts = re.split(r'(\d+)', name.lower())
            return [int(p) if p.isdigit() else p for p in parts]

        items = sorted(items, key=_emu_name_sort_key)
        active_port = int(getattr(bridge, 'adb_port', 5555) or 5555)
        active_avd = getattr(bridge, 'active_avd', '') or ''
        for it in items:
            it['is_visual_target'] = bool(
                it.get('running')
                and (
                    (active_avd and it.get('name') == active_avd)
                    or (it.get('adb_port') and int(it['adb_port']) == active_port)
                )
            )
        payload = {
            'ok': True,
            'emulators': items,
            'busy': bool(_EMU_STATE.get('busy')),
            'busy_msg': _EMU_STATE.get('busy_msg') or '',
            'progress_pct': int(_EMU_STATE.get('progress_pct') or 0),
            'active_avd': active_avd,
            'adb_port': active_port,
            'error': _EMU_STATE.get('error') or '',
        }
        if not payload['busy']:
            with _emu_list_cache_lock:
                _emu_list_cache['payload'] = payload
                _emu_list_cache['at'] = time.time()
        return jsonify(payload)
    except Exception as ex:
        return jsonify(ok=False, error=str(ex), emulators=[]), 500


@app.route('/api/emulator-manage/create', methods=['POST'])
def api_emulator_manage_create():
    data = request.get_json(silent=True) or {}
    name = (data.get('name') or '').strip()
    display = (data.get('display_name') or name).strip()
    clone_from = (data.get('clone_from') or '').strip()
    mode = (data.get('mode') or '').strip().lower()
    if mode == 'clone' or clone_from:
        clone_from = clone_from or (data.get('source') or '').strip()
    ram = data.get('ram_mb')
    cores = data.get('cores')
    disk = data.get('disk')
    if not name:
        return jsonify(ok=False, error='name required'), 400
    if clone_from and clone_from == name:
        return jsonify(ok=False, error='clone_from 不能与新名称相同'), 400

    def worker(bridge):
        args = ['--name', name, '--display', display]
        if clone_from:
            args += ['--clone-from', clone_from]
        # Blank defaults filled by script; for clone, only pass overrides when provided.
        if ram is not None and str(ram).strip() != '':
            args += ['--ram', str(ram)]
        elif not clone_from:
            args += ['--ram', '8192']
        if cores is not None and str(cores).strip() != '':
            args += ['--cores', str(cores)]
        elif not clone_from:
            args += ['--cores', '4']
        if disk is not None and str(disk).strip() != '':
            args += ['--disk', str(disk).strip()]
        elif not clone_from:
            args += ['--disk', '15G']
        timeout = 900.0 if clone_from else 120.0
        return bridge.manage_emulator('create', *args, timeout=timeout)

    job = f'整机克隆 {clone_from} → {name}' if clone_from else f'创建模拟器 {name}'
    return _emu_manage_async(job, worker)


@app.route('/api/emulator-manage/update', methods=['POST'])
def api_emulator_manage_update():
    data = request.get_json(silent=True) or {}
    name = (data.get('name') or '').strip()
    if not name:
        return jsonify(ok=False, error='name required'), 400
    args = ['--name', name]
    if data.get('display_name'):
        args += ['--display', str(data.get('display_name')).strip()]
    if data.get('ram_mb') is not None and str(data.get('ram_mb')).strip() != '':
        args += ['--ram', str(data.get('ram_mb'))]
    if data.get('cores') is not None and str(data.get('cores')).strip() != '':
        args += ['--cores', str(data.get('cores'))]
    if data.get('disk'):
        args += ['--disk', str(data.get('disk')).strip()]

    def worker(bridge):
        return bridge.manage_emulator('update', *args, timeout=60.0)

    return _emu_manage_async(f'更新模拟器 {name}', worker)


@app.route('/api/emulator-manage/start', methods=['POST'])
def api_emulator_manage_start():
    data = request.get_json(silent=True) or {}
    name = (data.get('name') or '').strip()
    if not name:
        return jsonify(ok=False, error='name required'), 400

    def worker(bridge):
        port = data.get('console_port')
        if not port:
            port = bridge.next_console_port()
        result = bridge.manage_emulator(
            'start', '--name', name, '--port', str(int(port)), timeout=600.0
        )
        # Auto-bind visual bridge to this instance after start
        adb_port = int(result.get('adb_port') or (int(port) + 1))
        bridge.set_active_emulator(adb_port=adb_port, avd_name=name)
        return result

    return _emu_manage_async(f'启动模拟器 {name}', worker)


@app.route('/api/emulator-manage/stop', methods=['POST'])
def api_emulator_manage_stop():
    data = request.get_json(silent=True) or {}
    name = (data.get('name') or '').strip()
    if not name:
        return jsonify(ok=False, error='name required'), 400

    def worker(bridge):
        return bridge.manage_emulator('stop', '--name', name, timeout=120.0)

    return _emu_manage_async(f'停止模拟器 {name}', worker)


@app.route('/api/emulator-manage/delete', methods=['POST'])
def api_emulator_manage_delete():
    data = request.get_json(silent=True) or {}
    name = (data.get('name') or '').strip()
    if not name:
        return jsonify(ok=False, error='name required'), 400

    def worker(bridge):
        return bridge.manage_emulator('delete', '--name', name, timeout=180.0)

    return _emu_manage_async(f'删除模拟器 {name}', worker)


@app.route('/api/emulator-manage/visualize', methods=['POST'])
def api_emulator_manage_visualize():
    """Bind viewer to a running emulator and return jump URL."""
    data = request.get_json(silent=True) or {}
    name = (data.get('name') or '').strip()
    adb_port = data.get('adb_port')
    bridge = ensure_emu_bridge()
    if not bridge:
        return jsonify(ok=False, error='bridge unavailable'), 503
    try:
        if not adb_port:
            items = bridge.list_emulators()
            hit = next((x for x in items if x.get('name') == name), None)
            if not hit or not hit.get('running'):
                return jsonify(ok=False, error='模拟器未运行，请先启动'), 400
            adb_port = hit.get('adb_port') or 5555
        bridge.set_active_emulator(adb_port=int(adb_port), avd_name=name)
        with _EMU_STATE['lock']:
            _EMU_STATE['last_png'] = None
            _EMU_STATE['last_jpeg'] = None
            _EMU_STATE['error'] = ''
        return jsonify(
            ok=True,
            url='/emulator',
            adb_port=int(adb_port),
            active_avd=name,
        )
    except Exception as ex:
        return jsonify(ok=False, error=str(ex)), 500


@app.route('/api/emulator/stream')
def api_emulator_stream():
    return Response(_emu_mjpeg_generator(), mimetype='multipart/x-mixed-replace; boundary=frame')


@app.route('/api/emulator/status')
def api_emulator_status():
    bridge = _get_emu_bridge()
    alive = bool(
        bridge
        and bridge.ready
        and getattr(bridge, 'is_alive', lambda: True)()
    )
    connecting = bool(_EMU_STATE.get('connecting'))
    # Page polls this every few seconds — kick auto-reconnect when dead.
    if bridge is not None and not alive and not connecting and not _EMU_STATE.get('busy'):
        _emu_schedule_reconnect('status-poll')
    w = int(bridge.width if bridge else 720)
    h = int(bridge.height if bridge else 1280)
    active_avd = getattr(bridge, 'active_avd', '') if bridge else ''
    adb_port = int(getattr(bridge, 'adb_port', 5555) or 5555) if bridge else 5555
    latency_ms = None
    smoothness_score = None
    jitter_ms = None
    if bridge is not None:
        try:
            qos = bridge.read_emu_qos() or {}
            rows = qos.get('emulators') or {}
            row = rows.get(active_avd) if active_avd else None
            if not isinstance(row, dict):
                for cand in rows.values():
                    if not isinstance(cand, dict):
                        continue
                    if cand.get('adb_port') == adb_port or cand.get('serial') == f'emulator-{adb_port - 1}':
                        row = cand
                        break
            if isinstance(row, dict):
                latency_ms = row.get('latency_ms')
                smoothness_score = row.get('smoothness_score')
                if smoothness_score is None and row.get('stutter_score') is not None:
                    try:
                        smoothness_score = max(0, min(100, 100 - int(row.get('stutter_score'))))
                    except Exception:
                        smoothness_score = None
                jitter_ms = row.get('jitter_ms')
        except Exception:
            pass
    return jsonify(
        ready=alive,
        connecting=connecting,
        error=_EMU_STATE.get('error') or (bridge.error if bridge else ''),
        width=w,
        height=h,
        orientation=('landscape' if w > h else 'portrait'),
        busy=bool(_EMU_STATE.get('busy')),
        busy_msg=_EMU_STATE.get('busy_msg') or '',
        progress_pct=int(_EMU_STATE.get('progress_pct') or 0),
        progress_phase=_EMU_STATE.get('progress_phase') or '',
        progress_pkg=_EMU_STATE.get('progress_pkg') or '',
        active_avd=active_avd,
        adb_port=adb_port,
        latency_ms=latency_ms,
        smoothness_score=smoothness_score,
        jitter_ms=jitter_ms,
    )


@app.route('/api/emulator/reconnect', methods=['POST'])
def api_emulator_reconnect():
    bridge = ensure_emu_bridge(force=True)
    return jsonify(ok=bool(bridge and bridge.ready), error=_EMU_STATE.get('error', ''))


@app.route('/api/emulator/recents')
def api_emulator_recents():
    """List recent-task apps on the emulator."""
    bridge = ensure_emu_bridge()
    if not bridge or not bridge.ready:
        return jsonify(ok=False, error='emulator offline', apps=[]), 503
    try:
        apps = bridge.list_recent_tasks()
        return jsonify(ok=True, apps=apps, count=len(apps))
    except Exception as ex:
        logger.warning('list recents failed: %s', ex)
        return jsonify(ok=False, error=str(ex), apps=[]), 500


@app.route('/api/emulator/info')
def api_emulator_info():
    """Memory / storage / network / device summary for the VM info panel."""
    bridge = ensure_emu_bridge()
    if not bridge or not bridge.ready:
        return jsonify(ok=False, error='emulator offline'), 503
    try:
        info = bridge.get_emulator_info()
        return jsonify(ok=True, **info)
    except Exception as ex:
        logger.warning('emulator info failed: %s', ex)
        return jsonify(ok=False, error=str(ex)), 500


@app.route('/api/emulator/recents/clear', methods=['POST'])
def api_emulator_recents_clear():
    """Clear one/many recent apps (force-stop + remove-task) or all clearable apps."""
    bridge = ensure_emu_bridge()
    if not bridge or not bridge.ready:
        return jsonify(ok=False, error='emulator offline'), 503
    # Detect dead ADB early (ready flag can lag)
    if getattr(bridge, 'error', None) and 'Connection refused' in str(bridge.error):
        return jsonify(ok=False, error='emulator adb offline (connection refused)'), 503
    body = request.get_json(force=True, silent=True) or {}
    package = str(body.get('package') or '').strip()
    packages = body.get('packages')
    if isinstance(packages, str):
        packages = [packages]
    if not isinstance(packages, list):
        packages = []
    packages = [str(p).strip() for p in packages if str(p).strip()]
    clear_all = bool(body.get('all'))
    try:
        if clear_all:
            result = bridge.clear_all_recent_apps()
            ok = not result.get('failed')
            return jsonify(ok=ok, **result)
        if packages:
            result = bridge.clear_recent_apps(packages)
            ok = not result.get('failed')
            return jsonify(ok=ok, **result)
        if not package:
            return jsonify(ok=False, error='package required'), 400
        task_id = body.get('task_id')
        try:
            task_id = int(task_id) if task_id is not None else None
        except (TypeError, ValueError):
            task_id = None
        result = bridge.clear_recent_app(package, task_id=task_id)
        return jsonify(ok=True, **result)
    except Exception as ex:
        logger.warning('clear recent failed: %s', ex)
        return jsonify(ok=False, error=str(ex)), 500


@app.route('/api/emulator/permissions')
def api_emulator_permissions():
    """List third-party installed packages for one-click permission grant."""
    bridge = ensure_emu_bridge()
    if not bridge or not bridge.ready:
        return jsonify(ok=False, error='emulator offline', apps=[]), 503
    try:
        apps = bridge.list_permission_targets()
        return jsonify(ok=True, apps=apps, count=len(apps))
    except Exception as ex:
        logger.warning('list permissions failed: %s', ex)
        return jsonify(ok=False, error=str(ex), apps=[]), 500


@app.route('/api/emulator/permissions/grant', methods=['POST'])
def api_emulator_permissions_grant():
    """Grant runtime/appops/a11y permissions for third-party packages (one-click)."""
    bridge = ensure_emu_bridge()
    if not bridge or not bridge.ready:
        return jsonify(ok=False, error='emulator offline'), 503
    if getattr(bridge, 'error', None) and 'Connection refused' in str(bridge.error):
        return jsonify(ok=False, error='emulator adb offline (connection refused)'), 503
    body = request.get_json(force=True, silent=True) or {}
    package = str(body.get('package') or '').strip()
    packages = body.get('packages')
    if isinstance(packages, str):
        packages = [packages]
    if not isinstance(packages, list):
        packages = []
    packages = [str(p).strip() for p in packages if str(p).strip()]
    grant_all = bool(body.get('all'))
    mode = str(body.get('mode') or 'all').strip().lower()
    if mode not in ('all', 'location'):
        mode = 'all'
    try:
        if grant_all:
            result = bridge.grant_known_permissions(None, mode=mode)
        elif packages:
            result = bridge.grant_known_permissions(packages, mode=mode)
        elif package:
            result = bridge.grant_known_permissions([package], mode=mode)
        else:
            # default: all third-party installed
            result = bridge.grant_known_permissions(None, mode=mode)
        ok = not result.get('failed')
        return jsonify(ok=ok, **result)
    except Exception as ex:
        logger.warning('grant permissions failed: %s', ex)
        return jsonify(ok=False, error=str(ex)), 500


@app.route('/api/emulator/restart', methods=['POST'])
def api_emulator_restart():
    """Restart ARM emulator qemu on target (async); re-applies DDS guest net after boot."""
    if _EMU_STATE.get('busy'):
        return jsonify(ok=False, error=_EMU_STATE.get('busy_msg') or 'emulator busy'), 409

    def _work():
        with _EMU_STATE['lock']:
            _EMU_STATE['busy'] = True
            _EMU_STATE['busy_msg'] = '正在重启虚拟机…'
            _EMU_STATE['progress_pct'] = 5
            _EMU_STATE['progress_phase'] = 'restart'
            _EMU_STATE['error'] = ''
        try:
            bridge = ensure_emu_bridge(force=True)
            if not bridge:
                raise RuntimeError('emulator bridge unavailable')
            with _EMU_STATE['lock']:
                _EMU_STATE['busy_msg'] = '正在停止并启动 qemu…'
                _EMU_STATE['progress_pct'] = 20
            out = bridge.restart_emulator(timeout=480.0)
            with _EMU_STATE['lock']:
                _EMU_STATE['busy_msg'] = '正在重连 ADB…'
                _EMU_STATE['progress_pct'] = 90
            ensure_emu_bridge(force=True)
            logger.info('emulator restart ok: %s', (out or '')[-400:])
            with _EMU_STATE['lock']:
                _EMU_STATE['error'] = ''
                _EMU_STATE['progress_pct'] = 100
                _EMU_STATE['busy_msg'] = '重启完成'
        except Exception as ex:
            logger.warning('emulator restart failed: %s', ex)
            with _EMU_STATE['lock']:
                _EMU_STATE['error'] = str(ex)
                _EMU_STATE['busy_msg'] = f'重启失败: {ex}'
        finally:
            time.sleep(1.5)
            with _EMU_STATE['lock']:
                _EMU_STATE['busy'] = False
                _EMU_STATE['progress_pct'] = 0
                _EMU_STATE['progress_phase'] = ''
                if _EMU_STATE.get('busy_msg', '').startswith('重启完成'):
                    _EMU_STATE['busy_msg'] = ''

    threading.Thread(target=_work, daemon=True, name='emu-restart').start()
    return jsonify(ok=True, message='restart started')


@app.route('/api/emulator/ensure-guest-net', methods=['POST'])
def api_emulator_ensure_guest_net():
    """Refresh guest DDS CONNECT gateway before voice inject."""
    if _EMU_STATE.get('busy'):
        return jsonify(ok=False, error=_EMU_STATE.get('busy_msg') or 'emulator busy'), 409
    body = request.get_json(force=True, silent=True) or {}
    force = bool(body.get('force'))
    try:
        bridge = ensure_emu_bridge()
        if not bridge or not bridge.ready:
            return jsonify(ok=False, error='emulator offline'), 503
        result = bridge.ensure_guest_net(timeout=120.0, force=force)
        logger.info('emulator ensure-guest-net done ok=%s', result.get('ok'))
        return jsonify(**result)
    except Exception as ex:
        logger.warning('emulator ensure-guest-net failed: %s', ex)
        return jsonify(ok=False, error=str(ex)), 500


@app.route('/api/emulator/fix-mic', methods=['POST'])
def api_emulator_fix_mic():
    """Diagnose + restore Pulse VirtualMic / hostmicon (no qemu restart)."""
    if _EMU_STATE.get('busy'):
        return jsonify(ok=False, error=_EMU_STATE.get('busy_msg') or 'emulator busy'), 409
    try:
        bridge = ensure_emu_bridge()
        if not bridge or not bridge.ready:
            return jsonify(ok=False, error='emulator offline'), 503
        result = bridge.fix_virtual_mic(timeout=120.0)
        logger.info(
            'emulator fix-mic done need_restart=%s rec=%s',
            result.get('need_restart'),
            result.get('recommendation'),
        )
        return jsonify(ok=True, **result)
    except Exception as ex:
        logger.warning('emulator fix-mic failed: %s', ex)
        return jsonify(ok=False, error=str(ex)), 500


@app.route('/api/emulator/ensure-mic', methods=['POST'])
def api_emulator_ensure_mic():
    """Pre-inject gate: diagnose, fix if needed, always refresh hostmicon."""
    if _EMU_STATE.get('busy'):
        return jsonify(ok=False, error=_EMU_STATE.get('busy_msg') or 'emulator busy'), 409
    body = request.get_json(force=True, silent=True) or {}
    force = bool(body.get('force'))
    try:
        bridge = ensure_emu_bridge()
        if not bridge or not bridge.ready:
            return jsonify(ok=False, error='emulator offline'), 503
        result = bridge.ensure_virtual_mic(timeout=120.0, force=force)
        after = result.get('after') or {}
        if 'healthy' not in result:
            result['healthy'] = bool(after.get('healthy'))
        logger.info(
            'emulator ensure-mic done healthy=%s need_restart=%s skipped=%s rec=%s',
            result.get('healthy'),
            result.get('need_restart'),
            result.get('skipped_full_fix'),
            result.get('recommendation'),
        )
        return jsonify(ok=True, **result)
    except Exception as ex:
        logger.warning('emulator ensure-mic failed: %s', ex)
        return jsonify(ok=False, error=str(ex)), 500


@app.route('/api/emulator/orientation', methods=['GET', 'POST'])
def api_emulator_orientation():
    """Get or set emulator display orientation (portrait / landscape)."""
    bridge = ensure_emu_bridge()
    if not bridge or not bridge.ready:
        return jsonify(ok=False, error='emulator offline'), 503
    try:
        if request.method == 'GET':
            info = bridge.get_display_orientation()
            return jsonify(ok=True, **info)
        body = request.get_json(force=True, silent=True) or {}
        orientation = (
            body.get('orientation')
            or body.get('mode')
            or body.get('value')
            or ''
        )
        info = bridge.set_display_orientation(str(orientation))
        return jsonify(ok=True, **info)
    except ValueError as ex:
        return jsonify(ok=False, error=str(ex)), 400
    except Exception as ex:
        logger.warning('emulator orientation failed: %s', ex)
        return jsonify(ok=False, error=_emu_friendly_error(ex)), 500


@app.route('/api/emulator/key', methods=['POST'])
def api_emulator_key():
    bridge = ensure_emu_bridge()
    if not bridge or not bridge.ready:
        return jsonify(ok=False, error='emulator offline'), 503
    body = request.get_json(force=True, silent=True) or {}
    code = str(body.get('code', '')).strip().upper()
    keycodes = {
        'HOME': '3', 'BACK': '4', 'APP_SWITCH': '187', 'RECENTS': '187',
        'POWER': '26', 'VOLUME_UP': '24', 'VOLUME_DOWN': '25', 'MUTE': '164',
        'ENTER': '66', 'DPAD_UP': '19', 'DPAD_DOWN': '20', 'DPAD_LEFT': '21',
        'DPAD_RIGHT': '22', 'MENU': '82', 'NOTIFICATION': '83', 'WAKE': '224',
        'DEL': '67', 'DELETE': '67', 'BACKSPACE': '67',
        'CLEAR_TEXT': 'CLEAR_TEXT', 'SOFT_KB': 'SOFT_KB', 'KEYBOARD': 'SOFT_KB',
    }
    if code not in keycodes:
        return jsonify(ok=False, error='unknown code'), 400
    try:
        if code == 'WAKE':
            bridge.wake()
        elif code == 'DEL' or code == 'DELETE' or code == 'BACKSPACE':
            times = int(body.get('times', 1) or 1)
            bridge.delete_char(times=times)
        elif code == 'CLEAR_TEXT':
            bridge.clear_text_field()
        elif code == 'SOFT_KB' or code == 'KEYBOARD':
            state = bridge.toggle_soft_keyboard()
            return jsonify(ok=True, soft_keyboard=state)
        else:
            bridge.keyevent(keycodes[code])
        return jsonify(ok=True)
    except Exception as ex:
        return jsonify(ok=False, error=_emu_friendly_error(ex)), 500


@app.route('/api/emulator/tap', methods=['POST'])
def api_emulator_tap():
    bridge = ensure_emu_bridge()
    if not bridge or not bridge.ready:
        return jsonify(ok=False, error='emulator offline'), 503
    body = request.get_json(force=True, silent=True) or {}
    try:
        x = int(float(body['nx']) * bridge.width)
        y = int(float(body['ny']) * bridge.height)
        bridge.tap(x, y)
        return jsonify(ok=True, x=x, y=y)
    except Exception as ex:
        return jsonify(ok=False, error=str(ex)), 500


@app.route('/api/emulator/swipe', methods=['POST'])
def api_emulator_swipe():
    bridge = ensure_emu_bridge()
    if not bridge or not bridge.ready:
        return jsonify(ok=False, error='emulator offline'), 503
    body = request.get_json(force=True, silent=True) or {}
    try:
        direction = str(body.get('direction', 'left')).lower()
        w, h = bridge.width, bridge.height
        dur = int(body.get('duration', 300))
        mid_x, mid_y = w // 2, h // 2
        if direction == 'left':
            x1, y1, x2, y2 = int(w * 0.8), mid_y, int(w * 0.2), mid_y
        elif direction == 'right':
            x1, y1, x2, y2 = int(w * 0.2), mid_y, int(w * 0.8), mid_y
        elif direction == 'up':
            x1, y1, x2, y2 = mid_x, int(h * 0.75), mid_x, int(h * 0.25)
        else:
            x1, y1, x2, y2 = mid_x, int(h * 0.25), mid_x, int(h * 0.75)
        bridge.swipe(x1, y1, x2, y2, dur)
        return jsonify(ok=True)
    except Exception as ex:
        return jsonify(ok=False, error=str(ex)), 500


@app.route('/api/emulator/text', methods=['POST'])
def api_emulator_text():
    bridge = ensure_emu_bridge()
    if not bridge or not bridge.ready:
        return jsonify(ok=False, error='emulator offline'), 503
    body = request.get_json(force=True, silent=True) or {}
    text = str(body.get('text', ''))
    if not text:
        return jsonify(ok=False, error='empty'), 400
    press_enter = bool(body.get('press_enter') or body.get('enter'))
    prefer_package = str(body.get('package') or 'com.huawei.aisftvassistant')
    tap_x = tap_y = None
    try:
        if 'x' in body and 'y' in body:
            tap_x = int(float(body['x']))
            tap_y = int(float(body['y']))
        elif 'nx' in body and 'ny' in body:
            tap_x = int(float(body['nx']) * bridge.width)
            tap_y = int(float(body['ny']) * bridge.height)
    except Exception:
        tap_x = tap_y = None
    # If user provided a screen tap, target that field; otherwise keep current focus
    # and do not jump back to the first EditText.
    autofocus = tap_x is not None
    try:
        detail = bridge.type_in_edittext(
            text,
            press_enter=press_enter,
            prefer_package=prefer_package,
            autofocus=autofocus,
            tap_x=tap_x,
            tap_y=tap_y,
        )
        return jsonify(ok=True, message='已注入', **detail)
    except Exception as ex:
        return jsonify(ok=False, error=str(ex)), 500


@app.route('/api/download/apk/info')
def latest_apk_info():
    """获取最新 APK 信息。"""
    info = get_latest_apk_info()
    if not info:
        return jsonify({'success': False, 'message': '暂无可用 APK'})
    return jsonify({'success': True, 'apk': info})

@app.route('/api/download/apk')
def download_latest_apk():
    """下载 output 目录中最新的 APK。"""
    pkg = _software_pkg('testapp')
    path, _ = resolve_software_apk_path(pkg) if pkg else (None, None)
    if not path:
        return jsonify({'success': False, 'message': '暂无可用 APK'}), 404
    return send_file(path, as_attachment=True, download_name=os.path.basename(path))


@app.route('/api/download/apk/devicesdkdemo/info')
def devicesdkdemo_apk_info():
    """获取 DeviceSDK Demo APK 信息。"""
    info = get_devicesdkdemo_apk_info()
    if not info:
        return jsonify({'success': False, 'message': '暂无可用 APK，请先上传'})
    return jsonify({'success': True, 'apk': info})


@app.route('/api/download/apk/devicesdkdemo')
def download_devicesdkdemo_apk():
    """下载已上传的 DeviceSDK Demo APK。"""
    filepath = get_devicesdkdemo_apk_path()
    if not filepath or not os.path.isfile(filepath):
        return jsonify({'success': False, 'message': '暂无可用 APK，请先上传'}), 404
    return send_file(
        filepath,
        as_attachment=True,
        download_name=os.path.basename(filepath),
    )


@app.route('/api/download/apk/devicesdkdemo/upload', methods=['POST'])
def upload_devicesdkdemo_apk():
    """上传 DeviceSDK Demo APK（写入云端v3托管目录）。"""
    return _do_upload_software_package('devicesdkdemo_cloud_v3')


@app.route('/api/download/packages')
def list_software_packages():
    """软件下载页：安装包列表。"""
    packages = [get_software_apk_info(pkg) for pkg in SOFTWARE_PACKAGES]
    return jsonify({'success': True, 'packages': packages})


def _download_notepad_path():
    return os.path.join(app.config['SOFTWARE_APKS_ROOT'], 'download_notepad.txt')


# Excel 数据集经记事本通道分块保存（公司代理只放行该精确 URL）
_excel_save_sessions = {}
_excel_save_sessions_lock = threading.Lock()
EXCEL_SAVE_SESSION_TTL_SEC = 600


def _excel_save_session_cleanup(now=None):
    now = time.time() if now is None else now
    dead = [
        sid for sid, sess in _excel_save_sessions.items()
        if now - sess.get('ts', 0) > EXCEL_SAVE_SESSION_TTL_SEC
    ]
    for sid in dead:
        _excel_save_sessions.pop(sid, None)


def _handle_dataset_save_via_notepad_content(content):
    """记事本 content 协议：DS1|begin|id|name|total|b64(headersJson)
    DS1|chunk|id|offset|b64(rowsJson)  /  DS1|done|id
    返回 Response 或 None（非协议则交给普通记事本逻辑）。
    """
    if not isinstance(content, str) or not content.startswith('DS1|'):
        return None
    parts = content.split('|')
    if len(parts) < 3:
        return jsonify({'success': False, 'message': '数据集分块协议无效'}), 400
    op = parts[1]
    with _excel_save_sessions_lock:
        _excel_save_session_cleanup()
        if op == 'begin':
            if len(parts) < 6:
                return jsonify({'success': False, 'message': 'begin 参数不足'}), 400
            sid, name, total_s, headers_b64 = parts[2], parts[3], parts[4], parts[5]
            try:
                total = int(total_s)
                headers = json.loads(base64.b64decode(headers_b64).decode('utf-8'))
            except Exception as e:
                return jsonify({'success': False, 'message': f'begin 解析失败: {e}'}), 400
            if not isinstance(headers, list):
                return jsonify({'success': False, 'message': 'headers 无效'}), 400
            safe = secure_filename(name or '')
            if not safe or safe != name:
                return jsonify({'success': False, 'message': '无效的数据集名称'}), 400
            if not os.path.isdir(os.path.join(app.config['EXCEL_DATASETS_FOLDER'], safe)):
                return jsonify({'success': False, 'message': '数据集不存在'}), 404
            _excel_save_sessions[sid] = {
                'name': name,
                'headers': headers,
                'total': max(0, total),
                'rows': {},
                'ts': time.time(),
            }
            logger.info('notepad-ds begin: id=%s name=%s total=%s', sid, name, total)
            return jsonify({'success': True, 'message': 'begin ok', 'id': sid})

        if op == 'chunk':
            if len(parts) < 5:
                return jsonify({'success': False, 'message': 'chunk 参数不足'}), 400
            sid, offset_s, rows_b64 = parts[2], parts[3], parts[4]
            sess = _excel_save_sessions.get(sid)
            if not sess:
                return jsonify({'success': False, 'message': '分块会话不存在或已过期'}), 400
            try:
                offset = int(offset_s)
                rows = json.loads(base64.b64decode(rows_b64).decode('utf-8'))
            except Exception as e:
                return jsonify({'success': False, 'message': f'chunk 解析失败: {e}'}), 400
            if not isinstance(rows, list):
                return jsonify({'success': False, 'message': 'rows 无效'}), 400
            sess['rows'][offset] = rows
            sess['ts'] = time.time()
            got = sum(len(v) for v in sess['rows'].values())
            return jsonify({'success': True, 'message': 'chunk ok', 'received': got})

        if op == 'done':
            sid = parts[2]
            sess = _excel_save_sessions.pop(sid, None)
            if not sess:
                return jsonify({'success': False, 'message': '分块会话不存在或已过期'}), 400
            ordered = []
            for offset in sorted(sess['rows'].keys()):
                ordered.extend(sess['rows'][offset])
            name = sess.get('name')
            headers = sess.get('headers')
            total = sess.get('total')

    if op == 'done':
        logger.info(
            'notepad-ds done: id=%s name=%s rows=%s expected=%s',
            sid, name, len(ordered), total,
        )
        ok, message, code = _write_excel_dataset_from_grid(name, headers, ordered)
        return jsonify({'success': ok, 'message': message, 'rows': len(ordered)}), code

    return jsonify({'success': False, 'message': f'未知分块操作: {op}'}), 400


@app.route('/api/download/notepad', methods=['GET', 'POST'])
def download_notepad_api():
    """软件下载页记事本：读取 / 保存纯文本。

    POST 也承载 Excel 数据集分块保存（content 以 DS1| 开头），
    因部分公司代理仅放行此精确 URL。
    """
    path = _download_notepad_path()
    if request.method == 'GET':
        content = ''
        if os.path.isfile(path):
            try:
                with open(path, 'r', encoding='utf-8') as f:
                    content = f.read()
            except OSError as e:
                return jsonify({'success': False, 'message': f'读取失败: {e}'}), 500
        return jsonify({'success': True, 'content': content})

    data = request.get_json(silent=True) or {}
    content = data.get('content')
    if content is None:
        content = request.form.get('content', '')
    if not isinstance(content, str):
        return jsonify({'success': False, 'message': 'content 必须是字符串'}), 400

    ds_resp = _handle_dataset_save_via_notepad_content(content)
    if ds_resp is not None:
        return ds_resp

    if len(content.encode('utf-8')) > 2 * 1024 * 1024:
        return jsonify({'success': False, 'message': '内容过大（上限约 2MB）'}), 400
    try:
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, 'w', encoding='utf-8') as f:
            f.write(content)
    except OSError as e:
        return jsonify({'success': False, 'message': f'保存失败: {e}'}), 500
    return jsonify({'success': True, 'message': '已保存'})


def _write_excel_dataset_from_grid(dataset_name, headers, rows):
    """Validate dataset name and write headers/rows to dataset.xlsx. Returns (ok, message, http_code)."""
    safe_name = secure_filename(dataset_name or '')
    if not safe_name or safe_name != dataset_name:
        return False, '无效的数据集名称', 400
    dataset_path = os.path.join(app.config['EXCEL_DATASETS_FOLDER'], safe_name)
    if not os.path.isdir(dataset_path):
        return False, '数据集不存在', 404
    if not isinstance(headers, list) or not isinstance(rows, list):
        return False, '需要 headers/rows 数组', 400
    if not headers:
        headers = DEFAULT_EXCEL_HEADERS[:]
    dest_path = os.path.join(dataset_path, EXCEL_DATASET_FILENAME)
    try:
        write_excel_dataset_grid(dest_path, headers, rows)
    except ImportError:
        return False, '服务端缺少 openpyxl，请执行 pip install openpyxl', 500
    except Exception as e:
        logger.error('Write excel dataset grid failed: %s', e)
        return False, f'保存 Excel 失败: {e}', 500
    scan_excel_datasets()
    return True, '数据集内容已保存', 200


@app.route('/api/download/dataset-save', methods=['POST'])
def download_dataset_save_api():
    """保存 Excel 数据集表格内容。

    走 /api/download/* 前缀（与记事本相同），以绕过部分公司代理对
    /api/excel-datasets/*/upload|content 的 403 拦截。请求体同记事本：JSON。
    """
    data = request.get_json(silent=True, force=True) or {}
    if not isinstance(data, dict):
        data = {}
    if not data.get('name'):
        data['name'] = request.form.get('name') or request.args.get('name')
    if 'headers' not in data and 'headers' in request.form:
        try:
            data['headers'] = json.loads(request.form.get('headers') or '[]')
        except Exception:
            data['headers'] = None
    if 'rows' not in data and 'rows' in request.form:
        try:
            data['rows'] = json.loads(request.form.get('rows') or '[]')
        except Exception:
            data['rows'] = None

    name = (data.get('name') or '').strip()
    headers = data.get('headers')
    rows = data.get('rows')
    try:
        approx = len(json.dumps({'headers': headers, 'rows': rows}, ensure_ascii=False).encode('utf-8'))
    except Exception:
        approx = request.content_length or 0
    if approx > 2 * 1024 * 1024:
        return jsonify({'success': False, 'message': '内容过大（上限约 2MB）'}), 400
    logger.info(
        'dataset-save via download channel: name=%s rows=%s body_len=%s',
        name,
        len(rows) if isinstance(rows, list) else None,
        request.content_length,
    )
    ok, message, code = _write_excel_dataset_from_grid(name, headers, rows)
    return jsonify({'success': ok, 'message': message}), code


def _shared_safe_filename(name: str) -> str:
    """Sanitize a single path segment; keep Chinese names."""
    name = (name or '').strip().replace('\\', '/').split('/')[-1]
    name = name.replace('\x00', '')
    for ch in '<>:"|?*':
        name = name.replace(ch, '_')
    name = name.strip(' .')
    if not name or name in ('.', '..'):
        return ''
    return name[:200]


def _shared_safe_relpath(relpath: str):
    """Sanitize relative path (may include folders). Returns (posix_rel, abs_path)."""
    raw = (relpath or '').replace('\\', '/').strip('/')
    if not raw:
        return None, None
    parts = []
    for part in raw.split('/'):
        safe = _shared_safe_filename(part)
        if not safe:
            return None, None
        parts.append(safe)
    root = _shared_files_root()
    path = os.path.abspath(os.path.join(root, *parts))
    if path != root and not path.startswith(root + os.sep):
        return None, None
    return '/'.join(parts), path


def _shared_files_root() -> str:
    root = os.path.abspath(app.config['SHARED_FILES_FOLDER'])
    os.makedirs(root, exist_ok=True)
    return root


def _shared_resolve_path(filename: str):
    return _shared_safe_relpath(filename)


def _shared_unique_path(safe_rel: str, abs_path: str):
    """If path exists, append _N before extension (last segment only)."""
    if not os.path.exists(abs_path):
        return safe_rel, abs_path
    root = _shared_files_root()
    parent_rel = '/'.join(safe_rel.split('/')[:-1])
    base = safe_rel.split('/')[-1]
    stem, ext = os.path.splitext(base)
    n = 1
    while n <= 9999:
        cand_base = f'{stem}_{n}{ext}'
        cand_rel = f'{parent_rel}/{cand_base}' if parent_rel else cand_base
        cand_path = os.path.join(root, *cand_rel.split('/'))
        if not os.path.exists(cand_path):
            return cand_rel, cand_path
        n += 1
    return None, None


def _shared_entry_url(rel: str) -> str:
    # Keep '/' as path separators; encode each segment.
    enc = '/'.join(urllib.parse.quote(seg, safe='') for seg in rel.split('/'))
    return build_ui_path(f'/api/download/shared-files/{enc}')


@app.route('/api/download/shared-files')
def list_shared_files():
    """软件下载页「文件下载」弹窗：根目录文件/文件夹列表。"""
    root = _shared_files_root()
    entries = []
    try:
        names = os.listdir(root)
    except OSError as ex:
        return jsonify({'success': False, 'message': str(ex)}), 500

    for name in names:
        if name.startswith('.'):
            continue
        path = os.path.join(root, name)
        try:
            if os.path.isdir(path):
                size = 0
                latest = os.path.getmtime(path)
                for dirpath, dirnames, filenames in os.walk(path):
                    dirnames[:] = [d for d in dirnames if not d.startswith('.')]
                    for fn in filenames:
                        if fn.startswith('.'):
                            continue
                        fp = os.path.join(dirpath, fn)
                        if not os.path.isfile(fp):
                            continue
                        size += os.path.getsize(fp)
                        latest = max(latest, os.path.getmtime(fp))
                entries.append({
                    'name': name,
                    'type': 'dir',
                    'size': size,
                    'modified': latest,
                    'download_url': _shared_entry_url(name),
                })
            elif os.path.isfile(path):
                entries.append({
                    'name': name,
                    'type': 'file',
                    'size': os.path.getsize(path),
                    'modified': os.path.getmtime(path),
                    'download_url': _shared_entry_url(name),
                })
        except OSError:
            continue

    entries.sort(key=lambda e: (0 if e.get('type') == 'dir' else 1, -(e.get('modified') or 0), e.get('name') or ''))
    return jsonify({'success': True, 'files': entries})


@app.route('/api/download/shared-files/upload', methods=['POST'])
def upload_shared_file():
    """Upload one or more files; optional relative_path[] preserves folder structure."""
    files = request.files.getlist('file')
    if not files:
        # Compat: single field
        if 'file' in request.files:
            files = [request.files['file']]
    files = [f for f in files if f and f.filename]
    if not files:
        return jsonify({'success': False, 'message': '未选择文件'}), 400

    rel_paths = request.form.getlist('relative_path')
    saved = []
    errors = []
    for idx, file in enumerate(files):
        rel = ''
        if idx < len(rel_paths) and rel_paths[idx]:
            rel = rel_paths[idx]
        else:
            # webkitdirectory may put path only in filename on some browsers
            rel = file.filename or ''
        safe, path = _shared_safe_relpath(rel)
        if not safe or not path:
            errors.append(f'非法路径: {rel}')
            continue
        safe, path = _shared_unique_path(safe, path)
        if not safe or not path:
            errors.append(f'无法保存: {rel}')
            continue
        os.makedirs(os.path.dirname(path), exist_ok=True)
        file.save(path)
        saved.append({
            'name': safe,
            'type': 'file',
            'size': os.path.getsize(path),
            'modified': os.path.getmtime(path),
            'download_url': _shared_entry_url(safe),
        })
        logger.info('Shared file uploaded: %s', path)

    if not saved:
        return jsonify({'success': False, 'message': '; '.join(errors) or '上传失败'}), 400
    msg = f'成功上传 {len(saved)} 个文件'
    if errors:
        msg += f'，{len(errors)} 个失败'
    return jsonify({
        'success': True,
        'message': msg,
        'files': saved,
        'file': saved[0],
        'errors': errors,
    })


@app.route('/api/download/shared-files/<path:filename>')
def download_shared_file(filename):
    safe, path = _shared_resolve_path(filename)
    if not safe or not path:
        return jsonify({'success': False, 'message': '文件不存在'}), 404
    if os.path.isfile(path):
        resp = send_file(path, as_attachment=True, download_name=os.path.basename(path))
        try:
            resp.headers['Content-Length'] = str(os.path.getsize(path))
        except OSError:
            pass
        return resp
    if os.path.isdir(path):
        buf = io.BytesIO()
        base = os.path.basename(safe.rstrip('/')) or 'folder'
        with zipfile.ZipFile(buf, 'w', zipfile.ZIP_DEFLATED) as zf:
            for dirpath, dirnames, filenames in os.walk(path):
                dirnames[:] = [d for d in dirnames if not d.startswith('.')]
                for fn in filenames:
                    if fn.startswith('.'):
                        continue
                    fp = os.path.join(dirpath, fn)
                    if not os.path.isfile(fp):
                        continue
                    arc = os.path.relpath(fp, path).replace('\\', '/')
                    zf.write(fp, arcname=arc)
        data = buf.getvalue()
        resp = send_file(
            io.BytesIO(data),
            as_attachment=True,
            download_name=f'{base}.zip',
            mimetype='application/zip',
        )
        resp.headers['Content-Length'] = str(len(data))
        return resp
    return jsonify({'success': False, 'message': '文件不存在'}), 404


@app.route('/api/download/shared-files/<path:filename>', methods=['DELETE'])
def delete_shared_file(filename):
    safe, path = _shared_resolve_path(filename)
    if not safe or not path:
        return jsonify({'success': False, 'message': '文件不存在'}), 404
    try:
        if os.path.isfile(path):
            os.remove(path)
            # Clean empty parent dirs under shared root
            root = _shared_files_root()
            parent = os.path.dirname(path)
            while parent.startswith(root + os.sep):
                try:
                    os.rmdir(parent)
                except OSError:
                    break
                parent = os.path.dirname(parent)
        elif os.path.isdir(path):
            import shutil
            shutil.rmtree(path)
        else:
            return jsonify({'success': False, 'message': '文件不存在'}), 404
    except OSError as ex:
        return jsonify({'success': False, 'message': str(ex)}), 500
    logger.info('Shared path deleted: %s', path)
    return jsonify({'success': True, 'message': '已删除'})


@app.route('/api/download/packages/<pkg_id>/file')
def download_software_package(pkg_id):
    pkg = _software_pkg(pkg_id)
    if not pkg:
        return jsonify({'success': False, 'message': '未知安装包'}), 404
    path, _ = resolve_software_apk_path(pkg)
    if not path:
        return jsonify({'success': False, 'message': '暂无可用 APK'}), 404
    resp = send_file(path, as_attachment=True, download_name=os.path.basename(path))
    try:
        resp.headers['Content-Length'] = str(os.path.getsize(path))
    except OSError:
        pass
    return resp


@app.route('/api/download/packages/<pkg_id>/upload', methods=['POST'])
def upload_software_package(pkg_id):
    return _do_upload_software_package(pkg_id)


def _do_upload_software_package(pkg_id):
    pkg = _software_pkg(pkg_id)
    if not pkg:
        return jsonify({'success': False, 'message': '未知安装包'}), 404
    if not pkg.get('uploadable', True):
        return jsonify({'success': False, 'message': '该安装包不支持上传'}), 400
    if 'file' not in request.files:
        return jsonify({'success': False, 'message': '未选择文件'}), 400
    file = request.files['file']
    if not file or file.filename == '':
        return jsonify({'success': False, 'message': '未选择文件'}), 400
    original_name = secure_filename(file.filename)
    if not original_name.lower().endswith('.apk'):
        return jsonify({'success': False, 'message': '仅支持 .apk 文件'}), 400

    dest_dir = os.path.dirname(_software_managed_path(pkg))
    os.makedirs(dest_dir, exist_ok=True)
    import shutil
    if pkg_id == 'aisf_voice' and 'phoneRelease' in original_name:
        stamped = os.path.join(dest_dir, original_name)
        file.save(stamped)
        dest_path = _software_managed_path(pkg)
        if os.path.abspath(stamped) != os.path.abspath(dest_path):
            shutil.copy2(stamped, dest_path)
    elif pkg_id == 'devicesdkdemo_cloud_v3':
        # 统一存为 devicesdk-yyyyMMdd-HHmmss.apk
        if original_name.lower().startswith('devicesdk-') and original_name.lower().endswith('.apk'):
            save_name = original_name
        else:
            save_name = time.strftime('devicesdk-%Y%m%d-%H%M%S.apk')
        dest_path = os.path.join(dest_dir, save_name)
        file.save(dest_path)
    elif pkg_id == 'testapp_guangzhong':
        # 统一存为 testapp-guangzhong-yyyyMMdd-HHmmss.apk
        if (original_name.lower().startswith('testapp-guangzhong-')
                and original_name.lower().endswith('.apk')):
            save_name = original_name
        else:
            save_name = time.strftime('testapp-guangzhong-%Y%m%d-%H%M%S.apk')
        dest_path = os.path.join(dest_dir, save_name)
        file.save(dest_path)
    elif pkg_id == 'devicesdkdemo_guangzhong':
        # 统一存为 devicesdk-guangzhong-yyyyMMdd-HHmmss.apk
        if (original_name.lower().startswith('devicesdk-guangzhong-')
                and original_name.lower().endswith('.apk')):
            save_name = original_name
        else:
            save_name = time.strftime('devicesdk-guangzhong-%Y%m%d-%H%M%S.apk')
        dest_path = os.path.join(dest_dir, save_name)
        file.save(dest_path)
    elif pkg_id == 'albummemory':
        # 统一存为 AlbumMemory-yyyyMMdd-HHmm.apk，精确到分钟
        if original_name.startswith('AlbumMemory-') and original_name.lower().endswith('.apk'):
            save_name = original_name
        else:
            save_name = time.strftime('AlbumMemory-%Y%m%d-%H%M.apk')
        dest_path = os.path.join(dest_dir, save_name)
        file.save(dest_path)
    else:
        dest_path = _software_managed_path(pkg)
        file.save(dest_path)
    logger.info('Software APK uploaded: %s -> %s (from %s)', pkg_id, dest_path, original_name)
    info = get_software_apk_info(pkg)
    return jsonify({'success': True, 'message': '上传成功', 'apk': info})


@app.route('/api/download/packages/<pkg_id>/install-emu', methods=['POST'])
def install_software_package_to_emu(pkg_id):
    pkg = _software_pkg(pkg_id)
    if not pkg:
        return jsonify({'success': False, 'message': '未知安装包'}), 404
    if not pkg.get('installable', True):
        return jsonify({'success': False, 'message': '该安装包不支持安装到虚拟机'}), 400
    path, _ = resolve_software_apk_path(pkg)
    if not path:
        return jsonify({'success': False, 'message': '暂无可用 APK，请先上传'}), 404

    bridge = ensure_emu_bridge()
    if not bridge or not bridge.ready:
        return jsonify({'success': False, 'message': '安卓虚拟机离线，请先打开虚拟机页面并确认在线'}), 503
    if _EMU_STATE.get('busy'):
        return jsonify({'success': False, 'message': _EMU_STATE.get('busy_msg') or '虚拟机正忙，请稍后再试'}), 409

    def _set_progress(phase, pct, msg):
        _EMU_STATE['busy'] = True
        _EMU_STATE['busy_msg'] = msg
        _EMU_STATE['progress_pct'] = int(pct)
        _EMU_STATE['progress_phase'] = phase or ''
        _EMU_STATE['progress_pkg'] = pkg_id

    _set_progress('start', 0, f"正在安装 {pkg.get('name') or pkg_id}，画面暂停刷新…")
    try:
        logger.info('Installing %s to emulator from %s', pkg_id, path)
        out = bridge.install_apk(
            path,
            timeout=900.0,
            package_name=pkg.get('package_name') or '',
            replace_incompatible=True,
            progress_cb=_set_progress,
        )
        package_name = pkg.get('package_name') or ''
        perm_info = {}
        if package_name:
            _set_progress('perms', 95, f"正在授予 {pkg.get('name') or pkg_id} 权限…")
            try:
                perm_info = bridge.grant_package_permissions(package_name) or {}
                logger.info(
                    'Post-install perms for %s: granted=%s a11y=%s',
                    package_name,
                    perm_info.get('granted'),
                    perm_info.get('a11y'),
                )
            except Exception as perr:
                logger.warning('grant permissions failed for %s: %s', package_name, perr)
                perm_info = {'error': str(perr)}
        _set_progress('done', 100, '安装成功（权限已处理）')
        return jsonify({
            'success': True,
            'message': '安装成功',
            'output': (out or '').strip(),
            'package_name': package_name,
            'filename': os.path.basename(path),
            'permissions': perm_info,
        })
    except Exception as ex:
        logger.warning('install-emu failed for %s: %s', pkg_id, ex)
        return jsonify({'success': False, 'message': str(ex)}), 500
    finally:
        _EMU_STATE['busy'] = False
        _EMU_STATE['busy_msg'] = ''
        _EMU_STATE['progress_pct'] = 0
        _EMU_STATE['progress_phase'] = ''
        _EMU_STATE['progress_pkg'] = ''


@app.route('/api/download/packages/install-progress')
def software_package_install_progress():
    """Poll install progress while /install-emu request is in flight."""
    return jsonify({
        'success': True,
        'busy': bool(_EMU_STATE.get('busy')),
        'message': _EMU_STATE.get('busy_msg') or '',
        'percent': int(_EMU_STATE.get('progress_pct') or 0),
        'phase': _EMU_STATE.get('progress_phase') or '',
        'pkg_id': _EMU_STATE.get('progress_pkg') or '',
    })


# ========== API: Logs ==========

@app.route('/api/logs')
def get_logs():
    """Get server logs (last 24 hours)."""
    hours = int(request.args.get('hours', 24))
    logs = get_log_lines(hours)
    return jsonify({'success': True, 'logs': logs})

@app.route('/api/logs/download')
def download_server_log():
    """下载 Web 服务运行日志 webserver.log。"""
    log_file = os.path.join(LOG_DIR, 'webserver.log')
    if not os.path.isfile(log_file):
        return jsonify({'success': False, 'message': 'webserver.log 不存在'}), 404
    return send_file(log_file, as_attachment=True, download_name='webserver.log')

# ========== API: Devices ==========

@app.route('/api/devices')
def get_devices():
    """Get WebSocket-online devices + ADB-visible devices (local / USB bridge)."""
    online = get_online_devices()
    try:
        adb_devices = get_adb_visible_devices()
    except Exception as e:
        logger.warning('get_adb_visible_devices failed: %s', e)
        adb_devices = []
    return jsonify({
        'success': True,
        'devices': online,
        'adb_devices': adb_devices,
    })


@app.route('/api/devices/<device_id>/console-logs')
def api_device_console_logs(device_id):
    """首页：拉取某台 WS 设备的控制台日志（内存缓冲）。"""
    try:
        since_seq = int(request.args.get('since_seq') or 0)
    except (TypeError, ValueError):
        since_seq = 0
    try:
        limit = int(request.args.get('limit') or 1500)
    except (TypeError, ValueError):
        limit = 1500
    limit = max(50, min(limit, DEVICE_CONSOLE_MAX_LINES))
    lines, seq = get_device_console_lines(device_id, since_seq=since_seq, limit=limit)
    online_ids = {d.get('device_id') for d in get_online_devices()}
    return jsonify({
        'success': True,
        'deviceId': device_id,
        'online': device_id in online_ids,
        'seq': seq,
        'lines': lines,
        'count': len(lines),
    })


@app.route('/api/devices/<device_id>/console-logs/download')
def api_device_console_logs_download(device_id):
    """下载某台设备控制台日志文件。"""
    path = _device_console_file(device_id)
    # 若文件为空/不存在，先把内存缓冲落盘
    lines, _seq = get_device_console_lines(device_id, limit=DEVICE_CONSOLE_MAX_LINES)
    if lines and (not os.path.isfile(path) or os.path.getsize(path) == 0):
        try:
            with open(path, 'w', encoding='utf-8', errors='replace') as f:
                for ln in lines:
                    f.write(ln + '\n')
        except Exception as e:
            return jsonify({'success': False, 'message': str(e)}), 500
    if not os.path.isfile(path):
        # 仍无文件：返回临时文本
        buf = io.BytesIO(('\n'.join(lines) + ('\n' if lines else '')).encode('utf-8'))
        buf.seek(0)
        return send_file(
            buf,
            as_attachment=True,
            download_name=f'device_console_{_device_console_safe_id(device_id)}.log',
            mimetype='text/plain',
        )
    return send_file(
        path,
        as_attachment=True,
        download_name=f'device_console_{_device_console_safe_id(device_id)}.log',
        mimetype='text/plain',
    )


@app.route('/api/devices/<device_id>/console-logs/subscribe', methods=['POST'])
def api_device_console_subscribe(device_id):
    """通知设备开启 logcat 上报（可选；Demo 注册后默认已开）。"""
    body = request.get_json(silent=True) or {}
    enable = body.get('enable', True)
    ok, msg = send_device_console_control(device_id, enable=bool(enable))
    status = 200 if ok else 400
    return jsonify({'success': ok, 'message': msg, 'deviceId': device_id, 'enable': bool(enable)}), status


@app.route('/api/devices/<device_id>/console-logs/clear', methods=['POST'])
def api_device_console_clear(device_id):
    """清空某设备控制台缓冲与文件。"""
    device_id = (device_id or '').strip()
    with device_console_lock:
        device_console_logs[device_id] = {
            'seq': 0,
            'lines': deque(maxlen=DEVICE_CONSOLE_MAX_LINES),
        }
    path = _device_console_file(device_id)
    try:
        if os.path.isfile(path):
            open(path, 'w', encoding='utf-8').close()
    except Exception as e:
        return jsonify({'success': False, 'message': str(e)}), 500
    return jsonify({'success': True, 'deviceId': device_id})


@app.route('/api/adb-shot/status')
def api_adb_shot_status():
    return jsonify({
        'success': True, 'running': False, 'disabled': True,
        'message': 'ADB 截图已停用，请用 WS 截图',
        'frames': [], 'captures': 0, 'seq': 0, 'has_frame': False,
    })


@app.route('/api/adb-shot/start', methods=['POST'])
def api_adb_shot_start():
    return jsonify({
        'success': False, 'message': 'ADB 截图已停用，请用首页 WS 截图',
        'disabled': True,
    }), 410


@app.route('/api/adb-shot/stop', methods=['POST'])
def api_adb_shot_stop():
    return jsonify({
        'success': False, 'message': 'ADB 截图已停用，请用首页 WS 截图',
        'disabled': True, 'running': False,
    }), 410


@app.route('/api/adb-shot/clear', methods=['POST'])
def api_adb_shot_clear():
    return jsonify({
        'success': False, 'message': 'ADB 截图已停用，请用首页 WS 截图',
        'disabled': True, 'running': False,
    }), 410


@app.route('/api/adb-shot/toggle', methods=['POST'])
def api_adb_shot_toggle():
    return jsonify({
        'success': False, 'message': 'ADB 截图已停用，请用首页 WS 截图',
        'disabled': True, 'running': False,
    }), 410


@app.route('/api/remote-adb/status')
def api_remote_adb_status():
    """USB bridge listen status + visible ADB devices."""
    return jsonify(_remote_adb_status_payload())


@app.route('/api/remote-adb/devices')
def api_remote_adb_devices():
    """Normalized ADB device list for the remote-ADB page."""
    try:
        devices = get_adb_visible_devices()
    except Exception as e:
        logger.warning('remote-adb devices failed: %s', e)
        return jsonify({'success': False, 'message': str(e), 'devices': []}), 500
    rows = []
    for d in devices or []:
        rows.append({
            'serial': d.get('device_id') or d.get('serial') or '',
            'device_name': d.get('device_name') or '',
            'model': d.get('model') or '',
            'manufacturer': d.get('manufacturer') or '',
            'market_name': d.get('market_name') or '',
            'kind': d.get('kind') or '',
            'kind_label': d.get('kind_label') or '',
            'channel': d.get('channel') or '',
            'channel_label': d.get('channel_label') or '',
            'adb_host': d.get('adb_host') or '',
            'adb_port': d.get('adb_port') or 0,
        })
    return jsonify({
        'success': True,
        'bridge_port': _remote_adb_bridge_port(),
        'devices': rows,
    })


@app.route('/api/remote-adb/probe', methods=['POST'])
def api_remote_adb_probe():
    """Smoke-test ADB to a serial via getprop ro.product.model."""
    data = request.get_json(silent=True) or {}
    serial = (data.get('serial') or data.get('device_id') or '').strip()
    if not serial:
        return jsonify({'success': False, 'message': '请提供 serial'}), 400
    adb_bin = _resolve_adb_bin()
    if not adb_bin:
        return jsonify({'success': False, 'message': '未找到 adb'}), 500
    device = _remote_adb_find_device(serial)
    host = None
    port = None
    if device:
        host = device.get('adb_host') or None
        port = device.get('adb_port') or None
        if port:
            try:
                port = int(port)
            except (TypeError, ValueError):
                port = None
        if not host and device.get('kind') == 'adb_usb_bridge':
            host, port = '127.0.0.1', _remote_adb_bridge_port()
    model = _adb_shell_getprop(
        adb_bin, serial, 'ro.product.model', host=host, port=port, timeout=8
    )
    android = _adb_shell_getprop(
        adb_bin, serial, 'ro.build.version.release', host=host, port=port, timeout=8
    )
    ok = bool(model or android)
    return jsonify({
        'success': ok,
        'serial': serial,
        'model': model,
        'android_version': android,
        'channel': (device or {}).get('channel') or ('local' if not host else f'{host}:{port}'),
        'message': 'ADB 连通' if ok else '探测失败（无 getprop 输出）',
    })


@app.route('/api/ws-shot/status')
def api_ws_shot_status():
    with _WS_SHOT['lock']:
        st = _ws_shot_status_unlocked()
    return jsonify({'success': True, **st})


@app.route('/api/ws-shot/clear', methods=['POST'])
def api_ws_shot_clear():
    ok, msg, st = ws_shot_clear_history()
    return jsonify({'success': ok, 'message': msg, **st})


@app.route('/api/ws-shot/toggle', methods=['POST'])
def api_ws_shot_toggle():
    body = request.get_json(silent=True) or {}
    device_id = (body.get('device_id') or body.get('deviceId') or '').strip()
    try:
        max_width = int(body.get('max_width') or 720)
    except (TypeError, ValueError):
        max_width = 720
    try:
        jpeg_quality = int(body.get('jpeg_quality') or 70)
    except (TypeError, ValueError):
        jpeg_quality = 70
    with _WS_SHOT['lock']:
        running = bool(_WS_SHOT['running'])
    if running:
        ok, msg, st = ws_shot_stop()
    else:
        ok, msg, st = ws_shot_start(
            device_id=device_id,
            interval_sec=3.0,
            max_width=max_width,
            jpeg_quality=jpeg_quality,
            clear_history=True,
        )
    return jsonify({'success': ok, 'message': msg, **st}), (200 if ok else 400)


@app.route('/api/ws-shot/latest.jpg')
@app.route('/api/ws-shot/latest.png')
def api_ws_shot_latest():
    with _WS_SHOT['lock']:
        data = _WS_SHOT.get('jpeg')
        seq = int(_WS_SHOT.get('seq') or 0)
        frames = list(_WS_SHOT.get('frames') or [])
        if not data and frames:
            path = frames[-1].get('path')
            mime = frames[-1].get('mime') or 'image/jpeg'
        else:
            path = None
            mime = 'image/jpeg'
    if data:
        resp = Response(data, mimetype=mime)
        resp.headers['Cache-Control'] = 'no-store, no-cache, must-revalidate, max-age=0'
        resp.headers['X-Ws-Shot-Seq'] = str(seq)
        return resp
    if path and os.path.isfile(path):
        return send_file(path, mimetype=mime, conditional=False)
    return jsonify({'success': False, 'message': '尚无截图帧'}), 404


@app.route('/api/ws-shot/frame/<int:seq>.jpg')
@app.route('/api/ws-shot/frame/<int:seq>.png')
def api_ws_shot_frame(seq: int):
    with _WS_SHOT['lock']:
        frames = list(_WS_SHOT.get('frames') or [])
    for f in frames:
        if int(f.get('seq') or 0) == int(seq):
            path = f.get('path')
            if path and os.path.isfile(path):
                return send_file(path, mimetype=f.get('mime') or 'image/jpeg', conditional=False)
            break
    return jsonify({'success': False, 'message': f'frame {seq} not found'}), 404


@app.route('/api/device/screenshot', methods=['POST'])
def upload_device_screenshot():
    """设备通过 HTTP 上传截图（result_final 等），避免 WebSocket 队列拥塞导致丢失。"""
    task_id = (request.form.get('taskId') or '').strip()
    # 优先 testId（拍摄用例），再 caseId；防止队列延迟时 caseId 已切到下一用例
    case_id = (request.form.get('testId') or request.form.get('caseId') or '').strip()
    device_id = (request.form.get('deviceId') or '').strip()
    upload_file = request.files.get('file')

    if not task_id or not case_id:
        fallback_task, fallback_case = get_device_current_task(device_id)
        if not task_id:
            task_id = fallback_task or ''
        if not case_id:
            case_id = fallback_case or ''

    if not upload_file or not upload_file.filename:
        return jsonify({'success': False, 'message': '缺少截图文件'}), 400
    if not task_id or not case_id:
        return jsonify({
            'success': False,
            'message': f'缺少 taskId/caseId: taskId={task_id}, caseId={case_id}'
        }), 400

    filename = os.path.basename(upload_file.filename)
    if not _is_gui_poll_filename(filename):
        logger.info(
            f"跳过非 gui_poll 截图(HTTP): task={task_id} case={case_id} file={filename}"
        )
        return jsonify({'success': True, 'skipped': True, 'filename': filename})

    if should_reject_task_upload(task_id):
        logger.info(
            f"忽略已停止任务的截图上传(HTTP): task={task_id} case={case_id} "
            f"file={filename}"
        )
        return jsonify({'success': False, 'message': '任务已停止，忽略截图'}), 409
    try:
        result_path = os.path.join(app.config['TESTRESULTS_FOLDER'], task_id, case_id)
        os.makedirs(result_path, exist_ok=True)
        filepath = os.path.join(result_path, filename)
        upload_file.save(filepath)
        if device_id:
            with devices_lock:
                dev = devices.get(device_id)
                if dev is not None:
                    dev['last_seen'] = time.time()
        logger.info(f"Screenshot saved (HTTP): {filepath}")
        if _is_result_final_filename(filename):
            logger.info(f"result_final 已保存: {filepath}")
        return jsonify({'success': True, 'path': filepath, 'filename': filename})
    except Exception as e:
        logger.error(f"HTTP 截图保存失败: {e}")
        return jsonify({'success': False, 'message': str(e)}), 500


@app.route('/api/device/heartbeat', methods=['POST'])
def device_http_heartbeat():
    """HTTP 心跳：WS 在跨 App GUI 期间被系统卡住时，仍可刷新 last_seen。"""
    data = request.get_json(silent=True) or {}
    if not data and request.form:
        data = request.form.to_dict()
    device_id = (data.get('deviceId') or '').strip()
    if not device_id:
        return jsonify({'success': False, 'message': '缺少 deviceId'}), 400
    with devices_lock:
        dev = devices.get(device_id)
        if dev is None:
            return jsonify({'success': False, 'message': 'device not registered'}), 404
        dev['last_seen'] = time.time()
    return jsonify({'success': True, 'deviceId': device_id})


@app.route('/api/device/case_reply', methods=['POST'])
def upload_device_case_reply():
    """HTTP 上报模型回复，不依赖 WebSocket（截图 HTTP 通、WS 断时仍能落盘）。"""
    data = request.get_json(silent=True)
    if not data:
        data = request.form.to_dict() if request.form else {}
    ok, msg = ingest_case_reply(data)
    if ok:
        return jsonify({'success': True, 'message': msg})
    status = 409 if msg == 'task stopped' else 400
    return jsonify({'success': False, 'message': msg}), status


@app.route('/api/device/round_start', methods=['POST'])
def upload_device_round_start():
    """HTTP 上报轮次开始（GUI_TASK 到达即建截图归轮时间窗）。"""
    data = request.get_json(silent=True)
    if not data:
        data = request.form.to_dict() if request.form else {}
    ok, msg = ingest_round_start(data)
    if ok:
        return jsonify({'success': True, 'message': msg})
    return jsonify({'success': False, 'message': msg}), 400


@app.route('/api/device/gui_step_latency', methods=['POST'])
def upload_device_gui_step_latency():
    """HTTP 上报 GUI 单步时延（端侧/云侧）。"""
    data = request.get_json(silent=True)
    if not data:
        data = request.form.to_dict() if request.form else {}
    ok, msg = ingest_gui_step_latency(data)
    if ok:
        return jsonify({'success': True, 'message': msg})
    return jsonify({'success': False, 'message': msg}), 400


@app.route('/api/device/log_chunk', methods=['POST'])
def upload_device_log_chunk():
    """HTTP 追加 device_live.log，WS 断开时仍可观测 GUI 接管日志。"""
    data = request.get_json(silent=True) or {}
    task_id = (data.get('taskId') or '').strip()
    case_id = (data.get('caseId') or data.get('testId') or '').strip()
    device_id = (data.get('deviceId') or '').strip()
    text = data.get('text') or ''
    reset = bool(data.get('reset'))

    if not task_id or not case_id:
        fallback_task, fallback_case = get_device_current_task(device_id)
        if not task_id:
            task_id = fallback_task or ''
        if not case_id:
            case_id = fallback_case or ''

    if not task_id or not case_id:
        return jsonify({
            'success': False,
            'message': f'缺少 taskId/caseId: taskId={task_id}, caseId={case_id}'
        }), 400

    if device_id:
        with devices_lock:
            dev = devices.get(device_id)
            if dev is not None:
                dev['last_seen'] = time.time()

    append_device_live_log(task_id, case_id, text, reset=reset)
    return jsonify({'success': True})


@app.route('/api/device/log', methods=['POST'])
def upload_device_log():
    """设备通过 HTTP 上传测试日志，保存到用例结果目录供页面下载。"""
    task_id = (request.form.get('taskId') or '').strip()
    case_id = (request.form.get('testId') or request.form.get('caseId') or '').strip()
    device_id = (request.form.get('deviceId') or '').strip()
    upload_file = request.files.get('file')

    if not task_id or not case_id:
        fallback_task, fallback_case = get_device_current_task(device_id)
        if not task_id:
            task_id = fallback_task or ''
        if not case_id:
            case_id = fallback_case or ''

    if not upload_file or not upload_file.filename:
        return jsonify({'success': False, 'message': '缺少日志文件'}), 400
    if not task_id or not case_id:
        return jsonify({
            'success': False,
            'message': f'缺少 taskId/caseId: taskId={task_id}, caseId={case_id}'
        }), 400

    filename = os.path.basename(upload_file.filename)
    if not filename.lower().endswith('.log'):
        return jsonify({'success': False, 'message': '仅支持 .log 文件'}), 400
    if not filename.startswith('device_'):
        filename = 'device_' + filename

    try:
        result_path = os.path.join(app.config['TESTRESULTS_FOLDER'], task_id, case_id)
        os.makedirs(result_path, exist_ok=True)
        filepath = os.path.join(result_path, filename)
        upload_file.save(filepath)
        logger.info(f"Device log saved (HTTP): {filepath}")
        print_device_diagnostic_log(filepath)
        return jsonify({'success': True, 'path': filepath, 'filename': filename})
    except Exception as e:
        logger.error(f"HTTP 日志保存失败: {e}")
        return jsonify({'success': False, 'message': str(e)}), 500

# ========== API: Testsets ==========

@app.route('/api/testsets')
def get_testsets():
    """Get all test sets."""
    scan_testfiles()
    with testsets_lock:
        return jsonify({'success': True, 'testsets': list(testsets.values())})

@app.route('/api/testsets/<testset_name>/files')
def get_testset_files(testset_name):
    """Get files in a testset with pagination."""
    scan_testfiles()
    page = int(request.args.get('page', 1))
    per_page = int(request.args.get('per_page', 20))

    with testsets_lock:
        if testset_name not in testsets:
            return jsonify({'success': False, 'message': 'Testset not found'}), 404

        all_files = testsets[testset_name].get('files', [])
        total = len(all_files)
        start = (page - 1) * per_page
        end = start + per_page
        files = all_files[start:end]

        return jsonify({
            'success': True,
            'files': files,
            'total': total,
            'page': page,
            'per_page': per_page,
            'total_pages': (total + per_page - 1) // per_page
        })

@app.route('/api/testsets', methods=['POST'])
def create_testset():
    """Create a new test set (folder)."""
    data = request.get_json()
    testset_name = data.get('name', '').strip()

    if not testset_name:
        return jsonify({'success': False, 'message': 'Name is required'})

    # Sanitize name
    testset_name = secure_filename(testset_name)
    if not testset_name:
        return jsonify({'success': False, 'message': 'Invalid name'})

    testset_path = os.path.join(app.config['UPLOAD_FOLDER'], testset_name)
    if os.path.exists(testset_path):
        return jsonify({'success': False, 'message': 'Testset already exists'})

    os.makedirs(testset_path, exist_ok=True)
    scan_testfiles()

    return jsonify({'success': True, 'message': f'Testset {testset_name} created'})

    return jsonify({'success': True, 'message': f'Testset {testset_name} deleted'})


def _excel_case_level_to_display(level):
    """Export WebTest case level for Excel (L1 -> Level 1)."""
    text = str(level or '').strip().upper()
    if re.fullmatch(r'L\d+', text):
        return f'Level {text[1:]}'
    return str(level or '').strip()


def _excel_row_to_webtest_meta(row_data):
    """Build webtest metadata block from parsed Excel row."""
    mapping = {
        's1': 's1',
        's2': 's2',
        's3': 's3',
        's4': 's4',
        'intelligenceLevel': 'intelligence_level',
        'caseLevel': 'case_level',
        'factorCombo': 'factor_combo',
        'preCondition': 'pre_condition',
    }
    meta = {}
    for dst, src in mapping.items():
        val = str(row_data.get(src) or '').strip()
        if val:
            meta[dst] = val
    return meta or None


def _excel_infer_case_type_from_webtest_row(row_data):
    s2 = str(row_data.get('s2') or '').upper()
    test_id = str(row_data.get('test_id') or '').upper()
    if 'GUI' in s2 or test_id.startswith('CK_GUI'):
        return 'GUI'
    if test_id.startswith('DH_') or 'QA' in s2:
        return 'QA'
    return 'GUI'


def _json_case_to_excel_row(testcase, testset_name):
    """把单个用例 JSON 展开为与 Excel 模板一致的一行。"""
    steps = testcase.get('steps') or []
    inputs = []
    waits_sec = []
    clarifies = []
    clarify_map = testcase.get('clarifyMessages') or {}
    if not isinstance(clarify_map, dict):
        clarify_map = {}

    for step in steps:
        if not isinstance(step, dict):
            continue
        action = step.get('action')
        step_id = str(step.get('stepId') or '')
        params = step.get('params') or {}
        if action == 'input':
            inputs.append(str(params.get('text') or '').strip())
        if action == 'wait' and step_id.startswith('wait-response'):
            try:
                dur_ms = int(params.get('duration') or 0)
            except (TypeError, ValueError):
                dur_ms = 0
            waits_sec.append(str(max(dur_ms // 1000, 0)) if dur_ms else '')
            clarifies.append(str(params.get('clarifyMessage') or '').strip())

    for idx in range(len(inputs)):
        round_no = idx + 1
        mapped = str(clarify_map.get(str(round_no)) or clarify_map.get(round_no) or '').strip()
        if mapped:
            if idx < len(clarifies):
                if not clarifies[idx]:
                    clarifies[idx] = mapped
            else:
                clarifies.append(mapped)

    exceptions = testcase.get('exceptions') or []
    if isinstance(exceptions, str):
        exc_items = [exceptions.strip()] if exceptions.strip() else []
    elif isinstance(exceptions, list):
        exc_items = [str(item).strip() for item in exceptions if str(item).strip()]
    else:
        exc_items = []

    steps_text = inputs[0] if len(inputs) == 1 else '\n'.join(
        f'{i + 1}. {text}' for i, text in enumerate(inputs) if text
    )
    exc_text = exc_items[0] if len(exc_items) == 1 else '\n'.join(
        f'{i + 1}. {item}' for i, item in enumerate(exc_items)
    )

    webtest = testcase.get('webtest') if isinstance(testcase.get('webtest'), dict) else {}

    def webtest_field(*keys):
        for key in keys:
            val = webtest.get(key) if isinstance(webtest, dict) else None
            if val is not None and str(val).strip():
                return str(val).strip()
        return ''

    pre_condition = webtest_field('preCondition', 'pre_condition')
    if not pre_condition:
        pre_condition = str(testcase.get('description') or '').strip()

    return [
        webtest_field('s1', 's1Scenario'),
        webtest_field('s2', 's2Scenario'),
        webtest_field('s3', 's3Scenario'),
        webtest_field('intelligenceLevel', 'intelligence_level'),
        webtest_field('s4', 's4Scenario'),
        str(testcase.get('testId') or '').strip(),
        str(testcase.get('testName') or '').strip(),
        webtest_field('factorCombo', 'factor_combo'),
        pre_condition,
        steps_text,
        exc_text,
        _excel_case_level_to_display(webtest_field('caseLevel', 'case_level')),
    ]


def _json_sheet_headers():
    return [
        'S1场景', 'S2场景', 'S3场景', '智能等级', 'S4场景（可选）', '用例编号', '用例标题',
        '因子组合', '预置条件（可选）', '测试步骤（语料）', '预期结果', '用例等级',
    ]


def _excel_dataset_headers():
    """Excel 数据集默认表头（对齐 GUI_danger0903）。"""
    return [
        '用例编号',
        '用例标题',
        '预置条件（可选）',
        'S1场景',
        'S2场景',
        'S3场景',
        'S4场景（可选）',
        '分类标识',
        '规划路标',
        '智能等级',
        '用例等级（level 0重统计）',
        '用例责任人',
        '测试步骤（语料）',
        'APP',
        '要求是否登录',
        '预期结果',
        '预期意图',
        '预期skill',
        '声纹ID',
        '预期操作类型',
        '设备类型',
        '用户',
    ]


def read_json_testset_sheet(testset_name):
    """读取 JSON 用例集为可编辑表格（表头 + 行）。"""
    testset_path = resolve_testset_path(testset_name)
    if not os.path.isdir(testset_path):
        raise FileNotFoundError(testset_name)

    headers = _json_sheet_headers()
    rows = []
    package_hint = ''
    for filename in sorted(list_testset_json_files(testset_name)):
        filepath = os.path.join(testset_path, filename)
        try:
            with open(filepath, 'r', encoding='utf-8') as f:
                testcase = json.load(f)
        except Exception as e:
            logger.warning(f'Read json case failed {filepath}: {e}')
            continue
        if not isinstance(testcase, dict):
            continue
        row = _json_case_to_excel_row(testcase, testset_name)
        while len(row) < len(headers):
            row.append('')
        rows.append(row[:len(headers)])
        if not package_hint:
            package_hint = row[-1] if row else ''
    return headers, rows, package_hint


def write_excel_dataset_grid_to_bytes(headers, rows):
    """把表头 + 行写到内存 xlsx，供 JSON 用例集回写复用。"""
    import openpyxl
    from openpyxl.styles import Alignment

    headers = headers or _json_sheet_headers()
    rows = rows or []
    wb = openpyxl.Workbook()
    ws = wb.active
    ws.title = '用例'

    wrap_col_indexes = set()
    for idx, header in enumerate(headers):
        if any(key in str(header) for key in ('步骤', '等待', '预期', '描述', '澄清', '语料', '预置', '因子')):
            wrap_col_indexes.add(idx)

    for col_idx, header in enumerate(headers, 1):
        ws.cell(row=1, column=col_idx, value=header)

    for row_idx, row in enumerate(rows, 2):
        for col_idx in range(len(headers)):
            value = row[col_idx] if col_idx < len(row) else ''
            cell = ws.cell(row=row_idx, column=col_idx + 1, value=value)
            if col_idx in wrap_col_indexes:
                cell.alignment = Alignment(wrap_text=True, vertical='top')

    buf = io.BytesIO()
    wb.save(buf)
    buf.seek(0)
    return buf


def write_json_testset_sheet(testset_name, headers, rows, package_name=None):
    """把表格写回 JSON 用例集（按 Excel 导入规则重生 JSON）。"""
    testset_path = resolve_testset_path(testset_name)
    if not os.path.isdir(testset_path):
        raise FileNotFoundError(testset_name)

    headers = headers or _json_sheet_headers()
    rows = rows or []

    pkg = (package_name or '').strip() or EXCEL_DEFAULT_PACKAGE
    pkg_col = None
    for i, h in enumerate(headers):
        hs = str(h or '')
        if '包名' in hs or 'package' in hs.lower():
            pkg_col = i
            break
    if pkg_col is not None:
        for row in rows:
            if pkg_col < len(row) and str(row[pkg_col] or '').strip():
                pkg = str(row[pkg_col]).strip()
                break

    # import_excel_bytes_to_testset / EXCEL_* 定义在本文件后部，调用时已就绪
    buf = write_excel_dataset_grid_to_bytes(headers, rows)
    result = import_excel_bytes_to_testset(
        testset_path,
        buf.getvalue(),
        config={
            'package_name': pkg if pkg in EXCEL_PACKAGE_PRESETS else EXCEL_DEFAULT_PACKAGE,
            'wait_ms': EXCEL_DEFAULT_CHAT_WAIT_MS,
            'timeout_ms': EXCEL_DEFAULT_TIMEOUT_MS,
        },
    )
    if not result.get('success'):
        return result

    created = set(result.get('created') or [])
    for filename in list_testset_json_files(testset_name):
        if filename not in created:
            try:
                os.remove(os.path.join(testset_path, filename))
            except OSError as e:
                logger.warning(f'Remove obsolete case failed {filename}: {e}')

    scan_testfiles()
    return result


@app.route('/api/testsets/<testset_name>/sheet', methods=['GET'])
def get_testset_sheet_api(testset_name):
    """JSON 用例集 → Excel 风格表格数据。"""
    safe_name = (testset_name or '').strip()
    if not safe_name or '/' in safe_name or '\\' in safe_name or safe_name.startswith('.'):
        return jsonify({'success': False, 'message': '无效的用例集名称'}), 400
    try:
        headers, rows, package_hint = read_json_testset_sheet(safe_name)
    except FileNotFoundError:
        return jsonify({'success': False, 'message': '用例集不存在'}), 404
    except Exception as e:
        logger.error(f'Read testset sheet failed: {e}')
        return jsonify({'success': False, 'message': f'读取失败: {e}'}), 500
    return jsonify({
        'success': True,
        'testset': safe_name,
        'headers': headers,
        'rows': rows,
        'package_name': package_hint,
        'case_count': len(rows),
    })


@app.route('/api/testsets/<testset_name>/sheet', methods=['PUT'])
def put_testset_sheet_api(testset_name):
    """Excel 风格表格 → 写回 JSON 用例集。"""
    safe_name = (testset_name or '').strip()
    if not safe_name or '/' in safe_name or '\\' in safe_name or safe_name.startswith('.'):
        return jsonify({'success': False, 'message': '无效的用例集名称'}), 400
    data = request.get_json(silent=True) or {}
    headers = data.get('headers') or []
    rows = data.get('rows') or []
    package_name = data.get('package_name')
    try:
        result = write_json_testset_sheet(safe_name, headers, rows, package_name=package_name)
    except FileNotFoundError:
        return jsonify({'success': False, 'message': '用例集不存在'}), 404
    except Exception as e:
        logger.error(f'Write testset sheet failed: {e}')
        return jsonify({'success': False, 'message': f'保存失败: {e}'}), 500
    if not result.get('success'):
        return jsonify(result), 400
    return jsonify({
        'success': True,
        'message': result.get('message') or '已保存',
        'created': result.get('created') or [],
        'case_count': len(result.get('created') or []),
    })


# ========== Excel 用例导入（用例-格式.xlsx） ==========

EXCEL_DEFAULT_PACKAGE = 'com.example.devicesdkdemo'
EXCEL_DEFAULT_INPUT_ID = 'com.unicom.cloudphone:id/chat_view_et_question'
EXCEL_DEFAULT_SEND_ID = 'com.unicom.cloudphone:id/chat_view_btn_send'
EXCEL_DEFAULT_VOICE_TOGGLE_ID = ''
EXCEL_DEFAULT_CHAT_WAIT_MS = 1500
EXCEL_DEFAULT_TIMEOUT_MS = 90000

EXCEL_PACKAGE_PRESETS = {
    'com.unicom.cloudphone': {
        'label': '云手机',
        'input_id': 'com.unicom.cloudphone:id/chat_view_et_question',
        'send_id': 'com.unicom.cloudphone:id/chat_view_btn_send',
        'voice_toggle_id': '',
    },
    'com.example.devicesdkdemo': {
        'label': 'Device SDK Demo',
        'input_id': 'com.example.devicesdkdemo:id/et_input',
        'send_id': 'com.example.devicesdkdemo:id/btn_send',
        'voice_toggle_id': '',
        'enter_test_id': 'com.example.devicesdkdemo:id/btn_confirm',
        'login_button_id': 'com.example.devicesdkdemo:id/btn_login',
        'qa_button_id': 'com.example.devicesdkdemo:id/btn_qa',
    },
    'com.huawei.aisftvassistant': {
        'label': 'AISF 语音助手',
        'input_id': '',
        'send_id': '',
        'voice_toggle_id': '',
    },
}

# 聊天页已就绪时：点「会话 ▼」→「新增会话」开干净会话（前置准备/案间 return 已保证在 QA）
DEVICESDKDEMO_PRE_STEP_IDS = frozenset({
    'click-session-manage',
    'click-new-session',
    'wait-new-session',
})

DEVICESDKDEMO_WAIT_NEW_SESSION_MS = 2000
DEVICESDKDEMO_PRE_STEP_ORDER = (
    'click-session-manage',
    'click-new-session',
    'wait-new-session',
)

# 旧版「进测试/登录/进 QA」前置，下发时剥掉，避免与新会话前置叠跑
DEVICESDKDEMO_LEGACY_PRE_STEP_IDS = frozenset({
    'dismiss-session-dialog',
    'scroll-to-enter-test',
    'wait-test-home',
    'dismiss-need-login',
    'click-login',
    'wait-login',
    'dismiss-after-login',
    'click-qa',
    'wait-qa-page',
    'scroll-config-down-1',
    'wait-scroll-config-1',
    'scroll-config-down-2',
    'wait-scroll-config-2',
    'scroll-config-down-3',
    'wait-scroll-config-3',
    'scroll-config-down-4',
    'wait-scroll-config-4',
    'scroll-config-down-5',
    'wait-scroll-config-5',
    'scroll-config-down-6',
    'wait-scroll-config-6',
    'click-enter-test',
})

DEVICESDKDEMO_ALL_PRE_STEP_IDS = DEVICESDKDEMO_PRE_STEP_IDS | DEVICESDKDEMO_LEGACY_PRE_STEP_IDS


def _build_devicesdkdemo_pre_steps(package_name):
    """DeviceSDK Demo：在聊天页新增会话。

    若不在聊天页，端侧 launchApp / click-session-manage 会先 Config→登录→QA，再执行本前置。
    """
    session_btn_id = f'{package_name}:id/btn_session_manage'
    new_session_btn_id = f'{package_name}:id/btn_session_new'
    steps = [
        {
            'stepId': 'click-session-manage',
            'description': '聊天页点击「会话 ▼」打开会话管理',
            'action': 'click',
            'target': {'strategy': 'id', 'value': session_btn_id},
        },
        {
            'stepId': 'click-new-session',
            'description': '会话管理弹窗点击「新增会话」',
            'action': 'click',
            'target': {'strategy': 'id', 'value': new_session_btn_id},
        },
        {
            'stepId': 'wait-new-session',
            'description': '等待新会话就绪',
            'action': 'wait',
            'params': {'duration': DEVICESDKDEMO_WAIT_NEW_SESSION_MS},
        },
    ]
    return steps


def _devicesdkdemo_pre_step_extra_timeout_ms():
    """DeviceSDK Demo 前置导航步骤额外需要的 timeout（毫秒）。"""
    pre_steps = _build_devicesdkdemo_pre_steps('com.example.devicesdkdemo')
    wait_ms = sum(
        step.get('params', {}).get('duration', 0)
        for step in pre_steps
        if step.get('action') == 'wait'
    )
    action_overhead_ms = 0
    for step in pre_steps:
        action = step.get('action')
        if action == 'scrollUntilClick':
            params = step.get('params') or {}
            max_scrolls = int(params.get('maxScrolls') or 6)
            scroll_wait_ms = int(params.get('duration') or 600)
            action_overhead_ms += max_scrolls * (scroll_wait_ms + 700) + 2000
        elif action in ('scroll', 'click'):
            action_overhead_ms += 2000
    return wait_ms + action_overhead_ms + 5000


def _bump_timeout_for_devicesdkdemo_pre_steps(testcase):
    """为含 DeviceSDK 前置步骤的用例增加 timeout。"""
    base_timeout = int(testcase.get('timeout') or EXCEL_DEFAULT_TIMEOUT_MS)
    testcase['timeout'] = base_timeout + _devicesdkdemo_pre_step_extra_timeout_ms()
    return testcase


def _sum_wait_steps_ms(testcase):
    """合计用例中所有 wait 步 duration（毫秒）。"""
    total = 0
    for step in testcase.get('steps') or []:
        if step.get('action') != 'wait':
            continue
        try:
            total += int((step.get('params') or {}).get('duration') or 0)
        except (TypeError, ValueError):
            continue
    return total


def _ensure_timeout_covers_wait_steps(testcase, headroom_ms=120000):
    """
    联通云智 Excel 会生成大量 wait-screenshot 垫步，时长合计≈Excel timeout；
    启动/点击/截图上传还要额外时间。若 JSON.timeout 不大于「wait 合计」，
    手机跑到后半段会被服务端「用例等待超时」打断（见 55/67 被 stop）。
    """
    if not isinstance(testcase, dict):
        return testcase
    wait_sum = _sum_wait_steps_ms(testcase)
    if wait_sum <= 0:
        return testcase
    step_count = len(testcase.get('steps') or [])
    non_wait_ms = max(int(headroom_ms), 30000 + step_count * 500)
    needed = wait_sum + non_wait_ms
    try:
        old = int(testcase.get('timeout') or EXCEL_DEFAULT_TIMEOUT_MS)
    except (TypeError, ValueError):
        old = EXCEL_DEFAULT_TIMEOUT_MS
    if needed > old:
        testcase['timeout'] = needed
        logger.info(
            f"用例 {testcase.get('testId')} timeout 不足以覆盖 wait 步: "
            f"{old}ms -> {needed}ms "
            f"(wait合计={wait_sum}ms + 非wait开销={non_wait_ms}ms, 步数={step_count})"
        )
    return testcase


def _has_devicesdkdemo_pre_steps(steps):
    return any(step.get('stepId') in DEVICESDKDEMO_ALL_PRE_STEP_IDS for step in steps)


def _has_complete_devicesdkdemo_pre_steps(steps):
    """缺会话前置（或仍是旧登录前置）时需重新注入。"""
    step_ids = {step.get('stepId') for step in (steps or [])}
    return DEVICESDKDEMO_PRE_STEP_IDS.issubset(step_ids)


def _normalize_devicesdkdemo_pre_steps(steps):
    """下发前校正 DeviceSDK 新会话前置顺序与等待时长。"""
    if not steps:
        return steps
    pre_ids = set(DEVICESDKDEMO_PRE_STEP_ORDER)
    pre_by_id = {}
    pre_indices = []
    for i, step in enumerate(steps):
        sid = step.get('stepId')
        if sid not in pre_ids:
            continue
        pre_indices.append(i)
        pre_by_id[sid] = step
        if sid == 'wait-new-session':
            try:
                dur = int((step.get('params') or {}).get('duration') or 0)
            except (TypeError, ValueError):
                dur = 0
            if dur <= 0 or dur > DEVICESDKDEMO_WAIT_NEW_SESSION_MS * 4:
                step.setdefault('params', {})['duration'] = DEVICESDKDEMO_WAIT_NEW_SESSION_MS
    if not pre_indices:
        return steps
    ordered_pre = [pre_by_id[sid] for sid in DEVICESDKDEMO_PRE_STEP_ORDER if sid in pre_by_id]
    insert_at = min(pre_indices)
    new_steps = [s for s in steps if s.get('stepId') not in pre_ids]
    new_steps[insert_at:insert_at] = ordered_pre
    return new_steps


def _find_post_launch_insert_index(steps):
    for i, step in enumerate(steps):
        if step.get('action') != 'launchApp':
            continue
        insert_at = i + 1
        while insert_at < len(steps) and steps[insert_at].get('action') == 'wait':
            insert_at += 1
        return insert_at
    return min(3, len(steps))


def _apply_package_pre_steps(steps, target_package):
    """按目标应用包插入或移除进入聊天页的前置步骤。"""
    steps = [step for step in steps if step.get('stepId') not in DEVICESDKDEMO_ALL_PRE_STEP_IDS]
    if target_package == 'com.example.devicesdkdemo':
        insert_at = _find_post_launch_insert_index(steps)
        pre_steps = _build_devicesdkdemo_pre_steps(target_package)
        steps[insert_at:insert_at] = pre_steps
    return steps


def _excel_get_package_ui_config(package_name):
    """根据目标应用包名返回 launch 与聊天控件 resource-id 配置。"""
    preset = EXCEL_PACKAGE_PRESETS.get(package_name)
    if preset:
        return {
            'package_name': package_name,
            'input_id': preset['input_id'],
            'send_id': preset['send_id'],
            'voice_toggle_id': preset.get('voice_toggle_id', ''),
        }
    return {
        'package_name': package_name,
        'input_id': f'{package_name}:id/et_input',
        'send_id': f'{package_name}:id/btn_send',
        'voice_toggle_id': '',
    }


def get_target_app_label(package_name):
    preset = EXCEL_PACKAGE_PRESETS.get(package_name, {})
    return preset.get('label', package_name or EXCEL_DEFAULT_PACKAGE)


def _build_package_control_id_map(target_package):
    """将各预设应用的输入/发送控件 id 映射到目标应用对应控件。"""
    target_ui = _excel_get_package_ui_config(target_package)
    id_map = {}
    for key in ('input_id', 'send_id', 'voice_toggle_id'):
        target_id = target_ui.get(key)
        if not target_id:
            continue
        for preset_pkg in EXCEL_PACKAGE_PRESETS:
            src_id = _excel_get_package_ui_config(preset_pkg).get(key)
            if src_id:
                id_map[src_id] = target_id
    return id_map


def adapt_testcase_for_package(testcase, target_package):
    """按目标应用包名适配用例 JSON（不修改磁盘原文件）。"""
    if target_package not in EXCEL_PACKAGE_PRESETS:
        raise ValueError(f'不支持的目标应用包: {target_package}')

    source_package = (testcase.get('targetApp') or {}).get('packageName')
    needs_id_remap = source_package != target_package
    needs_pre_steps = (
        target_package == 'com.example.devicesdkdemo'
        and not _has_complete_devicesdkdemo_pre_steps(testcase.get('steps', []))
    )
    if not needs_id_remap and not needs_pre_steps:
        return testcase

    adapted = json.loads(json.dumps(testcase, ensure_ascii=False))
    if needs_id_remap:
        id_map = _build_package_control_id_map(target_package)
        adapted.setdefault('targetApp', {})['packageName'] = target_package
        for step in adapted.get('steps', []):
            target = step.get('target')
            if not isinstance(target, dict) or target.get('strategy') != 'id':
                continue
            value = target.get('value', '')
            if value in id_map:
                target['value'] = id_map[value]
            elif source_package and value.startswith(f'{source_package}:id/'):
                suffix = value.split(':id/', 1)[1]
                target['value'] = f'{target_package}:id/{suffix}'

    old_step_count = len(adapted.get('steps', []))
    old_step_ids = {s.get('stepId') for s in adapted.get('steps', [])}
    adapted['steps'] = _apply_package_pre_steps(adapted.get('steps', []), target_package)
    if target_package == 'com.example.devicesdkdemo' and (
        len(adapted.get('steps', [])) != old_step_count
        or not DEVICESDKDEMO_PRE_STEP_IDS.issubset(old_step_ids)
    ):
        old_timeout = int(adapted.get('timeout') or EXCEL_DEFAULT_TIMEOUT_MS)
        _bump_timeout_for_devicesdkdemo_pre_steps(adapted)
        logger.info(
            f'DeviceSDK 前置步骤已注入/刷新: {old_step_count} -> {len(adapted["steps"])} 步, '
            f'timeout {old_timeout}ms -> {adapted["timeout"]}ms'
        )
    return adapted


def ensure_devicesdkdemo_login_steps(testcase, target_package):
    """下发前兜底：缺新会话前置则注入；已有则校正顺序与等待。"""
    if target_package != 'com.example.devicesdkdemo':
        return testcase
    steps = testcase.get('steps') or []
    if _has_complete_devicesdkdemo_pre_steps(steps):
        adapted = json.loads(json.dumps(testcase, ensure_ascii=False))
        adapted['steps'] = _normalize_devicesdkdemo_pre_steps(adapted.get('steps', []))
        return adapted
    preview = [s.get('stepId') for s in steps[:14]]
    logger.warning(
        f'DeviceSDK 用例缺少新会话前置步骤，强制刷新: steps={len(steps)} preview={preview}'
    )
    adapted = json.loads(json.dumps(testcase, ensure_ascii=False))
    old_count = len(adapted.get('steps', []))
    adapted['steps'] = _apply_package_pre_steps(adapted.get('steps', []), target_package)
    adapted['steps'] = _normalize_devicesdkdemo_pre_steps(adapted.get('steps', []))
    _bump_timeout_for_devicesdkdemo_pre_steps(adapted)
    logger.info(
        f'DeviceSDK 新会话前置步骤强制刷新: {old_count} -> {len(adapted["steps"])} 步'
    )
    return adapted


def load_adapted_testcase(testset, filename, target_package=None):
    """读取并按目标应用包适配用例 dict。"""
    filepath = resolve_testset_path(testset, filename)
    if not os.path.exists(filepath):
        raise FileNotFoundError(filepath)
    with open(filepath, 'r', encoding='utf-8') as f:
        testcase = json.load(f)
    pkg = target_package or EXCEL_DEFAULT_PACKAGE
    if pkg not in EXCEL_PACKAGE_PRESETS:
        pkg = EXCEL_DEFAULT_PACKAGE
    adapted = adapt_testcase_for_package(testcase, pkg)
    adapted = ensure_devicesdkdemo_login_steps(adapted, pkg)
    # 即使未做包名适配，也要校正联通垫步导致的 timeout 过短
    if adapted is testcase:
        adapted = json.loads(json.dumps(testcase, ensure_ascii=False))
    normalize_case_exceptions_fields(adapted)
    adapted = _strip_qa_screenshot_steps(adapted)
    return _ensure_timeout_covers_wait_steps(adapted)


def load_testcase_json_for_execution(testset, filename, target_package=None):
    """读取用例 JSON，并按任务所选目标应用做运行时适配。"""
    adapted = load_adapted_testcase(testset, filename, target_package)
    return json.dumps(adapted, ensure_ascii=False, indent=2)


def _excel_sanitize_filename(name):
    invalid_chars = '<>:"/\\|?*'
    for char in invalid_chars:
        name = name.replace(char, '_')
    return name.strip()


def _excel_parse_numbered_lines(cell_value):
    if cell_value is None:
        return []
    text = str(cell_value).strip()
    if not text or text == 'None':
        return []

    items = []
    for raw_line in re.split(r'[\r\n]+', text):
        line = raw_line.strip()
        if not line:
            continue
        match = re.match(r'^\d+[.、):：\s]+(.+)$', line)
        items.append(match.group(1).strip() if match else line)
    return items


def _excel_parse_numbered_map(cell_value):
    """解析编号行到 {序号: 正文}；仅有编号无正文时值为空串（用于澄清消息列对齐）。"""
    result = {}
    if cell_value is None:
        return result
    text = str(cell_value).strip()
    if not text or text == 'None':
        return result
    for raw_line in re.split(r'[\r\n]+', text):
        line = raw_line.strip()
        if not line:
            continue
        match = re.match(r'^(\d+)\s*[.、):：]\s*(.*)$', line)
        if match:
            result[int(match.group(1))] = (match.group(2) or '').strip()
        else:
            next_idx = (max(result.keys()) + 1) if result else 1
            result[next_idx] = line
    return result


def _excel_normalize_case_type(value, package_name=None):
    """用例类型：GUI / QA。空值时按包名推断。"""
    text = (value or '').strip().upper()
    if text in ('GUI', 'QA'):
        return text
    if package_name == 'com.example.devicesdkdemo':
        return 'QA'
    return 'GUI'


def _excel_parse_optional_ms(cell_value, default_ms):
    """解析 Excel 单元格中的毫秒数，空值则返回 default_ms。"""
    if cell_value is None:
        return default_ms
    text = str(cell_value).strip()
    if not text or text.lower() == 'none':
        return default_ms
    try:
        value = int(float(text))
        return value if value > 0 else default_ms
    except ValueError:
        return default_ms


def _excel_parse_wait_seconds_lines(cell_value, default_wait_ms):
    default_sec = default_wait_ms / 1000.0
    waits_ms = []
    for line in _excel_parse_numbered_lines(cell_value):
        try:
            waits_ms.append(int(float(line) * 1000))
        except ValueError:
            waits_ms.append(int(default_sec * 1000))
    return waits_ms



VOICE_ASSISTANT_PKG = 'com.huawei.aisftvassistant'
VOICE_INJECT_ACTION = 'com.huawei.aisftvassistant.action.INJECT_UTTERANCE'
VOICE_INJECT_COMPONENT = 'com.huawei.aisftvassistant/.inject.TestUtteranceReceiver'


def _is_voice_assistant_package(package_name):
    return (package_name or '') == VOICE_ASSISTANT_PKG


def _excel_create_voice_utterance_steps(step_index, text, wait_ms, clarify_message=None):
    """文字语料改成导出广播，不再点 Demo 输入框/发送。等待用超时兜底。"""

    def _inject(step_id, description, utterance):
        return {
            'stepId': step_id,
            'description': description,
            'action': 'broadcast',
            'params': {
                'action': VOICE_INJECT_ACTION,
                'component': VOICE_INJECT_COMPONENT,
                'text': utterance,
            },
        }

    preview = f'{text[:30]}...' if len(text) > 30 else text
    steps = [
        _inject(f'inject-utterance-{step_index}', f'注入语料 {step_index}: {preview}', text),
        {
            'stepId': f'wait-utterance-{step_index}',
            'description': f'等待语音助手执行 {step_index}',
            'action': 'wait',
            'params': {'duration': int(wait_ms)},
        },
    ]
    clarify = (clarify_message or '').strip()
    if clarify:
        steps.append(_inject(
            f'inject-clarify-{step_index}', f'注入澄清 {step_index}', clarify,
        ))
        steps.append({
            'stepId': f'wait-clarify-{step_index}',
            'description': f'等待澄清执行 {step_index}',
            'action': 'wait',
            'params': {'duration': int(wait_ms)},
        })
    return steps


def _excel_create_chat_step(step_index, text, input_id, send_id, wait_ms,
                            event_driven=False, clarify_message=None):
    # DeviceSDK：wait-response 由 AISF/TestReply 事件结束；duration 仅作无回复时的安全兜底，
    # 不按 Excel「单步操作等待时间」空等固定秒数。
    if event_driven:
        safety_ms = max(int(wait_ms or 0), 5 * 60 * 1000)
        wait_desc = f'等待AISF回复(事件驱动) {step_index}'
        wait_duration = safety_ms
    else:
        wait_desc = f'等待AI回复 {step_index}'
        wait_duration = wait_ms
    wait_params = {'duration': wait_duration}
    clarify = (clarify_message or '').strip()
    if clarify:
        # 仅在本轮收到澄清(DIALOG)报文时由 Device/TestApp 发送，不预先插入发送步骤
        wait_params['clarifyMessage'] = clarify
    return [
        {
            'stepId': f'click-input-{step_index}',
            'description': f'点击输入框 {step_index}',
            'action': 'click',
            'target': {'strategy': 'id', 'value': input_id},
        },
        {
            'stepId': f'input-message-{step_index}',
            'description': (
                f'输入消息: {text[:30]}...' if len(text) > 30 else f'输入消息: {text}'
            ),
            'action': 'input',
            'target': {'strategy': 'id', 'value': input_id},
            'params': {'text': text, 'clearFirst': True},
        },
        {
            'stepId': f'click-send-{step_index}',
            'description': f'点击发送按钮 {step_index}',
            'action': 'click',
            'target': {'strategy': 'id', 'value': send_id},
        },
        {
            'stepId': f'wait-response-{step_index}',
            'description': wait_desc,
            'action': 'wait',
            'params': wait_params,
        },
    ]


def _excel_create_test_case(
    test_id, test_name, description, package_name,
    input_id, send_id, voice_toggle_id, wait_ms, timeout_ms,
    chat_steps, exceptions=None, case_type=None, webtest_meta=None,
):
    case_type = _excel_normalize_case_type(case_type, package_name)
    test_case = {
        'version': '1.0.0',
        'testId': test_id,
        'testName': test_name,
        'description': description,
        'caseType': case_type,
        'targetApp': {'packageName': package_name},
        'timeout': timeout_ms,
        'steps': [],
    }
    if webtest_meta:
        test_case['webtest'] = webtest_meta
    if exceptions:
        test_case['exceptions'] = exceptions

    clarify_map = {}
    for i, step in enumerate(chat_steps, 1):
        clarify = (step.get('clarify') or '').strip()
        if clarify:
            clarify_map[str(i)] = clarify
    if clarify_map:
        test_case['clarifyMessages'] = clarify_map

    test_case['steps'].extend([
        {
            'stepId': 'wait-home',
            'description': '用例切换缓冲',
            'action': 'wait',
            'params': {'duration': 500},
        },
        {
            'stepId': 'launch',
            'description': '启动目标应用',
            'action': 'launchApp',
        },
        {
            'stepId': 'wait-home',
            'description': '等待首页加载',
            'action': 'wait',
            'params': {'duration': 1000},
        },
    ])

    if package_name == 'com.example.devicesdkdemo':
        test_case['steps'].extend(_build_devicesdkdemo_pre_steps(package_name))
        _bump_timeout_for_devicesdkdemo_pre_steps(test_case)

    if voice_toggle_id:
        test_case['steps'].extend([
            {
                'stepId': 'click-voice-toggle',
                'description': '点击切换到文字输入模式',
                'action': 'click',
                'target': {'strategy': 'id', 'value': voice_toggle_id},
            },
            {
                'stepId': 'wait-toggle',
                'description': '等待切换完成',
                'action': 'wait',
                'params': {'duration': 500},
            },
        ])

    # QA：AISF 事件驱动；GUI：固定等待 + 截图垫步（云手机等）
    event_driven = case_type == 'QA' or package_name == 'com.example.devicesdkdemo'
    for i, step in enumerate(chat_steps, 1):
        step_wait = int(step.get('wait_ms', wait_ms) or wait_ms)
        if _is_voice_assistant_package(package_name):
            test_case['steps'].extend(
                _excel_create_voice_utterance_steps(
                    i, step.get('text', ''),
                    max(step_wait, int(timeout_ms or 0)),
                    clarify_message=step.get('clarify'),
                )
            )
            continue
        test_case['steps'].extend(
            _excel_create_chat_step(
                i, step.get('text', ''), input_id, send_id,
                step_wait,
                event_driven=event_driven,
                clarify_message=step.get('clarify'),
            )
        )

    # QA / DeviceSDK：不插长轮询截图垫步，避免问答结束后空等
    if case_type == 'GUI' and package_name != 'com.example.devicesdkdemo' and not _is_voice_assistant_package(package_name):
        shot_interval_ms = min(int(wait_ms), EXCEL_DEFAULT_CHAT_WAIT_MS)
        if shot_interval_ms <= 0:
            shot_interval_ms = EXCEL_DEFAULT_CHAT_WAIT_MS
        for i in range(max(0, (timeout_ms // max(shot_interval_ms, 1)) - 1)):
            test_case['steps'].append({
                'stepId': f'wait-screenshot-{i}',
                'description': f'等待截图 {i}',
                'action': 'wait',
                'params': {'duration': shot_interval_ms},
            })

    # 对齐本地 excel_convert：非 QA（含 DeviceSDK GUI）追加末尾 screenshot-result
    if case_type != 'QA':
        test_case['steps'].append({
            'stepId': 'screenshot-result',
            'description': '截图保存结果',
            'action': 'screenshot',
            'params': {'saveName': 'final_result'},
        })
    return _ensure_timeout_covers_wait_steps(test_case)


def _excel_parse_workbook(workbook, default_wait_ms=None, default_timeout_ms=None):
    if default_wait_ms is None:
        default_wait_ms = EXCEL_DEFAULT_CHAT_WAIT_MS
    if default_timeout_ms is None:
        default_timeout_ms = EXCEL_DEFAULT_TIMEOUT_MS

    ws = workbook.active
    headers = [str(cell.value).strip() if cell.value else '' for cell in ws[1]]

    col_map = {}
    step_columns = []
    for i, header in enumerate(headers):
        header_stripped = header.strip()
        header_lower = header_stripped.lower()
        if '用例编号' in header_stripped or 'case_id' in header_lower or 'testid' in header_lower:
            col_map['test_id'] = i
        elif '用例标题' in header_stripped:
            col_map['test_name'] = i
        elif '用例名称' in header_stripped or 'case_name' in header_lower or 'testname' in header_lower:
            if 'test_name' not in col_map:
                col_map['test_name'] = i
        elif 'S1场景' in header_stripped or header_lower == 's1':
            col_map['s1'] = i
        elif 'S2场景' in header_stripped or header_lower == 's2':
            col_map['s2'] = i
        elif 'S3场景' in header_stripped or header_lower == 's3':
            col_map['s3'] = i
        elif 'S4场景' in header_stripped or header_lower == 's4':
            col_map['s4'] = i
        elif '智能等级' in header_stripped:
            col_map['intelligence_level'] = i
        elif '因子组合' in header_stripped:
            col_map['factor_combo'] = i
        elif '预置条件' in header_stripped:
            col_map['pre_condition'] = i
        elif '用例等级' in header_stripped:
            col_map['case_level'] = i
        elif '用例集' in header_stripped or 'folder' in header_lower or 'category' in header_lower:
            col_map['folder'] = i
        elif header_stripped == '描述' or (
            '描述' in header_stripped and '测试' not in header_stripped
        ) or 'desc' in header_lower:
            col_map['description'] = i
        elif (
            header_stripped == 'APP'
            or '包名' in header_stripped
            or 'package' in header_lower
        ):
            col_map['package'] = i
        elif '单步操作等待时间' in header_stripped or header_stripped == '等待时间':
            col_map['step_waits'] = i
        elif '预期结果' in header_stripped:
            col_map['exceptions'] = i
        elif '澄清' in header_stripped or 'clarif' in header_lower:
            col_map['clarify'] = i
        elif '用例类型' in header_stripped or header_lower in ('case_type', 'casetype', 'type'):
            col_map['case_type'] = i
        elif header_lower == 'timeout' or header_stripped.startswith('timeout'):
            col_map['timeout'] = i
        elif 'wait_duration' in header_lower:
            col_map['wait_duration'] = i
        elif re.fullmatch(r'测试步骤\d+', header_stripped):
            step_columns.append(i)
        elif '测试步骤' in header_stripped or '语料' in header_stripped:
            col_map['steps_multiline'] = i

    step_columns.sort()
    webtest_grid = 's1' in col_map
    test_cases = []
    for row_idx, row in enumerate(ws.iter_rows(min_row=2), start=2):
        row_data = {}
        row_data['test_id'] = (
            str(row[col_map['test_id']].value or '').strip()
            if 'test_id' in col_map else f'TC-{row_idx - 1:03d}'
        )
        row_data['test_name'] = (
            str(row[col_map['test_name']].value or '').strip()
            if 'test_name' in col_map else row_data['test_id']
        )
        row_data['folder'] = (
            str(row[col_map['folder']].value or 'default').strip()
            if 'folder' in col_map else 'default'
        )
        row_data['description'] = (
            str(row[col_map['description']].value or '').strip()
            if 'description' in col_map else row_data['test_name']
        )
        for field in ('s1', 's2', 's3', 's4', 'factor_combo', 'pre_condition', 'intelligence_level'):
            if field in col_map:
                row_data[field] = str(row[col_map[field]].value or '').strip()
        if 'case_level' in col_map:
            try:
                from webtest_eval import normalize_webtest_case_level
                row_data['case_level'] = normalize_webtest_case_level(
                    row[col_map['case_level']].value
                )
            except Exception:
                row_data['case_level'] = str(row[col_map['case_level']].value or '').strip()
        if row_data.get('pre_condition'):
            row_data['description'] = row_data['pre_condition']
        if webtest_grid and row_data.get('s3') and row_data.get('folder') == 'default':
            row_data['folder'] = row_data['s3']
        if 'package' in col_map:
            row_data['package'] = str(row[col_map['package']].value or '').strip()

        row_timeout_ms = (
            _excel_parse_optional_ms(row[col_map['timeout']].value, default_timeout_ms)
            if 'timeout' in col_map else default_timeout_ms
        )
        row_wait_ms = (
            _excel_parse_optional_ms(row[col_map['wait_duration']].value, default_wait_ms)
            if 'wait_duration' in col_map else default_wait_ms
        )
        row_data['timeout_ms'] = row_timeout_ms
        row_data['wait_ms'] = row_wait_ms

        row_package = row_data.get('package')
        if 'case_type' in col_map:
            row_data['case_type'] = _excel_normalize_case_type(
                row[col_map['case_type']].value, row_package
            )
        elif webtest_grid:
            row_data['case_type'] = _excel_infer_case_type_from_webtest_row(row_data)
        else:
            row_data['case_type'] = _excel_normalize_case_type(None, row_package)

        chat_texts = []
        chat_steps = []
        exceptions = []
        clarify_map = (
            _excel_parse_numbered_map(row[col_map['clarify']].value)
            if 'clarify' in col_map else {}
        )
        if 'steps_multiline' in col_map:
            chat_texts = _excel_parse_numbered_lines(row[col_map['steps_multiline']].value)
            wait_list = (
                _excel_parse_wait_seconds_lines(
                    row[col_map['step_waits']].value, row_wait_ms
                )
                if 'step_waits' in col_map else []
            )
            for idx, text in enumerate(chat_texts):
                step_wait = wait_list[idx] if idx < len(wait_list) else row_wait_ms
                round_no = idx + 1
                clarify = (clarify_map.get(round_no) or '').strip()
                chat_steps.append({
                    'text': text,
                    'wait_ms': step_wait,
                    'clarify': clarify or None,
                })
            if 'exceptions' in col_map:
                exceptions = _excel_parse_numbered_lines(row[col_map['exceptions']].value)
        elif step_columns:
            for col_idx in step_columns:
                text = str(row[col_idx].value).strip() if row[col_idx].value else ''
                if text and text != 'None':
                    chat_texts.append(text)
            for idx, t in enumerate(chat_texts):
                round_no = idx + 1
                clarify = (clarify_map.get(round_no) or '').strip()
                chat_steps.append({
                    'text': t,
                    'wait_ms': row_wait_ms,
                    'clarify': clarify or None,
                })
        else:
            known_cols = set(col_map.values())
            for i, cell in enumerate(row):
                if i not in known_cols:
                    text = str(cell.value).strip() if cell.value else ''
                    if text and text != 'None':
                        chat_texts.append(text)
            for idx, t in enumerate(chat_texts):
                round_no = idx + 1
                clarify = (clarify_map.get(round_no) or '').strip()
                chat_steps.append({
                    'text': t,
                    'wait_ms': row_wait_ms,
                    'clarify': clarify or None,
                })

        row_data['chat_steps'] = chat_steps
        row_data['exceptions'] = exceptions
        if not row_data['test_id'] or not chat_texts:
            continue
        test_cases.append(row_data)
    return test_cases


def _excel_parse_bytes(data, default_wait_ms=None, default_timeout_ms=None):
    from openpyxl import load_workbook
    workbook = load_workbook(filename=io.BytesIO(data))
    return _excel_parse_workbook(workbook, default_wait_ms, default_timeout_ms)


def _excel_import_config_from_form(form):
    """从上传表单解析 Excel 导入默认配置。"""
    timeout_ms = form.get('timeout_ms', type=int)
    wait_duration = form.get('wait_duration', type=int)
    if timeout_ms is not None and timeout_ms <= 0:
        return None, 'timeout 须为正整数（毫秒）'
    if wait_duration is not None and wait_duration <= 0:
        return None, 'wait_duration 须为正整数（毫秒）'

    package_name = (form.get('package_name') or '').strip() or EXCEL_DEFAULT_PACKAGE
    if package_name not in EXCEL_PACKAGE_PRESETS:
        return None, f'不支持的目标应用包: {package_name}'

    pkg_cfg = _excel_get_package_ui_config(package_name)
    return {
        'timeout_ms': timeout_ms or EXCEL_DEFAULT_TIMEOUT_MS,
        'wait_ms': wait_duration or EXCEL_DEFAULT_CHAT_WAIT_MS,
        'package_name': pkg_cfg['package_name'],
        'input_id': pkg_cfg['input_id'],
        'send_id': pkg_cfg['send_id'],
        'voice_toggle_id': pkg_cfg['voice_toggle_id'],
    }, None


def _excel_import_config_from_json(data, target_package=None):
    """从任务创建 JSON 解析 Excel 转 JSON 配置。"""
    timeout_ms = data.get('timeout_ms')
    wait_duration = data.get('wait_duration')
    try:
        timeout_ms = int(timeout_ms) if timeout_ms is not None else None
    except (TypeError, ValueError):
        return None, 'timeout 须为正整数（毫秒）'
    try:
        wait_duration = int(wait_duration) if wait_duration is not None else None
    except (TypeError, ValueError):
        return None, 'wait_duration 须为正整数（毫秒）'
    if timeout_ms is not None and timeout_ms <= 0:
        return None, 'timeout 须为正整数（毫秒）'
    if wait_duration is not None and wait_duration <= 0:
        return None, 'wait_duration 须为正整数（毫秒）'

    package_name = (target_package or data.get('package_name') or '').strip() or EXCEL_DEFAULT_PACKAGE
    if package_name not in EXCEL_PACKAGE_PRESETS:
        return None, f'不支持的目标应用包: {package_name}'

    pkg_cfg = _excel_get_package_ui_config(package_name)
    return {
        'timeout_ms': timeout_ms or EXCEL_DEFAULT_TIMEOUT_MS,
        'wait_ms': wait_duration or EXCEL_DEFAULT_CHAT_WAIT_MS,
        'package_name': pkg_cfg['package_name'],
        'input_id': pkg_cfg['input_id'],
        'send_id': pkg_cfg['send_id'],
        'voice_toggle_id': pkg_cfg['voice_toggle_id'],
    }, None


def import_excel_bytes_to_testset(testset_dir, data, config=None):
    """将 Excel 字节流解析并写入用例集目录，返回导入结果。"""
    if config is None:
        config = {}

    package_name = config.get('package_name', EXCEL_DEFAULT_PACKAGE)
    pkg_ui = _excel_get_package_ui_config(package_name)
    input_id = config.get('input_id', pkg_ui['input_id'])
    send_id = config.get('send_id', pkg_ui['send_id'])
    voice_toggle_id = config.get('voice_toggle_id', pkg_ui['voice_toggle_id'])
    wait_ms = config.get('wait_ms', EXCEL_DEFAULT_CHAT_WAIT_MS)
    timeout_ms = config.get('timeout_ms', EXCEL_DEFAULT_TIMEOUT_MS)

    os.makedirs(testset_dir, exist_ok=True)
    try:
        test_cases = _excel_parse_bytes(
            data,
            default_wait_ms=wait_ms,
            default_timeout_ms=timeout_ms,
        )
    except ImportError:
        raise
    except Exception as e:
        return {'success': False, 'message': f'Excel 解析失败: {e}', 'created': [], 'skipped': []}

    if not test_cases:
        return {
            'success': False,
            'message': 'Excel 中未找到有效用例（需包含用例编号和至少一个测试步骤）',
            'created': [],
            'skipped': [],
        }

    created = []
    for tc_data in test_cases:
        tc_package = tc_data.get('package') or package_name
        tc_pkg_ui = _excel_get_package_ui_config(tc_package)
        tc_timeout_ms = tc_data.get('timeout_ms', timeout_ms)
        tc_wait_ms = tc_data.get('wait_ms', wait_ms)
        test_case = _excel_create_test_case(
            test_id=tc_data['test_id'],
            test_name=tc_data['test_name'],
            description=tc_data['description'],
            package_name=tc_package,
            input_id=tc_pkg_ui['input_id'],
            send_id=tc_pkg_ui['send_id'],
            voice_toggle_id=tc_pkg_ui['voice_toggle_id'],
            wait_ms=tc_wait_ms,
            timeout_ms=tc_timeout_ms,
            chat_steps=tc_data['chat_steps'],
            exceptions=tc_data.get('exceptions') or None,
            case_type=tc_data.get('case_type'),
            webtest_meta=_excel_row_to_webtest_meta(tc_data),
        )
        filename = f"{_excel_sanitize_filename(tc_data['test_id'])}.json"
        filepath = os.path.join(testset_dir, filename)
        with open(filepath, 'w', encoding='utf-8') as f:
            json.dump(test_case, f, ensure_ascii=False, indent=2)
        created.append(filename)

    return {
        'success': True,
        'message': f'已从 Excel 生成 {len(created)} 个用例',
        'created': created,
        'skipped': [],
    }


def build_excel_template_bytes():
    """生成用例模板 xlsx（对齐 GUI_danger0903 列）。"""
    import openpyxl
    from openpyxl.styles import Alignment

    wb = openpyxl.Workbook()
    ws = wb.active
    ws.title = 'Sheet1'
    headers = _excel_dataset_headers()
    for col, header in enumerate(headers, 1):
        ws.cell(row=1, column=col, value=header)

    ws.column_dimensions['A'].width = 18
    ws.column_dimensions['B'].width = 28
    ws.column_dimensions['C'].width = 22
    ws.column_dimensions['M'].width = 48
    ws.column_dimensions['N'].width = 14
    ws.column_dimensions['P'].width = 28

    # 列序同 GUI_danger0903；示例仅填常用列
    sample_rows = [
        [
            'GUI_DEMO_001', '示例：卸载QQ阅读', '',
            '', '', '', '',
            '', '', '', '', '',
            '1. 桌面长按「QQ阅读」应用图标，卸载「QQ阅读」APP',
            'QQ阅读', '否',
            '卸载成功', '', '', '', '', '', '',
        ],
        [
            'GUI_DEMO_002', '示例：交话费', '',
            '', '', '', '',
            '', '', '', '', '',
            '1. 帮我交100元话费',
            '', '否',
            '充值成功', '', '', '', '', '', '',
        ],
    ]
    wrap_cols = {1, 3, 13, 16}  # 1-based: 标题/预置/语料/预期
    for row_idx, row in enumerate(sample_rows, 2):
        for col_idx, value in enumerate(row, 1):
            cell = ws.cell(row=row_idx, column=col_idx, value=value)
            if col_idx in wrap_cols:
                cell.alignment = Alignment(wrap_text=True, vertical='top')

    buf = io.BytesIO()
    wb.save(buf)
    buf.seek(0)
    return buf


DEFAULT_EXCEL_HEADERS = _excel_dataset_headers()


def _excel_cell_to_str(value):
    if value is None:
        return ''
    if isinstance(value, float) and value == int(value):
        return str(int(value))
    return str(value).strip()


def read_excel_dataset_grid(xlsx_path):
    """读取 Excel 工作表为可编辑的表头 + 行数据。"""
    from openpyxl import load_workbook

    wb = load_workbook(xlsx_path, data_only=True)
    ws = wb.active
    all_rows = list(ws.iter_rows(values_only=True))
    wb.close()

    if not all_rows:
        return DEFAULT_EXCEL_HEADERS[:], []

    headers = [_excel_cell_to_str(c) for c in all_rows[0]]
    while headers and not headers[-1]:
        headers.pop()
    if not headers:
        headers = DEFAULT_EXCEL_HEADERS[:]

    col_count = len(headers)
    data_rows = []
    for row in all_rows[1:]:
        cells = [_excel_cell_to_str(c) for c in (row[:col_count] if row else [])]
        while len(cells) < col_count:
            cells.append('')
        if any(cell for cell in cells):
            data_rows.append(cells)
    return headers, data_rows


def count_excel_dataset_cases(xlsx_path):
    """统计 Excel 数据集中的用例行数（非空数据行）。"""
    try:
        _, rows = read_excel_dataset_grid(xlsx_path)
        return len(rows)
    except Exception as e:
        logger.warning(f'Count excel cases failed for {xlsx_path}: {e}')
        return 0


def write_excel_dataset_grid(xlsx_path, headers, rows):
    """将编辑后的表头 + 行数据写回 Excel 文件。"""
    import openpyxl
    from openpyxl.styles import Alignment

    headers = headers or DEFAULT_EXCEL_HEADERS[:]
    rows = rows or []
    wb = openpyxl.Workbook()
    ws = wb.active
    ws.title = '用例'

    wrap_col_indexes = set()
    for idx, header in enumerate(headers):
        if any(key in header for key in ('步骤', '等待', '预期', '描述', '语料', '预置', '因子')):
            wrap_col_indexes.add(idx)

    for col_idx, header in enumerate(headers, 1):
        ws.cell(row=1, column=col_idx, value=header)

    for row_idx, row in enumerate(rows, 2):
        for col_idx in range(len(headers)):
            value = row[col_idx] if col_idx < len(row) else ''
            value = '' if value is None else str(value)
            cell = ws.cell(row=row_idx, column=col_idx + 1, value=value or None)
            if col_idx in wrap_col_indexes:
                cell.alignment = Alignment(wrap_text=True, vertical='top')

    os.makedirs(os.path.dirname(xlsx_path), exist_ok=True)
    wb.save(xlsx_path)
    wb.close()


# ========== API: Test Files ==========

def resolve_testfile_path(testset, filename):
    """解析用例 JSON 路径，并防止目录穿越。"""
    if not testset or not filename:
        return None
    upload_root = os.path.abspath(app.config['UPLOAD_FOLDER'])
    testset_path = os.path.abspath(os.path.join(upload_root, testset))
    if not testset_path.startswith(upload_root + os.sep) and testset_path != upload_root:
        return None
    safe_name = secure_filename(filename)
    if not safe_name or not safe_name.endswith('.json'):
        return None
    filepath = os.path.join(testset_path, safe_name)
    if not os.path.abspath(filepath).startswith(testset_path + os.sep):
        return None
    return filepath


@app.route('/api/files/content', methods=['GET'])
def get_file_content():
    """读取用例 JSON 文件内容。"""
    testset = request.args.get('testset', '').strip()
    filename = request.args.get('filename', '').strip()
    filepath = resolve_testfile_path(testset, filename)
    if not filepath or not os.path.exists(filepath):
        return jsonify({'success': False, 'message': '文件不存在'}), 404
    try:
        with open(filepath, 'r', encoding='utf-8') as f:
            content = f.read()
        return jsonify({
            'success': True,
            'content': content,
            'filename': os.path.basename(filepath),
            'testset': testset,
        })
    except Exception as e:
        return jsonify({'success': False, 'message': str(e)}), 500


@app.route('/api/files/content', methods=['PUT'])
def save_file_content():
    """保存编辑后的用例 JSON。"""
    data = request.get_json() or {}
    testset = (data.get('testset') or '').strip()
    filename = (data.get('filename') or '').strip()
    content = data.get('content', '')
    filepath = resolve_testfile_path(testset, filename)
    if not filepath:
        return jsonify({'success': False, 'message': '无效的文件路径'}), 400
    if not os.path.exists(filepath):
        return jsonify({'success': False, 'message': '文件不存在'}), 404
    try:
        parsed = json.loads(content)
    except json.JSONDecodeError as e:
        return jsonify({'success': False, 'message': f'JSON 格式错误: {e}'}), 400
    try:
        with open(filepath, 'w', encoding='utf-8') as f:
            json.dump(parsed, f, indent=2, ensure_ascii=False)
            f.write('\n')
        scan_testfiles()
        logger.info(f"File updated: {filename} in {testset}")
        return jsonify({'success': True, 'message': '保存成功'})
    except Exception as e:
        return jsonify({'success': False, 'message': str(e)}), 500


@app.route('/api/files/download', methods=['POST'])
def download_testfiles():
    """将选中的用例 JSON 打包为 zip 下载。"""
    data = request.get_json() or {}
    testset = (data.get('testset') or '').strip()
    filenames = data.get('filenames', [])

    if not testset or not filenames:
        return jsonify({'success': False, 'message': '请选择要下载的文件'}), 400

    buf = io.BytesIO()
    added = 0
    with zipfile.ZipFile(buf, 'w', zipfile.ZIP_DEFLATED) as zf:
        for filename in filenames:
            filepath = resolve_testfile_path(testset, filename)
            if filepath and os.path.isfile(filepath):
                zf.write(filepath, os.path.basename(filepath))
                added += 1

    if added == 0:
        return jsonify({'success': False, 'message': '没有找到可下载的文件'}), 404

    buf.seek(0)
    zip_name = f'{secure_filename(testset) or "testset"}_testcases.zip'
    return send_file(
        buf,
        mimetype='application/zip',
        as_attachment=True,
        download_name=zip_name,
    )


@app.route('/api/files/upload', methods=['POST'])
def upload_file():
    """Upload test file to a testset."""
    if 'file' not in request.files:
        return jsonify({'success': False, 'message': 'No file provided'})

    file = request.files['file']
    testset = request.form.get('testset', 'default')

    if file.filename == '':
        return jsonify({'success': False, 'message': 'No file selected'})

    if not file.filename.endswith('.json'):
        return jsonify({'success': False, 'message': 'Only JSON files allowed'})

    # Create testset folder if not exists
    testset_path = os.path.join(app.config['UPLOAD_FOLDER'], testset)
    os.makedirs(testset_path, exist_ok=True)

    # Save file
    filename = secure_filename(file.filename)
    filepath = os.path.join(testset_path, filename)
    file.save(filepath)

    logger.info(f"File uploaded: {filename} to {testset}")
    scan_testfiles()

    return jsonify({'success': True, 'message': 'File uploaded', 'file': filename})


@app.route('/api/files/upload-excel', methods=['POST'])
def upload_excel_file():
    """Upload Excel and convert rows to JSON test cases in the selected testset."""
    if 'file' not in request.files:
        return jsonify({'success': False, 'message': '未选择文件'}), 400

    file = request.files['file']
    testset = request.form.get('testset', 'default').strip()

    if not file.filename:
        return jsonify({'success': False, 'message': '未选择文件'}), 400

    filename_lower = file.filename.lower()
    if not filename_lower.endswith(('.xlsx', '.xlsm')):
        return jsonify({'success': False, 'message': '仅支持 .xlsx / .xlsm 文件'}), 400

    testset_name = secure_filename(testset)
    if not testset_name:
        return jsonify({'success': False, 'message': '无效的用例集名称'}), 400

    testset_path = os.path.join(app.config['UPLOAD_FOLDER'], testset_name)
    if not os.path.isdir(testset_path):
        return jsonify({'success': False, 'message': '用例集不存在，请先创建'}), 404

    config, config_error = _excel_import_config_from_form(request.form)
    if config_error:
        return jsonify({'success': False, 'message': config_error}), 400

    try:
        result = import_excel_bytes_to_testset(testset_path, file.read(), config=config)
    except ImportError:
        return jsonify({
            'success': False,
            'message': '服务端缺少 openpyxl，请执行 pip install openpyxl',
        }), 500
    except Exception as e:
        logger.error(f"Excel upload failed: {e}")
        return jsonify({'success': False, 'message': f'Excel 转换失败: {e}'}), 500

    if not result.get('success'):
        return jsonify(result), 400

    scan_testfiles()
    logger.info(
        f"Excel uploaded: {file.filename} -> {testset_name}, "
        f"created {len(result.get('created', []))} json files"
    )
    return jsonify(result)


@app.route('/api/files/excel-template')
def download_excel_template():
    """Download Excel test case template."""
    template_path = os.path.join(os.path.dirname(__file__), '用例-格式.xlsx')
    if os.path.isfile(template_path):
        return send_file(
            template_path,
            mimetype='application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
            as_attachment=True,
            download_name='用例-格式.xlsx',
        )

    try:
        buf = build_excel_template_bytes()
        return send_file(
            buf,
            mimetype='application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
            as_attachment=True,
            download_name='用例-格式.xlsx',
        )
    except ImportError:
        return jsonify({
            'success': False,
            'message': '服务端缺少 openpyxl，请执行 pip install openpyxl',
        }), 500
    except Exception as e:
        logger.error(f"Excel template generation failed: {e}")
        return jsonify({'success': False, 'message': f'模板生成失败: {e}'}), 500


# ========== API: Excel Datasets ==========

@app.route('/api/excel-datasets')
def get_excel_datasets_api():
    """List Excel datasets (stored as .xlsx, not converted to JSON)."""
    scan_excel_datasets()
    with excel_datasets_lock:
        datasets = sorted(
            excel_datasets.values(),
            key=lambda x: float(x.get('last_modified') or x.get('create_time') or 0),
            reverse=True,
        )
    resp = jsonify({'success': True, 'datasets': datasets})
    resp.headers['Cache-Control'] = 'no-store, no-cache, must-revalidate, max-age=0'
    resp.headers['Pragma'] = 'no-cache'
    return resp


@app.route('/api/excel-datasets', methods=['POST'])
def create_excel_dataset_api():
    """Create Excel dataset folder and optionally store uploaded Excel file."""
    dataset_name = (request.form.get('name') or '').strip()
    if not dataset_name and request.is_json:
        data = request.get_json(silent=True) or {}
        dataset_name = (data.get('name') or '').strip()

    if not dataset_name:
        return jsonify({'success': False, 'message': '请输入数据集名称'}), 400

    safe_name = secure_filename(dataset_name)
    if not safe_name:
        return jsonify({'success': False, 'message': '无效的数据集名称'}), 400

    dataset_path = os.path.join(app.config['EXCEL_DATASETS_FOLDER'], safe_name)
    if os.path.exists(dataset_path):
        return jsonify({'success': False, 'message': '数据集已存在'}), 400

    os.makedirs(dataset_path, exist_ok=True)

    uploaded = request.files.get('file') if request.files else None
    dest_path = os.path.join(dataset_path, EXCEL_DATASET_FILENAME)
    if uploaded and uploaded.filename:
        if not uploaded.filename.lower().endswith(('.xlsx', '.xlsm')):
            shutil.rmtree(dataset_path, ignore_errors=True)
            return jsonify({'success': False, 'message': '仅支持 .xlsx / .xlsm 文件'}), 400
        uploaded.save(dest_path)
    else:
        # 未上传时写入 GUI_danger0903 表头空表，便于直接编辑
        try:
            write_excel_dataset_grid(dest_path, DEFAULT_EXCEL_HEADERS[:], [])
        except Exception as e:
            shutil.rmtree(dataset_path, ignore_errors=True)
            logger.error(f'Create empty excel dataset failed: {e}')
            return jsonify({'success': False, 'message': f'创建空模板失败: {e}'}), 500

    scan_excel_datasets()
    return jsonify({'success': True, 'message': f'数据集 {safe_name} 已创建', 'name': safe_name})


@app.route('/api/excel-datasets/<dataset_name>/upload', methods=['POST'])
def upload_excel_dataset_file_api(dataset_name):
    """Replace Excel file for an existing dataset.

    也接受「看起来像 xlsx」的 JSON 表格上传（浏览器保存大表时走此通道，
    以绕过部分网关对 /content POST 的 403 拦截）。
    """
    safe_name = secure_filename(dataset_name)
    if not safe_name or safe_name != dataset_name:
        return jsonify({'success': False, 'message': '无效的数据集名称'}), 400

    dataset_path = os.path.join(app.config['EXCEL_DATASETS_FOLDER'], safe_name)
    if not os.path.isdir(dataset_path):
        return jsonify({'success': False, 'message': '数据集不存在'}), 404

    if 'file' not in request.files:
        return jsonify({'success': False, 'message': '未选择文件'}), 400
    file = request.files['file']
    if not file.filename:
        return jsonify({'success': False, 'message': '未选择文件'}), 400

    raw = file.read() or b''
    stripped = raw.lstrip()
    # JSON 表格保存：字段名仍为 file / dataset.xlsx，内容为 {"headers":[],"rows":[]}
    if stripped[:1] in (b'{', b'[') or file.filename.lower().endswith('.json'):
        try:
            data = json.loads(raw.decode('utf-8', errors='replace'))
        except Exception as e:
            return jsonify({'success': False, 'message': f'表格 JSON 无效: {e}'}), 400
        headers = data.get('headers') if isinstance(data, dict) else None
        rows = data.get('rows') if isinstance(data, dict) else None
        if not isinstance(headers, list) or not isinstance(rows, list):
            return jsonify({'success': False, 'message': '表格 JSON 需要 headers/rows'}), 400
        if not headers:
            headers = DEFAULT_EXCEL_HEADERS[:]
        dest_path = os.path.join(dataset_path, EXCEL_DATASET_FILENAME)
        try:
            write_excel_dataset_grid(dest_path, headers, rows)
        except ImportError:
            return jsonify({
                'success': False,
                'message': '服务端缺少 openpyxl，请执行 pip install openpyxl',
            }), 500
        except Exception as e:
            logger.error(f"Write excel dataset via upload failed: {e}")
            return jsonify({'success': False, 'message': f'保存 Excel 失败: {e}'}), 500
        logger.info(
            'excel grid save via upload: dataset=%s rows=%s bytes=%s',
            safe_name, len(rows), len(raw),
        )
        scan_excel_datasets()
        return jsonify({'success': True, 'message': '数据集内容已保存'})

    if not file.filename.lower().endswith(('.xlsx', '.xlsm')):
        return jsonify({'success': False, 'message': '仅支持 .xlsx / .xlsm 文件'}), 400

    for existing in os.listdir(dataset_path):
        if existing.lower().endswith(('.xlsx', '.xlsm')):
            os.remove(os.path.join(dataset_path, existing))

    dest_path = os.path.join(dataset_path, EXCEL_DATASET_FILENAME)
    with open(dest_path, 'wb') as out:
        out.write(raw)
    scan_excel_datasets()
    return jsonify({'success': True, 'message': 'Excel 文件已更新'})


@app.route('/api/excel-datasets/<dataset_name>/download')
def download_excel_dataset_api(dataset_name):
    """Download Excel file for a dataset."""
    xlsx_path = get_excel_dataset_file_path(dataset_name)
    if not xlsx_path:
        return jsonify({'success': False, 'message': 'Excel 文件不存在'}), 404
    download_name = f'{dataset_name}_{EXCEL_DATASET_FILENAME}'
    return send_file(
        xlsx_path,
        mimetype='application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        as_attachment=True,
        download_name=download_name,
    )


@app.route('/api/excel-datasets/<dataset_name>/content', methods=['GET'])
def get_excel_dataset_content(dataset_name):
    """Get editable Excel sheet content for a dataset."""
    safe_name = secure_filename(dataset_name)
    if not safe_name or safe_name != dataset_name:
        return jsonify({'success': False, 'message': '无效的数据集名称'}), 400

    dataset_path = os.path.join(app.config['EXCEL_DATASETS_FOLDER'], safe_name)
    if not os.path.isdir(dataset_path):
        return jsonify({'success': False, 'message': '数据集不存在'}), 404

    xlsx_path = get_excel_dataset_file_path(dataset_name)
    if not xlsx_path:
        return jsonify({
            'success': True,
            'headers': DEFAULT_EXCEL_HEADERS[:],
            'rows': [],
            'has_file': False,
        })

    try:
        headers, rows = read_excel_dataset_grid(xlsx_path)
    except ImportError:
        return jsonify({
            'success': False,
            'message': '服务端缺少 openpyxl，请执行 pip install openpyxl',
        }), 500
    except Exception as e:
        logger.error(f"Read excel dataset content failed: {e}")
        return jsonify({'success': False, 'message': f'读取 Excel 失败: {e}'}), 500

    return jsonify({
        'success': True,
        'headers': headers,
        'rows': rows,
        'has_file': True,
    })


@app.route('/api/excel-datasets/<dataset_name>/content', methods=['PUT', 'POST'])
def put_excel_dataset_content(dataset_name):
    """Save edited Excel sheet content back to dataset file.

    同时接受 PUT/POST，以及 JSON / multipart form：
    部分代理会丢掉 PUT 或 application/json 请求体（body_len=0）。
    """
    safe_name = secure_filename(dataset_name)
    if not safe_name or safe_name != dataset_name:
        return jsonify({'success': False, 'message': '无效的数据集名称'}), 400

    dataset_path = os.path.join(app.config['EXCEL_DATASETS_FOLDER'], safe_name)
    if not os.path.isdir(dataset_path):
        return jsonify({'success': False, 'message': '数据集不存在'}), 404

    data = {}
    ctype = (request.content_type or '').lower()
    # 先读 multipart（勿先 get_data/get_json，否则会吃掉 body）
    # 优先 payload 文件字段：大表走文件上传通道，比巨大 form 文本字段更稳
    payload_file = request.files.get('payload') if request.files else None
    if payload_file and getattr(payload_file, 'filename', None) is not None:
        try:
            raw_payload = payload_file.read()
            if isinstance(raw_payload, bytes):
                raw_payload = raw_payload.decode('utf-8', errors='replace')
            parsed = json.loads((raw_payload or '').strip() or '{}')
            if isinstance(parsed, dict):
                data = parsed
        except Exception as e:
            logger.warning('excel content save payload file parse failed: %s', e)
    if 'multipart/form-data' in ctype or 'application/x-www-form-urlencoded' in ctype:
        if not isinstance(data.get('headers'), list) and 'headers' in request.form:
            try:
                data['headers'] = json.loads(request.form.get('headers') or '[]')
            except Exception:
                data['headers'] = None
        if not isinstance(data.get('rows'), list) and 'rows' in request.form:
            try:
                data['rows'] = json.loads(request.form.get('rows') or '[]')
            except Exception:
                data['rows'] = None
    if not isinstance(data.get('headers'), list) or not isinstance(data.get('rows'), list):
        parsed = request.get_json(silent=True, force=True)
        if isinstance(parsed, dict):
            data = parsed
        elif not data:
            raw = (request.get_data(as_text=True) or '').strip()
            if raw:
                try:
                    parsed = json.loads(raw)
                    if isinstance(parsed, dict):
                        data = parsed
                except Exception:
                    pass

    headers = data.get('headers')
    rows = data.get('rows')
    logger.info(
        'excel content save attempt: dataset=%s method=%s content_type=%r '
        'body_len=%s form_keys=%s file_keys=%s row_count=%s',
        safe_name,
        request.method,
        request.content_type,
        request.content_length,
        list(request.form.keys()),
        list(request.files.keys()),
        len(rows) if isinstance(rows, list) else None,
    )
    if not isinstance(headers, list) or not isinstance(rows, list):
        logger.warning(
            'excel content save rejected: dataset=%s method=%s content_type=%r '
            'body_len=%s headers_type=%s rows_type=%s',
            safe_name,
            request.method,
            request.content_type,
            request.content_length,
            type(headers).__name__,
            type(rows).__name__,
        )
        return jsonify({
            'success': False,
            'message': (
                '请求格式无效（需要 headers/rows；'
                f'body长度={request.content_length or 0}，'
                f'Content-Type={request.content_type or "-"}）'
            ),
        }), 400
    if not headers:
        headers = DEFAULT_EXCEL_HEADERS[:]

    dest_path = os.path.join(dataset_path, EXCEL_DATASET_FILENAME)
    try:
        write_excel_dataset_grid(dest_path, headers, rows)
    except ImportError:
        return jsonify({
            'success': False,
            'message': '服务端缺少 openpyxl，请执行 pip install openpyxl',
        }), 500
    except Exception as e:
        logger.error(f"Write excel dataset content failed: {e}")
        return jsonify({'success': False, 'message': f'保存 Excel 失败: {e}'}), 500

    scan_excel_datasets()
    return jsonify({'success': True, 'message': '数据集内容已保存'})


@app.route('/api/excel-datasets/<dataset_name>', methods=['DELETE'])
def delete_excel_dataset_api(dataset_name):
    """Delete an Excel dataset."""
    safe_name = secure_filename(dataset_name)
    if not safe_name or safe_name != dataset_name:
        return jsonify({'success': False, 'message': '无效的数据集名称'}), 400

    dataset_path = os.path.join(app.config['EXCEL_DATASETS_FOLDER'], safe_name)
    if not os.path.isdir(dataset_path):
        return jsonify({'success': False, 'message': '数据集不存在'}), 404

    shutil.rmtree(dataset_path)
    scan_excel_datasets()
    return jsonify({'success': True, 'message': f'数据集 {safe_name} 已删除'})


@app.route('/api/files/delete', methods=['POST'])
def delete_file():
    """Delete test file(s)."""
    data = request.get_json()
    files = data.get('files', [])  # List of {testset, filename}

    deleted = []
    for f in files:
        testset = f.get('testset', 'default')
        filename = f.get('filename')
        filepath = os.path.join(app.config['UPLOAD_FOLDER'], testset, secure_filename(filename))
        if os.path.exists(filepath):
            os.remove(filepath)
            deleted.append(filename)

    scan_testfiles()
    return jsonify({'success': True, 'deleted': deleted})


@app.route('/api/target-apps')
def get_target_apps():
    """获取可选的目标应用包列表（与 Excel 导入预设一致）。"""
    apps = [
        {'package': pkg, 'label': cfg['label']}
        for pkg, cfg in EXCEL_PACKAGE_PRESETS.items()
    ]
    return jsonify({'success': True, 'apps': apps})

# ========== API: Tasks ==========

@app.route('/api/tasks', methods=['GET'])
def get_tasks():
    """Get tasks with pagination."""
    page = max(1, int(request.args.get('page', 1)))
    per_page = int(request.args.get('per_page', 10))
    if per_page not in (10, 20, 50):
        per_page = 10

    task_list = load_tasks()
    sorted_tasks = sorted(
        task_list.values(), key=get_task_sort_key, reverse=True
    )
    total = len(sorted_tasks)
    start = (page - 1) * per_page
    page_tasks = sorted_tasks[start:start + per_page]
    for task in page_tasks:
        st = task.get('status')
        allow_disk = st not in ('pending', 'running', 'finishing', 'analyzing')
        task['case_pass'] = get_task_case_pass_stats(task, allow_disk=allow_disk)
    has_active = any(
        t.get('status') in ('pending', 'running', 'finishing', 'analyzing') for t in sorted_tasks
    )

    return jsonify({
        'success': True,
        'tasks': page_tasks,
        'total': total,
        'page': page,
        'per_page': per_page,
        'total_pages': (total + per_page - 1) // per_page if total else 0,
        'has_active_tasks': has_active,
    })

@app.route('/api/tasks', methods=['POST'])
def create_task():
    """Create a new task."""
    data = request.get_json() or {}
    logger.info(
        'create_task request devices=%s package=%s excel=%s testset=%s auto_wake=%s',
        data.get('devices'), data.get('target_package'),
        data.get('excel_dataset'), data.get('testset'), data.get('auto_wake'),
    )
    task_id = (data.get('task_id') or '').strip()
    account = (data.get('account') or '').strip()
    device_ids = data.get('devices', [])
    testset = (data.get('testset') or '').strip()
    excel_dataset = (data.get('excel_dataset') or '').strip()
    source_type = (data.get('source_type') or '').strip()
    target_package = (data.get('target_package') or '').strip() or EXCEL_DEFAULT_PACKAGE
    start_type = data.get('start_type', 'immediate')  # immediate or scheduled
    schedule_time = data.get('schedule_time')
    concurrency_mode = (data.get('concurrency_mode') or 'broadcast').strip().lower()
    if concurrency_mode not in ('broadcast', 'shard'):
        return jsonify({
            'success': False,
            'message': 'concurrency_mode 仅支持 broadcast（同案广播）或 shard（多机分片并发）',
        }), 400

    if not task_id:
        task_id = generate_task_id()
    # account 可选：未填则跳过云侧日志下载
    account = (account or '').strip()

    # DeviceSDK：未选设备时自动用 TestApp WS（经 TestApp 中转，不直连 Demo）
    is_devicesdk = _is_devicesdk_package(target_package)
    if is_devicesdk:
        try:
            device_ids = _normalize_devicesdk_relay_device_ids(device_ids or [])
            if device_ids:
                logger.info('create_task: DeviceSDK relay via TestApp %s', device_ids)
        except Exception as e:
            logger.warning('create_task TestApp relay normalize failed: %s', e)

    if is_devicesdk:
        serial_err = _reject_devicesdk_adb_serials(device_ids)
        if serial_err:
            return jsonify({'success': False, 'message': serial_err}), 400
        if not device_ids:
            return jsonify({
                'success': False,
                'message': (
                    '没有在线的 TestApp（DeviceSDK 任务经 TestApp 中转）。'
                    '请打开手机 TestApp 并连接 9526。'
                ),
            }), 400

    # 新建任务时若没有足够在线设备，先拉起各模拟器 TestApp
    # DeviceSDK（真机经 TestApp 中转）：禁止走实验室 SSH wake，避免卡死整个 9526
    auto_wake = data.get('auto_wake')
    if auto_wake is None:
        auto_wake = not is_devicesdk
    if is_devicesdk:
        auto_wake = False
    try:
        from task_preflight import wake_lab_testapps
        need_wake = bool(auto_wake) and not is_devicesdk
    except Exception:
        need_wake = False
        wake_lab_testapps = None  # type: ignore

    if need_wake and wake_lab_testapps is not None:
        with devices_lock:
            all_online = [did for did, info in devices.items() if info.get('ws')]
        min_need = 2 if concurrency_mode == 'shard' else 1
        online_now = [d for d in (device_ids or []) if d in all_online]

        # 已有足够在线设备且用户未选：自动全选，无需再 wake
        if not device_ids and len(all_online) >= min_need:
            device_ids = list(all_online)
        elif len(all_online) < min_need or (device_ids and not online_now):
            logger.info(
                'create_task: waking TestApp on lab AVDs '
                f'(online={len(all_online)}, selected={device_ids})'
            )
            try:
                from task_preflight import wake_lab_testapps, serials_for_device_ids
                wake_serials = serials_for_device_ids(device_ids)
                wake_result = wake_lab_testapps(
                    serials=wake_serials or None,
                    wait_online_sec=float(data.get('wake_wait_sec') or 90),
                    min_online=min_need,
                    device_ids=device_ids,
                )
            except Exception as e:
                logger.exception('create_task wake failed: %s', e)
                return jsonify({
                    'success': False,
                    'message': f'拉起模拟器 TestApp 失败: {e}',
                }), 500
            with devices_lock:
                all_online = [did for did, info in devices.items() if info.get('ws')]
            if not device_ids:
                device_ids = list(all_online)
            else:
                device_ids = [d for d in device_ids if d in all_online] or list(all_online)
            if len(device_ids) < min_need:
                return jsonify({
                    'success': False,
                    'message': (
                        wake_result.get('message')
                        or f'拉起后在线设备不足（需要≥{min_need}，当前{len(device_ids)}）'
                    ),
                }), 400

    if not device_ids:
        return jsonify({'success': False, 'message': 'Select at least one device'})
    if concurrency_mode == 'shard' and len(device_ids) < 2:
        return jsonify({
            'success': False,
            'message': '分片并发模式请至少选择 2 台设备',
        }), 400

    if excel_dataset:
        source_type = 'excel'
    elif testset:
        source_type = source_type or 'json'
    else:
        return jsonify({'success': False, 'message': '请选择 Excel 数据集或 JSON 用例集'})

    if target_package not in EXCEL_PACKAGE_PRESETS:
        return jsonify({'success': False, 'message': f'不支持的目标应用包: {target_package}'}), 400

    offline = [device_id for device_id in device_ids if not is_device_online(device_id)]
    # DeviceSDK 经 TestApp WS：即时任务要求 TestApp 已在线；定时任务可稍后连 WS
    allow_offline = is_devicesdk and start_type == 'scheduled'
    if offline and not allow_offline:
        hint = ''
        if is_devicesdk:
            hint = '（请确认 TestApp 已连接 9526；DeviceSDK 不再直连 WebServer）'
        return jsonify({
            'success': False,
            'message': f'以下设备未在线或不存在，无法使用: {", ".join(offline)}{hint}',
        }), 400

    if start_type == 'scheduled':
        if not schedule_time:
            return jsonify({'success': False, 'message': '请选择定时执行时间'}), 400
        schedule_ts = parse_schedule_time(schedule_time)
        if schedule_ts is None:
            return jsonify({'success': False, 'message': '定时时间格式无效'}), 400
        if schedule_ts <= time.time():
            return jsonify({'success': False, 'message': '定时时间须晚于当前时间'}), 400
    else:
        available, busy_message = check_devices_available(
            device_ids,
            require_online=not allow_offline,
            allow_adb=False if is_devicesdk else (
                allow_offline and all(
                    is_adb_visible_device(d) or is_lab_known_device(d)
                    for d in device_ids
                )
            ),
        )
        if not available:
            return jsonify({'success': False, 'message': busy_message}), 400

    if is_devicesdk and start_type != 'scheduled':
        blocked = _screen_capture_block_response(device_ids)
        if blocked:
            return blocked

    # Check if task_id already exists
    existing_tasks = load_tasks()
    if task_id in existing_tasks:
        return jsonify({'success': False, 'message': '任务ID冲突，请重试'})

    excel_config = None
    if source_type == 'excel':
        safe_dataset = secure_filename(excel_dataset)
        if not safe_dataset or safe_dataset != excel_dataset:
            return jsonify({'success': False, 'message': '无效的 Excel 数据集名称'}), 400
        scan_excel_datasets()
        with excel_datasets_lock:
            if safe_dataset not in excel_datasets:
                return jsonify({'success': False, 'message': 'Excel 数据集不存在'}), 404
            if not excel_datasets[safe_dataset].get('has_file'):
                return jsonify({'success': False, 'message': 'Excel 数据集尚未上传 Excel 文件'}), 400

        excel_config, config_error = _excel_import_config_from_json(data, target_package)
        if config_error:
            return jsonify({'success': False, 'message': config_error}), 400

        runtime_testset, convert_result = convert_excel_dataset_to_runtime(
            task_id, safe_dataset, excel_config
        )
        if not runtime_testset:
            return jsonify({'success': False, 'message': convert_result}), 400
        testset = runtime_testset
        test_files = convert_result
    else:
        scan_testfiles()
        with testsets_lock:
            if testset not in testsets:
                return jsonify({'success': False, 'message': 'Testset not found'})
            test_files = [f['name'] for f in testsets[testset]['files']]

        if not test_files:
            return jsonify({'success': False, 'message': 'Testset has no files'})

    now = time.time()
    # Create task
    task = {
        'id': task_id,
        'account': account,
        'devices': device_ids,
        'source_type': source_type,
        'excel_dataset': excel_dataset if source_type == 'excel' else None,
        'excel_config': excel_config if source_type == 'excel' else None,
        'testset': testset,
        'target_package': target_package,
        'target_app_label': get_target_app_label(target_package),
        'concurrency_mode': concurrency_mode,
        'files': test_files,
        'start_type': start_type,
        'schedule_time': schedule_time,
        'created_at': now,
        'start_time': now if start_type == 'immediate' else None,
        'status': 'pending' if start_type == 'scheduled' else 'running',
        'results': [],
        'case_progress': init_case_progress(test_files),
        'progress': {'completed': 0, 'total': len(test_files), 'percent': 0},
    }
    if is_devicesdk:
        task['execution_mode'] = 'ws'

    # 允许复用曾删除过的 task_id（删除时会标记 cancelled）
    clear_task_cancelled(task_id)
    save_task(task)
    if task_id not in load_tasks():
        logger.error(f"Task {task_id} save failed after create")
        return jsonify({'success': False, 'message': '任务保存失败，请更换任务ID后重试'}), 500

    # If immediate, start execution
    if start_type == 'immediate':
        threading.Thread(target=execute_task, args=(task_id,), daemon=True).start()

    logger.info(f"Task created: {task_id}")
    return jsonify({'success': True, 'message': 'Task created', 'task_id': task_id})


def init_case_progress(files):
    """初始化任务内各用例的进度状态。"""
    return [
        {
            'case_id': os.path.splitext(f)[0],
            'filename': f,
            'status': 'pending',
        }
        for f in files
    ]


def parse_evaluation_passed(eval_data):
    """从 evaluation.json 解析用例是否通过（VL/QA 最终结果）。"""
    if not eval_data:
        return None
    if eval_data.get('passed') is not None:
        return bool(eval_data.get('passed'))
    parsed = eval_data.get('result_parsed')
    if not isinstance(parsed, list):
        return None
    round_items = [
        x for x in parsed
        if x.get('round') is not None and x.get('result')
    ]
    if round_items and any(x.get('result') != 'success' for x in round_items):
        return False
    for item in parsed:
        if item.get('final_result') is not None:
            return item.get('result') == 'success'
    if round_items:
        return all(x.get('result') == 'success' for x in round_items)
    return None


def _strip_qa_screenshot_steps(testcase):
    """QA 用例不下发/不依赖截图步骤（评判用模型回复）。"""
    if not isinstance(testcase, dict) or get_case_type(testcase) != 'QA':
        return testcase
    steps = testcase.get('steps') or []
    filtered = [
        s for s in steps
        if s.get('action') != 'screenshot'
        and not str(s.get('stepId') or '').startswith('wait-screenshot')
    ]
    if len(filtered) == len(steps):
        return testcase
    out = json.loads(json.dumps(testcase, ensure_ascii=False))
    out['steps'] = filtered
    return out


def get_case_type(testcase, target_package=None):
    """用例类型 GUI / QA。"""
    if not isinstance(testcase, dict):
        testcase = {}
    ct = (testcase.get('caseType') or '').strip().upper()
    if ct in ('GUI', 'QA'):
        return ct
    pkg = target_package or (testcase.get('targetApp') or {}).get('packageName')
    if pkg == 'com.example.devicesdkdemo':
        return 'QA'
    return 'GUI'


def extract_case_chat_rounds(testcase):
    """从用例 JSON 提取每轮用户提问。"""
    rows = []
    if not isinstance(testcase, dict):
        return rows
    for step in testcase.get('steps') or []:
        step_id = step.get('stepId') or ''
        if not step_id.startswith('input-message-'):
            continue
        try:
            round_no = int(step_id.replace('input-message-', ''))
        except ValueError:
            continue
        text = (step.get('params') or {}).get('text') or ''
        rows.append({'round': round_no, 'user_message': text})
    rows.sort(key=lambda x: x['round'])
    return rows


def format_report_timestamp(ts):
    if not ts:
        return ''
    try:
        if isinstance(ts, (int, float)) and ts > 1e12:
            ts = ts / 1000.0
        return datetime.fromtimestamp(float(ts)).strftime('%Y-%m-%d %H:%M:%S')
    except Exception:
        return str(ts)


def case_status_display_text(status, fail_reason=None):
    """用例执行状态中文文案（预览/导出用）。"""
    key = str(status or '').strip().lower()
    labels = {
        'pending': '等待中',
        'queued': '排队中',
        'running': '执行中',
        'finishing': '收尾中',
        'analyzing': '分析中',
        'completed': '已完成',
        'cancelled': '已取消',
        'failed': '失败',
        'takeover': '人工接管',
        'skipped': '跳过',
    }
    text = labels.get(key) or (status or '')
    reason = (fail_reason or '').strip()
    if reason and key in ('failed', 'cancelled', 'takeover'):
        # 接管原因常与状态同文，避免「人工接管（人工接管）」
        if reason != text:
            return f'{text}（{reason}）'
    return text


def build_case_report_rows(task_id, case_id, testcase=None, eval_data=None):
    """构建单用例分步结果行（对齐导出/预览表格）。"""
    task = load_tasks().get(task_id) or {}
    if testcase is None:
        testset = task.get('testset')
        if testset:
            try:
                testcase = load_adapted_testcase(
                    testset, f'{case_id}.json', task.get('target_package'))
            except Exception:
                testcase = {}
    testcase = testcase or {}
    exceptions = extract_case_exceptions(testcase)
    clarify_map = testcase.get('clarifyMessages') or {}
    chat_rounds = {r['round']: r['user_message'] for r in extract_case_chat_rounds(testcase)}
    replies = load_case_replies(task_id, case_id)
    reply_by_round = {
        r.get('round'): r for r in replies if r.get('round') is not None
    }
    latency_by_round = {}
    first_token_by_round = {}
    for item in load_case_round_latency(task_id, case_id) or []:
        rnd = item.get('round')
        if rnd is not None and item.get('latency_ms') is not None:
            latency_by_round[rnd] = item.get('latency_ms')
        if rnd is not None and item.get('first_token_latency_ms') is not None:
            first_token_by_round[rnd] = item.get('first_token_latency_ms')

    gui_step_avg_by_round = {}
    for item in load_gui_step_latency(task_id, case_id) or []:
        rnd = item.get('round')
        if rnd is None:
            continue
        stats = compute_gui_step_avg_latencies_sec(
            item.get('device_steps') or [],
            item.get('cloud_steps') or [],
        )
        if stats.get('avg_step_sec') is not None:
            gui_step_avg_by_round[rnd] = stats

    if eval_data is None:
        eval_path = os.path.join(
            app.config['TESTRESULTS_FOLDER'], task_id, case_id, 'evaluation.json')
        if os.path.isfile(eval_path):
            try:
                with open(eval_path, 'r', encoding='utf-8') as f:
                    eval_data = json.load(f)
            except Exception:
                eval_data = None
    if eval_data:
        try:
            from webtest_eval import hydrate_webtest_evaluation
            eval_data = hydrate_webtest_evaluation(eval_data)
        except Exception:
            pass

    eval_by_round = {}
    # 预览/导出「评判」列使用用例最终综合结果（文字+截图），不是单轮文字 success
    overall_result = None
    if eval_data:
        overall_passed = None
        if eval_data.get('passed') is not None:
            overall_passed = bool(eval_data.get('passed'))
        else:
            overall_passed = parse_evaluation_passed(eval_data)
        if overall_passed is True:
            overall_result = 'success'
        elif overall_passed is False:
            overall_result = 'failed'
        parsed = eval_data.get('result_parsed') or []
        for item in parsed:
            if not isinstance(item, dict):
                continue
            if item.get('round') is not None:
                try:
                    eval_by_round[int(item['round'])] = item
                except (TypeError, ValueError):
                    pass
        # VL 截图评判通常只有 expectation/result，没有 round；
        # 按非 final 条目顺序映射到第 1、2、… 轮，避免下拉里一直「未评判」。
        if not eval_by_round:
            step_items = [
                x for x in parsed
                if isinstance(x, dict)
                and x.get('final_result') is None
                and x.get('result')
            ]
            for idx, item in enumerate(step_items, start=1):
                eval_by_round[idx] = item

    round_eval_by_round = {}
    if eval_data:
        for item in eval_data.get('round_evaluations') or []:
            if not isinstance(item, dict):
                continue
            try:
                round_eval_by_round[int(item.get('round'))] = item
            except (TypeError, ValueError):
                continue

    progress = None
    for item in task.get('case_progress') or []:
        if item.get('case_id') == case_id or item.get('filename') == f'{case_id}.json':
            progress = item
            break

    case_status = (progress or {}).get('status') or ''
    fail_reason = (progress or {}).get('fail_reason') or ''
    status_text = case_status_display_text(case_status, fail_reason)

    max_round = max(
        len(exceptions),
        max(chat_rounds.keys()) if chat_rounds else 0,
        max(reply_by_round.keys()) if reply_by_round else 0,
        max((int(k) for k in clarify_map.keys()), default=0),
        0,
    )

    rows = []
    for i in range(1, max_round + 1):
        rep = reply_by_round.get(i) or {}
        ev = eval_by_round.get(i) or {}
        ts = rep.get('captured_at') or rep.get('sent_at_ms')
        if not ts and progress:
            ts = progress.get('started_at')
        lat_ms = rep.get('latency_ms')
        if lat_ms is None:
            lat_ms = latency_by_round.get(i)
        ft_ms = rep.get('first_token_latency_ms')
        if ft_ms is None:
            ft_ms = first_token_by_round.get(i)
        gui_stats = gui_step_avg_by_round.get(i) or {}
        round_eval = round_eval_by_round.get(i) or {}
        text_passed = round_eval.get('text_passed')
        if text_passed is None and ev.get('result'):
            text_passed = ev.get('result') == 'success'
        screenshot_passed = round_eval.get('screenshot_passed')
        round_passed = round_eval.get('passed')
        if round_passed is None and not round_eval:
            round_passed = text_passed
        rows.append({
            'case_id': case_id,
            'round': i,
            'status': case_status,
            'status_text': status_text,
            'fail_reason': fail_reason or None,
            'user_message': chat_rounds.get(i) or rep.get('user_message') or '',
            'clarify_message': clarify_map.get(str(i)) or rep.get('clarify_message') or '',
            'expected': exceptions[i - 1] if i <= len(exceptions) else '',
            'model_answer': rep.get('reply_text') or '',
            'segments': rep.get('segments') or [],
            'is_gui': bool(round_eval.get('is_gui')),
            'text_passed': text_passed,
            'screenshot_passed': screenshot_passed,
            'round_passed': round_passed,
            'result': overall_result if overall_result is not None else ev.get('result'),
            'think': ev.get('think'),
            'accuracy': ev.get('accuracy'),
            'metrics': ev.get('metrics') or [],
            'extra': ev.get('extra') or [],
            'latency_ms': lat_ms,
            'first_token_latency_ms': ft_ms,
            'step_latency_sec': gui_stats.get('avg_step_sec'),
            'avg_device_step_sec': gui_stats.get('avg_device_step_sec'),
            'avg_cloud_step_sec': gui_stats.get('avg_cloud_step_sec'),
            'executed_at': format_report_timestamp(ts),
        })
    # 无轮次数据时仍输出一行，便于预览看到状态（失败/接管等）
    if not rows and (case_status or fail_reason):
        ts = (progress or {}).get('end_time') or (progress or {}).get('started_at')
        rows.append({
            'case_id': case_id,
            'round': None,
            'status': case_status,
            'status_text': status_text,
            'fail_reason': fail_reason or None,
            'user_message': '',
            'clarify_message': '',
            'expected': '',
            'model_answer': '',
            'segments': [],
            'is_gui': False,
            'text_passed': None,
            'screenshot_passed': None,
            'round_passed': None,
            'result': overall_result,
            'think': None,
            'accuracy': None,
            'metrics': [],
            'extra': [],
            'latency_ms': None,
            'first_token_latency_ms': None,
            'step_latency_sec': None,
            'avg_device_step_sec': None,
            'avg_cloud_step_sec': None,
            'executed_at': format_report_timestamp(ts),
        })
    return rows


def build_task_report_rows(task_id):
    """任务内全部用例分步结果行。"""
    all_rows = []
    for row in list_task_case_rows(task_id):
        case_id = row.get('case_id')
        if not case_id:
            continue
        all_rows.extend(build_case_report_rows(task_id, case_id))
    return all_rows


def build_report_xlsx_bytes(rows, sheet_title='结果'):
    """生成结果报告 xlsx。"""
    from openpyxl import Workbook
    from openpyxl.styles import Alignment

    wb = Workbook()
    ws = wb.active
    ws.title = (sheet_title or '结果')[:31]
    headers = [
        '用例编号', '轮次', '状态', '用户提问', '澄清消息', '预期回答',
        '模型回答', '评判结果', '单步时延(秒)', '首Token时延(秒)', '用例执行时间',
    ]
    for col, header in enumerate(headers, 1):
        ws.cell(row=1, column=col, value=header)
    for row_idx, row in enumerate(rows or [], 2):
        ws.cell(row=row_idx, column=1, value=row.get('case_id'))
        ws.cell(row=row_idx, column=2, value=row.get('round'))
        ws.cell(
            row=row_idx,
            column=3,
            value=row.get('status_text')
            or case_status_display_text(row.get('status'), row.get('fail_reason')),
        )
        ws.cell(row=row_idx, column=4, value=row.get('user_message'))
        ws.cell(row=row_idx, column=5, value=row.get('clarify_message'))
        ws.cell(row=row_idx, column=6, value=row.get('expected'))
        ws.cell(row=row_idx, column=7, value=row.get('model_answer'))
        # 评判列只导出最终 success/failed，不拼接分数
        ws.cell(row=row_idx, column=8, value=row.get('result') or '')
        step_sec = row.get('step_latency_sec')
        if step_sec is None:
            # 非 GUI 或无单步数据时不回退整轮时延，保持空
            step_val = ''
        else:
            try:
                step_val = round(float(step_sec), 3)
            except (TypeError, ValueError):
                step_val = ''
        ws.cell(row=row_idx, column=9, value=step_val)
        ft_ms = row.get('first_token_latency_ms')
        ft_sec = ''
        if ft_ms is not None:
            try:
                ft_sec = round(float(ft_ms) / 1000.0, 3)
            except (TypeError, ValueError):
                ft_sec = ''
        ws.cell(row=row_idx, column=10, value=ft_sec)
        ws.cell(row=row_idx, column=11, value=row.get('executed_at'))
        for col in (4, 5, 6, 7):
            ws.cell(row=row_idx, column=col).alignment = Alignment(
                wrap_text=True, vertical='top')
    for col, width in zip('ABCDEFGHIJK', [22, 8, 14, 40, 28, 36, 40, 12, 14, 14, 20]):
        ws.column_dimensions[col].width = width
    buf = io.BytesIO()
    wb.save(buf)
    buf.seek(0)
    return buf


def _run_local_qa_round_llm(eval_rounds, case_id):
    """本仓 llm_eval 文本评判（WebTest 未启用或失败时回退）。"""
    from llm_eval import evaluate_qa_rounds

    executor = ThreadPoolExecutor(max_workers=1)
    try:
        future = executor.submit(evaluate_qa_rounds, eval_rounds)
        return future.result(timeout=VL_EVAL_TIMEOUT_SEC)
    except FuturesTimeoutError:
        logger.error(
            f"文本评判超时 ({VL_EVAL_TIMEOUT_SEC}s): case={case_id}"
        )
        return None
    finally:
        executor.shutdown(wait=False, cancel_futures=True)


def run_qa_round_evaluation(
    task_id, case_id, testcase, replies, result_dir, mode='qa_rounds', persist=True,
):
    """按轮文本评判（优先用于有模型回答的 QA / GUI）。

    persist=False 时只返回 eval_data，不写盘、不更新 case_progress。
    返回 (ok, detail, eval_data)；失败时 eval_data 可能为 None。
    """
    exceptions = extract_case_exceptions(testcase)
    chat_rounds = {r['round']: r['user_message'] for r in extract_case_chat_rounds(testcase)}
    reply_by_round = {r.get('round'): r for r in (replies or []) if r.get('round') is not None}
    usable_ordered = _usable_replies_in_order(replies)
    used_reply_ids = set()

    max_round = max(
        len(exceptions),
        max(chat_rounds.keys()) if chat_rounds else 0,
        0,
    )
    eval_rounds = []
    for i in range(1, max_round + 1):
        expected = exceptions[i - 1] if i <= len(exceptions) else ''
        if not (expected or '').strip():
            continue
        rep = pick_reply_for_eval_round(
            i, reply_by_round, usable_ordered, used_reply_ids,
        )
        eval_rounds.append({
            'round': i,
            'user_message': chat_rounds.get(i) or rep.get('user_message') or '',
            'expected': expected,
            'actual': _case_reply_text(rep) or '',
            'executed_at': (
                rep.get('captured_at') or rep.get('sent_at_ms') or time.time()
            ),
        })

    if not eval_rounds:
        return False, '无带预期结果的轮次，跳过文本评判', None

    logger.info(
        f"开始文本评判: case={case_id} mode={mode} rounds={len(eval_rounds)}"
    )
    eval_result = None
    source = 'model_replies'
    webtest_meta = {}
    try:
        from webtest_eval import (
            WebtestEvalError,
            evaluate_qa_rounds_via_webtest,
            is_webtest_eval_enabled,
            load_webtest_eval_config,
        )
        webtest_on = is_webtest_eval_enabled()
        fallback_local = True
        if webtest_on:
            fallback_local = bool(load_webtest_eval_config().get('fallback_local', True))
    except Exception as e:
        logger.warning('WebTest 评测配置不可用，回退本仓评判: %s', e)
        webtest_on = False
        fallback_local = True

    if webtest_on:
        title = ''
        if isinstance(testcase, dict):
            title = (testcase.get('testName') or testcase.get('testId') or '') or ''
        try:
            logger.info(
                '使用 WebTest 评审: case=%s task=%s rounds=%s',
                case_id, task_id, len(eval_rounds),
            )
            eval_result = evaluate_qa_rounds_via_webtest(
                eval_rounds,
                case_id=case_id,
                title=title or case_id,
                task_name=f'{task_id}-{case_id}',
                testcase=testcase if isinstance(testcase, dict) else None,
            )
            source = 'webtest'
            webtest_meta = {
                'webtest_task_id': eval_result.get('webtest_task_id'),
                'webtest_accuracy': eval_result.get('accuracy'),
                'webtest_scores': eval_result.get('webtest_scores'),
            }
        except WebtestEvalError as e:
            logger.exception('WebTest 评审失败 case=%s: %s', case_id, e)
            if not fallback_local:
                return False, f'WebTest 评审失败: {e}', None
            logger.warning('WebTest 失败，回退本仓 llm_eval: case=%s', case_id)
            eval_result = None

    if eval_result is None:
        eval_result = _run_local_qa_round_llm(eval_rounds, case_id)
        if eval_result is None:
            return False, f'文本评判超时 ({VL_EVAL_TIMEOUT_SEC}s)', None
        source = 'model_replies'

    eval_data = {
        'mode': mode,
        'source': source,
        'exceptions': exceptions,
        'rounds': eval_rounds,
        'result_raw': eval_result.get('raw', ''),
        'result_parsed': eval_result.get('parsed'),
        'timestamp': time.time(),
    }
    if webtest_meta:
        eval_data.update(webtest_meta)
    if eval_result.get('passed') is not None:
        eval_data['passed'] = bool(eval_result.get('passed'))
    text_passed = parse_evaluation_passed(eval_data)
    if text_passed is not None:
        eval_data['passed'] = bool(text_passed)

    if persist:
        os.makedirs(result_dir, exist_ok=True)
        eval_path = os.path.join(result_dir, 'evaluation.json')
        with open(eval_path, 'w', encoding='utf-8') as f:
            json.dump(eval_data, f, indent=2, ensure_ascii=False)
        if text_passed is not None:
            update_case_evaluation_passed(task_id, f'{case_id}.json', text_passed)
        return True, eval_path, eval_data
    return True, 'text_ok', eval_data


def list_gui_poll_images(result_dir):
    """按时间序列出用例目录中的 gui_poll_* 截图（不含 final_result 收尾图）。"""
    if not result_dir or not os.path.isdir(result_dir):
        return []
    images = []
    for fname in os.listdir(result_dir):
        lower = fname.lower()
        # VL / 轮次截图评判只看操控过程帧，收尾图会误挂到末轮
        if not _is_gui_poll_only_filename(fname):
            continue
        if not lower.endswith(('.png', '.jpg', '.jpeg')):
            continue
        fpath = os.path.join(result_dir, fname)
        if not os.path.isfile(fpath):
            continue
        captured_at_ms = _extract_image_timestamp(fname)
        if not captured_at_ms:
            captured_at_ms = int(os.path.getmtime(fpath) * 1000)
        images.append((captured_at_ms, fname, fpath))
    images.sort(key=lambda x: (x[0], x[1]))
    return [p for _, _, p in images]


def _case_replies_fingerprint(task_id, case_id):
    """回复是否还在变：轮次 + 全文 + 分段。"""
    parts = []
    for item in load_case_replies(task_id, case_id) or []:
        segments = item.get('segments') or []
        parts.append('\n'.join([
            str(item.get('round')),
            _case_reply_text(item),
            '\n'.join(str(seg) for seg in segments),
        ]))
    return '\n---\n'.join(parts)


def wait_case_replies_settle(
    task_id, case_id, quiet_sec=4.0, max_wait_sec=30.0,
):
    """等本用例聊天回复补写安静后再评判。

    后到的 AISF 句子会继续写入 replies.json。文字评判只读一次快照，
    必须等全文不再增长后才读取。
    """
    deadline = time.time() + max(1.0, float(max_wait_sec))
    quiet = max(0.5, float(quiet_sec))
    last = None
    last_change = time.time()
    while True:
        snap = _case_replies_fingerprint(task_id, case_id)
        now = time.time()
        if snap != last:
            if last is not None:
                logger.info(
                    'reply settle: case=%s text changed len %s -> %s',
                    case_id or '?', len(last), len(snap),
                )
            last = snap
            last_change = now
        if (now - last_change) >= quiet:
            break
        if now >= deadline:
            logger.warning(
                'reply settle timeout: case=%s len=%s waited=%.1fs',
                case_id or '?', len(snap or ''), float(max_wait_sec),
            )
            break
        time.sleep(0.4)
    return load_case_replies(task_id, case_id)


def wait_gui_poll_images_settle(
    result_dir, quiet_sec=4.0, max_wait_sec=45.0, case_id='',
):
    """等 gui_poll 上传安静一段时间再评判，避免只抽到早期 QA 帧、漏掉播放页。

    手机 drain/上报与后台评判并行时，目录里截图会陆续到齐；过早 VL 会把
    「对话规划中」判失败，而稍后到的优酷播放帧从未送进模型。
    """
    if not result_dir or not os.path.isdir(result_dir):
        return []
    deadline = time.time() + max(1.0, float(max_wait_sec))
    quiet = max(0.5, float(quiet_sec))
    last_count = -1
    last_change = time.time()
    paths = []
    while True:
        paths = list_gui_poll_images(result_dir)
        n = len(paths)
        now = time.time()
        if n != last_count:
            if last_count >= 0:
                logger.info(
                    'gui_poll settle: case=%s count %s -> %s',
                    case_id or '?', last_count, n,
                )
            last_count = n
            last_change = now
        if n > 0 and (now - last_change) >= quiet:
            break
        if now >= deadline:
            logger.warning(
                'gui_poll settle timeout: case=%s count=%s waited=%.1fs',
                case_id or '?', n, float(max_wait_sec),
            )
            break
        time.sleep(0.5)
    return paths


def _image_captured_at_ms(path):
    ts = _extract_image_timestamp(os.path.basename(path))
    if not ts:
        try:
            ts = int(os.path.getmtime(path) * 1000)
        except OSError:
            ts = 0
    return ts


def _group_gui_poll_images_by_round(task_id, case_id, paths):
    """按 qa_round_latency 时间窗把 gui_poll_* 截图分组，返回 [(round, [paths])]。

    无轮次时间窗时返回 [(None, paths)]，调用方按整批评判（兼容老结果）。
    """
    paths = list(paths or [])
    if not task_id or not paths:
        return [(None, paths)]
    items = [
        {'name': os.path.basename(p), 'captured_at': _image_captured_at_ms(p)}
        for p in paths
    ]
    _assign_screenshot_rounds(task_id, case_id, items)
    grouped = {}
    order = []
    for item, path in zip(items, paths):
        rnd = item.get('round')
        if rnd not in grouped:
            grouped[rnd] = []
            order.append(rnd)
        grouped[rnd].append(path)
    if len(order) == 1 and order[0] is None:
        return [(None, paths)]
    return [(rnd, grouped[rnd]) for rnd in order]


def _eval_items_by_round(parsed):
    """把结论条目映射到轮次：优先用条目自带 round，缺失时按顺序回填。"""
    out = {}
    if not isinstance(parsed, list):
        return out
    for item in parsed:
        if not isinstance(item, dict) or item.get('final_result') is not None:
            continue
        rnd = item.get('round')
        if rnd is None:
            continue
        try:
            out[int(rnd)] = item
        except (TypeError, ValueError):
            continue
    if out:
        return out
    idx = 1
    for item in parsed:
        if not isinstance(item, dict) or item.get('final_result') is not None:
            continue
        if item.get('result'):
            out[idx] = item
            idx += 1
    return out


def build_round_evaluations(testcase, replies, text_eval, shot_eval, gui_rounds=None,
                            exceptions=None):
    """汇总每轮评判结论：TEXT 轮只看文字，GUI 轮文字+截图都要有。"""
    if exceptions is None:
        exceptions = extract_case_exceptions(testcase)
    chat_rounds = {r['round']: r['user_message'] for r in extract_case_chat_rounds(testcase)}
    reply_by_round = {
        r.get('round'): r for r in (replies or []) if r.get('round') is not None
    }
    max_round = max(
        len(exceptions),
        max(chat_rounds.keys()) if chat_rounds else 0,
        max(reply_by_round.keys()) if reply_by_round else 0,
        0,
    )
    text_by_round = _eval_items_by_round((text_eval or {}).get('result_parsed'))
    shot_by_round = _eval_items_by_round((shot_eval or {}).get('result_parsed'))
    gui_set = set()
    for rnd in gui_rounds or []:
        try:
            gui_set.add(int(rnd))
        except (TypeError, ValueError):
            continue
    items = []
    for i in range(1, max_round + 1):
        is_gui = i in gui_set
        t_item = text_by_round.get(i) or {}
        s_item = shot_by_round.get(i) or {}
        text_passed = None
        if t_item.get('result'):
            text_passed = t_item.get('result') == 'success'
        elif (text_eval or {}).get('passed') is not None and not text_by_round:
            text_passed = bool((text_eval or {}).get('passed'))
        shot_passed = None
        if is_gui:
            if s_item.get('result'):
                shot_passed = s_item.get('result') == 'success'
            elif (shot_eval or {}).get('passed') is not None:
                shot_passed = bool((shot_eval or {}).get('passed'))
        if is_gui:
            if text_passed is None and shot_passed is None:
                overall = None
            elif text_passed is None:
                overall = shot_passed
            elif shot_passed is None:
                overall = text_passed
            else:
                overall = bool(text_passed) and bool(shot_passed)
        else:
            overall = text_passed
        items.append({
            'round': i,
            'is_gui': is_gui,
            'user_message': (
                chat_rounds.get(i)
                or (reply_by_round.get(i) or {}).get('user_message')
                or ''
            ),
            'expected': exceptions[i - 1] if i <= len(exceptions) else '',
            'text_passed': text_passed,
            'screenshot_passed': shot_passed,
            'passed': overall,
            'text_think': t_item.get('think'),
            'text_accuracy': t_item.get('accuracy'),
            'screenshot_think': s_item.get('think'),
            'screenshot_accuracy': s_item.get('accuracy'),
        })
    return items


def run_gui_poll_vl_evaluation(result_dir, exceptions, case_id='', progress_cb=None,
                               task_id=None):
    """对 gui_poll_* 截图做 VL；有轮次时间窗时按轮分组评判。

    返回 (ok_run, detail, eval_blob)；eval_blob['round_results'] 为每轮结果。
    """
    if progress_cb:
        try:
            progress_cb(
                phase='vl_wait_shots', percent=26,
                message='等待过程截图到齐…',
            )
        except Exception:
            pass
    paths = wait_gui_poll_images_settle(result_dir, case_id=case_id)
    if not paths:
        return False, '无 gui_poll_* 截图', None
    if not exceptions:
        return False, '无 exceptions，跳过 gui_poll VL', None

    from llm_eval import evaluate_gui_poll_images

    groups = _group_gui_poll_images_by_round(task_id, case_id, paths)
    logger.info(
        '开始 gui_poll VL 评判: case=%s images=%s exceptions=%s groups=%s',
        case_id, len(paths), len(exceptions), [g[0] for g in groups],
    )
    if progress_cb:
        try:
            progress_cb(
                phase='vl_start', percent=30,
                message=f'截图评判开始（{len(paths)} 张）',
            )
        except Exception:
            pass

    multi = len(groups) > 1
    round_results = []
    all_parsed = []
    all_raw = []
    all_used = []
    for rnd, round_paths in groups:
        if rnd is None:
            round_exps = list(exceptions)
        elif 1 <= int(rnd) <= len(exceptions):
            round_exps = [exceptions[int(rnd) - 1]]
        else:
            round_exps = []
        if not round_exps or not str(round_exps[0] or '').strip():
            logger.warning('轮次 %s 无对应预期，跳过该轮截图评判: case=%s', rnd, case_id)
            round_results.append({
                'round': rnd,
                'images': [os.path.basename(p) for p in round_paths],
                'images_used': [],
                'result_parsed': [],
                'passed': None,
                'error': '该轮无预期结果',
            })
            continue

        n = len(round_paths)
        timeout_sec = min(600, int(VL_EVAL_TIMEOUT_SEC) + 20 * min(n, 16))
        eval_result = None
        with ThreadPoolExecutor(max_workers=1) as executor:
            future = executor.submit(
                evaluate_gui_poll_images, round_paths, round_exps,
                None if multi else progress_cb,
            )
            try:
                eval_result = future.result(timeout=timeout_sec)
            except FuturesTimeoutError:
                logger.error(
                    'gui_poll VL 超时 (%ss): case=%s round=%s images=%s',
                    timeout_sec, case_id, rnd, n,
                )
                round_results.append({
                    'round': rnd,
                    'images': [os.path.basename(p) for p in round_paths],
                    'images_used': [],
                    'result_parsed': [],
                    'passed': False,
                    'error': f'VL 超时 ({timeout_sec}s)',
                })
                continue
            except Exception as e:
                logger.exception(
                    'gui_poll VL 失败 case=%s round=%s: %s', case_id, rnd, e,
                )
                round_results.append({
                    'round': rnd,
                    'images': [os.path.basename(p) for p in round_paths],
                    'images_used': [],
                    'result_parsed': [],
                    'passed': False,
                    'error': f'VL 失败: {e}',
                })
                continue

        if not eval_result:
            round_results.append({
                'round': rnd,
                'images': [os.path.basename(p) for p in round_paths],
                'images_used': [],
                'result_parsed': [],
                'passed': False,
                'error': 'VL 返回空',
            })
            continue

        parsed = eval_result.get('parsed')
        if isinstance(parsed, list):
            for item in parsed:
                if isinstance(item, dict) and item.get('round') is None and rnd is not None:
                    item['round'] = rnd
            all_parsed.extend(parsed)
        all_raw.append(eval_result.get('raw', ''))
        used = eval_result.get('images') or []
        for name in used:
            if name not in all_used:
                all_used.append(name)
        probe = {'result_parsed': parsed}
        rp = parse_evaluation_passed(probe)
        round_results.append({
            'round': rnd,
            'images': [os.path.basename(p) for p in round_paths],
            'images_used': used,
            'result_raw': eval_result.get('raw', ''),
            'result_parsed': parsed,
            'passed': bool(rp) if rp is not None else None,
        })

    evaluated = [r for r in round_results if r.get('passed') is not None]
    shot_passed = None
    if any(r.get('passed') is False for r in evaluated):
        shot_passed = False
    elif evaluated and all(r.get('passed') for r in evaluated):
        shot_passed = True
    elif any(r.get('error') for r in round_results):
        shot_passed = False

    if not all_parsed and not evaluated and not any(
        r.get('error') and r.get('error') != '该轮无预期结果' for r in round_results
    ):
        return False, 'gui_poll VL 无有效轮次结果', None

    shot_data = {
        'mode': 'gui_poll_vl',
        'source': 'gui_poll_screenshots',
        'exceptions': exceptions,
        'images': [os.path.basename(p) for p in paths],
        'images_used': all_used,
        'image_count_total': len(paths),
        'image_count_used': len(all_used),
        'result_raw': '\n\n'.join([r for r in all_raw if r]),
        'result_parsed': all_parsed,
        'round_results': round_results,
        'timestamp': time.time(),
    }
    if shot_passed is not None:
        shot_data['passed'] = bool(shot_passed)
    return True, f'gui_poll={len(paths)} rounds={len(round_results)}', shot_data


def _persist_combined_evaluation(task_id, case_id, result_dir, eval_data):
    os.makedirs(result_dir, exist_ok=True)
    eval_path = os.path.join(result_dir, 'evaluation.json')
    with open(eval_path, 'w', encoding='utf-8') as f:
        json.dump(eval_data, f, indent=2, ensure_ascii=False)
    eval_passed = parse_evaluation_passed(eval_data)
    if eval_passed is not None:
        update_case_evaluation_passed(task_id, f'{case_id}.json', eval_passed)
    return True, eval_path


def run_case_evaluation(
    task_id, case_id, filename=None, result_dir=None, final_image=None,
    exceptions=None, target_package=None, testcase=None, progress_cb=None,
):
    """执行评判。

    - 无 gui_poll_*：仅文字评判（QA/GUI 同）。
    - 有 gui_poll_*：文字评判 + 全部 gui_poll VL，两者都通过才算通过。
    """
    def _p(phase, percent, message=''):
        if not progress_cb:
            return
        try:
            progress_cb(phase=phase, percent=percent, message=message)
        except Exception:
            pass

    file_name = filename or f'{case_id}.json'
    if not result_dir:
        result_dir = os.path.join(app.config['TESTRESULTS_FOLDER'], task_id, case_id)
    if testcase is None:
        task = load_tasks().get(task_id) or {}
        testset = task.get('testset')
        if testset:
            try:
                testcase = load_adapted_testcase(testset, file_name, target_package)
            except Exception:
                testcase = {}
    case_type = get_case_type(testcase, target_package)
    ensure_case_replies_from_device_logs(task_id, case_id)
    case_exceptions = exceptions if exceptions is not None else extract_case_exceptions(testcase)
    # 先等截图目录安静，再等聊天回复补写停更。文字评判只用停更后的那一版。
    _p('vl_wait_shots', 8, '等待过程截图到齐…')
    gui_polls = wait_gui_poll_images_settle(result_dir, case_id=case_id)
    has_gui_poll = bool(gui_polls)
    _p('reply_settle', 12, '等待聊天回复补写完成…')
    replies = wait_case_replies_settle(task_id, case_id)
    has_text = case_has_usable_model_replies(replies)

    text_mode = 'qa_rounds' if case_type == 'QA' else 'gui_text_replies'

    # 无 gui_poll：只做文字
    if not has_gui_poll:
        if not has_text:
            return False, (
                f'用例 {case_id} 无可用模型回答且无 gui_poll_*，仅文字评判不可用 '
                f'(caseType={case_type}, replies={len(replies or [])})'
            )
        _p('text', 20, '文字评判中')
        ok, detail, text_eval = run_qa_round_evaluation(
            task_id, case_id, testcase, replies, result_dir,
            mode=text_mode, persist=False,
        )
        if ok and text_eval is not None:
            text_eval['gui_rounds'] = []
            text_eval['round_evaluations'] = build_round_evaluations(
                testcase, replies, text_eval, None, gui_rounds=[])
            _persist_combined_evaluation(task_id, case_id, result_dir, text_eval)
            _p('done', 100, '评判完成')
            return True, 'text_ok'
        if ok:
            _p('done', 100, '评判完成')
        return ok, detail

    # 有 gui_poll：文字 + 截图都要做，AND 才通过
    text_eval = None
    text_passed = False
    text_detail = '未做文字评判'
    _p('text', 10, '文字评判中')
    if has_text:
        ok, text_detail, text_eval = run_qa_round_evaluation(
            task_id, case_id, testcase, replies, result_dir,
            mode=text_mode, persist=False,
        )
        if ok and text_eval is not None:
            tp = parse_evaluation_passed(text_eval)
            text_passed = bool(tp) if tp is not None else False
        else:
            text_passed = False
            if not ok:
                text_eval = {
                    'mode': text_mode,
                    'source': 'error',
                    'passed': False,
                    'error': text_detail,
                    'timestamp': time.time(),
                }
    else:
        text_detail = f'无可用模型回答 (replies={len(replies or [])})'
        text_eval = {
            'mode': text_mode,
            'source': 'missing_replies',
            'passed': False,
            'error': text_detail,
            'timestamp': time.time(),
        }
        text_passed = False

    _p('vl_start', 28, '文字评判完成，开始截图评判')
    shot_ok, shot_detail, shot_eval = run_gui_poll_vl_evaluation(
        result_dir, case_exceptions, case_id=case_id, progress_cb=progress_cb,
        task_id=task_id,
    )
    # VL 内会再 settle；用最终目录列表写回，避免只记评判开始时的早期张数
    gui_polls = list_gui_poll_images(result_dir) or gui_polls
    gui_rounds = [
        rnd for rnd, _ in _group_gui_poll_images_by_round(task_id, case_id, gui_polls)
        if rnd is not None
    ]
    screenshot_passed = False
    if shot_ok and shot_eval is not None:
        sp = parse_evaluation_passed(shot_eval)
        screenshot_passed = bool(sp) if sp is not None else False
    elif shot_eval is None:
        shot_eval = {
            'mode': 'gui_poll_vl',
            'source': 'error',
            'passed': False,
            'error': shot_detail,
            'images': [os.path.basename(p) for p in gui_polls],
            'timestamp': time.time(),
        }

    overall = bool(text_passed) and bool(screenshot_passed)
    eval_data = {
        'mode': 'text_and_gui_poll',
        'source': 'text_and_gui_poll',
        'exceptions': case_exceptions,
        'text_passed': bool(text_passed),
        'screenshot_passed': bool(screenshot_passed),
        'passed': overall,
        'text_evaluation': text_eval,
        'screenshot_evaluation': shot_eval,
        'gui_poll_images': [os.path.basename(p) for p in gui_polls],
        'gui_poll_count': len(gui_polls),
        # 兼容结果页：轮次展示仍读顶层 result_parsed / rounds
        'rounds': (text_eval or {}).get('rounds'),
        'result_parsed': (text_eval or {}).get('result_parsed'),
        'result_raw': (text_eval or {}).get('result_raw'),
        'webtest_task_id': (text_eval or {}).get('webtest_task_id'),
        'webtest_accuracy': (text_eval or {}).get('webtest_accuracy'),
        'webtest_scores': (text_eval or {}).get('webtest_scores'),
        'gui_rounds': gui_rounds,
        'round_evaluations': build_round_evaluations(
            testcase, replies, text_eval, shot_eval, gui_rounds=gui_rounds,
            exceptions=case_exceptions,
        ),
        'timestamp': time.time(),
    }
    logger.info(
        '联合评判完成 case=%s text_passed=%s screenshot_passed=%s overall=%s '
        'gui_poll=%s text=%s shot=%s',
        case_id, text_passed, screenshot_passed, overall,
        len(gui_polls), text_detail, shot_detail,
    )
    return _persist_combined_evaluation(task_id, case_id, result_dir, eval_data)


def extract_case_exceptions(testcase):
    """从用例 dict 取出评判条件。兼容历史拼写 expections（缺 a）。"""
    if not isinstance(testcase, dict):
        return []
    raw = testcase.get('exceptions')
    if raw is None or raw == []:
        raw = testcase.get('expections')
    if not raw:
        return []
    if isinstance(raw, str):
        parts = [ln.strip() for ln in raw.replace('\r', '').split('\n') if ln.strip()]
        return parts
    if isinstance(raw, list):
        return [str(x).strip() for x in raw if str(x).strip()]
    return [str(raw).strip()]


def normalize_case_exceptions_fields(testcase):
    """把 expections 归一到 exceptions，避免只写错拼写时后处理漏读。"""
    if not isinstance(testcase, dict):
        return testcase
    items = extract_case_exceptions(testcase)
    if items:
        testcase['exceptions'] = items
    # 保留旧键以免人工对照困惑，但以 exceptions 为准
    return testcase


def load_case_exceptions(task_id, case_id):
    """从用例 JSON 读取 exceptions 评判条件。"""
    if not case_id:
        return []
    task = load_tasks().get(task_id)
    testset = task.get('testset') if task else None
    if not testset:
        return []
    try:
        filepath = resolve_testset_path(testset, f'{case_id}.json')
    except Exception:
        filepath = os.path.join(app.config['UPLOAD_FOLDER'], testset, f'{case_id}.json')
    if not filepath or not os.path.exists(filepath):
        return []
    try:
        with open(filepath, 'r', encoding='utf-8') as f:
            testcase = json.load(f)
        return extract_case_exceptions(testcase)
    except Exception:
        return []


def get_task_case_pass_stats(task, *, allow_disk=True):
    """统计任务通过用例数与全部用例数（通过/总数，不算百分比）。"""
    files = task.get('files') or []
    total = len(files)
    if not total:
        return {'passed': 0, 'total': 0}

    task_id = task.get('id', '')
    case_by_id = {}
    for item in task.get('case_progress') or []:
        case_id = item.get('case_id') or os.path.splitext(item.get('filename', ''))[0]
        if case_id:
            case_by_id[case_id] = item

    passed = 0
    for filename in files:
        case_id = os.path.splitext(filename)[0]
        case_item = case_by_id.get(case_id, {})
        if 'evaluation_passed' in case_item:
            if case_item.get('evaluation_passed'):
                passed += 1
            continue
        if not allow_disk:
            continue
        eval_file = os.path.join(
            app.config['TESTRESULTS_FOLDER'], task_id, case_id, 'evaluation.json'
        )
        if not os.path.exists(eval_file):
            continue
        try:
            with open(eval_file, 'r', encoding='utf-8') as f:
                eval_data = json.load(f)
            if parse_evaluation_passed(eval_data) is True:
                passed += 1
        except Exception:
            pass
    return {'passed': passed, 'total': total}


def apply_task_progress(task):
    """根据 case_progress 汇总任务级进度（仅修改 task 对象，不持久化）。

    progress.completed = 已结束用例数（成功/失败/接管/跳过/取消均计入），
    不是「仅成功」。细分见 progress.by_status。
    """
    cases = task.get('case_progress', [])
    total = len(cases)
    if not total:
        task['progress'] = {
            'completed': 0,
            'total': 0,
            'percent': 0,
            'by_status': {},
            'success': 0,
            'failed': 0,
            'takeover': 0,
        }
        return

    terminal = ('completed', 'failed', 'cancelled', 'skipped', 'takeover')
    by_status = {}
    for case in cases:
        st = case.get('status') or 'pending'
        by_status[st] = by_status.get(st, 0) + 1

    finished = sum(1 for c in cases if c.get('status') in terminal)
    success = by_status.get('completed', 0)
    failed = by_status.get('failed', 0)
    takeover = by_status.get('takeover', 0)

    partial_sum = 0.0
    for case in cases:
        status = case.get('status')
        if status in terminal:
            partial_sum += 1.0
        elif status in ('running', 'finishing', 'analyzing'):
            step_index = case.get('step_index') or 0
            step_total = case.get('step_total') or 0
            if status in ('running', 'finishing') and step_total > 0 and step_index > 0:
                partial_sum += min(0.99, step_index / step_total)
            elif status == 'finishing':
                partial_sum += 0.99
            elif status == 'analyzing':
                partial_sum += 0.98
            else:
                partial_sum += 0.5
    percent = int(partial_sum / total * 100)
    percent = max(0, min(100, percent))
    task['progress'] = {
        'completed': finished,
        'total': total,
        'percent': percent,
        'by_status': by_status,
        'success': success,
        'failed': failed,
        'takeover': takeover,
    }


def sync_task_progress(task_id):
    """根据 case_progress 汇总任务级进度并持久化。"""
    tasks_dir = load_tasks()
    if task_id not in tasks_dir:
        return
    task = tasks_dir[task_id]
    apply_task_progress(task)
    save_task(task)


def update_case_progress(
    task_id, filename, status, case_timeout=None, step_total=None,
    assigned_device=None, fail_reason=None,
):
    """更新单个用例进度并同步任务级进度。

    fail_reason: 失败/取消原因（写入 case_progress，结果页状态列展示）。
    """
    if is_task_cancelled(task_id):
        return
    reason = (fail_reason or '').strip() or None
    with task_io_lock:
        tasks_dir = {}
        tasks_folder = app.config['TASKS_FOLDER']
        path = os.path.join(tasks_folder, f"{task_id}.json")
        if not os.path.exists(path):
            return
        try:
            with open(path, 'r', encoding='utf-8') as fp:
                task = json.load(fp)
        except Exception as e:
            logger.error(f"update_case_progress load failed {task_id}: {e}")
            return
        cases = task.setdefault('case_progress', [])
        case_id = os.path.splitext(os.path.basename(filename))[0]
        found = False
        for item in cases:
            if item.get('filename') == filename or item.get('case_id') == case_id:
                item['status'] = status
                if assigned_device:
                    item['assigned_device'] = assigned_device
                if status == 'running':
                    item['started_at'] = time.time()
                    item.pop('completed_at', None)
                    item.pop('fail_reason', None)
                    if case_timeout:
                        item['timeout_sec'] = case_timeout
                    if step_total is not None:
                        item['step_total'] = step_total
                        item['step_index'] = 0
                elif status == 'analyzing':
                    item['analyzing_started_at'] = time.time()
                    item.pop('completed_at', None)
                elif status in ('completed', 'failed', 'skipped', 'cancelled', 'takeover'):
                    item.pop('step_index', None)
                    item.pop('step_total', None)
                    if status in ('completed', 'failed', 'takeover'):
                        item['completed_at'] = time.time()
                    if status == 'completed':
                        item.pop('fail_reason', None)
                    elif status in ('failed', 'cancelled', 'takeover') and reason:
                        item['fail_reason'] = reason
                    elif status == 'takeover' and not reason:
                        item['fail_reason'] = '人工接管'
                    elif status == 'skipped':
                        item.pop('fail_reason', None)
                found = True
                break
        if not found:
            item = {
                'case_id': case_id,
                'filename': filename,
                'status': status,
            }
            if assigned_device:
                item['assigned_device'] = assigned_device
            if status == 'running':
                item['started_at'] = time.time()
                item.pop('completed_at', None)
                if case_timeout:
                    item['timeout_sec'] = case_timeout
                if step_total is not None:
                    item['step_total'] = step_total
                    item['step_index'] = 0
            elif status == 'analyzing':
                item['analyzing_started_at'] = time.time()
                item.pop('completed_at', None)
            elif status in ('completed', 'failed', 'takeover'):
                item['completed_at'] = time.time()
            if status in ('failed', 'cancelled', 'takeover') and reason:
                item['fail_reason'] = reason
            elif status == 'takeover' and not reason:
                item['fail_reason'] = '人工接管'
            cases.append(item)
        apply_task_progress(task)
        tmp_file = path + '.tmp'
        with open(tmp_file, 'w', encoding='utf-8') as f:
            json.dump(task, f, indent=2, ensure_ascii=False)
            f.flush()
            os.fsync(f.fileno())
        os.replace(tmp_file, path)


def update_case_step_progress(task_id, case_id, step_index, step_total,
                              step_id=None, action=None):
    """更新用例步骤进度（手机 stepIndex/stepTotal 上报）。"""
    if is_task_cancelled(task_id):
        return
    if not task_id or not case_id or step_index <= 0 or step_total <= 0:
        return
    path = os.path.join(app.config['TASKS_FOLDER'], f'{task_id}.json')
    with task_io_lock:
        if not os.path.exists(path):
            return
        try:
            with open(path, 'r', encoding='utf-8') as fp:
                task = json.load(fp)
        except Exception as e:
            logger.error(f"update_case_step_progress load failed {task_id}: {e}")
            return
        cases = task.setdefault('case_progress', [])
        found = False
        for item in cases:
            if item.get('case_id') == case_id or item.get('filename', '').startswith(case_id):
                item['step_index'] = min(step_index, step_total)
                item['step_total'] = step_total
                item['step_updated_at'] = time.time()
                if step_id:
                    item['step_id'] = step_id
                if action:
                    item['step_action'] = action
                if action == 'finishing' or (step_id == 'done' and action != 'finish'):
                    item['status'] = 'finishing'
                    item['finishing_started_at'] = time.time()
                    item.pop('completed_at', None)
                    logger.info(
                        f"用例进入收尾 task={task_id} case={case_id} "
                        f"step={step_index}/{step_total} stepId={step_id or '-'}"
                    )
                elif action == 'finish':
                    started = item.get('finishing_started_at')
                    if started:
                        logger.info(
                            f"用例收尾结束(手机上报 finish) task={task_id} case={case_id} "
                            f"耗时={time.time() - started:.1f}s"
                        )
                    else:
                        logger.info(
                            f"用例收尾结束(手机上报 finish) task={task_id} case={case_id} "
                            f"无 finishing_started_at"
                        )
                elif item.get('status') == 'pending':
                    item['status'] = 'running'
                    if not item.get('started_at'):
                        item['started_at'] = time.time()
                found = True
                break
        if not found:
            case_status = 'finishing' if action == 'finishing' else 'running'
            new_item = {
                'case_id': case_id,
                'filename': f'{case_id}.json',
                'status': case_status,
                'step_index': min(step_index, step_total),
                'step_total': step_total,
                'step_updated_at': time.time(),
                'step_id': step_id or '',
                'step_action': action or '',
                'started_at': time.time(),
            }
            if action == 'finishing':
                new_item['finishing_started_at'] = time.time()
                logger.info(
                    f"用例进入收尾 task={task_id} case={case_id} "
                    f"step={step_index}/{step_total} stepId={step_id or '-'}"
                )
            cases.append(new_item)
        apply_task_progress(task)
        if action == 'finishing' and task.get('status') == 'running':
            task['status'] = 'finishing'
        tmp_file = path + '.tmp'
        with open(tmp_file, 'w', encoding='utf-8') as f:
            json.dump(task, f, indent=2, ensure_ascii=False)
            f.flush()
            os.fsync(f.fileno())
        os.replace(tmp_file, path)


def ensure_case_assigned_device(task_id, case_id, device_id):
    """若进度缺少 assigned_device，用设备上报结果回填（避免步骤更新竞态丢字段）。"""
    did = (device_id or '').strip()
    if not task_id or not case_id or not did:
        return
    path = os.path.join(app.config['TASKS_FOLDER'], f'{task_id}.json')
    with task_io_lock:
        if not os.path.exists(path):
            return
        try:
            with open(path, 'r', encoding='utf-8') as fp:
                task = json.load(fp)
        except Exception:
            return
        cases = task.setdefault('case_progress', [])
        changed = False
        for item in cases:
            if item.get('case_id') == case_id or item.get('filename', '').startswith(case_id):
                if not (item.get('assigned_device') or '').strip():
                    item['assigned_device'] = did
                    changed = True
                if not item.get('started_at'):
                    item['started_at'] = (
                        item.get('finishing_started_at')
                        or item.get('analyzing_started_at')
                        or time.time()
                    )
                    changed = True
                break
        else:
            cases.append({
                'case_id': case_id,
                'filename': f'{case_id}.json',
                'status': 'running',
                'assigned_device': did,
                'started_at': time.time(),
            })
            changed = True
        if not changed:
            return
        tmp_file = path + '.tmp'
        with open(tmp_file, 'w', encoding='utf-8') as f:
            json.dump(task, f, indent=2, ensure_ascii=False)
            f.flush()
            os.fsync(f.fileno())
        os.replace(tmp_file, path)


def update_case_evaluation_passed(task_id, filename, passed):
    """VL 评判完成后记录用例是否通过。"""
    if is_task_cancelled(task_id):
        return
    tasks_dir = load_tasks()
    if task_id not in tasks_dir:
        return
    task = tasks_dir[task_id]
    cases = task.setdefault('case_progress', [])
    case_id = os.path.splitext(os.path.basename(filename))[0]
    for item in cases:
        if item.get('filename') == filename or item.get('case_id') == case_id:
            item['evaluation_passed'] = bool(passed)
            save_task(task)
            return
    cases.append({
        'case_id': case_id,
        'filename': filename,
        'status': 'completed',
        'evaluation_passed': bool(passed),
    })
    save_task(task)


def update_task_status(task_id, status, **extra):
    """更新任务状态并持久化到 tasks/*.json。"""
    if is_task_cancelled(task_id):
        return
    tasks_dir = load_tasks()
    if task_id not in tasks_dir:
        return
    task = tasks_dir[task_id]
    task['status'] = status
    for key, value in extra.items():
        task[key] = value
    save_task(task)


def count_case_completions(task_id, test_id, device_ids):
    """统计指定用例已完成（上报 test_completed）的设备数。"""
    if not device_ids:
        return 0
    device_set = set(device_ids)
    with tasks_lock:
        if task_id not in tasks:
            return 0
        completed_devices = set()
        for result in tasks[task_id].get('results', []):
            if (result.get('test_id') == test_id
                    and result.get('status') == 'completed'
                    and result.get('device_id') in device_set):
                completed_devices.add(result.get('device_id'))
        return len(completed_devices)


def resolve_device_case_outcome(task_id, test_id):
    """根据手机 test_completed 上报，解析终态（takeover / completed）。"""
    with tasks_lock:
        if task_id not in tasks:
            return 'completed'
        for result in reversed(tasks[task_id].get('results') or []):
            if result.get('test_id') != test_id:
                continue
            if result.get('outcome') == 'takeover':
                return 'takeover'
            msg = str(result.get('message') or '')
            if '人工接管' in msg:
                return 'takeover'
            break
    return 'completed'


def wait_for_case_completion(task_id, test_id, device_ids, case_timeout, filename=None):
    """等待所有设备上报 test_completed，收到后立即返回。"""
    if not device_ids:
        return False
    start = time.time()
    while time.time() - start < case_timeout:
        if is_task_cancelled(task_id):
            return False
        if filename and is_case_cancelled(task_id, filename):
            logger.info(f"用例 {test_id} 已取消，停止等待")
            return False
        done = count_case_completions(task_id, test_id, device_ids)
        if done >= len(device_ids):
            logger.info(
                f"用例 {test_id} 全部设备完成 ({done}/{len(device_ids)})，"
                f"等待 {time.time() - start:.1f}s"
            )
            return True
        time.sleep(CASE_COMPLETION_POLL_SEC)
    done = count_case_completions(task_id, test_id, device_ids)
    logger.warning(
        f"用例 {test_id} 等待超时 ({case_timeout}s)，"
        f"已完成 {done}/{len(device_ids)} 台设备"
    )
    return done >= len(device_ids)


def _extract_step_index(filename):
    """从 step_N 标签提取步骤序号（兼容前缀/后缀时间戳命名）。"""
    match = re.search(r'step_(\d+)(?:[_.]|$)', os.path.basename(filename))
    return int(match.group(1)) if match else -1


def _glob_result_images(result_dir, prefix):
    """匹配 step_* / result_final* 的 png/jpg/jpeg（大小写不敏感）。"""
    import glob as glob_module
    if not result_dir or not os.path.isdir(result_dir):
        return []
    images = []
    for ext in ('png', 'jpg', 'jpeg', 'PNG', 'JPG', 'JPEG'):
        images.extend(glob_module.glob(os.path.join(result_dir, f'{prefix}*.{ext}')))
        images.extend(glob_module.glob(os.path.join(result_dir, f'*_{prefix}*.{ext}')))
    # 兼容个别机型落成 result_final.png（无下划线时间戳）
    if prefix.startswith('result_final'):
        for ext in ('png', 'jpg', 'jpeg', 'PNG', 'JPG', 'JPEG'):
            images.extend(glob_module.glob(os.path.join(result_dir, f'result_final.{ext}')))
            images.extend(glob_module.glob(os.path.join(result_dir, f'*_result_final.{ext}')))
    # 去重并只保留文件
    uniq = []
    seen = set()
    for path in images:
        ap = os.path.abspath(path)
        if ap in seen or not os.path.isfile(path):
            continue
        seen.add(ap)
        uniq.append(path)
    return uniq


def _list_result_final_images(result_dir):
    """列出可供 VL 使用的最终截图（优先 result_final_*, 也兼容 result_final.*）。"""
    finals = _glob_result_images(result_dir, 'result_final_')
    if finals:
        return finals
    return _glob_result_images(result_dir, 'result_final')


def _pick_latest_step_image(step_images):
    """按步骤序号选真正的最后一步截图（避免 step_9 > step_68 的字符串排序 bug）。"""
    if not step_images:
        return None
    return max(
        step_images,
        key=lambda path: (_extract_step_index(path), _extract_image_timestamp(path)),
    )


def _pick_latest_image_by_timestamp(image_paths):
    """按文件名时间戳选最新图片。"""
    if not image_paths:
        return None
    return max(image_paths, key=_extract_image_timestamp)


def ensure_result_final_from_step(result_dir, source_step=None):
    """无 result_final 或已有 result_final 早于最后一步时，用最后一步 step 图生成。"""
    import glob as glob_module
    import shutil

    step_images = _glob_result_images(result_dir, 'step_')
    if not step_images and not source_step:
        return None

    latest_step = source_step or _pick_latest_step_image(step_images)
    if not latest_step:
        return None

    final_images = _glob_result_images(result_dir, 'result_final_')
    step_ts = _extract_image_timestamp(latest_step)
    step_no = _extract_step_index(latest_step)
    if final_images:
        best_final = _pick_latest_image_by_timestamp(final_images)
        final_ts = _extract_image_timestamp(best_final)
        # result_final 时间戳不早于最后一步，说明 App 已正确上传
        if final_ts >= step_ts:
            return best_final
        logger.warning(
            f"已有 result_final 早于 step_{step_no}，将用最后一步重新生成: "
            f"{os.path.basename(best_final)}"
        )

    ext = os.path.splitext(latest_step)[1] or '.png'
    dest = os.path.join(result_dir, f'{int(time.time() * 1000)}_result_final{ext}')
    shutil.copy2(latest_step, dest)
    logger.info(
        f"已从最后一步截图(step_{step_no})生成 result_final: "
        f"{dest} <- {os.path.basename(latest_step)}"
    )
    return dest


def prepare_result_final_before_analysis(
    result_dir, expected_step_count=0, timeout_sec=RESULT_FINAL_WAIT_SEC, task_id=None
):
    """
    在后处理分析（日志/时延/VL）之前确保 result_final 已就绪。
    规则：只要目录里已有 result_final_*，立刻返回并开始分析，不做多余等待。
    仅当还没有 final 时，才短等 step 稳定后降级生成一张。
    """
    # 已有 result_final → 马上分析（不再和 step 时间戳较劲、不再空等）
    final_images = _list_result_final_images(result_dir)
    if final_images:
        best_final = _pick_latest_image_by_timestamp(final_images)
        logger.info(f"result_final 已存在，立即进入分析: {best_final}")
        return best_final

    start = time.time()
    last_seen_max_step = -1
    stable_since = None

    while time.time() - start < timeout_sec:
        if task_id and is_task_cancelled(task_id):
            logger.info(f"准备 result_final 已取消: {result_dir}")
            return None

        # 等待期间手机刚上传完成 → 立刻用
        final_images = _list_result_final_images(result_dir)
        if final_images:
            best_final = _pick_latest_image_by_timestamp(final_images)
            logger.info(f"result_final 刚到达，立即进入分析: {best_final}")
            return best_final

        step_images = _glob_result_images(result_dir, 'step_')
        latest_step = _pick_latest_step_image(step_images)
        max_step_idx = _extract_step_index(latest_step) if latest_step else -1

        if max_step_idx >= 0:
            if max_step_idx == last_seen_max_step:
                if stable_since is None:
                    stable_since = time.time()
                elif time.time() - stable_since >= RESULT_FINAL_STEP_STABLE_SEC:
                    ready = (
                        expected_step_count <= 0
                        or max_step_idx >= expected_step_count
                        or (time.time() - start) >= min(8, timeout_sec)
                    )
                    if ready and latest_step:
                        logger.info(
                            f"无 App result_final，用稳定 step_{max_step_idx} 生成后分析"
                        )
                        return ensure_result_final_from_step(
                            result_dir, source_step=latest_step
                        )
            else:
                last_seen_max_step = max_step_idx
                stable_since = None

        time.sleep(0.5)

    logger.warning(
        f"准备 result_final 超时 ({timeout_sec}s)，尝试用当前最后 step 生成: {result_dir}"
    )
    return ensure_result_final_from_step(result_dir)


def wait_for_result_final_image(result_dir, timeout_sec=RESULT_FINAL_WAIT_SEC):
    """兼容旧调用：等同于分析前的 result_final 准备。"""
    return prepare_result_final_before_analysis(result_dir, timeout_sec=timeout_sec)


def run_post_case_prep(task_id, testset, filename, account, target_package=None):
    """用例结束后的快速收尾：result_final + 日志/时延（不含 VL）。

    返回供后台评判使用的上下文 dict；任务已取消时返回 None。
    """
    if is_task_cancelled(task_id):
        return None
    file_name = os.path.basename(filename)
    case_id = os.path.splitext(file_name)[0]
    result_dir = os.path.join(app.config['TESTRESULTS_FOLDER'], task_id, case_id)
    os.makedirs(result_dir, exist_ok=True)

    testcase = None
    exceptions = []
    expected_step_count = 0
    # 优先完整适配；适配失败时仍要能读出 expections/exceptions，不能因此跳过 VL
    try:
        testcase = load_adapted_testcase(testset, file_name, target_package)
        exceptions = extract_case_exceptions(testcase)
        steps = testcase.get('steps', [])
        if isinstance(steps, list):
            expected_step_count = len(steps)
    except Exception as e:
        logger.error(f"读取/适配用例 JSON 失败 {file_name}: {e}")
        exceptions = load_case_exceptions(task_id, case_id) or []
        logger.info(
            f"适配失败后回退读取评判条件: case={case_id} exceptions={len(exceptions)}条"
        )

    if not exceptions:
        exceptions = load_case_exceptions(task_id, case_id) or []

    logger.info(
        f"用例 {case_id} 后处理准备: exceptions={len(exceptions)}条 "
        f"testset={testset} file={file_name}"
    )

    final_image = None
    case_type = get_case_type(testcase, target_package)
    try:
        if case_type == 'QA':
            logger.info(f"用例 {case_id} 后处理: QA 类型，跳过 result_final 准备")
        else:
            logger.info(
                f"用例 {case_id} 后处理: 1/3 准备 result_final"
                f"（预期最后 step_{expected_step_count}）"
            )
            final_image = prepare_result_final_before_analysis(
                result_dir, expected_step_count=expected_step_count, task_id=task_id
            )
            if final_image:
                logger.info(f"result_final 准备完成: {final_image}")
            else:
                logger.warning(f"未能准备 result_final: {result_dir}")
    except Exception as e:
        logger.error(f"准备 result_final 失败: {e}")

    if is_task_cancelled(task_id):
        return None

    try:
        log_path = None
        logger.info(f"用例 {case_id} 后处理: 2/3 日志与时延分析")
        if not ENABLE_CLOUD_LOG_DOWNLOAD:
            logger.info(
                '已跳过云侧日志下载（ENABLE_CLOUD_LOG_DOWNLOAD=false）。'
                '请用结果页 device_ 日志或 Demo logcat（非 ADB 执行路径）。'
            )
        elif not account:
            logger.warning(f"任务 {task_id} 未配置 account，跳过云侧日志下载")
        else:
            from get_log import download_log
            logger.info(f"开始下载云侧日志: account={account}")
            log_path = download_log(
                account=account,
                server_url=LOG_SERVICE_URL,
                file_dir=result_dir,
                timeout_sec=LOG_DOWNLOAD_TIMEOUT_SEC
            )
        if log_path:
            from parse_log import analyze_last_task
            latency_file = os.path.join(result_dir, 'latency.json')
            analyze_last_task(log_path, latency_file)
            logger.info(f"时延分析完成: {latency_file}")
    except Exception as e:
        logger.error(f"Error downloading log: {e}")

    if is_task_cancelled(task_id):
        return None

    return {
        'task_id': task_id,
        'case_id': case_id,
        'file_name': file_name,
        'result_dir': result_dir,
        'final_image': final_image,
        'exceptions': exceptions,
        'target_package': target_package,
        'testcase': testcase,
    }


def _eval_progress_key(task_id, case_id) -> str:
    return f"{task_id}/{case_id}"


def set_case_eval_progress(task_id, case_id, **fields) -> None:
    """更新用例评判进度（供结果页进度条轮询）。"""
    if not task_id or not case_id:
        return
    key = _eval_progress_key(task_id, case_id)
    with case_eval_progress_lock:
        cur = dict(case_eval_progress.get(key) or {})
        if 'started_at' not in cur:
            cur['started_at'] = time.time()
        cur.update(fields)
        cur['updated_at'] = time.time()
        if 'percent' in cur:
            try:
                cur['percent'] = max(0, min(100, int(cur['percent'])))
            except (TypeError, ValueError):
                cur['percent'] = 0
        case_eval_progress[key] = cur


def get_case_eval_progress(task_id, case_id) -> dict | None:
    if not task_id or not case_id:
        return None
    with case_eval_progress_lock:
        cur = case_eval_progress.get(_eval_progress_key(task_id, case_id))
        return dict(cur) if cur else None


def clear_case_eval_progress(task_id, case_id) -> None:
    if not task_id or not case_id:
        return
    with case_eval_progress_lock:
        case_eval_progress.pop(_eval_progress_key(task_id, case_id), None)


def _run_case_evaluation_job(ctx: dict) -> None:
    """后台线程：跑文字 + VL 评判（不阻塞下一例下发；多例并行受信号量限制）。"""
    if not ctx:
        return
    task_id = ctx['task_id']
    case_id = ctx['case_id']
    file_name = ctx['file_name']
    set_case_eval_progress(
        task_id, case_id,
        status='queued', phase='queued', percent=0, message='排队等待评判',
    )
    acquired = case_eval_semaphore.acquire(timeout=1800)
    if not acquired:
        set_case_eval_progress(
            task_id, case_id,
            status='error', phase='timeout', percent=0,
            message='等待评判槽位超时',
        )
        logger.error(f"评判排队超时: {case_id}")
        return
    try:
        logger.info(
            f"用例 {case_id} 后处理: 3/3 结果评判（后台并行，上限={CASE_EVAL_PARALLEL}）"
        )
        set_case_eval_progress(
            task_id, case_id,
            status='running', phase='start', percent=2, message='开始评判',
        )
        final_image = ctx.get('final_image')
        result_dir = ctx['result_dir']
        if not final_image:
            late = _list_result_final_images(result_dir)
            if late:
                final_image = _pick_latest_image_by_timestamp(late)
                logger.info(f"评判前再次发现 result_final: {final_image}")
        if is_task_cancelled(task_id):
            logger.info(f"跳过后台评判（任务已取消）: {case_id}")
            clear_case_eval_progress(task_id, case_id)
            return

        def _progress_cb(**kw):
            set_case_eval_progress(task_id, case_id, status='running', **kw)

        ok, detail = run_case_evaluation(
            task_id,
            case_id,
            filename=file_name,
            result_dir=result_dir,
            final_image=final_image,
            exceptions=ctx.get('exceptions'),
            target_package=ctx.get('target_package'),
            testcase=ctx.get('testcase'),
            progress_cb=_progress_cb,
        )
        if ok:
            logger.info(f"评判完成: {detail}")
            set_case_eval_progress(
                task_id, case_id,
                status='done', phase='done', percent=100, message='评判完成',
            )
        else:
            logger.info(f"评判未完成: {detail}")
            set_case_eval_progress(
                task_id, case_id,
                status='error', phase='error', percent=100,
                message=str(detail or '评判未完成')[:160],
            )
        # 完成后短暂保留进度，下一轮列表会因 has_evaluation 显示通过/未通过
        time.sleep(1.5)
        clear_case_eval_progress(task_id, case_id)
    except Exception as e:
        logger.error(f"结果评判失败 case={case_id}: {e}")
        set_case_eval_progress(
            task_id, case_id,
            status='error', phase='error', percent=100,
            message=str(e)[:160],
        )
        time.sleep(1.5)
        clear_case_eval_progress(task_id, case_id)
    finally:
        case_eval_semaphore.release()


def schedule_case_evaluation(ctx: dict) -> None:
    """异步启动评判；设备可立刻跑下一例。"""
    if not ctx:
        return
    case_id = ctx.get('case_id') or '?'
    task_id = ctx.get('task_id') or ''
    # 已在评判中则不重复入队
    cur = get_case_eval_progress(task_id, case_id)
    if cur and str(cur.get('status') or '').lower() in ('queued', 'running'):
        return
    set_case_eval_progress(
        task_id, case_id,
        status='queued', phase='queued', percent=0, message='已加入评判队列',
    )
    t = threading.Thread(
        target=_run_case_evaluation_job,
        args=(ctx,),
        name=f"eval-{case_id}",
        daemon=True,
    )
    t.start()
    logger.info(
        f"已后台启动评判线程: {case_id} (parallel_limit={CASE_EVAL_PARALLEL})"
    )


_missing_eval_kick_lock = threading.Lock()
_missing_eval_kick_at = {}  # task_id -> monotonic ts


def build_eval_ctx_for_case(task_id, case_id, task=None) -> dict | None:
    """为已完成但尚未评判的用例构造后台评判上下文（轻量，不重下日志）。"""
    if not task_id or not case_id:
        return None
    result_dir = os.path.join(app.config['TESTRESULTS_FOLDER'], task_id, case_id)
    if not os.path.isdir(result_dir):
        return None
    task = task if task is not None else (load_tasks().get(task_id) or {})
    file_name = f'{case_id}.json'
    testcase = {}
    testset = task.get('testset')
    target_package = task.get('target_package')
    if testset:
        try:
            testcase = load_adapted_testcase(testset, file_name, target_package) or {}
        except Exception:
            testcase = {}
    return {
        'task_id': task_id,
        'case_id': case_id,
        'file_name': file_name,
        'result_dir': result_dir,
        'final_image': None,
        'exceptions': extract_case_exceptions(testcase),
        'target_package': target_package,
        'testcase': testcase,
    }


def maybe_schedule_missing_evaluations(task_id, rows=None) -> int:
    """补跑：终态且无 evaluation.json、当前又不在评判中的用例。

    服务重启会丢掉内存中的评判线程；结果页轮询时自动捡回。
    """
    if not task_id:
        return 0
    now = time.monotonic()
    with _missing_eval_kick_lock:
        last = float(_missing_eval_kick_at.get(task_id) or 0)
        if now - last < 12.0:
            return 0
        _missing_eval_kick_at[task_id] = now

    task = load_tasks().get(task_id) or {}
    rows = list(rows) if rows is not None else list_task_case_rows(task_id)
    kicked = 0
    for row in rows:
        case_id = row.get('case_id')
        if not case_id:
            continue
        status = str(row.get('status') or '').lower()
        if status not in TERMINAL_CASE_STATUSES:
            continue
        # 用户停止/跳过的不自动补评判
        if status in ('cancelled', 'skipped'):
            continue
        if row.get('has_evaluation'):
            continue
        if row.get('evaluation_passed') is not None:
            continue
        if get_case_eval_progress(task_id, case_id):
            continue
        ctx = build_eval_ctx_for_case(task_id, case_id, task=task)
        if not ctx:
            continue
        schedule_case_evaluation(ctx)
        kicked += 1
    if kicked:
        logger.info(f"任务 {task_id} 补跑缺失评判: {kicked} 例")
    return kicked


def task_has_eval_activity(rows) -> bool:
    """是否有评判进行中，或终态仍待评判（需前端继续轮询）。"""
    for r in rows or []:
        ep = r.get('eval_progress') or {}
        st = str(ep.get('status') or '').lower()
        if st in ('queued', 'running'):
            return True
        status = str(r.get('status') or '').lower()
        if (
            status in TERMINAL_CASE_STATUSES
            and not r.get('has_evaluation')
            and r.get('evaluation_passed') is None
        ):
            return True
    return False


def run_post_case_analysis(task_id, testset, filename, account, target_package=None):
    """兼容旧调用：同步 prep + 同步评判（测试/工具脚本用）。"""
    ctx = run_post_case_prep(task_id, testset, filename, account, target_package)
    if ctx:
        _run_case_evaluation_job(ctx)


def run_vl_evaluation_for_case(
    task_id, case_id, filename=None, result_dir=None, final_image=None,
    exceptions=None, target_package=None,
):
    """对已有 result_final 执行 VL 并写入 evaluation.json。

    返回 (ok: bool, detail: str)。
    """
    from llm_eval import evaluate as vl_evaluate

    file_name = filename or f'{case_id}.json'
    if not result_dir:
        result_dir = os.path.join(app.config['TESTRESULTS_FOLDER'], task_id, case_id)
    if not os.path.isdir(result_dir):
        return False, f'结果目录不存在: {result_dir}'

    if exceptions is None:
        task = load_tasks().get(task_id) or {}
        testset = task.get('testset')
        exceptions = []
        if testset:
            try:
                tc = load_adapted_testcase(testset, file_name, target_package or task.get('target_package'))
                exceptions = extract_case_exceptions(tc)
            except Exception as e:
                logger.warning(f"读取 exceptions 失败 {file_name}: {e}")
                exceptions = load_case_exceptions(task_id, case_id) or []
        else:
            exceptions = load_case_exceptions(task_id, case_id) or []

    if not exceptions:
        return False, (
            '用例 JSON 无 exceptions/expections 评判条件，跳过 VL'
            '（字段名请用 exceptions；历史拼写 expections 也可）'
        )

    if not final_image:
        finals = _list_result_final_images(result_dir)
        final_image = _pick_latest_image_by_timestamp(finals) if finals else None
    if not final_image:
        names = [
            n for n in os.listdir(result_dir)
            if n.lower().endswith(('.png', '.jpg', '.jpeg'))
        ][:20]
        return False, f'未找到 result_final；目录图片={names}'

    logger.info(f"开始VL评判: image={final_image}, exceptions={len(exceptions)}条")
    eval_result = None
    with ThreadPoolExecutor(max_workers=1) as executor:
        future = executor.submit(vl_evaluate, final_image, exceptions)
        try:
            eval_result = future.result(timeout=VL_EVAL_TIMEOUT_SEC)
        except FuturesTimeoutError:
            logger.error(f"VL评判超时 ({VL_EVAL_TIMEOUT_SEC}s): {final_image}")
            return False, f'VL 调用超时 ({VL_EVAL_TIMEOUT_SEC}s)'

    if not eval_result:
        return False, 'VL 返回空结果'

    eval_data = {
        'mode': 'vl_image',
        'source': 'screenshot',
        'image': os.path.basename(final_image),
        'exceptions': exceptions,
        'result_raw': eval_result.get('raw', ''),
        'result_parsed': eval_result.get('parsed'),
        'timestamp': time.time(),
    }
    eval_path = os.path.join(result_dir, 'evaluation.json')
    with open(eval_path, 'w', encoding='utf-8') as f:
        json.dump(eval_data, f, indent=2, ensure_ascii=False)
    eval_passed = parse_evaluation_passed(eval_data)
    if eval_passed is not None:
        update_case_evaluation_passed(task_id, file_name, eval_passed)
    return True, eval_path


def try_start_scheduled_task(task_id):
    """启动已到期的定时任务（幂等，设备忙则下次轮询重试）。"""
    if is_task_cancelled(task_id):
        return False

    tasks_dir = load_tasks()
    if task_id not in tasks_dir:
        return False

    task = tasks_dir[task_id]
    if task.get('status') != 'pending' or task.get('start_type') != 'scheduled':
        return False

    schedule_ts = parse_schedule_time(task.get('schedule_time'))
    if schedule_ts is None or time.time() < schedule_ts:
        return False

    device_ids = task.get('devices', [])
    target_package = task.get('target_package') or EXCEL_DEFAULT_PACKAGE
    if _is_devicesdk_package(target_package):
        block_msg = devicesdk_screen_capture_problem(device_ids, force=False)
        if block_msg:
            logger.warning('定时任务 %s 未启动: %s', task_id, block_msg.replace('\n', ' | '))
            if task.get('screen_capture_block') != block_msg:
                task['screen_capture_block'] = block_msg
                task['screen_capture_block_at'] = time.time()
                save_task(task)
            return False
        if task.get('screen_capture_block'):
            task.pop('screen_capture_block', None)
            task.pop('screen_capture_block_at', None)
            save_task(task)
    allow_offline = False
    try:
        from task_preflight import should_run_preflight
        allow_offline = should_run_preflight(target_package)
    except Exception:
        allow_offline = False
    available, busy_message = check_devices_available(
        device_ids, exclude_task_id=task_id, require_online=not allow_offline
    )
    if not available:
        logger.warning(f"定时任务 {task_id} 到期但暂不可用: {busy_message}")
        return False

    task['status'] = 'running'
    task['start_time'] = time.time()
    save_task(task)
    threading.Thread(target=execute_task, args=(task_id,), daemon=True).start()
    logger.info(f"Scheduled task started: {task_id}")
    return True


def dispatch_due_scheduled_tasks():
    """检查并启动所有到期的定时任务。"""
    now = time.time()
    for task_id, task in load_tasks().items():
        if task.get('status') != 'pending' or task.get('start_type') != 'scheduled':
            continue
        schedule_ts = parse_schedule_time(task.get('schedule_time'))
        if schedule_ts is None or now < schedule_ts:
            continue
        try_start_scheduled_task(task_id)


def execute_task(task_id, files_to_run=None):
    """Execute a task - send files to devices and wait for completion."""
    if not try_acquire_task_execution(task_id):
        logger.warning(f"Task {task_id} execute skipped: already running")
        return
    try:
        batch = list(files_to_run) if files_to_run is not None else None
        while True:
            _execute_task_body(task_id, batch)
            if is_task_cancelled(task_id):
                clear_task_run_queue(task_id)
                break
            more = pop_all_queued_reruns(task_id)
            if not more:
                break
            logger.info(
                f"Task {task_id}: drain rerun queue ({len(more)} cases) after current batch"
            )
            prepare_cases_for_immediate_rerun(task_id, more)
            batch = more
    finally:
        # 收尾窗口内若仍有入队：再开一轮（持锁外启动会重新 acquire）
        leftover = pop_all_queued_reruns(task_id)
        release_task_execution(task_id)
        if leftover and not is_task_cancelled(task_id):
            logger.info(
                f"Task {task_id}: late-queued {len(leftover)} case(s), spawn follow-up"
            )
            prepare_cases_for_immediate_rerun(task_id, leftover)
            threading.Thread(
                target=execute_task,
                args=(task_id,),
                kwargs={'files_to_run': leftover},
                daemon=True,
            ).start()
        else:
            clear_task_run_queue(task_id)


def _is_devicesdk_package(pkg):
    return pkg == 'com.example.devicesdkdemo' or str(pkg or '').endswith('devicesdkdemo')


def _client_app_from_device_info(info):
    """从已取出的 device info 读 clientApp（不抢锁）。"""
    info = info or {}
    return (
        info.get('clientApp') or info.get('client_app') or ''
    ).strip().lower()


def _list_testapp_ws_online_device_ids():
    """在线 TestApp WS（DeviceSDK 任务中转端）。"""
    # 注意：不可在 devices_lock 内再调 _get_device_client_app（同锁非可重入 → 整站死锁）
    with devices_lock:
        return [
            did for did, info in devices.items()
            if info.get('ws')
            and _client_app_from_device_info(info.get('info')) == 'testapp'
        ]


def _list_devicesdk_ws_online_device_ids():
    """兼容旧名：DeviceSDK 任务执行端现为 TestApp，不再返回 Demo。"""
    return _list_testapp_ws_online_device_ids()


def _normalize_devicesdk_relay_device_ids(device_ids):
    """
    DeviceSDK 任务只经 TestApp WS。
    若选了 Demo 设备 id，尽量映射到同机 __testapp；否则用当前在线 TestApp。
    """
    testapps = _list_testapp_ws_online_device_ids()
    testapp_set = set(testapps)
    if not device_ids:
        return list(testapps[:1])
    out = []
    for did in device_ids:
        did = str(did or '').strip()
        if not did:
            continue
        if did in testapp_set:
            if did not in out:
                out.append(did)
            continue
        sibling = did if did.endswith('__testapp') else f'{did}__testapp'
        if sibling in testapp_set:
            if sibling not in out:
                out.append(sibling)
            continue
        # 指纹前缀相同（device_xxx vs device_xxx__testapp）
        prefix = did.split('__', 1)[0]
        matched = [t for t in testapps if t.split('__', 1)[0] == prefix]
        if matched:
            if matched[0] not in out:
                out.append(matched[0])
            continue
        if len(testapps) == 1 and testapps[0] not in out:
            out.append(testapps[0])
    return out


def _reject_devicesdk_adb_serials(device_ids):
    """DeviceSDK 任务只允许 WS 设备 id（device_ 前缀），拒绝 ADB serial。"""
    bad = [d for d in (device_ids or []) if not str(d).startswith('device_')]
    if not bad:
        return None
    return (
        f'DeviceSDK 任务请选 TestApp WS 在线设备（device_ 开头），'
        f'勿用 ADB serial：{", ".join(bad)}'
    )


def _get_device_client_app(device_id):
    """Return client_app from WS register info, e.g. devicesdkdemo / testapp."""
    with devices_lock:
        info = (devices.get(device_id) or {}).get('info') or {}
    return _client_app_from_device_info(info)


def _is_devicesdk_demo_ws_device(device_id):
    return _get_device_client_app(device_id) == 'devicesdkdemo'


def _is_testapp_ws_device(device_id):
    return _get_device_client_app(device_id) == 'testapp'


def _send_case_to_device(device_id, filename, content, task_id, case_id):
    """向单台设备下发用例文件并启动执行。成功返回 True。"""
    with devices_lock:
        device_ws = devices.get(device_id, {}).get('ws') if device_id in devices else None
    if not device_ws:
        logger.warning(f"设备 {device_id} 不在线，跳过用例 {filename}")
        return False
    is_demo_ws = _is_devicesdk_demo_ws_device(device_id)
    is_testapp_ws = _is_testapp_ws_device(device_id)
    try:
        device_ws.send(json.dumps({
            'type': 'files',
            'files': [{'name': filename, 'content': content}],
        }))
        logger.info(f"Sent {filename} to {device_id}")
        time.sleep(1)
        set_device_current_task(device_id, task_id, case_id)
        execute_payload = {
            'type': 'execute_test',
            'filename': filename,
            'taskId': task_id,
            'caseId': case_id,
        }
        if is_demo_ws:
            # 兼容：若仍有人把 Demo 当执行端
            execute_payload['baseUrl'] = _public_webserver_base_url()
            logger.info(
                f"DeviceSDK Demo WS execute_test {filename} on {device_id} "
                f"baseUrl={execute_payload['baseUrl']}"
            )
        elif is_testapp_ws:
            logger.info(
                f"DeviceSDK via TestApp relay execute_test {filename} on {device_id}"
            )
        device_ws.send(json.dumps(execute_payload))
        logger.info(f"Sent execute_test command to {device_id}")
        return True
    except Exception as e:
        logger.error(f"Error sending to {device_id}: {e}")
        return False


def _finalize_case_after_wait(
    task_id, testset, filename, test_id, account, target_package,
    target_device_ids, case_completed, case_timeout,
):
    """等待结束后的停止/分析/进度收尾。返回是否应继续任务。"""
    if is_task_cancelled(task_id):
        logger.info(f"Task {task_id} 已取消，跳过用例 {filename} 后处理")
        abort_task_execution(task_id)
        return False

    if is_case_cancelled(task_id, filename):
        clear_case_cancelled(task_id, filename)
        update_case_progress(
            task_id, filename, 'cancelled', fail_reason='用户停止用例',
        )
        send_stop_test_to_devices(
            task_id, target_device_ids, reason='用户停止用例',
        )
        return sleep_interruptible(CASE_BATCH_GAP_SEC, task_id)

    timed_out_without_completion = False
    if not case_completed:
        done = count_case_completions(task_id, test_id, target_device_ids)
        timed_out_without_completion = True
        logger.warning(
            f"用例 {test_id} 等待超时 ({case_timeout:.0f}s)，"
            f"未收到全部 test_completed ({done}/{len(target_device_ids)} 台)，"
            f"仍尝试后处理(VL)，并通知手机停止"
        )
        send_stop_test_to_devices(
            task_id, target_device_ids, reason='用例等待超时',
        )
        if not sleep_interruptible(min(8, CASE_BATCH_GAP_SEC + 5), task_id):
            abort_task_execution(task_id)
            return False
    else:
        logger.info(
            f"用例 {test_id} 手机已上报 test_completed，任务进入分析阶段"
        )

    if is_case_cancelled(task_id, filename):
        clear_case_cancelled(task_id, filename)
        update_case_progress(
            task_id, filename, 'cancelled', fail_reason='用户停止用例',
        )
        return sleep_interruptible(CASE_BATCH_GAP_SEC, task_id)

    update_task_status(task_id, 'analyzing')
    update_case_progress(task_id, filename, 'analyzing')
    logger.info(
        f"用例 {test_id} 进入后处理：准备 result_final → 日志/时延；"
        f"VL 评判后台执行（不阻塞下一例）"
        + ("（等待超时后的尽力评判）" if timed_out_without_completion else "")
    )

    if not timed_out_without_completion:
        for device_id in target_device_ids:
            with devices_lock:
                device_ws = devices.get(device_id, {}).get('ws') if device_id in devices else None
            if not device_ws:
                continue
            try:
                device_ws.send(json.dumps({'type': 'delete_file', 'filename': filename}))
                logger.info(f"Sent delete_file {filename} to {device_id}")
            except Exception as e:
                logger.error(f"Error deleting file on {device_id}: {e}")

    # 仅同步做收尾准备；VL 放到后台，避免 2～数分钟评判卡住下一例下发
    eval_ctx = run_post_case_prep(
        task_id, testset, filename, account, target_package
    )

    if is_case_cancelled(task_id, filename):
        clear_case_cancelled(task_id, filename)
        update_case_progress(
            task_id, filename, 'cancelled', fail_reason='用户停止用例',
        )
    elif not is_task_cancelled(task_id):
        if timed_out_without_completion:
            update_case_progress(
                task_id, filename, 'failed',
                fail_reason=(
                    f'用例等待超时（{case_timeout:.0f}s 内未收到手机完成上报）'
                ),
            )
        else:
            outcome = resolve_device_case_outcome(task_id, test_id)
            if outcome == 'takeover':
                update_case_progress(
                    task_id, filename, 'takeover',
                    fail_reason='人工接管',
                )
            else:
                update_case_progress(task_id, filename, 'completed')
        # 设备已释放/可下发下一例；评判结果稍后回填 evaluation.json
        if eval_ctx is not None:
            schedule_case_evaluation(eval_ctx)

    if is_task_cancelled(task_id):
        abort_task_execution(task_id)
        return False
    return sleep_interruptible(CASE_BATCH_GAP_SEC, task_id)


def _run_case_on_devices(
    task_id, testset, filename, device_ids, account, target_package,
    assigned_device=None,
):
    """
    在给定设备集合上执行单个用例（broadcast=多台同案，shard=通常一台）。
    返回 True 表示任务可继续；False 表示任务已中止。
    """
    if is_task_cancelled(task_id):
        abort_task_execution(task_id)
        return False
    if is_case_cancelled(task_id, filename):
        clear_case_cancelled(task_id, filename)
        update_case_progress(
            task_id, filename, 'cancelled', fail_reason='用户停止用例',
        )
        return True

    update_task_status(task_id, 'running')
    filepath = os.path.join(app.config['UPLOAD_FOLDER'], testset, filename)
    if not os.path.exists(filepath):
        logger.error(f"File not found: {filepath}")
        update_case_progress(
            task_id, filename, 'failed', fail_reason='用例文件不存在',
        )
        return True

    try:
        adapted = load_adapted_testcase(testset, filename, target_package)
        content = json.dumps(adapted, ensure_ascii=False, indent=2)
        step_ids = [s.get('stepId') for s in adapted.get('steps', [])]
        wait_new_session_ms = next(
            (
                (s.get('params') or {}).get('duration')
                for s in adapted.get('steps', [])
                if s.get('stepId') == 'wait-new-session'
            ),
            None,
        )
        logger.info(
            f'下发用例 {filename}: 共 {len(step_ids)} 步, '
            f'wait-new-session={wait_new_session_ms}ms, '
            f'click-new-session={"click-new-session" in step_ids}, '
            f'前12步={step_ids[:12]}'
        )
        if (
            target_package == 'com.example.devicesdkdemo'
            and 'click-new-session' not in step_ids
        ):
            logger.error(f'下发 abort: DeviceSDK 用例仍缺少 click-new-session: {filename}')
            update_case_progress(
                task_id, filename, 'failed',
                fail_reason='用例缺少 click-new-session 步骤',
            )
            return True
        if str(testset).startswith(f'{RUNTIME_TESTSET_PREFIX}/'):
            try:
                runtime_path = resolve_testset_path(testset, filename)
                with open(runtime_path, 'w', encoding='utf-8') as rf:
                    json.dump(adapted, rf, ensure_ascii=False, indent=2)
            except Exception as write_err:
                logger.warning(f'回写 runtime 用例失败 {filename}: {write_err}')
    except Exception as e:
        logger.error(f"Adapt testcase failed {filename}: {e}")
        update_case_progress(
            task_id, filename, 'failed',
            fail_reason=f'用例适配失败: {e}',
        )
        return True

    case_timeout = get_case_timeout_sec(testset, filename, target_package)
    step_total = len(adapted.get('steps') or [])
    progress_extra = {}
    if assigned_device:
        progress_extra['assigned_device'] = assigned_device
    test_id = os.path.splitext(filename)[0]
    case_id = test_id

    # 真正开始执行本用例前再删历史结果（截图/评判/回复等）
    try:
        clear_case_results(task_id, [case_id])
        logger.info(f"Cleared prior case results before run: {task_id}/{case_id}")
    except Exception as e:
        logger.warning(f"Clear prior case results failed {task_id}/{case_id}: {e}")

    update_case_progress(
        task_id, filename, 'running',
        case_timeout=case_timeout, step_total=step_total,
        **progress_extra,
    )

    active_devices = []
    for device_id in device_ids:
        if _send_case_to_device(device_id, filename, content, task_id, case_id):
            active_devices.append(device_id)

    if not active_devices:
        update_case_progress(
            task_id, filename, 'failed',
            fail_reason='无在线设备可执行',
        )
        return True

    # DeviceSDK 经 TestApp：截图只保留端侧 gui_poll_*（勿再开服务端 ws_shot，会重复）
    shot_stop = threading.Event()
    shot_thread = None
    use_case_ws_shot = False
    if use_case_ws_shot:
        _, shot_thread = _start_case_ws_shot_loop(
            task_id, case_id, active_devices, shot_stop,
            interval_sec=CASE_WS_SHOT_INTERVAL_SEC,
        )
        logger.info(
            'case WS shot enabled for %s on %s (%.0fs interval, prefer TestApp)',
            case_id, active_devices, CASE_WS_SHOT_INTERVAL_SEC,
        )
    elif _is_devicesdk_package(target_package):
        logger.info(
            'case WS shot skipped for DeviceSDK %s (keep TestApp gui_poll only)',
            case_id,
        )

    try:
        case_completed = wait_for_case_completion(
            task_id, test_id, active_devices, case_timeout,
            filename=filename,
        )
    finally:
        if shot_thread is not None:
            shot_stop.set()
            try:
                shot_thread.join(timeout=12)
            except Exception:
                pass

    return _finalize_case_after_wait(
        task_id, testset, filename, test_id, account, target_package,
        active_devices, case_completed, case_timeout,
    )


def _execute_task_shard(task_id, files, device_ids, testset, account, target_package):
    """多机分片：用例队列，各设备并发领取执行。"""
    work_q = queue.Queue()
    for filename in files:
        work_q.put(filename)

    stop_event = threading.Event()

    def worker(device_id):
        while not stop_event.is_set() and not is_task_cancelled(task_id):
            try:
                filename = work_q.get_nowait()
            except queue.Empty:
                return
            logger.info(f"Shard assign {filename} -> {device_id}")
            ok = _run_case_on_devices(
                task_id, testset, filename, [device_id],
                account, target_package, assigned_device=device_id,
            )
            if not ok:
                stop_event.set()
                # drain remaining so other workers exit quickly
                while True:
                    try:
                        skipped = work_q.get_nowait()
                        update_case_progress(
                            task_id, skipped, 'cancelled',
                            fail_reason='任务中止，未执行',
                        )
                    except queue.Empty:
                        break
                return

    with ThreadPoolExecutor(max_workers=max(1, len(device_ids))) as pool:
        futures = [pool.submit(worker, device_id) for device_id in device_ids]
        for fut in futures:
            try:
                fut.result()
            except Exception as e:
                logger.error(f"Shard worker failed: {e}")


def _devicesdk_adb_meta_for_serial(serial):
    """Lookup ADB visible device meta for a serial."""
    try:
        for d in get_adb_visible_devices() or []:
            if d.get('device_id') == serial:
                return d
    except Exception:
        pass
    # Fallback: assume USB bridge
    bridge_port = int(os.environ.get('ADB_USB_BRIDGE_PORT', '15037') or '15037')
    return {
        'device_id': serial,
        'kind': 'adb_usb_bridge',
        'channel': f'127.0.0.1:{bridge_port}',
        'adb_host': '127.0.0.1',
        'adb_port': bridge_port,
    }


def _task_use_adb_runner(task, device_ids):
    """DeviceSDK 经 TestApp WS 中转，不再使用 ADB runner。"""
    pkg = task.get('target_package') or ''
    if _is_devicesdk_package(pkg):
        return False
    return False


def _public_webserver_base_url():
    """Base URL reachable from phone (TestApp / legacy Demo)."""
    env = (os.environ.get('WEBSERVER_PUBLIC_URL') or '').strip().rstrip('/')
    if env:
        return env
    return 'http://124.71.69.102:9526'


def _execute_task_body(task_id, files_to_run=None):
    """execute_task 实际执行逻辑。"""
    tasks_dir = load_tasks()
    if task_id not in tasks_dir:
        return

    task = tasks_dir[task_id]
    account = task.get('account', '').strip()
    device_ids = list(task.get('devices') or [])
    testset = task['testset']
    files = list(files_to_run) if files_to_run else task['files']
    target_package = task.get('target_package') or EXCEL_DEFAULT_PACKAGE
    concurrency_mode = (task.get('concurrency_mode') or 'broadcast').strip().lower()

    # DeviceSDK：执行端统一映射到 TestApp WS
    if _is_devicesdk_package(target_package):
        remapped = _normalize_devicesdk_relay_device_ids(device_ids)
        if remapped and remapped != device_ids:
            logger.info(
                'Task %s DeviceSDK devices remapped %s -> %s',
                task_id, device_ids, remapped,
            )
            device_ids = remapped
            task['devices'] = list(device_ids)
            try:
                save_task(task)
            except Exception:
                pass

    update_task_status(task_id, 'running')

    # ADB runner 已移除：DeviceSDK 经 TestApp WS（files/execute_test）
    if task.get("execution_mode") == "adb":
        logger.warning("Task %s ignored deprecated execution_mode=adb; use WS", task_id)

    # DeviceSDK QA：启动/重启前检测权限与聊天页；不够则自动准备
    try:
        from task_preflight import run_task_preflight, should_run_preflight
        if should_run_preflight(target_package):
            update_task_status(
                task_id, 'running',
                preflight_phase='checking',
                preflight_message='检测无障碍 / 聊天页 / 设备在线状态…',
            )
            pf = run_task_preflight(task_id, device_ids, target_package, force=False)
            if pf.get('skipped'):
                logger.info(f"Task {task_id} preflight skipped: {pf.get('reason')}")
            elif not pf.get('ok'):
                msg = pf.get('message') or pf.get('error') or '任务前置准备未通过'
                logger.error(f"Task {task_id} preflight failed, abort: {msg}")
                clear_task_run_queue(task_id)
                for filename in files:
                    update_case_progress(
                        task_id, filename, 'failed', fail_reason=msg,
                    )
                update_task_status(
                    task_id, 'completed', end_time=time.time(),
                    preflight_phase='failed',
                    message=msg,
                    preflight_message=msg,
                )
                sync_task_progress(task_id)
                with tasks_lock:
                    if task_id in tasks:
                        del tasks[task_id]
                clear_task_cancelled(task_id)
                clear_all_case_cancelled(task_id)
                return
            else:
                logger.info(
                    f"Task {task_id} preflight ok prepared={pf.get('prepared')}"
                )
    except Exception as e:
        msg = f"前置准备异常: {e}"
        logger.error(f"Task {task_id} preflight error, abort: {e}")
        clear_task_run_queue(task_id)
        for filename in files:
            update_case_progress(
                task_id, filename, 'failed', fail_reason=msg,
            )
        update_task_status(
            task_id, 'completed', end_time=time.time(),
            preflight_phase='failed',
            message=msg,
            preflight_message=msg,
        )
        sync_task_progress(task_id)
        with tasks_lock:
            if task_id in tasks:
                del tasks[task_id]
        clear_task_cancelled(task_id)
        clear_all_case_cancelled(task_id)
        return

    # 下发前必须全部选中设备在线；否则再 wake，仍不齐则失败收尾
    def _online_now():
        with devices_lock:
            return [d for d in device_ids if devices.get(d, {}).get('ws')]

    online_devices = _online_now()
    is_devicesdk_relay_task = _is_devicesdk_package(target_package)
    if len(online_devices) < len(device_ids):
        if is_devicesdk_relay_task:
            # 真机中转：只等/报错，不 SSH 拉实验室模拟器（会把 werkzeug 卡住）
            logger.warning(
                f"Task {task_id}: TestApp WS online {len(online_devices)}/"
                f"{len(device_ids)} (skip lab wake)"
            )
            remapped = _normalize_devicesdk_relay_device_ids(device_ids)
            if remapped:
                device_ids = remapped
                task['devices'] = list(device_ids)
                try:
                    save_task(task)
                except Exception:
                    pass
                online_devices = _online_now()
        else:
            update_task_status(
                task_id, 'running',
                preflight_phase='waking',
                preflight_message='设备未齐，正在拉起 TestApp…',
            )
            logger.warning(
                f"Task {task_id}: online {len(online_devices)}/{len(device_ids)} "
                f"after preflight, waking"
            )
            try:
                from task_preflight import wake_lab_testapps, serials_for_device_ids
                wake_lab_testapps(
                    serials=serials_for_device_ids(device_ids),
                    wait_online_sec=90,
                    min_online=len(device_ids),
                    device_ids=device_ids,
                )
            except Exception as e:
                logger.error(f"Task {task_id} post-preflight wake failed: {e}")
            online_devices = _online_now()

    if len(online_devices) < len(device_ids):
        offline = [d for d in device_ids if d not in online_devices]
        if is_devicesdk_relay_task:
            msg = (
                f"准备失败：TestApp WS 未全部上线 "
                f"{len(online_devices)}/{len(device_ids)}，离线={offline}。"
                f"请打开手机 TestApp 并连接 {_public_webserver_base_url()} "
                f"（DeviceSDK 经 TestApp 中转，无需 Demo 直连）"
            )
        else:
            msg = (
                f"准备失败：TestApp 未全部上线 {len(online_devices)}/{len(device_ids)}，"
                f"离线={offline}"
            )
        logger.error(f"Task {task_id}: {msg}")
        clear_task_run_queue(task_id)
        for filename in files:
            update_case_progress(
                task_id, filename, 'failed', fail_reason=msg,
            )
        update_task_status(
            task_id, 'completed', end_time=time.time(),
            preflight_phase='failed',
            message=msg,
            preflight_message=msg,
        )
        sync_task_progress(task_id)
        with tasks_lock:
            if task_id in tasks:
                del tasks[task_id]
        clear_task_cancelled(task_id)
        clear_all_case_cancelled(task_id)
        return

    update_task_status(
        task_id, 'running',
        preflight_phase='done',
        preflight_message='前置准备完成，开始下发用例',
    )

    with tasks_lock:
        tasks[task_id] = {
            'id': task_id,
            'devices': device_ids,
            'testset': testset,
            'files': task['files'],
            'status': 'running',
            'results': [],
            'concurrency_mode': concurrency_mode,
        }

    if concurrency_mode == 'shard':
        logger.info(
            f"Task {task_id} shard mode: {len(files)} cases across {len(device_ids)} devices"
        )
        _execute_task_shard(task_id, files, device_ids, testset, account, target_package)
    else:
        broadcast_label = ','.join(device_ids) if device_ids else None
        for filename in files:
            ok = _run_case_on_devices(
                task_id, testset, filename, device_ids, account, target_package,
                assigned_device=broadcast_label,
            )
            if not ok:
                clear_task_run_queue(task_id)
                return

    if is_task_cancelled(task_id):
        clear_task_run_queue(task_id)
        abort_task_execution(task_id)
        return

    # 本批结束后若有排队重跑，交给 execute_task 外层循环继续；此处不标 completed
    queued_now = peek_queued_reruns(task_id)
    if queued_now:
        logger.info(
            f"Task {task_id}: batch done, {len(queued_now)} queued; defer complete"
        )
        sync_task_progress(task_id)
        return

    task = load_tasks().get(task_id)
    if task and all_task_cases_terminal(task):
        update_task_status(task_id, 'completed', end_time=time.time())
    elif task:
        update_task_status(task_id, 'completed', end_time=time.time())
    sync_task_progress(task_id)

    with tasks_lock:
        if task_id in tasks:
            del tasks[task_id]
    clear_task_cancelled(task_id)
    clear_all_case_cancelled(task_id)

    logger.info(f"Task {task_id} completed")


@app.route('/api/lab/preflight', methods=['GET', 'POST'])
def api_lab_preflight():
    """任务前置准备：检测 / 准备 TestApp 权限与 DeviceSDK QA 聊天页。

    GET  : 仅检测
    POST : 条件不够则准备；body.force=true 强制准备
           body.mode=wake 时只拉起 TestApp 上线（新建任务用）
    """
    data = request.get_json(silent=True) or {}
    device_ids = data.get('devices') or request.args.getlist('device')
    force = bool(data.get('force')) if request.method == 'POST' else False
    mode = (data.get('mode') or request.args.get('mode') or '').strip().lower()
    ws_only = bool(data.get('ws_only')) if request.method == 'POST' else bool(
        request.args.get('ws_only')
    )
    check_only = bool(data.get('check_only')) if request.method == 'POST' else bool(
        request.args.get('check_only')
    )
    try:
        from task_preflight import check_lab_ready, ensure_lab_ready, wake_lab_testapps
        if request.method == 'GET':
            result = check_lab_ready(device_ids=device_ids or None, ws_only=ws_only)
        elif mode == 'wake':
            wait_sec = float(data.get('wait_online_sec') or 45)
            min_online = int(data.get('min_online') or 1)
            wake_serials = None
            if device_ids:
                from task_preflight import serials_for_device_ids
                wake_serials = serials_for_device_ids(device_ids)
            result = wake_lab_testapps(
                serials=wake_serials,
                wait_online_sec=wait_sec,
                min_online=min_online,
            )
        elif mode in ('ensure_ws', 'check_ws'):
            if mode == 'check_ws':
                result = check_lab_ready(device_ids=device_ids or None, ws_only=True)
            else:
                result = ensure_lab_ready(
                    device_ids=device_ids or None, force=force, ws_only=True,
                )
        else:
            result = ensure_lab_ready(
                device_ids=device_ids or None,
                force=force,
                ws_only=ws_only,
                check_only=check_only,
            )
        # 避免超大 raw_tail 撑爆响应
        if isinstance(result, dict) and result.get('raw_tail'):
            result = dict(result)
            result['raw_tail'] = str(result['raw_tail'])[-500:]
        return jsonify({'success': True, **result})
    except Exception as e:
        logger.exception('lab preflight failed: %s', e)
        return jsonify({'success': False, 'message': str(e) or repr(e)}), 500


@app.route('/api/lab/wake-devices', methods=['POST'])
def api_lab_wake_devices():
    """新建任务前拉起模拟器 TestApp，使其出现在在线设备列表。"""
    data = request.get_json(silent=True) or {}
    try:
        from task_preflight import wake_lab_testapps
        result = wake_lab_testapps(
            wait_online_sec=float(data.get('wait_online_sec') or 90),
            min_online=int(data.get('min_online') or 1),
        )
        return jsonify({'success': bool(result.get('ok')), **result})
    except Exception as e:
        logger.exception('wake-devices failed: %s', e)
        return jsonify({'success': False, 'message': str(e) or repr(e)}), 500


@app.route('/api/tasks/<task_id>/stop', methods=['POST'])
def stop_task(task_id):
    """停止任务执行，并向手机下发 stop_test 指令。"""
    tasks_dir = load_tasks()
    if task_id not in tasks_dir:
        return jsonify({'success': False, 'message': '任务不存在'}), 404

    task = tasks_dir[task_id]
    status = task.get('status')
    if status in ('completed', 'cancelled'):
        return jsonify({'success': False, 'message': '任务已结束，无法停止'}), 400
    if status not in ('pending', 'running', 'finishing', 'analyzing'):
        return jsonify({'success': False, 'message': f'任务状态为 {status}，无法停止'}), 400

    device_ids = task.get('devices', [])
    cancel_task(task_id)
    send_stop_test_to_devices(task_id, device_ids)

    if status == 'pending':
        abort_task_execution(task_id)
    else:
        finalize_task_stop(task_id, device_ids)

    logger.info(f"Task {task_id} stop requested (status={status})")
    return jsonify({'success': True, 'message': '任务已停止'})


@app.route('/api/tasks/<task_id>/restart', methods=['POST'])
def restart_task(task_id):
    """重新执行已结束的任务。"""
    tasks_dir = load_tasks()
    if task_id not in tasks_dir:
        return jsonify({'success': False, 'message': '任务不存在'}), 404

    task = tasks_dir[task_id]
    status = task.get('status')
    # 磁盘已结束但执行锁残留时，允许清锁后重启（停止按钮在已取消态不显示）
    release_stale_task_execution_if_needed(task_id)
    if status in ACTIVE_TASK_STATUSES or is_task_executing(task_id):
        return jsonify({
            'success': False,
            'message': '任务运行中，请先停止后再重启（运行中任务操作列有「停止」）',
        }), 400

    device_ids = task.get('devices', [])
    target_package = task.get('target_package') or EXCEL_DEFAULT_PACKAGE
    is_devicesdk = _is_devicesdk_package(target_package)
    if is_devicesdk:
        device_ids = _normalize_devicesdk_relay_device_ids(device_ids)
        task['devices'] = list(device_ids)
        serial_err = _reject_devicesdk_adb_serials(device_ids)
        if serial_err:
            return jsonify({'success': False, 'message': serial_err}), 400
        if not device_ids:
            return jsonify({
                'success': False,
                'message': '没有在线的 TestApp，无法重启 DeviceSDK 任务',
            }), 400
    allow_offline = False
    try:
        from task_preflight import should_run_preflight
        allow_offline = should_run_preflight(target_package) and not is_devicesdk
    except Exception:
        allow_offline = False
    available, busy_message = check_devices_available(
        device_ids,
        exclude_task_id=task_id,
        require_online=not allow_offline,
        allow_adb=False,
    )
    if not available:
        return jsonify({'success': False, 'message': busy_message}), 400

    if is_devicesdk:
        blocked = _screen_capture_block_response(device_ids)
        if blocked:
            return blocked

    # 重启前先做 DeviceSDK 前置检测；不够则准备（真正执行仍在 execute_task 线程内）
    try:
        from task_preflight import should_run_preflight, check_lab_ready, _is_ws_only_testset
        if should_run_preflight(target_package):
            ws_only = _is_ws_only_testset(task.get("testset") or "")
            checked = check_lab_ready(device_ids=device_ids, ws_only=ws_only)
            task['preflight_check'] = {
                'ok': bool(checked.get('ok')),
                'at': time.time(),
                'message': checked.get('message') or '',
                'will_prepare': not bool(checked.get('ok')),
            }
            save_task(task)
            if not checked.get('ok'):
                logger.info(
                    f"Task {task_id} restart: preflight not ready, "
                    f"will prepare in execute thread"
                )
    except Exception as e:
        logger.warning(f"Task {task_id} restart preflight check failed: {e}")

    testset = task.get('testset', '')
    existing_files = [f for f in (task.get('files') or []) if f]
    if existing_files:
        test_files = existing_files
    elif task.get('source_type') == 'excel' and task.get('excel_dataset'):
        excel_config = task.get('excel_config')
        if not excel_config:
            excel_config, config_error = _excel_import_config_from_json(
                {}, task.get('target_package')
            )
            if config_error:
                return jsonify({'success': False, 'message': config_error}), 400
        runtime_testset, convert_result = convert_excel_dataset_to_runtime(
            task_id, task['excel_dataset'], excel_config
        )
        if not runtime_testset:
            return jsonify({'success': False, 'message': convert_result}), 400
        testset = runtime_testset
        test_files = convert_result
        task['testset'] = testset
    else:
        test_files = list_testset_json_files(testset)
        if not test_files:
            scan_testfiles()
            with testsets_lock:
                if testset not in testsets:
                    return jsonify({'success': False, 'message': '用例集不存在'}), 400
                test_files = [f['name'] for f in testsets[testset]['files']]

    if not test_files:
        return jsonify({'success': False, 'message': '用例集没有文件'}), 400

    # 重启按任务内用例列表顺序依次执行（不重新扫描打乱顺序）。
    # 不在这里清整任务结果：每个用例真正开跑时再 clear_case_results。
    clear_task_run_queue(task_id)
    clear_task_cancelled(task_id)
    task['files'] = test_files
    task['status'] = 'running'
    task['start_time'] = time.time()
    task.pop('end_time', None)
    task['results'] = []
    task['case_progress'] = init_case_progress(test_files)
    apply_task_progress(task)
    save_task(task)

    with tasks_lock:
        tasks[task_id] = {
            'id': task_id,
            'devices': task.get('devices', []),
            'testset': testset,
            'files': test_files,
            'status': 'running',
            'results': [],
        }

    # 先占执行锁，再开线程直接跑 body，避免连点两次都返回成功但第二次被 skip
    if not try_acquire_task_execution(task_id):
        task['status'] = status  # 恢复重启前状态
        if status not in ACTIVE_TASK_STATUSES:
            # 保持已结束态
            pass
        save_task(task)
        with tasks_lock:
            tasks.pop(task_id, None)
        return jsonify({
            'success': False,
            'message': '任务执行线程仍在运行，请先停止后再重启',
        }), 400

    def _restart_runner():
        try:
            _execute_task_body(task_id)
        finally:
            release_task_execution(task_id)

    threading.Thread(target=_restart_runner, daemon=True).start()
    logger.info(f"Task {task_id} restarted")
    return jsonify({'success': True, 'message': '任务已重启，正在前置准备/执行'})

@app.route('/api/tasks/<task_id>/cases/run', methods=['POST'])
def run_task_cases(task_id):
    """重新执行选定的用例（不影响未选用例的结果）。"""
    data = request.get_json() or {}
    case_ids = data.get('case_ids') or []
    if not case_ids:
        return jsonify({'success': False, 'message': '请选择用例'}), 400

    tasks_dir = load_tasks()
    if task_id not in tasks_dir:
        return jsonify({'success': False, 'message': '任务不存在'}), 404

    release_stale_task_execution_if_needed(task_id)
    task = tasks_dir[task_id]

    filenames = case_ids_to_filenames(task, case_ids)
    if not filenames:
        return jsonify({'success': False, 'message': '未找到有效用例'}), 400

    if _is_devicesdk_package(task.get('target_package')):
        blocked = _screen_capture_block_response(task.get('devices') or [])
        if blocked:
            return blocked

    running = get_running_case_in_task(task)
    if running and not is_task_executing(task_id):
        # 进度脏数据：磁盘显示 running 但已无执行线程
        logger.warning(
            f"Task {task_id} case progress shows running but not executing; allowing rerun"
        )

    ok, message = start_case_execution(task_id, filenames)
    if not ok:
        return jsonify({'success': False, 'message': message}), 400
    return jsonify({'success': True, 'message': message})


@app.route('/api/tasks/<task_id>/cases/<case_id>/run', methods=['POST'])
def run_single_task_case(task_id, case_id):
    """执行单个用例。"""
    tasks_dir = load_tasks()
    if task_id not in tasks_dir:
        return jsonify({'success': False, 'message': '任务不存在'}), 404

    release_stale_task_execution_if_needed(task_id)
    task = tasks_dir[task_id]
    filenames = case_ids_to_filenames(task, [case_id])
    if not filenames:
        return jsonify({'success': False, 'message': '用例不存在'}), 404

    if _is_devicesdk_package(task.get('target_package')):
        blocked = _screen_capture_block_response(task.get('devices') or [])
        if blocked:
            return blocked

    ok, message = start_case_execution(task_id, filenames)
    if not ok:
        return jsonify({'success': False, 'message': message}), 400
    return jsonify({'success': True, 'message': message})


@app.route('/api/tasks/<task_id>/cases/<case_id>/stop', methods=['POST'])
def stop_task_case(task_id, case_id):
    """停止正在执行的单个用例（不停止整个任务）。"""
    tasks_dir = load_tasks()
    if task_id not in tasks_dir:
        return jsonify({'success': False, 'message': '任务不存在'}), 404

    task = tasks_dir[task_id]
    filenames = case_ids_to_filenames(task, [case_id])
    if not filenames:
        return jsonify({'success': False, 'message': '用例不存在'}), 404
    filename = filenames[0]

    progress_status = None
    for item in task.get('case_progress') or []:
        if item.get('case_id') == case_id or item.get('filename') == filename:
            progress_status = item.get('status')
            break

    if progress_status == 'queued':
        remove_from_task_rerun_queue(task_id, [filename])
        update_case_progress(
            task_id, filename, 'cancelled', fail_reason='用户取消排队',
        )
        logger.info(f"Case {case_id} in task {task_id} removed from rerun queue")
        return jsonify({'success': True, 'message': '已取消排队'})

    if progress_status not in ('running', 'finishing', 'analyzing'):
        return jsonify({'success': False, 'message': '用例未在执行中'}), 400

    running = get_running_case_in_task(task)
    is_current = bool(
        running and (
            running.get('case_id') == case_id
            or running.get('filename') == filename
        )
    )

    cancel_case(task_id, filename)
    # 当前用例：必须下发 stop_test（含 current_task 设备），避免只改进度手机还在跑
    send_stop_test_to_devices(
        task_id, task.get('devices', []), reason='用户停止用例',
    )
    try:
        path = os.path.join(app.config['TASKS_FOLDER'], f"{task_id}.json")
        with task_io_lock:
            with open(path, 'r', encoding='utf-8') as fp:
                disk_task = json.load(fp)
            for item in disk_task.get('case_progress') or []:
                if item.get('filename') == filename or item.get('case_id') == case_id:
                    item['status'] = 'cancelled'
                    item['fail_reason'] = '用户停止用例'
                    break
            apply_task_progress(disk_task)
            tmp = path + '.tmp'
            with open(tmp, 'w', encoding='utf-8') as f:
                json.dump(disk_task, f, indent=2, ensure_ascii=False)
                f.flush()
                os.fsync(f.fileno())
            os.replace(tmp, path)
    except Exception as e:
        logger.warning(f"stop_task_case persist failed: {e}")
        if not is_task_cancelled(task_id):
            update_case_progress(
                task_id, filename, 'cancelled', fail_reason='用户停止用例',
            )
    logger.info(f"Case {case_id} in task {task_id} stop requested current={is_current}")
    return jsonify({'success': True, 'message': '用例已停止'})

def _delete_idle_task(task_id):
    """删除已停止任务。运行中返回错误，不存在视为已删除。"""
    tasks_dir = load_tasks()
    if task_id not in tasks_dir:
        return False, '任务不存在', 404
    status = tasks_dir[task_id].get('status')
    if status in ('pending', 'running', 'finishing', 'analyzing'):
        return False, '任务运行中，请先停止后再删除', 400
    with tasks_lock:
        if task_id in tasks:
            del tasks[task_id]
    delete_task_data(task_id)
    return True, f'Task {task_id} deleted', 200


@app.route('/api/tasks/batch-delete', methods=['POST'])
def batch_delete_tasks():
    """批量删除已停止的任务。"""
    data = request.get_json(silent=True) or {}
    task_ids = data.get('task_ids') or data.get('taskIds') or []
    if isinstance(task_ids, str):
        task_ids = [task_ids]
    ids = []
    seen = set()
    for item in task_ids:
        tid = str(item or '').strip()
        if tid and tid not in seen:
            seen.add(tid)
            ids.append(tid)
    if not ids:
        return jsonify({'success': False, 'message': '请选择要删除的任务'}), 400
    deleted = []
    skipped = []
    for tid in ids:
        ok, msg, _status = _delete_idle_task(tid)
        if ok:
            deleted.append(tid)
        else:
            skipped.append({'task_id': tid, 'message': msg})
    success = bool(deleted) and not skipped
    if deleted and skipped:
        message = f'已删除 {len(deleted)} 个，{len(skipped)} 个未删除（可能仍在运行）'
    elif deleted:
        message = f'已删除 {len(deleted)} 个任务'
    else:
        message = skipped[0]['message'] if skipped else '没有可删除的任务'
        success = False
    return jsonify({
        'success': success or bool(deleted),
        'message': message,
        'deleted': deleted,
        'skipped': skipped,
    }), (200 if deleted else 400)


@app.route('/api/tasks/<task_id>', methods=['DELETE'])
def delete_task(task_id):
    """Delete a task and its results."""
    ok, msg, status = _delete_idle_task(task_id)
    if not ok:
        return jsonify({'success': False, 'message': msg}), status
    return jsonify({'success': True, 'message': msg})

# ========== API: Test Results ==========

@app.route('/api/results')
def get_all_results():
    """获取所有任务 ID 列表（第一层为任务，下层为用例）。"""
    all_results = []
    for task_id in list_task_ids():
        cases = scan_task_cases(task_id)
        screenshot_count = sum(c['screenshot_count'] for c in cases)
        log_count = sum(c['log_count'] for c in cases)
        all_results.append({
            'task_id': task_id,
            'case_count': len(cases),
            'screenshot_count': screenshot_count,
            'log_count': log_count,
            'has_latency': any(c['has_latency'] for c in cases),
            'has_evaluation': any(c['has_evaluation'] for c in cases)
        })

    return jsonify({'success': True, 'results': all_results})

@app.route('/api/results/<task_id>/cases/<case_id>/live-log')
def get_case_live_log(task_id, case_id):
    """按字节偏移增量读取设备实时日志 device_live.log。"""
    case_path = os.path.join(app.config['TESTRESULTS_FOLDER'], task_id, case_id)
    log_path = os.path.join(case_path, 'device_live.log')
    try:
        offset = max(0, int(request.args.get('offset', 0)))
    except (TypeError, ValueError):
        offset = 0

    if not os.path.isfile(log_path):
        return jsonify({
            'success': True,
            'content': '',
            'offset': 0,
            'size': 0,
            'exists': False,
        })

    size = os.path.getsize(log_path)
    if offset > size:
        offset = 0
    try:
        with open(log_path, 'rb') as f:
            f.seek(offset)
            raw = f.read(64 * 1024)
            new_offset = f.tell()
        content = raw.decode('utf-8', errors='replace')
        return jsonify({
            'success': True,
            'content': content,
            'offset': new_offset,
            'size': size,
            'exists': True,
        })
    except Exception as e:
        return jsonify({'success': False, 'message': str(e)}), 500


@app.route('/api/results/<task_id>/logs')
def get_task_logs(task_id):
    """Get logs for a specific task (optionally filtered by case_id)."""
    task_path = os.path.join(app.config['TESTRESULTS_FOLDER'], task_id)
    if not os.path.exists(task_path):
        return jsonify({'success': False, 'message': 'Task not found'}), 404

    case_id = request.args.get('case_id')
    logs = []

    def add_log_file(log_name, rel_path):
        log_path = os.path.join(task_path, rel_path) if rel_path else os.path.join(task_path, log_name)
        if os.path.isfile(log_path):
            url_path = f'/api/results/{task_id}/logs/{rel_path or log_name}'
            logs.append({
                'name': rel_path or log_name,
                'size': os.path.getsize(log_path),
                'url': build_ui_path(url_path)
            })

    if case_id:
        case_path = os.path.join(task_path, case_id)
        if os.path.isdir(case_path):
            for f in os.listdir(case_path):
                if f.endswith('.log'):
                    add_log_file(f, f'{case_id}/{f}')
    else:
        for item in os.listdir(task_path):
            item_path = os.path.join(task_path, item)
            if os.path.isfile(item_path) and item.endswith('.log'):
                add_log_file(item, item)
            elif os.path.isdir(item_path):
                for f in os.listdir(item_path):
                    if f.endswith('.log'):
                        add_log_file(f, f'{item}/{f}')

    return jsonify({'success': True, 'logs': logs})

@app.route('/api/results/<task_id>/log-file/<path:filepath>')
def download_task_log_file(task_id, filepath):
    """下载用例目录下的原始 .log 文件。"""
    if not filepath.endswith('.log'):
        return jsonify({'success': False, 'message': '仅支持下载 .log 文件'}), 400
    task_dir = os.path.join(app.config['TESTRESULTS_FOLDER'], task_id)
    log_path = safe_join(task_dir, filepath)
    if log_path is None or not os.path.isfile(log_path):
        return jsonify({'success': False, 'message': '日志文件未找到'}), 404
    return send_file(
        log_path,
        as_attachment=True,
        download_name=os.path.basename(log_path)
    )

@app.route('/api/results/<task_id>/logs/<path:filename>')
def get_log_content(task_id, filename):
    """Get log file content with pagination."""
    task_dir = os.path.join(app.config['TESTRESULTS_FOLDER'], task_id)
    log_path = safe_join(task_dir, filename)
    if log_path is None:
        return jsonify({'success': False, 'message': 'Invalid log path'}), 400
    if not os.path.exists(log_path):
        return jsonify({'success': False, 'message': 'Log file not found'}), 404

    try:
        offset = int(request.args.get('offset', 0))
        limit = int(request.args.get('limit', 1000))  # 默认限制为1000行

        with open(log_path, 'r', encoding='utf-8') as f:
            # 读取文件内容
            lines = f.readlines()

            # 计算要返回的内容
            start = offset
            end = offset + limit
            content = ''.join(lines[start:end])

            # 检查是否还有更多内容
            has_more = end < len(lines)

        return jsonify({
            'success': True,
            'content': content,
            'hasMore': has_more,
            'totalLines': len(lines)
        })
    except Exception as e:
        return jsonify({'success': False, 'message': f'Error reading log file: {e}'}), 500

@app.get('/test')
def test():
    "a api test method"
    return jsonify(
        {
            'success':True,
            'message':'test'
        }
    )

TASK_STATUS_LABELS = {
    'pending': '等待中',
    'running': '执行中',
    'finishing': '收尾中',
    'analyzing': '分析中',
    'completed': '已完成',
    'cancelled': '已取消',
}


def diagnose_latency_missing_reason(task_id, case_id):
    """推断时延分析无数据的原因。"""
    if not case_id:
        return '请在左侧选择具体用例'

    task = load_tasks().get(task_id)
    status = task.get('status') if task else None
    if status in ('pending', 'running', 'finishing', 'analyzing'):
        label = TASK_STATUS_LABELS.get(status, status)
        return f'任务当前状态为「{label}」，时延分析尚未完成，请稍后刷新'

    account = (task.get('account') or '').strip() if task else ''
    if not account:
        return '任务未配置 Account，无法从日志服务下载云侧日志'

    result_dir = os.path.join(app.config['TESTRESULTS_FOLDER'], task_id, case_id)
    log_files = []
    if os.path.isdir(result_dir):
        log_files = [f for f in os.listdir(result_dir) if f.endswith('.log')]

    latency_file = os.path.join(result_dir, 'latency.json')
    if not log_files:
        return (
            '未找到云侧日志文件（.log）。请确认创建任务时 Account 正确，'
            f'且日志服务 {LOG_SERVICE_URL} 可从本机访问；也可点击「下载日志」确认是否已有文件'
        )

    if not os.path.exists(latency_file):
        return (
            f'已下载日志（{", ".join(log_files)}），但未生成 latency.json。'
            '可能日志中无有效 TaskIdr/CmdType，或解析失败'
        )

    try:
        with open(latency_file, 'r', encoding='utf-8') as f:
            latency_data = json.load(f)
        if not latency_data.get('steps'):
            cloud_task = latency_data.get('task_id') or '无'
            return (
                f'latency.json 已生成但 steps 为空（云侧任务 ID: {cloud_task}）。'
                '日志可能缺少 CmdType 1005（上传截图）记录，'
                '或拉取日志时云侧 GUI 任务尚未执行完成'
            )
    except Exception as e:
        return f'latency.json 读取失败: {e}'

    return '暂无时延分析数据'


def diagnose_evaluation_missing_reason(task_id, case_id):
    """推断 VL 评判无数据的原因（以目录实况为准，避免「页面有图却提示没有」）。"""
    if not case_id:
        return '请在左侧选择具体用例'

    task = load_tasks().get(task_id)
    status = task.get('status') if task else None
    result_dir = os.path.join(app.config['TESTRESULTS_FOLDER'], task_id, case_id)
    final_images = _list_result_final_images(result_dir) if os.path.isdir(result_dir) else []
    final_names = [os.path.basename(p) for p in final_images[:5]]

    if status in ('pending', 'running', 'finishing', 'analyzing'):
        label = TASK_STATUS_LABELS.get(status, status)
        extra = (
            f'（目录里已有 {final_names[0]}，等待分析跑完）'
            if final_names else ''
        )
        return f'任务当前状态为「{label}」，VL 评判尚未完成，请稍后刷新{extra}'

    testset = task.get('testset') if task else None
    testcase_path = None
    exceptions = load_case_exceptions(task_id, case_id)
    if testset:
        testcase_path = os.path.join(app.config['UPLOAD_FOLDER'], testset, f'{case_id}.json')

    if not testcase_path or not os.path.exists(testcase_path):
        return f'未找到用例 JSON（{case_id}.json），无法检查 exceptions 配置'

    if not exceptions:
        if final_names:
            return (
                f'截图目录已有最终图 {final_names[0]}，但用例 JSON 未配置 exceptions，'
                '系统不会调用 VL 评判（与有没有 result_final 无关）'
            )
        return '用例 JSON 未配置 exceptions 字段，系统已跳过 VL 评判'

    eval_file = os.path.join(result_dir, 'evaluation.json')
    if os.path.exists(eval_file):
        try:
            with open(eval_file, 'r', encoding='utf-8') as f:
                eval_data = json.load(f)
            if not eval_data.get('result_parsed'):
                return (
                    'evaluation.json 已存在但解析结果为空，'
                    '可能 VL 模型返回格式异常、调用超时，或 configs/model.yaml 中 vl_eval 未配置'
                    + (f'；目录中的最终图: {", ".join(final_names)}' if final_names else '')
                )
        except Exception as e:
            return f'evaluation.json 读取失败: {e}'

    if not final_images:
        step_n = len(_glob_result_images(result_dir, 'step_')) if os.path.isdir(result_dir) else 0
        return (
            '未找到 VL 所需图片 result_final_* '
            f'（当前目录 step 图约 {step_n} 张）。'
            '若页面截图列表里能看到类似名字，请确认选中的是同一用例目录。'
        )

    # 有图、有 exceptions、无有效评判 → 多半是分析当时图还没到，后来才上传
    if status == 'completed':
        return (
            f'目录里已有最终图（{final_names[0]}），但当时分析未写出有效评判结果。'
            '常见原因：分析启动时图尚未上传，或 VL 调用失败/超时。请查看 webserver.log，'
            '或重新执行该用例。'
        )

    return (
        f'最终图已就绪（{final_names[0]}），VL 评判结果尚未生成，请稍后刷新'
    )


@app.route('/api/results/<task_id>/latency')
def get_task_latency(task_id):
    """获取指定任务的时延解析数据。"""
    task_path = os.path.join(app.config['TESTRESULTS_FOLDER'], task_id)
    case_id = request.args.get('case_id')
    if case_id:
        latency_file = os.path.join(task_path, case_id, 'latency.json')
    else:
        latency_file = os.path.join(task_path, 'latency.json')

    latency_data = None
    if os.path.exists(latency_file):
        try:
            with open(latency_file, 'r', encoding='utf-8') as f:
                latency_data = json.load(f)
        except Exception as e:
            return jsonify({'success': False, 'message': f'Error reading latency file: {e}'}), 500

    if latency_data and latency_data.get('steps'):
        return jsonify({'success': True, 'latency': latency_data})

    reason = diagnose_latency_missing_reason(task_id, case_id)
    return jsonify({'success': True, 'latency': latency_data, 'reason': reason})

@app.route('/api/results/<task_id>')
def get_task_results(task_id):
    """获取指定任务下的用例列表（第二层为用例 ID）。"""
    task_path = os.path.join(app.config['TESTRESULTS_FOLDER'], task_id)
    if not os.path.exists(task_path):
        return jsonify({'success': False, 'message': '任务未找到'}), 404

    cases = scan_task_cases(task_id)
    result = {
        'task_id': task_id,
        'cases': cases,
        'case_count': len(cases),
        'screenshot_count': sum(c['screenshot_count'] for c in cases)
    }

    return jsonify({'success': True, 'result': result})


@app.route('/api/results/<task_id>/cases')
def get_task_cases_table(task_id):
    """分页获取任务用例结果表（筛选、检索）。"""
    task_path = os.path.join(app.config['TESTRESULTS_FOLDER'], task_id)
    task_meta = load_tasks().get(task_id)
    if not os.path.exists(task_path) and not task_meta:
        return jsonify({'success': False, 'message': '任务未找到'}), 404

    try:
        page = max(1, int(request.args.get('page', 1)))
    except (TypeError, ValueError):
        page = 1
    try:
        per_page = min(max(1, int(request.args.get('per_page', 20))), 100)
    except (TypeError, ValueError):
        per_page = 20

    query = (request.args.get('q') or '').strip()
    # 支持 ?pass=pass,fail 或重复 ?pass=pass&pass=fail
    pass_raw = request.args.getlist('pass') or [request.args.get('pass') or 'all']
    status_raw = request.args.getlist('status') or [request.args.get('status') or 'all']
    pass_filter = ','.join(pass_raw)
    status_filter = ','.join(status_raw)

    all_rows = list_task_case_rows(task_id)
    # 重启后内存评判线程会丢：结果页轮询时自动补跑仍缺 evaluation 的终态用例
    if maybe_schedule_missing_evaluations(task_id, all_rows):
        all_rows = list_task_case_rows(task_id)
    # 分面统计：随检索词变，不受状态/评判筛选影响（便于对照改筛选项）
    facet_rows = filter_case_result_rows(all_rows, query, 'all', 'all')
    facets = build_case_filter_facets(facet_rows)
    filtered = filter_case_result_rows(all_rows, query, pass_filter, status_filter)
    total = len(filtered)
    total_pages = max(1, (total + per_page - 1) // per_page)
    if page > total_pages:
        page = total_pages
    start = (page - 1) * per_page
    page_rows = filtered[start:start + per_page]

    task_status = task_meta.get('status') if task_meta else 'unknown'
    has_active_cases = is_task_executing(task_id) or any(
        r.get('status') in ('running', 'finishing', 'analyzing', 'queued')
        for r in all_rows
    ) or task_has_eval_activity(all_rows)

    resp = jsonify({
        'success': True,
        'task_id': task_id,
        'task_status': task_status,
        'task_executing': is_task_executing(task_id),
        'has_active_cases': has_active_cases,
        'cases': page_rows,
        'all_case_ids': [r.get('case_id') for r in filtered if r.get('case_id')],
        'pagination': {
            'page': page,
            'per_page': per_page,
            'total': total,
            'total_pages': total_pages,
        },
        'summary': {
            'total_cases': len(all_rows),
            'passed': sum(1 for r in all_rows if r.get('evaluation_passed') is True),
            'failed': sum(1 for r in all_rows if r.get('evaluation_passed') is False),
            'unevaluated': sum(1 for r in all_rows if r.get('evaluation_passed') is None),
        },
        'facets': facets,
    })
    resp.headers['Cache-Control'] = 'no-store, no-cache, must-revalidate, max-age=0'
    return resp


@app.route('/api/results/<task_id>/cases/<case_id>')
def get_task_case_detail(task_id, case_id):
    """获取单个用例的截图等详情（供表格行「查看截图」使用）。"""
    case_path = os.path.join(app.config['TESTRESULTS_FOLDER'], task_id, case_id)
    if not os.path.isdir(case_path):
        return jsonify({'success': False, 'message': '用例结果未找到'}), 404
    screenshots = collect_case_screenshots(task_id, case_id)
    task = load_tasks().get(task_id) or {}
    progress = None
    for item in task.get('case_progress') or []:
        if item.get('case_id') == case_id:
            progress = item
            break
    row = build_case_result_row(
        task_id,
        {
            'case_id': case_id,
            'screenshot_count': len(screenshots),
            'log_count': sum(1 for f in os.listdir(case_path) if f.endswith('.log')),
            'has_latency': os.path.exists(os.path.join(case_path, 'latency.json')),
            'has_evaluation': os.path.exists(os.path.join(case_path, 'evaluation.json')),
        },
        progress,
    )
    row['screenshots'] = screenshots
    row['replies'] = load_case_replies(task_id, case_id)
    row['qa_round_latency'] = load_case_round_latency(task_id, case_id)
    resp = jsonify({'success': True, 'case': row})
    resp.headers['Cache-Control'] = 'no-store, no-cache, must-revalidate, max-age=0'
    return resp


@app.route('/api/results/<task_id>/cases/<case_id>/report')
def get_case_report(task_id, case_id):
    """单用例分步结果预览（JSON）。"""
    rows = build_case_report_rows(task_id, case_id)
    return jsonify({
        'success': True,
        'task_id': task_id,
        'case_id': case_id,
        'rows': rows,
        'columns': [
            '用例编号', '轮次', '状态', '用户提问', '澄清消息', '预期回答',
            '模型回答', '评判结果', '单步时延(秒)', '首Token时延(秒)', '用例执行时间',
        ],
    })


@app.route('/api/results/<task_id>/cases/<case_id>/report.xlsx')
def download_case_report(task_id, case_id):
    """下载单用例分步结果 Excel。"""
    rows = build_case_report_rows(task_id, case_id)
    if not rows:
        return jsonify({'success': False, 'message': '暂无可导出的分步结果'}), 404
    buf = build_report_xlsx_bytes(rows, sheet_title=case_id)
    return send_file(
        buf,
        mimetype='application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        as_attachment=True,
        download_name=f'{case_id}_report.xlsx',
    )


@app.route('/api/results/<task_id>/report')
def get_task_report(task_id):
    """任务全用例分步结果预览（JSON）。"""
    rows = build_task_report_rows(task_id)
    return jsonify({
        'success': True,
        'task_id': task_id,
        'rows': rows,
        'columns': [
            '用例编号', '轮次', '状态', '用户提问', '澄清消息', '预期回答',
            '模型回答', '评判结果', '单步时延(秒)', '首Token时延(秒)', '用例执行时间',
        ],
    })


@app.route('/api/results/<task_id>/report.xlsx')
def download_task_report(task_id):
    """下载任务全用例分步结果 Excel。"""
    rows = build_task_report_rows(task_id)
    if not rows:
        return jsonify({'success': False, 'message': '暂无可导出的分步结果'}), 404
    buf = build_report_xlsx_bytes(rows, sheet_title=task_id)
    return send_file(
        buf,
        mimetype='application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        as_attachment=True,
        download_name=f'{task_id}_report.xlsx',
    )


@app.route('/api/results/<task_id>/cases/<case_id>/replies')
def get_task_case_replies(task_id, case_id):
    """获取用例 Device 回复记录及分步报告行。"""
    eval_data = load_hydrated_case_evaluation(task_id, case_id)
    replies = load_case_replies(task_id, case_id)
    report_rows = build_case_report_rows(task_id, case_id, eval_data=eval_data)
    return jsonify({
        'success': True,
        'task_id': task_id,
        'case_id': case_id,
        'replies': replies,
        'report_rows': report_rows,
        'evaluation': eval_data,
    }), 200, {'Cache-Control': 'no-store, no-cache, must-revalidate, max-age=0'}


@app.route('/api/results/<task_id>/cases/<case_id>/qa-latency')
def get_task_case_qa_latency(task_id, case_id):
    """获取问答轮次时延（DeviceAPP 事件驱动）。"""
    rounds = load_case_round_latency(task_id, case_id)
    total_ms = sum(r.get('latency_ms') or 0 for r in rounds)
    return jsonify({
        'success': True,
        'task_id': task_id,
        'case_id': case_id,
        'rounds': rounds,
        'summary': {
            'round_count': len(rounds),
            'total_latency_ms': total_ms,
            'total_latency_sec': round(total_ms / 1000.0, 3) if total_ms else 0,
        },
    })


@app.route('/api/results/<task_id>/cases/<case_id>/gui-step-latency')
def get_task_case_gui_step_latency(task_id, case_id):
    """获取 GUI 单步时延（端侧/云侧）。"""
    round_arg = request.args.get('round')
    round_no = None
    if round_arg is not None and str(round_arg).strip() != '':
        try:
            round_no = int(round_arg)
        except (TypeError, ValueError):
            return jsonify({'success': False, 'message': 'invalid round'}), 400
    if round_no is not None:
        item = load_gui_step_latency(task_id, case_id, round_no=round_no)
        device_steps = (item or {}).get('device_steps') or []
        cloud_steps = (item or {}).get('cloud_steps') or []
        stats = compute_gui_step_avg_latencies_sec(device_steps, cloud_steps)
        return jsonify({
            'success': True,
            'task_id': task_id,
            'case_id': case_id,
            'round': round_no,
            'item': item,
            'device_steps': device_steps,
            'cloud_steps': cloud_steps,
            **stats,
        }), 200, {'Cache-Control': 'no-store, no-cache, must-revalidate, max-age=0'}
    rounds = load_gui_step_latency(task_id, case_id)
    return jsonify({
        'success': True,
        'task_id': task_id,
        'case_id': case_id,
        'rounds': rounds or [],
    }), 200, {'Cache-Control': 'no-store, no-cache, must-revalidate, max-age=0'}


@app.route('/api/results/<task_id>/cases/<case_id>/eval-progress')
def get_case_eval_progress_api(task_id, case_id):
    """查询单用例评判进度（供重新评判弹窗轮询）。"""
    if not case_id:
        return jsonify({'success': False, 'message': '缺少 case_id'}), 400
    ep = get_case_eval_progress(task_id, case_id)
    eval_path = os.path.join(
        app.config['TESTRESULTS_FOLDER'], task_id, case_id, 'evaluation.json'
    )
    has_evaluation = os.path.isfile(eval_path)
    st = str((ep or {}).get('status') or '').lower()
    resp = jsonify({
        'success': True,
        'task_id': task_id,
        'case_id': case_id,
        'eval_progress': ep,
        'has_evaluation': has_evaluation,
        'busy': st in ('queued', 'running'),
        'eval_mtime': (
            os.path.getmtime(eval_path) if has_evaluation else None
        ),
    })
    resp.headers['Cache-Control'] = 'no-store, no-cache, must-revalidate, max-age=0'
    return resp


@app.route('/api/results/<task_id>/cases/<case_id>/evaluate', methods=['POST'])
def reevaluate_case_vl(task_id, case_id):
    """手动重跑评判：默认异步启动，前端轮询进度条；?async=0 可同步等待。"""
    if not case_id:
        return jsonify({'success': False, 'message': '缺少 case_id'}), 400
    task = load_tasks().get(task_id)
    if not task:
        return jsonify({'success': False, 'message': '任务不存在'}), 404
    if task.get('status') in ('running', 'finishing', 'analyzing'):
        return jsonify({
            'success': False,
            'message': f'任务状态为「{TASK_STATUS_LABELS.get(task.get("status"), task.get("status"))}」，请稍后再试',
        }), 409

    body = request.get_json(silent=True) or {}
    async_raw = request.args.get('async', body.get('async', '1'))
    async_mode = str(async_raw).strip().lower() not in ('0', 'false', 'no')

    cur = get_case_eval_progress(task_id, case_id)
    cur_st = str((cur or {}).get('status') or '').lower()
    if cur_st in ('queued', 'running'):
        return jsonify({
            'success': True,
            'started': True,
            'message': '评判进行中',
            'eval_progress': cur,
        })

    if async_mode:
        ctx = build_eval_ctx_for_case(task_id, case_id, task=task)
        if not ctx:
            return jsonify({'success': False, 'message': '用例结果目录不存在'}), 404
        # 由 schedule_case_evaluation 写入 queued 并启动线程（勿预先 set，否则会被去重跳过）
        schedule_case_evaluation(ctx)
        return jsonify({
            'success': True,
            'started': True,
            'message': '已开始后台评判',
            'eval_progress': get_case_eval_progress(task_id, case_id),
        })

    set_case_eval_progress(
        task_id, case_id,
        status='running', phase='start', percent=2, message='手动重跑评判',
    )

    def _progress_cb(**kw):
        set_case_eval_progress(task_id, case_id, status='running', **kw)

    try:
        ok, detail = run_case_evaluation(
            task_id, case_id,
            filename=f'{case_id}.json',
            target_package=task.get('target_package'),
            progress_cb=_progress_cb,
        )
    except Exception as e:
        logger.exception(f'手动 VL 评判失败 {task_id}/{case_id}: {e}')
        set_case_eval_progress(
            task_id, case_id,
            status='error', phase='error', percent=100, message=str(e)[:160],
        )
        return jsonify({'success': False, 'message': str(e)}), 500
    finally:
        clear_case_eval_progress(task_id, case_id)
    if not ok:
        return jsonify({'success': False, 'message': detail}), 400
    eval_path = os.path.join(
        app.config['TESTRESULTS_FOLDER'], task_id, case_id, 'evaluation.json'
    )
    eval_data = None
    if os.path.exists(eval_path):
        with open(eval_path, 'r', encoding='utf-8') as f:
            eval_data = json.load(f)
    if eval_data:
        try:
            from webtest_eval import hydrate_webtest_evaluation
            eval_data = hydrate_webtest_evaluation(eval_data)
        except Exception:
            pass
    return jsonify({
        'success': True,
        'message': '评判完成',
        'detail': detail,
        'evaluation': eval_data,
        'evaluation_passed': parse_evaluation_passed(eval_data) if eval_data else None,
    })


@app.route('/api/results/<task_id>/evaluation')
def get_task_evaluation(task_id):
    """获取 VL 评判结果"""
    task_path = os.path.join(app.config['TESTRESULTS_FOLDER'], task_id)
    case_id = request.args.get('case_id')
    if case_id:
        eval_file = os.path.join(task_path, case_id, 'evaluation.json')
    else:
        eval_file = os.path.join(task_path, 'evaluation.json')

    eval_data = None
    if os.path.exists(eval_file):
        try:
            with open(eval_file, 'r', encoding='utf-8') as f:
                eval_data = json.load(f)
        except Exception as e:
            return jsonify({'success': False, 'message': str(e)}), 500
    if eval_data:
        try:
            from webtest_eval import hydrate_webtest_evaluation
            eval_data = hydrate_webtest_evaluation(eval_data)
        except Exception:
            pass

    exceptions = []
    if eval_data and eval_data.get('exceptions'):
        exceptions = eval_data.get('exceptions') or []
    if not exceptions:
        exceptions = load_case_exceptions(task_id, case_id) or []

    if eval_data and eval_data.get('result_parsed'):
        return jsonify({
            'success': True,
            'evaluation': eval_data,
            'exceptions': exceptions,
            'evaluation_passed': parse_evaluation_passed(eval_data),
        })

    reason = diagnose_evaluation_missing_reason(task_id, case_id)
    return jsonify({
        'success': True,
        'evaluation': eval_data,
        'exceptions': exceptions,
        'evaluation_passed': parse_evaluation_passed(eval_data) if eval_data else None,
        'reason': reason,
    })

@app.route('/api/results/<task_id>/<path:filepath>')
def get_screenshot(task_id, filepath):
    """获取截图文件（支持子目录）。"""
    task_dir = os.path.join(app.config['TESTRESULTS_FOLDER'], task_id)
    safe_path = safe_join(task_dir, filepath)
    if safe_path is None:
        return jsonify({'success': False, 'message': '无效的文件路径'}), 400
    directory = os.path.dirname(safe_path)
    filename = os.path.basename(safe_path)
    return send_from_directory(directory, filename)

def _parse_screenshot_download_request():
    """解析截图下载请求（支持 JSON 与表单 POST，便于浏览器原生下载进度）。"""
    if request.is_json:
        data = request.get_json(silent=True) or {}
        task_id = data.get('task_id', '').strip()
        filenames = data.get('filenames', [])
        zip_name = data.get('zip_name', '').strip()
    else:
        task_id = request.form.get('task_id', '').strip()
        filenames_raw = request.form.get('filenames', '[]')
        zip_name = request.form.get('zip_name', '').strip()
        try:
            filenames = json.loads(filenames_raw) if filenames_raw else []
        except (TypeError, json.JSONDecodeError):
            filenames = []
    if not isinstance(filenames, list):
        filenames = []
    return task_id, filenames, zip_name


def _safe_zip_download_name(name, default):
    """生成安全的 zip 下载文件名。"""
    candidate = (name or '').strip() or default
    candidate = re.sub(r'[^\w.\-]+', '_', candidate)
    if not candidate.lower().endswith('.zip'):
        candidate += '.zip'
    return candidate


@app.route('/api/results/download', methods=['POST'])
def download_screenshots():
    """将选中的截图打包为 zip 下载。"""
    task_id, filenames, zip_name = _parse_screenshot_download_request()

    if not task_id or not filenames:
        return jsonify({'success': False, 'message': '缺少 task_id 或文件列表'}), 400

    task_dir = os.path.join(app.config['TESTRESULTS_FOLDER'], task_id)
    if not os.path.exists(task_dir):
        return jsonify({'success': False, 'message': '任务目录不存在'}), 404

    buf = io.BytesIO()
    with zipfile.ZipFile(buf, 'w', zipfile.ZIP_DEFLATED) as zf:
        for filename in filenames:
            filepath = safe_join(task_dir, filename)
            if filepath is None or not os.path.isfile(filepath):
                continue
            # 使用文件名（去掉可能的子目录前缀）作为 zip 内的路径
            arcname = filename.replace('\\', '/')
            zf.write(filepath, arcname)

    if buf.tell() == 0:
        return jsonify({'success': False, 'message': '没有找到可下载的文件'}), 404

    buf.seek(0)
    default_zip_name = f'{task_id}_screenshots.zip'
    return send_file(
        buf,
        mimetype='application/zip',
        as_attachment=True,
        download_name=_safe_zip_download_name(zip_name, default_zip_name)
    )


@app.route('/api/results/delete', methods=['POST'])
def delete_results():
    """Delete test results."""
    data = request.get_json()
    task_id = data.get('task_id')
    testset = data.get('testset')
    testcase = data.get('testcase')

    filename = data.get('filename')
    case_id = data.get('case_id') or data.get('testcase')

    if task_id and filename:
        task_path = os.path.join(app.config['TESTRESULTS_FOLDER'], task_id)
        filepath = safe_join(task_path, filename)
        if filepath and os.path.isfile(filepath):
            os.remove(filepath)
            return jsonify({'success': True, 'message': f'File {filename} deleted'})

    if task_id and case_id and not filename:
        case_path = os.path.join(app.config['TESTRESULTS_FOLDER'], task_id, case_id)
        if os.path.exists(case_path):
            import shutil
            shutil.rmtree(case_path)
            return jsonify({'success': True, 'message': f'Case {case_id} deleted'})
        return jsonify({'success': False, 'message': f'用例 {case_id} 未找到'})

    if task_id and not filename and not case_id:
        task_file = os.path.join(app.config['TASKS_FOLDER'], f"{task_id}.json")
        results_dir = os.path.join(app.config['TESTRESULTS_FOLDER'], task_id)
        existed = os.path.exists(task_file) or os.path.exists(results_dir)
        delete_task_data(task_id)
        if existed:
            return jsonify({'success': True, 'message': f'Task {task_id} deleted'})
        return jsonify({'success': False, 'message': '任务未找到'})

    return jsonify({'success': False, 'message': 'Invalid request'})

# ========== WebSocket Events ==========

@socketio.on('connect')
def handle_connect():
    """Handle device connection."""
    logger.info(f"Client connected: {request.sid}")

@socketio.on('disconnect')
def handle_disconnect():
    """Handle device disconnection."""
    logger.info(f"Client disconnected: {request.sid}")
    with devices_lock:
        for device_id, device in list(devices.items()):
            if device.get('sid') == request.sid:
                del devices[device_id]
                logger.info(f"Device removed: {device_id}")
                break


@socketio.on('join_device_console')
def handle_join_device_console(data):
    """浏览器订阅某台设备的实时控制台日志。"""
    device_id = ''
    if isinstance(data, dict):
        device_id = (data.get('deviceId') or data.get('device_id') or '').strip()
    if device_id:
        join_room(f'device_console:{device_id}')
        emit('joined_device_console', {'deviceId': device_id})
        send_device_console_control(device_id, enable=True)


@socketio.on('leave_device_console')
def handle_leave_device_console(data):
    device_id = ''
    if isinstance(data, dict):
        device_id = (data.get('deviceId') or data.get('device_id') or '').strip()
    if device_id:
        leave_room(f'device_console:{device_id}')


@socketio.on('join_task')
def handle_join_task(data):
    """浏览器加入任务房间，接收 device_live_log 推送。"""
    task_id = ''
    if isinstance(data, dict):
        task_id = (data.get('taskId') or '').strip()
    if task_id:
        join_room(f'task:{task_id}')
        emit('joined_task', {'taskId': task_id})


@socketio.on('leave_task')
def handle_leave_task(data):
    task_id = ''
    if isinstance(data, dict):
        task_id = (data.get('taskId') or '').strip()
    if task_id:
        leave_room(f'task:{task_id}')


@socketio.on('message')
def handle_message(data):
    """Handle messages from devices."""
    msg_type = data.get('type')

    if msg_type == 'register':
        device_id = data.get('deviceId')
        device_name = data.get('deviceName')
        device_info = data.get('deviceInfo', {})

        with devices_lock:
            devices[device_id] = {
                'id': device_id,
                'name': device_name,
                'info': device_info,
                'last_seen': time.time(),
                'sid': request.sid
            }

        logger.info(f"Device registered: {device_id} - {device_name}")
        emit('message', {'type': 'registered'}, room=request.sid)

    elif msg_type == 'heartbeat':
        device_id = data.get('deviceId')
        with devices_lock:
            if device_id in devices:
                devices[device_id]['last_seen'] = time.time()

    elif msg_type == 'test_completed':
        # Handle test completion
        task_id = data.get('taskId')
        test_id = data.get('testId')
        success = data.get('success', False)
        logger.info(f"Test completed: {test_id}, success={success}")

        # Update tasks tracking
        with tasks_lock:
            if task_id in tasks:
                task_data = tasks[task_id]
                # Add result to task
                task_data['results'].append({
                    'test_id': test_id,
                    'status': 'completed',
                    'success': success,
                    'device_id': data.get('deviceId'),
                    'timestamp': time.time()
                })
                logger.info(f"Updated task {task_id} with test result: {test_id}")

        # Save screenshot if provided
        screenshot_data = data.get('screenshot')
        if screenshot_data and task_id:
            import base64
            try:
                img_data = base64.b64decode(screenshot_data.split(',')[1])
                result_path = os.path.join(app.config['TESTRESULTS_FOLDER'], task_id, test_id)
                os.makedirs(result_path, exist_ok=True)
                filename = f"result_{int(time.time())}.png"
                with open(os.path.join(result_path, filename), 'wb') as f:
                    f.write(img_data)
            except Exception as e:
                logger.error(f"Error saving screenshot: {e}")

# ========== Main ==========

def scheduled_tasks_thread():
    """轮询到期的定时任务并启动执行。"""
    while True:
        time.sleep(SCHEDULED_TASK_POLL_SEC)
        try:
            dispatch_due_scheduled_tasks()
        except Exception as e:
            logger.error(f"Scheduled task dispatch error: {e}")


threading.Thread(target=scheduled_tasks_thread, daemon=True).start()

if __name__ == '__main__':
    # Initialize testfiles
    scan_testfiles()
    scan_excel_datasets()
    host = os.getenv('GUI_EVAL_HOST', '0.0.0.0').strip() or '0.0.0.0'
    try:
        port = int(os.getenv('GUI_EVAL_PORT', '9527'))
    except ValueError:
        port = 9527
    debug = os.getenv('GUI_EVAL_DEBUG', '').strip().lower() in ('1', 'true', 'yes', 'on')

    logger.info(f"Starting WebServer V2 on {host}:{port} (base_path={app.config['BASE_PATH'] or '/'})")
    # flask-socketio 已自行 threaded=True；勿再传 threaded（会 TypeError 起不来）
    # DeviceSDK 卡死根因是 create_task 同步 wake_lab，已对 is_devicesdk 强制跳过
    socketio.run(app, host=host, port=port, debug=debug, allow_unsafe_werkzeug=True)
