using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Runtime.InteropServices;
using System.Threading;
using System.Windows.Forms;
using Microsoft.Win32;

namespace LocalPrivacyScreen
{
    internal static class Native
    {
        internal const uint ExcludeFromCapture = 0x11;
        [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
        private struct VersionInfo
        {
            internal uint Size, Major, Minor, Build, Platform;
            [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 128)]
            internal string ServicePack;
        }
        [DllImport("ntdll.dll", CharSet = CharSet.Unicode)]
        private static extern int RtlGetVersion(ref VersionInfo version);
        [DllImport("user32.dll", SetLastError = true)]
        [return: MarshalAs(UnmanagedType.Bool)]
        internal static extern bool SetProcessDpiAwarenessContext(IntPtr context);
        [DllImport("dwmapi.dll")]
        private static extern int DwmIsCompositionEnabled([MarshalAs(UnmanagedType.Bool)] out bool enabled);
        [DllImport("user32.dll", SetLastError = true)]
        [return: MarshalAs(UnmanagedType.Bool)]
        internal static extern bool SetWindowDisplayAffinity(IntPtr window, uint affinity);
        [DllImport("user32.dll", SetLastError = true)]
        [return: MarshalAs(UnmanagedType.Bool)]
        internal static extern bool GetWindowDisplayAffinity(IntPtr window, out uint affinity);
        [DllImport("user32.dll", SetLastError = true)]
        [return: MarshalAs(UnmanagedType.Bool)]
        internal static extern bool SetLayeredWindowAttributes(IntPtr window, uint color, byte alpha, uint flags);
        [DllImport("user32.dll", SetLastError = true)]
        [return: MarshalAs(UnmanagedType.Bool)]
        internal static extern bool SetWindowPos(IntPtr window, IntPtr after, int x, int y, int width, int height, uint flags);
        [DllImport("user32.dll", EntryPoint = "GetWindowLongW")]
        internal static extern int GetWindowStyle(IntPtr window, int index);
        [StructLayout(LayoutKind.Sequential)]
        internal struct WindowRect
        {
            internal int Left, Top, Right, Bottom;
            internal Rectangle ToRectangle() { return Rectangle.FromLTRB(Left, Top, Right, Bottom); }
        }
        [DllImport("user32.dll")]
        [return: MarshalAs(UnmanagedType.Bool)]
        internal static extern bool IsWindowVisible(IntPtr window);
        [DllImport("user32.dll", SetLastError = true)]
        [return: MarshalAs(UnmanagedType.Bool)]
        internal static extern bool GetWindowRect(IntPtr window, out WindowRect rect);
        [DllImport("user32.dll", SetLastError = true)]
        [return: MarshalAs(UnmanagedType.Bool)]
        internal static extern bool GetClientRect(IntPtr window, out WindowRect rect);
        [DllImport("user32.dll", EntryPoint = "SendMessageW")]
        internal static extern IntPtr SendWindowMessage(IntPtr window, uint message, IntPtr wParam, IntPtr lParam);
        [DllImport("user32.dll", SetLastError = true)]
        [return: MarshalAs(UnmanagedType.Bool)]
        internal static extern bool RegisterHotKey(IntPtr window, int id, uint modifiers, uint key);
        [DllImport("user32.dll")]
        [return: MarshalAs(UnmanagedType.Bool)]
        internal static extern bool UnregisterHotKey(IntPtr window, int id);
        [StructLayout(LayoutKind.Sequential)]
        internal struct PaintInfo
        {
            internal IntPtr Dc;
            internal int Erase;
            internal WindowRect PaintRect;
            internal int Restore, IncrementalUpdate;
            [MarshalAs(UnmanagedType.ByValArray, SizeConst = 32)]
            internal byte[] Reserved;
        }
        [DllImport("user32.dll")]
        internal static extern IntPtr BeginPaint(IntPtr window, out PaintInfo paint);
        [DllImport("user32.dll")]
        [return: MarshalAs(UnmanagedType.Bool)]
        internal static extern bool EndPaint(IntPtr window, ref PaintInfo paint);
        [DllImport("user32.dll")]
        internal static extern int FillRect(IntPtr dc, ref WindowRect rect, IntPtr brush);
        [DllImport("gdi32.dll")]
        internal static extern IntPtr GetStockObject(int index);

