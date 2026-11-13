package org.agmas.noellesroles.client.decree;

import com.mojang.text2speech.Narrator;
import com.mojang.text2speech.NarratorWindows;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.COM.COMUtils;
import com.sun.jna.platform.win32.COM.Unknown;
import com.sun.jna.platform.win32.Guid;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.platform.win32.WTypes;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 与 AsyncNarrator 相同的固定线程隔离；Windows 优先男声，找不到则保留系统默认声线。 */
public final class DecreeVoice implements Narrator {
    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "NoellesRoles-DecreeVoice");
        thread.setDaemon(true);
        return thread;
    });
    private Narrator delegate;
    private volatile boolean active;
    private boolean closed;

    public void initialize() {
        submit(() -> {});
    }

    private synchronized void submit(Runnable action) {
        if (closed) return;
        executor.execute(() -> {
            try {
                if (delegate == null) {
                    delegate = createNarrator();
                    active = delegate.active();
                }
                action.run();
            } catch (RuntimeException | LinkageError error) {
                LOGGER.error("律令语音后台调用失败", error);
            }
        });
    }

    private static Narrator createNarrator() {
        if (Platform.isWindows()) {
            try {
                return new SolemnWindowsNarrator();
            } catch (Narrator.InitializeException | RuntimeException | LinkageError error) {
                LOGGER.warn("律令 SAPI 初始化失败，回退系统旁白", error);
            }
        }
        return Narrator.getNarrator();
    }

    @Override
    public void say(String message, boolean interrupt) {
        submit(() -> delegate.say(message, interrupt));
    }

    @Override
    public void clear() {
        submit(() -> delegate.clear());
    }

    @Override
    public boolean active() {
        return active;
    }

    @Override
    public synchronized void destroy() {
        if (closed) return;
        closed = true;
        executor.execute(() -> {
            try {
                if (delegate != null) {
                    try {
                        delegate.clear();
                    } finally {
                        delegate.destroy();
                    }
                }
            } catch (RuntimeException | LinkageError error) {
                LOGGER.warn("律令语音释放失败", error);
            } finally {
                active = false;
            }
        });
        executor.shutdown();
    }

    /** sapi.h 的 ISpVoice / ISpObjectTokenCategory / IEnumSpObjectTokens，所有引用同线程释放。 */
    private static final class SolemnWindowsNarrator extends NarratorWindows {
        private SolemnWindowsNarrator() throws InitializeException {
            super();
            // NarratorWindows 已在本线程初始化 COM；失败仅影响音色，不能泄漏已创建的 voice。
            try {
                selectMaleVoice();
            } catch (RuntimeException | LinkageError error) {
                LOGGER.warn("无法选择本机男声，保留默认声线", error);
            }
            try {
                // ISpVoice::SetRate，合法范围 -10..10；-2 比系统默认略慢。
                COMUtils.checkRC(new com.sun.jna.platform.win32.WinNT.HRESULT(
                        _invokeNativeInt(28, new Object[] {getPointer(), -2})));
            } catch (RuntimeException | LinkageError error) {
                LOGGER.warn("无法设置律令语音速度，保留默认语速", error);
            }
        }

        private void selectMaleVoice() {
            PointerByReference categoryPointer = new PointerByReference();
            COMUtils.checkRC(Ole32.INSTANCE.CoCreateInstance(
                    new Guid.CLSID("{A910187F-0C7A-45AC-92CC-59EDAFB77B53}"), null, WTypes.CLSCTX_INPROC_SERVER,
                    new Guid.IID("{2D3D3845-39AF-4850-BBF9-40B49780011D}"), categoryPointer));
            NativeObject category = new NativeObject(categoryPointer.getValue());
            try {
                // ISpObjectTokenCategory::SetId 与 EnumTokens；不修改系统默认 token。
                category.call(15, new WString("HKEY_LOCAL_MACHINE\\SOFTWARE\\Microsoft\\Speech\\Voices"), 0);
                PointerByReference enumPointer = new PointerByReference();
                // 只选择能朗读中文的男声；仅有英文男声时保留可用的系统默认声线。
                category.call(18, new WString("Gender=Male;Language=804"), null, enumPointer);
                NativeObject tokens = new NativeObject(enumPointer.getValue());
                try {
                    PointerByReference tokenPointer = new PointerByReference();
                    IntByReference fetched = new IntByReference();
                    tokens.call(3, 1, tokenPointer, fetched);
                    Pointer token = tokenPointer.getValue();
                    if (token == null) {
                        LOGGER.info("本机没有匹配的 SAPI 男声，律令将使用系统默认声线");
                        return;
                    }
                    NativeObject selected = new NativeObject(token);
                    try {
                        if (fetched.getValue() == 1) {
                            // ISpVoice::SetVoice；voice 持有自己的引用，随后释放枚举返回的引用。
                            COMUtils.checkRC(new com.sun.jna.platform.win32.WinNT.HRESULT(
                                    _invokeNativeInt(18, new Object[] {getPointer(), token})));
                        }
                    } finally {
                        selected.Release();
                    }
                } finally {
                    tokens.Release();
                }
            } finally {
                category.Release();
            }
        }
    }

    private static final class NativeObject extends Unknown {
        private NativeObject(Pointer pointer) {
            super(pointer);
        }

        private void call(int index, Object... arguments) {
            Object[] all = new Object[arguments.length + 1];
            all[0] = getPointer();
            System.arraycopy(arguments, 0, all, 1, arguments.length);
            COMUtils.checkRC(new com.sun.jna.platform.win32.WinNT.HRESULT(_invokeNativeInt(index, all)));
        }
    }
}
