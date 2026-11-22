"""快速构建 (QuickBuild) 核心逻辑。

职责：
  1. 扫描已添加文件夹中的构建脚本 / 构建描述文件，产出可执行的任务 (Target)；
  2. 串行或并行执行任务，实时回传日志，可中止；
  3. 构建成功后把产物复制到「旁边」的输出文件夹里。

本模块不依赖任何 GUI 库，可单独在命令行里验证。
"""

from __future__ import annotations

import json
import os
import re
import shlex
import shutil
import signal
import subprocess
import sys
import threading
import time
from dataclasses import dataclass, field
from pathlib import Path

IS_WIN = os.name == "nt"
CREATE_NO_WINDOW = 0x08000000 if IS_WIN else 0
CREATE_NEW_PROCESS_GROUP = 0x00000200 if IS_WIN else 0

# --------------------------------------------------------------------------- #
# 常量
# --------------------------------------------------------------------------- #

# 扫描时跳过的目录（这些目录里不会有"用户想构建的项目"）
SKIP_DIRS = {
    ".git", ".hg", ".svn", ".gradle", ".idea", ".vscode", ".vs", ".venv", ".tox",
    ".mypy_cache", ".pytest_cache", ".next", ".nuxt", ".dart_tool", ".workbuddy",
    ".zcode", ".kilocode", "node_modules", "__pycache__", "site-packages",
    "build", "dist", "out", "target", "bin", "obj", "coverage", "vendor",
    "run", "runtime_mods", "logs", ".m2", "Pods", "venv", ".cache",
}

# 产物溯源时可进入的目录（"build/libs/*.jar" 这类目录必须能进）
DISCOVER_SKIP_DIRS = {
    ".git", ".hg", ".svn", ".gradle", ".idea", ".vscode", ".vs", ".venv", ".tox",
    ".mypy_cache", ".pytest_cache", "node_modules", "__pycache__", "site-packages",
    "run", "runtime_mods", "logs", ".m2", "vendor", "Pods", ".cache", ".workbuddy",
}

ARTIFACT_EXTS = {
    ".jar", ".zip", ".war", ".aar", ".apk", ".aab", ".whl", ".tgz", ".tar.gz",
    ".exe", ".dll", ".msi", ".nupkg", ".appimage", ".so", ".dylib", ".dmg",
    ".7z", ".gz", ".mod", ".pkg", ".deb", ".rpm", ".vsix", ".fpm",
}

EXCLUDE_SUFFIXES = ("-sources.jar", "-javadoc.jar", "-dev.jar", "-sources.zip", ".pom")

KIND_LABEL = {
    "gradle": "Gradle",
    "maven": "Maven",
    "npm": "Node",
    "cargo": "Cargo",
    "dotnet": ".NET",
    "cmake": "CMake",
    "make": "Make",
    "python": "Python",
    "go": "Go",
    "script": "脚本",
    "custom": "自定义",
}

# 同一目录内谁优先作为主任务
KIND_PRIORITY = {
    "gradle": 10, "maven": 20, "dotnet": 25, "cargo": 30, "npm": 35,
    "cmake": 40, "go": 45, "python": 50, "make": 55, "script": 60, "custom": 99,
}

ARTIFACT_PATTERNS = {
    "gradle": ["build/libs/*.jar", "build/libs/*.zip"],
    "maven": ["target/*.jar", "target/*.war", "target/*.zip"],
    "npm": ["dist/", "build/", "out/", "release/", "*.tgz"],
    "cargo": ["target/release/*.exe", "target/release/*.dll", "target/release/*"],
    "dotnet": ["bin/Release/", "publish/"],
    "cmake": ["build/*.exe", "build/*.dll", "build/**/*.exe"],
    "python": ["dist/*.whl", "dist/*.tar.gz", "dist/*.zip"],
    "go": ["*.exe", "bin/*"],
    "make": [],
    "script": [],
    "custom": [],
}

ANSI_RE = re.compile(r"\x1b\[[0-9;?]*[A-Za-z]")
ERR_RE = re.compile(
    r"(^|\W)(error|errors|failed|failure|fatal|exception|traceback|cannot find|"
    r"BUILD FAILED|ERR!|not found)(\W|$)",
    re.IGNORECASE,
)
WARN_RE = re.compile(r"(^|\W)(warning|warn|deprecated|skipping)(\W|$)", re.IGNORECASE)


def comspec() -> str:
    return os.environ.get("COMSPEC", "cmd.exe")



# --------------------------------------------------------------------------- #
# 命令与任务
# --------------------------------------------------------------------------- #


def _unwrap(argv: list[str]) -> list[str]:
    """把 ["cmd.exe","/c","xxx.bat",...] 还原为 ["xxx.bat",...]，便于展示与查找。"""
    if (
        IS_WIN
        and len(argv) >= 3
        and argv[1].lower() == "/c"
        and Path(argv[0]).name.lower() in ("cmd.exe", "cmd")
    ):
        return list(argv[2:])
    return list(argv)


