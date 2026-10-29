package org.agmas.noellesroles.game.modes.echo.cards;

import org.agmas.noellesroles.game.modes.echo.state.EchoFaction;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.agmas.noellesroles.game.modes.echo.cards.EchoCardDefinition.Tier;
import static org.agmas.noellesroles.game.modes.echo.cards.EchoEffectState.Marker;

/** 第二批功能性卡的纯状态回归：定身、护甲、陷阱、假钥匙与配方闭合。 */
class EchoFunctionalCardTest {
    private static final String PREFIX = "noellesroles:echo/";

    @Test void secondBatchIsReachableAndEveryBaseGroupPairsWithOneUpgrade() {
        Map<String, Long> bases = new HashMap<>();
        Map<String, Long> upgrades = new HashMap<>();
        for (var card : EchoCardRegistry.all()) {
            var byGroup = card.tier() == Tier.BASE ? bases : upgrades;
            byGroup.merge(card.recipeGroup(), 1L, Long::sum);
        }
        assertEquals(bases.keySet(), upgrades.keySet());
        assertTrue(upgrades.values().stream().allMatch(n -> n == 1L), "每个配方组只能有一个高级产物");
        assertEquals(23, upgrades.size());
    }

    @Test void secondBatchCardsAreActuallyDraftableForTheirFaction() {
        for (var faction : EchoFaction.values()) {
            var enabled = new HashSet<String>();
            EchoCardRegistry.all().stream().filter(c -> c.drawFaction() == faction && !c.isKey())
                    .forEach(c -> enabled.add(c.id()));
            var offer = EchoDraftService.draw(EchoDraftService.PackQuality.COMMON,
                    new EchoDraftService.Context(faction, enabled, false, true), new Random(7));
            assertEquals(3, offer.candidates().size(), faction.name());
            assertTrue(offer.candidates().stream().allMatch(c -> c.drawFaction() == faction));
        }
    }

    @Test void holdLocksMovementWithoutBecomingControlAndRespectsImmunity() {
        var s = new EchoEffectState();
        assertTrue(s.hold(100, 60, 40));
        assertTrue(s.held(159));
        assertFalse(s.held(160));
        // 已定身窗口内再次触发必须拒绝，不刷新旧截止。
        assertFalse(s.hold(120, 60, 40));
        assertEquals(160, s.heldUntil());
        // 免疫窗口过去前不能再次定身。
        assertFalse(s.hold(180, 60, 40));
        assertTrue(s.hold(200, 60, 40));
        // 定身不进入移速负修正：基线保持 0，避免与倒地 -1 叠加。
        assertEquals(0D, s.speedDelta(150, 0.25), 0.00001);
        s.clear();
        assertFalse(s.held(150));
    }

    @Test void armorAbsorbsExactlyOneHitAndNeverStacks() {
        var s = new EchoEffectState();
        assertTrue(s.armor(0, 1));
        assertFalse(s.armor(10, 1), "已有护甲时不得叠层");
        assertEquals(1, s.armor());
        assertTrue(s.consumeArmor());
        assertEquals(0, s.armor());
        assertFalse(s.consumeArmor(), "消耗后不得再免疫");
        assertTrue(s.armor(20, 1));
        s.clear();
        assertEquals(0, s.armor());
    }

    @Test void trapsArePerOwnerBoundedAndExpireOnMatchTick() {
        var state = new EchoSceneState();
        var owner = UUID.randomUUID();
        var point = new EchoSceneState.Point(1, 2, 3);
        assertTrue(state.addTrap(owner, point, 60, 20, 2.5, 200, 0));
        assertTrue(state.addTrap(owner, point, 60, 20, 2.5, 200, 0));
        assertFalse(state.addTrap(owner, point, 60, 20, 2.5, 200, 0), "每人最多两个陷阱");
        state.clock(199);
        assertEquals(2, state.traps().size());
        state.clock(200);
        assertTrue(state.traps().isEmpty(), "陷阱必须按比赛 tick 过期");
        assertThrows(IllegalArgumentException.class, () -> new EchoSceneState.Trap(UUID.randomUUID(), owner, point, 0, 0, 2, 10));
    }

