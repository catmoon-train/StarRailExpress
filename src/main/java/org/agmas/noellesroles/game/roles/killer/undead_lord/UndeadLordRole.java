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

package org.agmas.noellesroles.game.roles.killer.undead_lord;

import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.EggRole;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.role_data.killer.UndeadLordRoleData;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 亡灵之主（杀手阵营，控场 / 滚雪球）。
 * 专属商店精简为四件核心道具：骨杖、瘟疫之雾、亡者召唤符、感染增幅器（外加通用撬棍、开锁器）。
 */
public class UndeadLordRole extends EggRole {
    public static final ResourceLocation COMMAND_SKILL_ID = ResourceLocation.fromNamespaceAndPath(
            "noellesroles", "undead_lord_command");
    public static final ResourceLocation SUMMON_SKILL_ID = ResourceLocation.fromNamespaceAndPath(
            "noellesroles", "undead_lord_summon");

    public UndeadLordRole(ResourceLocation identifier, int color, boolean isInnocent,
            boolean canUseKiller, MoodType moodType, int maxSprintTime, boolean hideScoreboard) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, hideScoreboard);
        // 固定注册，复用技能键与 Shift+技能键；冷却由成功施法时读取当前配置。
        RoleSkill.register(this,
                RoleSkill.skill(COMMAND_SKILL_ID, "skill.noellesroles.undead_lord.command",
                        UndeadLordRole::commandSkill).noCastCCA(true).recordReplay().announceToSelf().build(),
                RoleSkill.skill(SUMMON_SKILL_ID, "skill.noellesroles.undead_lord.summon",
                        UndeadLordRole::summonSkill).shifted(true).noCastCCA(true)
                        .recordReplay().announceToSelf().build());
    }

    private static NoellesRolesConfig config() {
        return NoellesRolesConfig.HANDLER.instance();
    }

    /** 成功击杀钩子在死亡保护判定之后触发；不监听尸体或普通伤害事件。 */
    @Override
    public void onKill(Player victim, boolean spawnBody, Player killer, ResourceLocation deathReason) {
        if (!(killer instanceof ServerPlayer lord) || !(victim instanceof ServerPlayer killed)
                || killer == victim || !canAct(lord) || !GameUtils.isPlayerAliveAndSurvival(killed)) return;
        UndeadLordRoleData data = RoleData.getNullable(UndeadLordRoleData.class, lord);
        if (data != null) data.onSuccessfulKill(killed);
    }

    public static boolean canAct(ServerPlayer player) {
        var world = SREGameWorldComponent.KEY.get(player.level());
        return world.isRunning() && world.isRole(player, ModRoles.UNDEAD_LORD)
                && GameUtils.isPlayerAliveAndSurvival(player);
    }

    /** 忽略客户端传来的 target，使用服务器眼位、朝向和方块射线选择最近玩家。 */
    public static boolean commandSkill(RoleSkill.RoleSkillContext context) {
        ServerPlayer player = context.player();
        if (!canAct(player) || !context.skillReady() || context.phase() != RoleSkill.Phase.PRESS) return false;
        var data = RoleData.getNullable(UndeadLordRoleData.class, player);
        if (data == null || data.getActiveUndeadCount() == 0) {
            fail(player, "no_undead");
            return false;
        }
        double range = config().undeadLordCommandRange;
        if (!Double.isFinite(range) || range <= 0) return false;
        ServerPlayer target = aimedPlayer(player, range);
        var world = SREGameWorldComponent.KEY.get(player.level());
        if (target == null || player.isAlliedTo(target)
                || world.getRole(target) == null
                || SREGameWorldComponent.isKillerTeamRoleStatic(world.getRole(target))) {
            fail(player, "no_target");
            return false;
        }
        if (!data.commandUndead(target.getUUID(), secondsToTicks(config().undeadLordCommandDurationSeconds))) {
            fail(player, "no_undead");
            return false;
        }
        context.setSkillCooldown(secondsToTicks(config().undeadLordCommandCooldownSeconds));
        return true;
    }

    public static boolean summonSkill(RoleSkill.RoleSkillContext context) {
        ServerPlayer player = context.player();
        if (!canAct(player) || !context.skillReady() || context.phase() != RoleSkill.Phase.PRESS) return false;
        var data = RoleData.getNullable(UndeadLordRoleData.class, player);
        if (data == null) return false;
        if (data.getSouls() < 1) {
            fail(player, "no_souls");
            return false;
        }
        if (!data.trySoulSummon()) {
            fail(player, "summon_failed");
            return false;
        }
        context.setSkillCooldown(secondsToTicks(config().undeadLordSummonCooldownSeconds));
        return true;
    }

    private static int secondsToTicks(int seconds) {
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, (long) seconds * 20));
    }

    private static void fail(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable("message.noellesroles.undead_lord." + key)
                .withStyle(ChatFormatting.RED), true);
    }

    private static Vec3 visibleRayEnd(ServerPlayer player, double range) {
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getLookAngle().scale(range));
        return player.serverLevel().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player)).getLocation();
    }

    private static ServerPlayer aimedPlayer(ServerPlayer player, double range) {
        Vec3 start = player.getEyePosition();
        Vec3 end = visibleRayEnd(player, range);
        ServerPlayer closest = null;
        double nearest = start.distanceToSqr(end);
        for (ServerPlayer candidate : player.serverLevel().players()) {
            if (candidate == player || !GameUtils.isPlayerAliveAndSurvival(candidate)) continue;
            var hit = candidate.getBoundingBox().inflate(0.1).clip(start, end);
            if (hit.isPresent() && start.distanceToSqr(hit.get()) <= nearest) {
                closest = candidate;
                nearest = start.distanceToSqr(hit.get());
            }
        }
        return closest;
    }

    /** 复苏交互也重新验证服务器瞄准射线，最多 3 格，不采信客户端 hitResult。 */
    public static boolean canReachCorpse(ServerPlayer player, PlayerBodyEntity body) {
        if (body.isRemoved() || body.level() != player.level()) return false;
        Vec3 start = player.getEyePosition();
        return body.getBoundingBox().inflate(0.1).clip(start, visibleRayEnd(player, 3.0)).isPresent();
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        List<ShopEntry> entries = new ArrayList<>();

        // 骨杖：近战武器，攻击玩家时为其注入感染值（5 点耐久）
        entries.add(new ShopEntry(org.agmas.noellesroles.init.ModItems.BONE_STAFF.getDefaultInstance(),
                config().undeadLordBoneStaffPrice, ShopEntry.Type.WEAPON));

        // 瘟疫之雾：在所在位置释放毒雾
        entries.add(effectEntry(Items.FERMENTED_SPIDER_EYE, 120, "plague_fog", comp -> {
            comp.releasePlagueFog(config().undeadLordFogSeconds * 20);
        }));

        // 亡者召唤符：召唤 2 个临时亡灵（无需尸体）；有冷却，且不超过亡灵上限
        entries.add(conditionalEffectEntry(Items.WITHER_SKELETON_SKULL, config().undeadLordCharmPrice,
                "summon_charm", comp ->
                comp.trySummonCharm(2, config().undeadLordCharmLifetimeSeconds * 20)));

        // 感染增幅器：接下来 60 秒内亡灵攻击感染翻倍
        entries.add(effectEntry(Items.BLAZE_POWDER, 100, "infection_amp", comp -> {
            comp.startInfectionAmp(config().undeadLordAmpSeconds * 20);
        }));

        // 通用工具：撬棍（对任意门使用可将其永久打开）
        entries.add(new ShopEntry(TMMItems.CROWBAR.getDefaultInstance(),
                SREConfig.instance().crowbarPrice,
                dev.doctor4t.wathe.util.ShopEntry.Type.TOOL));

        // 通用工具：开锁器（对任何锁住的门可打开，潜行卡门）
        entries.add(new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(),
                SREConfig.instance().lockpickPrice,
                dev.doctor4t.wathe.util.ShopEntry.Type.TOOL));

        return entries;
    }

    /**
     * 构造一个“即时效果”商店条目：购买后只执行效果、扣金币，不向背包发放物品。
     */
    private ShopEntry effectEntry(net.minecraft.world.item.Item icon, int price, String nameKey,
            java.util.function.Consumer<UndeadLordRoleData> effect) {
        return conditionalEffectEntry(icon, price, nameKey, comp -> {
            effect.accept(comp);
            return true;
        });
    }

    /**
     * 与 {@link #effectEntry} 相同，但效果返回是否成功：返回 false 时视为购买失败，
     * 不扣金币、不播放音效与提示（用于受冷却 / 上限约束的道具，如亡者召唤符）。
     */
    private ShopEntry conditionalEffectEntry(net.minecraft.world.item.Item icon, int price, String nameKey,
            java.util.function.Predicate<UndeadLordRoleData> effect) {
        ItemStack stack = new ItemStack(icon);
        stack.set(DataComponents.CUSTOM_NAME,
                Component.translatable("shop.noellesroles.undead_lord." + nameKey));
        // 添加商店物品描述（由 item_intro/lang 语言文件提供）
        stack.set(DataComponents.LORE, new ItemLore(
                List.of(Component.translatable("shop.noellesroles.undead_lord." + nameKey + ".desc")
                        .withStyle(ChatFormatting.GRAY))));
        return new ShopEntry(stack, price, ShopEntry.Type.TOOL) {
            @Override
            public boolean onBuy(@NotNull Player player) {
                if (!(player instanceof ServerPlayer serverPlayer)) {
                    return false;
                }
                SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
                if (!gameWorldComponent.isRole(player, UndeadLordRole.this)) {
                    return false;
                }
                UndeadLordRoleData comp = RoleData.getNullable(UndeadLordRoleData.class, serverPlayer);
                if (comp == null) {
                    return false;
                }
                if (!effect.test(comp)) {
                    // 条件不满足（冷却中 / 已达亡灵上限）：购买失败，不扣金币
                    return false;
                }
                player.level().playSound(null, player.blockPosition(), SoundEvents.SOUL_ESCAPE.value(),
                        SoundSource.PLAYERS, 0.8f, 0.8f);
                player.displayClientMessage(
                        Component.translatable("message.noellesroles.undead_lord.shop_used")
                                .withStyle(ChatFormatting.LIGHT_PURPLE),
                        true);
                return true;
            }
        };
    }
}
