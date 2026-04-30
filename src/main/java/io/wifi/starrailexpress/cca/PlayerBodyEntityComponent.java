package io.wifi.starrailexpress.cca;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
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
    public PlayerBodyEntity playerBodyEntity;

    private UUID killer;
    private String deathReason = "";

    // 容器大小27，仅允许0-13槽放置物品
    private final SimpleContainer corpseInventory = new SimpleContainer(27) {
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return slot < 14 && super.canPlaceItem(slot, stack);
        }
        // setItem 不再自动同步，由调用者根据需要手动调用 sync()
    };

    public PlayerBodyEntityComponent(PlayerBodyEntity playerBodyEntity) {
        this.playerBodyEntity = playerBodyEntity;
    }

    // ---------- 物品容器访问 ----------
    public SimpleContainer getCorpseInventory() {
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
        this.killer = null;
        this.deathReason = "";
        for (int i = 0; i < 14; i++) {
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
        tag.putString("playerRole", playerRole.toString());
        tag.putBoolean("vultured", vultured);
        if (killer != null) {
            tag.putUUID("Killer", killer);
        }
        tag.putString("DeathReason", deathReason);

        ListTag items = new ListTag();
        for (int i = 0; i < 14; i++) {
            ItemStack stack = corpseInventory.getItem(i);
            if (!stack.isEmpty()) {
                CompoundTag itemTag = new CompoundTag();
                itemTag.putByte("Slot", (byte) i);
                stack.save(registryLookup, itemTag);
                items.add(itemTag);
            }
        }
        tag.put("CorpseInventory", items);
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.playerRole = ResourceLocation.tryParse(tag.getString("playerRole"));
        if (this.playerRole == null)
            this.playerRole = TMMRoles.CIVILIAN.identifier();
        this.vultured = tag.getBoolean("vultured");

        if (tag.hasUUID("Killer")) {
            killer = tag.getUUID("Killer");
        } else {
            killer = null;
        }
        deathReason = tag.getString("DeathReason");

        // 清空并加载物品
        for (int i = 0; i < 14; i++) {
            corpseInventory.setItem(i, ItemStack.EMPTY);
        }
        if (tag.contains("CorpseInventory", Tag.TAG_LIST)) {
            ListTag items = tag.getList("CorpseInventory", Tag.TAG_COMPOUND);
            for (int i = 0; i < items.size(); i++) {
                CompoundTag itemTag = items.getCompound(i);
                int slot = itemTag.getByte("Slot") & 255;
                if (slot >= 0 && slot < 14) {
                    ItemStack stack = ItemStack.parse(registryLookup, itemTag).orElse(ItemStack.EMPTY);
                    corpseInventory.setItem(slot, stack);
                }
            }
        }
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        // 同步基本数据，不包含物品（打开容器时由菜单自动同步物品）
        tag.putString("playerRole", playerRole.toString());
        tag.putBoolean("vultured", vultured);
        if (killer != null) {
            tag.putUUID("Killer", killer);
        }
        tag.putString("DeathReason", deathReason);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.playerRole = ResourceLocation.tryParse(tag.getString("playerRole"));
        if (this.playerRole == null)
            this.playerRole = TMMRoles.CIVILIAN.identifier();
        this.vultured = tag.getBoolean("vultured");
        if (tag.hasUUID("Killer")) {
            killer = tag.getUUID("Killer");
        } else {
            killer = null;
        }
        deathReason = tag.getString("DeathReason");
    }

    @Override
    public void serverTick() {
        // 可选的定时逻辑
    }
}