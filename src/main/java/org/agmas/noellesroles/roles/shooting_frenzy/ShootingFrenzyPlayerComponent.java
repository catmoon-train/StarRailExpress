package org.agmas.noellesroles.roles.shooting_frenzy;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.event.AllowShootRevolverDrop;
import io.wifi.starrailexpress.event.OnRevolverUsed;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.Scheduler;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.harpymodloader.events.ModdedRoleAssigned;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;
import org.agmas.noellesroles.Noellesroles;
import net.minecraft.resources.ResourceLocation;
import io.wifi.starrailexpress.index.tag.TMMItemTags;

/**
 * 刽子手射击狂热组件
 *
 * 魔改Psycho角色特性：
 * - 狂暴时间内射击冷却-50%
 * - 枪不会掉落
 * - 手持双枪
 * - 无盾（狂暴时护甲为0）
 * - 不会受锁定目标影响
 * - 狂暴皮肤
 */
public class ShootingFrenzyPlayerComponent implements RoleComponent, ServerTickingComponent {

    public static final ComponentKey<ShootingFrenzyPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "shooting_frenzy"),
            ShootingFrenzyPlayerComponent.class);

    private final Player player;

    public ShootingFrenzyPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {
        this.sync();
    }

    @Override
    public void clear() {
        this.init();
    }

    public void sync() {
        KEY.sync(this.player);
    }

    @Override
    public void serverTick() {
        if (player.level().isClientSide())
            return;
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorldComponent == null)
            return;
        if (!gameWorldComponent.isRunning())
            return;
        if (!gameWorldComponent.isRole(player, ModRoles.SHOOTING_FRENZY))
            return;

        // 确保副手始终有枪（双枪特性）
        ItemStack offhandStack = player.getItemInHand(InteractionHand.OFF_HAND);
        if (offhandStack.isEmpty() || !offhandStack.is(TMMItemTags.GUNS)) {
            // 检查主手是否有枪
            ItemStack mainHandStack = player.getItemInHand(InteractionHand.MAIN_HAND);
            if (mainHandStack.is(TMMItemTags.GUNS)) {
                // 如果副手为空且主手有枪，给副手也放一把
                if (offhandStack.isEmpty()) {
                    player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(TMMItems.REVOLVER));
                }
            }
        }
    }

    // ==================== 静态事件注册 ====================

    /**
     * 注册枪械不掉落事件
     * 射击狂热角色的枪永远不会掉落
     */
    public static void registerGunNoDropEvent() {
        AllowShootRevolverDrop.EVENT.register((player, target) -> {
            SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
            if (gameWorldComponent.isRole(player, ModRoles.SHOOTING_FRENZY)) {
                return AllowShootRevolverDrop.ShouldDropResult.FALSE;
            }
            return AllowShootRevolverDrop.ShouldDropResult.PASS;
        });
    }

    /**
     * 注册狂暴时射击冷却减半事件
     * 在狂暴模式（psycho mode）期间，射击冷却时间减少50%
     * 使用 Scheduler 延迟1 tick 确保在基础冷却设置之后再覆盖
     */
    public static void registerFrenzyCooldownEvent() {
        OnRevolverUsed.EVENT.register((player, target) -> {
            SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
            if (gameWorldComponent.isRole(player, ModRoles.SHOOTING_FRENZY)) {
                SREPlayerPsychoComponent psychoComponent = SREPlayerPsychoComponent.KEY.get(player);
                if (psychoComponent.psychoTicks > 0) {
                    // 延迟1 tick，确保在基础冷却设置之后覆盖为半值
                    Scheduler.schedule(() -> {
                        ItemStack mainHandStack = player.getMainHandItem();
                        if (mainHandStack.is(TMMItemTags.GUNS)) {
                            int originalCooldown = GameConstants.ITEM_COOLDOWNS.getOrDefault(mainHandStack.getItem(),
                                    GameConstants.ITEM_COOLDOWNS.getOrDefault(TMMItems.REVOLVER, 0));
                            player.getCooldowns().addCooldown(mainHandStack.getItem(), originalCooldown / 2);
                        }
                    }, 1);
                }
            }
        });
    }

    /**
     * 注册角色分配事件
     * 当射击狂热角色被分配时，给予双枪（副手放一把左轮）
     */
    public static void registerRoleAssignedEvent() {
        ModdedRoleAssigned.EVENT.register((player, role) -> {
            if (role.identifier().equals(ModRoles.SHOOTING_FRENZY_ID)) {
                // 给副手放一把左轮手枪（双枪特性）
                player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(TMMItems.REVOLVER));
            }
        });
    }

    // ==================== NBT 序列化 ====================

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
