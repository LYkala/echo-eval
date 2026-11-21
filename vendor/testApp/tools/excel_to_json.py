#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Excel to JSON Test Case Converter

This tool converts Excel test cases to JSON format for TestApp.

Excel Format:
- Each row represents one test case
- Each row should have columns like: 用例编号, 用例名称, 用例集, 测试步骤...

JSON Format:
- Each test case generates a JSON file
- Files are organized by test case set (folder)
- Chat steps include: click input -> input text -> click send -> wait 15s

Usage:
    python excel_to_json.py <input_excel> <output_dir>

Example:
    python excel_to_json.py testcases.xlsx output/
"""

import os
import sys
import json
import argparse
from datetime import datetime

try:
    import openpyxl
    from openpyxl import load_workbook
except ImportError:
    print("Error: openpyxl library required. Install with: pip install openpyxl")
    sys.exit(1)

# Default configuration
DEFAULT_PACKAGE_NAME = "com.huawei.aisfdemo"  # Default target app package
DEFAULT_INPUT_ID = "com.huawei.aisfdemo:id/editTextMessage"  # Default input field ID
DEFAULT_SEND_ID = "com.huawei.aisfdemo:id/btnSend"  # Default send button ID
DEFAULT_VOICE_TOGGLE_ID = "com.huawei.aisfdemo:id/textVoice"  # Voice/text toggle button
DEFAULT_CHAT_WAIT_MS = 15000  # Default wait time after sending message (15 seconds)
DEFAULT_TIMEOUT_MS = 300000  # Default total test timeout (5 minutes)


def sanitize_filename(name):
    """Convert string to valid filename."""
    # Replace invalid characters
    invalid_chars = '<>:"/\\|?*'
    for char in invalid_chars:
        name = name.replace(char, '_')
    return name.strip()


def create_chat_step(step_index, text, input_id, send_id, wait_ms):
    """Create test steps for a single chat message.

    Steps:
    1. Click input field
    2. Input text
    3. Click send button
    4. Wait for response
    """
    steps = []

    # Step 1: Click input field
    steps.append({
        "stepId": f"click-input-{step_index}",
        "description": f"点击输入框 {step_index}",
        "action": "click",
        "target": {
            "strategy": "id",
            "value": input_id
        }
    })

    # Step 2: Input text
    steps.append({
        "stepId": f"input-message-{step_index}",
        "description": f"输入消息: {text[:30]}..." if len(text) > 30 else f"输入消息: {text}",
        "action": "input",
        "target": {
            "strategy": "id",
            "value": input_id
        },
        "params": {
            "text": text,
            "clearFirst": True
        }
    })

    # Step 3: Click send button
    steps.append({
        "stepId": f"click-send-{step_index}",
        "description": f"点击发送按钮 {step_index}",
        "action": "click",
        "target": {
            "strategy": "id",
            "value": send_id
        }
    })

    # Step 4: Wait for response
    steps.append({
        "stepId": f"wait-response-{step_index}",
        "description": f"等待AI回复 {step_index}",
        "action": "wait",
        "params": {
            "duration": wait_ms
        }
    })

    return steps


def create_test_case(test_id, test_name, description, chat_texts, package_name,
                     input_id, send_id, voice_toggle_id, wait_ms, timeout_ms):
    """Create a complete test case JSON structure."""

    test_case = {
        "version": "1.0.0",
        "testId": test_id,
        "testName": test_name,
        "description": description,
        "targetApp": {
            "packageName": package_name
        },
        "timeout": timeout_ms,
        "steps": []
    }

    # Step 1: Launch app
    test_case["steps"].append({
        "stepId": "launch",
        "description": "启动目标应用",
        "action": "launchApp"
    })

    # Step 2: Wait for app to load
    test_case["steps"].append({
        "stepId": "wait-home",
        "description": "等待首页加载",
        "action": "wait",
        "params": {
            "duration": 1000
        }
    })

    # Step 3: Click voice toggle if specified (to switch to text input mode)
    if voice_toggle_id:
        test_case["steps"].append({
            "stepId": "click-voice-toggle",
            "description": "点击切换到文字输入模式",
            "action": "click",
            "target": {
                "strategy": "id",
                "value": voice_toggle_id
            }
        })
        test_case["steps"].append({
            "stepId": "wait-toggle",
            "description": "等待切换完成",
            "action": "wait",
            "params": {
                "duration": 500
            }
        })

    # Add chat steps for each message
    for i, text in enumerate(chat_texts, 1):
        steps = create_chat_step(i, text, input_id, send_id, wait_ms)
        test_case["steps"].extend(steps)

    # Final step: Take screenshot
    test_case["steps"].append({
        "stepId": "screenshot-result",
        "description": "截图保存结果",
        "action": "screenshot",
        "params": {
            "saveName": "final_result"
        }
    })

    return test_case


def parse_excel(excel_path):
    """Parse Excel file and extract test case data.

    Returns a list of dictionaries with test case info.
    """
    wb = load_workbook(excel_path)
    ws = wb.active

    # Get header row
    headers = []
    for cell in ws[1]:
        headers.append(str(cell.value).strip() if cell.value else "")

    print(f"Excel columns: {headers}")

    # Find column indices
    col_map = {}
    step_columns = []  # Track all step columns separately

    for i, header in enumerate(headers):
        header_lower = header.lower()
        if "用例编号" in header or "case_id" in header_lower or "testid" in header_lower:
            col_map["test_id"] = i
        elif "用例名称" in header or "case_name" in header_lower or "testname" in header_lower:
            col_map["test_name"] = i
        elif "用例集" in header or "folder" in header_lower or "category" in header_lower:
            col_map["folder"] = i
        elif "描述" in header or "desc" in header_lower:
            col_map["description"] = i
        elif "包名" in header or "package" in header_lower:
            col_map["package"] = i
        elif "测试步骤" in header or "步骤" in header:
            step_columns.append(i)

    # Sort step columns by index
    step_columns.sort()

    print(f"Column mapping: {col_map}")
    print(f"Step columns: {step_columns}")

    # Parse each row (skip header)
    test_cases = []
    for row_idx, row in enumerate(ws.iter_rows(min_row=2), start=2):
        row_data = {}

        # Extract known fields
        if "test_id" in col_map:
            row_data["test_id"] = str(row[col_map["test_id"]].value or "").strip()
        else:
            row_data["test_id"] = f"TC-{row_idx - 1:03d}"

        if "test_name" in col_map:
            row_data["test_name"] = str(row[col_map["test_name"]].value or "").strip()
        else:
            row_data["test_name"] = row_data["test_id"]

        if "folder" in col_map:
            row_data["folder"] = str(row[col_map["folder"]].value or "default").strip()
        else:
            row_data["folder"] = "default"

        if "description" in col_map:
            row_data["description"] = str(row[col_map["description"]].value or "").strip()
        else:
            row_data["description"] = row_data["test_name"]

        if "package" in col_map:
            row_data["package"] = str(row[col_map["package"]].value or "").strip()

        # Extract chat steps from step columns
        chat_texts = []
        if step_columns:
            # Use identified step columns
            for col_idx in step_columns:
                text = str(row[col_idx].value).strip() if row[col_idx].value else ""
                if text and text != "None":
                    chat_texts.append(text)
        else:
            # Fallback: extract all non-empty cells after known columns
            known_cols = set(col_map.values())
            for i, cell in enumerate(row):
                if i not in known_cols:
                    text = str(cell.value).strip() if cell.value else ""
                    if text and text != "None":
                        chat_texts.append(text)

        row_data["chat_texts"] = chat_texts

        # Skip empty rows
        if not row_data["test_id"] or not chat_texts:
            print(f"Skipping row {row_idx}: empty test_id or no steps")
            continue

        test_cases.append(row_data)
        print(f"Parsed row {row_idx}: {row_data['test_id']} - {len(chat_texts)} steps")

    return test_cases


def convert_excel_to_json(excel_path, output_dir, config=None):
    """Convert Excel file to JSON test cases."""

    if config is None:
        config = {}

    # Default configuration
    package_name = config.get("package_name", DEFAULT_PACKAGE_NAME)
    input_id = config.get("input_id", DEFAULT_INPUT_ID)
    send_id = config.get("send_id", DEFAULT_SEND_ID)
    voice_toggle_id = config.get("voice_toggle_id", DEFAULT_VOICE_TOGGLE_ID)
    wait_ms = config.get("wait_ms", DEFAULT_CHAT_WAIT_MS)
    timeout_ms = config.get("timeout_ms", DEFAULT_TIMEOUT_MS)

    # Parse Excel
    print(f"\n=== Parsing Excel: {excel_path} ===")
    test_cases = parse_excel(excel_path)

    if not test_cases:
        print("No test cases found in Excel file!")
        return

    print(f"\nFound {len(test_cases)} test cases")

    # Create output directory
    os.makedirs(output_dir, exist_ok=True)

    # Convert each test case
    print(f"\n=== Converting to JSON ===")
    created_files = []

    for tc_data in test_cases:
        # Get package name (use row-specific if provided)
        tc_package = tc_data.get("package") or package_name

        # Create test case JSON
        test_case = create_test_case(
            test_id=tc_data["test_id"],
            test_name=tc_data["test_name"],
            description=tc_data["description"],
            chat_texts=tc_data["chat_texts"],
            package_name=tc_package,
            input_id=input_id,
            send_id=send_id,
            voice_toggle_id=voice_toggle_id,
            wait_ms=wait_ms,
            timeout_ms=timeout_ms
        )

        # Create folder for test case set
        folder_name = sanitize_filename(tc_data["folder"])
        folder_path = os.path.join(output_dir, folder_name)
        os.makedirs(folder_path, exist_ok=True)

        # Write JSON file
        filename = f"{sanitize_filename(tc_data['test_id'])}.json"
        filepath = os.path.join(folder_path, filename)

        with open(filepath, 'w', encoding='utf-8') as f:
            json.dump(test_case, f, ensure_ascii=False, indent=2)

        created_files.append(filepath)
        print(f"Created: {filepath}")

    print(f"\n=== Conversion Complete ===")
    print(f"Created {len(created_files)} JSON files in {output_dir}")

    return created_files


def create_sample_excel(output_path):
    """Create a sample Excel file with the expected format."""
    wb = openpyxl.Workbook()
    ws = wb.active
    ws.title = "Test Cases"

    # Header row
    headers = ["用例编号", "用例名称", "用例集", "描述", "测试步骤1", "测试步骤2", "测试步骤3"]
    for col, header in enumerate(headers, 1):
        ws.cell(row=1, column=col, value=header)

    # Sample data
    sample_data = [
        ["TC-CHAT-001", "聊天测试1", "聊天功能", "测试基本聊天功能", "你好", "今天天气怎么样", "谢谢"],
        ["TC-CHAT-002", "聊天测试2", "聊天功能", "测试智能问答", "帮我写一首诗", "", ""],
        ["TC-QUERY-001", "查询测试", "查询功能", "测试查询功能", "查询明天的天气", "查询北京的温度", ""],
    ]

    for row_idx, row_data in enumerate(sample_data, 2):
        for col_idx, value in enumerate(row_data, 1):
            ws.cell(row=row_idx, column=col_idx, value=value)

    wb.save(output_path)
    print(f"Sample Excel created: {output_path}")


def main():
    parser = argparse.ArgumentParser(
        description="Convert Excel test cases to JSON format for TestApp",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  %(prog)s testcases.xlsx output/
  %(prog)s testcases.xlsx output/ --package com.example.app
  %(prog)s --sample sample.xlsx

Excel Format:
  - Column "用例编号": Test case ID (e.g., TC-CHAT-001)
  - Column "用例名称": Test case name
  - Column "用例集": Test case set (folder name)
  - Column "描述": Test case description
  - Columns "测试步骤1", "测试步骤2"...: Chat messages (one per column)
        """
    )

    parser.add_argument("input", nargs="?", help="Input Excel file path")
    parser.add_argument("output", nargs="?", help="Output directory for JSON files")
    parser.add_argument("--sample", metavar="FILE", help="Create a sample Excel file")
    parser.add_argument("--package", default=DEFAULT_PACKAGE_NAME,
                        help=f"Target app package name (default: {DEFAULT_PACKAGE_NAME})")
    parser.add_argument("--input-id", default=DEFAULT_INPUT_ID,
                        help=f"Input field resource ID (default: {DEFAULT_INPUT_ID})")
    parser.add_argument("--send-id", default=DEFAULT_SEND_ID,
                        help=f"Send button resource ID (default: {DEFAULT_SEND_ID})")
    parser.add_argument("--voice-toggle-id", default=DEFAULT_VOICE_TOGGLE_ID,
                        help=f"Voice/text toggle button ID (default: {DEFAULT_VOICE_TOGGLE_ID})")
    parser.add_argument("--wait", type=int, default=DEFAULT_CHAT_WAIT_MS,
                        help=f"Wait time after each message in ms (default: {DEFAULT_CHAT_WAIT_MS})")
    parser.add_argument("--timeout", type=int, default=DEFAULT_TIMEOUT_MS,
                        help=f"Total test timeout in ms (default: {DEFAULT_TIMEOUT_MS})")

    args = parser.parse_args()

    # Create sample Excel
    if args.sample:
        create_sample_excel(args.sample)
        return

    # Check required arguments
    if not args.input or not args.output:
        parser.print_help()
        print("\nError: input and output arguments are required")
        sys.exit(1)

    # Check input file exists
    if not os.path.exists(args.input):
        print(f"Error: Input file not found: {args.input}")
        sys.exit(1)

    # Configuration
    config = {
        "package_name": args.package,
        "input_id": args.input_id,
        "send_id": args.send_id,
        "voice_toggle_id": args.voice_toggle_id,
        "wait_ms": args.wait,
        "timeout_ms": args.timeout,
    }

    # Convert
    convert_excel_to_json(args.input, args.output, config)


if __name__ == "__main__":
    main()