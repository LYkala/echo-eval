#!/usr/bin/env python
"""重启 webserver_v2.py，使用当前 python（与手动执行 python webserver_v2.py 相同）。"""

import os
import signal
import subprocess
import sys
import time

ROOT = os.path.dirname(os.path.abspath(__file__))
V2_DIR = os.path.join(ROOT, 'webserverV2')
MAIN = os.path.join(V2_DIR, 'webserver_v2.py')
LOG_DIR = os.path.join(V2_DIR, 'logs')
LOG_FILE = os.path.join(LOG_DIR, 'webserver_stdout.log')
PORT = int(os.environ.get('GUI_EVAL_PORT', '9527'))
PYTHON = sys.executable


def stop_old():
    try:
        out = subprocess.check_output(['pgrep', '-f', 'webserver_v2.py'], text=True)
        for line in out.splitlines():
            pid = int(line.strip())
            if pid != os.getpid():
                os.kill(pid, signal.SIGTERM)
    except (subprocess.CalledProcessError, ProcessLookupError, ValueError):
        pass
    time.sleep(2)


def _load_emu_env():
    """Ensure ARM emulator bridge has target SSH creds after restart."""
    for path in (
        os.environ.get('EMU_ENV_FILE', '').strip(),
        '/root/testApp/env_web.sh',
        os.path.join(V2_DIR, 'env_emu.sh'),
    ):
        if not path or not os.path.isfile(path):
            continue
        with open(path, encoding='utf-8', errors='replace') as f:
            for raw in f:
                line = raw.strip()
                if not line or line.startswith('#'):
                    continue
                if line.startswith('export '):
                    line = line[7:].strip()
                if '=' not in line:
                    continue
                key, val = line.split('=', 1)
                key = key.strip()
                val = val.strip().strip("'").strip('"')
                if key.startswith('EMU_') or key.startswith('WEB_DEVICE_'):
                    os.environ.setdefault(key, val)
        break
    os.environ.setdefault('EMU_ON_JUMP', '1')


def start():
    _load_emu_env()
    os.makedirs(LOG_DIR, exist_ok=True)
    with open(LOG_FILE, 'a', encoding='utf-8') as log:
        log.write(f'\n--- restart {time.strftime("%Y-%m-%d %H:%M:%S")} ---\n')
        log.flush()
        proc = subprocess.Popen(
            [PYTHON, MAIN],
            cwd=V2_DIR,
            stdout=log,
            stderr=subprocess.STDOUT,
            stdin=subprocess.DEVNULL,
            start_new_session=True,
            env=os.environ.copy(),
        )
    time.sleep(2)
    if proc.poll() is not None:
        print('启动失败，最近日志:')
        with open(LOG_FILE, encoding='utf-8', errors='ignore') as f:
            print(''.join(f.readlines()[-20:]))
        sys.exit(1)
    print('启动成功')
    print(f'Python: {PYTHON}')
    print(f'PID: {proc.pid}')
    print(f'本机访问: http://127.0.0.1:{PORT}')
    print(f'日志: {LOG_FILE}')


def main():
    if not os.path.isfile(MAIN):
        print(f'错误: 找不到 {MAIN}', file=sys.stderr)
        sys.exit(1)
    print(f'正在重启 (Python: {PYTHON})...')
    stop_old()
    start()


if __name__ == '__main__':
    main()
