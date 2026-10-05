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

package org.agmas.noellesroles.game.roles.neutral.mafia;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.event.OnRevolverUsed;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.GameUtils.WinStatus;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.index.TMMItems;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.agmas.noellesroles.events.OnShopPurchase;
import org.agmas.noellesroles.init.NRSounds;
import org.agmas.noellesroles.packet.MafiaActionC2SPacket;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;
import pro.fazeclan.river.stupid_express.modifier.refugee.cca.RefugeeComponent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MafiaManager {
    private static final Map<UUID, UUID> godfatherByMember = new HashMap<>();
    private static final Map<UUID, SRERole> previousRoleByMember = new HashMap<>();
    /**
     * 通过「领袖技能」加入家族的领袖：领袖 UUID -> 教父 UUID。
     *
     * <p>这类家族成员不是被教父改职来的（不进 {@link #godfatherByMember}），所以教父死亡时
     * 需要单独清理，否则会留下一个"没有教父的家族成员"，让教父独立胜利永远无法达成。
     */
    private static final Map<UUID, UUID> godfatherByLeader = new HashMap<>();
    private static final String MAFIA_SHOP_TAG = "sre_mafia_shop_item";

    public static void registerPayloadTypes() {
        PayloadTypeRegistry.playC2S().register(MafiaActionC2SPacket.ID, MafiaActionC2SPacket.CODEC);
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(MafiaActionC2SPacket.ID,
                (payload, context) -> context.server().execute(
                        () -> handleAction(context.player(), payload.action(), payload.target(), payload.rolePath())));
        OnRevolverUsed.EVENT.register((player, target) -> {
            if (isGodfather(player) && player.getMainHandItem().is(TMMItems.DERRINGER)) {
                consumeBullet(player);
            }
        });
        AllowPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            if (deathReason != null && deathReason.equals(GameConstants.DeathReasons.FELL_OUT_OF_TRAIN))
                return true;
            if (killer instanceof ServerPlayer sk && victim instanceof ServerPlayer sv) {
                if (isMafiaMember(sk) && isMafiaMember(sv))
                    return false;
            }
            return true;
        });

        // 家族成员（非教父）从商店购买物品时打上标记，恢复原始身份时清除
        OnShopPurchase.EVENT.register((player, entry, price) -> {
            if (!(player instanceof ServerPlayer sp))
                return;
            if (!isMafiaCareerMember(sp) || isGodfather(sp))
                return;

            var targetItem = entry.stack().getItem();
            for (var list : sp.getInventory().compartments) {
                for (var stack : list) {
                    if (!stack.isEmpty() && stack.getItem() == targetItem && !isMafiaShopItem(stack)) {
                        markAsMafiaShopItem(stack);
                        return;
                    }
                }
            }
        });
    }

    private static void handleAction(ServerPlayer player, int action, UUID target, String rolePath) {
        if (!GameUtils.isPlayerAliveAndSurvival(player))
            return;
        if (!isGodfather(player))
            return;
        if (target == null)
            return;
        ServerPlayer tgt = player.server.getPlayerList().getPlayer(target);
        if (tgt == null || !GameUtils.isPlayerAliveAndSurvival(tgt))
            return;
        var comp = io.wifi.starrailexpress.api.data.RoleData.getNullable(
                org.agmas.noellesroles.role_data.neutral.GodfatherRoleData.class, player);
        if (comp == null)
            return;
        long now = SRE.getTicksFromGameStart();

        if (action == MafiaActionC2SPacket.RECRUIT_ROLE) {
            // 动态解析被招募的职业：必须是 isMafiaTeam，且不能是教父自己
            SRERole newRole = RoleUtils.getRole(rolePath);
            if (newRole == null || !newRole.isMafiaTeam()
                    || newRole.identifier().equals(ModRoles.GODFATHER.identifier())
                    // 领袖只是"招募教父后反向算作家族成员"，本身不是可招募的家族职业
                    || newRole.identifier().equals(ModRoles.LEADER.identifier())) {
                player.displayClientMessage(net.minecraft.network.chat.Component
                        .translatable("message.noellesroles.godfather.cannot_recruit"), true);
                return;
            }
            if (now < comp.recruitCooldownUntil)
                return;
            if (comp.familyMembers.size() >= comp.recruitLimit)
                return;
            if (!isRecruitable(tgt)) {
                player.displayClientMessage(net.minecraft.network.chat.Component
                        .translatable("message.noellesroles.godfather.cannot_recruit"), true);
                return;
            }
            SRERole prevRole = SREGameWorldComponent.KEY.get(tgt.level()).getRole(tgt);
            previousRoleByMember.put(target, prevRole);
            godfatherByMember.put(target, player.getUUID());
            comp.familyMembers.add(target);
            RoleUtils.changeRole(tgt, newRole);
            comp.recruitCooldownUntil = now + comp.recruitCooldownSeconds * 20L;
            comp.sync();

            // 被招募者：发送欢迎报幕
            RoleUtils.sendWelcomeAnnouncement(tgt, newRole);
            // 教父：确认提示
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("message.noellesroles.godfather.recruit_success",
                            tgt.getDisplayName(),
                            net.minecraft.network.chat.Component
                                    .translatable("announcement.star.role." + newRole.getIdentifier().getPath()))
                            .withStyle(net.minecraft.ChatFormatting.GREEN),
                    true);
        }
    }

    public static boolean isRecruitable(ServerPlayer p) {
        var role = SREGameWorldComponent.KEY.get(p.level()).getRole(p);
        if (role == null || !role.canBeRandomedDefination())
            return false;
        // 傀儡师及其操控的假人不可被教父改变职业
        if (role == ModRoles.PUPPETEER)
            return false;
        // 领袖不可被教父招募：招募会把他变成别的家族职业，领袖身份（及其家族标记）就没了
        if (role.identifier().equals(ModRoles.LEADER.identifier()))
            return false;
        var puppeteer = org.agmas.noellesroles.component.ModComponents.PUPPETEER.get(p);
        if (puppeteer != null && puppeteer.isControllingPuppet)
            return false;
        return true;
    }

    public static boolean isGodfather(ServerPlayer p) {
        return SREGameWorldComponent.KEY.get(p.level()).isRole(p, ModRoles.GODFATHER);
    }

    public static boolean isParasol(ServerPlayer p) {
        return SREGameWorldComponent.KEY.get(p.level()).isRole(p, ModRoles.PARASOL);
    }

    public static boolean isMafiaMember(ServerPlayer p) {
        var role = SREGameWorldComponent.KEY.get(p.level()).getRole(p);
        return role != null && role.isMafiaTeam();
    }

    /**
     * 是否"真正的 mafia 家族职业成员"（教父通过招募把玩家改成家族职业的那些人）。
     *
     * <p>与 {@link #isMafiaMember} 的区别：领袖招募教父后也会满足 isMafiaMember，
     * 但他不是家族职业成员，不该享受家族商店物品标记等"按职业成员"处理。
     */
    public static boolean isMafiaCareerMember(ServerPlayer p) {
        var role = SREGameWorldComponent.KEY.get(p.level()).getRole(p);
        return role != null && role.isMafiaTeam() && !role.identifier().equals(ModRoles.LEADER.identifier());
    }

    // ==================== 领袖招募教父后加入家族 ====================

    /**
     * 标记「该领袖已招募教父」，此刻起这位领袖被视为 mafia 家族成员。
     *
     * <p>只由服务端调用；客户端通过 {@code LeaderRoleData.mafiaTeamActive} 的同步结果判断。
     */
    public static void markLeaderAsGodfatherFamilyMember(ServerPlayer leader, ServerPlayer godfather) {
        if (leader == null || godfather == null) {
            return;
        }
        godfatherByLeader.put(leader.getUUID(), godfather.getUUID());
        var data = io.wifi.starrailexpress.api.data.RoleData.getNullable(
                org.agmas.noellesroles.role_data.neutral.LeaderRoleData.class, leader);
        if (data != null) {
            data.setMafiaTeamActive(true);
        }
    }

    /** 场上是否存在"已加入教父家族"的存活领袖（服务端权威判定，供 LeaderRole#isMafiaTeam 使用）。 */
    public static boolean hasLivingGodfatherFamilyLeader() {
        if (godfatherByLeader.isEmpty()) {
            return false;
        }
        for (var entry : godfatherByLeader.entrySet()) {
            ServerPlayer leader = resolveOnline(entry.getKey());
            if (leader == null || !GameUtils.isPlayerAliveAndSurvival(leader)) {
                continue;
            }
            // 教父本人必须还活着，否则"家族"已经不存在
            ServerPlayer godfather = resolveOnline(entry.getValue());
            if (godfather == null || !GameUtils.isPlayerAliveAndSurvival(godfather)) {
                continue;
            }
            return true;
        }
        return false;
    }

    /** 取消所有"通过领袖技能加入家族"的标记（局末 / 教父死亡时调用）。 */
    public static void clearLeaderFamilyMarks() {
        if (godfatherByLeader.isEmpty()) {
            return;
        }
        for (UUID leaderId : new ArrayList<>(godfatherByLeader.keySet())) {
            ServerPlayer leader = resolveOnline(leaderId);
            if (leader == null) {
                continue;
            }
            var data = io.wifi.starrailexpress.api.data.RoleData.getNullable(
                    org.agmas.noellesroles.role_data.neutral.LeaderRoleData.class, leader);
            if (data != null) {
                data.setMafiaTeamActive(false);
            }
        }
        godfatherByLeader.clear();
    }

    private static ServerPlayer resolveOnline(UUID id) {
        var server = SRE.SERVER;
        return server == null ? null : server.getPlayerList().getPlayer(id);
    }

    public static void onGodfatherDeath(ServerPlayer godfather) {
        UUID gfId = godfather.getUUID();
        // 领袖通过技能加入的家族成员：家族随教父一起消失
        if (godfatherByLeader.containsValue(gfId)) {
            clearLeaderFamilyMarks();
        }
        // 亡命徒时刻（难民修饰符触发）期间，教父死亡不还原家族成员职业
        boolean inLooseEndMoment = RefugeeComponent.KEY.get(godfather.level()).isAnyRevivals;
        for (UUID memberId : new ArrayList<>(godfatherByMember.keySet())) {
            if (gfId.equals(godfatherByMember.get(memberId))) {
                ServerPlayer member = godfather.server.getPlayerList().getPlayer(memberId);
                // 只有在亡命徒时刻之外，且当前仍然是家族成员，才变回原来的职业
                if (!inLooseEndMoment && member != null && isMafiaMember(member)
                        && previousRoleByMember.containsKey(memberId)) {
                    RoleUtils.changeRole(member, previousRoleByMember.get(memberId), true, false, false, true, true);
                    // 清除从家族商店购买的标记物品
                    clearMafiaShopItems(member);
                }
                godfatherByMember.remove(memberId);
                previousRoleByMember.remove(memberId);
            }
        }
    }

    // Bullet system
    public static int getLoadedBullets(ServerPlayer godfather) {
        var comp = io.wifi.starrailexpress.api.data.RoleData.getNullable(
                org.agmas.noellesroles.role_data.neutral.GodfatherRoleData.class, godfather);
        return comp != null ? comp.loadedBullets : 0;
    }

    public static int getMaxLoadedBullets(ServerPlayer godfather) {
        var comp = io.wifi.starrailexpress.api.data.RoleData.getNullable(
                org.agmas.noellesroles.role_data.neutral.GodfatherRoleData.class, godfather);
        return comp != null ? comp.maxLoadedBullets : 0;
    }

    public static void consumeBullet(ServerPlayer godfather) {
        var comp = io.wifi.starrailexpress.api.data.RoleData.getNullable(
                org.agmas.noellesroles.role_data.neutral.GodfatherRoleData.class, godfather);
        if (comp != null && comp.loadedBullets > 0) {
            comp.loadedBullets--;
            comp.sync();
        }
        syncDerringerUsed(godfather, comp != null && comp.loadedBullets > 0);
    }

    public static boolean tryLoadBullet(ServerPlayer godfather) {
        var comp = io.wifi.starrailexpress.api.data.RoleData.getNullable(
                org.agmas.noellesroles.role_data.neutral.GodfatherRoleData.class, godfather);
        if (comp == null || comp.loadedBullets >= comp.maxLoadedBullets)
            return false;
        comp.loadedBullets++;
        comp.sync();
        syncDerringerUsed(godfather, true);
        return true;
    }

    private static void syncDerringerUsed(ServerPlayer godfather, boolean hasBullets) {
        for (var list : godfather.getInventory().compartments) {
            for (var stack : list) {
                if (stack.is(TMMItems.DERRINGER)) {
                    stack.set(SREDataComponentTypes.USED, !hasBullets);
                    return;
                }
            }
        }
    }

    public static void playSpawnSound(ServerPlayer godfather) {
        for (var p : godfather.serverLevel().players()) {
            if (p != null) {
                p.playNotifySound(NRSounds.MAFIA, SoundSource.MASTER, 1.0F, 1.0F);
            }
        }
    }

    public static boolean checkMafiaVictory(ServerLevel level) {
        int alive = 0, mafiaAlive = 0;
        for (ServerPlayer p : level.players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(p))
                continue;
            alive++;
            if (isMafiaMember(p))
                mafiaAlive++;
        }
        if (mafiaAlive > 0 && alive == mafiaAlive) {
            RoleUtils.customWinnerWin(level, WinStatus.CUSTOM, "godfather",
                    java.util.OptionalInt.of(ModRoles.GODFATHER.color()));
            return true;
        }
        return false;
    }

    public static boolean shouldPreventGameEnd(ServerLevel level) {
        for (ServerPlayer p : level.players())
            if (GameUtils.isPlayerAliveAndSurvival(p) && isGodfather(p))
                return true;
        return false;
    }

    public static void clearAll() {
        godfatherByMember.clear();
        previousRoleByMember.clear();
        clearLeaderFamilyMarks();
    }

    // ==================== 家族商店物品标记 ====================

    /** 给物品打上家族商店标记 */
    public static void markAsMafiaShopItem(ItemStack stack) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();
        tag.putBoolean(MAFIA_SHOP_TAG, true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /** 检查物品是否带有家族商店标记 */
    public static boolean isMafiaShopItem(ItemStack stack) {
        if (stack.isEmpty())
            return false;
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            return customData.copyTag().getBoolean(MAFIA_SHOP_TAG);
        }
        return false;
    }

    /** 清除玩家背包中所有带家族商店标记的物品 */
    public static void clearMafiaShopItems(ServerPlayer player) {
        for (var list : player.getInventory().compartments) {
            for (int i = 0; i < list.size(); i++) {
                if (isMafiaShopItem(list.get(i))) {
                    list.set(i, ItemStack.EMPTY);
                }
            }
        }
    }
}