@dataclass
class Step:
    """一条构建命令：argv 形式（推荐）或 shell 文本形式。"""

    argv: list[str] | None = None
    text: str | None = None

    def display(self) -> str:
        if self.text:
            return self.text
        argv = _unwrap(self.argv or [])
        if IS_WIN:
            return subprocess.list2cmdline(argv)
        return shlex.join(argv)

    def program(self) -> str | None:
        if self.text:
            first = self.text.strip().split()
            return first[0] if first else None
        argv = _unwrap(self.argv or [])
        return argv[0] if argv else None


@dataclass
class Target:
    """一个可构建的任务。"""

    key: str
    root: Path                 # 用户添加的文件夹
    dir: Path                  # 任务所在目录（产物规则相对于它）
    kind: str
    name: str                  # 展示名（相对路径）
    steps: list[Step]
    alternatives: list[tuple[str, list[Step]]] = field(default_factory=list)
    artifacts: list[str] = field(default_factory=list)
    out_subdir: str = ""
    note: str = ""             # 展示用备注
    script: str = ""           # 若是脚本任务，记录脚本文件名
    cwd: Path | None = None    # 实际执行目录（多项目构建时是 settings.gradle 所在根）

    @property
    def workdir(self) -> Path:
        return self.cwd or self.dir

    @property
    def kind_label(self) -> str:
        return KIND_LABEL.get(self.kind, self.kind)

    def command_text(self) -> str:
        return " && ".join(s.display() for s in self.steps)

    def rel_dir(self) -> str:
        try:
            rel = self.dir.relative_to(self.root)
        except ValueError:
            rel = self.dir
        return "." if str(rel) in ("", ".") else str(rel).replace("\\", "/")

    def title(self) -> str:
        rel = self.rel_dir()
        return self.root.name if rel == "." else f"{self.root.name}/{rel}"

    def display(self) -> str:
        """列表里展示的名字（同目录多个任务时带上脚本/类型）。"""
        base = self.title()
        if self.script:
            return f"{base} · {self.script}"
        if self.kind == "custom":
            return f"{base} · {self.note or '自定义'}"
        return base


@dataclass
class BuildResult:
    ok: bool
    cancelled: bool
    exit_code: int
    duration: float
    copied: list[Path]
    out_dir: Path
    log_file: Path | None
    error: str | None = None


# --------------------------------------------------------------------------- #
# 扫描
# --------------------------------------------------------------------------- #


def _skip_dir(name: str, depth: int) -> bool:
    if depth > 0 and name.startswith("."):
        return True
    return name in SKIP_DIRS


def _walk(root: Path, max_depth: int):
    for dirpath, dirnames, filenames in os.walk(root):
        d = Path(dirpath)
        depth = len(d.relative_to(root).parts)
        dirnames[:] = sorted(n for n in dirnames if not _skip_dir(n, max(0, depth)))
        yield d, filenames
        if depth >= max_depth:
            dirnames[:] = []


def _find_up(start: Path, stop: Path, names: list[str]) -> Path | None:
    """从 start 向上找文件，最多找到 stop（含）。"""
    cur = start
    stop = stop.resolve()
    while True:
        for n in names:
            p = cur / n
            if p.is_file():
                return p
        if cur.resolve() == stop or cur.parent == cur:
            return None
        cur = cur.parent


def _wrap_program(prog: str, args: list[str]) -> list[str]:
    """Windows 下 .bat/.cmd 必须经 cmd 调用。"""
    if IS_WIN and prog.lower().endswith((".bat", ".cmd")):
        return [comspec(), "/c", prog, *args]
    return [prog, *args]


def _read_text(path: Path, limit: int = 60000) -> str:
    try:
        data = path.read_bytes()[:limit]
    except OSError:
        return ""
    for enc in ("utf-8", "gbk", "latin-1"):
        try:
            return data.decode(enc)
        except UnicodeDecodeError:
            continue
    return data.decode("utf-8", "replace")


def _read_json(path: Path) -> dict:
    try:
        return json.loads(_read_text(path, 400000) or "{}")
    except (json.JSONDecodeError, OSError):
        return {}


def _script_name_ok(name: str) -> bool:
    low = name.lower()
    if not low.endswith((".bat", ".cmd", ".sh", ".ps1")):
        return False
    stem = low.rsplit(".", 1)[0]
    if stem in ("build", "make", "compile", "package") or stem.startswith(("build-", "build_", "build.")):
        return True
    return False


def _gradle_task_in_script(text: str) -> str | None:
    m = re.search(r"gradlew(?:\.bat)?\"?\s+((?::[^\s\"']+)|(?:[^\s\"'&|>-][^\s\"'&|]*))", text, re.IGNORECASE)
    if not m:
        return None
    task = m.group(1).strip()
    if task.startswith("-"):
        return None
    return task


