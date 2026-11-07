package io.wifi.starrailexpress.event;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** 防止接收器提取方法后 INVOKE 注入失效，并约束冷却事件的原触发边界。 */
class KnifeCooldownEventTest {
    @Test
    void obsoleteMixinIsAbsentFromConfigurationAndSources() throws Exception {
        var json = JsonParser.parseString(Files.readString(Path.of("src/main/resources/noellesroles.mixins.json")))
                .getAsJsonObject();
        assertTrue(json.getAsJsonArray("mixins").asList().stream()
                .noneMatch(value -> value.getAsString().equals("item.ThrowingKnifeCooldownMixin")));
        assertFalse(Files.exists(Path.of("src/main/java/org/agmas/noellesroles/mixin/item/ThrowingKnifeCooldownMixin.java")));
    }

    @Test
    void allThreeRealCooldownBranchesNotifyBeforeWritingFrozenArguments() throws Exception {
        String receiver = source("io/wifi/starrailexpress/network/original/KnifeStabPayload.java");
        assertTrue(receiver.contains("stab(context.player(), payload.target());"));
        assertEquals(4, count(receiver, "applyKnifeCooldown(")); // 三处调用及方法声明。
        String helper = receiver.substring(receiver.indexOf("private static void applyKnifeCooldown("),
                receiver.indexOf("private static int getKnifeCooldown("));
        assertTrue(helper.indexOf("BEFORE_KNIFE_COOLDOWN.invoker()") < helper.indexOf("cooldowns.addCooldown(item, ticks)"));
        assertTrue(helper.contains("ItemCooldowns cooldowns, Item item, int ticks"));
        String dummy = receiver.substring(receiver.indexOf("targetEntity instanceof io.wifi.starrailexpress.traindummy.TrainDummyEntity"),
                receiver.indexOf("if (target == null)"));
        assertFalse(dummy.contains("applyKnifeCooldown("), "训练假人分支不新增原本不存在的冷却");
        assertFalse(receiver.contains("ModItems.STALKER_KNIFE"), "核心只派发事件");
    }

    @Test
    void businessListenerIsRegisteredOnceAndKeepsSharedCooldownAndDash() throws Exception {
        String listener = source("org/agmas/noellesroles/init/events/KnifeCooldownRoleEvents.java");
        assertTrue(listener.indexOf("if (registered) return;") < listener.indexOf("BEFORE_KNIFE_COOLDOWN.register"));
        assertTrue(listener.contains("addCooldown(ModItems.THROWING_KNIFE, GameConstants.ITEM_COOLDOWNS.get(TMMItems.KNIFE))"));
        assertTrue(listener.contains("ModItems.STALKER_KNIFE_OFFHAND"));
        assertTrue(listener.contains("ModItems.STALKER_KNIFE,"));
        assertEquals(2, count(listener, "GameConstants.ITEM_COOLDOWNS.get(TMMItems.KNIFE) - 200"));
        assertTrue(listener.contains("new MobEffectInstance(MobEffects.INVISIBILITY, 15, 0)"));
        assertTrue(listener.contains("StalkerKnifeItem.performDashOnHit(level, player, level.getEntity(targetId))"));
        assertTrue(source("org/agmas/noellesroles/register/NREventRegister.java").contains("KnifeCooldownRoleEvents.register()"));
    }

    private static int count(String source, String token) {
        return (source.length() - source.replace(token, "").length()) / token.length();
    }

    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java", path));
    }
}
