#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""本地小艺 / 豆包 QA 平台。风格对齐 9527，数据来自已执行的打分结果。"""
import sqlite3
from datetime import datetime
from pathlib import Path

from flask import Flask, abort, flash, jsonify, redirect, render_template, request, send_file, url_for
from openpyxl import load_workbook

ROOT = Path(__file__).resolve().parent
DB_PATH = ROOT / "data" / "qa.db"
IMPORT_DIR = ROOT / "_import_src"
PASS_LINE = 60.0

BUILTIN_TASKS = (
    {
        "task_id": "xiaoyi_200_0429",
        "name": "小艺 200 用例打分",
        "product": "xiaoyi",
        "source_file": "小艺200用例打分结果0429.xlsx",
        "dataset": "小艺200用例打分0429",
        "kind": "xiaoyi_detail",
    },
    {
        "task_id": "xiaoyi_memory_458",
        "name": "小艺记忆 458+94 评测",
        "product": "xiaoyi",
        "source_file": "458+94记忆小艺执行最终完整评测表.xlsx",
        "dataset": "小艺记忆458+94",
        "kind": "xiaoyi_memory",
    },
    {
        "task_id": "doubao_1162",
        "name": "豆包 1162 用例打分",
        "product": "doubao",
        "source_file": "doubaoAllScoreResult_Cases1162.xlsx",
        "dataset": "豆包1162打分",
        "kind": "doubao_score",
    },
    {
        "task_id": "xiaoyi_run_20260411",
        "name": "小艺执行结果 20260411",
        "product": "xiaoyi",
        "source_file": "XIAOYIresult-20260411-case111-line579.xlsx",
        "dataset": "小艺执行20260411",
        "kind": "dialogue",
    },
    {
        "task_id": "doubao_run_20260422",
        "name": "豆包执行结果 20260422",
        "product": "doubao",
        "source_file": "DOUBAOresult-20260422-case276-line1365.xlsx",
        "dataset": "豆包执行20260422",
        "kind": "dialogue",
    },
)

PRODUCT_LABEL = {"xiaoyi": "小艺", "doubao": "豆包"}
OS_LABEL = {"harmony": "鸿蒙", "android": "安卓"}
DEVICE_COUNTS = list(range(1, 11))


