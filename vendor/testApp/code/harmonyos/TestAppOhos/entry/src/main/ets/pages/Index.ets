import { webSocket } from '@kit.NetworkKit';
import { BusinessError } from '@kit.BasicServicesKit';
import { deviceInfo } from '@kit.BasicServicesKit';

const DEFAULT_WS = 'ws://124.71.69.102:9526/ws/device';
const CLIENT_APP = 'testapp';
const APP_VERSION = '1.0.0-ohos-scaffold';

interface DeviceInfoPayload {
  manufacturer: string;
  model: string;
  androidVersion: string;
  appVersion: string;
  clientApp: string;
  serial: string;
}

interface RegisterPayload {
  type: string;
  deviceId: string;
  deviceName: string;
  deviceInfo: DeviceInfoPayload;
  files: string[];
}

interface HeartbeatPayload {
  type: string;
  deviceId: string;
  files: string[];
}

interface TestCompletedPayload {
  type: string;
  taskId: string;
  caseId: string;
  filename: string;
  success: boolean;
  message: string;
}

@Entry
@Component
struct Index {
  @State serverUrl: string = DEFAULT_WS;
  @State statusText: string = '未连接';
  @State logText: string = '';
  @State deviceId: string = '';
  private socket: webSocket.WebSocket | undefined = undefined;
  private heartbeatTimer: number = -1;

  aboutToAppear(): void {
    this.deviceId = `device_ohos_${Date.now().toString(16)}`;
  }

  aboutToDisappear(): void {
    this.disconnect();
  }

  private appendLog(line: string): void {
    const stamp = new Date().toLocaleTimeString();
    this.logText = `[${stamp}] ${line}\n` + this.logText;
    if (this.logText.length > 4000) {
      this.logText = this.logText.substring(0, 4000);
    }
  }

  private stopHeartbeat(): void {
    if (this.heartbeatTimer !== -1) {
      clearInterval(this.heartbeatTimer);
    }
    this.heartbeatTimer = -1;
  }

  private startHeartbeat(): void {
    this.stopHeartbeat();
    this.heartbeatTimer = setInterval(() => {
      if (!this.socket) {
        return;
      }
      const payload: HeartbeatPayload = {
        type: 'heartbeat',
        deviceId: this.deviceId,
        files: []
      };
      const msg = JSON.stringify(payload);
      this.socket.send(msg, (err: BusinessError, ok: boolean) => {
        if (err || !ok) {
          this.appendLog(`heartbeat send fail: ${JSON.stringify(err)}`);
        }
      });
    }, 15000);
  }

  private sendRegister(): void {
    if (!this.socket) {
      return;
    }
    const brand = deviceInfo.brand || 'Harmony';
    const model = deviceInfo.marketName || deviceInfo.productModel || 'Phone';
    const info: DeviceInfoPayload = {
      manufacturer: deviceInfo.brand || 'HUAWEI',
      model: deviceInfo.marketName || deviceInfo.productModel || 'unknown',
      androidVersion: deviceInfo.osFullName || 'HarmonyOS',
      appVersion: APP_VERSION,
      clientApp: CLIENT_APP,
      serial: ''
    };
    const payload: RegisterPayload = {
      type: 'register',
      deviceId: this.deviceId,
      deviceName: `${brand} ${model}`,
      deviceInfo: info,
      files: []
    };
    const json = JSON.stringify(payload);
    this.appendLog(`register -> ${json}`);
    this.socket.send(json, (err: BusinessError, ok: boolean) => {
      if (err || !ok) {
        this.statusText = '注册发送失败';
        this.appendLog(`register send fail: ${JSON.stringify(err)}`);
      }
    });
  }

  private handleMessage(raw: string): void {
    this.appendLog(`<< ${raw}`);
    try {
      const msg = JSON.parse(raw) as Record<string, Object>;
      const type = String(msg['type'] || '');
      if (type === 'registered') {
        this.statusText = `已上线: ${this.deviceId}`;
        this.startHeartbeat();
        return;
      }
      if (type === 'execute_test') {
        const filename = String(msg['filename'] || '');
        const taskId = String(msg['taskId'] || '');
        this.appendLog(`收到 execute_test: ${filename} (鸿蒙脚手架暂未实现执行)`);
        const replyPayload: TestCompletedPayload = {
          type: 'test_completed',
          taskId: taskId,
          caseId: filename.replace(/\.json$/i, ''),
          filename: filename,
          success: false,
          message: 'HarmonyOS scaffold: GUI/accessibility not ported yet'
        };
        const reply = JSON.stringify(replyPayload);
        this.socket?.send(reply, (_err: BusinessError, _ok: boolean) => {});
        return;
      }
      if (type === 'stop_test') {
        this.appendLog('收到 stop_test');
      }
    } catch (e) {
      this.appendLog(`parse fail: ${String(e)}`);
    }
  }

  private connect(): void {
    this.disconnect();
    this.statusText = '连接中…';
    this.socket = webSocket.createWebSocket();
    this.socket.on('open', (err: BusinessError) => {
      if (err) {
        this.statusText = '连接失败';
        this.appendLog(`open err: ${JSON.stringify(err)}`);
        return;
      }
      this.statusText = '已连接，正在注册…';
      this.appendLog('WS open');
      this.sendRegister();
    });
    this.socket.on('message', (err: BusinessError, value: string | ArrayBuffer) => {
      if (err) {
        this.appendLog(`message err: ${JSON.stringify(err)}`);
        return;
      }
      if (typeof value === 'string') {
        this.handleMessage(value);
      }
    });
    this.socket.on('error', (err: BusinessError) => {
      this.statusText = 'WS 错误';
      this.appendLog(`error: ${JSON.stringify(err)}`);
    });
    this.socket.on('close', () => {
      this.statusText = '已断开';
      this.stopHeartbeat();
      this.appendLog('WS close');
    });
    this.socket.connect(this.serverUrl, (err: BusinessError, ok: boolean) => {
      if (err || !ok) {
        this.statusText = 'connect 调用失败';
        this.appendLog(`connect fail: ${JSON.stringify(err)}`);
      }
    });
  }

  private disconnect(): void {
    this.stopHeartbeat();
    if (this.socket) {
      try {
        this.socket.close();
      } catch (_e) {
      }
      this.socket = undefined;
    }
  }

  build() {
    Column({ space: 12 }) {
      Text('TestApp 鸿蒙脚手架')
        .fontSize(22)
        .fontWeight(FontWeight.Bold)
        .width('100%')

      Text('仅实现 9526 WebSocket 注册/心跳；无障碍与用例执行尚未移植。')
        .fontSize(13)
        .fontColor('#666666')
        .width('100%')

      TextInput({ text: this.serverUrl, placeholder: 'ws://host:9526/ws/device' })
        .onChange((v: string) => {
          this.serverUrl = v;
        })
        .width('100%')

      Text(`状态: ${this.statusText}`)
        .fontSize(16)
        .width('100%')

      Text(`deviceId: ${this.deviceId}`)
        .fontSize(12)
        .fontColor('#888888')
        .width('100%')

      Row({ space: 12 }) {
        Button('连接并注册')
          .onClick(() => this.connect())
        Button('断开')
          .backgroundColor('#888888')
          .onClick(() => this.disconnect())
      }
      .width('100%')

      Scroll() {
        Text(this.logText)
          .fontSize(12)
          .fontFamily('monospace')
          .width('100%')
      }
      .layoutWeight(1)
      .width('100%')
      .backgroundColor('#F5F5F5')
      .padding(8)
      .borderRadius(8)
    }
    .padding(16)
    .width('100%')
    .height('100%')
  }
}
