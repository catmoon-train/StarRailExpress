package org.agmas.noellesroles.game.roles.neutral.mafia;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.event.OnPlayerDeathWithKiller;
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
    private static final String MAFIA_SHOP_TAG = "sre_mafia_shop_item";

    public static void register() {
        PayloadTypeRegistry.playC2S().register(MafiaActionC2SPacket.ID, MafiaActionC2SPacket.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(MafiaActionC2SPacket.ID,
            (payload, context) -> context.server().execute(() -> handleAction(context.player(), payload.action(), payload.target(), payload.rolePath())));
        OnRevolverUsed.EVENT.register((player, target) -> {
            if (isGodfather(player) && player.getMainHandItem().is(TMMItems.DERRINGER)) {
                consumeBullet(player);
            }
        });
        OnPlayerDeathWithKiller.EVENT.register((victim, killer, reason) -> {
            if (killer instanceof ServerPlayer sp) {
                if (isGodfather(sp)) {
                    SREPlayerShopComponent.KEY.get(sp).addToBalance(50);
                } else if (isParasol(sp)) {
                    SREPlayerShopComponent.KEY.get(sp).addToBalance(50);
                } else if (isMafiaMember(sp)) {
                    SREPlayerShopComponent.KEY.get(sp).addToBalance(75);
                }
            }
        });
        GameUtils.CustomWinnersPredicates.add(entry -> {
            if (entry.getValue() != null && entry.getValue().equals("godfather")) {
                return isMafiaMember((ServerPlayer) entry.getKey());
            }
            return false;
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
            if (!(player instanceof ServerPlayer sp)) return;
            if (!isMafiaMember(sp) || isGodfather(sp)) return;

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
        if (!isGodfather(player)) return;
        if (target == null) return;
        ServerPlayer tgt = player.server.getPlayerList().getPlayer(target);
        if (tgt == null || !GameUtils.isPlayerAliveAndSurvival(tgt)) return;
        var comp = GodfatherComponent.KEY.get(player);
        long now = player.level().getGameTime();

        if (action == MafiaActionC2SPacket.RECRUIT_ROLE) {
            // 动态解析被招募的职业：必须是 isMafiaTeam，且不能是教父自己
            SRERole newRole = RoleUtils.getRole(rolePath);
            if (newRole == null || !newRole.isMafiaTeam() || newRole.identifier().equals(ModRoles.GODFATHER.identifier())) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.noellesroles.godfather.cannot_recruit"), true);
                return;
            }
            if (now < comp.recruitCooldownUntil) return;
            if (comp.familyMembers.size() >= comp.recruitLimit) return;
            if (!isRecruitable(tgt)) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.noellesroles.godfather.cannot_recruit"), true);
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
                            net.minecraft.network.chat.Component.translatable("announcement.star.role." + newRole.getIdentifier().getPath()))
                            .withStyle(net.minecraft.ChatFormatting.GREEN),
                    true);
        }
    }

    public static boolean isRecruitable(ServerPlayer p) {
        var role = SREGameWorldComponent.KEY.get(p.level()).getRole(p);
        if (role == null || !role.canBeRandomed()) return false;
        // 傀儡师及其操控的假人不可被教父改变职业
        if (role == ModRoles.PUPPETEER) return false;
        var puppeteer = org.agmas.noellesroles.component.ModComponents.PUPPETEER.get(p);
        if (puppeteer != null && puppeteer.isControllingPuppet) return false;
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

    public static void onGodfatherDeath(ServerPlayer godfather) {
        UUID gfId = godfather.getUUID();
        // 亡命徒时刻（难民修饰符触发）期间，教父死亡不还原家族成员职业
        boolean inLooseEndMoment = RefugeeComponent.KEY.get(godfather.level()).isAnyRevivals;
        for (UUID memberId : new ArrayList<>(godfatherByMember.keySet())) {
            if (gfId.equals(godfatherByMember.get(memberId))) {
                ServerPlayer member = godfather.server.getPlayerList().getPlayer(memberId);
                // 只有在亡命徒时刻之外，且当前仍然是家族成员，才变回原来的职业
                if (!inLooseEndMoment && member != null && isMafiaMember(member)
                        && previousRoleByMember.containsKey(memberId)) {
                    RoleUtils.changeRole(member, previousRoleByMember.get(memberId));
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
        return GodfatherComponent.KEY.get(godfather).loadedBullets;
    }
    public static int getMaxLoadedBullets(ServerPlayer godfather) {
        return GodfatherComponent.KEY.get(godfather).maxLoadedBullets;
    }
    public static void consumeBullet(ServerPlayer godfather) {
        var comp = GodfatherComponent.KEY.get(godfather);
        if (comp.loadedBullets > 0) { comp.loadedBullets--; comp.sync(); }
        syncDerringerUsed(godfather, comp.loadedBullets > 0);
    }
    public static boolean tryLoadBullet(ServerPlayer godfather) {
        var comp = GodfatherComponent.KEY.get(godfather);
        if (comp.loadedBullets >= comp.maxLoadedBullets) return false;
        comp.loadedBullets++; comp.sync();
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
            if (!GameUtils.isPlayerAliveAndSurvival(p)) continue;
            alive++;
            if (isMafiaMember(p)) mafiaAlive++;
        }
        if (mafiaAlive > 0 && alive == mafiaAlive) {
            RoleUtils.customWinnerWin(level, WinStatus.CUSTOM, "godfather", java.util.OptionalInt.of(ModRoles.GODFATHER.color()));
            return true;
        }
        return false;
    }

    public static boolean shouldPreventGameEnd(ServerLevel level) {
        for (ServerPlayer p : level.players())
            if (GameUtils.isPlayerAliveAndSurvival(p) && isGodfather(p)) return true;
        return false;
    }

    public static void clearAll() {
        godfatherByMember.clear();
        previousRoleByMember.clear();
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
        if (stack.isEmpty()) return false;
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