        internal static void CheckPlatform()
        {
            var version = new VersionInfo { Size = (uint)Marshal.SizeOf(typeof(VersionInfo)) };
            if (RtlGetVersion(ref version) != 0)
                throw new InvalidOperationException("无法确认 Windows 版本，未启用隐私屏。");
            if (version.Major < 10 || (version.Major == 10 && version.Build < 19041))
                throw new NotSupportedException("需要 Windows 10 2004（内部版本 19041）或更新版本。旧版系统的捕获排除可能让远程也变黑，已拒绝启动。");
            bool composition;
            if (DwmIsCompositionEnabled(out composition) != 0 || !composition)
                throw new NotSupportedException("桌面合成不可用，无法启用捕获排除。");
        }
        internal static void Check(bool success, string operation)
        {
            if (!success) throw new Win32Exception(Marshal.GetLastWin32Error(), operation);
        }
    }

    internal interface IPrivacySurface : IDisposable
    {
        void Prepare();
        void ShowExcluded();
        void KeepAbove();
    }

    // 使用原生顶层窗口并自行绘制纯黑，不经过 WinForms 窗体装饰和最大化逻辑。
    internal sealed class BlackOverlay : NativeWindow, IPrivacySurface
    {
        private readonly Rectangle fullBounds;
        private bool showRequested;
        internal bool Visible { get { return Handle != IntPtr.Zero && Native.IsWindowVisible(Handle); } }
        internal BlackOverlay(Rectangle bounds) { fullBounds = bounds; }
        public void Prepare()
        {
            if (Handle == IntPtr.Zero)
            {
                CreateHandle(new CreateParams
                {
                    ClassName = "STATIC", Caption = string.Empty,
                    // WS_POPUP | SS_BLACKRECT：无标题栏和边框；实际颜色由自定义绘制保证。
                    Style = unchecked((int)0x80000000) | 4,
                    // 分层、点击穿透、不激活、工具窗口、置顶，不占任务栏。
                    ExStyle = 0x00080000 | 0x00000020 | 0x08000000 | 0x00000080 | 0x00000008,
                    X = fullBounds.X, Y = fullBounds.Y,
                    Width = fullBounds.Width, Height = fullBounds.Height,
                    Parent = IntPtr.Zero
                });
            }
            Native.Check(Native.SetLayeredWindowAttributes(Handle, 0, 255, 2), "无法设置全屏黑色窗口的不透明度");
            Native.Check(Native.SetWindowDisplayAffinity(Handle, Native.ExcludeFromCapture), "Windows 拒绝设置捕获排除");
            VerifyExclusion();
        }
        private void VerifyExclusion()
        {
            uint affinity;
            Native.Check(Native.GetWindowDisplayAffinity(Handle, out affinity), "无法读取窗口捕获排除状态");
            if (affinity != Native.ExcludeFromCapture)
                throw new InvalidOperationException("窗口未获得完整捕获排除，不使用普通黑色遮罩作为替代。");
        }
        public void ShowExcluded()
        {
            showRequested = true;
            KeepAbove();
        }
        public void KeepAbove()
        {
            Native.Check(Native.SetWindowDisplayAffinity(Handle, Native.ExcludeFromCapture), "无法重新设置隐私屏捕获排除");
            VerifyExclusion();
            // 不使用工作区或最大化尺寸；每次都覆盖完整屏幕，包括任务栏所在区域。
            uint flags = 0x0010 | 0x0020 | (showRequested ? 0x0040U : 0U);
            Native.Check(Native.SetWindowPos(Handle, new IntPtr(-1), fullBounds.X, fullBounds.Y,
                fullBounds.Width, fullBounds.Height, flags), "无法维持隐私屏全屏覆盖");
            Native.WindowRect window, client;
            Native.Check(Native.GetWindowRect(Handle, out window), "无法确认全屏窗口范围");
            Native.Check(Native.GetClientRect(Handle, out client), "无法确认纯黑客户区范围");
            if (window.ToRectangle() != fullBounds || client.ToRectangle() != new Rectangle(Point.Empty, fullBounds.Size))
                throw new InvalidOperationException("纯黑窗口未完整覆盖指定显示器，将继续重试。");
        }
        private void PaintBlack(IntPtr dc)
        {
            Native.WindowRect client;
            if (dc != IntPtr.Zero && Native.GetClientRect(Handle, out client))
                Native.FillRect(dc, ref client, Native.GetStockObject(4)); // BLACK_BRUSH，不受主题影响。
        }
        protected override void WndProc(ref Message message)
        {
            if (message.Msg == 0x000F) // WM_PAINT
            {
                Native.PaintInfo paint;
                IntPtr dc = Native.BeginPaint(Handle, out paint);
                try { PaintBlack(dc); }
                finally { Native.EndPaint(Handle, ref paint); }
                message.Result = IntPtr.Zero;
                return;
            }
            if (message.Msg == 0x0014 || message.Msg == 0x0318) // WM_ERASEBKGND / WM_PRINTCLIENT
            {
                PaintBlack(message.WParam);
                message.Result = message.Msg == 0x0014 ? new IntPtr(1) : IntPtr.Zero;
                return;
            }
            base.WndProc(ref message);
        }
        public void Dispose() { if (Handle != IntPtr.Zero) DestroyHandle(); }
    }

