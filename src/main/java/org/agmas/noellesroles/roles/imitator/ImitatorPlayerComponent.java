package org.agmas.noellesroles.roles.imitator;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.entity.EntityTypeTest;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.UUID;

public class ImitatorPlayerComponent implements RoleComponent, ServerTickingComponent {
    public static final ComponentKey<ImitatorPlayerComponent> KEY = ModComponents.IMITATOR;
    private final Player player;

    public static final int MAX_SLOTS = 3;
    public static final int MAX_CHARGE_TICKS = 60; // 3 seconds

    // 3 ability slots
    private final ResourceLocation[] slotRoleId = new ResourceLocation[MAX_SLOTS];
    private final int[] slotUsesRemaining = new int[MAX_SLOTS];
    private final boolean[] slotUnlimited = new boolean[MAX_SLOTS];
    private final int[] slotFillOrder = new int[MAX_SLOTS];
    public int activeSlotIndex = 0;
    public int filledSlots = 0;
    private int nextFillOrder = 0;

    // Temporary copy state (from pressing G on living player)
    public ResourceLocation tempCopiedRoleId = null;
    public int tempCopiedUsesRemaining = 0;

    // Charging state (eating corpse via right-click)
    public boolean isCharging = false;
    public int chargeTicks = 0;
    private UUID chargingCorpseUuid = null;

    // Cooldown
    public int cooldown = 0;

    public ImitatorPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {
        for (int i = 0; i < MAX_SLOTS; i++) {
            slotRoleId[i] = null;
            slotUsesRemaining[i] = 0;
            slotUnlimited[i] = false;
            slotFillOrder[i] = 0;
        }
        activeSlotIndex = 0;
        filledSlots = 0;
        nextFillOrder = 0;
        tempCopiedRoleId = null;
        tempCopiedUsesRemaining = 0;
        isCharging = false;
        chargeTicks = 0;
        chargingCorpseUuid = null;
        cooldown = 0;
        this.sync();
    }

    @Override
    public void clear() {
        this.init();
    }

    public void sync() {
        KEY.sync(this.player);
    }

    // ==================== Copy ability from living player ====================

    public void tryCopyAbility(ServerPlayer self, UUID targetUUID) {
        if (cooldown > 0) {
            self.displayClientMessage(Component.translatable("message.noellesroles.imitator.cooldown",
                    (cooldown + 19) / 20).withStyle(ChatFormatting.RED), true);
            return;
        }
        Player target = self.level().getPlayerByUUID(targetUUID);
        if (target == null || !GameUtils.isPlayerAliveAndSurvival(target))
            return;

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(self.level());
        var role = gameWorld.getRole(target);
        if (role == null)
            return;

        ResourceLocation roleId = role.identifier();
        if (!ImitatorSkillRegistry.isImitatable(roleId)) {
            self.displayClientMessage(Component.translatable("message.noellesroles.imitator.copy_fail")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }

        tempCopiedRoleId = roleId;
        tempCopiedUsesRemaining = 3;
        cooldown = 20; // 1s cooldown after copy

        String roleName = role.identifier().getPath();
        self.displayClientMessage(Component.translatable("message.noellesroles.imitator.copy_success",
                roleName, tempCopiedUsesRemaining).withStyle(ChatFormatting.GREEN), true);
        this.sync();
    }

    // ==================== Eat corpse (right-click starts charging)
    // ====================

    public void startCharging(UUID corpseEntityUuid) {
        if (isCharging)
            return;
        isCharging = true;
        chargeTicks = 0;
        chargingCorpseUuid = corpseEntityUuid;
        if (player instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.imitator.eat_start")
                    .withStyle(ChatFormatting.YELLOW), true);
        }
        this.sync();
    }

    public void cancelCharging() {
        if (!isCharging)
            return;
        isCharging = false;
        chargeTicks = 0;
        chargingCorpseUuid = null;
        this.sync();
    }

