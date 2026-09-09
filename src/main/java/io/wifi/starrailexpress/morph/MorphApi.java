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

package io.wifi.starrailexpress.morph;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.client.morph.ClientMorphCache;
import io.wifi.starrailexpress.client.plush.ClientPlushEquipmentCache;
import io.wifi.starrailexpress.client.util.ClientSkinCache;
import io.wifi.starrailexpress.event.OnGettingPlayerSkin;
import io.wifi.starrailexpress.event.OnGettingPlayerSkin.PlayerSkinResult;
import io.wifi.starrailexpress.event.OnResolveDisplayedSkinOwner;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.hat.HatEquipmentApi;
import io.wifi.starrailexpress.plush.PlushEquipmentIdentity;
import io.wifi.starrailexpress.plush.PlushEquipmentManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin.Model;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.content.block.SREPlushItem;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * 统一变形 API。
 * <p>
 * 服务端写入外观后会同步到所有客户端。玩家变形为另一名玩家时，皮肤、帽子、名牌与身份玩偶
 * 一律跟随「显示皮肤拥有者」，避免用赞助玩偶 / 名牌识破伪装。
 * <p>
 * 贴图变形没有可复制的真实玩家，帽子 / 名牌前缀 / 玩偶按隐藏处理（与固定职业皮肤一致）。
 *
 * <pre>{@code
 * MorphApi.morphToPlayer(player, target.getUUID());
 * MorphApi.morphToRandomPlayer(player);
 * MorphApi.morphToTexture(player, SRE.id("textures/entity/disguise/disguise_skin_1.png"), false);
 * MorphApi.clearMorph(player);
 * }</pre>
 */
public final class MorphApi {

    private MorphApi() {
    }

    public static MorphAppearance getAppearance(Player player) {
        if (player == null) {
            return MorphAppearance.NONE;
        }
        if (player.level() != null && player.level().isClientSide) {
            return ClientMorphCache.get(player.getUUID());
        }
        return MorphManager.get(player.getUUID());
    }

    public static MorphAppearance getAppearance(UUID uuid) {
        if (uuid == null) {
            return MorphAppearance.NONE;
        }
        return MorphManager.get(uuid);
    }

    public static boolean isMorphed(Player player) {
        return !getAppearance(player).isNone();
    }

    /**
     * 变形成指定玩家：复制其皮肤，并绑定其帽子 / 名牌 / 身份玩偶。
     *
     * @param durationTicks 持续 tick；{@code <=0} 直到 {@link #clearMorph} 或玩家重置
     */
    public static boolean morphToPlayer(ServerPlayer player, UUID targetUuid, int durationTicks) {
        if (player == null || targetUuid == null) {
            return false;
        }
        if (player.getUUID().equals(targetUuid)) {
            return clearMorph(player);
        }
        return MorphManager.set(player, MorphAppearance.ofPlayer(targetUuid), expireAt(durationTicks));
    }

    public static boolean morphToPlayer(ServerPlayer player, UUID targetUuid) {
        return morphToPlayer(player, targetUuid, 0);
    }

    public static boolean morphToPlayer(ServerPlayer player, Player target) {
        return target == null ? false : morphToPlayer(player, target.getUUID(), 0);
    }

    public static boolean morphToPlayer(ServerPlayer player, Player target, int durationTicks) {
        return target == null ? false : morphToPlayer(player, target.getUUID(), durationTicks);
    }

    /**
     * 随机变形成一名存活玩家（默认排除自己与旁观/创造）。
     */
    public static boolean morphToRandomPlayer(ServerPlayer player, int durationTicks) {
        return morphToRandomPlayer(player, MorphApi::defaultRandomCandidate, durationTicks);
    }

    public static boolean morphToRandomPlayer(ServerPlayer player) {
        return morphToRandomPlayer(player, 0);
    }

    public static boolean morphToRandomPlayer(ServerPlayer player, Predicate<ServerPlayer> filter) {
        return morphToRandomPlayer(player, filter, 0);
    }

