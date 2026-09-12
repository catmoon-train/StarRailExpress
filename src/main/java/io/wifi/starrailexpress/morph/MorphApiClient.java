package io.wifi.starrailexpress.morph;

import java.util.UUID;

import org.agmas.noellesroles.content.block.SREPlushItem;

import io.wifi.starrailexpress.client.morph.ClientMorphCache;
import io.wifi.starrailexpress.client.plush.ClientPlushEquipmentCache;
import io.wifi.starrailexpress.client.util.ClientSkinCache;
import io.wifi.starrailexpress.event.OnGettingPlayerSkin;
import io.wifi.starrailexpress.event.OnGettingPlayerSkin.PlayerSkinResult;
import io.wifi.starrailexpress.event.OnResolveDisplayedSkinOwner;
import io.wifi.starrailexpress.event.client.OnGameFinishedClient;
import io.wifi.starrailexpress.event.client.OnGameStartedClient;
import io.wifi.starrailexpress.hat.HatEquipmentApi;
import io.wifi.starrailexpress.plush.PlushEquipmentIdentity;
import io.wifi.starrailexpress.plush.PlushEquipmentManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.PlayerSkin.Model;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
public class MorphApiClient {
    
    /**
     * 客户端：解析「当前显示的皮肤属于谁」。
     * 先读统一变形覆盖层，再走 {@link OnResolveDisplayedSkinOwner}（帽子 / 职业伪装等）。
     */
    
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
    
    public static boolean isTextureMorph(AbstractClientPlayer player) {
        return player != null && ClientMorphCache.get(player.getUUID()).isTexture();
    }

    /**
     * 客户端：应显示的玩家名 + 名牌前缀（跟随显示皮肤拥有者）。
     */
    
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
        // 开局 / 结束时清空本地缓存，与服务端的生命周期清理保持一致。
        OnGameStartedClient.EVENT.register(ClientMorphCache::clear);
        OnGameFinishedClient.EVENT.register(ClientMorphCache::clear);
    }
}
