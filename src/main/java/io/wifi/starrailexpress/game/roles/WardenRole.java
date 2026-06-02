
package io.wifi.starrailexpress.game.roles;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.CustomWinnerRole;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREArmorPlayerComponent;
import io.wifi.starrailexpress.cca.WardenPlayerComponent;
import io.wifi.starrailexpress.game.GameUtils;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.init.ModItems;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 职业：典狱长
 *
 * 独立胜利中立角色，12人以上刷新。
 * 被动：开局自带一层护盾，持续获得速度I，自带一把假左轮手枪，不会掉san。
 * 按直觉键可透视周围所有人（灰色，10格内），目标常驻透视（深蓝色，无限距离）。
 * 主动技能：[正义戒律]，冷却60s，需要持有假左轮才能使用，
 *   对3格内目标施加正义戒律，冷却后可更改目标。
 * 若目标被杀手或中立击杀，进入正义审判阶段：假左轮替换为德林加手枪，
 *   需在击杀上限内击杀凶手则独立胜利，否则正义反噬死亡。
 * 商店：100金币买假左轮，90秒购买冷却。
 */
public class WardenRole extends CustomWinnerRole {

    public WardenRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
                      MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    // ==================== 角色初始化 ====================

    @Override
    public void onInit(MinecraftServer server, ServerPlayer serverPlayer) {
        super.onInit(server, serverPlayer);

        WardenPlayerComponent wardenComp = WardenPlayerComponent.KEY.get(serverPlayer);
        wardenComp.init();

        // 开局自带一层护盾
        SREArmorPlayerComponent armorComp = SREArmorPlayerComponent.KEY.get(serverPlayer);
        armorComp.giveArmor();

        // 初始化技能冷却（开局50秒冷却）
        wardenComp.setSkillCooldownEnd(serverPlayer.level().getGameTime() + 50 * 20);
    }

    // ==================== 持续速度I ====================

    @Override
    public void serverTick(ServerPlayer player) {
        super.serverTick(player);
        if (GameUtils.isPlayerAliveAndSurvival(player)) {
            if (player.level().getGameTime() % 20 == 0) {
                var speedEffect = player.getEffect(MobEffects.MOVEMENT_SPEED);
                if (speedEffect == null || speedEffect.getDuration() <= 21) {
                    player.addEffect(new MobEffectInstance(
                            MobEffects.MOVEMENT_SPEED,
                            40, 0, false, false, true
                    ));
                }
                // 典狱长不会掉san，持续恢复为1f
                var moodComponent = io.wifi.starrailexpress.cca.SREPlayerMoodComponent.KEY.get(player);
                if (moodComponent.getMood() < 1.0f) {
                    moodComponent.setMood(1.0f);
                }
            }
        }
    }

    // ==================== 技能系统 ====================

    /**
     * 注册技能 - 正义戒律
     */
    public static void registerSkill() {
        RoleSkill.register(org.agmas.noellesroles.role.ModRoles.WARDEN, (context) -> {
            ServerPlayer player = context.player();
            WardenPlayerComponent wardenComp = WardenPlayerComponent.KEY.get(player);

            // 审判阶段不能使用技能
            if (wardenComp.isInJudgment()) {
                player.displayClientMessage(
                        Component.translatable("message.warden.skill_judgment")
                                .withStyle(style -> style.withColor(0xFFAA00)),
                        true);
                return;
            }

            // 检查是否有假左轮
            boolean hasFakeRevolver = player.getInventory().items.stream()
                    .anyMatch(stack -> stack.is(ModItems.FAKE_REVOLVER));
            if (!hasFakeRevolver) {
                player.displayClientMessage(
                        Component.translatable("message.warden.no_gun")
                                .withStyle(style -> style.withColor(0xFFAA00)),
                        true);
                return;
            }

            if (!wardenComp.isSkillReady()) {
                long remaining = (wardenComp.getSkillCooldownEnd() - player.level().getGameTime()) / 20;
                player.displayClientMessage(
                        Component.translatable("message.warden.skill_cooldown", remaining)
                                .withStyle(style -> style.withColor(0xFFAA00)),
                        true);
                return;
            }

            // 寻找3格内的目标玩家
            ServerPlayer target = findNearbyTarget(player);
            if (target == null) {
                player.displayClientMessage(
                        Component.translatable("message.warden.no_target")
                                .withStyle(style -> style.withColor(0xFFAA00)),
                        true);
                return;
            }

            wardenComp.useJusticeVerdict(target);
        });
    }

