
    package io.wifi.starrailexpress.cca;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.content.gui.PlayerBodyEntityContainer;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.UUID;

public class PlayerBodyEntityComponent implements RoleComponent, ServerTickingComponent {
    public static final ComponentKey<PlayerBodyEntityComponent> KEY = ComponentRegistry.getOrCreate(
            SRE.id("body_entity"),
            PlayerBodyEntityComponent.class);

    public ResourceLocation playerRole = TMMRoles.CIVILIAN.identifier();
    public boolean vultured = false;
    public boolean isFakeBody = false; // 葬仪伪造的尸体标记
    public PlayerBodyEntity playerBodyEntity;

    private UUID killer;
    private UUID conspiratorEvidence;
    private String deathReason = "";
    // 容器大小54（6行），支持DAY_NIGHT_FIGHT模式，仅允许0-53槽放置物品
    private final PlayerBodyEntityContainer corpseInventory = new PlayerBodyEntityContainer(54);

    public PlayerBodyEntityComponent(PlayerBodyEntity playerBodyEntity) {
        this.playerBodyEntity = playerBodyEntity;
    }

    // ---------- 物品容器访问 ----------
    public PlayerBodyEntityContainer getCorpseInventory() {
        return corpseInventory;
    }

    // ---------- Killer / DeathReason 带同步控制的 setter ----------
    public UUID getKillerUuid() {
        return killer;
    }

    public void setKillerUuid(UUID uuid) {
        setKillerUuid(uuid, true);
    }

    public void setKillerUuid(UUID uuid, boolean sync) {
        this.killer = uuid;
        if (sync && !playerBodyEntity.level().isClientSide) {
            sync();
        }
    }

    public UUID getConspiratorEvidenceUuid() {
        return conspiratorEvidence;
    }

    public void setConspiratorEvidenceUuid(UUID uuid) {
        setConspiratorEvidenceUuid(uuid, true);
    }

    public void setConspiratorEvidenceUuid(UUID uuid, boolean sync) {
        this.conspiratorEvidence = uuid;
        if (sync && !playerBodyEntity.level().isClientSide) {
            sync();
        }
    }

    public String getDeathReason() {
        return deathReason;
    }

    public void setDeathReason(String reason) {
        setDeathReason(reason, true);
    }

    public void setDeathReason(String reason, boolean sync) {
        this.deathReason = reason != null ? reason : "";
        if (sync && !playerBodyEntity.level().isClientSide) {
            sync();
        }
    }

    // ---------- 生命周期 & 同步 ----------
    @Override
    public void init() {
        this.sync();
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer sp) {
        return true;
    }

    @Override
    public void clear() {
        this.playerRole = TMMRoles.CIVILIAN.identifier();
        this.vultured = false;
        this.isFakeBody = false;
        this.killer = null;
        this.conspiratorEvidence = null;
        this.deathReason = "";
        for (int i = 0; i < 54; i++) {
            corpseInventory.setItem(i, ItemStack.EMPTY);
        }
        this.sync();
    }

    @Override
    public Player getPlayer() {
        return null;
    }

    public void sync() {
        KEY.sync(this.playerBodyEntity);
    }

    // ---------- 持久化 ----------
    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    public void writeToNbtFromBody(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putString("playerRole", playerRole.toString());
        tag.putBoolean("vultured", vultured);
        tag.putBoolean("isFakeBody", isFakeBody);
        if (killer != null) {
            tag.putUUID("Killer", killer);
        }
        if (conspiratorEvidence != null) {
            tag.putUUID("ConspiratorEvidence", conspiratorEvidence);
        }
        tag.putString("DeathReason", deathReason);

        ListTag items = new ListTag();
        for (int i = 0; i < 54; i++) {
            ItemStack stack = corpseInventory.getItem(i);
            if (!stack.isEmpty()) {
                // 确保物品数量在有效范围内 (1-99)
                if (stack.getCount() <= 0) {
                    continue; // 跳过无效数量的物品
                }
                if (stack.getCount() > 99) {
                    stack.setCount(99); // 限制最大数量为99
                }
                
                CompoundTag itemTag = new CompoundTag();
                Tag itemItemTag = stack.save(registryLookup);
                itemTag.put("Item", itemItemTag);
                itemTag.putByte("Slot", (byte) i);
                items.add(itemTag);
            }
        }
        tag.put("CorpseInventory", items);
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    public void readFromNbtFromBody(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.playerRole = ResourceLocation.tryParse(tag.getString("playerRole"));
        if (this.playerRole == null)
            this.playerRole = TMMRoles.CIVILIAN.identifier();
        this.vultured = tag.getBoolean("vultured");
        this.isFakeBody = tag.getBoolean("isFakeBody");

        if (tag.hasUUID("Killer")) {
            killer = tag.getUUID("Killer");
        } else {
            killer = null;
        }
        if (tag.hasUUID("ConspiratorEvidence")) {
            conspiratorEvidence = tag.getUUID("ConspiratorEvidence");
        } else {
            conspiratorEvidence = null;
        }
        deathReason = tag.getString("DeathReason");

        // 清空并加载物品
        for (int i = 0; i < 54; i++) {
            corpseInventory.setItem(i, ItemStack.EMPTY);
        }
        if (tag.contains("CorpseInventory", Tag.TAG_LIST)) {
            ListTag items = tag.getList("CorpseInventory", Tag.TAG_COMPOUND);
            for (int i = 0; i < items.size(); i++) {
                CompoundTag itemTag = items.getCompound(i);
                int slot = itemTag.getByte("Slot") & 255;
                if (slot >= 0 && slot < 54) {
                    if (itemTag.contains("Item")) {
                        ItemStack stack = ItemStack.parse(registryLookup, itemTag.getCompound("Item"))
                                .orElse(ItemStack.EMPTY);
                        // 验证并修正物品数量
                        if (!stack.isEmpty()) {
                            if (stack.getCount() <= 0) {
                                stack = ItemStack.EMPTY; // 无效数量则设为空
                            } else if (stack.getCount() > 99) {
                                stack.setCount(99); // 限制最大数量为99
                            }
                        }
                        corpseInventory.setItem(slot, stack);
                    }
                }
            }
        }
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        // 同步基本数据，不包含物品（打开容器时由菜单自动同步物品）
        tag.putString("playerRole", playerRole.toString());
        tag.putBoolean("vultured", vultured);
        tag.putBoolean("isFakeBody", isFakeBody);
        if (killer != null) {
            tag.putUUID("Killer", killer);
        }
        if (conspiratorEvidence != null) {
            tag.putUUID("ConspiratorEvidence", conspiratorEvidence);
        }
        tag.putString("DeathReason", deathReason);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.playerRole = ResourceLocation.tryParse(tag.getString("playerRole"));
        if (this.playerRole == null)
            this.playerRole = TMMRoles.CIVILIAN.identifier();
        this.vultured = tag.getBoolean("vultured");
        this.isFakeBody = tag.getBoolean("isFakeBody");
        if (tag.hasUUID("Killer")) {
            killer = tag.getUUID("Killer");
        } else {
            killer = null;
        }
        if (tag.hasUUID("ConspiratorEvidence")) {
            conspiratorEvidence = tag.getUUID("ConspiratorEvidence");
        } else {
            conspiratorEvidence = null;
        }
        deathReason = tag.getString("DeathReason");
    }

    @Override
    public void serverTick() {
        // 可选的定时逻辑
    }
}
