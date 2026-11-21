# 网盘搜索（百度网盘手机 App · 9526）

跳板机 9526 上的「网盘搜索」子模块：用 **ADB 驱动手机上的百度网盘 App**，按 E2E 用例的「原始输入」逐条搜索，
把结果页图片的**位次 + 文件名**采集出来，再换算成 `fid` 写回数据集。

- 入口页面：`http://124.71.69.102:9526/pan-search`（主站侧栏「网盘搜索」）
- 独立控制台：`/pan-search/app`（`pan_search.html` 用 iframe 内嵌）
- 蓝图：`netdisk_search`（`netdisk_search/blueprint.py`），API 前缀 `/api/netdisk/*`
- 目标 App：`com.baidu.netdisk`（`BAIDU_PKG`）

> 与另外两套的区分：
>
> | 模块 | 位置 | 驱动方式 | 被操作对象 |
> |---|---|---|---|
> | **本模块 `netdisk_search`** | 9526（`/pan-search`） | ADB（真机 + USB 桥） | **手机**百度网盘 App |
> | `mcloud_search` | 9526（`/mcloud-search`） | ADB（同一套桥设施） | 手机**移动云盘** App（`com.chinamobile.mcloud`） |
> | `netdisk-pc-search`（8767） | 本机 8767 | CDP 注入 JS | **PC** 百度网盘客户端 |

---

## 1. 总体架构

```
┌──────────┐   浏览器 HTTP     ┌───────────────────────────────┐
│ 网页      │ ───────────────► │ 9526 Flask（webserverV2）      │
│ pan-search│ ◄─────────────── │  blueprint: netdisk_search     │
└──────────┘   /api/netdisk/*  └───────────────┬───────────────┘
                                              │ subprocess
                                              ▼
                              adb -H 127.0.0.1 -P <桥端口> shell …
                                              │  (跳板机侧)
        ┌─────────────────────────────────────┘
        │ SSH 反向隧道（本机助手 paramiko request_port_forward）
        ▼
┌───────────────────────────┐   USB    ┌──────────────────────────┐
│ 你的电脑                   │ ───────► │ 手机（百度网盘 App）       │
│ StartAgent.bat            │          │ 搜索 → 结果页 → 大图页     │
│  ├ 本机 adb server :5037  │          └──────────────────────────┘
│  └ 反向上报到 9526 设备列表 │
└───────────────────────────┘
```

核心点：**跳板机上没有手机**。手机插在**你自己的电脑**上，由本机助手把 USB 经 SSH 反向隧道
暴露到跳板机的 `127.0.0.1:<桥端口>`，跳板机再用 `adb -H 127.0.0.1 -P <桥端口>` 控制手机。

---

## 2. 目录结构

```
webserverV2/
├── webserver_v2.py                  # 主站入口，注册蓝图（失败仅 warning）
├── netdisk_search/
│   ├── __init__.py                  # 导出 bp / register_netdisk
│   ├── config.py                    # 路径、常量、adb 可执行文件解析
│   ├── blueprint.py                 # 路由：页面 + /api/netdisk/* + 一键准备中转
│   ├── adb_util.py                  # ADB 封装 + 百度网盘 App 的页面操作原语
│   ├── dataset.py                   # 读写 E2E xlsx（openpyxl）
│   ├── runner.py                    # 后台单任务跑批线程
│   ├── agent_registry.py            # 多电脑助手注册表（每台电脑一个桥端口）
│   └── data/
│       ├── E2E_1000+cases_1w_data_v4.xlsx   # 用例数据集（主表 E2E_XLSX_NAME）
│       └── ES_联通_1w_data.xlsx              # fileName → fid 映射（ES*.xlsx）
├── templates/
│   ├── pan_search.html              # 主站页面（iframe 壳）
│   └── pan_search_frame.html        # 控制台 UI（实际交互页）
├── static/netdisk/
│   ├── app.css
│   └── app.js                       # 前端逻辑（2s 轮询 / 5s 助手刷新）
└── tools/
    ├── prepare_agent.py             # 本机助手（StartAgent）
    ├── setup_adb_bridge.py          # adb/paramiko 安装 + SSH 反向隧道
    ├── _deploy_to_9526.py           # 部署到 9526（`--netdisk`）
    └── start_prepare_agent.bat      # 本机一键启动
```

---

## 3. 关键常量（`config.py`）