SETTINGS_FILES = ("settings.gradle", "settings.gradle.kts")


def _find_wrapper(start: Path) -> Path | None:
    """从 start 起向上找 gradlew（直到盘符根）。"""
    return _find_up(start, Path(start.anchor or "/"), ["gradlew.bat", "gradlew"])


def _parse_settings_projects(settings_file: Path) -> dict[str, Path]:
    """解析 settings.gradle(.kts)，返回 {':项目路径': 绝对 projectDir}。"""
    text = _read_text(settings_file, 200000)
    base = settings_file.parent
    mapping: dict[str, Path] = {}

    # include 'a' / include ':a', ':b:c'
    for m in re.finditer(r"^\s*include\b([^\n]*)", text, re.MULTILINE):
        for name in re.findall(r"['\"]([^'\"]+)['\"]", m.group(1)):
            key = ":" + name.strip(":").replace("/", ":")
            mapping.setdefault(key, base / name.strip(":").replace(":", "/"))

    # project(':a').projectDir = file('../a')  （子项目放在根目录之外）
    pattern = (
        r"project\(\s*['\"]([^'\"]+)['\"]\s*\)\s*\.\s*projectDir\s*=\s*"
        r"(?:file|new\s+File)\s*\(\s*['\"]([^'\"]+)['\"]"
    )
    for m in re.finditer(pattern, text):
        key = ":" + m.group(1).strip(":").replace("/", ":")
        try:
            mapping[key] = (base / m.group(2)).resolve()
        except OSError:
            continue
    return mapping


def _candidate_settings_dirs(d: Path, root: Path) -> list[Path]:
    """d 的各级父目录，加上项目根/根的同级目录（projectDir 可能指向外面）。"""
    dirs: list[Path] = []
    cur = d
    while True:
        if cur not in dirs:
            dirs.append(cur)
        if cur == root or cur.parent == cur:
            break
        cur = cur.parent
    for base in (root, root.parent):
        try:
            children = sorted(c for c in base.iterdir() if c.is_dir())
        except OSError:
            continue
        for child in children:
            if child not in dirs:
                dirs.append(child)
    return dirs


def _find_owning_settings(d: Path, root: Path) -> tuple[Path, str] | None:
    """
    找到「把 d 当作子项目包含进来」的 settings.gradle：
    返回 (settings 所在目录, ':项目路径')。用于 d 自己没有 gradlew 的多项目构建。
    """
    try:
        target = d.resolve()
    except OSError:
        return None
    for cand in _candidate_settings_dirs(d, root):
        for name in SETTINGS_FILES:
            settings = cand / name
            if not settings.is_file():
                continue
            for path, project_dir in _parse_settings_projects(settings).items():
                try:
                    if project_dir.resolve() == target:
                        return cand, path
                except OSError:
                    continue
    return None


