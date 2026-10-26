package org.agmas.noellesroles.game.modes.echo.state;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EchoSkillStateTest {
    @Test void activationStartsCooldownAndWindowOnlyOnce() {
        EchoSkillState state = new EchoSkillState();
        EchoSkill skill = EchoSkill.SWIFT_PICK;
        assertTrue(state.ready(0));
        assertTrue(state.activate(skill, 100));
        assertEquals(100 + skill.cooldownTicks(), state.readyAt());
        assertEquals(100 + skill.windowTicks(), state.pendingUntil());
        assertFalse(state.ready(200));
        assertFalse(state.activate(skill, 200), "冷却中不得重复释放");
        assertEquals(100 + skill.cooldownTicks(), state.readyAt(), "失败不得改冷却");
    }

    @Test void pendingEffectIsConsumedExactlyOnceAndExpiryRefundsNothing() {
        EchoSkillState state = new EchoSkillState();
        // LOCK_ON 已改为持续增益（狩猎本能），这里改用仍然是一次性的 STALL_BURST 验证 pending 语义。
        EchoSkill skill = EchoSkill.STALL_BURST;
        assertFalse(state.consumePending(skill, 0), "没有窗口时不能消耗");
        assertTrue(state.activate(skill, 10));
        assertTrue(state.pendingActive(skill, 20));
        assertTrue(state.consumePending(skill, 20));
        assertFalse(state.pendingActive(skill, 20), "消耗后窗口立即关闭");
        assertFalse(state.consumePending(skill, 20));
        assertEquals(10 + skill.cooldownTicks(), state.readyAt(), "消耗不退还冷却");
        assertTrue(state.activate(skill, 10 + skill.cooldownTicks()));
        assertFalse(state.pendingActive(skill, 10 + skill.cooldownTicks() + skill.windowTicks()),
                "窗口到期自动失效");
    }

    @Test void buffWindowNeverReportsPendingAndExpiresByTick() {
        EchoSkillState state = new EchoSkillState();
        EchoSkill skill = EchoSkill.STEADY_HAUL;
        assertTrue(skill.pending() == false);
        assertTrue(state.activate(skill, 0));
        assertTrue(state.buffActive(skill, skill.windowTicks() - 1));
        assertFalse(state.buffActive(skill, skill.windowTicks()));
        assertFalse(state.pendingActive(skill, 0));
        assertFalse(state.consumePending(skill, 0), "持续增益不能被一次性消耗");
        state.expire(skill.windowTicks());
        assertEquals(0, state.buffUntil());
    }

    @Test void expireAndClearDropWindowsWithoutTouchingRemainingCooldownOrder() {
        EchoSkillState state = new EchoSkillState();
        assertTrue(state.activate(EchoSkill.STALL_BURST, 0));
        state.expire(EchoSkill.STALL_BURST.windowTicks());
        assertEquals(0, state.pendingUntil());
        assertEquals(EchoSkill.STALL_BURST.cooldownTicks(), state.readyAt());
        state.clear();
        assertEquals(0, state.readyAt());
        assertEquals(0, state.buffUntil());
        assertEquals(0, state.pendingUntil());
        assertTrue(state.ready(0));
    }

    @Test void everySpecializationOwnsExactlyOneSkillWithDistinctTextAndIcon() {
        Set<EchoSkill> skills = new HashSet<>();
        Set<String> ids = new HashSet<>();
        Set<String> icons = new HashSet<>();
        for (EchoSpecialization specialization : EchoSpecialization.values()) {
            EchoSkill skill = EchoSkill.of(specialization);
            assertEquals(specialization, skill.specialization());
            assertTrue(skills.add(skill), "专长与技能必须一一对应");
            assertTrue(ids.add(skill.id()));
            assertTrue(icons.add(skill.icon()));
            assertTrue(skill.cooldownTicks() > 0 && skill.windowTicks() >= 0);
            assertTrue(skill.nameKey().startsWith("echo.skill."));
            assertTrue(skill.descriptionKey().startsWith("echo.skill."));
        }
        assertEquals(EchoSpecialization.values().length, skills.size());
        // 职业技能走独立的 EchoClassState（有自己的冷却），不再占用 EchoSkill 枚举。
        // 每个专长仍有唯一技能；职业技能走 EchoClassState 的独立冷却。
        for (var spec : EchoSpecialization.values()) {
            var skill = EchoSkill.of(spec);
            assertEquals(spec, skill.specialization(), spec.id());
        }
        assertEquals(EchoSpecialization.values().length,
                java.util.Arrays.stream(EchoSkill.values()).filter(s -> s.specialization() != null).count());
    }

    @Test void skillHearingAndIntroKeysExistInThreeLanguages() throws Exception {
        for (String language : List.of("zh_cn", "zh_tw", "en_us")) {
            var json = JsonParser.parseString(Files.readString(
                    Path.of("src/main/resources/assets/echo_freight/lang/" + language + ".json"),
                    StandardCharsets.UTF_8)).getAsJsonObject();
            for (EchoSkill skill : EchoSkill.values()) {
                assertTrue(json.has(skill.nameKey()), skill.nameKey() + " @ " + language);
                assertTrue(json.has(skill.descriptionKey()), skill.descriptionKey() + " @ " + language);
            }
            for (String key : List.of("echo.signal.hearing", "echo.signal.cargo", "echo.signal.key",
                    "echo.intro.spec_tag", "echo.intro.cue.map.title", "echo.intro.cue.map.body",
                    "echo.intro.cue.interceptor.title", "echo.intro.cue.carrier.title",
                    "echo.intro.cue.facility.title", "echo.intro.cue.evacuation.title",
                    "echo.intro.cue.specialization.title", "echo.phase.safe", "echo.direction.0",
                    "echo.direction.7", "echo.combat.bleeding",
                    "echo.hunt.strike_empowered", "echo.hunt.empowered", "echo.hunt.instinct",
                    "echo.hunt.phase.stalk", "echo.hunt.phase.charge", "echo.hunt.phase.lunge",
                    "echo.hunt.phase.recover"))
                assertTrue(json.has(key), key + " @ " + language);
        }
    }
}