    private void completeEat() {
        isCharging = false;
        chargeTicks = 0;
        if (!(player instanceof ServerPlayer sp))
            return;
        if (chargingCorpseUuid == null)
            return;

        // Find the corpse entity
        var bodies = sp.level().getEntities(
                EntityTypeTest.forExactClass(PlayerBodyEntity.class),
                sp.getBoundingBox().inflate(10),
                body -> body.getUUID().equals(chargingCorpseUuid));

        if (bodies.isEmpty()) {
            chargingCorpseUuid = null;
            this.sync();
            return;
        }

        PlayerBodyEntity corpse = bodies.getFirst();
        UUID deadPlayerUuid = corpse.getPlayerUuid();
        if (deadPlayerUuid == null) {
            chargingCorpseUuid = null;
            this.sync();
            return;
        }

        // Get the dead player's role from game world
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(sp.level());
        var role = gameWorld.getRole(deadPlayerUuid);
        if (role == null) {
            chargingCorpseUuid = null;
            this.sync();
            return;
        }

        ResourceLocation roleId = role.identifier();
        if (!ImitatorSkillRegistry.isImitatable(roleId)) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.imitator.copy_fail")
                    .withStyle(ChatFormatting.RED), true);
            chargingCorpseUuid = null;
            this.sync();
            return;
        }

        // Find slot to put the ability in
        int slotIndex;
        if (filledSlots < MAX_SLOTS) {
            // Find empty slot
            slotIndex = -1;
            for (int i = 0; i < MAX_SLOTS; i++) {
                if (slotRoleId[i] == null) {
                    slotIndex = i;
                    break;
                }
            }
            if (slotIndex == -1)
                slotIndex = 0; // fallback
            filledSlots++;
        } else {
            // Override oldest slot
            slotIndex = getOldestSlotIndex();
            sp.displayClientMessage(Component.translatable("message.noellesroles.imitator.slot_replaced")
                    .withStyle(ChatFormatting.YELLOW), true);
        }

        slotRoleId[slotIndex] = roleId;
        slotUsesRemaining[slotIndex] = 0;
        slotUnlimited[slotIndex] = true;
        slotFillOrder[slotIndex] = nextFillOrder++;
        activeSlotIndex = slotIndex;

        // Remove corpse
        corpse.discard();
        chargingCorpseUuid = null;
        cooldown = 40; // 2s cooldown after eat

        String roleName = role.identifier().getPath();
        sp.displayClientMessage(Component.translatable("message.noellesroles.imitator.eat_complete",
                roleName).withStyle(ChatFormatting.GREEN), true);
        this.sync();
    }

    // ==================== Slot management ====================

    public void switchSlot() {
        if (filledSlots == 0) {
            if (player instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.translatable("message.noellesroles.imitator.no_ability")
                        .withStyle(ChatFormatting.RED), true);
            }
            return;
        }

        // Cycle to next filled slot
        int startIndex = activeSlotIndex;
        for (int i = 1; i <= MAX_SLOTS; i++) {
            int nextIndex = (startIndex + i) % MAX_SLOTS;
            if (slotRoleId[nextIndex] != null) {
                activeSlotIndex = nextIndex;
                break;
            }
        }

        if (player instanceof ServerPlayer sp) {
            String name = slotRoleId[activeSlotIndex] != null ? slotRoleId[activeSlotIndex].getPath() : "empty";
            sp.displayClientMessage(Component.translatable("message.noellesroles.imitator.slot_switch",
                    activeSlotIndex + 1, name).withStyle(ChatFormatting.AQUA), true);
        }
        this.sync();
    }

    // ==================== Use ability ====================

    public void useActiveAbility(ServerPlayer self, @Nullable UUID target) {
        if (cooldown > 0) {
            self.displayClientMessage(Component.translatable("message.noellesroles.imitator.cooldown",
                    (cooldown + 19) / 20).withStyle(ChatFormatting.RED), true);
            return;
        }

        // Priority: temp copy > active slot
        if (tempCopiedRoleId != null) {
            if (ImitatorSkillRegistry.execute(tempCopiedRoleId, self, target)) {
                tempCopiedUsesRemaining--;
                cooldown = 20; // 1s cooldown
                if (tempCopiedUsesRemaining <= 0) {
                    tempCopiedRoleId = null;
                    tempCopiedUsesRemaining = 0;
                } else {
                    self.displayClientMessage(Component.translatable("message.noellesroles.imitator.uses_left",
                            tempCopiedUsesRemaining).withStyle(ChatFormatting.GRAY), true);
                }
                this.sync();
            }
            return;
        }

        // Use active slot ability
        if (slotRoleId[activeSlotIndex] == null) {
            self.displayClientMessage(Component.translatable("message.noellesroles.imitator.no_ability")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }

        ResourceLocation roleId = slotRoleId[activeSlotIndex];
        if (ImitatorSkillRegistry.execute(roleId, self, target)) {
            if (!slotUnlimited[activeSlotIndex]) {
                slotUsesRemaining[activeSlotIndex]--;
                if (slotUsesRemaining[activeSlotIndex] <= 0) {
                    clearSlot(activeSlotIndex);
                }
            }
            cooldown = 20; // 1s cooldown
            this.sync();
        }
    }

    private void clearSlot(int index) {
        slotRoleId[index] = null;
        slotUsesRemaining[index] = 0;
        slotUnlimited[index] = false;
        slotFillOrder[index] = 0;
        filledSlots = 0;
        for (int i = 0; i < MAX_SLOTS; i++) {
            if (slotRoleId[i] != null)
                filledSlots++;
        }
    }

    private int getOldestSlotIndex() {
        int oldest = 0;
        int minOrder = Integer.MAX_VALUE;
        for (int i = 0; i < MAX_SLOTS; i++) {
            if (slotRoleId[i] != null && slotFillOrder[i] < minOrder) {
                minOrder = slotFillOrder[i];
                oldest = i;
            }
        }
        return oldest;
    }

    public boolean hasAnyAbility() {
        if (tempCopiedRoleId != null)
            return true;
        for (int i = 0; i < MAX_SLOTS; i++) {
            if (slotRoleId[i] != null)
                return true;
        }
        return false;
    }

    public ResourceLocation getCurrentAbilityRoleId() {
        if (tempCopiedRoleId != null)
            return tempCopiedRoleId;
        if (slotRoleId[activeSlotIndex] != null)
            return slotRoleId[activeSlotIndex];
        return null;
    }

    public ResourceLocation getSlotRoleId(int index) {
        if (index < 0 || index >= MAX_SLOTS)
            return null;
        return slotRoleId[index];
    }

    public boolean isSlotUnlimited(int index) {
        if (index < 0 || index >= MAX_SLOTS)
            return false;
        return slotUnlimited[index];
    }

    public int getSlotUsesRemaining(int index) {
        if (index < 0 || index >= MAX_SLOTS)
            return 0;
        return slotUsesRemaining[index];
    }

    // ==================== Tick ====================

    @Override
    public void serverTick() {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorld.isRole(player, ModRoles.IMITATOR))
            return;

        if (cooldown > 0)
            cooldown--;

        if (isCharging) {
            chargeTicks++;
            if (chargeTicks >= MAX_CHARGE_TICKS) {
                completeEat();
            }
        }
    }

    // ==================== NBT Sync ====================

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        for (int i = 0; i < MAX_SLOTS; i++) {
            if (slotRoleId[i] != null) {
                tag.putString("slot" + i + "Role", slotRoleId[i].toString());
                tag.putInt("slot" + i + "Uses", slotUsesRemaining[i]);
                tag.putBoolean("slot" + i + "Unlimited", slotUnlimited[i]);
                tag.putInt("slot" + i + "Order", slotFillOrder[i]);
            }
        }
        tag.putInt("activeSlot", activeSlotIndex);
        tag.putInt("filledSlots", filledSlots);
        tag.putInt("nextFillOrder", nextFillOrder);

        if (tempCopiedRoleId != null) {
            tag.putString("tempRole", tempCopiedRoleId.toString());
            tag.putInt("tempUses", tempCopiedUsesRemaining);
        }

        tag.putBoolean("isCharging", isCharging);
        tag.putInt("chargeTicks", chargeTicks);
        tag.putInt("cooldown", cooldown);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        for (int i = 0; i < MAX_SLOTS; i++) {
            String key = "slot" + i + "Role";
            if (tag.contains(key)) {
                slotRoleId[i] = ResourceLocation.parse(tag.getString(key));
                slotUsesRemaining[i] = tag.getInt("slot" + i + "Uses");
                slotUnlimited[i] = tag.getBoolean("slot" + i + "Unlimited");
                slotFillOrder[i] = tag.getInt("slot" + i + "Order");
            } else {
                slotRoleId[i] = null;
                slotUsesRemaining[i] = 0;
                slotUnlimited[i] = false;
                slotFillOrder[i] = 0;
            }
        }
        activeSlotIndex = tag.contains("activeSlot") ? tag.getInt("activeSlot") : 0;
        filledSlots = tag.contains("filledSlots") ? tag.getInt("filledSlots") : 0;
        nextFillOrder = tag.contains("nextFillOrder") ? tag.getInt("nextFillOrder") : 0;

        if (tag.contains("tempRole")) {
            tempCopiedRoleId = ResourceLocation.parse(tag.getString("tempRole"));
            tempCopiedUsesRemaining = tag.getInt("tempUses");
        } else {
            tempCopiedRoleId = null;
            tempCopiedUsesRemaining = 0;
        }

        isCharging = tag.contains("isCharging") && tag.getBoolean("isCharging");
        chargeTicks = tag.contains("chargeTicks") ? tag.getInt("chargeTicks") : 0;
        cooldown = tag.contains("cooldown") ? tag.getInt("cooldown") : 0;
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