| 常量 | 值 | 说明 |
|---|---|---|
| `E2E_XLSX_NAME` | `E2E_1000+cases_1w_data_v4.xlsx` | 用例数据集 |
| `ES_GLOB` | `ES*.xlsx` | 文件名→fid 映射表 |
| `BAIDU_PKG` | `com.baidu.netdisk` | 目标 App 包名 |
| `IME_ADB` | `com.android.adbkeyboard/.AdbIME` | 输入用输入法 |
| `ADB_USB_BRIDGE_HOST` | `127.0.0.1` | 桥主机（可环境变量覆盖） |
| `ADB_USB_BRIDGE_PORT` | `15036` | 默认桥端口（实际由助手指派，见 §5.8） |
| `resolve_adb_bin()` | 依次取 `ADB_BIN` → `ADB` → `/opt/android-sdk/platform-tools/adb` → 仓库 `tools/platform-tools/adb(.exe)` → `which adb` | adb 可执行文件定位 |

数据/结果目录都是**模块私有**的（`netdisk_search/data`、`netdisk_search/results`），与 `mcloud_search` 互不干扰。

---

## 4. 手机端操作链路（核心）

百度网盘 App 的搜索有两条完全不同的路径，前端「搜索方式」下拉切换，后端 `runner` 分流到两套原语。

### 4.1 相册搜索（`search_mode=album`，默认）

```
Flutter 搜索页 ──搜索──► SearchTimeLineActivity（结果时间线）
    │                        │ 点第一个 /thumbnail
    │                        ▼
    │                 ImagePagerActivity（大图页）
    │                        │ 点「更多」→ 读 /image_name
    │                        │ 关弹窗 → 左滑下一张
    │                        └──► 循环直到 到头/重复/达到上限
```

涉及的关键节点与实现：

- **搜索栏坐标缓存** `_BAR_CACHE`：同一台手机上搜索栏位置不变，`learn_search_bar()` 只解析一次，
  缓存 `edit`（输入框中心）/`clear`（右侧叉）/`search`（搜索按钮）三个坐标，后续用例直接点缓存坐标，
  省掉大量 `dump`。缓存键为 `host:port:serial`。
- **输入**：`ensure_ime()` 切到 AdbIME → 点输入框 → 点叉清空 → 再点回输入框 →
  `commit_query()` 用 `am broadcast -a ADB_INPUT_B64 --es msg <base64>` 发送（中文走 base64，避免 shell 吃字符）→
  `field_has_query()` 校验输入框回显，不匹配则重聚焦重发。
- **判定结果页**：轮询 `dumpsys window` 里的 `mCurrentFocus`，命中 `SearchTimeLineActivity` 即算就绪，
  记录 `latency_ms = 点搜索 → 结果页出现`。
- **进大图页**：`list_thumbs()` 取 `/thumbnail` 且边长 ≥80 的格子里第一个，`on_preview()` 校验 `ImagePagerActivity`。
- **读文件名** `read_name_via_more()`：点「更多」（找不到先点屏幕 (540,1200) 唤出底栏）→
  在弹层里读 `/image_name` → BACK 关弹层**留在原地**（不重新 dump 页面）。
- **翻页** `pager_swipe_next()`：`input swipe 920 1200 → 160 1200`（左滑）。
- **收敛条件**：读名连续失败 3 次 → `exhausted`（有记录）/`failed`（无记录）；读到与第一张同名 → `looped`（滑一圈回来了）；
  读到已见过的名字 → `exhausted`（划不动）；达到 `max_images` → `capped`。

### 4.2 全局搜索（`search_mode=global`）

```
首页(MainActivity) ─点 searchbox─► 全局搜索输入页(KmpShared/Compose)
    │  ADB_CLEAR_TEXT + ADB_INPUT_B64 → KEYCODE_BACK 收键盘提交
    ▼
结果页(ImageSearchResult/综合) ─点 +N 或 ImageRow_thumb_0─► 图片网格
    │  网格从左到右、从上到下：点开大图 → 读名 → 返回网格
    │  本页采完上滑翻页
    ▼
循环直到 没更多 / 无新增 / 达到上限
```

与相册搜索不同的坑与对策（代码注释里都有记录）：

- **首页搜索栏定位** `_g_home_search_xy()`：只认 `id/desc` 含 `searchbox`、宽度 ≥300、**高度最矮**的可点节点。
  按 y 区间取会命中 `stickyHeadView`（落在「最近文件」内容区，误开文件）；首页搜索栏还有收起/展开两种版式。
