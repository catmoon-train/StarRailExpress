using System;
using System.Collections.Generic;
using System.Drawing;
using System.IO;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using System.Windows.Forms;

namespace MonitorBlackout
{
    internal interface IMonitor : IDisposable
    {
        string Name { get; }
        bool ReadPower(out uint value);
        bool SetPower(uint value);
    }

    internal static class Native
    {
        internal const byte PowerCode = 0xD6;
        internal const uint PowerOn = 1;
        internal const uint PowerOff = 4;
        [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
        internal struct PhysicalMonitor
        {
            internal IntPtr Handle;
            [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 128)]
            internal string Description;
        }
        [StructLayout(LayoutKind.Sequential)]
        internal struct Rect { internal int Left, Top, Right, Bottom; }
        internal delegate bool MonitorCallback(IntPtr monitor, IntPtr dc, ref Rect rect, IntPtr data);
        [DllImport("user32.dll", SetLastError = true)]
        [return: MarshalAs(UnmanagedType.Bool)]
        internal static extern bool EnumDisplayMonitors(IntPtr dc, IntPtr clip, MonitorCallback callback, IntPtr data);
        [DllImport("dxva2.dll", SetLastError = true)]
        [return: MarshalAs(UnmanagedType.Bool)]
        internal static extern bool GetNumberOfPhysicalMonitorsFromHMONITOR(IntPtr monitor, out uint count);
        [DllImport("dxva2.dll", SetLastError = true)]
        [return: MarshalAs(UnmanagedType.Bool)]
        internal static extern bool GetPhysicalMonitorsFromHMONITOR(IntPtr monitor, uint count,
            [Out] PhysicalMonitor[] monitors);
        [DllImport("dxva2.dll", SetLastError = true)]
        [return: MarshalAs(UnmanagedType.Bool)]
        internal static extern bool GetVCPFeatureAndVCPFeatureReply(IntPtr monitor, byte code,
            out uint type, out uint current, out uint maximum);
        [DllImport("dxva2.dll", SetLastError = true)]
        [return: MarshalAs(UnmanagedType.Bool)]
        internal static extern bool SetVCPFeature(IntPtr monitor, byte code, uint value);
        [DllImport("dxva2.dll", SetLastError = true)]
        [return: MarshalAs(UnmanagedType.Bool)]
        internal static extern bool DestroyPhysicalMonitor(IntPtr monitor);
        [DllImport("user32.dll", SetLastError = true)]
        [return: MarshalAs(UnmanagedType.Bool)]
        internal static extern bool RegisterHotKey(IntPtr window, int id, uint modifiers, uint key);
        [DllImport("user32.dll")]
        [return: MarshalAs(UnmanagedType.Bool)]
        internal static extern bool UnregisterHotKey(IntPtr window, int id);
    }

    internal sealed class DdcMonitor : IMonitor
    {
        private IntPtr handle;
        public string Name { get; private set; }
        internal DdcMonitor(Native.PhysicalMonitor monitor, int index)
        {
            handle = monitor.Handle;
            Name = index + ". " + monitor.Description;
        }
        public bool ReadPower(out uint value)
        {
            uint type, maximum;
            return Native.GetVCPFeatureAndVCPFeatureReply(handle, Native.PowerCode, out type, out value, out maximum);
        }
        public bool SetPower(uint value) { return Native.SetVCPFeature(handle, Native.PowerCode, value); }
        public void Dispose()
        {
            if (handle != IntPtr.Zero) { Native.DestroyPhysicalMonitor(handle); handle = IntPtr.Zero; }
        }
        public override string ToString() { return Name; }
        internal static List<IMonitor> Discover()
        {
            var result = new List<IMonitor>();
            var errors = new List<string>();
            Native.MonitorCallback callback = delegate(IntPtr h, IntPtr dc, ref Native.Rect rect, IntPtr data)
            {
                uint count;
                if (!Native.GetNumberOfPhysicalMonitorsFromHMONITOR(h, out count))
                {
                    errors.Add("读取显示器数量失败：" + Marshal.GetLastWin32Error());
                    return true;
                }
                if (count == 0) return true;
                var monitors = new Native.PhysicalMonitor[count];
                if (!Native.GetPhysicalMonitorsFromHMONITOR(h, count, monitors))
                {
                    errors.Add("读取物理显示器失败：" + Marshal.GetLastWin32Error());
                    return true;
                }
                foreach (var monitor in monitors) result.Add(new DdcMonitor(monitor, result.Count + 1));
                return true;
            };
            if (!Native.EnumDisplayMonitors(IntPtr.Zero, IntPtr.Zero, callback, IntPtr.Zero))
                errors.Add("枚举显示器失败：" + Marshal.GetLastWin32Error());
            if (result.Count == 0 && errors.Count > 0) throw new InvalidOperationException(string.Join("；", errors));
            return result;
        }
    }