    // 新窗口全部准备并显示后再撤掉旧窗口；重建失败不主动暴露原桌面。
    internal sealed class PrivacySession : IDisposable
    {
        private List<IPrivacySurface> surfaces = new List<IPrivacySurface>();
        internal bool Active { get { return surfaces.Count > 0; } }
        internal static bool HasTimedOut(bool unlimited, TimeSpan elapsed)
        {
            return !unlimited && elapsed >= TimeSpan.FromSeconds(60);
        }
        internal void Start(Rectangle[] bounds, Func<Rectangle, IPrivacySurface> factory)
        {
            if (Active) throw new InvalidOperationException("隐私屏已经运行。");
            try
            {
                if (bounds.Length == 0) throw new InvalidOperationException("没有可覆盖的显示器。");
                foreach (var rect in bounds)
                {
                    if (rect.Width <= 0 || rect.Height <= 0) throw new InvalidOperationException("无效的显示器尺寸。");
                    var surface = factory(rect);
                    surfaces.Add(surface);
                    surface.Prepare();
                }
                foreach (var surface in surfaces) surface.ShowExcluded();
            }
            catch { Stop(); throw; }
        }
        internal void Rebuild(Rectangle[] bounds, Func<Rectangle, IPrivacySurface> factory)
        {
            if (!Active) throw new InvalidOperationException("隐私屏已停止，不自动重新开启。");
            using (var replacement = new PrivacySession())
            {
                replacement.Start(bounds, factory);
                var previous = surfaces;
                surfaces = replacement.surfaces;
                replacement.surfaces = previous;
            }
        }
        internal void Pulse()
        {
            Exception failure = null;
            foreach (var surface in surfaces)
            {
                try { surface.KeepAbove(); }
                catch (Exception ex) { failure = ex; }
            }
            if (failure != null) throw new InvalidOperationException("隐私屏捕获排除或置顶尚未确认。", failure);
        }
        internal void Stop()
        {
            // 先释放所有权，即使释放某个窗口失败，也继续撤掉其他窗口。
            var previous = surfaces.ToArray();
            surfaces.Clear();
            Exception failure = null;
            foreach (var surface in previous)
            {
                try { surface.Dispose(); }
                catch (Exception ex) { failure = ex; }
            }
            if (failure != null) throw new InvalidOperationException("清理隐私屏窗口失败，请退出程序。", failure);
        }
        public void Dispose() { Stop(); }
    }

    internal sealed class MainForm : Form
    {
        private readonly CheckBox persistent = new CheckBox
        {
            Name = "PersistentMode", AutoSize = true,
            Text = "我已通过向日葵测试，持续运行直到手动恢复"
        };
        private readonly Button start = new Button { Name = "StartPrivacy", Text = "开始 60 秒测试", AutoSize = true };
        private readonly Button stop = new Button { Text = "恢复显示", AutoSize = true };
        private readonly Label status = new Label { AutoSize = true, Text = "未启动隐私屏。", Name = "PrivacyStatus" };
        private readonly TextBox log = new TextBox { Multiline = true, ReadOnly = true, ScrollBars = ScrollBars.Vertical, Dock = DockStyle.Fill };
        private readonly System.Windows.Forms.Timer timer = new System.Windows.Forms.Timer { Interval = 250 };
        private readonly PrivacySession session;
        private readonly Stopwatch elapsed = new Stopwatch();
        private readonly EventWaitHandle restoreSignal;
        private readonly NotifyIcon tray = new NotifyIcon();
        private bool hotkey, platformReady, unlimited, subscribed, cleanedUp;
        private int lastPulse;
        private bool rebuildPending;
        private string lastRevalidationError;