- **输入框定位** `_g_field_tap()`：只认 `content-desc` 含 `inputTextField` / `placeholderBox`；
  用 `searchbox` 会命中整块容器 `PanSearchContent_searchboxColumn`，中心点落在死区不聚焦。
- **输入提交**：全局搜索页是 Compose 自绘，键盘弹起期间输入框不落账，**必须先 `KEYCODE_BACK` 收起键盘**才提交；
  App 自动搜索，物理上不存在「点搜索按钮」这一跳，所以 `latency_ms = 收键盘提交 → 结果列表页出现`。
- **结果页判据** `_g_result_page_visible()`：只认结果页专属节点（`ImageRow_moreBox`、`ImageRow_thumb_`、
  `ImageSearchGridContent`、`SearchCompositeResult`）。用「综合/图像」松匹配会把输入页误判成结果页。
- **防瞎搜守门**：结果页搜索框回显必须等于 query，否则判定为 App 用 placeholder 猜词，整条用例放弃重跑。

### 4.3 UI dump 的可靠性（`Adb.dump()`）

`uiautomator dump <remote>` → `pull` 到临时文件 → 校验文件存在且 ≥32 字节 → 解析 XML。
拉不到就直接抛错，并提示「请在手机电脑上保持 StartAgent.bat 运行，并重新点『一键准备』」——
哨兵式失败，避免拿着拉取失败的空结果继续瞎点。

---

## 5. 核心设计

### 5.1 数据集读写（`dataset.py`）

- 读：`read_cases(path, offset, limit, q, sort_by, sort_dir, count_min, count_max, status, ids_only)`，
  `read_only=True, data_only=True`。`status=未跑` 同时匹配空值与字面「未跑」；`ids_only=True` 只回 `ids` 列表
  （供前端「全部选中」一次拿全筛选结果）。
- 写：`write_actual_results()` 按固定顺序**按需插列**

  ```
  … | 执行状态 | 搜索时延(ms) | 实际搜索数量 | 实际fid | 实际图片名称
  ```

  `ensure_header_before(ws, title, before)`：列不存在时插到 `before` 前面，保证列顺序稳定。
- **已完成保护**：`overwrite_done=False` 且目标行当前 `执行状态=完成` 时，**不写入**（防止中断/失败覆盖已完成结果）。
- `is_case_done()`：只有显式 `执行状态=完成` 才算完成；`中断`/`失败` 会被重跑。
- `names_to_actual_fid()`：用 ES 表 `fileName → fid` 映射把文件名批量换算成 fid，未命中的记入 `missing`。

### 5.2 结果文件流转（`runner._run`）

```
results/latest.xlsx  ──copy──►  results/run_<时间戳>.xlsx  ──copy──►  results/latest.xlsx
      │（不存在则用主表 E2E）
      └─ 作为本次跑的基线（保留历史完成结果）
```

每条用例的结果同时写 **3 个位置**：本次 `run_<ts>.xlsx`、`results/latest.xlsx`、主表 `data/E2E_….xlsx`；
每跑完一条用例，把整份运行状态 `state` 落到 `results/run_<ts>.json`（含 order / fid / 时延 / 错误），可事后审计。

### 5.3 采集状态 → 执行状态映射

`collect_search_order` / `global_collect_search_order` 返回：

| 采集 status | 含义 | 有图片记录？ | 写回「执行状态」 |
|---|---|---|---|
| `looped` | 左滑一圈回到第一张 | 有 | `完成` |
| `exhausted` | 到尾 / 划不动 | 有 | `完成` |
| `capped` | 达到单用例采图上限 | 有 | `完成` |
| `looped/exhausted/capped` | 同上但**一张没采到** | 无 | 保持空（未跑） |
| `empty` | 结果页确实没有图片模块 | 无 | 保持空（未跑） |
| `stopped` | 用户点了停止 | 任意 | `中断` |
| `failed` / 其它 | 异常 | 无 | `失败` |
| `failed` / 其它 | 异常 | 有 | `中断` |

`_DONE_STATUSES = {"looped", "exhausted", "capped"}`。

### 5.4 单任务模型与停止语义

- `RUNNER = Runner()` 是**进程内单例**，同一时刻只允许一个跑批；重复 `start` 返回 `{"ok": false, "error": "already running"}`（HTTP 409）。
- `stop()`：置 `_stop` 事件 + `adb.halt()`（`halt` 会把在途 adb 子进程 kill 掉，`run()` 之后一律返回空串）。
- 停止后 `finally` 里先 `adb.halted = False`（放开才能继续发返回键），把手机**复位到搜索页并点叉清空关键词**，
  再重新 `halt()`。
