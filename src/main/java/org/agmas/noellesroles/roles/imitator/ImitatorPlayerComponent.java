package org.agmas.noellesroles.roles.imitator;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.entity.EntityTypeTest;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.UUID;

public class ImitatorPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<ImitatorPlayerComponent> KEY = ModComponents.IMITATOR;
    private final Player player;

    public static final int MAX_SLOTS = 3;
    public static final int MAX_CHARGE_TICKS = 60; // 3秒吃尸体
    public static final int COPY_ACTION_COOLDOWN = 60 * 20; // 复制动作冷却60秒

    // ==================== 3个永久槽位 ====================
    private final ResourceLocation[] slotRoleId = new ResourceLocation[MAX_SLOTS];
    private final int[] slotFillOrder = new int[MAX_SLOTS];
    private final int[] slotCooldown = new int[MAX_SLOTS]; // 每槽位独立冷却
    public int activeSlotIndex = 0;
    public int filledSlots = 0;
    private int nextFillOrder = 0;

    // ==================== 临时复制 ====================
    public ResourceLocation tempCopiedRoleId = null;
    public int tempCopiedUsesRemaining = 0;
    public int tempSkillCooldown = 0; // 临时技能冷却

    // ==================== 复制动作冷却 ====================
    public int copyActionCooldown = 0; // 复制活人的冷却

    // ==================== 吃尸体充能 ====================
    public boolean isCharging = false;
    public int chargeTicks = 0;
    private UUID chargingCorpseUuid = null;

    // ==================== 召回者状态 ====================
    public boolean imitRecallerPlaced = false;
    public double imitRecallerX = 0, imitRecallerY = 0, imitRecallerZ = 0;

    // ==================== 拳击手无敌状态 ====================
    public int imitBoxerInvulnTicks = 0;

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
            slotFillOrder[i] = 0;
            slotCooldown[i] = 0;
        }
        activeSlotIndex = 0;
        filledSlots = 0;
        nextFillOrder = 0;
        tempCopiedRoleId = null;
        tempCopiedUsesRemaining = 0;
        tempSkillCooldown = 0;
        copyActionCooldown = 0;
        isCharging = false;
        chargeTicks = 0;
        chargingCorpseUuid = null;
        imitRecallerPlaced = false;
        imitRecallerX = imitRecallerY = imitRecallerZ = 0;
        imitBoxerInvulnTicks = 0;
        this.sync();
    }

    @Override
    public void clear() {
        this.init();
    }

    public void sync() {
        KEY.sync(this.player);
    }

    // ==================== 复制活人能力 ====================

    public void tryCopyAbility(ServerPlayer self, UUID targetUUID) {
        if (copyActionCooldown > 0) {
            self.displayClientMessage(Component.translatable("message.noellesroles.imitator.cooldown",
                    (copyActionCooldown + 19) / 20).withStyle(ChatFormatting.RED), true);
            return;
        }
        Player target = self.level().getPlayerByUUID(targetUUID);
        if (target == null || !GameUtils.isPlayerAliveAndSurvival(target))
            return;

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(self.level());
        SRERole role = gameWorld.getRole(target);
        if (role == null)
            return;

        // 不能复制杀手和中立
        if (!role.isInnocent()) {
            self.displayClientMessage(Component.translatable("message.noellesroles.imitator.copy_fail_not_innocent")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }

        ResourceLocation roleId = role.identifier();
        // 只能复制指定的8个好人角色
        if (!ImitatorSkillRegistry.isImitatable(roleId)) {
            self.displayClientMessage(Component.translatable("message.noellesroles.imitator.copy_fail")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }

        tempCopiedRoleId = roleId;
        tempCopiedUsesRemaining = 3;
        tempSkillCooldown = 0;
        copyActionCooldown = COPY_ACTION_COOLDOWN; // 60秒
        // 复制新能力时重置召回者状态
        imitRecallerPlaced = false;

        String roleName = role.identifier().getPath();
        self.displayClientMessage(Component.translatable("message.noellesroles.imitator.copy_success",
                roleName, tempCopiedUsesRemaining).withStyle(ChatFormatting.GREEN), true);
        this.sync();
    }

    // ==================== 吃尸体 ====================

    public void startCharging(UUID corpseEntityUuid) {
        if (isCharging) return;
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
        if (!isCharging) return;
        isCharging = false;
        chargeTicks = 0;
        chargingCorpseUuid = null;
        this.sync();
    }

    private void completeEat() {
        isCharging = false;
        chargeTicks = 0;
        if (!(player instanceof ServerPlayer sp)) return;
        if (chargingCorpseUuid == null) return;

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

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(sp.level());
        var role = gameWorld.getRole(deadPlayerUuid);
        if (role == null) {
            chargingCorpseUuid = null;
            this.sync();
            return;
        }

        // 不能吃杀手和中立的能力
        if (!role.isInnocent()) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.imitator.copy_fail_not_innocent")
                    .withStyle(ChatFormatting.RED), true);
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

        // 找槽位
        int slotIndex;
        if (filledSlots < MAX_SLOTS) {
            slotIndex = -1;
            for (int i = 0; i < MAX_SLOTS; i++) {
                if (slotRoleId[i] == null) {
                    slotIndex = i;
                    break;
                }
            }
            if (slotIndex == -1) slotIndex = 0;
            filledSlots++;
        } else {
            slotIndex = getOldestSlotIndex();
            sp.displayClientMessage(Component.translatable("message.noellesroles.imitator.slot_replaced")
                    .withStyle(ChatFormatting.YELLOW), true);
        }

        slotRoleId[slotIndex] = roleId;
        slotCooldown[slotIndex] = 0;
        slotFillOrder[slotIndex] = nextFillOrder++;
        activeSlotIndex = slotIndex;

        corpse.discard();
        chargingCorpseUuid = null;
        copyActionCooldown = 40; // 2秒吃完后小冷却

        String roleName = role.identifier().getPath();
        sp.displayClientMessage(Component.translatable("message.noellesroles.imitator.eat_complete",
                roleName).withStyle(ChatFormatting.GREEN), true);
        this.sync();
    }

    // ==================== 槽位管理 ====================

    public void switchSlot() {
        if (filledSlots == 0) {
            if (player instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.translatable("message.noellesroles.imitator.no_ability")
                        .withStyle(ChatFormatting.RED), true);
            }
            return;
        }
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
            int cd = slotCooldown[activeSlotIndex];
            String cdStr = cd > 0 ? " (" + ((cd + 19) / 20) + "s)" : "";
            sp.displayClientMessage(Component.translatable("message.noellesroles.imitator.slot_switch",
                    activeSlotIndex + 1, name + cdStr).withStyle(ChatFormatting.AQUA), true);
        }
        this.sync();
    }

    // ==================== 使用能力(非消息类) ====================

    public void useActiveAbility(ServerPlayer self, @Nullable UUID target) {
        // 优先级: 临时 > 当前槽位
        ResourceLocation roleId;
        boolean isTemp;

        if (tempCopiedRoleId != null) {
            roleId = tempCopiedRoleId;
            isTemp = true;
        } else if (slotRoleId[activeSlotIndex] != null) {
            roleId = slotRoleId[activeSlotIndex];
            isTemp = false;
        } else {
            self.displayClientMessage(Component.translatable("message.noellesroles.imitator.no_ability")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }

        // 消息技能提示用屏幕
        if (ImitatorSkillRegistry.isMessageSkill(roleId)) {
            self.displayClientMessage(Component.translatable("message.noellesroles.imitator.use_screen")
                    .withStyle(ChatFormatting.YELLOW), true);
            return;
        }

        // 检查对应冷却
        int currentCd = isTemp ? tempSkillCooldown : slotCooldown[activeSlotIndex];
        if (currentCd > 0) {
            self.displayClientMessage(Component.translatable("message.noellesroles.imitator.cooldown",
                    (currentCd + 19) / 20).withStyle(ChatFormatting.RED), true);
            return;
        }

        boolean isPermanent = !isTemp;
        ImitatorSkillRegistry.SkillResult result = ImitatorSkillRegistry.execute(roleId, self, target, this, isPermanent);

        switch (result) {
            case SUCCESS -> {
                applySkillCooldownAndConsume(roleId, isPermanent);
            }
            case HANDLED -> {
                // 技能内部已处理
            }
            case FAIL -> {
                // 执行失败，不做任何事
            }
        }

        this.sync();
    }

    // ==================== 使用消息技能(由服务端包处理器调用) ====================

    public boolean useMessageAbility(ServerPlayer self, String message) {
        ResourceLocation roleId;
        boolean isTemp;

        if (tempCopiedRoleId != null) {
            roleId = tempCopiedRoleId;
            isTemp = true;
        } else if (slotRoleId[activeSlotIndex] != null) {
            roleId = slotRoleId[activeSlotIndex];
            isTemp = false;
        } else {
            return false;
        }

        if (!ImitatorSkillRegistry.isMessageSkill(roleId)) return false;

        int currentCd = isTemp ? tempSkillCooldown : slotCooldown[activeSlotIndex];
        if (currentCd > 0) {
            self.displayClientMessage(Component.translatable("message.noellesroles.imitator.cooldown",
                    (currentCd + 19) / 20).withStyle(ChatFormatting.RED), true);
            return false;
        }

        boolean isPermanent = !isTemp;
        ImitatorSkillRegistry.SkillResult result = ImitatorSkillRegistry.executeMessage(roleId, self, message, this, isPermanent);

        if (result == ImitatorSkillRegistry.SkillResult.SUCCESS) {
            applySkillCooldownAndConsume(roleId, isPermanent);
            this.sync();
            return true;
        }
        return false;
    }

    // ==================== 冷却/次数管理 ====================

    /**
     * 设置当前技能冷却 + 扣临时次数(如果非永久)
     */
    public void applySkillCooldownAndConsume(ResourceLocation roleId, boolean isPermanent) {
        int cd = ImitatorSkillRegistry.getCooldown(roleId);
        if (tempCopiedRoleId != null && tempCopiedRoleId.equals(roleId)) {
            tempSkillCooldown = cd;
            if (!isPermanent) {
                tempCopiedUsesRemaining--;
                if (tempCopiedUsesRemaining <= 0) {
                    tempCopiedRoleId = null;
                    tempCopiedUsesRemaining = 0;
                    tempSkillCooldown = 0;
                    imitRecallerPlaced = false;
                }
            }
        } else {
            slotCooldown[activeSlotIndex] = cd;
        }
    }

    /**
     * 获取当前活跃技能的冷却
     */
    public int getCurrentSkillCooldown() {
        if (tempCopiedRoleId != null) return tempSkillCooldown;
        if (slotRoleId[activeSlotIndex] != null) return slotCooldown[activeSlotIndex];
        return 0;
    }

    // ==================== 查询方法 ====================

    public boolean hasAnyAbility() {
        if (tempCopiedRoleId != null) return true;
        for (int i = 0; i < MAX_SLOTS; i++) {
            if (slotRoleId[i] != null) return true;
        }
        return false;
    }

    public ResourceLocation getCurrentAbilityRoleId() {
        if (tempCopiedRoleId != null) return tempCopiedRoleId;
        if (slotRoleId[activeSlotIndex] != null) return slotRoleId[activeSlotIndex];
        return null;
    }

    public ResourceLocation getSlotRoleId(int index) {
        if (index < 0 || index >= MAX_SLOTS) return null;
        return slotRoleId[index];
    }

    public int getSlotCooldown(int index) {
        if (index < 0 || index >= MAX_SLOTS) return 0;
        return slotCooldown[index];
    }

    public boolean isSlotUnlimited(int index) {
        if (index < 0 || index >= MAX_SLOTS) return false;
        return slotRoleId[index] != null; // 槽位都是永久的
    }

    public int getSlotUsesRemaining(int index) {
        return 0; // 永久槽位不计次数
    }

    public boolean isImitatorInvulnerable() {
        return imitBoxerInvulnTicks > 0;
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

    // ==================== Tick ====================

    @Override
    public void serverTick() {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorld.isRole(player, ModRoles.IMITATOR)) return;

        boolean needSync = false;

        // 复制动作冷却
        if (copyActionCooldown > 0) {
            copyActionCooldown--;
            if (copyActionCooldown == 0) needSync = true;
        }

        // 临时技能冷却
        if (tempSkillCooldown > 0) {
            tempSkillCooldown--;
            if (tempSkillCooldown == 0) needSync = true;
        }

        // 槽位冷却
        for (int i = 0; i < MAX_SLOTS; i++) {
            if (slotCooldown[i] > 0) {
                slotCooldown[i]--;
                if (slotCooldown[i] == 0) needSync = true;
            }
        }

        // 拳击手无敌
        if (imitBoxerInvulnTicks > 0) {
            imitBoxerInvulnTicks--;
            if (imitBoxerInvulnTicks == 0) {
                if (player instanceof ServerPlayer sp) {
                    sp.displayClientMessage(Component.translatable("message.noellesroles.imitator.boxer_ended")
                            .withStyle(ChatFormatting.GRAY), true);
                }
                needSync = true;
            }
        }

        // 吃尸体充能
        if (isCharging) {
            chargeTicks++;
            if (chargeTicks >= MAX_CHARGE_TICKS) {
                completeEat();
            }
        }

        if (needSync) sync();
    }

    @Override
    public void clientTick() {
        if (copyActionCooldown > 1) copyActionCooldown--;
        if (tempSkillCooldown > 1) tempSkillCooldown--;
        for (int i = 0; i < MAX_SLOTS; i++) {
            if (slotCooldown[i] > 1) slotCooldown[i]--;
        }
        if (imitBoxerInvulnTicks > 0) imitBoxerInvulnTicks--;
        if (isCharging && chargeTicks < MAX_CHARGE_TICKS) chargeTicks++;
    }

    // ==================== NBT Sync ====================

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        for (int i = 0; i < MAX_SLOTS; i++) {
            if (slotRoleId[i] != null) {
                tag.putString("slot" + i + "Role", slotRoleId[i].toString());
                tag.putInt("slot" + i + "Order", slotFillOrder[i]);
                tag.putInt("slot" + i + "Cd", slotCooldown[i]);
            }
        }
        tag.putInt("activeSlot", activeSlotIndex);
        tag.putInt("filledSlots", filledSlots);
        tag.putInt("nextFillOrder", nextFillOrder);

        if (tempCopiedRoleId != null) {
            tag.putString("tempRole", tempCopiedRoleId.toString());
            tag.putInt("tempUses", tempCopiedUsesRemaining);
            tag.putInt("tempCd", tempSkillCooldown);
        }

        tag.putInt("copyActionCd", copyActionCooldown);
        tag.putBoolean("isCharging", isCharging);
        tag.putInt("chargeTicks", chargeTicks);

        // 召回者状态
        tag.putBoolean("imitRecPlaced", imitRecallerPlaced);
        tag.putDouble("imitRecX", imitRecallerX);
        tag.putDouble("imitRecY", imitRecallerY);
        tag.putDouble("imitRecZ", imitRecallerZ);

        // 拳击手状态
        tag.putInt("imitBoxerInvuln", imitBoxerInvulnTicks);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        for (int i = 0; i < MAX_SLOTS; i++) {
            String key = "slot" + i + "Role";
            if (tag.contains(key)) {
                slotRoleId[i] = ResourceLocation.parse(tag.getString(key));
                slotFillOrder[i] = tag.getInt("slot" + i + "Order");
                slotCooldown[i] = tag.getInt("slot" + i + "Cd");
            } else {
                slotRoleId[i] = null;
                slotFillOrder[i] = 0;
                slotCooldown[i] = 0;
            }
        }
        activeSlotIndex = tag.contains("activeSlot") ? tag.getInt("activeSlot") : 0;
        filledSlots = tag.contains("filledSlots") ? tag.getInt("filledSlots") : 0;
        nextFillOrder = tag.contains("nextFillOrder") ? tag.getInt("nextFillOrder") : 0;

        if (tag.contains("tempRole")) {
            tempCopiedRoleId = ResourceLocation.parse(tag.getString("tempRole"));
            tempCopiedUsesRemaining = tag.getInt("tempUses");
            tempSkillCooldown = tag.getInt("tempCd");
        } else {
            tempCopiedRoleId = null;
            tempCopiedUsesRemaining = 0;
            tempSkillCooldown = 0;
        }

        copyActionCooldown = tag.contains("copyActionCd") ? tag.getInt("copyActionCd") : 0;
        isCharging = tag.contains("isCharging") && tag.getBoolean("isCharging");
        chargeTicks = tag.contains("chargeTicks") ? tag.getInt("chargeTicks") : 0;

        imitRecallerPlaced = tag.contains("imitRecPlaced") && tag.getBoolean("imitRecPlaced");
        imitRecallerX = tag.contains("imitRecX") ? tag.getDouble("imitRecX") : 0;
        imitRecallerY = tag.contains("imitRecY") ? tag.getDouble("imitRecY") : 0;
        imitRecallerZ = tag.contains("imitRecZ") ? tag.getDouble("imitRecZ") : 0;

        imitBoxerInvulnTicks = tag.contains("imitBoxerInvuln") ? tag.getInt("imitBoxerInvuln") : 0;
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