        internal MainForm(EventWaitHandle signal, PrivacySession privacySession = null)
        {
            restoreSignal = signal;
            session = privacySession ?? new PrivacySession();
            Text = "本地隐私屏 · 不依赖 DDC";
            Font = new Font("Microsoft YaHei UI", 9F);
            ClientSize = new Size(720, 420);
            MinimumSize = new Size(650, 440);
            StartPosition = FormStartPosition.CenterScreen;
            var layout = new TableLayoutPanel { Dock = DockStyle.Fill, Padding = new Padding(16), ColumnCount = 1, RowCount = 5 };
            layout.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 100));
            layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 120));
            layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 36));
            layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 46));
            layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 32));
            layout.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
            layout.Controls.Add(new Label { Dock = DockStyle.Fill, Text =
                "独立实验方案，不调用或修改向日葵；无远程连接也能运行。\n"
                + "覆盖全部显示器，黑色窗口请求从 Windows 屏幕捕获中排除。\n"
                + "API 成功不代表向日葵兼容：请先测试远程画面和鼠标键盘。\n"
                + "恢复：Ctrl + Alt + F12，或点击恢复按钮／托盘菜单。\n"
                + "不锁定输入，不是安全隔离；本地指针、系统提示仍可能出现。" }, 0, 0);
            layout.Controls.Add(persistent, 0, 1);
            var actions = new FlowLayoutPanel { Dock = DockStyle.Fill };
            actions.Controls.Add(start);
            actions.Controls.Add(stop);
            layout.Controls.Add(actions, 0, 2);
            layout.Controls.Add(status, 0, 3);
            layout.Controls.Add(log, 0, 4);
            Controls.Add(layout);
            persistent.CheckedChanged += delegate { UpdateControls(); };
            start.Click += delegate { StartPrivacy(); };
            stop.Click += delegate { StopPrivacy("已手动恢复显示。"); };
            var menu = new ContextMenuStrip();
            menu.Items.Add("恢复显示", null, delegate { StopPrivacy("已通过托盘恢复显示。"); });
            menu.Items.Add("显示控制窗口", null, delegate { Show(); WindowState = FormWindowState.Normal; Activate(); });
            menu.Items.Add("恢复并退出", null, delegate { Close(); });
            tray.Icon = SystemIcons.Application;
            tray.Text = "本地隐私屏：未启动";
            tray.ContextMenuStrip = menu;
            tray.DoubleClick += delegate { StopPrivacy("已通过托盘恢复显示。"); Show(); WindowState = FormWindowState.Normal; Activate(); };
            timer.Tick += delegate { TickPrivacy(); };
            Shown += delegate
            {
                try { Native.CheckPlatform(); platformReady = true; WriteLog("Windows 平台检查通过；尚未开启隐私屏。"); }
                catch (Exception ex) { WriteLog(ex.Message); }
                tray.Visible = true;
                SystemEvents.DisplaySettingsChanged += DisplaySettingsChanged;
                SystemEvents.SessionSwitch += SessionChanged;
                subscribed = true;
                UpdateControls();
            };
            FormClosing += delegate { StopPrivacy("程序退出，已撤掉隐私屏窗口。"); };
            FormClosed += delegate { Cleanup(); };
            UpdateControls();
        }
        protected override void OnHandleCreated(EventArgs e)
        {
            base.OnHandleCreated(e);
            hotkey = Native.RegisterHotKey(Handle, 1, 0x4003, (uint)Keys.F12);
            if (!hotkey) WriteLog("Ctrl+Alt+F12 注册失败，已禁止启动隐私屏。请先关闭旧版 DDC 工具或占用此快捷键的软件，再重新打开本程序。");
            if (session.Active)
            {
                if (hotkey) RequestRevalidation("控制窗口句柄重建");
                else StopPrivacy("恢复快捷键注册失败，已撤掉隐私屏窗口。");
            }
            UpdateControls();
        }
        protected override void OnHandleDestroyed(EventArgs e)
        {
            if (session.Active && !RecreatingHandle) StopPrivacy("控制窗口关闭，已恢复显示。");
            if (hotkey) Native.UnregisterHotKey(Handle, 1);
            hotkey = false;
            base.OnHandleDestroyed(e);
        }
        protected override void WndProc(ref Message message)
        {
            if (message.Msg == 0x0312 && message.WParam.ToInt32() == 1)
                StopPrivacy("已通过 Ctrl+Alt+F12 恢复显示。");
            base.WndProc(ref message);
        }
        private void WriteLog(string text)
        {
            log.AppendText(DateTime.Now.ToString("HH:mm:ss ") + text + Environment.NewLine);
        }
        private void UpdateControls()
        {
            start.Enabled = platformReady && hotkey && !session.Active;
            start.Text = persistent.Checked ? "开始持续隐私屏" : "开始 60 秒测试";
            persistent.Enabled = !session.Active;
        }
        private void StartPrivacy()
        {
            if (!platformReady || !hotkey || session.Active) return;
            if (persistent.Checked && MessageBox.Show(this,
                "只有确认向日葵能正常显示并操作桌面后，才应持续运行。\n本次不会在 60 秒后自动退出。\n恢复快捷键：Ctrl+Alt+F12。是否继续？",
                "持续隐私屏", MessageBoxButtons.YesNo, MessageBoxIcon.Warning,
                MessageBoxDefaultButton.Button2) != DialogResult.Yes) return;
            try
            {
                Native.CheckPlatform();
                var bounds = ReadDisplayBounds();
                unlimited = persistent.Checked;
                elapsed.Restart();
                lastPulse = 0;
                rebuildPending = false;
                lastRevalidationError = null;
                restoreSignal.WaitOne(0);
                session.Start(bounds, delegate(Rectangle rect) { return new BlackOverlay(rect); });
                timer.Start();
                tray.Text = unlimited ? "本地隐私屏：持续运行" : "本地隐私屏：60 秒测试";
                WriteLog("已启动，覆盖 " + bounds.Length + " 个显示器；Windows 接受捕获排除。"
                    + (unlimited ? "持续运行，需手动恢复。" : "60 秒后自动撤掉黑色窗口。")
                    + " 请确认向日葵实际画面和输入；API 成功不代表远程一定不黑屏。");
                UpdateControls();
                UpdateStatus();
                // 控制窗口退到托盘；不让它的标题栏和窗口按钮留在桌面。
                Hide();
            }
            catch (Exception ex) { StopPrivacy("启动失败，已撤掉所有隐私屏窗口：" + ex.Message); }
        }
        private void TickPrivacy()
        {
            try
            {
                if (restoreSignal.WaitOne(0)) { StopPrivacy("已通过命令行恢复显示。"); return; }
                if (!session.Active) return;
                if (PrivacySession.HasTimedOut(unlimited, elapsed.Elapsed))
                {
                    StopPrivacy("60 秒测试结束，已自动撤掉隐私屏窗口。");
                    return;
                }
                int seconds = (int)elapsed.Elapsed.TotalSeconds;
                if (seconds != lastPulse)
                {
                    lastPulse = seconds;
                    try
                    {
                        if (rebuildPending)
                        {
                            Native.CheckPlatform();
                            var bounds = ReadDisplayBounds();
                            session.Rebuild(bounds, delegate(Rectangle rect) { return new BlackOverlay(rect); });
                            rebuildPending = false;
                            WriteLog("隐私屏已重新覆盖 " + bounds.Length + " 个显示器并验证捕获排除；原运行模式与计时不变。请确认远程画面。");
                        }
                        else session.Pulse();
                        lastRevalidationError = null;
                    }
                    catch (Exception ex)
                    {
                        rebuildPending = true;
                        string error = ex.GetBaseException().Message;
                        if (error != lastRevalidationError)
                            WriteLog("暂时无法确认捕获排除，保留现有黑色窗口并每秒重试；不保证安全桌面覆盖或远程捕获正常：" + error);
                        lastRevalidationError = error;
                    }
                }
                UpdateStatus();
            }
            catch (Exception ex) { StopPrivacy("隐私屏异常，已停止：" + ex.Message); }
        }
        private void UpdateStatus()
        {
            status.Text = !session.Active ? "未启动隐私屏。"
                : rebuildPending ? "保留现有黑屏，等待重新验证捕获排除；Ctrl+Alt+F12 恢复。"
                : unlimited ? "持续运行中；Ctrl+Alt+F12 恢复。"
                : "测试中，约 " + Math.Max(0, 60 - (int)elapsed.Elapsed.TotalSeconds) + " 秒后自动恢复。";
        }
        private void StopPrivacy(string reason)
        {
            timer.Stop();
            elapsed.Stop();
            rebuildPending = false;
            lastRevalidationError = null;
            try { session.Stop(); }
            catch (Exception ex) { reason += " " + ex.Message; }
            tray.Text = "本地隐私屏：未启动";
            UpdateStatus();
            UpdateControls();
            WriteLog(reason);
        }
        private static Rectangle[] ReadDisplayBounds()
        {
            var screens = Screen.AllScreens;
            var bounds = new Rectangle[screens.Length];
            for (int i = 0; i < screens.Length; i++) bounds[i] = screens[i].Bounds;
            return bounds;
        }
        private void DisplaySettingsChanged(object sender, EventArgs e) { QueueRevalidation("显示器配置改变"); }
        private void SessionChanged(object sender, SessionSwitchEventArgs e) { QueueRevalidation("Windows 会话状态改变（" + e.Reason + "）"); }
        internal void RequestRevalidation(string reason)
        {
            if (IsDisposed || Disposing || !session.Active) return;
            if (!rebuildPending) WriteLog(reason + "，保留现有隐私屏，将重新验证捕获排除并更新覆盖范围。");
            rebuildPending = true;
            lastPulse = -1;
            UpdateStatus();
        }
        private void QueueRevalidation(string reason)
        {
            if (IsDisposed || Disposing || !IsHandleCreated) return;
            try { BeginInvoke(new Action(delegate { RequestRevalidation(reason); })); }
            catch (InvalidOperationException) { }
        }
        private void Cleanup()
        {
            if (cleanedUp) return;
            cleanedUp = true;
            if (subscribed)
            {
                SystemEvents.DisplaySettingsChanged -= DisplaySettingsChanged;
                SystemEvents.SessionSwitch -= SessionChanged;
                subscribed = false;
            }
            timer.Dispose();
            var menu = tray.ContextMenuStrip;
            tray.Visible = false;
            tray.Dispose();
            if (menu != null) menu.Dispose();
        }
        protected override void Dispose(bool disposing)
        {
            if (disposing)
            {
                session.Dispose();
                Cleanup();
            }
            base.Dispose(disposing);
        }
    }

    internal static class Program
    {
        private const string RestoreEvent = "Local\\LocalPrivacyScreen.Restore";
        [STAThread]
        private static int Main(string[] args)
        {
            if (args.Length == 1 && args[0] == "--restore")
            {
                try { using (var signal = EventWaitHandle.OpenExisting(RestoreEvent)) signal.Set(); return 0; }
                catch (WaitHandleCannotBeOpenedException) { return 0; }
                catch (UnauthorizedAccessException)
                {
                    MessageBox.Show("恢复命令需要与隐私屏使用同一 Windows 用户、会话和权限级别。");
                    return 1;
                }
            }
            bool diagnostic = args.Length == 2 && (args[0] == "--self-test" || args[0] == "--probe");
            try
            {
                Native.CheckPlatform();
                Native.Check(Native.SetProcessDpiAwarenessContext(new IntPtr(-4)), "无法启用逐显示器 DPI 感知，已拒绝启动以免覆盖范围错误");
                Application.EnableVisualStyles();
                Application.SetCompatibleTextRenderingDefault(false);
                if (diagnostic)
                {
                    string details = string.Empty;
                    if (args[0] == "--self-test") SelfTest.Run();
                    else
                    {
                        // 仅创建未显示的窗口并验证属性，不遮屏、不写显示器电源。
                        using (var overlay = new BlackOverlay(new Rectangle(-32, -16, 16, 16)))
                        {
                            overlay.Prepare();
                            // 模拟合成器丢失窗口属性，再验证维持逻辑可重新申请。
                            Native.Check(Native.SetWindowDisplayAffinity(overlay.Handle, 0), "无法模拟捕获排除属性重置");
                            overlay.KeepAbove();
                            if (overlay.Visible) throw new Exception("Probe window must stay hidden.");
                            if ((Native.GetWindowStyle(overlay.Handle, -16) & 0x00CF0000) != 0)
                                throw new Exception("Overlay must not have a caption, system menu, frame or minimize/maximize buttons.");
                            if ((Native.GetWindowStyle(overlay.Handle, -20) & 0x00040000) != 0)
                                throw new Exception("Overlay must not have an application taskbar entry.");
                            // 仅在内存中请求原生客户区绘制，确认黑色填充，无可见窗口。
                            using (var image = new Bitmap(16, 16))
                            {
                                using (var graphics = Graphics.FromImage(image))
                                {
                                    graphics.Clear(Color.Magenta);
                                    IntPtr dc = graphics.GetHdc();
                                    try { Native.SendWindowMessage(overlay.Handle, 0x0318, dc, new IntPtr(4)); }
                                    finally { graphics.ReleaseHdc(dc); }
                                }
                                details = "; native paint sample=" + image.GetPixel(0, 0);
                                for (int y = 0; y < image.Height; y++)
                                    for (int x = 0; x < image.Width; x++)
                                    {
                                        // GDI 绘制到 DIB 不保证保留 Alpha；窗口不透明度由分层属性的 255 决定。
                                        Color pixel = image.GetPixel(x, y);
                                        if (pixel.R != 0 || pixel.G != 0 || pixel.B != 0)
                                            throw new Exception("Native fullscreen client must paint solid black; actual=" + pixel);
                                    }
                            }
                        }
                        foreach (var screen in Screen.AllScreens)
                            using (var fullscreen = new BlackOverlay(screen.Bounds))
                            {
                                fullscreen.Prepare();
                                fullscreen.KeepAbove();
                                if (fullscreen.Visible) throw new Exception("Fullscreen probe must stay hidden.");
                            }
                    }
                    File.WriteAllText(args[1], "PASS: " + args[0] + "; no visible overlays, no monitor power writes" + details + ".\n");
                    return 0;
                }
                bool created;
                using (var mutex = new Mutex(true, "Local\\LocalPrivacyScreen.Instance", out created))
                {
                    if (!created) { MessageBox.Show("本地隐私屏已在运行。请使用托盘菜单或恢复快捷键。"); return 0; }
                    using (var signal = new EventWaitHandle(false, EventResetMode.AutoReset, RestoreEvent))
                    using (var form = new MainForm(signal)) Application.Run(form);
                }
                return 0;
            }
            catch (Exception ex)
            {
                if (diagnostic) File.WriteAllText(args[1], ex.ToString());
                else MessageBox.Show(ex.Message, "隐私屏未启动", MessageBoxButtons.OK, MessageBoxIcon.Error);
                return 1;
            }
        }
    }

    internal static class SelfTest
    {
        private sealed class FakeSurface : IPrivacySurface
        {
            internal bool Prepared, Shown, Disposed, FailPrepare, FailShow, FailPulse;
            internal int Pulses;
            internal Action BeforeShow;
            public void Prepare() { if (FailPrepare) throw new Exception("prepare"); Prepared = true; }
            public void ShowExcluded() { Check(Prepared); if (BeforeShow != null) BeforeShow(); if (FailShow) throw new Exception("show"); Shown = true; }
            public void KeepAbove() { if (FailPulse) throw new Exception("pulse"); Pulses++; }
            public void Dispose() { Disposed = true; Shown = false; }
        }
        private static void Check(bool condition) { if (!condition) throw new Exception("Privacy self-test assertion failed."); }
        private static void MustFail(Action action)
        {
            bool failed = false;
            try { action(); } catch { failed = true; }
            Check(failed);
        }
        internal static void Run()
        {
            Check(!PrivacySession.HasTimedOut(false, TimeSpan.FromSeconds(59.9)));
            Check(PrivacySession.HasTimedOut(false, TimeSpan.FromSeconds(60)));
            Check(PrivacySession.HasTimedOut(false, TimeSpan.FromSeconds(61)));
            Check(!PrivacySession.HasTimedOut(true, TimeSpan.FromDays(1)));
            var bounds = new[] { new Rectangle(-1920, 0, 1920, 1080), new Rectangle(0, 0, 2560, 1440) };
            using (var session = new PrivacySession())
            {
                var a = new FakeSurface();
                var b = new FakeSurface();
                int index = 0;
                session.Start(bounds, delegate(Rectangle rect) { return index++ == 0 ? a : b; });
                Check(session.Active && a.Shown && b.Shown);
                MustFail(delegate { session.Start(bounds, delegate { return new FakeSurface(); }); });
                session.Pulse();
                Check(a.Pulses == 1 && b.Pulses == 1);
                session.Stop();
                Check(!session.Active && a.Disposed && b.Disposed);
                session.Stop();
                a = new FakeSurface();
                b = new FakeSurface { FailPrepare = true };
                index = 0;
                MustFail(delegate { session.Start(bounds, delegate(Rectangle rect) { return index++ == 0 ? a : b; }); });
                Check(!session.Active && a.Disposed && b.Disposed && !a.Shown);
                a = new FakeSurface();
                b = new FakeSurface { FailShow = true };
                index = 0;
                MustFail(delegate { session.Start(bounds, delegate(Rectangle rect) { return index++ == 0 ? a : b; }); });
                Check(!session.Active && a.Disposed && b.Disposed);
                a = new FakeSurface { FailPulse = true };
                session.Start(new[] { bounds[0] }, delegate { return a; });
                MustFail(delegate { session.Pulse(); });
                Check(session.Active && a.Shown && !a.Disposed);
                a.FailPulse = false;
                session.Pulse();
                Check(a.Pulses == 1);
                var failedReplacement = new FakeSurface { FailPrepare = true };
                MustFail(delegate { session.Rebuild(bounds, delegate { return failedReplacement; }); });
                Check(session.Active && a.Shown && !a.Disposed && failedReplacement.Disposed);
                var replacementA = new FakeSurface();
                var replacementB = new FakeSurface { FailShow = true };
                index = 0;
                MustFail(delegate { session.Rebuild(bounds, delegate { return index++ == 0 ? replacementA : replacementB; }); });
                Check(session.Active && a.Shown && !a.Disposed && replacementA.Disposed && replacementB.Disposed);
                MustFail(delegate { session.Rebuild(new Rectangle[0], delegate { return new FakeSurface(); }); });
                Check(session.Active && a.Shown && !a.Disposed);
                replacementA = new FakeSurface { BeforeShow = delegate { Check(a.Shown && !a.Disposed); } };
                replacementB = new FakeSurface { BeforeShow = delegate { Check(a.Shown && !a.Disposed && replacementA.Shown); } };
                index = 0;
                session.Rebuild(bounds, delegate { return index++ == 0 ? replacementA : replacementB; });
                Check(session.Active && a.Disposed && replacementA.Shown && replacementB.Shown);
                replacementA.FailPulse = true;
                MustFail(delegate { session.Pulse(); });
                Check(session.Active && !replacementA.Disposed && !replacementB.Disposed && replacementB.Pulses == 1);
                session.Stop();
                Check(!session.Active && replacementA.Disposed && replacementB.Disposed);
                MustFail(delegate { session.Rebuild(bounds, delegate { return new FakeSurface(); }); });
                MustFail(delegate { session.Start(new Rectangle[0], delegate { return new FakeSurface(); }); });
                MustFail(delegate { session.Start(new[] { Rectangle.Empty }, delegate { return new FakeSurface(); }); });
                Check(!session.Active);
            }
            using (var signal = new EventWaitHandle(false, EventResetMode.AutoReset))
            using (var simulatedSession = new PrivacySession())
            using (var form = new MainForm(signal, simulatedSession))
            {
                var persistent = (CheckBox)form.Controls.Find("PersistentMode", true)[0];
                var start = (Button)form.Controls.Find("StartPrivacy", true)[0];
                Check(!persistent.Checked && start.Text.Contains("60") && !start.Enabled);
                persistent.Checked = true;
                Check(start.Text.Contains("持续") && !start.Enabled);
                var surface = new FakeSurface();
                simulatedSession.Start(new[] { bounds[0] }, delegate { return surface; });
                form.RequestRevalidation("模拟会话断开");
                form.RequestRevalidation("模拟会话重连／显示器改变");
                var status = (Label)form.Controls.Find("PrivacyStatus", true)[0];
                Check(simulatedSession.Active && surface.Shown && !surface.Disposed && status.Text.Contains("等待"));
                simulatedSession.Stop();
                form.RequestRevalidation("手动停止之后的延迟会话事件");
                Check(!simulatedSession.Active && surface.Disposed);
                signal.Set();
                Check(signal.WaitOne(0));
            }
        }
    }
}
