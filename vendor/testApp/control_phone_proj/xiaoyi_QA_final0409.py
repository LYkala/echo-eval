#!/usr/bin/env python
# -*- coding: utf-8 -*-
# Copyright (c) Huawei Technologies Co., Ltd. 2023. All rights reserved.

import time
import re
import os
import glob
import pandas as pd
from datetime import datetime
from xdevice import DeviceNode
from xdevice import DeviceSelector
from xdevice import EnvPool

from hypium import *
from hypium.model import *
from hypium.action.os_hypium.system import System
from hypium.action.app.launcher import Launcher
from hypium.uidriver.uicomponent import UiComponent

# ====================== 配置区域 ======================
INPUT_DIR = "input"
OUTPUT_DIR = "XIAOYI_output"

os.makedirs(INPUT_DIR, exist_ok=True)
os.makedirs(OUTPUT_DIR, exist_ok=True)
APP_NAME = '小艺'
XIAOYI_APP_NAME = 'com.huawei.hmos.vassistant'
ADD_BTN_PATH = '/root/Navigation/NavBar/NavBarContent/Stack/Column/Column/Tabs/Swiper/TabContent/Navigation/NavBar/TitleBar/Row/__Common__/Button/SymbolGlyph'
RETURN_BTN_PATH = '/root/Navigation/NavigationContent/NavDestination/NavDestinationContent/Stack/Stack[0]/Column/Stack/Stack/RelativeContainer/__Common__[0]/Column/__Common__/Stack/RelativeContainer/__Common__[0]/Stack/Button/SymbolGlyph'
KEYBOARD_INPUT_KEY = 'PushTalkView_AI_keyboard'
INPUT_TEXT_KEY = 'id_text_input'
SEND_BTN_KEY = 'ChatPage_arrow_up_circle'
AI_RESPONSE_PATH = '/root[1]/RelativeContainer/Column/Row/Stack/Column/__Common__/Column/Column/Row[1]/Scroll/Text'
BTN_TYPE = 'SymbolGlyph'

AI_WAIT_TIMEOUT = 120
# ======================================================

def start_xiaoyi(driver):
    print("\n[步骤] 回到桌面...")
    driver.swipe_to_home()
    driver.stop_app(XIAOYI_APP_NAME)
    time.sleep(2)

    print("[步骤] 查找并打开小艺...")
    app = Launcher.find_app(driver, APP_NAME)
    if app is None:
        raise Exception("找不到小艺应用")
    driver.touch(app)
    time.sleep(5)

def add_new_dialog(driver):
    print("[步骤] 新增对话...")
    try:
        add_btn_pos = driver.UiTree.find_component_by_path(path=ADD_BTN_PATH).center
        driver.touch(add_btn_pos)
        time.sleep(2)
    except Exception as e:
        print(f"新增对话失败: {e}")

def switch_to_keyboard_input(driver):
    try:
        print("[步骤] 切换键盘输入...")
        jump_pos = driver.get_component_pos(BY.key(KEYBOARD_INPUT_KEY))
        driver.touch(jump_pos)
        time.sleep(1)
    except:
        print("已是键盘模式")

def find_copy_btn_pos(driver):
    start_time = time.time()
    while True:
        if time.time() - start_time > AI_WAIT_TIMEOUT:
            break

        try:
            components_btn = driver.find_all_components(BY.type(BTN_TYPE))
            data = [i.getBoundsCenter() for i in components_btn]
            window_size = 5
            n = len(data)
            result_idx = -1
            for left in range(n - window_size + 1):
                current_y = data[left].Y
                all_same = True
                for i in range(left, left + window_size):
                    if data[i].Y != current_y:
                        all_same = False
                        break
                if all_same:
                    result_idx = left
                    break
            if result_idx != -1:
                return data[result_idx + 1]
        except:
            pass

        time.sleep(2)
        driver.swipe(UiParam.UP)

    print("[信息] 上滑寻找超时，开始短暂下滑寻找复制按钮...")
    DOWN_SEARCH_TIME = 15
    while True:
        if time.time() - start_time > AI_WAIT_TIMEOUT + DOWN_SEARCH_TIME:
            raise Exception("等待AI回复超时")

        try:
            components_btn = driver.find_all_components(BY.type('SymbolGlyph'))
            data = [i.getBoundsCenter() for i in components_btn]
            window_size = 5
            n = len(data)
            result_idx = -1
            for left in range(n - window_size + 1):
                current_y = data[left].Y
                all_same = True
                for i in range(left, left + window_size):
                    if data[i].Y != current_y:
                        all_same = False
                        break
                if all_same:
                    result_idx = left
                    break
            if result_idx != -1:
                return data[result_idx + 1]
        except:
            pass

        time.sleep(2)
        driver.swipe(UiParam.DOWN)

def send_question(driver, question):
    input_pos = driver.get_component_pos(BY.key(INPUT_TEXT_KEY))
    driver.input_text(component=input_pos, text=question)
    time.sleep(1)

    send_pos = driver.get_component_pos(BY.key(SEND_BTN_KEY))
    driver.touch(send_pos)
    print(f"发送问题: {question}")

    try:
        copy_btn_pos = find_copy_btn_pos(driver)
    except Exception as e:
        if "等待AI回复超时" in str(e):
            raise Exception("等待AI回复超时")
        else:
            raise e

    driver.touch(copy_btn_pos)
    time.sleep(0.8)

    try:
        input_pos = driver.get_component_pos(BY.key(INPUT_TEXT_KEY))
        driver.touch(input_pos)
        ans = driver.UiTree.find_component_by_path(path=AI_RESPONSE_PATH).getText()
        return ans.strip()
    except:
        return "【读取失败】"

