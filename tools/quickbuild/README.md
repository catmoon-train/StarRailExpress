# 快速构建 · QuickBuild

一个单文件、零依赖（只用 Python 标准库 tkinter）的构建小工具：

1. **添加文件夹** → 自动扫描里面的构建脚本 / 构建描述文件；
2. 勾选任务 → **并行构建**，日志实时滚动；
3. 构建成功后，把产物复制到项目**旁边的文件夹**里，方便直接取用。

## 运行

```bash
python quickbuild.py            # 直接运行
python quickbuild.py D:/某项目   # 顺便把这个文件夹加进列表
```

也可以双击本目录里的 `quickbuild.bat`（用 `pythonw` 启动，不留黑窗口）。
如果双击后没反应，改用 `python quickbuild.py` 在终端里跑，就能看到报错。

## 能识别的构建方式

| 依据文件 | 默认命令 |
| --- | --- |
| `build.gradle` / `build.gradle.kts` / `settings.gradle*` | 就近找 `gradlew(.bat)` → `gradlew build --console=plain` |
| `pom.xml` | `mvn -B package` |
| `package.json`（需有 `scripts.build`） | 按锁文件选 `npm run build` / `pnpm run build` / `yarn build` / `bun run build` |
| `Cargo.toml` | `cargo build --release` |
| `*.sln` / `*.csproj` | `dotnet build <项目> -c Release` |
| `CMakeLists.txt` | `cmake -S . -B build` + `cmake --build build --config Release` |
| `go.mod` | `go build ./...` |
| `pyproject.toml` / `setup.py` | `python -m build` |
| `Makefile` / `GNUmakefile` | `make`（Windows 上自动改用 `mingw32-make` / `nmake`） |
| `build.bat` / `build.cmd` / `build.sh` / `build.ps1` 等构建脚本 | 直接执行该脚本 |

同一个目录里出现多种方式时，按 Gradle → Maven → .NET → Cargo → Node → CMake → Go → Python → Make → 脚本 取一个作为主任务，其余的放进「编辑命令」里的**备选**下拉框，一键切换。
像 `build.bat` 这种只是 `gradlew build` 包装脚本的，不会重复成第二个任务；而 `build-compat.bat`（构建别的子项目）会被识别成独立任务。
关于 Gradle 多项目：如果某个目录是被**别的** `settings.gradle` include 进来的子项目（包括
`project(':x').projectDir = file('../x')` 这种放在根目录之外的写法），会自动改用那个 settings 根目录的
`gradlew` 执行 `<gradlew> :项目名:build`，工作目录也切到 settings 根；找不到 `gradlew` / `gradle` 时，
任务类型列会打 ⚠，提示右键「编辑命令」手写路径。
某个目录自身就是独立项目（有自己的 settings，或上面没有 settings）时，仍用它自己/上层的 `gradlew build`。


## 产物去哪了

默认输出到**项目旁边**：`<父目录>/<项目名>-build/<任务子目录>/`。
工具栏「输出到」可以切换：
- `与项目同级（旁边的文件夹）`：`D:/work/mod-a` → `D:/work/mod-a-build/`
- `项目内的 quickbuild-dist`：`D:/work/mod-a/quickbuild-dist/`
- `自定义目录下的 <项目名>-build`：选择一个基础目录，如 `D:/drop/` → `D:/drop/mod-a-build/`

每个任务子目录里除了产物，还会有 `build.log`；输出根目录还会有一次批量构建的 `_summary.txt`。

产物默认按各构建系统的惯例路径收集（`build/libs/*.jar`、`target/*.jar`、`dist/`、`target/release/*` …），并默认排除 `-sources.jar` / `-javadoc.jar`。
收集不到时，会退回到「构建开始后新生成、位于常见产物目录里的文件」，所以自定义脚本也能捞到产物。
规则不适用就右键任务 → **编辑命令 / 产物规则**，写成相对任务目录的 glob（逗号分隔，目录末尾加 `/` 表示整目录复制）。

## 界面操作

- 点「选」列（或选中行按空格）勾选/取消任务；点文件夹行的「选」列可一键全选/全不选该文件夹。
- 双击任务 → 打开它的单独日志页；「合并输出」页是按 `[项目/子目录]` 前缀混排的总日志。
- 右键任务：构建此任务 / 编辑命令 / 打开任务目录 / 打开输出目录 / 清除输出 / 复制命令。
- 工具栏：并行数（1–16）、扫描深度（1–6）、是否复制产物、排除 sources、搜索过滤、开始/停止。
- 停止会连同整个进程树一起结束（Gradle 的 daemon 之外，构建进程会被 `taskkill /T` 干掉），不会卡住。
- **构建互斥**：一批构建没结束时不能开新的一批 —— 开始按钮变成「⏳ 构建中」并禁用，右键「构建此任务」变灰，
  真点了也会在状态栏提示“已有构建在进行中”，不会静默失败。
- **同一项目串行**：同一批里属于同一个文件夹的任务会排队串行执行（两个 gradle 构建同时跑会互相抢构建锁），
  不同文件夹之间仍然按「并行数」并发。

## 配置文件

默认写在脚本旁边 `quickbuild_config.json`（可用环境变量 `QUICKBUILD_CONFIG` 指定别处），保存：文件夹列表、并行数、扫描深度、输出模式、勾选状态、自定义命令/产物规则、窗口大小。

## 已知边界

- 只扫描到「扫描深度」层，`build/`、`node_modules/`、`.git/` 等目录会跳过。
- 命令行里的路径包含空格也能正常工作（内部统一用参数列表 + `cmd /c` 调用 `.bat`）。
- 多个 Gradle 任务并行时内存占用会明显上升，机器吃不消就把并行数降到 2–3。
- 仅支持 tkinter 环境（Windows 官方 Python 自带；Linux 需 `python3-tk`）。