def _candidates_for_dir(d: Path, filenames: list[str], root: Path) -> list[Target]:
    """返回该目录里检测到的候选任务（按优先级排序）。"""
    lower = {n.lower(): n for n in filenames}
    found: list[tuple[int, Target]] = []

    def add(kind: str, steps: list[Step], note: str = "", script: str = "",
            cwd: Path | None = None) -> None:
        found.append((
            KIND_PRIORITY.get(kind, 90),
            Target(
                key="",
                root=root,
                dir=d,
                kind=kind,
                name=d.name or str(d),
                steps=steps,
                artifacts=list(ARTIFACT_PATTERNS.get(kind, [])),
                note=note,
                script=script,
                cwd=cwd,
            ),
        ))

    # ---- Gradle ----
    if any(f in lower for f in ("build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts")):
        owner = _find_owning_settings(d, root)
        if owner is not None and owner[0] != d:
            # d 是别人 settings.gradle 里的子项目（可能放在根目录之外）：
            # 在 settings 根目录执行 gradlew :项目路径:build
            settings_dir, project_path = owner
            task = f"{project_path.rstrip(':')}:build"
            wrapper = _find_wrapper(settings_dir)
            exec_dir = settings_dir
        else:
            task = "build"
            wrapper = _find_up(d, root, ["gradlew.bat", "gradlew"]) or _find_wrapper(d)
            exec_dir = d
        args = [task, "--console=plain"]
        if wrapper is not None:
            add("gradle", [Step(argv=_wrap_program(str(wrapper), args))], cwd=exec_dir)
        else:
            prog = "gradle.bat" if IS_WIN else "gradle"
            if shutil.which(prog):
                note = "⚠ 未找到 gradlew，改用系统 gradle"
            else:
                note = "⚠ 未找到 gradlew/gradle，请右键『编辑命令』手动指定"
            add("gradle", [Step(argv=_wrap_program(prog, args))], note, cwd=exec_dir)

    # ---- Maven ----
    if "pom.xml" in lower:
        prog = "mvn.cmd" if IS_WIN else "mvn"
        add("maven", [Step(argv=_wrap_program(prog, ["-B", "package"]))])

    # ---- .NET ----
    sln = [lower[n] for n in lower if n.endswith(".sln")]
    csproj = [lower[n] for n in lower if n.endswith(".csproj")]
    if sln or csproj:
        proj = (sln or csproj)[0]
        add("dotnet", [Step(argv=["dotnet", "build", proj, "-c", "Release"])])

    # ---- Cargo ----
    if "cargo.toml" in lower:
        add("cargo", [Step(argv=["cargo", "build", "--release"])])

    # ---- Node ----
    if "package.json" in lower:
        pkg = _read_json(d / lower["package.json"])
        if isinstance(pkg.get("scripts"), dict) and "build" in pkg["scripts"]:
            if "pnpm-lock.yaml" in lower:
                argv = ["pnpm", "run", "build"]
            elif "yarn.lock" in lower:
                argv = ["yarn", "build"]
            elif "bun.lockb" in lower or "bun.lock" in lower:
                argv = ["bun", "run", "build"]
            else:
                argv = ["npm", "run", "build"]
            add("npm", [Step(argv=argv)])

    # ---- CMake ----
    if "cmakelists.txt" in lower:
        add("cmake", [
            Step(argv=["cmake", "-S", ".", "-B", "build", "-DCMAKE_BUILD_TYPE=Release"]),
            Step(argv=["cmake", "--build", "build", "--config", "Release"]),
        ])

    # ---- Go ----
    if "go.mod" in lower:
        add("go", [Step(argv=["go", "build", "./..."])])

    # ---- Python ----
    if "pyproject.toml" in lower or "setup.py" in lower:
        text = _read_text(d / lower.get("pyproject.toml", lower.get("setup.py", "")), 4000)
        if "build-system" in text or "setuptools" in text or "setup.py" in lower:
            add("python", [Step(argv=[sys.executable, "-m", "build"])], "需要 pip install build")

    # ---- Make ----
    if any(n in lower for n in ("makefile", "gnumakefile")):
        prog = "make"
        if IS_WIN:
            for cand in ("make", "mingw32-make", "nmake"):
                if shutil.which(cand):
                    prog = cand
                    break
        add("make", [Step(argv=[prog])])

    # ---- 构建脚本（build.bat / build.sh / build-compat.bat ...）----
    for low, real in sorted(lower.items()):
        if not _script_name_ok(real):
            continue
        path = d / real
        if real.lower().endswith((".bat", ".cmd")):
            steps = [Step(argv=_wrap_program(str(path), []))]
        elif real.lower().endswith(".ps1"):
            steps = [Step(argv=["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", str(path)])]
        else:
            steps = [Step(argv=["bash", str(path)])]
        add("script", steps, f"执行 {real}", real)

    found.sort(key=lambda x: x[0])
    return [t for _, t in found]


def detect_targets(
    root: Path,
    *,
    max_depth: int = 3,
) -> list[Target]:
    """扫描一个已添加文件夹，返回其中的构建任务。"""
    root = Path(root).expanduser().resolve()
    if not root.is_dir():
        return []

    result: list[Target] = []
    for d, filenames in _walk(root, max_depth):
        cands = _candidates_for_dir(d, filenames, root)
        if not cands:
            continue

        primary = cands[0]
        extras: list[Target] = []

        for t in cands[1:]:
            label = t.script or KIND_LABEL.get(t.kind, t.kind)
            if t.kind != "script":
                primary.alternatives.append((label, t.steps))
                continue
            task = _gradle_task_in_script(_read_text(d / t.script, 8000))
            if primary.kind == "gradle" and task is not None:
                if task in ("build", "assemble", "jar", "classes"):
                    # build.bat 这类只是包装 gradlew build，作为备选而不是重复任务
                    primary.alternatives.append((t.script, t.steps))
                    continue
                t.name = f"{d.name or str(d)} · {t.script}"
                extras.append(t)   # 例如 build-compat.bat 构建另一个子项目
                continue
            primary.alternatives.append((label, t.steps))

        result.extend([primary, *extras])

    _assign_identity(result, root)
    return result


def _assign_identity(targets: list[Target], root: Path) -> None:
    """生成稳定的 key 与输出子目录名。"""
    per_root: dict[str, list[Target]] = {}
    for t in targets:
        per_root.setdefault(str(t.root), []).append(t)

    for _root, items in per_root.items():
        used: dict[str, int] = {}
        for t in items:
            rel = t.rel_dir()
            base = "root" if rel == "." else safe_name(rel.replace("/", "-"))
            n = used.get(base, 0) + 1
            used[base] = n
            sub = base if n == 1 else f"{base}_{safe_name(t.kind) if t.kind != 'script' else safe_name(script_tag(t))}"
            t.out_subdir = sub
            tag = t.kind if t.kind != "script" else script_tag(t)
            t.key = f"{t.root}|{rel}|{tag}|{base}|{n}"


