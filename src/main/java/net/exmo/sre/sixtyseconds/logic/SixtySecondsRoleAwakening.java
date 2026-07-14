package net.exmo.sre.sixtyseconds.logic;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.DynamicShopComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.ShopContent;
import io.wifi.starrailexpress.network.original.AnnounceWelcomePayload;
import io.wifi.starrailexpress.util.ShopEntry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.exmo.sre.sixtyseconds.SixtySecondsBalance;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.harpymodloader.events.ModdedRoleAssigned;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 每日觉醒哈比职业：换日时随机挑 {@link #PER_DAY} 名<b>无职业</b>的存活玩家觉醒一个职业。
 * 每职业只出现一次（{@link SixtySecondsState.Data#usedAwakenRoles}），抽完不再分配。
 * 最后两个中立职业（秉烛人 / 小偷）每人独立 {@link #NEUTRAL_CHANCE} 概率出现。
 * <p>
 * 职业名单据用户规格映射到现有 {@link ModRoles}（22/28 有对应；作家/帕秋莉/船长/信使/小透明/记者 暂无对应，略）。
 * 各职业的数值修正（价格×、冷却×、技能无金币、广播消耗…）逐职业接线，见 {@link #applyModifiers} 的 TODO。
 */
public final class SixtySecondsRoleAwakening {
    public static final int PER_DAY = 4;
    public static final double NEUTRAL_CHANCE = 0.001; // 0.1%

    /** 平民职业池（仅 isInnocent && !canUseKiller 的会被过滤保留）。 */
    private static final List<ResourceLocation> CIVILIAN_POOL = List.of(
            ModRoles.CHEF_ID, ModRoles.NOISEMAKER_ID, ModRoles.PSYCHOLOGIST_ID, ModRoles.ALCHEMIST_ID,
            ModRoles.AGENT_ID, ModRoles.FIREFIGHTER_ID, ModRoles.BUILDER_ID, ModRoles.GLITCH_ROBOT_ID,
            ModRoles.MONITOR_ID, ModRoles.OLDMAN_ID, ModRoles.CORONER_ID, ModRoles.CAKE_MAKER_ID,
            ModRoles.JADE_GENERAL_ID, ModRoles.SUPERSTAR_ID, ModRoles.SINGER_ID, ModRoles.ATTENDANT_ID,
            ModRoles.FIGHTER_ID, ModRoles.GREAT_DETECTIVE_ID, ModRoles.DOCTOR_ID, ModRoles.BROADCASTER_ID,
            ModRoles.AWESOME_BINGLUS_ID, ModRoles.GHOST_ID);

    /** 隐藏中立职业池（0.1% 独立触发）。 */
    private static final List<ResourceLocation> NEUTRAL_POOL = List.of(
            ModRoles.CANDLE_BEARER_ID, ModRoles.THIEF_ID);

    private SixtySecondsRoleAwakening() {
    }

    public static void awaken(ServerLevel level, SixtySecondsState.Data data) {
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(level);
        List<ServerPlayer> roleless = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (GameUtils.isPlayerAliveAndSurvival(player) && gameWorldComponent.getRole(player) == null) {
                roleless.add(player);
            }
        }
        if (roleless.isEmpty()) {
            return;
        }
        // 用 gameTime 作种子洗牌，避免 Math.random（此处为模组代码，java.util.Random 可用）
        java.util.Collections.shuffle(roleless, new Random(level.getGameTime()));

        int assigned = 0;
        for (ServerPlayer player : roleless) {
            if (assigned >= PER_DAY) {
                break;
            }
            ResourceLocation roleId = pick(level, data);
            if (roleId == null) {
                break; // 职业池已抽完
            }
            SRERole role = TMMRoles.ROLES.get(roleId);
            if (role == null) {
                continue;
            }
            RoleUtils.changeRole(player, role);
            ServerPlayNetworking.send(player,
                    new AnnounceWelcomePayload(role.getIdentifier().toString(), (int) 0,
                            level.players().stream().filter(GameUtils::isPlayerAliveAndSurvival).toList().size()));

            data.usedAwakenRoles.add(roleId.toString());
            applyModifiers(player, roleId);
            assigned++;
        }
        if (assigned > 0) {
            Noellesroles.LOGGER.info("[60s] 今日觉醒 {} 名玩家职业。", assigned);
        }
    }

    private static ResourceLocation pick(ServerLevel level, SixtySecondsState.Data data) {
        // 每个中立职业独立掷 0.1%
        for (ResourceLocation neutral : NEUTRAL_POOL) {
            if (!data.usedAwakenRoles.contains(neutral.toString())
                    && level.getRandom().nextDouble() < NEUTRAL_CHANCE) {
                return neutral;
            }
        }
        // 平民：随机未用
        List<ResourceLocation> available = new ArrayList<>();
        for (ResourceLocation id : CIVILIAN_POOL) {
            if (!data.usedAwakenRoles.contains(id.toString())) {
                available.add(id);
            }
        }
        if (available.isEmpty()) {
            return null;
        }
        return available.get(level.getRandom().nextInt(available.size()));
    }

    /**
     * 各职业数值修正。全部已实现：
     * <ul>
     *   <li>蛋糕师×2、医生×3 商品价格（经 {@link DynamicShopComponent} 逐商品设置，此处）；</li>
     *   <li>记者 便签×2：觉醒时补发 {@link SixtySecondsBalance#REPORTER_NOTE_COUNT} 张便签（此处）；</li>
     *   <li>玉将军/斗士 冷却×、明星 技能无金币、广播员 每次消耗 100、药剂师 禁调鹤顶红：
     *       走各职业硬编码点的单行委托，见 {@link SixtySecondsRoleTweaks}；</li>
     *   <li>小透明 隐身技能觉醒即解锁（不等原版剩 3 分钟条件）：见
     *       {@link SixtySecondsRoleTweaks#ghostSkillAlwaysUnlocked}；隐身本身走技能（非常驻）。</li>
     * </ul>
     */
    private static void applyModifiers(ServerPlayer player, ResourceLocation roleId) {
        double priceMultiplier = 0;
        if (roleId.equals(ModRoles.CAKE_MAKER_ID)) {
            priceMultiplier = 2.0; // 蛋糕师 价格×2
        } else if (roleId.equals(ModRoles.DOCTOR_ID)) {
            priceMultiplier = 3.0; // 医生 价格×3
        }
        if (priceMultiplier > 0) {
            applyShopPriceMultiplier(player, roleId, priceMultiplier);
        }
        if (roleId.equals(ModRoles.AWESOME_BINGLUS_ID)) {
            giveReporterNotes(player); // 记者 便签×2
        }
    }

    /** 记者觉醒时补发便签（便签×2 = {@link SixtySecondsBalance#REPORTER_NOTE_COUNT} 张）。 */
    private static void giveReporterNotes(ServerPlayer player) {
        net.minecraft.world.item.ItemStack notes =
                io.wifi.starrailexpress.index.TMMItems.NOTE.getDefaultInstance();
        notes.setCount(SixtySecondsBalance.REPORTER_NOTE_COUNT);
        if (!player.getInventory().add(notes)) {
            player.drop(notes, false);
        }
    }

    /** 对该职业商店里的每件商品设置价格乘数（局内动态价格，商店 UI 与扣费一致）。 */
    private static void applyShopPriceMultiplier(ServerPlayer player, ResourceLocation roleId, double multiplier) {
        DynamicShopComponent dynamic = DynamicShopComponent.KEY.get(player);
        for (ShopEntry entry : ShopContent.getShopEntries(roleId)) {
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(entry.stack().getItem());
            dynamic.setMultiplier(itemId, multiplier);
        }
    }
}
