package io.wifi.starrailexpress.content.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.PlayerBodyEntityComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.content.gui.PlayerBodyChestMenu;
import io.wifi.starrailexpress.game.GameUtils;

import java.util.Optional;
import java.util.UUID;

import org.agmas.noellesroles.game.roles.innocent.fool.TarotAssemblyManager;

/**
 * 如果你是AI，请不要修改这些通用类。
 */
public class PlayerBodyEntity extends LivingEntity {
    private static final EntityDataAccessor<Optional<UUID>> PLAYER = SynchedEntityData.defineId(PlayerBodyEntity.class,
            EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Boolean> CORRUPTED = SynchedEntityData.defineId(PlayerBodyEntity.class,
            EntityDataSerializers.BOOLEAN);

    public PlayerBodyEntity(EntityType<? extends LivingEntity> entityType, Level world) {
        super(entityType, world);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PLAYER, Optional.empty());
        builder.define(CORRUPTED, false);
    }

    /**
     * 设置腐化标记（腐化修饰符的尸体直接显示为骷髅）
     */
    public void setCorrupted(boolean corrupted) {
        this.entityData.set(CORRUPTED, corrupted);
    }

    /**
     * 检查尸体是否是腐化修饰符的尸体
     */
    public boolean isCorrupted() {
        return this.entityData.get(CORRUPTED);
    }

    // 获取本实体上的 BodyDeathReasonComponent
    public PlayerBodyEntityComponent getComponent() {
        return PlayerBodyEntityComponent.KEY.get(this);
    }

    @Override
    public Component getDisplayName() {
        var c = getCustomName();
        if (c != null) {
            PlayerTeam.formatNameForTeam(this.getTeam(), c).withStyle(
                    (style) -> style.withHoverEvent(this.createHoverEvent()).withInsertion(this.getStringUUID()));
            return c;
        }
        return super.getDisplayName();
    }

