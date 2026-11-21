#!/usr/bin/env python3
"""Build GUI_Youku_iQIYI_Movies_100 Excel dataset (DeviceSDK Mix format)."""

from __future__ import annotations

import random
from pathlib import Path

from openpyxl import Workbook
from openpyxl.styles import Alignment

# Fixed seed so rebuild keeps the same shuffled order.
SHUFFLE_SEED = 20260911

DATASET_NAME = 'GUI_Youku_iQIYI_Movies_100'
WEBSERVER_DIR = Path(__file__).resolve().parents[1]
OUT_DIR = WEBSERVER_DIR / 'excel_datasets' / DATASET_NAME
OUT_XLSX = OUT_DIR / 'dataset.xlsx'

HEADERS = [
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
    '预期结果',
    '预期意图',
    '预期skill',
    '声纹ID',
    '预期操作类型',
    '设备类型',
    '用户',
]

# 50 Youku + 50 iQiyi; common CN movies (titles only — availability varies by app).
YOUKU_MOVIES = [
    '流浪地球2', '满江红', '长津湖', '你好李焕英', '独行月球',
    '封神第一部', '消失的她', '孤注一掷', '热辣滚烫', '第二十条',
    '飞驰人生2', '熊出没·逆转时空', '志愿军：雄兵出击', '坚如磐石', '八角笼中',
    '学爸', '我经过风暴', '莫斯科行动', '三大队', '红毯先生',
    '交换人生', '人生路不熟', '这个杀手不太冷静', '奇迹笨小孩', '我和我的祖国',
    '我和我的父辈', '中国机长', '夺冠', '烈火英雄', '攀登者',
    '流浪地球', '战狼2', '红海行动', '湄公河行动', '唐人街探案3',
    '唐人街探案2', '捉妖记2', '西虹市首富', '羞羞的铁拳', '夏洛特烦恼',
    '疯狂的外星人', '速度与激情10', '阿凡达：水之道', '奥本海默', '芭比',
    '碟中谍7：致命清算', '蜘蛛侠：纵横宇宙', '哪吒之魔童降世', '姜子牙', '大鱼海棠',
]

IQIYI_MOVIES = [
    '长津湖之水门桥', '万里归途', '我和我的家乡', '一点就到家', '送你一朵小红花',
    '拆弹专家2', '急先锋', '刺杀小说家', '侍神令', '悬崖之上',
    '1921', '革命者', '峰爆', '中国医生', '我和我的青春',
    '穿过寒冬拥抱你', '扬名立万', '反贪风暴5', '怒火·重案', '智齿',
    '追虎擒龙', '人潮汹涌', '发财日记', '阳光劫匪', '沐浴之王',
    '我的姐姐', '穿过月亮的旅行', '喜羊羊与灰太狼之筐出未来', '熊出没·伴我熊芯', '小虎墩大英雄',
    '深海', '铃芽之旅', '名侦探柯南：黑铁的鱼影', '灌篮高手', '航海王：红发歌姬',
    '速度与激情9', '疾速追杀4', '疾速追杀3', '黑客帝国：矩阵重启', '沙丘',
    '沙丘2', '花木兰', '狮子王', '星际穿越', '盗梦空间',
    '泰坦尼克号', '阿凡达', '疯狂动物城', '寻梦环游记', '头脑特工队2',
]

STEP_TEMPLATES = [
    '打开{app}，播放电影《{title}》',
    '帮我打开{app}播放电影《{title}》',
    '打开{app}播放电影《{title}》',
    '请打开{app}，播放电影《{title}》',
    '帮我用{app}播放电影《{title}》',
]


def _expected(app: str, title: str) -> str:
    return (
        f'1. 应打开{app}并开始播放电影《{title}》'
        f'（可见播放页/播放控件，能确认该影片已进入播放）'
    )


def build_rows() -> list[list[str]]:
    rows: list[list[str]] = []
    for i, title in enumerate(YOUKU_MOVIES, start=1):
        app = '优酷'
        step = STEP_TEMPLATES[(i - 1) % len(STEP_TEMPLATES)].format(app=app, title=title)
        rows.append([
            f'GUI_YOUKU_{i:03d}',
            f'{app}播放电影《{title}》',
            '',
            app,
            '', '', '', '', '', '', '', '',
            f'1. {step}',
            _expected(app, title),
            '', '', '', '', '', '',
        ])
    for i, title in enumerate(IQIYI_MOVIES, start=1):
        app = '爱奇艺'
        step = STEP_TEMPLATES[(i - 1) % len(STEP_TEMPLATES)].format(app=app, title=title)
        rows.append([
            f'GUI_IQIYI_{i:03d}',
            f'{app}播放电影《{title}》',
            '',
            app,
            '', '', '', '', '', '', '', '',
            f'1. {step}',
            _expected(app, title),
            '', '', '', '', '', '',
        ])
    assert len(rows) == 100, len(rows)
    rng = random.Random(SHUFFLE_SEED)
    rng.shuffle(rows)
    return rows


def main() -> None:
    assert len(YOUKU_MOVIES) == 50 and len(IQIYI_MOVIES) == 50
    rows = build_rows()
    OUT_DIR.mkdir(parents=True, exist_ok=True)

    wb = Workbook()
    ws = wb.active
    ws.title = '用例'
    wrap_cols = {12, 13}  # 测试步骤、预期结果 (0-based)

    for col, header in enumerate(HEADERS, 1):
        ws.cell(1, col, header)

    for r_idx, row in enumerate(rows, 2):
        for c_idx, value in enumerate(row, 1):
            cell = ws.cell(r_idx, c_idx, value)
            if (c_idx - 1) in wrap_cols:
                cell.alignment = Alignment(wrap_text=True, vertical='top')

    wb.save(OUT_XLSX)
    print(f'wrote {OUT_XLSX} cases={len(rows)}')


if __name__ == '__main__':
    main()
