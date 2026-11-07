package org.agmas.noellesroles.init.events;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** 只约束本次迁移的扩展边界和时点，不限制历史职业代码。 */
class CoreGameplayRoleEventsTest {
    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java", path));
    }

    private static int count(String source, String token) {
        return (source.length() - source.replace(token, "").length()) / token.length();
    }

    private static void ordered(String source, String... tokens) {
        int previous = -1;
        for (String token : tokens) {
            int current = source.indexOf(token, previous + 1);
            assertTrue(current > previous, "缺少或顺序改变：" + token);
            previous = current;
        }
    }

    @Test
    void publicEventsAreFabricEventsWithoutBusinessDependencies() throws Exception {
        String events = source("io/wifi/starrailexpress/event/GameplayExtensionEvents.java");
        assertTrue(events.contains("createArrayBacked("));
        assertEquals(29, count(events, "public static final Event<"));
        for (String dependency : new String[]{"org.agmas.noellesroles", "pro.fazeclan", "ModRoles", "RoleData", "Halloween"}) {
            assertFalse(events.contains(dependency), dependency);
        }
        String listeners = source("org/agmas/noellesroles/init/events/CoreGameplayRoleEvents.java");
        assertEquals(29, count(listeners, ".register("));
        assertTrue(listeners.contains("if (registered) return;"));
    }

    @Test
    void deathGatesKeepAttributionCloneGuardAndShortCircuitOrder() throws Exception {
        String mode = source("io/wifi/starrailexpress/api/GameMode.java");
        ordered(mode, "public void killPlayer(", "ALLOW_KILL_ENTRY.invoker()",
                "EarlyKillPlayer.FIND_KILLER_EVENT.invoker()", "ALLOW_KILLER_RESOLUTION.invoker()",
                "earlyKillerEvent.findTrueKiller(", "CloneDoctorSessionManager.blocksDamage(killer)",
                "ALLOW_KILL_AFTER_ATTRIBUTION.invoker()", "OnKillPlayerTriggered.EVENT.invoker()");
        assertTrue(mode.contains("? weakArmorOverride : weakArmorComponent.getWeakArmor()"));
        ordered(mode, "ALLOW_BASE_KILL_COINS.invoker()", "&& shouldGiveKillerBalance(victim, killer, deathReason)",
                "addToBalance(gift)", "ALLOW_EXTRA_KILL_COINS.invoker()", "killerRole.grantKillCoin(killer)");
        assertFalse(mode.contains("PumpkinSpiritHandler"));
        assertFalse(mode.contains("Error404Role"));
    }

    @Test
    void allReturnsAndWelcomePacketRemainAtOriginalBoundaries() throws Exception {
        String mode = source("io/wifi/starrailexpress/game/modes/SREMurderGameMode.java");
        assertEquals(4, count(mode, "return GameplayExtensionEvents.GAME_END_RESULT.invoker().apply("));
        assertEquals(2, count(mode, "return GameplayExtensionEvents.ROLE_POOL_RESULT.invoker().apply("));
        ordered(mode, "var welcomePayload = new AnnounceWelcomePayload(", "WELCOME_PAYLOAD.invoker()",
                "ModdedRoleAssigned.EVENT.invoker().assignModdedRole(player, role)");
        assertFalse(mode.contains("ModRoles.MAGICIAN"));
        assertFalse(mode.contains("replaceCommandBlockExcludedRoles"));
    }

    @Test
    void taskTailStaysOneListenerWithUnchangedBusinessOrder() throws Exception {
        String listeners = source("org/agmas/noellesroles/init/events/CoreGameplayRoleEvents.java");
        assertEquals(1, count(listeners, "AFTER_QUEST.register("));
        String tail = listeners.substring(listeners.indexOf("private static void afterQuest("));
        ordered(tail, "HalloweenCombat.onTaskCompleted(", "HalloweenTaskRewards.onTaskCompleted(",
                "PumpkinSpiritHandler.onTaskCompleted(", "SealedArtifactHandler.onFinishQuest(",
                "SEModifiers.TASKMASTER", "addToBalance(25)", "TwinChildrenTaskShare.onTaskCompleted(");
        String dispatcher = source("io/wifi/starrailexpress/api/RoleMethodDispatcher.java");
        assertTrue(dispatcher.contains("shop.runWithoutIncome(() -> finishQuest("));
        ordered(dispatcher, "reward = GameplayExtensionEvents.QUEST_REWARD.invoker()",
                "role.onFinishQuest(player, quest)", "GameplayExtensionEvents.AFTER_QUEST.invoker()");
    }

    @Test
    void fieldReadWriteAndTwoPsychoTailsAreNotCollapsed() throws Exception {
        String task = source("io/wifi/starrailexpress/cca/SREPlayerTaskComponent.java");
        ordered(task, "int previousNextTaskTimer = this.nextTaskTimer;", "BEFORE_TASK_TIMER_DECREMENT.invoker()",
                "this.nextTaskTimer = previousNextTaskTimer - 1;");
        assertFalse(task.contains("WORKAHOLIC_START_TIME"));
        String psycho = source("io/wifi/starrailexpress/cca/SREPlayerPsychoComponent.java");
        assertEquals(2, count(psycho, "PSYCHO_STOPPED.invoker()"));
        assertFalse(psycho.contains("clearMagicianFakeBat"));
        String ability = source("io/wifi/starrailexpress/cca/SREAbilityPlayerComponent.java");
        assertEquals(2, count(ability, "ALLOW_ABILITY_TICK.invoker()"));
        assertFalse(ability.contains("isPumpkinTimeFrozen"));
    }

    @Test
    void minigameSuccessAndInteractionHeadsRemainGeneric() throws Exception {
        String minigame = source("io/wifi/starrailexpress/cca/SREPlayerMinigameTaskComponent.java");
        ordered(minigame.substring(minigame.indexOf("public boolean onMinigameBlockCompleted(")),
                "this.sync();", "MINIGAME_COMPLETED.invoker()", "return true;");
        for (String file : new String[]{"block/DoorPartBlock.java", "block/MountableBlock.java", "block/PlatterBlock.java",
                "entity/NoteEntity.java", "item/CrowbarItem.java"}) {
            String content = source("io/wifi/starrailexpress/content/" + file);
            assertTrue(content.contains("GameplayExtensionEvents."), file);
            for (String migratedRule : new String[]{"ModRoles.PRIEST", "DELUSION_VIAL", "ENTITY_NOTE_MAKER",
                    "TwinChildrenHandler", "AlarmTrapItem", "ReinforcementItem"}) {
                assertFalse(content.contains(migratedRule), file + ": " + migratedRule);
            }
        }
    }
}
