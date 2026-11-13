package org.agmas.noellesroles.client.decree;

import com.mojang.text2speech.Narrator;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.COM.COMUtils;
import com.sun.jna.platform.win32.COM.Unknown;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinReg;
import com.sun.jna.ptr.PointerByReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DecreeVoiceTest {
    @Test
    @EnabledOnOs(OS.WINDOWS)
    void selectsInstalledOneCoreChineseMaleWithoutChangingSystemVoice() throws Exception {
        assumeTrue(Advapi32Util.registryKeyExists(WinReg.HKEY_LOCAL_MACHINE,
                "SOFTWARE\\Microsoft\\Speech_OneCore\\Voices\\Tokens\\MSTTS_V110_zhCN_KangkangM"),
                "本机未安装 OneCore 康康男声");
        var create = DecreeVoice.class.getDeclaredMethod("createNarrator");
        create.setAccessible(true);
        // 创建、查询、释放都在同一测试线程，不播放声音，不改系统默认音色。
        Narrator narrator = (Narrator) create.invoke(null);
        try {
            assertTrue(narrator instanceof Unknown);
            String id = new Probe(((Unknown) narrator).getPointer()).voiceId();
            assertTrue(id.startsWith("HKEY_LOCAL_MACHINE\\") || id.startsWith("HKEY_CURRENT_USER\\"), id);
            boolean machine = id.startsWith("HKEY_LOCAL_MACHINE\\");
            String attributes = id.substring(id.indexOf('\\') + 1) + "\\Attributes";
            var hive = machine ? WinReg.HKEY_LOCAL_MACHINE : WinReg.HKEY_CURRENT_USER;
            assertEquals("Male", Advapi32Util.registryGetStringValue(hive, attributes, "Gender"));
            assertTrue(Advapi32Util.registryGetStringValue(hive, attributes, "Language").contains("804"));
            System.out.println("DECREE_NATIVE_MALE_VOICE=" + id);
        } finally {
            narrator.destroy();
        }
    }

    private static final class Probe extends Unknown {
        Probe(Pointer pointer) { super(pointer); }
        private void call(int index, PointerByReference result) {
            COMUtils.checkRC(new WinNT.HRESULT(_invokeNativeInt(index, new Object[]{getPointer(), result})));
        }
        String voiceId() {
            PointerByReference tokenPointer = new PointerByReference();
            call(19, tokenPointer); // ISpVoice::GetVoice
            Probe token = new Probe(tokenPointer.getValue());
            try {
                PointerByReference id = new PointerByReference();
                token.call(16, id); // ISpObjectToken::GetId
                try { return id.getValue().getWideString(0); }
                finally { Ole32.INSTANCE.CoTaskMemFree(id.getValue()); }
            } finally {
                token.Release();
            }
        }
    }
}
