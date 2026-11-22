"""快速构建 (QuickBuild) — 图形界面。

扫描已添加文件夹里的构建脚本 / 构建描述文件，一键并行构建，
并把产物复制到项目"旁边"的输出文件夹里。

只用标准库（tkinter），双击运行或：
    python quickbuild.py
"""

from __future__ import annotations

import json
import os
import queue
import subprocess
import sys
import threading
import time
import tkinter as tk
import tkinter.font as tkfont
from pathlib import Path
from tkinter import filedialog, messagebox, ttk

sys.path.insert(0, str(Path(__file__).resolve().parent))
import qb_core as core  # noqa: E402

APP_TITLE = "快速构建 · QuickBuild"

# --------------------------------------------------------------------------- #
# 配色 / 字体
# --------------------------------------------------------------------------- #

BG = "#16181c"
PANEL = "#1f2227"
PANEL2 = "#282c33"
HOVER = "#343941"
BORDER = "#2f343c"
FG = "#d7dbe1"
DIM = "#868f9c"
ACCENT = "#3f8ce8"
ACCENT_HOVER = "#5aa0f2"
OK = "#54b876"
ERR = "#e5544f"
WARN = "#d9a13b"
RUN = "#58a6ff"
PURPLE = "#a78bfa"

OUT_MODES = [
    ("与项目同级（旁边的文件夹）", "sibling"),
    ("项目内的 quickbuild-dist", "inside"),
    ("自定义目录下的 <项目名>-build", "custom"),
]

STATUS_STYLE = {
    "idle": ("待命", "idle"),
    "queued": ("排队中", "queued"),
    "run": ("构建中", "run"),
    "ok": ("成功", "ok"),
    "err": ("失败", "err"),
    "cancel": ("已取消", "cancel"),
}

DEFAULT_CONFIG = {
    "folders": [],
    "parallel": 3,
    "scan_depth": 3,
    "out_mode": "sibling",
    "custom_base": "",
    "exclude_sources": True,
    "copy_artifacts": True,
    "unchecked": [],
    "overrides": {},
    "geometry": "1240x780",
}


CONFIG_PATH = Path(
    os.environ.get("QUICKBUILD_CONFIG")
    or Path(__file__).resolve().with_name("quickbuild_config.json")
)


def load_config() -> dict:
    cfg = dict(DEFAULT_CONFIG)
    try:
        raw = json.loads(CONFIG_PATH.read_text(encoding="utf-8"))
        if isinstance(raw, dict):
            cfg.update(raw)
    except (OSError, json.JSONDecodeError):
        pass
    if not isinstance(cfg.get("folders"), list):
        cfg["folders"] = []
    if not isinstance(cfg.get("overrides"), dict):
        cfg["overrides"] = {}
    if not isinstance(cfg.get("unchecked"), list):
        cfg["unchecked"] = []
    cfg["parallel"] = max(1, min(16, int(cfg.get("parallel") or 3)))
    cfg["scan_depth"] = max(1, min(6, int(cfg.get("scan_depth") or 3)))
    return cfg


def save_config(cfg: dict) -> None:
    try:
        CONFIG_PATH.write_text(json.dumps(cfg, ensure_ascii=False, indent=2), encoding="utf-8")
    except OSError:
        pass


def open_path(path: Path) -> None:
    try:
        path = Path(path)
        if not path.exists() and not path.suffix:
            path.mkdir(parents=True, exist_ok=True)
        if core.IS_WIN:
            os.startfile(str(path))  # noqa: S606
        elif sys.platform == "darwin":
            subprocess.Popen(["open", str(path)])
        else:
            subprocess.Popen(["xdg-open", str(path)])
    except OSError:
        pass


def pick_font(candidates: list[str], default: str) -> str:
    try:
        families = set(tkfont.families())
    except tk.TclError:
        return default
    for name in candidates:
        if name in families:
            return name
    return default


# --------------------------------------------------------------------------- #
# 目标运行状态
# --------------------------------------------------------------------------- #


class TargetState:
    __slots__ = ("checked", "status", "duration", "copied", "lines", "tab")

    def __init__(self, checked: bool = True) -> None:
        self.checked = checked
        self.status = "idle"
        self.duration = 0.0
        self.copied = 0
        self.lines: list[tuple[str, str]] = []
        self.tab = None



# --------------------------------------------------------------------------- #
# 日志视图
# --------------------------------------------------------------------------- #


class LogView(tk.Frame):
    """带滚动条、自动跟随底部的只读日志框。"""

    MAX_LINES = 4000

    def __init__(self, master: tk.Misc, mono: str) -> None:
        super().__init__(master, bg=PANEL, bd=0, highlightthickness=0)
        self.text = tk.Text(
            self, bg=PANEL, fg=FG, insertbackground=FG, relief="flat",
            wrap="none", bd=0, padx=10, pady=8, spacing1=1, spacing3=1,
            selectbackground="#2f4f73", font=(mono, 10), state="disabled",
            cursor="arrow", highlightthickness=0,
        )
        vsb = ttk.Scrollbar(self, orient="vertical", command=self.text.yview, style="Vertical.TScrollbar")
        hsb = ttk.Scrollbar(self, orient="horizontal", command=self.text.xview, style="Horizontal.TScrollbar")
        self.text.configure(yscrollcommand=vsb.set, xscrollcommand=hsb.set)
        self.text.grid(row=0, column=0, sticky="nsew")
        vsb.grid(row=0, column=1, sticky="ns")
        hsb.grid(row=1, column=0, sticky="ew")
        self.rowconfigure(0, weight=1)
        self.columnconfigure(0, weight=1)

        self.text.tag_configure("out", foreground=FG)
        self.text.tag_configure("err", foreground=ERR)
        self.text.tag_configure("warn", foreground=WARN)
        self.text.tag_configure("cmd", foreground="#7fd6c1")
        self.text.tag_configure("sys", foreground=DIM)
        self.text.tag_configure("ok", foreground=OK)
        self.text.tag_configure("head", foreground=PURPLE)
        self.text.tag_configure("prefix", foreground=DIM)

    def append(self, line: str, tag: str = "out") -> None:
        at_bottom = self.text.yview()[1] > 0.995
        self.text.configure(state="normal")
        self.text.insert("end", line + "\n", tag)
        excess = int(self.text.index("end-1c").split(".")[0]) - self.MAX_LINES
        if excess > 0:
            self.text.delete("1.0", f"{excess + 1}.0")
        self.text.configure(state="disabled")
        if at_bottom:
            self.text.see("end")

    def append_prefixed(self, prefix: str, line: str, tag: str) -> None:
        at_bottom = self.text.yview()[1] > 0.995
        self.text.configure(state="normal")
        self.text.insert("end", prefix, "prefix")
        self.text.insert("end", line + "\n", tag)
        excess = int(self.text.index("end-1c").split(".")[0]) - self.MAX_LINES
        if excess > 0:
            self.text.delete("1.0", f"{excess + 1}.0")
        self.text.configure(state="disabled")
        if at_bottom:
            self.text.see("end")

    def clear(self) -> None:
        self.text.configure(state="normal")
        self.text.delete("1.0", "end")
        self.text.configure(state="disabled")