    public static boolean morphToRandomPlayer(ServerPlayer player, Predicate<ServerPlayer> filter,
            int durationTicks) {
        if (player == null || player.getServer() == null) {
            return false;
        }
        Predicate<ServerPlayer> predicate = filter == null ? MorphApi::defaultRandomCandidate : filter;
        List<ServerPlayer> candidates = new ArrayList<>();
        for (ServerPlayer other : player.serverLevel().players()) {
            if (other.getUUID().equals(player.getUUID())) {
                continue;
            }
            if (predicate.test(other)) {
                candidates.add(other);
            }
        }
        if (candidates.isEmpty()) {
            return false;
        }
        ServerPlayer target = candidates.get(player.getRandom().nextInt(candidates.size()));
        return morphToPlayer(player, target.getUUID(), durationTicks);
    }

    /**
     * 使用指定贴图变形（wide/slim）。没有真实玩家可复制，外观附属物隐藏。
     */
    public static boolean morphToTexture(ServerPlayer player, ResourceLocation texture, boolean slim,
            int durationTicks) {
        if (player == null || texture == null) {
            return false;
        }
        return MorphManager.set(player, MorphAppearance.ofTexture(texture, slim), expireAt(durationTicks));
    }

    public static boolean morphToTexture(ServerPlayer player, ResourceLocation texture, boolean slim) {
        return morphToTexture(player, texture, slim, 0);
    }

    public static boolean clearMorph(ServerPlayer player) {
        return MorphManager.clear(player, false);
    }

    private static boolean defaultRandomCandidate(ServerPlayer player) {
        return GameUtils.isPlayerAliveAndSurvival(player);
    }

    private static long expireAt(int durationTicks) {
        if (durationTicks <= 0) {
            return 0;
        }
        return SRE.getTicksFromGameStart() + durationTicks;
    }

    /**
     * 客户端：解析「当前显示的皮肤属于谁」。
     * 先读统一变形覆盖层，再走 {@link OnResolveDisplayedSkinOwner}（帽子 / 职业伪装等）。
     */
    @Environment(EnvType.CLIENT)
    public static UUID resolveDisplayedOwnerUuid(AbstractClientPlayer player) {
        if (player == null) {
            return null;
        }
        UUID resolved = OnResolveDisplayedSkinOwner.EVENT.invoker().resolveDisplayedOwner(player);
        return resolved != null ? resolved : player.getUUID();
    }

    /**
     * 客户端：当前是否为「无真实玩家可复制」的贴图变形。
     */
    @Environment(EnvType.CLIENT)
    public static boolean isTextureMorph(AbstractClientPlayer player) {
        return player != null && ClientMorphCache.get(player.getUUID()).isTexture();
    }

    /**
     * 客户端：应显示的玩家名 + 名牌前缀（跟随显示皮肤拥有者）。
     */
    @Environment(EnvType.CLIENT)
    public static Component getDisplayedName(Player target) {
        if (target == null) {
            return Component.literal("");
        }
        if (!(target instanceof AbstractClientPlayer clientPlayer)) {
            return fallbackName(target.getUUID(), target.getName());
        }
        if (isTextureMorph(clientPlayer) || HatEquipmentApi.shouldHideBoundCosmetics(clientPlayer)) {
            return target.getName();
        }
        UUID owner = resolveDisplayedOwnerUuid(clientPlayer);
        if (owner == null || owner.equals(target.getUUID())) {
            return fallbackName(target.getUUID(), target.getName());
        }
        PlayerInfo info = ClientSkinCache.getCachedPlayerInfo(owner);
        Minecraft client = Minecraft.getInstance();
        if (info == null && client.getConnection() != null) {
            info = client.getConnection().getPlayerInfo(owner);
        }
        if (info != null && info.getProfile() != null) {
            MutableComponent name = Component.literal(info.getProfile().getName());
            var prefix = ClientSkinCache.somePrefix(owner);
            return prefix == null ? name : Component.literal("").append(prefix).append(name);
        }
        return fallbackName(target.getUUID(), target.getName());
    }