- 线程收尾：**恢复用户原输入法**（`_restore_user_ime()`）→ `_adb = None` → `running = False` + `finished_at`。

### 5.5 输入法切换与恢复

跑批前记录用户默认输入法：

```
settings get secure default_input_method
  └ 已是 adbkeyboard → 从 `ime list -s` 里挑一个非 adbkeyboard 的作为恢复目标
```

跑批结束/被停止时 `restore_ime(adb, prev)` 恢复，避免把用户的手机输入法永久改成 ADBKeyboard。
`ensure_ime()` 里对华为/荣耀做了兼容（先写 `enabled_input_methods` 再 `enable`/`set`）。

### 5.6 单用例最多采图数

`max_images` 由前端「单用例最多采图数」传入，**默认 60**，直接透传给采集函数作 `max_items`（本模块不封顶）。
达到上限时采集状态为 `capped`，仍算该用例完成。

### 5.7 前端（`pan_search_frame.html` + `static/netdisk/app.js`）

- 页面通过 `window.NETDISK_API_BASE = "/api/netdisk"` 注入 API 前缀。
- **轮询**：`pollStatus()` **每 2 秒**（进度 / 日志 / 本轮明细一并刷新）；`refreshAgents()` **每 5 秒**。
- 布局：左侧「设备 / 执行」面板，右侧四个 Tab —— 数据集预览、结果预览、运行日志、本轮明细。
- 数据集工具栏：搜索框、预期数量区间、**执行状态筛选（全部/未跑/完成/中断/失败）**、
  **全部选中**（按当前筛选条件请求 `?which=cases&limit=1&ids=1` 一次拿到全部用例编号）、**取消全选**、
  筛选/刷新、清除筛选、上一页/下一页、每页 30/50/100。
- 表格内可直接改某行的「执行状态」下拉（`POST /api/netdisk/case/status`，同时写 `latest.xlsx` 与主表）。
- 「截取屏幕」弹窗：`GET /api/netdisk/screenshot?serial=…`，返回 `no-store` 的 PNG。
- 「一键准备」按钮走 `prepare/*` 接口，配进度条与提示文案。

### 5.8 多电脑 / 多手机隔离（`agent_registry.py`）

多个人同时用同一台 9526 时，必须保证「我看到的手机是我自己的」。

- 每台电脑跑一个助手，用稳定的 `agent_id`（`tools/agent_id.txt`；没有就用 `主机名-uuid6`）。
- `hello()` 给该 `agent_id` **分配一个专属桥端口**（`15036–15060` 内取空闲；已有则复用）。
- 助手上报设备时，`publish_devices()` 会给每个设备补上 `adb_host=127.0.0.1`、`adb_port=<该端口>`、
  `channel_label="{主机名} · 桥:{port}"`。**桥不通时清空设备列表**——避免网页「看得到却控不了」。
- 助手上报的设备**优先**；设备注册表有 `_AGENT_TTL = 45s`，助手掉线超时即清理。
- `list_devices()` 合并三个来源：① 各助手上报的设备；② 跳板机本机 adb；③ 兜底扫一遍在线助手的桥端口。
- `start` 前会二次校验：`resolve_device_endpoint(serial)`（在列表里且 `state=device`）→ `probe_device_reachable(ep)`
  （`adb -s serial get-state` 含 `device`），任一失败直接返回可读错误，不进入跑批。

### 5.9 一键准备（`prepare_*` + 本机助手）

网页侧（`blueprint.py` 的 `/api/netdisk/prepare/*`）只做**任务中转**：`request` 建 job → 助手 `poll` 领取 → 助手 `report` 回报进度。

本机侧 `tools/prepare_agent.py`（`StartAgent.bat` 启动）主循环（1s 一跳）：

```
hello()                      → 拿/续自己的桥端口
if tick % 5 == 0: ensure_bridge_alive()   → 桥断了就重连
publish_devices()            → 桥通才上报本机 USB 设备
poll()                       → 领到 job 就后台线程执行 run_prepare()
```

`tools/setup_adb_bridge.py` 负责真正打桥：确保 adb 可用 → 确保有本机 USB 设备 → 确保 paramiko →
`transport.request_port_forward("", port)` 在跳板机上开监听端口 → 跳板机连进来时把流量
pipe 到本机 adb server（`:5037`）。桥的存续是硬前提，助手进程必须保持运行。

---