    // 只恢复本次尝试过关屏的设备。失败的恢复请求保留，可再次重试。
    internal sealed class PowerSession
    {
        private readonly HashSet<IMonitor> touched = new HashSet<IMonitor>();
        internal int PendingCount { get { return touched.Count; } }
        internal string Begin(IEnumerable<IMonitor> monitors, bool allowUnreadable = false)
        {
            var report = new List<string>();
            foreach (var monitor in monitors)
            {
                uint original;
                bool readable = monitor.ReadPower(out original);
                if (!readable && !allowUnreadable)
                {
                    report.Add(monitor.Name + "：无法读取 DDC 电源接口，请开启 DDC/CI 或检查连接。");
                    continue;
                }
                if (readable && original != Native.PowerOn)
                {
                    report.Add(monitor.Name + "：当前不是开机状态，跳过。");
                    continue;
                }
                if (!readable)
                    report.Add(monitor.Name + "：兼容模式，读取失败，仍尝试写入 DDC 关屏指令。");
                // 即使驱动报告写入失败，设备也可能已执行，仍需允许恢复。
                touched.Add(monitor);
                report.Add(monitor.Name + (monitor.SetPower(Native.PowerOff)
                    ? "：已发送关屏指令（需人工确认实体屏幕）。" : "：关屏指令返回失败，仍已加入恢复列表。"));
            }
            return string.Join(Environment.NewLine, report);
        }
        internal string Repeat()
        {
            var failed = new List<string>();
            foreach (var monitor in touched)
                if (!monitor.SetPower(Native.PowerOff)) failed.Add(monitor.Name);
            return failed.Count == 0 ? "" : "持续关屏指令返回失败：" + string.Join("、", failed)
                + "。显示器可能在关屏后停止响应 DDC；可点击恢复，必要时按实体电源键。";
        }
        internal string Restore()
        {
            var restored = new List<IMonitor>();
            foreach (var monitor in touched)
                if (monitor.SetPower(Native.PowerOn)) restored.Add(monitor);
            foreach (var monitor in restored) touched.Remove(monitor);
            return touched.Count == 0 ? "已发送全部恢复指令；请确认屏幕已亮。"
                : "部分恢复指令失败，请重试；若仍不亮，请按显示器实体电源键。";
        }
    }

    internal sealed class MainForm : Form
    {
        private readonly CheckedListBox devices = new CheckedListBox();
        private readonly Button refresh = new Button { Text = "刷新显示器" };
        private readonly Button off = new Button { Text = "开始持续关屏" };
        private readonly Button restore = new Button { Text = "恢复显示器" };
        private readonly CheckBox timed = new CheckBox { Name = "TimedRestore", Text = "60 秒后自动恢复（首次测试建议保留）", Checked = true, AutoSize = true };
        private readonly CheckBox compatibility = new CheckBox { Name = "CompatibilityMode", Text = "兼容模式：读取失败也尝试关屏（强制 60 秒恢复）", AutoSize = true };
        private readonly TextBox log = new TextBox { Multiline = true, ReadOnly = true, ScrollBars = ScrollBars.Vertical };
        private readonly System.Windows.Forms.Timer timer = new System.Windows.Forms.Timer { Interval = 1000 };
        private readonly PowerSession session = new PowerSession();
        private List<IMonitor> monitors = new List<IMonitor>();
        private bool busy, active, pendingRestore, pendingClose, allowClose, hotkey;
        private DateTime deadline, nextRepeat;