def script_tag(target: Target) -> str:
    if target.script:
        return Path(target.script).stem
    return target.kind


def safe_name(name: str) -> str:
    cleaned = re.sub(r'[<>:"/\\|?*\x00-\x1f]', "_", str(name)).strip(" .")
    return cleaned or "target"




# --------------------------------------------------------------------------- #
# 输出目录（"旁边的文件夹"）
# --------------------------------------------------------------------------- #


def out_root_for(root: Path, out_mode: str = "sibling", custom_base: str | None = None) -> Path:
    """
    输出根目录：
      sibling - 与项目文件夹同级（默认）：<父目录>/<项目名>-build
      inside  - 项目内：<项目>/quickbuild-dist
      custom  - 指定基础目录下：<base>/<项目名>-build
    """
    root = Path(root).expanduser().resolve()
    if out_mode == "inside":
        return root / "quickbuild-dist"
    if out_mode == "custom" and custom_base:
        return Path(custom_base).expanduser().resolve() / f"{root.name}-build"
    return root.parent / f"{root.name}-build"


# --------------------------------------------------------------------------- #
# 执行
# --------------------------------------------------------------------------- #


def strip_ansi(text: str) -> str:
    return ANSI_RE.sub("", text)


def decode_line(raw: bytes) -> str:
    for enc in ("utf-8", "gbk"):
        try:
            return raw.decode(enc).rstrip("\r\n")
        except UnicodeDecodeError:
            continue
    return raw.decode("utf-8", "replace").rstrip("\r\n")


def kill_tree(proc: subprocess.Popen) -> None:
    if proc.poll() is not None:
        return
    try:
        if IS_WIN:
            subprocess.run(
                ["taskkill", "/F", "/T", "/PID", str(proc.pid)],
                capture_output=True,
                creationflags=CREATE_NO_WINDOW,
            )
        else:
            os.killpg(os.getpgid(proc.pid), signal.SIGKILL)
    except (OSError, ProcessLookupError, subprocess.SubprocessError):
        pass
    try:
        proc.kill()
    except OSError:
        pass


def _spawn(step: Step, cwd: Path) -> subprocess.Popen:
    env = os.environ.copy()
    env.setdefault("PYTHONIOENCODING", "utf-8")
    env["NO_COLOR"] = "1"
    env["TERM"] = "dumb"
    env.pop("PYTHONHOME", None)

    if step.text:
        if IS_WIN:
            argv = [comspec(), "/c", step.text]
        else:
            argv = ["/bin/sh", "-c", step.text]
        kwargs: dict = {}
    else:
        argv = list(step.argv or [])
        kwargs = {}
        if IS_WIN and argv and argv[0].lower().endswith((".bat", ".cmd")):
            argv = [comspec(), "/c", *argv]

    return subprocess.Popen(
        argv,
        cwd=str(cwd),
        stdin=subprocess.DEVNULL,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        env=env,
        bufsize=0,
        creationflags=CREATE_NO_WINDOW | CREATE_NEW_PROCESS_GROUP if IS_WIN else 0,
        start_new_session=not IS_WIN,
        **kwargs,
    )


def _line_tag(line: str) -> str:
    if ERR_RE.search(line):
        return "err"
    if WARN_RE.search(line):
        return "warn"
    return "out"


def _watch_cancel(proc: subprocess.Popen, cancel_event) -> threading.Thread | None:
    """取消看门狗：即使进程长时间没有输出，也能立即被取消掉。"""
    if cancel_event is None:
        return None

    def loop() -> None:
        while proc.poll() is None:
            try:
                if cancel_event.wait(0.15):
                    kill_tree(proc)
                    return
            except (OSError, RuntimeError):
                return

    th = threading.Thread(target=loop, daemon=True, name="qb-cancel-watch")
    th.start()
    return th


