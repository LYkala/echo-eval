import json
from datetime import datetime

# CmdType 映射
CMD_MAP = {
    1000: "开始提示",
    1003: "拉起APP",
    1004: "截图",
    1005: "上传截图",
    1006: "点击",
    1007: "滑动",
    1008: "长按",
    1009: "输入文本",
    1010: "操作完成",
    1011: "操作中断",
    1012: "HOME",
    1013: "BACK",
    1014: "ENTER",
    1018: "查询APP是否安装",
    1019: "上报APP安装结果",
}


def get_cmd_name(cmd):
    return f"{CMD_MAP.get(cmd, '未知操作')}({cmd})"


def parse_timestamp(ts):
    return datetime.fromisoformat(ts.replace("Z", "+00:00"))


def extract_cmdtype(details_str):
    """
    从 Message.Details 中提取 CmdType
    """
    try:
        start = details_str.find("{")
        if start == -1:
            return None

        data = json.loads(details_str[start:])
        return data.get("Payload", {}).get("CmdType")
    except:
        return None


def find_last_task_id(file_path):
    """
    找到时间上最后出现的 TaskIdr
    """
    last_task_id = None
    last_time = None

    with open(file_path, 'r', encoding='utf-8') as f:
        for line in f:
            try:
                log = json.loads(line.strip())
            except:
                continue

            task_id = log.get("TaskIdr")
            if not task_id:
                continue

            ts = parse_timestamp(log["TimeStamp"])

            if last_time is None or ts > last_time:
                last_time = ts
                last_task_id = task_id

    return last_task_id


def extract_task_logs(file_path, task_id):
    """
    提取某个 TaskIdr 的所有有效日志（带 CmdType）
    """
    logs = []

    with open(file_path, 'r', encoding='utf-8') as f:
        for line in f:
            try:
                log = json.loads(line.strip())
            except:
                continue

            if log.get("TaskIdr") != task_id:
                continue

            cmd = extract_cmdtype(log.get("Message", {}).get("Details", ""))
            if cmd is None:
                continue

            logs.append({
                "cmd": cmd,
                "time": parse_timestamp(log["TimeStamp"])
            })

    # 按时间排序（非常关键）
    logs.sort(key=lambda x: x["time"])
    return logs


def build_steps(logs):
    """
    构建 steps（device / cloud 混合结构）
    """
    steps = []
    prev = None

    for item in logs:
        if prev:
            delta = (item["time"] - prev["time"]).total_seconds()

            # 👉 端侧时延：xxx -> 1005
            if item["cmd"] == 1005:
                steps.append({
                    "type": "device",
                    "from": get_cmd_name(prev["cmd"]),
                    "to": get_cmd_name(item["cmd"]),
                    "latency": round(delta, 3)
                })

            # 👉 云测时延：1005 -> next
            if prev["cmd"] == 1005:
                steps.append({
                    "type": "cloud",
                    "from": get_cmd_name(prev["cmd"]),
                    "to": get_cmd_name(item["cmd"]),
                    "latency": round(delta, 3)
                })

        prev = item

    return steps


def analyze_last_task(input_file, output_file):
    # 1️⃣ 找最后一个任务
    last_task_id = find_last_task_id(input_file)

    if not last_task_id:
        print("未找到有效 TaskIdr")
        return

    print(f"分析最后任务: {last_task_id}")

    # 2️⃣ 提取该任务日志
    logs = extract_task_logs(input_file, last_task_id)

    if not logs:
        print("该任务没有有效 CmdType 日志")
        return

    # 3️⃣ 构建 steps
    steps = build_steps(logs)

    # 4️⃣ 输出 JSON
    result = {
        "task_id": last_task_id,
        "steps": steps
    }

    with open(output_file, 'w', encoding='utf-8') as f:
        json.dump(result, f, ensure_ascii=False, indent=2)

    print(f"分析完成，结果已写入: {output_file}")


if __name__ == "__main__":
    analyze_last_task(
        r"D:\eval\SysAgent-personal-n30077937-time_delay_parse\webtest\testApp\webserverV2\testresults\TC-CHAT-001\aisf_2047158695303999489_20260428_171444.log",
        r"/webtest/testApp/webserverV2/testresults/TC-CHAT-001/latency.json"
    )