        internal MainForm()
        {
            Text = "外接显示器持续关屏 · DDC/CI";
            Font = new Font("Microsoft YaHei UI", 9F);
            AutoScaleMode = AutoScaleMode.Dpi;
            ClientSize = new Size(680, 482);
            MinimumSize = new Size(600, 492);
            StartPosition = FormStartPosition.CenterScreen;
            var layout = new TableLayoutPanel { Dock = DockStyle.Fill, Padding = new Padding(14), ColumnCount = 1, RowCount = 6 };
            layout.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 100));
            layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 76));
            layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 100));
            layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 64));
            layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 46));
            layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 30));
            layout.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
            layout.Controls.Add(new Label { Dock = DockStyle.Fill, Text =
                "先在显示器菜单中开启 DDC/CI，再勾选需要关屏的设备。\n"
                + "不遮挡远程桌面、不禁用输入；向日葵是否受影响取决于显卡和显示器。\n"
                + "警告：这不是向日葵隐私屏，不能保证保密；首次务必测试自动恢复。" }, 0, 0);
            devices.Dock = DockStyle.Fill;
            devices.CheckOnClick = true;
            layout.Controls.Add(devices, 0, 1);
            var options = new FlowLayoutPanel { Dock = DockStyle.Fill, FlowDirection = FlowDirection.TopDown, WrapContents = false };
            options.Controls.Add(timed);
            options.Controls.Add(compatibility);
            layout.Controls.Add(options, 0, 2);
            var actions = new FlowLayoutPanel { Dock = DockStyle.Fill };
            foreach (var button in new[] { refresh, off, restore })
            {
                button.AutoSize = true;
                button.Height = 34;
                actions.Controls.Add(button);
            }
            layout.Controls.Add(actions, 0, 3);
            layout.Controls.Add(new Label { Dock = DockStyle.Fill, Text = "全局恢复：Ctrl + Alt + F12；也可点击恢复按钮。正常退出会尝试恢复。" }, 0, 4);
            log.Dock = DockStyle.Fill;
            layout.Controls.Add(log, 0, 5);
            Controls.Add(layout);
            refresh.Click += async delegate { await ScanAsync(); };
            off.Click += async delegate { await OffAsync(); };
            restore.Click += async delegate { await RestoreAsync(); };
            compatibility.CheckedChanged += delegate
            {
                if (compatibility.Checked) timed.Checked = true;
                UpdateControls();
            };
            timer.Tick += async delegate
            {
                if (!active) return;
                if (DateTime.UtcNow >= deadline) { await RestoreAsync(); return; }
                if (busy || DateTime.UtcNow < nextRepeat) return;
                nextRepeat = DateTime.UtcNow.AddSeconds(3);
                await WorkAsync(delegate { return session.Repeat(); });
            };
            Shown += async delegate { await ScanAsync(); };
            FormClosing += async delegate(object sender, FormClosingEventArgs e)
            {
                if (allowClose) return;
                e.Cancel = true;
                pendingClose = true;
                await RestoreAsync();
            };
            FormClosed += delegate
            {
                timer.Dispose();
                foreach (var monitor in monitors) monitor.Dispose();
            };
        }
        protected override void OnHandleCreated(EventArgs e)
        {
            base.OnHandleCreated(e);
            hotkey = Native.RegisterHotKey(Handle, 1, 0x4003, (uint)Keys.F12);
            if (!hotkey) WriteLog("恢复快捷键注册失败（可能已被占用），请使用恢复按钮。");
        }
        protected override void OnHandleDestroyed(EventArgs e)
        {
            if (hotkey) Native.UnregisterHotKey(Handle, 1);
            base.OnHandleDestroyed(e);
        }
        protected override void WndProc(ref Message message)
        {
            // WndProc 不能异步等待：恢复操作由 UI 消息队列启动。
            if (message.Msg == 0x0312 && message.WParam.ToInt32() == 1)
                BeginInvoke(new Action(async delegate { await RestoreAsync(); }));
            base.WndProc(ref message);
        }
        private void WriteLog(string text)
        {
            if (!string.IsNullOrEmpty(text)) log.AppendText(DateTime.Now.ToString("HH:mm:ss ") + text + Environment.NewLine);
        }
        private void UpdateControls()
        {
            refresh.Enabled = !busy && !active && session.PendingCount == 0;
            off.Enabled = !busy && !active && session.PendingCount == 0 && monitors.Count > 0;
            devices.Enabled = !busy && !active && session.PendingCount == 0;
            timed.Enabled = !busy && !active && !compatibility.Checked;
            compatibility.Enabled = !busy && !active && session.PendingCount == 0;
        }
        private async Task WorkAsync(Func<string> work)
        {
            if (busy) return;
            busy = true;
            UpdateControls();
            try { WriteLog(await Task.Run(work)); }
            catch (Exception ex) { WriteLog("操作异常：" + ex.Message); }
            finally { busy = false; UpdateControls(); }
            if (pendingRestore) await RestoreAsync();
        }
        private async Task ScanAsync()
        {
            await WorkAsync(delegate
            {
                foreach (var monitor in monitors) monitor.Dispose();
                monitors = new List<IMonitor>();
                monitors = DdcMonitor.Discover();
                return "检测到 " + monitors.Count + " 个物理显示器。未检测到时请检查显卡驱动、线缆和 DDC/CI。";
            });
            if (IsDisposed) return;
            devices.Items.Clear();
            foreach (var monitor in monitors) devices.Items.Add(monitor, true);
            UpdateControls();
        }
        private async Task OffAsync()
        {
            if (busy || active || session.PendingCount > 0) return;
            var selected = new List<IMonitor>();
            foreach (IMonitor monitor in devices.CheckedItems) selected.Add(monitor);
            if (selected.Count == 0) { WriteLog("请先勾选显示器。"); return; }
            bool compatible = compatibility.Checked;
            if (compatible && MessageBox.Show(this,
                "兼容模式会在无法读取电源状态时尝试关屏，并在 60 秒后发送恢复指令。\n"
                + "无法保证恢复成功或向日葵画面正常；请确保有人能按显示器实体电源键。\n是否开始测试？",
                "兼容模式测试", MessageBoxButtons.YesNo, MessageBoxIcon.Warning,
                MessageBoxDefaultButton.Button2) != DialogResult.Yes) return;
            if (compatible) WriteLog("兼容模式已启用：60 秒后自动发送恢复指令，必要时使用实体电源键恢复。");
            active = true;
            deadline = (timed.Checked || compatible) ? DateTime.UtcNow.AddSeconds(60) : DateTime.MaxValue;
            nextRepeat = DateTime.UtcNow.AddSeconds(3);
            timer.Start();
            await WorkAsync(delegate { return session.Begin(selected, compatible); });
            if (IsDisposed) return;
            if (session.PendingCount == 0) { active = false; timer.Stop(); }
            UpdateControls();
        }
        private async Task RestoreAsync()
        {
            active = false;
            if (IsDisposed) return;
            timer.Stop();
            pendingRestore = true;
            if (busy) { WriteLog("正在等待当前 DDC 请求结束，随后恢复屏幕……"); return; }
            pendingRestore = false;
            await WorkAsync(delegate { return session.Restore(); });
            if (pendingClose && !IsDisposed)
            {
                pendingClose = false;
                if (session.PendingCount > 0 && MessageBox.Show(this,
                    "部分显示器未确认恢复。是否仍退出？退出后可用显示器实体电源键恢复。", "恢复未完成",
                    MessageBoxButtons.YesNo, MessageBoxIcon.Warning) != DialogResult.Yes) return;
                allowClose = true;
                Close();
            }
        }
    }

    internal static class Program
    {
        [STAThread]
        private static int Main(string[] args)
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            if (args.Length == 2 && args[0] == "--self-test")
            {
                try { SelfTest.Run(); File.WriteAllText(args[1], "PASS: session and UI smoke tests; no physical power writes.\n"); return 0; }
                catch (Exception ex) { File.WriteAllText(args[1], ex.ToString()); return 1; }
            }
            if (args.Length == 2 && args[0] == "--probe")
            {
                var monitors = new List<IMonitor>();
                try
                {
                    monitors = DdcMonitor.Discover();
                    var report = new StringBuilder("Read-only DDC probe; no power writes.\n");
                    foreach (var monitor in monitors)
                    {
                        uint value;
                        bool supported = monitor.ReadPower(out value);
                        report.AppendLine(monitor.Name + ": D6 readable=" + supported + ", value=" + value);
                    }
                    report.AppendLine("Physical monitors: " + monitors.Count);
                    File.WriteAllText(args[1], report.ToString(), new UTF8Encoding(false));
                    return 0;
                }
                catch (Exception ex) { File.WriteAllText(args[1], ex.ToString()); return 1; }
                finally { foreach (var monitor in monitors) monitor.Dispose(); }
            }
            bool created;
            using (var mutex = new Mutex(true, "Local\\MonitorBlackout.Ddc.Session", out created))
            {
                if (!created) { MessageBox.Show("工具已在运行，请使用已有窗口恢复或关屏。"); return 0; }
                Application.Run(new MainForm());
            }
            return 0;
        }
    }

    internal static class SelfTest
    {
        private sealed class Fake : IMonitor
        {
            public string Name { get { return "Fake"; } }
            internal bool Readable = true, WriteOk = true;
            internal uint Current = 1;
            internal readonly List<uint> Writes = new List<uint>();
            public bool ReadPower(out uint value) { value = Current; return Readable; }
            public bool SetPower(uint value) { Writes.Add(value); return WriteOk; }
            public void Dispose() { }
        }
        private static void Check(bool condition) { if (!condition) throw new Exception("Self-test assertion failed"); }
        internal static void Run()
        {
            Check(Marshal.SizeOf(typeof(Native.PhysicalMonitor)) == (IntPtr.Size == 8 ? 264 : 260));
            var session = new PowerSession();
            var good = new Fake();
            var unsupported = new Fake { Readable = false };
            var alreadyOff = new Fake { Current = 4 };
            var failed = new Fake { WriteOk = false };
            session.Begin(new IMonitor[] { good, unsupported, alreadyOff, failed });
            Check(session.PendingCount == 2 && good.Writes[0] == 4);
            Check(unsupported.Writes.Count == 0 && alreadyOff.Writes.Count == 0);
            session.Repeat();
            Check(good.Writes.Count == 2 && good.Writes[1] == 4);
            session.Restore();
            Check(session.PendingCount == 1 && good.Writes[2] == 1);
            failed.WriteOk = true;
            session.Restore();
            Check(session.PendingCount == 0 && failed.Writes[failed.Writes.Count - 1] == 1);
            int before = good.Writes.Count;
            session.Repeat();
            Check(good.Writes.Count == before);
            var compatibilitySession = new PowerSession();
            var unreadableFailure = new Fake { Readable = false, WriteOk = false };
            compatibilitySession.Begin(new IMonitor[] { unsupported, unreadableFailure, alreadyOff }, true);
            Check(compatibilitySession.PendingCount == 2 && unsupported.Writes[0] == 4);
            Check(unreadableFailure.Writes[0] == 4 && alreadyOff.Writes.Count == 0);
            compatibilitySession.Repeat();
            Check(unsupported.Writes[1] == 4);
            compatibilitySession.Restore();
            Check(compatibilitySession.PendingCount == 1 && unsupported.Writes[2] == 1);
            unreadableFailure.WriteOk = true;
            compatibilitySession.Restore();
            Check(compatibilitySession.PendingCount == 0 && unreadableFailure.Writes[3] == 1);
            int compatibilityWrites = unsupported.Writes.Count;
            compatibilitySession.Repeat();
            Check(unsupported.Writes.Count == compatibilityWrites);
            using (var form = new MainForm())
            {
                Check(form.Controls.Count == 1 && form.Text.Contains("DDC/CI"));
                var compatible = (CheckBox)form.Controls.Find("CompatibilityMode", true)[0];
                var timedRestore = (CheckBox)form.Controls.Find("TimedRestore", true)[0];
                Check(!compatible.Checked && timedRestore.Checked);
                timedRestore.Checked = false;
                compatible.Checked = true;
                Check(timedRestore.Checked && !timedRestore.Enabled);
                compatible.Checked = false;
                Check(timedRestore.Enabled);
                // 不显示窗口，不枚举或写入真实显示器。
            }
        }
    }
}