# --------------------------------------------------------------------------- #
# 主窗口
# --------------------------------------------------------------------------- #


class QuickBuildApp:
    def __init__(self, root: tk.Tk) -> None:
        self.root = root
        self.cfg = load_config()
        self.ui_queue: "queue.Queue[tuple[str, object]]" = queue.Queue()

        self.targets: list[core.Target] = []
        self.states: dict[str, TargetState] = {}
        self.defaults: dict[str, tuple[str, list[str]]] = {}
        self.folders: list[Path] = []
        self.row_of: dict[str, str] = {}     # key -> tree iid
        self.key_of: dict[str, str] = {}     # tree iid -> key
        self.folder_rows: dict[str, str] = {}

        self.building = False
        self.cancel_event = threading.Event()
        self.procs: set[subprocess.Popen] = set()
        self.results: list[tuple[core.Target, core.BuildResult]] = []
        self._pending = 0
        self._ok = 0
        self._fail = 0
        self._started_at = 0.0
        self._search = ""
        self.log_tabs: dict[str, LogView] = {}

        self.family = pick_font(
            ["Microsoft YaHei UI", "Microsoft YaHei", "Segoe UI", "Noto Sans CJK SC", "PingFang SC"],
            "TkDefaultFont",
        )
        self.mono = pick_font(
            ["Cascadia Mono", "Consolas", "JetBrains Mono", "Sarasa Mono SC", "Courier New"], "TkFixedFont"
        )

        self._build_ui()
        self._load_folders()
        self._drain_queue()
        self.root.protocol("WM_DELETE_WINDOW", self._on_close)
        self._apply_geometry()

    # ------------------------------------------------------------------ UI

    def _apply_geometry(self) -> None:
        geo = self.cfg.get("geometry") or "1240x780"
        try:
            self.root.geometry(geo)
        except tk.TclError:
            self.root.geometry("1240x780")
        self.root.minsize(980, 620)

    def _build_ui(self) -> None:
        root = self.root
        root.title(APP_TITLE)
        root.configure(bg=BG)
        self._init_style()

        outer = ttk.Frame(root, style="TFrame", padding=(14, 12, 14, 8))
        outer.pack(fill="both", expand=True)
        outer.columnconfigure(0, weight=1)
        outer.rowconfigure(3, weight=1)

        # ---------- 标题行 ----------
        head = ttk.Frame(outer)
        head.grid(row=0, column=0, sticky="ew", pady=(0, 10))
        ttk.Label(head, text="快速构建", style="Title.TLabel").pack(side="left")
        ttk.Label(
            head,
            text="扫描构建脚本 · 并行构建 · 产物自动收集到旁边的文件夹",
            style="Dim.TLabel",
        ).pack(side="left", padx=(12, 0), pady=(6, 0))
        self.lbl_summary = ttk.Label(head, text="", style="Dim.TLabel")
        self.lbl_summary.pack(side="right", pady=(6, 0))

        # ---------- 第一排工具 ----------
        bar1 = ttk.Frame(outer)
        bar1.grid(row=1, column=0, sticky="ew", pady=(0, 8))

        self.btn_add = ttk.Button(bar1, text="＋ 添加文件夹", style="Accent.TButton", command=self.add_folder)
        self.btn_add.pack(side="left")
        ttk.Button(bar1, text="重新扫描", command=self.rescan).pack(side="left", padx=(8, 0))
        ttk.Button(bar1, text="移除文件夹", command=self.remove_folder).pack(side="left", padx=(8, 0))

        ttk.Label(bar1, text="搜索", style="Dim.TLabel").pack(side="left", padx=(18, 6))
        self.var_search = tk.StringVar()
        self.var_search.trace_add("write", lambda *_: self._on_search())
        entry = ttk.Entry(bar1, textvariable=self.var_search, width=22)
        entry.pack(side="left")

        ttk.Button(bar1, text="全选", command=lambda: self._check_all(True)).pack(side="left", padx=(16, 0))
        ttk.Button(bar1, text="全不选", command=lambda: self._check_all(False)).pack(side="left", padx=(6, 0))

        ttk.Button(bar1, text="打开输出目录", command=self.open_out_dir).pack(side="right")

        # ---------- 第二排工具 ----------
        bar2 = ttk.Frame(outer)
        bar2.grid(row=2, column=0, sticky="ew", pady=(0, 10))

        self.btn_build = ttk.Button(bar2, text="▶  开始构建", style="Accent.TButton", command=lambda: self.start_build())
        self.btn_build.pack(side="left")
        self.btn_stop = ttk.Button(bar2, text="■  停止", style="Danger.TButton", command=self.stop_build)
        self.btn_stop.pack(side="left", padx=(8, 0))
        self.btn_stop.state(["disabled"])

        ttk.Label(bar2, text="并行", style="Dim.TLabel").pack(side="left", padx=(20, 6))
        self.var_parallel = tk.IntVar(value=int(self.cfg["parallel"]))
        spin = ttk.Spinbox(
            bar2, from_=1, to=16, width=4, textvariable=self.var_parallel,
            command=self._save_runtime_cfg, justify="center",
        )
        spin.pack(side="left")
        spin.bind("<FocusOut>", lambda _e: self._save_runtime_cfg())

        ttk.Label(bar2, text="扫描深度", style="Dim.TLabel").pack(side="left", padx=(16, 6))
        self.var_depth = tk.IntVar(value=int(self.cfg["scan_depth"]))
        depth = ttk.Spinbox(
            bar2, from_=1, to=6, width=4, textvariable=self.var_depth,
            command=self._on_depth_change, justify="center",
        )
        depth.pack(side="left")
        depth.bind("<FocusOut>", lambda _e: self._on_depth_change())

        ttk.Label(bar2, text="输出到", style="Dim.TLabel").pack(side="left", padx=(18, 6))
        self.var_out = tk.StringVar(value=self._out_mode_label(self.cfg["out_mode"]))
        combo = ttk.Combobox(
            bar2, textvariable=self.var_out, state="readonly", width=30,
            values=[name for name, _ in OUT_MODES],
        )
        combo.pack(side="left")
        combo.bind("<<ComboboxSelected>>", lambda _e: self._on_out_mode())

        self.var_exclude_src = tk.BooleanVar(value=bool(self.cfg["exclude_sources"]))
        ttk.Checkbutton(
            bar2, text="排除 sources/javadoc", variable=self.var_exclude_src,
            command=self._save_runtime_cfg,
        ).pack(side="right")
        self.var_copy = tk.BooleanVar(value=bool(self.cfg["copy_artifacts"]))
        ttk.Checkbutton(
            bar2, text="复制产物", variable=self.var_copy, command=self._save_runtime_cfg
        ).pack(side="right", padx=(0, 14))

        # ---------- 主体 ----------
        paned = ttk.Panedwindow(outer, orient="horizontal")
        paned.grid(row=3, column=0, sticky="nsew")

        left = ttk.Frame(paned, style="Panel.TFrame")
        left.rowconfigure(1, weight=1)
        left.columnconfigure(0, weight=1)
        paned.add(left, weight=3)

        cols = ("check", "kind", "status", "time")
        self.tree = ttk.Treeview(left, columns=cols, show="tree headings", selectmode="browse")
        self.tree.heading("#0", text="项目 / 任务")
        self.tree.heading("check", text="选")
        self.tree.heading("kind", text="类型")
        self.tree.heading("status", text="状态")
        self.tree.heading("time", text="用时")
        self.tree.column("#0", width=300, minwidth=140, stretch=True)
        self.tree.column("check", width=38, minwidth=38, stretch=False, anchor="center")
        self.tree.column("kind", width=76, minwidth=56, stretch=False, anchor="center")
        self.tree.column("status", width=76, minwidth=58, stretch=False, anchor="center")
        self.tree.column("time", width=62, minwidth=52, stretch=False, anchor="e")

        self.tree.tag_configure("folder", foreground=DIM)
        self.tree.tag_configure("idle", foreground=FG)
        self.tree.tag_configure("queued", foreground=DIM)
        self.tree.tag_configure("run", foreground=RUN)
        self.tree.tag_configure("ok", foreground=OK)
        self.tree.tag_configure("err", foreground=ERR)
        self.tree.tag_configure("cancel", foreground=WARN)

        vsb = ttk.Scrollbar(left, orient="vertical", command=self.tree.yview, style="Vertical.TScrollbar")
        hsb = ttk.Scrollbar(left, orient="horizontal", command=self.tree.xview, style="Horizontal.TScrollbar")
        self.tree.configure(yscrollcommand=vsb.set, xscrollcommand=hsb.set)
        self.tree.grid(row=1, column=0, sticky="nsew", padx=(1, 0))
        vsb.grid(row=1, column=1, sticky="ns")
        hsb.grid(row=2, column=0, sticky="ew", pady=(0, 1))

        self.tree.bind("<Button-1>", self._on_tree_click)
        self.tree.bind("<Double-1>", self._on_tree_double)
        self.tree.bind("<Button-3>", self._on_tree_right)
        self.tree.bind("<space>", self._on_space)
        self.tree.bind("<<TreeviewSelect>>", lambda _e: self._show_selected_log())

        # 右侧日志
        right = ttk.Frame(paned, style="Panel.TFrame")
        right.rowconfigure(0, weight=1)
        right.columnconfigure(0, weight=1)
        paned.add(right, weight=5)

        self.notebook = ttk.Notebook(right)
        self.notebook.grid(row=0, column=0, sticky="nsew")

        self.log_merged = LogView(self.notebook, self.mono)
        self.notebook.add(self.log_merged, text=" 合并输出 ")
        self.log_merged.append(f"[QuickBuild] {time.strftime('%Y-%m-%d %H:%M:%S')}  就绪", "sys")

        # 日志工具条
        logbar = ttk.Frame(right, style="Panel.TFrame")
        logbar.grid(row=1, column=0, sticky="ew", pady=(6, 0))
        ttk.Button(logbar, text="清空日志", command=self.clear_logs).pack(side="left")
        ttk.Button(logbar, text="复制当前日志", command=self.copy_log).pack(side="left", padx=(6, 0))
        self.lbl_hint = ttk.Label(logbar, text="双击任务可查看单独日志", style="Dim.TLabel")
        self.lbl_hint.pack(side="right")

        # ---------- 状态栏 ----------
        status = ttk.Frame(outer)
        status.grid(row=4, column=0, sticky="ew", pady=(10, 0))
        status.columnconfigure(1, weight=1)
        self.lbl_status = ttk.Label(status, text="就绪", style="Dim.TLabel")
        self.lbl_status.grid(row=0, column=0, sticky="w")
        self.progress = ttk.Progressbar(status, mode="determinate", maximum=100, value=0)
        self.progress.grid(row=0, column=1, sticky="ew", padx=14)
        self.lbl_count = ttk.Label(status, text="", style="Dim.TLabel")
        self.lbl_count.grid(row=0, column=2, sticky="e")

    def _init_style(self) -> None:
        style = ttk.Style(self.root)
        try:
            style.theme_use("clam")
        except tk.TclError:
            pass

        family = self.family
        style.configure(
            ".", background=BG, foreground=FG, fieldbackground=PANEL2, bordercolor=BORDER,
            lightcolor=PANEL2, darkcolor=PANEL2, troughcolor=PANEL, focuscolor=ACCENT,
            font=(family, 10), borderwidth=0,
        )
        style.configure("TFrame", background=BG)
        style.configure("Panel.TFrame", background=PANEL)
        style.configure("TLabel", background=BG, foreground=FG, font=(family, 10))
        style.configure("Dim.TLabel", background=BG, foreground=DIM, font=(family, 9))
        style.configure("Title.TLabel", background=BG, foreground=FG, font=(family, 16, "bold"))

        style.configure("TButton", background=PANEL2, foreground=FG, padding=(12, 6), borderwidth=0)
        style.map(
            "TButton",
            background=[("pressed", HOVER), ("active", HOVER), ("disabled", PANEL)],
            foreground=[("disabled", DIM)],
        )
        style.configure("Accent.TButton", background=ACCENT, foreground="#ffffff", padding=(14, 6))
        style.map(
            "Accent.TButton",
            background=[("pressed", ACCENT), ("active", ACCENT_HOVER), ("disabled", PANEL2)],
            foreground=[("disabled", DIM)],
        )
        style.configure("Danger.TButton", background="#5a2a2c", foreground="#ffd9d7", padding=(14, 6))
        style.map(
            "Danger.TButton",
            background=[("pressed", "#4a2224"), ("active", "#7a3a3d"), ("disabled", PANEL)],
            foreground=[("disabled", DIM)],
        )

        style.configure("TEntry", fieldbackground=PANEL2, foreground=FG, insertcolor=FG, padding=5, borderwidth=1)
        style.map("TEntry", bordercolor=[("focus", ACCENT)])

        style.configure("TCombobox", fieldbackground=PANEL2, background=PANEL2, foreground=FG, arrowcolor=FG, padding=4)
        style.map(
            "TCombobox",
            fieldbackground=[("readonly", PANEL2)],
            foreground=[("readonly", FG)],
            background=[("active", HOVER)],
            selectbackground=[("readonly", PANEL2)],
            selectforeground=[("readonly", FG)],
            arrowcolor=[("active", ACCENT)],
        )
        style.configure("TSpinbox", fieldbackground=PANEL2, foreground=FG, arrowcolor=FG, padding=3)
        style.map("TSpinbox", background=[("active", HOVER)])

        style.configure("TCheckbutton", background=BG, foreground=FG, font=(family, 9))
        style.map("TCheckbutton", background=[("active", BG)], foreground=[("active", FG)])

        style.configure("Treeview", background=PANEL, fieldbackground=PANEL, foreground=FG,
                        bordercolor=PANEL, borderwidth=0, rowheight=27, font=(family, 10))
        style.map("Treeview", background=[("selected", "#31496b")], foreground=[("selected", "#ffffff")])
        style.configure("Treeview.Heading", background=PANEL2, foreground=DIM, relief="flat",
                        borderwidth=0, padding=(6, 6), font=(family, 9, "bold"))
        style.map("Treeview.Heading", background=[("active", HOVER)], foreground=[("active", FG)])

        style.configure("TNotebook", background=PANEL, borderwidth=0, tabmargins=(0, 0, 0, 0))
        style.configure("TNotebook.Tab", background=PANEL, foreground=DIM, padding=(16, 7),
                        borderwidth=0, font=(family, 9))
        style.map("TNotebook.Tab", background=[("selected", PANEL2)], foreground=[("selected", FG)])

        style.configure("Vertical.TScrollbar", background=PANEL2, troughcolor=BG, bordercolor=BG,
                        arrowcolor=DIM, borderwidth=0, width=11)
        style.map("Vertical.TScrollbar", background=[("active", HOVER)])
        style.configure("Horizontal.TScrollbar", background=PANEL2, troughcolor=BG, bordercolor=BG,
                        arrowcolor=DIM, borderwidth=0)
        style.map("Horizontal.TScrollbar", background=[("active", HOVER)])

        style.configure("TProgressbar", background=ACCENT, troughcolor=PANEL2, bordercolor=PANEL2,
                        lightcolor=ACCENT, darkcolor=ACCENT, borderwidth=0, thickness=6)

        self.root.option_add("*TCombobox*Listbox.background", PANEL2)
        self.root.option_add("*TCombobox*Listbox.foreground", FG)
        self.root.option_add("*TCombobox*Listbox.selectBackground", ACCENT)
        self.root.option_add("*TCombobox*Listbox.selectForeground", "#ffffff")

    # -------------------------------------------------------------- 配置

    def _out_mode_label(self, mode: str) -> str:
        for name, key in OUT_MODES:
            if key == mode:
                return name
        return OUT_MODES[0][0]

    def _out_mode_key(self) -> str:
        for name, key in OUT_MODES:
            if name == self.var_out.get():
                return key
        return "sibling"

    def _save_runtime_cfg(self) -> None:
        try:
            self.cfg["parallel"] = max(1, min(16, int(self.var_parallel.get())))
        except (tk.TclError, ValueError):
            self.var_parallel.set(self.cfg["parallel"])
        self.cfg["exclude_sources"] = bool(self.var_exclude_src.get())
        self.cfg["copy_artifacts"] = bool(self.var_copy.get())
        self.cfg["out_mode"] = self._out_mode_key()
        save_config(self.cfg)

    def _on_depth_change(self) -> None:
        try:
            depth = max(1, min(6, int(self.var_depth.get())))
        except (tk.TclError, ValueError):
            self.var_depth.set(self.cfg["scan_depth"])
            return
        if depth != self.cfg.get("scan_depth"):
            self.cfg["scan_depth"] = depth
            save_config(self.cfg)
            self.rescan(quiet=True)

    def _on_out_mode(self) -> None:
        mode = self._out_mode_key()
        if mode == "custom":
            folder = filedialog.askdirectory(title="选择输出基础目录（每个项目会生成 <项目名>-build）")
            if folder:
                self.cfg["custom_base"] = folder
            elif not self.cfg.get("custom_base"):
                self.var_out.set(self._out_mode_label("sibling"))
                return
        self.cfg["out_mode"] = self._out_mode_key()
        save_config(self.cfg)
        self._update_summary()

    def _save_unchecked(self) -> None:
        self.cfg["unchecked"] = sorted(k for k, st in self.states.items() if not st.checked)
        save_config(self.cfg)

    def _on_close(self) -> None:
        try:
            self.cfg["geometry"] = self.root.winfo_geometry()
        except tk.TclError:
            pass
        self._save_runtime_cfg()
        self._save_unchecked()
        if self.building:
            self.cancel_event.set()
            for p in list(self.procs):
                core.kill_tree(p)
        self.root.destroy()

    # -------------------------------------------------------------- 扫描

    def _load_folders(self) -> None:
        self.folders = []
        for raw in self.cfg.get("folders", []):
            p = Path(raw)
            if p.is_dir() and p not in self.folders:
                self.folders.append(p)
        self.rescan(quiet=True)

    def add_folder(self) -> None:
        folder = filedialog.askdirectory(title="选择要扫描构建脚本的文件夹")
        if not folder:
            return
        path = Path(folder).resolve()
        if path in self.folders:
            self._set_status("该文件夹已在列表中")
            return
        self.folders.append(path)
        self.cfg["folders"] = [str(p) for p in self.folders]
        save_config(self.cfg)
        self.rescan()

    def remove_folder(self) -> None:
        sel = self.tree.selection()
        if not sel:
            self._set_status("先在左侧选中一个文件夹")
            return
        iid = sel[0]
        root_path = None
        if iid in self.folder_rows:
            root_path = Path(self.folder_rows[iid])
        elif iid in self.key_of:
            t = self._target(self.key_of[iid])
            root_path = t.root if t else None
        if root_path is None:
            return
        if root_path in self.folders:
            self.folders.remove(root_path)
            self.cfg["folders"] = [str(p) for p in self.folders]
            save_config(self.cfg)
            self.rescan()

    def rescan(self, quiet: bool = False) -> None:
        if self.building:
            self._set_status("构建进行中，稍后再重新扫描")
            return
        depth = int(self.cfg.get("scan_depth") or 3)
        self.targets = []
        for folder in self.folders:
            try:
                self.targets.extend(core.detect_targets(folder, max_depth=depth))
            except OSError as exc:
                self._set_status(f"扫描 {folder} 失败：{exc}")
        self._apply_overrides()

        unchecked = set(self.cfg.get("unchecked") or [])
        old = {k: st for k, st in self.states.items()}
        self.states = {}
        for t in self.targets:
            st = old.get(t.key) or TargetState(checked=t.key not in unchecked)
            st.status = "idle"
            self.states[t.key] = st

        self._refresh_tree()
        self._update_summary()
        if not quiet:
            total = len(self.targets)
            self._set_status(f"扫描完成，共发现 {total} 个构建任务")
            self.log_merged.append(
                f"[QuickBuild] 重新扫描完成：{len(self.folders)} 个文件夹，{total} 个任务", "sys"
            )

    def _apply_overrides(self) -> None:
        overrides = self.cfg.get("overrides") or {}
        for t in self.targets:
            self.defaults[t.key] = (t.command_text(), list(t.artifacts))
            ov = overrides.get(t.key)
            if not isinstance(ov, dict):
                continue
            text = (ov.get("text") or "").strip()
            if text:
                t.steps = [core.Step(text=text)]
            arts = ov.get("artifacts")
            if isinstance(arts, list):
                t.artifacts = [str(a) for a in arts if str(a).strip()]

    # -------------------------------------------------------------- 列表

    def _on_search(self) -> None:
        self._search = self.var_search.get().strip().lower()
        self._refresh_tree()

    def _visible_numbers(self, target: core.Target) -> bool:
        if not self._search:
            return True
        hay = " ".join([
            target.display(), target.kind_label, target.command_text(), target.rel_dir(),
        ]).lower()
        return self._search in hay

    def _refresh_tree(self) -> None:
        tree = self.tree
        open_state = {}
        for iid in self.folder_rows.values():
            try:
                open_state[iid] = tree.item(iid, "open")
            except tk.TclError:
                pass
        tree.delete(*tree.get_children())
        self.row_of.clear()
        self.key_of.clear()
        self.folder_rows.clear()

        for idx, folder in enumerate(self.folders):
            items = [t for t in self.targets if t.root == folder and self._visible_numbers(t)]
            if self._search and not items:
                continue
            fid = f"folder::{idx}"
            self.folder_rows[fid] = str(folder)
            tree.insert(
                "", "end", iid=fid, text=f" {folder.name}    ({len(items)})",
                open=open_state.get(fid, True),
                values=("", "", "", ""), tags=("folder",),
            )
            for t in items:
                self._insert_target_row(fid, t)

        if self.folder_rows:
            first = next(iter(self.folder_rows))
            if not tree.selection():
                tree.selection_set(first)

    def _insert_target_row(self, parent: str, t: core.Target) -> None:
        st = self.states[t.key]
        iid = t.key
        self.row_of[t.key] = iid
        self.key_of[iid] = t.key
        self.tree.insert(
            parent, "end", iid=iid, text=" " + self._row_text(t),
            values=(
                "■" if st.checked else "□",
                self._kind_text(t),
                STATUS_STYLE[st.status][0],
                f"{st.duration:.1f}s" if st.duration else "",
            ),
            tags=(STATUS_STYLE[st.status][1],),
        )

    def _update_row(self, t: core.Target) -> None:
        iid = self.row_of.get(t.key)
        if not iid:
            return
        st = self.states[t.key]
        try:
            self.tree.item(
                iid,
                values=(
                    "■" if st.checked else "□",
                    self._kind_text(t),
                    STATUS_STYLE[st.status][0],
                    f"{st.duration:.1f}s" if st.duration else "",
                ),
                tags=(STATUS_STYLE[st.status][1],),
            )
        except tk.TclError:
            pass

    def _target(self, key: str) -> core.Target | None:
        for t in self.targets:
            if t.key == key:
                return t
        return None

    def _selected_target(self) -> core.Target | None:
        sel = self.tree.selection()
        if not sel:
            return None
        iid = sel[0]
        if iid in self.key_of:
            return self._target(self.key_of[iid])
        return None

    def _on_space(self, _event=None) -> str:
        t = self._selected_target()
        if t:
            self._toggle(t)
        return "break"

    def _on_tree_click(self, event) -> str | None:
        if self.tree.identify_region(event.x, event.y) != "cell":
            return None
        column = self.tree.identify_column(event.x)
        iid = self.tree.identify_row(event.y)
        if not iid:
            return None
        if column == "#1":  # 勾选列
            self.tree.selection_set(iid)
            if iid in self.key_of:
                t = self._target(self.key_of[iid])
                if t:
                    self._toggle(t)
                    return "break"
            elif iid in self.folder_rows:
                folder = Path(self.folder_rows[iid])
                items = [x for x in self.targets if x.root == folder]
                want = not all(self.states[x.key].checked for x in items)
                for x in items:
                    self.states[x.key].checked = want
                self._refresh_tree()
                self._save_unchecked()
                self._update_summary()
                return "break"
        return None

    def _toggle(self, t: core.Target) -> None:
        st = self.states[t.key]
        st.checked = not st.checked
        self._update_row(t)
        self._save_unchecked()
        self._update_summary()

    def _check_all(self, value: bool) -> None:
        for t in self.targets:
            if self._visible_numbers(t):
                self.states[t.key].checked = value
        self._refresh_tree()
        self._save_unchecked()
        self._update_summary()

    def _on_tree_double(self, event) -> None:
        iid = self.tree.identify_row(event.y)
        if not iid:
            return
        if iid in self.folder_rows:
            current = self.tree.item(iid, "open")
            self.tree.item(iid, open=not current)
            return
        if iid in self.key_of:
            t = self._target(self.key_of[iid])
            if t:
                self._ensure_log_tab(t)
                self.notebook.select(self.log_tabs[t.key])

    def _on_tree_right(self, event) -> None:
        iid = self.tree.identify_row(event.y)
        if not iid:
            return
        self.tree.selection_set(iid)
        t = None
        folder = None
        if iid in self.key_of:
            t = self._target(self.key_of[iid])
        elif iid in self.folder_rows:
            folder = Path(self.folder_rows[iid])

        menu = tk.Menu(
            self.root, tearoff=0, bg=PANEL2, fg=FG, activebackground=ACCENT,
            activeforeground="#ffffff", bd=0, font=(self.family, 9),
        )
        if t:
            if self.building:
                menu.add_command(label="构建此任务（构建中不可用）", state="disabled")
            else:
                menu.add_command(label="构建此任务", command=lambda: self.start_build(only=[t]))
            menu.add_command(label="查看日志", command=lambda: (self._ensure_log_tab(t), self.notebook.select(self.log_tabs[t.key])))
            menu.add_separator()
            menu.add_command(label="编辑命令 / 产物规则…", command=lambda: self._edit_target(t))
            menu.add_command(label="启用 / 停用（勾选）", command=lambda: self._toggle(t))
            menu.add_separator()
            menu.add_command(label="打开任务目录", command=lambda: open_path(t.dir))
            menu.add_command(label="打开输出目录", command=lambda: open_path(core.out_root_for(t.root, self._out_mode_key(), self.cfg.get("custom_base"))))
            menu.add_command(label="清除该任务输出", command=lambda: self._clear_out(t))
            menu.add_separator()
            menu.add_command(label="复制命令", command=lambda: self._copy_text(t.command_text()))
        elif folder:
            out_root = core.out_root_for(folder, self._out_mode_key(), self.cfg.get("custom_base"))
            menu.add_command(label="重新扫描", command=self.rescan)
            menu.add_command(label="打开文件夹", command=lambda: open_path(folder))
            menu.add_command(label="打开输出目录", command=lambda: open_path(out_root))
            menu.add_command(label="清除该文件夹输出", command=lambda: self._clear_out(None, folder))
            menu.add_command(label="从这个列表移除", command=self.remove_folder)
        if menu.index("end") is None:
            return
        try:
            menu.tk_popup(event.x_root, event.y_root)
        finally:
            menu.grab_release()

    # -------------------------------------------------------------- 构建

    def _set_status(self, text: str) -> None:
        self.lbl_status.configure(text=text)

    def _update_summary(self) -> None:
        total = len(self.targets)
        checked = sum(1 for t in self.targets if self.states[t.key].checked)
        mode = self._out_mode_key()
        if mode == "custom" and not self.cfg.get("custom_base"):
            dest = "请先选择自定义目录"
        elif self.folders:
            dest = str(core.out_root_for(self.folders[0], mode, self.cfg.get("custom_base")))
        else:
            dest = "—"
        self.lbl_summary.configure(text=f"{len(self.folders)} 个文件夹 · {total} 个任务 · 已选 {checked}")
        self.lbl_count.configure(text=f"输出：{dest}" if total else "")
        if self.building:
            self.btn_build.configure(text="⏳  构建中…")
        else:
            self.btn_build.configure(text=f"▶  开始构建 ({checked})" if checked else "▶  开始构建")

    def start_build(self, only: list[core.Target] | None = None) -> None:
        if self.building:
            self._set_status("已有构建在进行中：等它结束，或点『停止』后再开新的构建")
            try:
                self.root.bell()
            except tk.TclError:
                pass
            return
        self._save_runtime_cfg()
        if only:
            targets = [t for t in only if t]
        else:
            targets = [t for t in self.targets if self.states[t.key].checked]
        if not targets:
            self._set_status("没有选中任何任务")
            messagebox.showinfo(APP_TITLE, "请先勾选要构建的任务。", parent=self.root)
            return
        mode = self._out_mode_key()
        if mode == "custom" and not self.cfg.get("custom_base"):
            self._set_status("请先选择自定义输出目录")
            messagebox.showwarning(APP_TITLE, "已选择「自定义目录」输出，请先在工具栏选择一个目录。", parent=self.root)
            return

        self.building = True
        self.cancel_event = threading.Event()
        self.procs.clear()
        self.results = []
        self._pending = len(targets)
        self._ok = 0
        self._fail = 0
        self._started_at = time.time()
        for t in targets:
            st = self.states[t.key]
            st.status = "queued"
            st.duration = 0.0
            st.copied = 0
            st.lines = []
            self._update_row(t)
            if st.tab is not None:
                st.tab.clear()
        self.progress.configure(value=0, maximum=100)
        self.btn_build.state(["disabled"])
        self.btn_build.configure(text=f"⏳  构建中 ({len(targets)})")
        self.btn_stop.state(["!disabled"])
        self._set_status(f"构建中… 0/{self._pending}")

        workers = int(self.cfg.get("parallel") or 3)
        self.log_merged.append("", "out")
        self.log_merged.append(
            f"════ {time.strftime('%H:%M:%S')} 开始构建 {len(targets)} 个任务"
            f"（并行 {workers}，同一项目的任务会自动串行） ════", "head"
        )
        th = threading.Thread(
            target=self._batch_worker,
            args=(targets, mode, self.cfg.get("custom_base"), workers),
            daemon=True,
            name="qb-ui-batch",
        )
        th.start()

    def _batch_worker(self, targets, mode, custom_base, workers) -> None:
        try:
            results = core.run_batch(
                targets,
                out_mode=mode,
                custom_base=custom_base,
                max_workers=workers,
                on_event=lambda kind, payload: self.ui_queue.put((kind, payload)),
                cancel_event=self.cancel_event,
                exclude_sources=bool(self.cfg.get("exclude_sources", True)),
                copy_artifacts_enabled=bool(self.cfg.get("copy_artifacts", True)),
                on_process=self._on_process,
            )
            self.ui_queue.put(("batch_done", results))
        except Exception as exc:  # 兜底，避免界面卡在"构建中"
            self.ui_queue.put(("batch_error", str(exc)))

    def _on_process(self, proc) -> None:
        if proc is not None:
            self.procs.add(proc)

    def stop_build(self) -> None:
        if not self.building:
            return
        self.cancel_event.set()
        self._set_status("正在停止…")
        self.btn_stop.state(["disabled"])
        for p in list(self.procs):
            core.kill_tree(p)

    def _event(self, kind: str, payload) -> None:
        if kind == "start":
            st = self.states.get(payload.key)
            if st:
                st.status = "run"
                self._update_row(payload)
                self._log_line(payload, f"▶ 开始构建 {payload.display()}", "head")
            return

        if kind == "line":
            t, text, tag = payload
            self._log_line(t, text, tag)
            return

        if kind == "done":
            t, res = payload
            st = self.states.get(t.key)
            if st:
                st.status = "ok" if res.ok else ("cancel" if res.cancelled else "err")
                st.duration = res.duration
                st.copied = len(res.copied)
                self._update_row(t)
            self.results.append((t, res))
            self._pending = max(0, self._pending - 1)
            if res.ok:
                self._ok += 1
            elif not res.cancelled:
                self._fail += 1
            total = max(1, self._ok + self._fail + self._pending + (1 if res.cancelled else 0))
            self.progress.configure(value=min(100.0, 100.0 * (self._ok + self._fail) / total))
            tail = f"，产物 {len(res.copied)} 个" if res.copied else ""
            status_text = "成功" if res.ok else ("已取消" if res.cancelled else "失败")
            self._log_line(
                t,
                f"■ {status_text} · 用时 {res.duration:.1f}s{tail}",
                "ok" if res.ok else ("warn" if res.cancelled else "err"),
            )
            self._set_status(f"构建中… 完成 {self._ok + self._fail}/{self._ok + self._fail + self._pending}")
            return

        if kind == "batch_done":
            self._finish_batch()
            return

        if kind == "batch_error":
            self._finish_batch(error=str(payload))

    def _finish_batch(self, error: str | None = None) -> None:
        self.building = False
        self.btn_build.state(["!disabled"])
        self.btn_stop.state(["disabled"])
        self.progress.configure(value=100 if not error else self.progress["value"])
        self.procs.clear()

        summary_path = None
        if self.results and not error:
            summary_path = core.write_summary(
                self.results, self._out_mode_key(), self.cfg.get("custom_base")
            )

        elapsed = time.time() - self._started_at
        if error:
            self.log_merged.append(f"[QuickBuild] 批量执行异常：{error}", "err")
            self._set_status("构建异常结束")
        else:
            self.log_merged.append(
                f"════ 结束：成功 {self._ok} · 失败 {self._fail} · 总用时 {elapsed:.1f}s ════",
                "ok" if not self._fail else "err",
            )
            self._set_status(
                f"完成：成功 {self._ok}，失败 {self._fail}，用时 {elapsed:.1f}s"
                + ("（已取消）" if self.cancel_event.is_set() else "")
            )
            if summary_path:
                self.log_merged.append(f"[QuickBuild] 报告：{summary_path}", "sys")

        if self._fail:
            try:
                self.root.bell()
            except tk.TclError:
                pass
        self._update_summary()

        if self.cfg.get("open_out_after") and self._ok:
            first = next((t for t, r in self.results if r.ok), None)
            if first:
                open_path(core.out_root_for(first.root, self._out_mode_key(), self.cfg.get("custom_base")))

    # -------------------------------------------------------------- 日志

    def _log_line(self, t: core.Target, text: str, tag: str) -> None:
        st = self.states.get(t.key)
        if st is not None:
            st.lines.append((text, tag))
            if len(st.lines) > 6000:
                del st.lines[:2000]
            if st.tab is not None:
                st.tab.append(text, tag)
        prefix = f"[{t.root.name}{'/' + t.rel_dir() if t.rel_dir() != '.' else ''}] "
        self.log_merged.append_prefixed(prefix, text, tag)

    def _ensure_log_tab(self, t: core.Target) -> LogView:
        st = self.states.get(t.key)
        if st is None:
            st = TargetState()
            self.states[t.key] = st
        if st.tab is not None:
            return st.tab
        view = LogView(self.notebook, self.mono)
        st.tab = view
        self.log_tabs[t.key] = view
        self.notebook.add(view, text=" " + t.display()[:26] + " ")
        for line, tag in st.lines:
            view.append(line, tag)
        return view

    @staticmethod
    def _row_text(t: core.Target) -> str:
        """列表里只显示相对文件夹的路径（文件夹名在外层节点上）。"""
        rel = t.rel_dir()
        base = "（项目根）" if rel == "." else rel
        return f"{base} · {t.script}" if t.script else base


    @staticmethod
    def _kind_text(t: core.Target) -> str:
        """缺少构建工具的任务在类型列打 ⚠。"""
        return f"{t.kind_label} ⚠" if t.note.startswith("⚠") else t.kind_label


    def _show_selected_log(self) -> None:
        t = self._selected_target()
        if not t or not t.key:
            return
        if self.states[t.key].tab is not None:
            self.notebook.select(self.states[t.key].tab)
        if t.note.startswith("⚠"):
            self._set_status(t.note)

    def clear_logs(self) -> None:
        self.log_merged.clear()
        for view in self.log_tabs.values():
            view.clear()

    def _current_log(self) -> LogView | None:
        try:
            current = self.notebook.select()
        except tk.TclError:
            return None
        for view in self.log_tabs.values():
            if str(view) == current:
                return view
        return self.log_merged

    def copy_log(self) -> None:
        view = self._current_log()
        if not view:
            return
        text = view.text.get("1.0", "end-1c")
        self._copy_text(text)

    def _copy_text(self, text: str) -> None:
        try:
            self.root.clipboard_clear()
            self.root.clipboard_append(text)
            self._set_status("已复制到剪贴板")
        except tk.TclError:
            pass

    # -------------------------------------------------------------- 队列

    def _drain_queue(self) -> None:
        dirty_rows: set[str] = set()
        try:
            for _ in range(4000):
                kind, payload = self.ui_queue.get_nowait()
                if kind in ("start", "done"):
                    dirty_rows.add(getattr(payload if kind == "start" else payload[0], "key", ""))
                self._event(kind, payload)
        except queue.Empty:
            pass
        for key in dirty_rows:
            t = self._target(key) if key else None
            if t:
                self._update_row(t)
        self.root.after(60, self._drain_queue)

    # -------------------------------------------------------------- 对话框

    def _edit_target(self, t: core.Target) -> None:
        default_text, default_arts = self.defaults.get(t.key, (t.command_text(), list(t.artifacts)))

        win = tk.Toplevel(self.root)
        win.title(f"编辑任务 · {t.display()}")
        win.configure(bg=BG)
        win.transient(self.root)
        win.resizable(True, False)
        win.geometry("760x430")
        win.columnconfigure(1, weight=1)

        pad = {"padx": 14, "pady": 6}

        ttk.Label(win, text="任务", style="Dim.TLabel").grid(row=0, column=0, sticky="e", **pad)
        ttk.Label(win, text=t.display()).grid(row=0, column=1, columnspan=2, sticky="w", **pad)

        ttk.Label(win, text="目录", style="Dim.TLabel").grid(row=1, column=0, sticky="e", **pad)
        dir_text = str(t.workdir) + (f"   （任务目录 {t.dir}）" if t.workdir != t.dir else "")
        ttk.Label(win, text=dir_text).grid(row=1, column=1, columnspan=2, sticky="w", **pad)

        ttk.Label(win, text="命令", style="Dim.TLabel").grid(row=2, column=0, sticky="ne", **pad)
        var_cmd = tk.StringVar(value=t.command_text())
        entry = ttk.Entry(win, textvariable=var_cmd)
        entry.grid(row=2, column=1, columnspan=2, sticky="ew", **pad)

        ttk.Label(win, text="多步命令用 && 连接（Windows 下由 cmd 执行）", style="Dim.TLabel").grid(
            row=3, column=1, columnspan=2, sticky="w", padx=14
        )

        alternatives = [(name, steps) for name, steps in t.alternatives]
        if alternatives:
            ttk.Label(win, text="检测到的其他方式", style="Dim.TLabel").grid(row=4, column=0, sticky="e", **pad)
            names = [f"{name}  →  {' && '.join(s.display() for s in steps)}" for name, steps in alternatives]
            var_alt = tk.StringVar(value="")
            combo = ttk.Combobox(win, textvariable=var_alt, values=names, state="readonly")
            combo.grid(row=4, column=1, columnspan=2, sticky="ew", **pad)

            def use_alt(_e=None) -> None:
                idx = combo.current()
                if 0 <= idx < len(alternatives):
                    var_cmd.set(" && ".join(s.display() for s in alternatives[idx][1]))

            combo.bind("<<ComboboxSelected>>", use_alt)
            alt_row = 5
        else:
            alt_row = 4

        ttk.Label(win, text="产物规则", style="Dim.TLabel").grid(row=alt_row, column=0, sticky="e", **pad)
        var_arts = tk.StringVar(value=", ".join(t.artifacts))
        ttk.Entry(win, textvariable=var_arts).grid(row=alt_row, column=1, columnspan=2, sticky="ew", **pad)
        ttk.Label(
            win,
            text="相对任务目录的 glob，逗号分隔；目录结尾加 / 表示整目录复制。留空则自动查找新生成的产物。",
            style="Dim.TLabel", wraplength=620, justify="left",
        ).grid(row=alt_row + 1, column=1, columnspan=2, sticky="w", padx=14)

        ttk.Label(win, text="输出子目录", style="Dim.TLabel").grid(row=alt_row + 2, column=0, sticky="e", **pad)
        ttk.Label(
            win,
            text=f"{core.out_root_for(t.root, self._out_mode_key(), self.cfg.get('custom_base'))} / {t.out_subdir}",
            style="Dim.TLabel",
        ).grid(row=alt_row + 2, column=1, columnspan=2, sticky="w", **pad)

        btnbar = ttk.Frame(win)
        btnbar.grid(row=alt_row + 3, column=0, columnspan=3, sticky="ew", padx=14, pady=(18, 14))
        btnbar.columnconfigure(0, weight=1)

        def reset_default() -> None:
            var_cmd.set(default_text)
            var_arts.set(", ".join(default_arts))

        def save() -> None:
            text = var_cmd.get().strip()
            arts = [a.strip() for a in var_arts.get().replace("，", ",").split(",") if a.strip()]
            ov = {}
            if text and text != default_text:
                ov["text"] = text
            if arts != list(default_arts):
                ov["artifacts"] = arts
            overrides = self.cfg.setdefault("overrides", {})
            if ov:
                overrides[t.key] = ov
            else:
                overrides.pop(t.key, None)
            save_config(self.cfg)
            # 立即生效
            t.steps = [core.Step(text=text)] if text else t.steps
            t.artifacts = arts
            self.log_merged.append(f"[QuickBuild] 已更新任务命令：{t.display()}", "sys")
            self._set_status("任务已更新")
            win.destroy()

        ttk.Button(btnbar, text="恢复默认", command=reset_default).grid(row=0, column=1, padx=(0, 8))
        ttk.Button(btnbar, text="取消", command=win.destroy).grid(row=0, column=2, padx=(0, 8))
        ttk.Button(btnbar, text="保存", style="Accent.TButton", command=save).grid(row=0, column=3)

        entry.focus_set()
        entry.select_range(0, "end")
        win.bind("<Escape>", lambda _e: win.destroy())
        win.bind("<Return>", lambda _e: save())
        win.wait_visibility()
        win.grab_set()

    def _clear_out(self, t: core.Target | None, folder: Path | None = None) -> None:
        import shutil

        root = t.root if t else folder
        if not root:
            return
        out_root = core.out_root_for(root, self._out_mode_key(), self.cfg.get("custom_base"))
        target_dir = (out_root / (t.out_subdir or "root")) if t else out_root
        if not target_dir.exists():
            self._set_status("输出目录不存在，无需清理")
            return
        if not messagebox.askyesno(APP_TITLE, f"确定清空输出目录？\n{target_dir}", parent=self.root):
            return
        try:
            shutil.rmtree(target_dir, ignore_errors=True)
            self._set_status(f"已清空 {target_dir}")
            self.log_merged.append(f"[QuickBuild] 已清空 {target_dir}", "sys")
        except OSError as exc:
            messagebox.showerror(APP_TITLE, f"清理失败：{exc}", parent=self.root)

    def open_out_dir(self) -> None:
        t = self._selected_target()
        root = t.root if t else (self.folders[0] if self.folders else None)
        if not root:
            self._set_status("还没有添加文件夹")
            return
        out_root = core.out_root_for(root, self._out_mode_key(), self.cfg.get("custom_base"))
        if t:
            out_root = out_root / (t.out_subdir or "root")
        try:
            out_root.mkdir(parents=True, exist_ok=True)
        except OSError as exc:
            messagebox.showerror(APP_TITLE, f"无法创建输出目录：{exc}", parent=self.root)
            return
        open_path(out_root)


def main() -> int:
    if core.IS_WIN:
        try:
            import ctypes

            ctypes.windll.shcore.SetProcessDpiAwareness(1)
        except Exception:  # pragma: no cover - 老系统没有 shcore
            pass
    root = tk.Tk()
    try:
        root.tk.call("tk", "scaling", 1.25)
    except tk.TclError:
        pass
    app = QuickBuildApp(root)
    if len(sys.argv) > 1 and not sys.argv[1].startswith("-"):
        path = Path(sys.argv[1])
        if path.is_dir() and path.resolve() not in app.folders:
            app.folders.append(path.resolve())
            app.cfg["folders"] = [str(p) for p in app.folders]
            save_config(app.cfg)
            app.rescan()
    root.mainloop()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