    @Environment(EnvType.CLIENT)
    private static Component fallbackName(UUID uuid, Component playerName) {
        var prefix = ClientSkinCache.somePrefix(uuid);
        if (prefix == null) {
            return playerName;
        }
        return Component.literal("").append(prefix).append(playerName);
    }

    /**
     * 客户端：显示皮肤拥有者的身份玩偶（无则 {@link ItemStack#EMPTY}）。
     */
    @Environment(EnvType.CLIENT)
    public static ItemStack getDisplayedPlushStack(AbstractClientPlayer player) {
        if (player == null || isTextureMorph(player) || HatEquipmentApi.shouldHideBoundCosmetics(player)) {
            return ItemStack.EMPTY;
        }
        UUID owner = resolveDisplayedOwnerUuid(player);
        ItemStack cached = ClientPlushEquipmentCache.getStack(owner);
        if (!cached.isEmpty()) {
            return cached;
        }
        if (owner.equals(player.getUUID())) {
            PlushEquipmentIdentity own = PlushEquipmentManager.findIdentityPlush(player);
            return own == null ? ItemStack.EMPTY : own.toStack();
        }
        return ItemStack.EMPTY;
    }

    /**
     * 客户端：把手持玩偶重映射为显示拥有者的玩偶。
     * 持有玩偶时替换；若自己没持有但目标有玩偶且副手为空，则在副手显示，避免「缺玩偶识人」。
     *
     * @return 应渲染的物品；不处理时返回 {@code null}
     */
    @Environment(EnvType.CLIENT)
    public static @Nullable ItemStack remapHeldPlush(Player player, ItemStack stack, boolean mainHand) {
        if (!(player instanceof AbstractClientPlayer clientPlayer)) {
            return null;
        }
        boolean holdingPlush = stack.getItem() instanceof SREPlushItem;
        ItemStack displayed = getDisplayedPlushStack(clientPlayer);
        if (holdingPlush) {
            if (displayed.isEmpty()) {
                return ItemStack.EMPTY;
            }
            return ItemStack.isSameItemSameComponents(stack, displayed) ? null : displayed;
        }
        if (!mainHand && stack.isEmpty() && !displayed.isEmpty()) {
            ItemStack main = player.getMainHandItem();
            if (!(main.getItem() instanceof SREPlushItem)) {
                return displayed;
            }
        }
        return null;
    }

    /**
     * 注册客户端默认解析器（皮肤覆盖层 + 显示拥有者）。应在帽子默认解析器之前调用。
     */
    @Environment(EnvType.CLIENT)
    public static void registerClient() {
        OnResolveDisplayedSkinOwner.EVENT.register(player -> {
            if (io.wifi.starrailexpress.SRE.isLobby || io.wifi.starrailexpress.client.SREClient.isInLobby) {
                return null;
            }
            MorphAppearance overlay = ClientMorphCache.get(player.getUUID());
            if (overlay.isPlayer() && overlay.targetPlayer() != null
                    && !overlay.targetPlayer().equals(player.getUUID())) {
                return overlay.targetPlayer();
            }
            return null;
        });
        OnGettingPlayerSkin.EVENT.register((player, originalSkin) -> {
            MorphAppearance overlay = ClientMorphCache.get(player.getUUID());
            if (overlay.isTexture() && overlay.texture() != null) {
                return PlayerSkinResult.playerSkin(overlay.texture(),
                        overlay.slim() ? Model.SLIM : Model.WIDE);
            }
            if (overlay.isPlayer() && overlay.targetPlayer() != null
                    && !overlay.targetPlayer().equals(player.getUUID())) {
                PlayerInfo info = ClientSkinCache.getCachedPlayerInfo(overlay.targetPlayer());
                Minecraft client = Minecraft.getInstance();
                if (info == null && client.getConnection() != null) {
                    info = client.getConnection().getPlayerInfo(overlay.targetPlayer());
                }
                if (info != null && info.getSkin() != null) {
                    return PlayerSkinResult.playerSkin(info.getSkin());
                }
            }
            return PlayerSkinResult.SKIP;
        });
    }
}