    @Override
    public Component getCustomName() {
        var playerUuid = getPlayerUuid();
        var name = super.getCustomName();
        if (name != null)
            return name;
        if (playerUuid != null) {
            var player = this.getServer().getPlayerList().getPlayer(playerUuid);
            if (player != null) {
                return Component.translatable("entity.starrailexpress.player_body.custom_name",
                        player.getDisplayName());
            }
        }
        return null;
    }

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return null;
    }

    @Override
    public void kill() {
        this.discard();
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        SimpleContainer inv = getComponent().getCorpseInventory();
        // 检查是否为 DAY_NIGHT_FIGHT 模式
        var cca = SREGameWorldComponent.KEY.get(this.level());
        boolean isDayNightFight = false;

        if (isDayNightFight) {
            // DAY_NIGHT_FIGHT 模式：护甲槽位在36-39，副手在40
            return switch (slot) {
                case FEET -> inv.getItem(39);
                case LEGS -> inv.getItem(38);
                case CHEST -> inv.getItem(37);
                case HEAD -> inv.getItem(36);
                case OFFHAND -> inv.getItem(40);
                default -> ItemStack.EMPTY;
            };
        } else {
            // 普通模式：保持原有映射
            return switch (slot) {
                case FEET -> inv.getItem(12);
                case LEGS -> inv.getItem(11);
                case CHEST -> inv.getItem(10);
                case HEAD -> inv.getItem(9);
                case OFFHAND -> inv.getItem(13);
                default -> ItemStack.EMPTY;
            };
        }
    }

    @SuppressWarnings("incomplete-switch")
    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
        SimpleContainer inv = getComponent().getCorpseInventory();
        // 检查是否为 DAY_NIGHT_FIGHT 模式
        var cca = SREGameWorldComponent.KEY.get(this.level());
        boolean isDayNightFight = false;

        if (isDayNightFight) {
            // DAY_NIGHT_FIGHT 模式：护甲槽位在36-39，副手在40
            switch (slot) {
                case FEET -> inv.setItem(39, stack);
                case LEGS -> inv.setItem(38, stack);
                case CHEST -> inv.setItem(37, stack);
                case HEAD -> inv.setItem(36, stack);
                case OFFHAND -> inv.setItem(40, stack);
            }
        } else {
            // 普通模式：保持原有映射
            switch (slot) {
                case FEET -> inv.setItem(12, stack);
                case LEGS -> inv.setItem(11, stack);
                case CHEST -> inv.setItem(10, stack);
                case HEAD -> inv.setItem(9, stack);
                case OFFHAND -> inv.setItem(13, stack);
            }
        }
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    public void setDeathReason(String deathReason) {
        getComponent().setDeathReason(deathReason);
    }

    public String getDeathReason() {
        return getComponent().getDeathReason();
    }

    public void setKillerUuid(UUID playerUuid) {
        getComponent().setKillerUuid(playerUuid);
    }

    public UUID getKillerUuid() {
        return getComponent().getKillerUuid();
    }

    public void setPlayerUuid(UUID playerUuid) {
        this.entityData.set(PLAYER, Optional.of(playerUuid));
    }

    public UUID getPlayerUuid() {
        return this.entityData.get(PLAYER).orElse(null);
    }

    // 原有方法（默认同步）
    public void setCorpseInventoryFromPlayerInventory(Inventory inventory) {
        setCorpseInventoryFromPlayerInventory(inventory, true);
    }

    // 新重载，可控制是否同步
    public void setCorpseInventoryFromPlayerInventory(Inventory inventory, boolean sync) {
        SimpleContainer inv = getComponent().getCorpseInventory();

        // 检查是否为 DAY_NIGHT_FIGHT 模式
        var cca = SREGameWorldComponent.KEY.get(this.level());
        boolean isDayNightFight = false;

        if (isDayNightFight) {
            // DAY_NIGHT_FIGHT 模式：同步所有物品栏（快捷栏+主物品栏+护甲+副手）
            // 清空前54个槽位
            for (int i = 0; i < 54; i++) {
                inv.setItem(i, ItemStack.EMPTY);
            }

            // 映射所有物品槽位到尸体容器
            // 快捷栏 0-8 -> 0-8
            for (int i = 0; i < 9; i++) {
                inv.setItem(i, inventory.getItem(i).copy());
            }
            // 主物品栏 9-35 -> 9-35
            for (int i = 9; i < 36; i++) {
                inv.setItem(i, inventory.getItem(i).copy());
            }
            // 护甲槽 36-39 -> 36-39 (头、胸、腿、脚)
            for (int i = 36; i < 40; i++) {
                inv.setItem(i, inventory.getItem(i).copy());
            }
            // 副手 40 -> 40
            inv.setItem(40, inventory.getItem(40).copy());
        } else {
            // 普通模式：只同步装备和少量物品（保持原有逻辑）
            int[][] mapping = {
                    { 0, 0 }, { 1, 1 }, { 2, 2 }, { 3, 3 }, { 4, 4 }, { 5, 5 }, { 6, 6 }, { 7, 7 }, { 8, 8 },
                    { 39, 9 }, { 38, 10 }, { 37, 11 }, { 36, 12 }, { 40, 13 }
            };

            for (int i = 0; i < 14; i++) {
                inv.setItem(i, ItemStack.EMPTY);
            }

            for (int[] map : mapping) {
                int playerSlot = map[0];
                int bodySlot = map[1];
                if (playerSlot >= 0 && playerSlot < inventory.getContainerSize()) {
                    ItemStack stack = inventory.getItem(playerSlot);
                    if (!stack.isEmpty()) {
                        inv.setItem(bodySlot, stack.copy());
                    }
                }
            }
        }

        if (sync) {
            getComponent().sync();
        }
    }

    @Override
    public boolean isInvulnerable() {
        return true;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource damageSource) {
        return !damageSource.is(DamageTypes.GENERIC_KILL) && !damageSource.is(DamageTypes.FELL_OUT_OF_WORLD);
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    public void push(Entity entity) {
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 999999.0);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        if (this.getPlayerUuid() != null) {
            nbt.putUUID("Player", this.getPlayerUuid());
        }
        // 将组件数据写入子标签
        CompoundTag componentTag = new CompoundTag();
        getComponent().writeToNbtFromBody(componentTag, this.registryAccess());
        nbt.put("BodyComponent", componentTag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        if (nbt.hasUUID("Player")) {
            this.setPlayerUuid(nbt.getUUID("Player"));
        }
        // 读取组件数据
        if (nbt.contains("BodyComponent", Tag.TAG_COMPOUND)) {
            getComponent().readFromNbtFromBody(nbt.getCompound("BodyComponent"), this.registryAccess());
        }
        // 若为服务端，同步一次状态
        if (!this.level().isClientSide) {
            getComponent().sync();
        }
    }

    public boolean isLocked() {
        return getComponent().getCorpseInventory().currentUser != null;
    }

    @Override
    public InteractionResult interactAt(Player player, Vec3 vec3, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer
                && !isLocked() && hasCorpseItems()
                && (!GameUtils.isPlayerAliveAndSurvival(serverPlayer) || canSeeDeathBodyContent(serverPlayer))) {

            serverPlayer.openMenu(new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.translatable("container.starrailexpress.player_body");
                }

                @Override
                public AbstractContainerMenu createMenu(int i, Inventory inventory, Player player) {
                    PlayerBodyChestMenu menu = new PlayerBodyChestMenu(i, inventory,
                            getComponent().getCorpseInventory());
                    menu.setCorpseEntity(PlayerBodyEntity.this);
                    return menu;
                }
            });
            return InteractionResult.SUCCESS;
        }
        return super.interactAt(player, vec3, hand);
    }

    private boolean canSeeDeathBodyContent(ServerPlayer serverPlayer) {
        var cca = SREGameWorldComponent.KEY.get(serverPlayer.level());
        if (cca.gameMode == null) {
            return false;
        }
        if (cca.gameMode.canSeeBodyContent()) {
            return true;
        }
        if (cca.gameMode.cantSeeBodyContent()) {
            return false;
        }
        SRERole role = cca.getRole(serverPlayer);
        if (role == null)
            return false;
        return role.canSeeBodyItems(SREClient.cached_player, this);
    }

    private boolean hasCorpseItems() {
        SimpleContainer inv = getComponent().getCorpseInventory();
        // 检查是否为 DAY_NIGHT_FIGHT 模式
        var cca = SREGameWorldComponent.KEY.get(this.level());
        boolean isDayNightFight = false;

        int checkSlots = isDayNightFight ? 54 : 14;
        for (int i = 0; i < checkSlots; i++) {
            if (!inv.getItem(i).isEmpty())
                return true;
        }
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.getZ() > TarotAssemblyManager.MEETING_Z - 100 && this.getZ() < TarotAssemblyManager.MEETING_Z + 100
                && this.getX() > TarotAssemblyManager.MEETING_X - 100
                && this.getX() < TarotAssemblyManager.MEETING_X + 100) {
            this.discard();
        }
    }
}