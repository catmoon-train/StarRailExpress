package org.agmas.noellesroles.utils;

import java.util.OptionalInt;

import org.agmas.harpymodloader.events.ModdedRoleAssigned;
import org.agmas.harpymodloader.events.ModdedRoleRemoved;
import org.agmas.harpymodloader.modifiers.Modifier;
import org.agmas.noellesroles.Noellesroles;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import io.wifi.starrailexpress.api.Role;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.GameRoundEndComponent;
import io.wifi.starrailexpress.cca.GameWorldComponent;
import io.wifi.starrailexpress.game.GameFunctions;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
import io.wifi.starrailexpress.network.original.AnnounceWelcomePayload;
import io.wifi.starrailexpress.SRE;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 角色相关工具
 */
public class RoleUtils {
    public static void customWinnerWin(ServerLevel serverWorld, GameFunctions.WinStatus WinStatus,
            @Nullable String winnerId, @Nullable OptionalInt winnerColor) {
        var roundComponent = GameRoundEndComponent.KEY.get(serverWorld);
        if (winnerId != null) {
            if (roundComponent != null) {
                roundComponent.CustomWinnerID = winnerId;
            }
        }
        if (winnerColor != null) {
            if (!winnerColor.isEmpty()) {
                if (roundComponent != null) {
                    roundComponent.CustomWinnerColor = winnerColor.getAsInt();
                }
            }
        }
        GameRoundEndComponent.KEY.get(serverWorld).setRoundEndData(serverWorld.players(),
                WinStatus);
        GameFunctions.stopGame(serverWorld);
    }

    public static void playSound(ServerPlayer serverPlayer, SoundEvent soundEvent, SoundSource soundSource,
            float volume,
            float pitch) {
        double x = serverPlayer.getX();
        double y = serverPlayer.getY();
        double z = serverPlayer.getZ();
        playSound(serverPlayer, soundEvent, soundSource, x, y, z, volume, pitch);
    }