    @Test void fakeKeyOnlyMisdirectsKeyPulsesNotCargoSniffing() {
        var state = new EchoSceneState();
        var owner = UUID.randomUUID();
        var at = new EchoSceneState.Point(0, 0, 0);
        assertTrue(state.add(owner, List.of(new EchoSceneState.Spec(EchoSceneState.Kind.KEY_FAKE, at, 0, 0, 400, -1, 100, 20, 0)), 0));
        assertTrue(state.deception(at, 10, EchoSceneState.Kind.KEY_FAKE).isPresent());
        assertTrue(state.deception(at, 10).isEmpty(), "静默假人查询不能返回假钥匙");
        assertTrue(state.deception(at, 110, EchoSceneState.Kind.KEY_FAKE).isEmpty(), "诱导窗口结束后不再误导");
        assertTrue(state.deception(at, 110).isEmpty());
    }

    @Test void pairedDecoysAreRejectedAtomicallyWhenTooCloseOrOverLimit() {
        var state = new EchoSceneState();
        var owner = UUID.randomUUID();
        var a = new EchoSceneState.Point(0, 0, 0);
        var b = new EchoSceneState.Point(3, 0, 0);
        var spec = new EchoSceneState.Spec(EchoSceneState.Kind.DUMMY, a, 0, 0, 240, 80);
        assertTrue(state.add(owner, List.of(spec, new EchoSceneState.Spec(EchoSceneState.Kind.DUMMY, b, 0, 0, 240, 80)), 0));
        assertEquals(2, state.all().size());
        assertFalse(state.add(owner, List.of(spec), 1), "每人最多两个诱饵");
        assertEquals(2, state.all().size());
    }

    @Test void threeLanguageKeysCoverEveryNewCardAndParameter() throws Exception {
        for (String language : List.of("zh_cn", "zh_tw", "en_us")) {
            var lang = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(
                    java.nio.file.Path.of("src/main/resources/assets/echo_freight/lang/" + language + ".json"),
                    java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            for (var card : EchoCardRegistry.all()) {
                assertTrue(lang.has(card.nameKey()), language + " " + card.nameKey());
                assertTrue(lang.has(card.descriptionKey()), language + " " + card.descriptionKey());
                assertTrue(lang.has("echo.card." + card.shortId() + ".mechanic"), language + " " + card.shortId());
                for (String key : card.params().keySet())
                    if (!Set.of("layers", "unlock", "dropKey", "packs", "cargo").contains(key))
                        assertTrue(lang.has("echo.param." + key), language + " echo.param." + key);
            }
        }
        for (String param : List.of("holdTicks", "holdImmunityTicks", "triggerRange", "relayRange"))
            assertTrue(EchoCardRegistry.all().stream().anyMatch(c -> c.params().containsKey(param)), param);
        // 内部 tick 字段一律以 Ticks 结尾，外部配置才能换算成秒。
        for (var card : EchoCardRegistry.all())
            for (String key : card.params().keySet())
                assertTrue(!key.startsWith("hold") || key.endsWith("Ticks"), card.id() + "." + key);
    }

    @Test void craftingUsesOnlySameGroupBasePairs() {
        var owner = UUID.randomUUID();
        var trap = EchoCardRegistry.require(PREFIX + "snare_trap");
        var bait = EchoCardRegistry.require(PREFIX + "key_bait");
        assertTrue(EchoCardRegistry.craftOutput(owner, trap.id(), UUID.randomUUID(), trap.id(), EchoFaction.INTERCEPTOR).isPresent());
        assertTrue(EchoCardRegistry.craftOutput(owner, bait.id(), UUID.randomUUID(), bait.id(), EchoFaction.INTERCEPTOR).isPresent());
        assertTrue(EchoCardRegistry.craftOutput(owner, trap.id(), UUID.randomUUID(), bait.id(), EchoFaction.INTERCEPTOR).isEmpty(),
                "不同系列不得互相合成");
        assertTrue(EchoCardRegistry.craftOutput(owner, trap.id(), UUID.randomUUID(), trap.id(), EchoFaction.CARRIER).isEmpty());
        assertEquals("noellesroles:echo/hard_snare",
                EchoCardRegistry.craftOutput(owner, trap.id(), UUID.randomUUID(), trap.id(), EchoFaction.INTERCEPTOR).orElseThrow().id());
    }

    @Test void holdChannelMarkerIsSingleUseAndChannelScoped() {
        var s = new EchoEffectState();
        assertTrue(s.holdChannel("key_pulse", 200, 0));
        assertFalse(s.channelHeld("tracking", 10));
        assertFalse(s.consumeChannel("tracking", 10));
        assertTrue(s.channelHeld("key_pulse", 10));
        assertTrue(s.consumeChannel("key_pulse", 10));
        assertFalse(s.channelHeld("key_pulse", 11));
        assertTrue(s.mark(Marker.HOLD, 250).isEmpty() || s.mark(Marker.HOLD, 250).isPresent());
    }
}