## 6. HTTP API 一览（前缀 `/api/netdisk`）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/pan-search` | 页面（iframe 壳） |
| GET | `/pan-search/app` | 独立控制台 UI |
| GET | `/api/netdisk/health` | 健康检查，返回模块 ROOT |
| GET | `/api/netdisk/devices` | 在线设备列表（含 `channel_label`、`adb_host/port`） |
| GET | `/api/netdisk/screenshot?serial=` | 手机当前画面 PNG（`Cache-Control: no-store`） |
| GET | `/api/netdisk/dataset?which=cases\|results&offset&limit&q&sort_by&sort_dir&count_min&count_max&status&ids` | 数据集/结果分页；`ids=1` 只回编号列表 |
| GET | `/api/netdisk/run/status` | 跑批状态（running/progress/total/current_case/logs/cases/…） |
| POST | `/api/netdisk/run/start` | `{serial, case_ids?, limit?, max_images?, force?, search_mode?}` |
| POST | `/api/netdisk/run/stop` | 停止当前跑批 |
| POST | `/api/netdisk/case/status` | `{case_id, status}` 手改执行状态（`""` 表示清空为未跑） |
| GET | `/api/netdisk/results/download` | 下载 `results/latest.xlsx`（无则最新 `run_*.xlsx`） |
| GET | `/api/netdisk/prepare/agents` | 在线本机助手列表 |
| POST | `/api/netdisk/prepare/hello` | 助手心跳，分配专属桥端口 |
| POST | `/api/netdisk/prepare/devices` | 助手上报本机 USB 设备 |
| POST | `/api/netdisk/prepare/request` | 网页发起一键准备（必须指定 `agent_id`） |
| GET | `/api/netdisk/prepare/status` | 网页轮询准备进度 |
| GET | `/api/netdisk/prepare/poll` | 助手领取指定给自己的 job |
| POST | `/api/netdisk/prepare/report` | 助手上报进度 / 结果 |

---

## 7. 运行过程

### 7.1 部署到 9526

```powershell
# 只部署网盘搜索相关文件 + 数据集，并重启 9526
python tools/_deploy_to_9526.py --netdisk
```

脚本行为：

1. SFTP 上传 `webserver_v2.py`、`pan_search*.html`、`static/netdisk/*`、`netdisk_search/*.py`；
   上传前对远端同名文件做 `.bak_<时间戳>` 备份；
2. 上传 `netdisk_search/data/*.xlsx`，并建好 `data/` 与 `results/` 目录；
3. `py_compile webserver_v2.py` → 打印 `SYNTAX_OK`；`from netdisk_search import register_netdisk` → `NETDISK_IMPORT_OK`；
4. 重启 `webserver-9526.service`（无该 unit 则走 `stop_9526.sh` / `start_9526.sh`），
   校验 `:9526`/`:9527` 都在听、`/pan-search` 返回 200、`/api/netdisk/health`、`/api/netdisk/devices` 正常。

> 常用参数：`--no-restart` 只上传不重启；`--files a.py b.py` 指定文件；`--apk` 附带 APK。
> `webserver_v2.py` 里蓝图注册失败只打 warning，不会拖垮主站；所以部署后务必看 `NETDISK_IMPORT_OK`。

### 7.2 本机侧准备（每台要控制手机的电脑做一次/每次开机做一次）

1. 手机用 USB 连电脑，打开「USB 调试」，并在手机上授权该电脑。
2. 电脑上运行 `start_prepare_agent.bat`（→ `tools/prepare_agent.py`）；助手会：
   检测/安装 ADB → 列出本机 USB 设备 → 装 paramiko → 建 SSH 反向隧道到 9526 → 上报设备。
   若没有仓库，可下载便携助手 zip（`http://124.71.69.102:9526/static/netdisk/NetdiskPrepareAgent.zip`）解压后运行。
3. **保持助手窗口运行**：它是 9526 能控制你手机的唯一通道；关掉 → 影响就是桥断、跑批报 Connection refused。
4. 网页上选「电脑助手」（下拉显示 `主机名 · 桥:<port> · 设备N`）→ 点「一键准备」→ 进度条走完（100%）。

### 7.3 跑批一次完整时序