def create_app():
    app = Flask(__name__, template_folder=str(ROOT / "templates"))
    app.secret_key = "qa-platform-local"
    ROOT.joinpath("data").mkdir(parents=True, exist_ok=True)
    init_db()
    import_builtin_results()
    ensure_datasets()

    @app.context_processor
    def inject():
        return {"product_label": PRODUCT_LABEL, "pass_line": PASS_LINE}

    @app.get("/")
    def home():
        with connect() as db:
            tasks = [enrich_task(db, row) for row in db.execute(
                "SELECT * FROM tasks ORDER BY builtin DESC, created_at DESC"
            )]
            counts = {
                "xiaoyi": db.execute("SELECT COUNT(*) c FROM cases WHERE product='xiaoyi'").fetchone()["c"],
                "doubao": db.execute("SELECT COUNT(*) c FROM cases WHERE product='doubao'").fetchone()["c"],
                "rounds": db.execute("SELECT COUNT(*) c FROM rounds").fetchone()["c"],
            }
        return render_template("home.html", page="home", tasks=tasks, counts=counts)

    @app.get("/cases")
    def cases_page():
        with connect() as db:
            datasets = db.execute(
                "SELECT * FROM datasets ORDER BY builtin DESC, updated_at DESC"
            ).fetchall()
        return render_template("cases.html", page="cases", datasets=datasets)

    @app.post("/api/datasets")
    def create_dataset():
        name = request.form.get("name", "").strip()
        product = request.form.get("product", "")
        file = request.files.get("file")
        if not name or product not in PRODUCT_LABEL or file is None or not file.filename:
            return jsonify(success=False, message="请填写数据集名称、对象，并上传 xlsx"), 400
        if not file.filename.lower().endswith((".xlsx", ".xlsm")):
            return jsonify(success=False, message="只支持 xlsx / xlsm"), 400
        dest_root = ROOT / "data" / "datasets"
        dest_root.mkdir(parents=True, exist_ok=True)
        dest = dest_root / f"{name}.xlsx"
        file.save(dest)
        count = count_sheet_cases(dest)
        now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        with connect() as db:
            db.execute(
                """INSERT INTO datasets(name, product, filename, path, case_count, updated_at, builtin)
                   VALUES (?, ?, ?, ?, ?, ?, 0)
                   ON CONFLICT(name) DO UPDATE SET
                     product=excluded.product, filename=excluded.filename, path=excluded.path,
                     case_count=excluded.case_count, updated_at=excluded.updated_at""",
                (name, product, file.filename, str(dest), count, now),
            )
            db.commit()
        return jsonify(success=True, case_count=count)

    @app.get("/api/datasets/<name>/download")
    def download_dataset(name):
        row = dataset_row(name)
        if row is None:
            abort(404)
        return send_file(row["path"], as_attachment=True, download_name=row["filename"])

    @app.get("/api/datasets/<name>/sheet")
    def dataset_sheet(name):
        row = dataset_row(name)
        if row is None:
            abort(404)
        page = max(1, int(request.args.get("page", 1) or 1))
        per_page = 20
        headers, all_rows = read_sheet(Path(row["path"]))
        total = len(all_rows)
        pages = max(1, (total + per_page - 1) // per_page)
        page = min(page, pages)
        start = (page - 1) * per_page
        return jsonify(
            success=True, name=name, headers=headers,
            rows=all_rows[start:start + per_page],
            total=total, page=page, pages=pages,
        )

    @app.post("/api/datasets/<name>/sheet")
    def save_dataset_sheet(name):
        row = dataset_row(name)
        if row is None:
            abort(404)
        payload = request.get_json(force=True) or {}
        page = max(1, int(payload.get("page") or 1))
        edited = payload.get("rows") or []
        path = Path(row["path"])
        per_page = 20
        wb = load_workbook(path)
        ws = wb[wb.sheetnames[0]]
        start = (page - 1) * per_page + 2
        for offset, values in enumerate(edited):
            for col, value in enumerate(values, start=1):
                ws.cell(start + offset, col, value)
        wb.save(path)
        count = count_sheet_cases(path)
        now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        with connect() as db:
            db.execute(
                "UPDATE datasets SET case_count=?, updated_at=? WHERE name=?",
                (count, now, name),
            )
            db.commit()
        return jsonify(success=True, case_count=count)

    @app.delete("/api/datasets/<name>")
    def delete_dataset(name):
        row = dataset_row(name)
        if row is None:
            abort(404)
        if row["builtin"]:
            return jsonify(success=False, message="已导入的历史数据集保留"), 400
        path = Path(row["path"])
        if path.exists():
            path.unlink()
        with connect() as db:
            db.execute("DELETE FROM datasets WHERE name=?", (name,))
            db.commit()
        return jsonify(success=True)

    @app.get("/cases/browse")
    def cases_browse():
        product = request.args.get("product", "")
        q = request.args.get("q", "").strip()
        s2 = request.args.get("s2", "").strip()
        page = max(1, int(request.args.get("page", 1) or 1))
        per_page = 20
        where = []
        args = []
        if product in PRODUCT_LABEL:
            where.append("c.product = ?")
            args.append(product)
        if s2:
            where.append("c.s2 = ?")
            args.append(s2)
        if q:
            where.append("(c.case_id LIKE ? OR c.title LIKE ? OR c.s1 LIKE ? OR c.s3 LIKE ?)")
            like = f"%{q}%"
            args.extend([like, like, like, like])
        clause = (" WHERE " + " AND ".join(where)) if where else ""
        with connect() as db:
            total = db.execute(f"SELECT COUNT(*) c FROM cases c{clause}", args).fetchone()["c"]
            pages = max(1, (total + per_page - 1) // per_page)
            page = min(page, pages)
            rows = db.execute(
                f"""SELECT c.*,
                    (SELECT COUNT(*) FROM rounds r WHERE r.case_pk=c.id) round_count,
                    (SELECT AVG(r.score) FROM rounds r WHERE r.case_pk=c.id AND r.score IS NOT NULL) avg_score
                    FROM cases c{clause}
                    ORDER BY c.product, c.case_id
                    LIMIT ? OFFSET ?""",
                [*args, per_page, (page - 1) * per_page],
            ).fetchall()
            scenes = [r["s2"] for r in db.execute(
                "SELECT DISTINCT s2 FROM cases WHERE s2 IS NOT NULL AND s2 != '' ORDER BY s2"
            )]
        return render_template(
            "cases_browse.html", page="cases", rows=rows, total=total, pages=pages,
            page_no=page, product=product, q=q, s2=s2, scenes=scenes,
        )

    @app.post("/cases/upload")
    def upload_cases():
        product = request.form.get("product", "")
        file = request.files.get("file")
        if product not in PRODUCT_LABEL or file is None or not file.filename:
            flash("请选择小艺或豆包，并上传 xlsx")
            return redirect(url_for("cases_page"))
        dest = ROOT / "data" / "upload" / file.filename
        dest.parent.mkdir(parents=True, exist_ok=True)
        file.save(dest)
        added = import_case_workbook(dest, product)
        flash(f"已导入 {added} 条{PRODUCT_LABEL[product]}用例")
        return redirect(url_for("cases_page", product=product))

    @app.get("/cases/<product>/<case_id>")
    def case_detail(product, case_id):
        if product not in PRODUCT_LABEL:
            abort(404)
        task_id = request.args.get("task", "")
        with connect() as db:
            case = db.execute(
                "SELECT * FROM cases WHERE product=? AND case_id=?", (product, case_id)
            ).fetchone()
            if case is None:
                abort(404)
            sql = "SELECT * FROM rounds WHERE case_pk=?"
            args = [case["id"]]
            if task_id:
                sql += " AND task_id=?"
                args.append(task_id)
            sql += " ORDER BY task_id, round_no, id"
            rounds = db.execute(sql, args).fetchall()
            tasks = db.execute(
                """SELECT DISTINCT t.task_id, t.name FROM rounds r
                   JOIN tasks t ON t.task_id=r.task_id
                   WHERE r.case_pk=? ORDER BY t.created_at""",
                (case["id"],),
            ).fetchall()
        return render_template(
            "case_detail.html", page="cases", case=case, rounds=rounds,
            tasks=tasks, task_id=task_id,
        )

    @app.get("/tasks")
    def tasks_page():
        q = request.args.get("q", "").strip()
        with connect() as db:
            sql = "SELECT * FROM tasks"
            args = []
            if q:
                like = f"%{q}%"
                clauses = [
                    "task_id LIKE ?",
                    "name LIKE ?",
                    "IFNULL(dataset_name,'') LIKE ?",
                    "IFNULL(status,'') LIKE ?",
                    "IFNULL(os_name,'') LIKE ?",
                    "IFNULL(product,'') LIKE ?",
                ]
                args = [like, like, like, like, like, like]
                product_keys = [key for key, label in PRODUCT_LABEL.items() if q in label]
                os_keys = [key for key, label in OS_LABEL.items() if q in label]
                if product_keys:
                    clauses.append("product IN (%s)" % ",".join("?" * len(product_keys)))
                    args.extend(product_keys)
                if os_keys:
                    clauses.append("os_name IN (%s)" % ",".join("?" * len(os_keys)))
                    args.extend(os_keys)
                sql += " WHERE " + " OR ".join(clauses)
            sql += " ORDER BY builtin DESC, created_at DESC"
            tasks = [enrich_task(db, row) for row in db.execute(sql, args)]
            datasets = [dict(row) for row in db.execute(
                "SELECT name, product, case_count FROM datasets ORDER BY name"
            )]
        return render_template(
            "tasks.html", page="tasks", tasks=tasks, datasets=datasets, q=q,
        )

    @app.post("/api/tasks")
    def create_task():
        payload = request.get_json(force=True) or {}
        product = payload.get("product", "")
        dataset = (payload.get("dataset") or "").strip()
        os_name = payload.get("os", "")
        device_count = parse_device_count(payload.get("device_count"))
        if product not in PRODUCT_LABEL or not dataset:
            return jsonify(success=False, message="请选择对象和 Excel 数据集"), 400
        if os_name not in OS_LABEL:
            return jsonify(success=False, message="请选择鸿蒙或安卓"), 400
        if device_count is None:
            return jsonify(success=False, message="请选择并发真机数量（1 到 10）"), 400
        row = dataset_row(dataset)
        if row is None:
            return jsonify(success=False, message="数据集不存在"), 400
        if row["product"] != product:
            return jsonify(success=False, message="数据集对象和任务对象不一致"), 400
        task_id = "task_" + datetime.now().strftime("%Y%m%d_%H%M%S")
        now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        with connect() as db:
            db.execute(
                """INSERT INTO tasks(task_id, name, product, os_name, device_count, source_file, dataset_name, status, created_at, builtin)
                   VALUES (?, ?, ?, ?, ?, ?, ?, '待执行', ?, 0)""",
                (task_id, dataset, product, os_name, device_count, row["filename"], dataset, now),
            )
            db.commit()
        return jsonify(success=True, task_id=task_id, message="任务已创建。真机执行仍使用 control_phone_proj 里的脚本。")

    @app.post("/api/tasks/<task_id>")
    def update_task(task_id):
        payload = request.get_json(force=True) or {}
        name = (payload.get("name") or "").strip()
        product = payload.get("product", "")
        dataset = (payload.get("dataset") or "").strip()
        os_name = payload.get("os", "")
        device_count = parse_device_count(payload.get("device_count"))
        if not name:
            return jsonify(success=False, message="请填写任务名称"), 400
        if product not in PRODUCT_LABEL:
            return jsonify(success=False, message="请选择对象"), 400
        if os_name not in OS_LABEL:
            return jsonify(success=False, message="请选择鸿蒙或安卓"), 400
        if device_count is None:
            return jsonify(success=False, message="请选择并发真机数量（1 到 10）"), 400
        row = dataset_row(dataset)
        if row is None:
            return jsonify(success=False, message="数据集不存在"), 400
        if row["product"] != product:
            return jsonify(success=False, message="数据集对象和任务对象不一致"), 400
        with connect() as db:
            found = db.execute("SELECT task_id FROM tasks WHERE task_id=?", (task_id,)).fetchone()
            if found is None:
                return jsonify(success=False, message="任务不存在"), 404
            db.execute(
                """UPDATE tasks SET name=?, product=?, os_name=?, device_count=?, dataset_name=?
                   WHERE task_id=?""",
                (name, product, os_name, device_count, dataset, task_id),
            )
            db.commit()
        return jsonify(success=True)

    @app.post("/tasks/<task_id>/stop")
    def stop_task(task_id):
        with connect() as db:
            row = db.execute("SELECT status FROM tasks WHERE task_id=?", (task_id,)).fetchone()
            if row is None:
                abort(404)
            if row["status"] not in ("待执行", "执行中"):
                flash("只有待执行或执行中的任务可以停止")
                return redirect(url_for("tasks_page", q=request.values.get("q", "")))
            db.execute("UPDATE tasks SET status='已停止' WHERE task_id=?", (task_id,))
            db.commit()
        return redirect(url_for("tasks_page", q=request.values.get("q", "")))

    @app.post("/tasks/<task_id>/restart")
    def restart_task(task_id):
        with connect() as db:
            row = db.execute("SELECT status FROM tasks WHERE task_id=?", (task_id,)).fetchone()
            if row is None:
                abort(404)
            if row["status"] in ("待执行", "执行中"):
                flash("任务进行中，请先停止后再重启")
                return redirect(url_for("tasks_page", q=request.values.get("q", "")))
            db.execute("UPDATE tasks SET status='待执行' WHERE task_id=?", (task_id,))
            db.commit()
        return redirect(url_for("tasks_page", q=request.values.get("q", "")))

    @app.post("/tasks/<task_id>/delete")
    def delete_task(task_id):
        with connect() as db:
            row = db.execute("SELECT builtin FROM tasks WHERE task_id=?", (task_id,)).fetchone()
            if row is None:
                abort(404)
            if row["builtin"]:
                db.execute(
                    "INSERT OR IGNORE INTO removed_tasks(task_id) VALUES (?)",
                    (task_id,),
                )
            db.execute("DELETE FROM rounds WHERE task_id=?", (task_id,))
            db.execute("DELETE FROM tasks WHERE task_id=?", (task_id,))
            db.commit()
        return redirect(url_for("tasks_page", q=request.values.get("q", "")))

    @app.get("/tasks/<task_id>")
    def task_detail(task_id):
        q = request.args.get("q", "").strip()
        page = max(1, int(request.args.get("page", 1) or 1))
        per_page = int(request.args.get("per_page", 20) or 20)
        if per_page not in (10, 20, 50, 100):
            per_page = 20
        with connect() as db:
            task = db.execute("SELECT * FROM tasks WHERE task_id=?", (task_id,)).fetchone()
            if task is None:
                abort(404)
            task = enrich_task(db, task)
            where = "r.task_id=?"
            args = [task_id]
            if q:
                where += " AND (c.case_id LIKE ? OR IFNULL(c.title,'') LIKE ?)"
                args.extend([f"%{q}%", f"%{q}%"])
            summary = db.execute(
                f"""SELECT COUNT(*) total_cases,
                           SUM(CASE WHEN avg_score IS NULL THEN 1 ELSE 0 END) unevaluated,
                           SUM(CASE WHEN avg_score >= ? THEN 1 ELSE 0 END) passed,
                           SUM(CASE WHEN avg_score IS NOT NULL AND avg_score < ? THEN 1 ELSE 0 END) failed
                    FROM (
                        SELECT AVG(r.score) avg_score
                        FROM rounds r JOIN cases c ON c.id=r.case_pk
                        WHERE {where}
                        GROUP BY c.id
                    )""",
                [PASS_LINE, PASS_LINE, *args],
            ).fetchone()
            total = summary["total_cases"] or 0
            pages = max(1, (total + per_page - 1) // per_page)
            page = min(page, pages)
            rows = db.execute(
                f"""SELECT c.product, c.case_id, c.title,
                    COUNT(r.id) round_count, AVG(r.score) avg_score,
                    MIN(NULLIF(r.executed_at, '')) started_at,
                    MAX(NULLIF(r.executed_at, '')) ended_at,
                    (
                        SELECT r2.answer FROM rounds r2
                        WHERE r2.case_pk=c.id AND r2.task_id=?
                          AND IFNULL(r2.answer, '') != ''
                        ORDER BY r2.round_no DESC, r2.id DESC LIMIT 1
                    ) answer_preview
                    FROM rounds r JOIN cases c ON c.id=r.case_pk
                    WHERE {where}
                    GROUP BY c.id
                    ORDER BY c.case_id
                    LIMIT ? OFFSET ?""",
                [task_id, *args, per_page, (page - 1) * per_page],
            ).fetchall()
        return render_template(
            "task_detail.html", page="tasks", task=task, rows=rows,
            total=total, pages=pages, page_no=page, per_page=per_page, q=q,
            summary=summary,
        )

    @app.get("/api/tasks/<task_id>/cases/<case_id>/replies")
    def task_case_replies(task_id, case_id):
        with connect() as db:
            task = db.execute(
                "SELECT product FROM tasks WHERE task_id=?", (task_id,)
            ).fetchone()
            if task is None:
                return jsonify(success=False, message="任务不存在"), 404
            rows = db.execute(
                """SELECT r.round_no, r.question, r.expected, r.answer, r.executed_at,
                          r.task_completion, r.correctness, r.relevance, r.coherence,
                          r.intent, r.score
                   FROM rounds r JOIN cases c ON c.id=r.case_pk
                   WHERE r.task_id=? AND c.case_id=? AND c.product=?
                   ORDER BY r.round_no, r.id""",
                (task_id, case_id, task["product"]),
            ).fetchall()
        return jsonify(success=True, replies=[dict(row) for row in rows])

    return app


def connect():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn


def init_db():
    with connect() as db:
        db.executescript(
            """
            CREATE TABLE IF NOT EXISTS datasets (
                name TEXT PRIMARY KEY,
                product TEXT NOT NULL,
                filename TEXT NOT NULL,
                path TEXT NOT NULL,
                case_count INTEGER NOT NULL DEFAULT 0,
                updated_at TEXT NOT NULL,
                builtin INTEGER NOT NULL DEFAULT 0
            );
            CREATE TABLE IF NOT EXISTS cases (
                id INTEGER PRIMARY KEY,
                product TEXT NOT NULL,
                case_id TEXT NOT NULL,
                title TEXT DEFAULT '',
                s1 TEXT DEFAULT '',
                s2 TEXT DEFAULT '',
                s3 TEXT DEFAULT '',
                level TEXT DEFAULT '',
                UNIQUE(product, case_id)
            );
            CREATE TABLE IF NOT EXISTS tasks (
                task_id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                product TEXT NOT NULL,
                source_file TEXT DEFAULT '',
                status TEXT NOT NULL,
                created_at TEXT NOT NULL,
                builtin INTEGER NOT NULL DEFAULT 0
            );
            CREATE TABLE IF NOT EXISTS rounds (
                id INTEGER PRIMARY KEY,
                case_pk INTEGER NOT NULL,
                task_id TEXT NOT NULL,
                round_no INTEGER NOT NULL,
                question TEXT DEFAULT '',
                expected TEXT DEFAULT '',
                answer TEXT DEFAULT '',
                executed_at TEXT DEFAULT '',
                task_completion REAL,
                correctness REAL,
                relevance REAL,
                coherence REAL,
                intent REAL,
                score REAL,
                FOREIGN KEY(case_pk) REFERENCES cases(id)
            );
            """
        )
        cols = {row[1] for row in db.execute("PRAGMA table_info(tasks)")}
        if "dataset_name" not in cols:
            db.execute("ALTER TABLE tasks ADD COLUMN dataset_name TEXT DEFAULT ''")
        if "os_name" not in cols:
            db.execute("ALTER TABLE tasks ADD COLUMN os_name TEXT DEFAULT ''")
        if "device_count" not in cols:
            db.execute("ALTER TABLE tasks ADD COLUMN device_count INTEGER DEFAULT 0")
        db.execute(
            """CREATE TABLE IF NOT EXISTS removed_tasks (
                task_id TEXT PRIMARY KEY
            )"""
        )
        db.commit()


def parse_device_count(value):
    try:
        count = int(value)
    except (TypeError, ValueError):
        return None
    if count not in DEVICE_COUNTS:
        return None
    return count


def num(value):
    if value is None or value == "":
        return None
    try:
        return round(float(value), 2)
    except (TypeError, ValueError):
        return None


def mean(values):
    nums = [v for v in values if v is not None]
    if not nums:
        return None
    return round(sum(nums) / len(nums), 2)


def cell(row, idx):
    if idx is None or idx >= len(row) or row[idx] is None:
        return ""
    return str(row[idx]).strip()


def header_map(header):
    return {str(name).strip(): i for i, name in enumerate(header) if name is not None}


def upsert_case(db, product, case_id, title="", s1="", s2="", s3="", level=""):
    db.execute(
        """INSERT INTO cases(product, case_id, title, s1, s2, s3, level)
           VALUES (?, ?, ?, ?, ?, ?, ?)
           ON CONFLICT(product, case_id) DO UPDATE SET
             title=CASE WHEN excluded.title!='' THEN excluded.title ELSE cases.title END,
             s1=CASE WHEN excluded.s1!='' THEN excluded.s1 ELSE cases.s1 END,
             s2=CASE WHEN excluded.s2!='' THEN excluded.s2 ELSE cases.s2 END,
             s3=CASE WHEN excluded.s3!='' THEN excluded.s3 ELSE cases.s3 END,
             level=CASE WHEN excluded.level!='' THEN excluded.level ELSE cases.level END
        """,
        (product, case_id, title, s1, s2, s3, level),
    )
    return db.execute(
        "SELECT id FROM cases WHERE product=? AND case_id=?", (product, case_id)
    ).fetchone()["id"]


def import_builtin_results():
    with connect() as db:
        have = {row["task_id"] for row in db.execute("SELECT task_id FROM tasks WHERE builtin=1")}
        removed = {row["task_id"] for row in db.execute("SELECT task_id FROM removed_tasks")}
        for spec in BUILTIN_TASKS:
            if spec["task_id"] in have or spec["task_id"] in removed:
                continue
            path = IMPORT_DIR / spec["source_file"]
            if not path.exists():
                continue
            load_result_file(db, path, spec)
        db.commit()


def load_result_file(db, path, spec):
    wb = load_workbook(path, read_only=True, data_only=True)
    try:
        if spec["kind"] == "xiaoyi_detail":
            ws = wb["用例详情+评分"] if "用例详情+评分" in wb.sheetnames else wb[wb.sheetnames[0]]
            import_xiaoyi_detail(db, ws, spec)
        elif spec["kind"] == "xiaoyi_memory":
            import_xiaoyi_memory(db, wb[wb.sheetnames[0]], spec)
        elif spec["kind"] == "dialogue":
            import_dialogue(db, wb[wb.sheetnames[0]], spec)
        else:
            import_doubao(db, wb[wb.sheetnames[0]], spec)
    finally:
        wb.close()


def ensure_task(db, spec, created_at):
    db.execute(
        """INSERT INTO tasks(task_id, name, product, source_file, dataset_name, status, created_at, builtin)
           VALUES (?, ?, ?, ?, ?, '已完成', ?, 1)
           ON CONFLICT(task_id) DO UPDATE SET
             name=excluded.name, source_file=excluded.source_file,
             dataset_name=excluded.dataset_name, created_at=excluded.created_at""",
        (spec["task_id"], spec["name"], spec["product"], spec["source_file"],
         spec.get("dataset", ""), created_at),
    )


def import_xiaoyi_detail(db, ws, spec):
    rows = ws.iter_rows(values_only=True)
    header = header_map(next(rows))
    latest = ""
    count = 0
    for raw in rows:
        case_id = cell(raw, header.get("用例编号"))
        if not case_id:
            continue
        case_pk = upsert_case(
            db, "xiaoyi", case_id,
            title=cell(raw, header.get("用例标题")),
            s1=cell(raw, header.get("S1场景")),
            s2=cell(raw, header.get("S2场景")),
            s3=cell(raw, header.get("S3场景")),
            level=cell(raw, header.get("用例等级")),
        )
        executed = cell(raw, header.get("用例执行时间"))
        if executed > latest:
            latest = executed
        scores = {
            "task_completion": num(raw[header["task_completion_score"]]) if "task_completion_score" in header else None,
            "correctness": num(raw[header["correctness_score"]]) if "correctness_score" in header else None,
            "relevance": num(raw[header["relevance_score"]]) if "relevance_score" in header else None,
            "coherence": num(raw[header["contextual_coherence_score"]]) if "contextual_coherence_score" in header else None,
            "intent": num(raw[header["e2e_dialogue_intent_accuracy"]]) if "e2e_dialogue_intent_accuracy" in header else None,
        }
        round_no = num(raw[header["轮次"]]) if "轮次" in header else None
        db.execute(
            """INSERT INTO rounds(case_pk, task_id, round_no, question, expected, answer, executed_at,
               task_completion, correctness, relevance, coherence, intent, score)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            (
                case_pk, spec["task_id"], int(round_no or 0),
                cell(raw, header.get("用户提问")),
                cell(raw, header.get("预期回答")),
                cell(raw, header.get("模型回答")),
                executed,
                scores["task_completion"], scores["correctness"], scores["relevance"],
                scores["coherence"], scores["intent"],
                mean(scores.values()),
            ),
        )
        count += 1
    if count:
        ensure_task(db, spec, latest or "2026-04-29")


def import_dialogue(db, ws, spec):
    rows = ws.iter_rows(values_only=True)
    header = header_map(next(rows))
    latest = ""
    count = 0
    product = spec["product"]
    for raw in rows:
        case_id = cell(raw, header.get("用例编号"))
        if not case_id:
            continue
        case_pk = upsert_case(
            db, product, case_id,
            title=cell(raw, header.get("用例标题")),
            s1=cell(raw, header.get("S1场景")),
            s2=cell(raw, header.get("S2场景")),
            s3=cell(raw, header.get("S3场景")),
            level=cell(raw, header.get("用例等级")),
        )
        executed = cell(raw, header.get("用例执行时间"))
        if executed > latest:
            latest = executed
        round_no = num(raw[header["轮次"]]) if "轮次" in header else None
        db.execute(
            """INSERT INTO rounds(case_pk, task_id, round_no, question, expected, answer, executed_at,
               task_completion, correctness, relevance, coherence, intent, score)
               VALUES (?, ?, ?, ?, ?, ?, ?, NULL, NULL, NULL, NULL, NULL, NULL)""",
            (
                case_pk, spec["task_id"], int(round_no or 0),
                cell(raw, header.get("用户提问")),
                cell(raw, header.get("预期回答")),
                cell(raw, header.get("模型回答")),
                executed,
            ),
        )
        count += 1
    if count:
        ensure_task(db, spec, latest or "历史结果")


def import_xiaoyi_memory(db, ws, spec):
    rows = ws.iter_rows(values_only=True)
    header = header_map(next(rows))
    count = 0
    for raw in rows:
        case_id = cell(raw, header.get("case_id"))
        if not case_id:
            continue
        case_pk = upsert_case(db, "xiaoyi", case_id, s2=cell(raw, header.get("S2场景_x")))
        scores = {
            "task_completion": num(raw[header["task_completion"]]) if "task_completion" in header else None,
            "correctness": num(raw[header["correctness"]]) if "correctness" in header else None,
            "relevance": num(raw[header["relevance"]]) if "relevance" in header else None,
            "coherence": num(raw[header["contextual_coherence"]]) if "contextual_coherence" in header else None,
            "intent": num(raw[header["e2e_dialogue_intent_accuracy"]]) if "e2e_dialogue_intent_accuracy" in header else None,
        }
        db.execute(
            """INSERT INTO rounds(case_pk, task_id, round_no, question, expected, answer, executed_at,
               task_completion, correctness, relevance, coherence, intent, score)
               VALUES (?, ?, 1, '', '', '', '', ?, ?, ?, ?, ?, ?)""",
            (
                case_pk, spec["task_id"],
                scores["task_completion"], scores["correctness"], scores["relevance"],
                scores["coherence"], scores["intent"], mean(scores.values()),
            ),
        )
        count += 1
    if count:
        ensure_task(db, spec, "历史结果")


def import_doubao(db, ws, spec):
    rows = ws.iter_rows(values_only=True)
    header = header_map(next(rows))
    count = 0
    for raw in rows:
        case_id = cell(raw, header.get("用例编号"))
        if not case_id:
            continue
        case_pk = upsert_case(db, "doubao", case_id, s2=cell(raw, header.get("S2场景")))
        weighted = num(raw[header["加权得分"]]) if "加权得分" in header else None
        scores = {
            "task_completion": num(raw[header["完成率"]]) if "完成率" in header else None,
            "correctness": num(raw[header["正确性"]]) if "正确性" in header else None,
            "relevance": num(raw[header["相关性"]]) if "相关性" in header else None,
            "coherence": num(raw[header["上下文连贯性"]]) if "上下文连贯性" in header else None,
            "intent": num(raw[header["意图准确率"]]) if "意图准确率" in header else None,
        }
        db.execute(
            """INSERT INTO rounds(case_pk, task_id, round_no, question, expected, answer, executed_at,
               task_completion, correctness, relevance, coherence, intent, score)
               VALUES (?, ?, 1, '', '', '', '', ?, ?, ?, ?, ?, ?)""",
            (
                case_pk, spec["task_id"],
                scores["task_completion"], scores["correctness"], scores["relevance"],
                scores["coherence"], scores["intent"],
                weighted if weighted is not None else mean(list(scores.values())),
            ),
        )
        count += 1
    if count:
        ensure_task(db, spec, "历史结果")


def import_case_workbook(path, product):
    wb = load_workbook(path, read_only=True, data_only=True)
    added = 0
    try:
        ws = wb[wb.sheetnames[0]]
        rows = ws.iter_rows(values_only=True)
        header = header_map(next(rows))
        with connect() as db:
            for raw in rows:
                case_id = cell(raw, header.get("用例编号"))
                if not case_id:
                    continue
                before = db.execute(
                    "SELECT id FROM cases WHERE product=? AND case_id=?", (product, case_id)
                ).fetchone()
                upsert_case(
                    db, product, case_id,
                    title=cell(raw, header.get("用例标题")),
                    s1=cell(raw, header.get("S1场景")),
                    s2=cell(raw, header.get("S2场景")),
                    s3=cell(raw, header.get("S3场景")),
                    level=cell(raw, header.get("用例等级")),
                )
                if before is None:
                    added += 1
            db.commit()
    finally:
        wb.close()
    return added


def enrich_task(db, task):
    task = dict(task)
    stats = db.execute(
        """SELECT COUNT(DISTINCT case_pk) cases, COUNT(*) rounds, AVG(score) avg_score,
                  SUM(CASE WHEN score >= ? THEN 1 ELSE 0 END) pass_rounds
           FROM rounds WHERE task_id=?""",
        (PASS_LINE, task["task_id"]),
    ).fetchone()
    task["case_count"] = stats["cases"] or 0
    task["round_count"] = stats["rounds"] or 0
    task["avg_score"] = round(stats["avg_score"], 2) if stats["avg_score"] is not None else None
    task["pass_rounds"] = stats["pass_rounds"] or 0
    case_pass = db.execute(
        """SELECT SUM(CASE WHEN avg_score >= ? THEN 1 ELSE 0 END) passed, COUNT(*) total FROM (
               SELECT AVG(score) avg_score FROM rounds
               WHERE task_id=? AND score IS NOT NULL GROUP BY case_pk
           )""",
        (PASS_LINE, task["task_id"]),
    ).fetchone()
    task["passed_cases"] = case_pass["passed"] or 0
    task["scored_cases"] = case_pass["total"] or 0
    task["product_name"] = PRODUCT_LABEL.get(task["product"], task["product"])
    task["dataset_name"] = task.get("dataset_name") or ""
    task["os_name"] = task.get("os_name") or ""
    task["os_label"] = OS_LABEL.get(task["os_name"], "")
    try:
        task["device_count"] = int(task.get("device_count") or 0)
    except (TypeError, ValueError):
        task["device_count"] = 0
    return task


def dataset_row(name):
    with connect() as db:
        return db.execute("SELECT * FROM datasets WHERE name=?", (name,)).fetchone()


def read_sheet(path):
    wb = load_workbook(path, read_only=True, data_only=True)
    try:
        ws = wb[wb.sheetnames[0]]
        rows = ws.iter_rows(values_only=True)
        header = next(rows, None) or []
        headers = ["" if cell is None else str(cell) for cell in header]
        data = []
        for raw in rows:
            if raw is None or all(cell is None or str(cell).strip() == "" for cell in raw):
                continue
            data.append(["" if cell is None else str(cell) for cell in raw])
        return headers, data
    finally:
        wb.close()


def count_sheet_cases(path):
    headers, rows = read_sheet(path)
    id_idx = None
    for i, name in enumerate(headers):
        if name in ("用例编号", "case_id"):
            id_idx = i
            break
    if id_idx is None:
        return len(rows)
    return len({row[id_idx] for row in rows if id_idx < len(row) and row[id_idx]})


def ensure_datasets():
    dest_root = ROOT / "data" / "datasets"
    dest_root.mkdir(parents=True, exist_ok=True)
    with connect() as db:
        for spec in BUILTIN_TASKS:
            src = IMPORT_DIR / spec["source_file"]
            if not src.exists():
                continue
            dest = dest_root / spec["source_file"]
            if not dest.exists() or dest.stat().st_size != src.stat().st_size:
                dest.write_bytes(src.read_bytes())
            count = count_sheet_cases(dest)
            updated = datetime.fromtimestamp(dest.stat().st_mtime).strftime("%Y-%m-%d %H:%M:%S")
            db.execute(
                """INSERT INTO datasets(name, product, filename, path, case_count, updated_at, builtin)
                   VALUES (?, ?, ?, ?, ?, ?, 1)
                   ON CONFLICT(name) DO UPDATE SET
                     case_count=excluded.case_count, path=excluded.path, filename=excluded.filename""",
                (spec["dataset"], spec["product"], spec["source_file"], str(dest), count, updated),
            )
            db.execute(
                "UPDATE tasks SET dataset_name=? WHERE task_id=?",
                (spec["dataset"], spec["task_id"]),
            )
        db.commit()


app = create_app()


if __name__ == "__main__":
    app.run(host="127.0.0.1", port=9530, debug=False)
