package org.agmas.noellesroles.utils;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerStatsComponent;
import io.wifi.starrailexpress.cca.SRERoleWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.GameUtils.WinStatus;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
import io.wifi.starrailexpress.network.original.AnnounceWelcomePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
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
import org.agmas.harpymodloader.events.ModdedRoleAssigned;
import org.agmas.harpymodloader.events.ModdedRoleRemoved;
import org.agmas.harpymodloader.modded_murder.PlayerRoleWeightManager;
import org.agmas.harpymodloader.modifiers.SREModifier;
import org.agmas.noellesroles.Noellesroles;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.OptionalInt;

/**
 * 角色相关工具
 */
public class RoleUtils extends MCItemsUtils {
    public static void customWinnerWin(ServerLevel serverWorld,
            @NotNull String winnerId, @NotNull int winnerColor) {
        customWinnerWin(serverWorld, WinStatus.CUSTOM, winnerId, OptionalInt.of(winnerColor));
    }

    public static void customWinnerWin(ServerLevel serverWorld, GameUtils.WinStatus WinStatus,
            @Nullable String winnerId, @Nullable OptionalInt winnerColor) {
        var roundComponent = SREGameRoundEndComponent.KEY.get(serverWorld);
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
        SREGameRoundEndComponent.KEY.get(serverWorld).setRoundEndData(serverWorld.players(),
                WinStatus);
        GameUtils.stopGame(serverWorld);
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
            if (allAttris != null && !allAttris.isEmpty()) {
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
        if (entity.getActiveEffects() != null && !entity.getActiveEffects().isEmpty())
            return entity.removeAllEffects();
        return false;
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

    public static int dropAndClearAllGuns(ServerPlayer player) {
        int count = MCItemsUtils.clearItem(player, TMMItemTags.GUNS);
        for (int i = 0; i < count; i++) {
            player.drop(TMMItems.REVOLVER.getDefaultInstance(), false);
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

    public static void sendWelcomeAnnouncement(ServerPlayer player, ResourceLocation identifier, final int size) {
        if (identifier == null)
            return;
        ServerPlayNetworking.send(player, new AnnounceWelcomePayload(
                identifier.toString(), size, 0));
    }

    public static void sendWelcomeAnnouncement(ServerPlayer player, ResourceLocation identifier) {
        SREGameWorldComponent gameWorldComponent = (SREGameWorldComponent) SREGameWorldComponent.KEY
                .get(player.level());
        final var size = gameWorldComponent.getAllKillerPlayers().size();
        if (identifier == null)
            return;
        sendWelcomeAnnouncement(player, identifier, size);
    }

    public static void sendWelcomeAnnouncement(ServerPlayer player, SRERole role) {
        if (role == null) {
            return;
        }
        var identifier = role.getIdentifier();
        sendWelcomeAnnouncement(player, identifier);
    }

    public static void sendWelcomeAnnouncement(ServerPlayer player) {
        SREGameWorldComponent gameWorldComponent = (SREGameWorldComponent) SREGameWorldComponent.KEY
                .get(player.level());
        SRERole role = gameWorldComponent.getRole(player);
        sendWelcomeAnnouncement(player, role);
    }

    public static void changeRole(Player player, SRERole role) {
        changeRole(player, role, true);
    }

    public static void changeRole(Player player, SRERole role, boolean record) {
        changeRole(player, role, record, record);
    }

    public static void changeRole(Player player, SRERole role, boolean record, boolean addStats) {
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
        // 删除旧职业
        var oldRole = gameWorldComponent.getRole(player);
        if (oldRole != null) {
            if (record) {
                SRE.REPLAY_MANAGER.recordPlayerRoleChange(player.getUUID(), oldRole, role);
            }
            ((ModdedRoleRemoved) ModdedRoleRemoved.EVENT.invoker()).removeModdedRole(player, oldRole);
        }
        if (addStats) {
            SREPlayerStatsComponent stats = SREPlayerStatsComponent.KEY.get(player);
            stats.getOrCreateRoleStats(role.identifier()).incrementTimesPlayed();
            // 统计阵营场次
            if (role.isVigilanteTeam()) {
                stats.incrementTotalSheriffGames();
            } else if (role.canUseKiller()) {
                stats.incrementTotalKillerGames();
            } else if (role.isNeutrals()) {
                stats.incrementTotalNeutralGames();
            } else if (role.isInnocent() && !role.isVigilanteTeam()) {
                stats.incrementTotalCivilianGames();
            }
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
    public static MutableComponent getRoleName(SRERole role) {
        // 尝试获取翻译后的角色名称
        return getRoleName(role.identifier());
    }

    /**
     * 判断职业是否相等
     * 
     * @return 返回是否相等
     */
    public static boolean compareRole(SRERole role_a, SRERole role_b) {
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
    public static SRERole getRoleFromName(String roleName) {
        var roles = Noellesroles.id(roleName);
        return TMMRoles.ROLES.get(roles);
    }

    public static SRERole getRole(ResourceLocation role) {
        if (role == null)
            return null;
        return TMMRoles.ROLES.get(role);
    }

    public static MutableComponent getRoleDescription(SRERole selectedRole) {
        if (selectedRole == null)
            return null;
        return Component.translatable("info.screen.roleid." + selectedRole.getIdentifier().getPath());
    }

    public static MutableComponent getModifierName(ResourceLocation modifier) {
        if (!Language.getInstance().has("announcement.star.modifier." + modifier.toLanguageKey())
                && Language.getInstance().has("announcement.star.modifier." + modifier.getPath())) {
            return Component.translatable("announcement.star.modifier." + modifier.getPath());
        }
        final MutableComponent text = Component.translatable("announcement.star.modifier." + modifier.toLanguageKey());
        return text;
    }

    public static MutableComponent getModifierName(SREModifier modifier) {
        return modifier.getName();
    }

    public static MutableComponent getModifierNameWithColor(SREModifier modifier) {
        return modifier.getName(true);
    }

    public static MutableComponent getModifierDescription(SREModifier modifier) {
        return Component
                .translatable("info.screen.modifier." + modifier.identifier().getPath());
    }

    public static Component getRoleOrModifierName(Object role) {
        if (role instanceof SRERole r) {
            return getRoleName(r);
        } else if (role instanceof SREModifier m) {
            return m.getName(false);
        } else {
            return Component.literal("Unknown");
        }
    }

    public static MutableComponent getRoleOrModifierNameWithColor(Object role) {
        if (role == null)
            return Component.translatable("Unknown");
        if (role instanceof SRERole r) {
            return getRoleName(r).withColor(0xff000000 | r.color());
        } else if (role instanceof SREModifier m) {
            return m.getName(true);
        } else {
            return Component.translatable("Unknown");
        }
    }

    public static MutableComponent getRoleOrModifierDescription(Object role) {
        if (role instanceof SRERole r) {
            return getRoleDescription(r);
        } else if (role instanceof SREModifier m) {
            return getModifierDescription(m);
        } else {
            return Component.literal("Unknown");
        }
    }

    public static int getRoleOrModifierColor(Object role) {
        if (role instanceof SRERole r) {
            return 0xff000000 | r.color();
        } else if (role instanceof SREModifier m) {
            return 0xff000000 | m.color();
        } else {
            return java.awt.Color.WHITE.getRGB();
        }
    }

    public static MutableComponent getRoleOrModifierTypeName(Object role) {
        if (role instanceof SRERole) {
            return Component.translatable("display.type.role");
        } else if (role instanceof SREModifier) {
            return Component.translatable("display.type.modifier");
        } else {
            return Component.translatable("display.type.unknown");
        }
        //
    }

    public static ResourceLocation getRoleOrModifierIdentifier(Object role) {
        if (role instanceof SRERole r) {
            return r.identifier();
        } else if (role instanceof SREModifier m) {
            return m.identifier();
        } else {
            return null;
        }
    }

    public static MutableComponent getRoleOrModifierOrItemNameWithColor(Object selectedRole) {
        if (selectedRole instanceof Item it) {
            return it.getDescription().copy().withStyle(ChatFormatting.WHITE);
        } else {
            return getRoleOrModifierNameWithColor(selectedRole);
        }
    }

    public static ResourceLocation getRoleOrModifierOrItemIdentifier(Object selectedRole) {
        if (selectedRole instanceof Item it) {
            return BuiltInRegistries.ITEM.getKey(it);
        } else {
            return getRoleOrModifierIdentifier(selectedRole);
        }
    }

    public static Component getRoleOrModifierOrItemName(Object selectedRole) {
        if (selectedRole instanceof Item it) {
            return it.getDescription().copy();
        } else {
            return getRoleOrModifierName(selectedRole);
        }
    }

    public static MutableComponent getRoleOrModifierOrItemTypeName(Object role) {
        if (role instanceof Item) {
            return Component.translatable("display.type.item");
        } else {
            return getRoleOrModifierTypeName(role);
        }
    }

    public static MutableComponent getRoleOrModifierOrItemDescription(Object selectedRole) {
        if (selectedRole instanceof Item it) {
            String key = it.getDescriptionId() + ".desc";
            if (Language.getInstance().has(key))
                return Component.translatable(key);
            else {
                String key2 = it.getDescriptionId() + ".tooltip";
                if (Language.getInstance().has(key2))
                    return Component.translatable(key2);
                else
                    return it.getDescription().copy();
            }
        } else {
            return getRoleOrModifierDescription(selectedRole);
        }
    }

    public static Component getTeamName(int roleType) {
        Component teamName = Component.translatable("Unknown").withStyle(ChatFormatting.GRAY);
        if (roleType == 1) {
            teamName = Component.translatable("display.type.role.innocent").withStyle(ChatFormatting.GREEN);
        } else if (roleType == 2) {
            teamName = Component.translatable("display.type.role.neutral").withStyle(ChatFormatting.YELLOW);
        } else if (roleType == 3) {
            teamName = Component.translatable("display.type.role.neutral_for_killer")
                    .withStyle(ChatFormatting.LIGHT_PURPLE);
        } else if (roleType == 4) {
            teamName = Component.translatable("display.type.role.killer").withStyle(ChatFormatting.RED);
        } else if (roleType == 5) {
            teamName = Component.translatable("display.type.role.vigilante").withStyle(ChatFormatting.AQUA);
        }
        return teamName;
    }

    public static Component getTeamNameWithoutColor(int roleType) {
        Component teamName = Component.translatable("Unknown").withStyle(ChatFormatting.GRAY);
        if (roleType == 1) {
            teamName = Component.translatable("display.type.role.innocent");
        } else if (roleType == 2) {
            teamName = Component.translatable("display.type.role.neutral");
        } else if (roleType == 3) {
            teamName = Component.translatable("display.type.role.neutral_for_killer");
        } else if (roleType == 4) {
            teamName = Component.translatable("display.type.role.killer");
        } else if (roleType == 5) {
            teamName = Component.translatable("display.type.role.vigilante");
        }
        return teamName;
    }

    public static int getRoleType(SRERole role) {
        return PlayerRoleWeightManager.getRoleType(role);
    }

    public static Component getTeamNameWithoutColor(ResourceLocation roleId) {
        SRERole role = getRole(roleId);
        int roleType = PlayerRoleWeightManager.getRoleType(role);
        return getTeamNameWithoutColor(roleType);
    }

    public static Component getTeamName(ResourceLocation roleId) {
        SRERole role = getRole(roleId);
        int roleType = PlayerRoleWeightManager.getRoleType(role);
        return getTeamName(roleType);
    }

    public static Component getTeamNameWithoutColor(SRERole role) {
        int roleType = PlayerRoleWeightManager.getRoleType(role);
        return getTeamNameWithoutColor(roleType);
    }

    public static Component getTeamName(SRERole role) {
        int roleType = PlayerRoleWeightManager.getRoleType(role);
        return getTeamName(roleType);
    }

    public static boolean isPlayerTheJob(Player player, SRERole role) {
        if (player == null)
            return false;
        return SRERoleWorldComponent.KEY.get(player.level()).isRole(player, role);
    }
}