```
① 网页选设备（下拉来自 /api/netdisk/devices，只列 state=device）
② 数据集预览：搜索/预期数量/执行状态筛选 → 勾选用例（或不勾＝全部未完成）
③ （可选）设「用例数量上限」「单用例最多采图数」（默认 60）、「搜索方式」（相册/全局）、
   是否「强制重跑已完成」
④ 点「开始」→ POST /api/netdisk/run/start {serial, case_ids, limit, max_images, force, search_mode}
   └ 后端：resolve_device_endpoint → probe_device_reachable（不通即报错返回）
   └ RUNNER.start()：后台线程 _run()
⑤ runner：
   latest.xlsx（无则主表）→ copy 成 run_<ts>.xlsx 与新的 latest.xlsx
   载入 ES 表 fileName→fid 映射
   记录用户输入法（用于跑完恢复）
   逐条用例：
     input_query_and_search / global_input_query_and_search（失败则复位重试一次）
     collect_search_order / global_collect_search_order（最多 max_images 张）
     文件名 → fid 换算 → 写回 3 个 xlsx → 落 run_<ts>.json
     每条用例结束都复位到搜索页并清空关键词
   finally：恢复用户输入法
⑥ 前端每 2s 轮询 /run/status：进度、日志（保留最近 800 行）、本轮明细
⑦ 点「下载结果 xlsx」拿最终表；或在数据集/结果预览里核对写回列
⑧ 需要中止：点「停止」→ 当前用例记「中断」，手机复位
```

### 7.4 用例的跳过与重跑规则

- 已「完成」的用例**默认跳过**（勾选了也跳过，日志会打印 `skip already-done`）；
- 只有勾选「强制重跑已完成」（`force=true`）才重跑，且此时允许覆盖已完成行的写回结果；
- `中断` / `失败` 的用例**会**被重跑；
- 采集到「空结果 / 无图片模块」的用例保持空状态（视为未跑），下次会再跑。

---

## 8. 注意事项与排查

| 现象 | 原因 / 处理 |
|---|---|
| 设备下拉为空 / 提示「未检测到 device」 | 本机助手没跑或桥没通 → 在**插手机的电脑**上运行 `StartAgent.bat`，保持窗口，再点「一键准备」 |
| 报「跳板机无法经 127.0.0.1:<port> 控制该手机」 | 桥断（`Connection refused`）→ 检查助手窗口、USB 线、手机授权，重新「一键准备」 |
| 报「无法拉取 UI dump」 | 同上；`dump` 是哨兵，拿不到就整条用例失败，不会瞎点 |
| 日志 iPhone 式空转 / 搜索超时 | 手机可能不在百度网盘搜索页或弹了权限弹窗；先人工看一眼「截取屏幕」，必要时手动清弹窗 |
| 全局搜索 latency 恒为 0 | 曾因结果页判据过松（把输入页判成结果页）→ 现已只认结果页专属节点，见 §4.2 |
| 采图数量明显少于预期 | 结果页确实只有这么多；或中途 App 弹窗/卡顿导致连续读名失败（`exhausted`） |
| 跑批中途用户的手机输入法变成 ADBKeyboard | 属异常退出未走到恢复；跑批正常结束/停止都会 `restore_ime` 恢复用户原输入法 |
| 想改已跑完的用例状态 | 表格里直接改「执行状态」下拉，或在数据集里点「清除筛选」后重跑 |
| 改完前端/后端没生效 | 部署必须是 `python tools/_deploy_to_9526.py --netdisk` 落到 9526；注意 `pan_search.html` 里 `app.js?v=N`、`app.css?v=N` 的版本号要 bump，否则浏览器缓存旧脚本 |

---

## 9. 与 8767（PC 客户端版）的差异速查

| 维度 | 9526 `netdisk_search`（手机 App） | 8767 `netdisk-pc-search`（PC 客户端） |
|---|---|---|
| 驱动 | ADB（`uiautomator dump` + `input tap/swipe`） | CDP（`Runtime.evaluate` 注入 JS） |
| 是否占用系统输入 | 是（真的点/滑手机屏幕） | 否（纯注入，不抢焦点） |
| 前置依赖 | 手机 USB + 本机助手 + SSH 反向隧道 | 客户端带 `--remote-debugging-port=9222` 启动 |
| 页面路径 | 首页 → 搜索页 → 结果页 → 大图页 | Vue SPA 路由页面 |
| 取文件名 | 大图页点「更多」读 `/image_name` | 结果对象 `fileName` |
| 采图上限 | 默认 60（不封顶） | 默认 100（封顶 100） |
| 执行状态 | 是（完成/中断/失败 + 空） | 是（完成/中断/失败 + 空） |
| 多机隔离 | 每台电脑一个桥端口 + agent 注册表 | 单机单端口 |
