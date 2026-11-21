from hypium.model import UiParam
from hypium.action.host.host import get_resource_path
# !/usr/bin/env python
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
OUTPUT_DIR = "DOUBAO_output"

os.makedirs(INPUT_DIR, exist_ok=True)
os.makedirs(OUTPUT_DIR, exist_ok=True)

# 豆包配置
APP_NAME = '豆包'
DOUBAO_PACKAGE_NAME = 'com.huawei.hmos.vassistant'

# 豆包控件路径
ADD_BTN_PATH = '/android.widget.FrameLayout/android.widget.LinearLayout/android.widget.FrameLayout/android.view.ViewGroup/android.widget.FrameLayout/android.widget.FrameLayout/android.widget.LinearLayout/android.widget.FrameLayout[0]/androidx.viewpager.widget.ViewPager/androidx.recyclerview.widget.RecyclerView/android.widget.FrameLayout/android.view.ViewGroup/android.view.ViewGroup[0]/android.widget.ImageView[2]'
ADD_BTN_KEY = 'com.larus.nova:id/side_bar_create_conversation'
RETURN_BTN_PATH = '/android.widget.FrameLayout/android.widget.LinearLayout/android.widget.FrameLayout/android.view.ViewGroup/android.widget.FrameLayout/android.widget.FrameLayout/androidx.drawerlayout.widget.DrawerLayout/android.widget.FrameLayout/android.widget.FrameLayout/android.view.ViewGroup/android.widget.FrameLayout[0]/android.view.ViewGroup/android.widget.ImageView'
RETURN_BTN_KEY = 'com.larus.nova:id/back_icon'
KEYBOARD_INPUT_KEY = 'com.larus.nova:id/action_input'
INPUT_TEXT_KEY = 'com.larus.nova:id/input_text'
SEND_BTN_KEY = 'com.larus.nova:id/action_send'
AI_RESPONSE_PATH = '/root/RelativeContainer/Column/Row/Stack/Column/__Common__/Column/Column/Row[1]/Scroll/Text'
BTN_TYPE = 'android.widget.ImageView'
down_btn_img = get_resource_path('Hypium/20260414172416.jpeg')
copy_btn_img = get_resource_path('Hypium/20260414190935.jpeg')

AI_WAIT_TIMEOUT = 180
MAX_RETRY=3


# ======================================================

def start_app(driver):
    print("\n[步骤] 回到桌面...")
    driver.swipe_to_home()
    driver.stop_app(DOUBAO_PACKAGE_NAME)
    time.sleep(2)

    print("[步骤] 查找并打开豆包...")
    app = Launcher.find_app(driver, APP_NAME)
    if app is None:
        raise Exception("找不到豆包应用")
    driver.touch(app)
    time.sleep(5)

    print("[步骤] 等待返回按钮并点击...")
    start_wait = time.time()
    while time.time() - start_wait < 30:
        try:
            return_btn_pos = driver.get_component_pos(BY.key(RETURN_BTN_KEY))
            driver.touch(return_btn_pos)
            time.sleep(2)
            print("[步骤] 已点击返回按钮")
            break
        except:
            time.sleep(1)
    else:
        print("[警告] 等待返回按钮超时，继续执行")


def add_new_dialog(driver):
    print("[步骤] 新增对话...")
    try:
        add_btn_pos = driver.get_component_pos(BY.key(ADD_BTN_KEY))
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
            copy_btn_pos0 = driver.find_image(image_path_pc=copy_btn_img, mode='template', similarity=0.8)
            print("🔍 检查是否出现复制按钮...", copy_btn_pos0)


            components_btn = driver.find_all_components(BY.type(BTN_TYPE))
            data = [i.getBoundsCenter() for i in components_btn]
            n = len(data)
            result_idx = -1
            # 自适应检测：支持 4个 或 5个 连续同Y按钮
            for window_size in [5, 4]:  # 先试5个，再试4个
                if n < window_size:
                    continue
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
                    break


            if driver.find_image(image_path_pc=copy_btn_img, mode='template', similarity=0.8) != None and result_idx!=-1:
                print("🔽 检测到复制按钮，优先点击")
                copy_btn_pos = driver.find_image(image_path_pc=copy_btn_img, mode='template', similarity=0.8)
                return ((copy_btn_pos.left + copy_btn_pos.right) // 2, (copy_btn_pos.top + copy_btn_pos.bottom) // 2)

        except:
            pass

        time.sleep(2)

        down_btn_pos = None
        try:
            down_btn_pos = driver.find_image(image_path_pc=down_btn_img, mode='template', similarity=0.8)
        except:
            pass

        print("🔍 检查是否出现下滑按钮...", down_btn_pos)

        if down_btn_pos is not None:
            print("🔽 检测到下滑按钮，优先点击")
            driver.touch_image(image_path_pc=down_btn_img, similarity=0.8)
            time.sleep(1)
            continue

        driver.swipe(UiParam.UP, side=UiParam.RIGHT)


def send_question(driver, question):
    input_pos = driver.get_component_pos(BY.key(INPUT_TEXT_KEY))
    driver.input_text(component=input_pos, text=question)
    time.sleep(1)
    SEND_BTN_KEY2 = 'com.larus.nova:id/action_modify_confirm_new'

    try:
        send_pos = driver.get_component_pos(BY.key(SEND_BTN_KEY))
    except:
        try:
            send_pos = driver.get_component_pos(BY.key(SEND_BTN_KEY2))
        except:
            raise Exception("未找到任何发送按钮")

    driver.touch(send_pos)
    print(f"发送问题: {question}")

    try:
        copy_btn_pos = find_copy_btn_pos(driver)
    except Exception as e:
        if "等待AI回复超时" in str(e):
            raise Exception("等待AI回复超时")
        else:
            raise e
    if copy_btn_pos:
        driver.touch_image(image_path_pc=copy_btn_img, similarity=0.8)
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
        return_btn_pos = driver.get_component_pos(BY.key(RETURN_BTN_KEY))
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

    # ====================== 自动查找包含“用例等级”的列 ======================
    case_level_col = None
    for col in df_input.columns:
        if "用例等级" in str(col):
            case_level_col = col
            break
    # ====================================================================

    try:
        driver = UiDriver.connect(connector="hdc", logs_level="info", device_type="phone", agent_mode="bin")
        time.sleep(3)
        start_app(driver)

        for idx, row in df_input.iterrows():
            case_id = str(row.get("用例编号", "")).strip()
            case_title = str(row.get("用例标题", "")).strip()
            steps_raw = str(row.get("测试步骤（语料）", "")).strip()
            expect_raw = str(row.get("预期结果", "")).strip()

            s1_scene = str(row.get("S1场景", "")).strip()
            s2_scene = str(row.get("S2场景", "")).strip()
            s3_scene = str(row.get("S3场景", "")).strip()

            # 自动读取找到的用例等级列
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
                max_retry = MAX_RETRY
                while retry_count < max_retry:
                    try:
                        ans = send_question(driver, q)
                        print(f"✅ 回复：{ans}")

                        q_strip = str(q).strip()
                        ans_strip = str(ans).strip()
                        if ans_strip == q_strip or '执行失败：等待AI回复超时' in ans_strip:
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
        driver.stop_app(DOUBAO_PACKAGE_NAME)
        print("\n🎉 所有用例执行完成，豆包已退出")

    except Exception as e:
        print(f"\n💥 脚本执行过程中发生错误: {e}")


if __name__ == "__main__":
    main()