def run_target(
    target: Target,
    out_root: Path,
    *,
    log=None,
    cancel_event=None,
    on_process=None,
    exclude_sources: bool = True,
    copy_artifacts_enabled: bool = True,
) -> BuildResult:
    """执行一个任务并把产物复制到 out_root/<out_subdir>。"""
    started = time.time()
    out_dir = Path(out_root) / (target.out_subdir or "root")
    buffer: list[str] = []

    def emit(text: str, tag: str = "out") -> None:
        buffer.append(text)
        if log:
            log(text, tag)

    def emit_sys(text: str, tag: str = "sys") -> None:
        buffer.append(text)
        if log:
            log(f"[QuickBuild] {text}", tag)

    copied: list[Path] = []
    log_file: Path | None = None
    exit_code = 0
    cancelled = False
    error: str | None = None

    emit_sys(f"开始构建：{target.title()}  ({target.kind_label})")
    emit_sys(
        f"工作目录：{target.workdir}"
        + (f"（任务目录 {target.dir}）" if target.workdir != target.dir else "")
    )

    try:
        out_dir.mkdir(parents=True, exist_ok=True)
    except OSError as exc:
        emit_sys(f"无法创建输出目录：{exc}", "err")
        return BuildResult(False, False, -1, 0.0, [], out_dir, None, str(exc))

    if not cancel_event or not cancel_event.is_set():
        for idx, step in enumerate(target.steps, 1):
            cmdline = step.display()
            prog = step.program()
            if prog and not shutil.which(prog) and not Path(prog).exists():
                emit_sys(
                    f"警告：PATH 里找不到命令 {prog}，请确认已安装，"
                    f"或右键『编辑命令 / 产物规则』指定完整路径",
                    "warn",
                )
            if len(target.steps) > 1:
                emit_sys(f"步骤 {idx}/{len(target.steps)}")
            emit(f"$ {cmdline}", "cmd")
            try:
                proc = _spawn(step, target.workdir)
            except (OSError, ValueError) as exc:
                emit_sys(f"启动失败：{exc}", "err")
                error = str(exc)
                exit_code = -1
                break
            if on_process:
                on_process(proc)
            watchdog = _watch_cancel(proc, cancel_event)
            try:
                assert proc.stdout is not None
                while True:
                    if cancel_event and cancel_event.is_set():
                        kill_tree(proc)
                        cancelled = True
                        break
                    raw = proc.stdout.readline()
                    if not raw:
                        break
                    line = strip_ansi(decode_line(raw))
                    emit(line, _line_tag(line))
                proc.wait()
            finally:
                if proc.stdout:
                    try:
                        proc.stdout.close()
                    except OSError:
                        pass
                if watchdog:
                    watchdog.join(timeout=1.0)
                if on_process:
                    on_process(None)

            exit_code = proc.returncode
            # 进程被停止按钮/看门狗杀掉时 readline 会返回 EOF，这里补判取消
            if cancel_event and cancel_event.is_set():
                cancelled = True
            if cancelled:
                emit_sys("已取消", "err")
                break
            if exit_code != 0:
                emit_sys(f"构建失败（退出码 {exit_code}）", "err")
                break

    ok = (exit_code == 0) and not cancelled

    if ok and copy_artifacts_enabled and not cancelled:
        try:
            copied = collect_artifacts(target, out_dir, since=started, exclude_sources=exclude_sources)
        except OSError as exc:
            emit_sys(f"复制产物失败：{exc}", "err")
            error = str(exc)
            ok = False

        if copied:
            emit_sys(f"已复制 {len(copied)} 个文件到 {out_dir}")
            for p in copied[:40]:
                emit_sys(f"  → {p.name}")
            if len(copied) > 40:
                emit_sys(f"  …… 其他 {len(copied) - 40} 个文件")
        else:
            emit_sys("没有找到要复制的构建产物（可在右键菜单里编辑产物规则或输出位置）", "warn")

    duration = time.time() - started
    try:
        log_file = out_dir / "build.log"
        log_file.write_text("\n".join(buffer) + "\n", encoding="utf-8", errors="replace")
    except OSError:
        log_file = None

    if ok:
        emit_sys(f"完成，用时 {duration:.1f}s", "ok")
    elif not cancelled:
        emit_sys(f"结束（失败），用时 {duration:.1f}s", "err")

    return BuildResult(ok, cancelled, exit_code, duration, copied, out_dir, log_file, error)


# --------------------------------------------------------------------------- #
# 产物收集
# --------------------------------------------------------------------------- #


def _excluded(path: Path, exclude_sources: bool) -> bool:
    low = path.name.lower()
    if low.endswith(EXCLUDE_SUFFIXES):
        return True
    if exclude_sources and (low.endswith("-sources.jar") or low.endswith("-javadoc.jar")):
        return True
    return False


def collect_artifacts(
    target: Target,
    out_dir: Path,
    *,
    since: float = 0.0,
    exclude_sources: bool = True,
    limit: int = 300,
) -> list[Path]:
    """按任务的 patterns 找产物；找不到时按"构建开始后被修改的产物文件"兜底。"""
    found: list[Path] = []
    seen: set[str] = set()

    for pattern in target.artifacts:
        if pattern.endswith("/"):
            directory = target.dir / pattern.rstrip("/")
            if not directory.is_dir():
                continue
            for f in _iter_files(directory, exclude_sources, since=0.0):
                if str(f) not in seen:
                    seen.add(str(f))
                    found.append(f)
        else:
            import glob as _glob

            for raw in _glob.glob(str(target.dir / pattern), recursive=True):
                p = Path(raw)
                if p.is_file() and not _excluded(p, exclude_sources) and str(p) not in seen:
                    seen.add(str(p))
                    found.append(p)

    if not found:
        found = _discover_recent(target.dir, since, exclude_sources)

    return _copy_out(found, out_dir, limit=limit)