    /**
     * 寻找3格内的目标玩家
     */
    @Nullable
    private static ServerPlayer findNearbyTarget(ServerPlayer player) {
        double range = 3.0;
        ServerPlayer closest = null;
        double closestDist = range + 1;

        for (ServerPlayer other : player.server.getPlayerList().getPlayers()) {
            if (other == player) continue;
            if (!GameUtils.isPlayerAliveAndSurvival(other)) continue;
            if (other.level() != player.level()) continue;

            double dist = player.distanceTo(other);
            if (dist <= range && dist < closestDist) {
                closest = other;
                closestDist = dist;
            }
        }
        return closest;
    }

    // ==================== 击杀处理 ====================

    @Override
    public void onKill(Player victim, boolean spawnBody, @Nullable Player killer, ResourceLocation deathReason) {
        super.onKill(victim, spawnBody, killer, deathReason);

        if (killer == null) return;
        if (!(killer instanceof ServerPlayer killerSp)) return;

        WardenPlayerComponent wardenComp = WardenPlayerComponent.KEY.get(killerSp);

        // 审判阶段击杀处理
        if (wardenComp.isInJudgment() && victim instanceof ServerPlayer victimSp) {
            wardenComp.onJudgmentKill(victimSp);
        } else if (!wardenComp.isInJudgment()) {
            // 非审判阶段击杀任何人 → 正义反噬死亡
            wardenComp.setBackfired(true);
            killerSp.displayClientMessage(
                    Component.translatable("message.warden.judgment_backfire")
                            .withStyle(style -> style.withColor(0xFF0000)),
                    false);
            GameUtils.killPlayer(killerSp, true, null, io.wifi.starrailexpress.SRE.id("warden_backfire"), true);
        }
    }

    // ==================== 假左轮/德林加处理 ====================
    // 假左轮手枪(FakeRevolverItem)本身就不会真正射击，无需额外阻止逻辑

    // ==================== 胜利条件 ====================

    @Override
    public GameUtils.WinStatus checkWin(ServerPlayer player, GameUtils.WinStatus winStatus) {
        WardenPlayerComponent wardenComp = WardenPlayerComponent.KEY.get(player);
        if (wardenComp.hasWon()) {
            return GameUtils.WinStatus.CUSTOM;
        }
        return GameUtils.WinStatus.NOT_MODIFY;
    }

    @Override
    public boolean didPlayerWin(ServerPlayer player, boolean original, GameUtils.WinStatus winStatus) {
        WardenPlayerComponent wardenComp = WardenPlayerComponent.KEY.get(player);
        if (wardenComp.hasWon()) {
            return true;
        }
        return original;
    }

    // ==================== 商店 ====================

    @Override
    public List<ItemStack> getDefaultItems() {
        List<ItemStack> items = new ArrayList<>();
        items.add(new ItemStack(ModItems.FAKE_REVOLVER)); // 假左轮
        return items;
    }

    // ==================== 注册辅助 ====================

    public static SRERole registerRole(SRERole role) {
        io.wifi.starrailexpress.api.TMMRoles.ROLES.put(role.identifier(), role);
        if (role.getComponentKey() != null) {
            io.wifi.starrailexpress.api.TMMRoles.COMPONENT_KEYS.add(role.getComponentKey());
        }
        return role;
    }

    public static void init() {
        registerSkill();
    }
}