def back_to_pre_page(driver):
    try:
        return_btn_pos = driver.UiTree.find_component_by_path(path=RETURN_BTN_PATH).center
        driver.touch(return_btn_pos)
        time.sleep(2)
    except:
        print("返回失败")

# ====================== 工具：拆分多轮语料 ======================
def split_rounds(text):
    lines = re.split(r'\n\d+[.、]', str(text))
    res = []
    for line in lines:
        line = line.strip()
        if not line:
            continue
        line = re.sub(r'^\d+[.、]\s*', '', line)
        res.append(line)
    return res

# ====================== 工具：获取最新文件 ======================
def get_latest_excel_file(directory):
    excel_files = glob.glob(os.path.join(directory, "*.xlsx"))
    if not excel_files:
        return None
    excel_files.sort(key=os.path.getmtime)
    return excel_files[-1]

# ====================== 主逻辑 ======================
def main():
    print(f"📂 自动创建文件夹完成")
    print(f"📥 输入目录：{INPUT_DIR}")
    print(f"📤 输出目录：{OUTPUT_DIR}")

    latest_file = get_latest_excel_file(INPUT_DIR)
    if latest_file is None:
        print(f"\n❌ 错误：在 '{INPUT_DIR}' 文件夹中未找到任何 .xlsx 文件。")
        return

    print(f"\n🔎 检测到最新测试用例文件：{os.path.basename(latest_file)}")
    df_input = pd.read_excel(latest_file)

    # 自动查找包含“用例等级”的列
    case_level_col = None
    for col in df_input.columns:
        if "用例等级" in str(col):
            case_level_col = col
            break

    try:
        driver = UiDriver.connect(connector="hdc", logs_level="info", device_type="phone", agent_mode="bin")
        time.sleep(3)
        start_xiaoyi(driver)

        for idx, row in df_input.iterrows():
            case_id = str(row.get("用例编号", "")).strip()
            case_title = str(row.get("用例标题", "")).strip()
            steps_raw = str(row.get("测试步骤（语料）", "")).strip()
            expect_raw = str(row.get("预期结果", "")).strip()

            s1_scene = str(row.get("S1场景", "")).strip()
            s2_scene = str(row.get("S2场景", "")).strip()
            s3_scene = str(row.get("S3场景", "")).strip()
            case_level = str(row.get(case_level_col, "")).strip() if case_level_col else ""

            if not case_id or not steps_raw:
                continue

            output_file = os.path.join(OUTPUT_DIR, f"{case_id}.xlsx")
            if os.path.exists(output_file):
                print(f"\n⏭️  用例 {case_id} 已存在，直接跳过")
                continue

            print("\n" + "=" * 60)
            print(f"📌 执行用例: {case_id} - {case_title}")
            print(f"🏷️ S1场景: {s1_scene}")
            print(f"🏷️ S2场景: {s2_scene}")
            print(f"🏷️ S3场景: {s3_scene}")
            print(f"⭐ 用例等级: {case_level}")
            print("=" * 60)

            rounds = split_rounds(steps_raw)
            expect_list = split_rounds(expect_raw)

            if not rounds:
                continue

            add_new_dialog(driver)
            switch_to_keyboard_input(driver)

            case_rows = []

            for i, q in enumerate(rounds, 1):
                print(f"\n🔹 轮次{i}：{q}")
                ans = ""
                retry_count = 0
                max_retry = 5
                while retry_count < max_retry:
                    try:
                        ans = send_question(driver, q)
                        print(f"✅ 回复：{ans}")

                        q_strip = str(q).strip()
                        ans_strip = str(ans).strip()
                        if ans_strip == q_strip or '等待AI回复超时' in ans_strip:
                            retry_count += 1
                            print(f"⚠️  检测到问答内容一致，判定执行错误，开始第 {retry_count} 次重试...")
                            time.sleep(2)
                            continue
                        break
                    except Exception as e:
                        ans = f"执行失败：{str(e)}"
                        print(f"❌ {ans}")
                        break

                expect_ans = expect_list[i - 1] if (i - 1) < len(expect_list) else "无预期"
                now_time = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

                # 统一输出格式
                case_rows.append({
                    "用例编号": case_id,
                    "用例标题": case_title,
                    "S1场景": s1_scene,
                    "S2场景": s2_scene,
                    "S3场景": s3_scene,
                    "用例等级": case_level,
                    "用户提问": q,
                    "预期回答": expect_ans,
                    "模型回答": ans,
                    "用例执行时间": now_time
                })
                time.sleep(2)

            case_df = pd.DataFrame(case_rows)
            case_df.to_excel(output_file, index=False)
            print(f"\n✅ 用例 {case_id} 已保存：{output_file}")

            back_to_pre_page(driver)

        driver.swipe_to_home()
        driver.stop_app(XIAOYI_APP_NAME)
        print("\n🎉 所有用例执行完成，小艺已退出")

    except Exception as e:
        print(f"\n💥 脚本执行过程中发生错误: {e}")

if __name__ == "__main__":
    main()