def _iter_files(directory: Path, exclude_sources: bool, since: float):
    for dirpath, dirnames, filenames in os.walk(directory):
        dirnames[:] = [n for n in dirnames if n not in DISCOVER_SKIP_DIRS]
        for name in sorted(filenames):
            p = Path(dirpath) / name
            try:
                if since and p.stat().st_mtime < since:
                    continue
            except OSError:
                continue
            if _excluded(p, exclude_sources):
                continue
            if p.suffix.lower() in ARTIFACT_EXTS or name.lower().endswith(".tar.gz"):
                yield p


def _discover_recent(project_dir: Path, since: float, exclude_sources: bool, max_depth: int = 4):
    """兜底：在常见产物目录里找构建开始后生成的文件。"""
    results: list[Path] = []
    since = max(0.0, since - 2.0)  # 容忍文件系统时间粒度
    for dirpath, dirnames, filenames in os.walk(project_dir):
        d = Path(dirpath)
        depth = len(d.relative_to(project_dir).parts)
        if depth >= max_depth:
            dirnames[:] = []
        dirnames[:] = [n for n in dirnames if n not in DISCOVER_SKIP_DIRS]
        for name in filenames:
            p = d / name
            low = name.lower()
            if not (p.suffix.lower() in ARTIFACT_EXTS or low.endswith(".tar.gz")):
                continue
            if _excluded(p, exclude_sources):
                continue
            try:
                st = p.stat()
            except OSError:
                continue
            if st.st_mtime < since or st.st_size == 0:
                continue
            results.append(p)
    return results


