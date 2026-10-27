package org.agmas.noellesroles.role_data.killer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.agmas.noellesroles.game.command_block.Entity303Role;
import org.agmas.noellesroles.game.command_block.item.GazeRays;

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.morph.MorphApi;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;

/** 实体303：记住偷过的脸，临时借用物品栏，并在窃皮期间绑定死亡。 */
public class Entity303RoleData extends SimpleRoleData {
    public static final int MORPH_TICKS = 18 * 20;
    public static final int RECOGNIZE_LOCK_TICKS = 12 * 20;
    public static final int KILL_LOCK_TICKS = 15 * 20;

    private final Set<UUID> known = new HashSet<>();
    private UUID copied;
    private boolean wearing;
    private long morphLockUntil;
    private List<ItemStack> originalInventory;
    private int originalSelectedSlot;
    private boolean resolvingDeath;

    public Entity303RoleData(RoleDataContext context) {
        super(context);
    }

    public boolean knows(UUID id) {
        return id != null && known.contains(id);
    }

    public boolean morphLocked(long now) {
        return now < morphLockUntil;
    }

    public boolean isWearing(UUID id) {
        return wearing && copied != null && copied.equals(id);
    }

    public void remember(ServerPlayer self, ServerPlayer target) {
        // 先收回鼠标上的原有物品，再逐槽深拷贝，不能与目标共享可变的 ItemStack。
        self.stopUsingItem();
        self.closeContainer();
        originalInventory = new ArrayList<>();
        originalSelectedSlot = self.getInventory().selected;
        for (int slot = 0; slot < self.getInventory().getContainerSize(); slot++) {
            originalInventory.add(self.getInventory().getItem(slot).copy());
            self.getInventory().setItem(slot, target.getInventory().getItem(slot).copy());
        }
        self.getInventory().setChanged();
        self.inventoryMenu.broadcastFullState();
        known.add(target.getUUID());
        copied = target.getUUID();
        wearing = true;
        sync();
    }

    public void onKill(ServerPlayer self, long now) {
        morphLockUntil = now + KILL_LOCK_TICKS;
        self.addEffect(new MobEffectInstance(MobEffects.GLOWING, 8 * 20, 0, false, false, true));
        sync();
    }

    /** 只在已经通过死亡拦截的职业死亡回调中联动，互相窃皮时不重复结算。 */
    public void onDeath(ServerPlayer self) {
        if (resolvingDeath) {
            return;
        }
        UUID linked = isWearing(MorphApi.getAppearance(self).targetPlayer())
                && MorphApi.getRemainingTicks(self) != 0 ? copied : null;
        resolvingDeath = true;
        try {
            MorphApi.clearMorph(self);
            endDisguise(self);
            ServerPlayer original = linked == null ? null
                    : self.serverLevel().getServer().getPlayerList().getPlayer(linked);
            if (original != null && original != self && GameUtils.isPlayerAliveAndSurvival(original)) {
                Entity303RoleData other = RoleData.getNullable(Entity303RoleData.class, original);
                if (other == null || !other.resolvingDeath) {
                    GameUtils.killPlayer(original, true, self, Entity303Role.DEATH_PEEL, true);
                }
            }
        } finally {
            resolvingDeath = false;
        }
    }

    /** 到期、识破、外部解除和职业清理共用的物品栏恢复入口。 */
    public void endDisguise(ServerPlayer self) {
        if (!wearing && originalInventory == null) {
            return;
        }
        wearing = false;
        copied = null;
        if (originalInventory != null) {
            self.stopUsingItem();
            // 窃皮期间拿到鼠标上的临时物品也随伪装一起丢弃。
            self.containerMenu.setCarried(ItemStack.EMPTY);
            self.closeContainer();
            for (int slot = 0; slot < self.getInventory().getContainerSize(); slot++) {
                self.getInventory().setItem(slot, slot < originalInventory.size()
                        ? originalInventory.get(slot).copy() : ItemStack.EMPTY);
            }
            self.getInventory().selected = originalSelectedSlot;
            originalInventory = null;
            self.getInventory().setChanged();
            self.connection.send(new ClientboundSetCarriedItemPacket(originalSelectedSlot));
            self.inventoryMenu.broadcastFullState();
        }
        sync();
    }

    @Override
    public void clear() {
        if (player instanceof ServerPlayer sp) {
            MorphApi.clearMorph(sp);
            endDisguise(sp);
        }
        wearing = false;
        copied = null;
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer self)) {
            return;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(self)) {
            MorphApi.clearMorph(self);
            endDisguise(self);
            return;
        }
        long now = self.serverLevel().getGameTime();
        if (wearing && !isWearing(MorphApi.getAppearance(self).targetPlayer())) {
            endDisguise(self);
            return;
        }
        if (!wearing || copied == null) {
            return;
        }
        ServerPlayer original = self.serverLevel().getServer().getPlayerList().getPlayer(copied);
        if (original != null && GameUtils.isPlayerAliveAndSurvival(original)
                && self.distanceToSqr(original) <= 12.0D * 12.0D
                && GazeRays.lookingAt(original, self, 0.55D)) {
            shatter(self, now);
        }
    }

    private void shatter(ServerPlayer self, long now) {
        MorphApi.clearMorph(self);
        endDisguise(self);
        morphLockUntil = now + RECOGNIZE_LOCK_TICKS;
        self.addEffect(new MobEffectInstance(MobEffects.GLOWING, 8 * 20, 0, false, false, true));
        self.displayClientMessage(Component.translatable("message.noellesroles.entity_303.recognized")
                .withStyle(ChatFormatting.RED), true);
        sync();
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        ListTag list = new ListTag();
        for (UUID id : known) {
            list.add(StringTag.valueOf(id.toString()));
        }
        tag.put("known", list);
        tag.putBoolean("wearing", wearing);
        tag.putLong("lock", morphLockUntil);
        if (copied != null) {
            tag.putUUID("copied", copied);
        }
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        known.clear();
        if (tag.contains("known", Tag.TAG_LIST)) {
            ListTag list = tag.getList("known", Tag.TAG_STRING);
            for (Tag entry : list) {
                try {
                    known.add(UUID.fromString(entry.getAsString()));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        wearing = tag.getBoolean("wearing");
        morphLockUntil = tag.getLong("lock");
        copied = tag.contains("copied") ? tag.getUUID("copied") : null;
    }
}
