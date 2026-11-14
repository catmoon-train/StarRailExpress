package org.agmas.noellesroles.game.roles.killer.ma_chen_xu;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.api.time.TimeRewindEvents;
import org.agmas.noellesroles.content.item.GhostArtItem;
import org.agmas.noellesroles.content.item.GhostWardRelicItem;
import org.agmas.noellesroles.init.ModEffects;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 法器实物不回溯：快照排除、恢复前保全当前实物、恢复后原样归还。 */
public final class SackGhostRewind {
    private static final Map<UUID, List<ItemStack>> PRESERVED = new HashMap<>();
    private SackGhostRewind() {}

    public static void register() {
        TimeRewindEvents.CAPTURE.register((player, state) -> {
            stripRelics(state);
            SackGhostActions.filterRewindSnapshot(player, state);
            for (var owner : player.server.getPlayerList().getPlayers()) {
                var data = SackGhostItems.data(owner);
                if (data != null) data.filterRewindSnapshot(player, state);
            }
            removeSnapshotEffect(state, ModEffects.OTHERWORLD_AURA);
            removeSnapshotEffect(state, ModEffects.GHOST_CONDEMNED);
            removeSnapshotEffect(state, ModEffects.GHOST_WARD);
        });
        TimeRewindEvents.BEFORE_RESTORE.register((player, state) -> {
            stripRelics(state);
            var kept = new ArrayList<ItemStack>();
            PRESERVED.put(player.getUUID(), kept);
            for (int i = 0; i <= player.getInventory().getContainerSize(); i++) {
                boolean cursor = i == player.getInventory().getContainerSize();
                ItemStack stack = cursor ? player.containerMenu.getCarried() : player.getInventory().getItem(i);
                if (!(stack.getItem() instanceof GhostWardRelicItem)) continue;
                kept.add(stack);
                if (cursor) player.containerMenu.setCarried(ItemStack.EMPTY);
                else player.getInventory().setItem(i, ItemStack.EMPTY);
            }
            var data = SackGhostItems.data(player);
            if (data != null) data.cancelForRewind();
            for (var owner : player.server.getPlayerList().getPlayers()) {
                var other = SackGhostItems.data(owner);
                if (other != null) other.cleanse(player);
            }
        });
        TimeRewindEvents.AFTER_RESTORE.register((player, state) -> {
            List<ItemStack> kept = PRESERVED.remove(player.getUUID());
            if (kept != null) {
                for (ItemStack stack : kept) {
                    if (!player.getInventory().add(stack)) player.drop(stack, false);
                }
                player.getInventory().setChanged();
                player.containerMenu.broadcastChanges();
            }
        });
        TimeRewindEvents.ALLOW_AREA_ITEM.register(item -> !(item.getItem().getItem() instanceof GhostWardRelicItem)
                && !(item.getItem().getItem() instanceof GhostArtItem));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> PRESERVED.clear());
    }

    private static void stripRelics(CompoundTag state) {
        state.getList("Inventory", Tag.TAG_COMPOUND).removeIf(tag -> tag instanceof CompoundTag item
                && "noellesroles:ghost_ward_relic".equals(item.getString("id")));
    }

    /** 兼容原版新旧药水NBT键，保留所有不属于本机制的效果。 */
    public static void removeSnapshotEffect(CompoundTag state, Holder<MobEffect> effect) {
        for (String key : List.of("active_effects", "ActiveEffects")) {
            state.getList(key, Tag.TAG_COMPOUND).removeIf(tag -> {
                MobEffectInstance instance = tag instanceof CompoundTag data ? MobEffectInstance.load(data) : null;
                return instance != null && instance.getEffect().equals(effect);
            });
        }
    }
}
