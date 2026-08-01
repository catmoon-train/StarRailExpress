/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.voice.client;

import de.maxhenkel.voicechat.api.events.ClientReceiveSoundEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.init.ModEffects;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端接收端语音处理：根据<b>说话者</b>是否携带 {@link ModEffects#VOICE_BEEP} /
 * {@link ModEffects#VOICE_ROBOT} 对原始 PCM 做实时变换。
 *
 * <p>与 {@link org.agmas.noellesroles.voice.client.HeliumBuzzClientReceiver} 同一层
 * （ClientReceiveSoundEvent 的 raw audio），但本类做的是“替换/调制”而非“变调”，因此：
 * <ul>
 *   <li>{@code voice_beep}：pitch-tracking sine vocoder，用跟随语调起伏的纯正弦音完全替换人声
 *       （参考 more_wathe 的 warble 思路，但在 PCM 层实现，无需编解码 Opus）。</li>
 *   <li>{@code voice_robot}：ring modulation（环形调制）+ 轻度低通，保留可懂度但呈电子/机器人质感。</li>
 * </ul>
 *
 * <p>说话者的效果由 {@link org.agmas.noellesroles.voice.VoiceEffectSync} 广播到听者客户端，
 * 因此这里直接查 {@code level.getPlayerByUUID(speaker).hasEffect(...)} 即可生效。</p>
 */
@Environment(EnvType.CLIENT)
public class BeepRobotVoiceClientReceiver {

    /** SimpleVoiceChat 的固定采样率。 */
    private static final int SAMPLE_RATE = 48000;

    /** beep 效果的相位状态，按说话者保存以避免每帧重置造成爆音/音调跳变。 */
    private static final Map<UUID, Double> BEEP_PHASES = new ConcurrentHashMap<>();

    /** robot 效果的载波相位，按说话者保存。 */
    private static final Map<UUID, Double> ROBOT_PHASES = new ConcurrentHashMap<>();

    /** robot 效果的一阶低通状态，按说话者保存。 */
    private static final Map<UUID, Double> ROBOT_LP = new ConcurrentHashMap<>();

    public static void register(EventRegistration r) {
        // 三种语音模式都走同一处理：邻近语音（EntitySound）、定位/对讲机语音
        // （LocationalSound）、组队/静态语音（StaticSound）。三者基类统一用 getId()
        // 返回发送者 UUID，因此可共用 process。
        r.registerEvent(ClientReceiveSoundEvent.EntitySound.class, BeepRobotVoiceClientReceiver::onSound);
        r.registerEvent(ClientReceiveSoundEvent.LocationalSound.class, BeepRobotVoiceClientReceiver::onSound);
        r.registerEvent(ClientReceiveSoundEvent.StaticSound.class, BeepRobotVoiceClientReceiver::onSound);
    }

    private static void onSound(ClientReceiveSoundEvent event) {
        process(event, event.getId());
    }

    private static void process(ClientReceiveSoundEvent event, UUID speaker) {
        if (event.isCancelled()) {
            return;
        }
        short[] pcm = event.getRawAudio();
        if (pcm == null || pcm.length == 0 || speaker == null) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        Player player = mc.level.getPlayerByUUID(speaker);
        if (player == null) {
            // 说话者已不在客户端：清理该玩家的相位状态，避免内存泄漏。
            BEEP_PHASES.remove(speaker);
            ROBOT_PHASES.remove(speaker);
            ROBOT_LP.remove(speaker);
            return;
        }

        int beepLevel = effectLevel(player, ModEffects.VOICE_BEEP);
        int robotLevel = effectLevel(player, ModEffects.VOICE_ROBOT);

        if (beepLevel > 0) {
            event.setRawAudio(beepTransform(pcm, speaker, beepLevel));
        } else if (robotLevel > 0) {
            event.setRawAudio(robotTransform(pcm, speaker, robotLevel));
        }
    }

    /**
     * 取药水等级（1 基）。{@code MobEffectInstance} 的 amplifier 从 0 开始，
     * 故 +1 让等级从 1 计起，便于按等级线性缩放参数。
     */
    private static int effectLevel(Player player, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect) {
        net.minecraft.world.effect.MobEffectInstance inst = player.getEffect(effect);
        return inst == null ? 0 : inst.getAmplifier() + 1;
    }

    /**
     * beep_voice：用跟随语调起伏的纯正弦音替换整段人声。
     *
     * <p>用 RMS 估计响度包络，用过零率估计基频（再降调 150Hz、限制在 100~1000Hz），
     * 合成对应频率的正弦波。原始音色（元音/辅音）被完全剥离，因此听不出内容，
     * 但能感知说话者的语调与情绪起伏。</p>
     */
    private static short[] beepTransform(short[] pcm, UUID speaker, int level) {
        double phase = BEEP_PHASES.getOrDefault(speaker, 0.0);

        // ---- 响度（RMS）与基频（过零率）估算 ----
        double sum = 0.0;
        int zeroCrossings = 0;
        for (int i = 0; i < pcm.length; i++) {
            sum += (long) pcm[i] * pcm[i];
            if (i > 0
                    && ((pcm[i] >= 0 && pcm[i - 1] < 0) || (pcm[i] < 0 && pcm[i - 1] >= 0))) {
                zeroCrossings++;
            }
        }

        double rms = Math.sqrt(sum / pcm.length);
        double volumeFactor = Math.min(1.0, rms / 3000.0);

        double frameDuration = (double) pcm.length / SAMPLE_RATE;
        double targetFrequency = (zeroCrossings / frameDuration) / 2.0; // 每周期 2 次过零

        // ---- 参数随药水等级变化：等级越高，降调越多、频率上限越高、音量略增 ----
        double detune = 150.0 + (level - 1) * 40.0;            // 基础降调 150Hz，每级 +40Hz
        double minFreq = 100.0;
        double maxFreq = 1000.0 + (level - 1) * 120.0;         // 频率上限随等级提升
        double amp = 0.15 * (1.0 + (level - 1) * 0.05);        // 幅度随等级略增

        targetFrequency -= detune;
        if (targetFrequency < minFreq) targetFrequency = minFreq;
        if (targetFrequency > maxFreq) targetFrequency = maxFreq;

        // ---- 合成正弦音替换原声 ----
        double phaseStep = (2.0 * Math.PI * targetFrequency) / SAMPLE_RATE;
        for (int i = 0; i < pcm.length; i++) {
            double sine = Math.sin(phase);
            phase += phaseStep;
            if (phase > 2.0 * Math.PI) {
                phase -= 2.0 * Math.PI;
            }

            int sample = (int) (sine * Short.MAX_VALUE * Math.sqrt(volumeFactor) * amp);
            if (sample > Short.MAX_VALUE) sample = Short.MAX_VALUE;
            if (sample < Short.MIN_VALUE) sample = Short.MIN_VALUE;
            pcm[i] = (short) sample;
        }

        BEEP_PHASES.put(speaker, phase);
        return pcm;
    }

    /**
     * robot_voice：环形调制（ring modulation）+ 轻度一阶低通，模拟机器人电子音。
     *
     * <p>把信号与低频载波（默认 90Hz）相乘，产生经典的机器人/Dalek 质感；
     * 同时混入少量原声并做轻度低通，保留元音/辅音的共振峰结构，从而<b>仍可听清说话内容</b>。</p>
     */
    private static short[] robotTransform(short[] pcm, UUID speaker, int level) {
        double phase = ROBOT_PHASES.getOrDefault(speaker, 0.0);
        double lp = ROBOT_LP.getOrDefault(speaker, 0.0);

        // ---- 参数随药水等级变化 ----
        // 等级越高：载波越尖（更"电子/刺耳"）、低通越狠（更闷/更金属）、原声混入越少（更机器人）。
        double carrierHz = 90.0 + (level - 1) * 25.0;          // 基础 90Hz，每级 +25Hz
        double lpCoeff = 0.25 - (level - 1) * 0.03;            // 每级更闷，最低 0.08
        if (lpCoeff < 0.08) lpCoeff = 0.08;
        double origFrac = 0.25 - (level - 1) * 0.03;           // 原声混入比例，最低 0.05
        if (origFrac < 0.05) origFrac = 0.05;
        double ringFrac = 1.0 - origFrac;
        final double RING_GAIN = 1.4;                          // 补偿环形调制带来的能量衰减

        final double phaseStep = (2.0 * Math.PI * carrierHz) / SAMPLE_RATE;

        for (int i = 0; i < pcm.length; i++) {
            double carrier = Math.sin(phase);
            phase += phaseStep;
            if (phase > 2.0 * Math.PI) {
                phase -= 2.0 * Math.PI;
            }

            // 环形调制：原声 × 载波；混入部分原声保证可懂度
            double ring = (double) pcm[i] * carrier;
            double blended = ringFrac * ring + origFrac * pcm[i];

            // 轻度低通，柔化金属感、强化电子/机器人听感
            lp += lpCoeff * (blended - lp);

            int sample = (int) (lp * RING_GAIN);
            if (sample > Short.MAX_VALUE) sample = Short.MAX_VALUE;
            if (sample < Short.MIN_VALUE) sample = Short.MIN_VALUE;
            pcm[i] = (short) sample;
        }

        ROBOT_PHASES.put(speaker, phase);
        ROBOT_LP.put(speaker, lp);
        return pcm;
    }
}