    public static void playSound(ServerPlayer serverPlayer, SoundEvent soundEvent, SoundSource soundSource, double x,
            double y, double z, float volume, float pitch) {
        serverPlayer.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(soundEvent),
                soundSource, x, y, z, volume, pitch, serverPlayer.getRandom().nextLong()));
    }

    public static void RemoveAllPlayerAttributes(ServerPlayer serverPlayer) {
        var attris = serverPlayer.getAttributes();
        if (attris != null) {
            var allAttris = attris.getSyncableAttributes();
            if (allAttris != null && allAttris.size() > 0) {
                Multimap<Holder<Attribute>, AttributeModifier> multimap1 = ArrayListMultimap.create();
                for (var attri : allAttris) {
                    var amodifiers = attri.getModifiers();
                    if (amodifiers != null) {
                        for (var mo : amodifiers) {
                            multimap1.put(attri.getAttribute(), mo);
                        }
                    }
                }
                attris.removeAttributeModifiers(multimap1);
            }
        }
    }

    public static boolean RemoveAllEffects(Player entity) {
        return entity.removeAllEffects();
    }

    public static boolean isPlayerHasFreeSlot(@NotNull Player player) {
        for (int i = 0; i < 9; ++i) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public static boolean insertStackInFreeSlot(@NotNull Player player, ItemStack stackToInsert) {
        for (int i = 0; i < 9; ++i) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) {
                player.getInventory().setItem(i, stackToInsert);
                return true;
            }
        }

        return false;
    }

    public static void removeStackItem(ServerPlayer player, int slot) {
        player.getInventory().setItem(slot, net.minecraft.world.item.ItemStack.EMPTY);
    }

    public static int dropAndClearAllSatisfiedItems(ServerPlayer player, Item item) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(item)) {
                player.drop(player.getInventory().getItem(i), false);
                player.getInventory().setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
                count++;
            }
        }
        return count;
    }

    public static int dropAndClearAllSatisfiedItems(ServerPlayer player, TagKey<Item> tagKey) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(tagKey)) {
                player.drop(player.getInventory().getItem(i), false);
                player.getInventory().setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
                count++;
            }
        }
        return count;
    }

    public static int clearAllSatisfiedItems(ServerPlayer player, Item item) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(item)) {
                player.getInventory().setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
                count++;
            }
        }
        return count;
    }

    public static int clearAllSatisfiedItems(ServerPlayer player, TagKey<Item> tagKey) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(tagKey)) {
                player.getInventory().setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
                count++;
            }
        }
        return count;
    }

    public static int clearAllKnives(ServerPlayer player) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(TMMItems.KNIFE)) {
                player.getInventory().setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
                count++;
            }
        }
        return count;
    }

    public static int clearAllRevolver(ServerPlayer player) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(TMMItemTags.GUNS)) {
                player.getInventory().setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
                count++;
            }
        }
        return count;
    }

    public static void sendWelcomeAnnouncement(ServerPlayer player) {
        GameWorldComponent gameWorldComponent = (GameWorldComponent) GameWorldComponent.KEY
                .get(player.level());
        final var size = gameWorldComponent.getAllKillerTeamPlayers().size();
        ServerPlayNetworking.send(player, new AnnounceWelcomePayload(
                gameWorldComponent.getRole(player).getIdentifier().toString(), size, 0));
    }

    public static void changeRole(Player player, Role role) {
        changeRole(player, role, true);
    }

    public static void changeRole(Player player, Role role, boolean record) {

        GameWorldComponent gameWorldComponent = GameWorldComponent.KEY.get(player.level());
        // 删除旧职业
        var oldRole = gameWorldComponent.getRole(player);
        if (oldRole != null) {
            if (record) {
                SRE.REPLAY_MANAGER.recordPlayerRoleChange(player.getUUID(), oldRole, role);
            }
            ((ModdedRoleRemoved) ModdedRoleRemoved.EVENT.invoker()).removeModdedRole(player, oldRole);
        }
        // 给新职业
        gameWorldComponent.addRole(player, role);
        // 触发事件
        ((ModdedRoleAssigned) ModdedRoleAssigned.EVENT.invoker()).assignModdedRole(player, role);
    }

    public static MutableComponent getRoleName(ResourceLocation roleIdentifier) {
        if (roleIdentifier == null)
            return null;
        String translationKey = "announcement.star.role." + roleIdentifier.getPath();
        return Component.translatable(translationKey);
    }

    /**
     * 获取角色的显示名称
     */
    public static MutableComponent getRoleName(Role role) {
        // 尝试获取翻译后的角色名称
        return getRoleName(role.identifier());
    }

    /**
     * 判断职业是否相等
     * 
     * @return 返回是否相等
     */
    public static boolean compareRole(Role role_a, Role role_b) {
        if (role_a == null && role_b == null)
            return true;
        if (role_a == null || role_b == null)
            return false;
        return role_a.equals(role_b);
    }

    /**
     * 获取一个职业从他的路径
     * 
     * @return 返回Role
     */
    public static Role getRoleFromName(String roleName) {
        var roles = Noellesroles.id(roleName);
        return TMMRoles.ROLES.get(roles);
    }

    public static Role getRole(ResourceLocation role) {
        if (role == null)
            return null;
        return TMMRoles.ROLES.get(role);
    }

    public static MutableComponent getRoleDescription(Role selectedRole) {
        if (selectedRole == null)
            return null;
        return Component.translatable("info.screen.roleid." + selectedRole.getIdentifier().getPath());
    }

    public static MutableComponent getModifierName(ResourceLocation modifier) {
        return Component
                .translatable("announcement.star.modifier." + modifier.getPath());
    }

    public static MutableComponent getModifierName(Modifier modifier) {
        return Component
                .translatable("announcement.star.modifier." + modifier.identifier().getPath());
    }

    public static MutableComponent getModifierDescription(Modifier modifier) {
        return Component
                .translatable("info.screen.modifier." + modifier.identifier().getPath());
    }

    public static Component getRoleOrModifierName(Object role) {
        if (role instanceof Role r) {
            return getRoleName(r);
        } else if (role instanceof Modifier m) {
            return m.getName(false);
        } else {
            return Component.literal("Unknown");
        }
    }

    public static MutableComponent getRoleOrModifierNameWithColor(Object role) {
        if (role == null)
            return Component.translatable("Unknown");
        if (role instanceof Role r) {
            return getRoleName(r).withColor(0xff000000 | r.color());
        } else if (role instanceof Modifier m) {
            return m.getName(true);
        } else {
            return Component.translatable("Unknown");
        }
    }

    public static MutableComponent getRoleOrModifierDescription(Object role) {
        if (role instanceof Role r) {
            return getRoleDescription(r);
        } else if (role instanceof Modifier m) {
            return getModifierDescription(m);
        } else {
            return Component.literal("Unknown");
        }
    }

    public static int getRoleOrModifierColor(Object role) {
        if (role instanceof Role r) {
            return 0xff000000 | r.color();
        } else if (role instanceof Modifier m) {
            return 0xff000000 | m.color();
        } else {
            return java.awt.Color.WHITE.getRGB();
        }
    }

    public static MutableComponent getRoleOrModifierTypeName(Object role) {
        if (role instanceof Role) {
            return Component.translatable("display.type.role");
        } else if (role instanceof Modifier) {
            return Component.translatable("display.type.modifier");
        } else {
            return Component.translatable("display.type.unknown");
        }
        //
    }
}