def _copy_out(files: list[Path], out_dir: Path, *, limit: int = 300) -> list[Path]:
    copied: list[Path] = []
    used: dict[str, int] = {}
    out_dir = Path(out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    for src in files[:limit]:
        name = src.name
        n = used.get(name, 0)
        if n:
            parent = safe_name(src.parent.name)
            name = f"{parent}_{name}"
        used[src.name] = n + 1

        dest = out_dir / name
        if dest.exists():
            try:
                if dest.stat().st_size == src.stat().st_size and dest.stat().st_mtime >= src.stat().st_mtime:
                    copied.append(dest)
                    continue
            except OSError:
                pass
        try:
            shutil.copy2(src, dest)
            copied.append(dest)
        except (OSError, shutil.SameFileError):
            continue
    return copied


# --------------------------------------------------------------------------- #
# 批量执行（并行）
# --------------------------------------------------------------------------- #


def run_batch(targets: list[Target], *, out_mode="sibling", custom_base=None, max_workers=4,
              on_event=None, cancel_event=None, exclude_sources=True, copy_artifacts_enabled=True,
              on_process=None):
    """
    并行执行多个任务。

    - 同一批次里，同一个项目（root）的任务串行，不同项目之间并行；
    - 一个批次没结束时，界面不应再开新批次（由调用方保证）。

    on_event(kind, payload) 会在工作线程里被调用：
      ("start", target) / ("line", (target, text, tag)) / ("done", (target, BuildResult))
    """
    from concurrent.futures import ThreadPoolExecutor

    cancel_event = cancel_event or threading.Event()
    workers = max(1, min(int(max_workers or 1), max(1, len(targets))))

    def emit(kind, payload):
        if on_event:
            on_event(kind, payload)

    def work(t: Target) -> BuildResult:
        emit("start", t)
        out_root = out_root_for(t.root, out_mode, custom_base)

        def log(text, tag="out"):
            emit("line", (t, text, tag))

        res = run_target(
            t, out_root, log=log, cancel_event=cancel_event, on_process=on_process,
            exclude_sources=exclude_sources, copy_artifacts_enabled=copy_artifacts_enabled,
        )
        emit("done", (t, res))
        return res

    collected: dict[str, BuildResult] = {}
    if not targets:
        return []

    from concurrent.futures import FIRST_COMPLETED, wait as wait_futures

    # 同一个项目（root）里的任务串行执行：两个 gradle/maven 构建同时跑会互相抢
    # 同一个工程目录与构建锁，既慢又容易互相踩坏；不同项目之间仍然并行。
    waiting = list(targets)
    running: dict = {}
    busy_roots: set[str] = set()
    with ThreadPoolExecutor(max_workers=workers, thread_name_prefix="qb-build") as pool:
        while waiting or running:
            for t in list(waiting):
                if len(running) >= workers:
                    break
                root_key = str(t.root)
                if root_key in busy_roots:
                    continue          # 该项目的另一个任务正在构建，排队等它
                waiting.remove(t)
                busy_roots.add(root_key)
                running[pool.submit(work, t)] = (t, root_key)
            if not running:
                break                 # 理论上不会发生，避免死循环
            finished, _ = wait_futures(list(running), return_when=FIRST_COMPLETED)
            for fut in finished:
                target_done, root_key = running.pop(fut)
                busy_roots.discard(root_key)
                try:
                    collected[target_done.key] = fut.result()
                except Exception as exc:  # 单个任务异常不应影响其他任务
                    print(f"[QuickBuild] 任务异常：{exc}", file=sys.stderr)
    # 按传入顺序返回结果，方便调用方用 zip(targets, results) 之类的写法
    return [collected[t.key] for t in targets if t.key in collected]


# --------------------------------------------------------------------------- #
# 报告
# --------------------------------------------------------------------------- #


def write_summary(entries: list[tuple[Target, BuildResult]], out_mode="sibling", custom_base=None) -> Path | None:
    """在输出根目录写一份 _summary.txt。"""
    if not entries:
        return None
    root = entries[0][0].root
    out_root = out_root_for(root, out_mode, custom_base)
    try:
        out_root.mkdir(parents=True, exist_ok=True)
    except OSError:
        return None
    lines = [f"快速构建报告  {time.strftime('%Y-%m-%d %H:%M:%S')}", f"项目：{root}", ""]
    ok = sum(1 for _t, r in entries if r.ok)
    lines.append(f"成功 {ok} / 共 {len(entries)}")
    lines.append("")
    for t, r in entries:
        status = "成功" if r.ok else ("取消" if r.cancelled else "失败")
        lines.append(f"[{status}] {t.title()}  ({t.kind_label})  {r.duration:.1f}s  产物 {len(r.copied)} 个")
        lines.append(f"    命令：{t.command_text()}")
        if r.copied:
            lines.append(f"    输出：{r.out_dir}")
            for p in r.copied[:20]:
                lines.append(f"      - {p.name}")
        if r.error:
            lines.append(f"    错误：{r.error}")
    path = out_root / "_summary.txt"
    try:
        path.write_text("\n".join(lines) + "\n", encoding="utf-8")
        return path
    except OSError:
        return None


# --------------------------------------------------------------------------- #
# 命令行自检
# --------------------------------------------------------------------------- #


def _main(argv: list[str]) -> int:
    usage = (
        "用法:\n"
        "  python qb_core.py --scan <目录> [深度]               只扫描并列出任务\n"
        "  python qb_core.py --scan <目录> [深度] --run <序号>  扫描后构建指定任务\n"
        "  python qb_core.py --scan <目录> [深度] --run all     扫描后构建全部任务"
    )
    if len(argv) < 2 or argv[1] in ("-h", "--help"):
        print(usage)
        return 0
    if argv[1] != "--scan":
        print(usage)
        return 2

    root = Path(argv[2])
    positional = [a for a in argv[3:] if not a.startswith("--")]
    depth = int(positional[0]) if positional and positional[0].isdigit() else 3
    run: str | None = None
    if "--run" in argv:
        idx = argv.index("--run")
        run = argv[idx + 1] if len(argv) > idx + 1 else "all"

    targets = detect_targets(root, max_depth=depth)
    print(f"扫描 {root} 深度 {depth}：发现 {len(targets)} 个任务")
    for i, t in enumerate(targets, 1):
        print(f"{i:>2}. [{t.kind_label}] {t.display()}")
        extra = f"   (任务目录 {t.dir})" if t.workdir != t.dir else ""
        print(f"      cwd: {t.workdir}{extra}")
        print(f"      cmd: {t.command_text()}")
        print(f"      out: {out_root_for(t.root)} / {t.out_subdir or '(未定)'}")
        if t.alternatives:
            print(f"      备选: {', '.join(a[0] for a in t.alternatives)}")
        if t.note:
            print(f"      备注: {t.note}")

    if run is None:
        return 0
    if run == "all":
        picked = targets
    else:
        try:
            picked = [targets[int(run) - 1]]
        except (ValueError, IndexError):
            print(f"序号无效：{run}")
            return 2
    if not picked:
        return 0

    def show(text: str, tag: str = "out") -> None:
        if tag == "cmd":  # 命令行本身已带 "$ " 前缀
            print(text, flush=True)
            return
        prefix = {"err": "!! ", "warn": ".. "}.get(tag, "   ")
        print(prefix + text, flush=True)

    fails = 0
    for t in picked:
        print()
        result = run_target(t, out_root_for(t.root), log=show)
        fails += 0 if result.ok else 1
    return 0 if fails == 0 else 1


if __name__ == "__main__":
    raise SystemExit(_main(sys.argv))
