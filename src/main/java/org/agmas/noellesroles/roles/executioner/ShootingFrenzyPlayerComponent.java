package org.agmas.noellesroles.roles.executioner;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.event.AllowShootRevolverDrop;
import io.wifi.starrailexpress.event.OnRevolverUsed;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
import io.wifi.starrailexpress.network.TriggerStatusBarPayload;
import io.wifi.starrailexpress.util.ShopEntry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import io.wifi.starrailexpress.api.RoleComponent;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 射击狂热组件
 * 刽子手(EXECUTIONER)的商店购买技能
 * - 狂暴时间内射击冷却-50%
 * - 枪不会掉落
 * - 手持双枪
 * - 无盾
 * - 不会受锁定目标影响
 * - 狂暴皮肤（魔改psycho, type=1）
 * - 华丽特效（开枪粒子和杀人特效）
 */
public class ShootingFrenzyPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<ShootingFrenzyPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "shooting_frenzy"),
            ShootingFrenzyPlayerComponent.class);

    private final Player player;
    public boolean inFrenzy = false;
<<<<<<< HEAD
=======
    // 记录狂暴前副手是否有物品，用于结束时恢复
    //private ItemStack savedOffhandItem = ItemStack.EMPTY;
    private ItemStack savedMainhandItem = ItemStack.EMPTY;
>>>>>>> 5f9025f0f6fa7536fb50a293590ad963d9cbf3a0

    public ShootingFrenzyPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return this.player == player;
    }

    @Override
    public void init() {
        this.inFrenzy = false;
<<<<<<< HEAD
=======
//        this.savedOffhandItem = ItemStack.EMPTY;
        this.savedMainhandItem = ItemStack.EMPTY;
>>>>>>> 5f9025f0f6fa7536fb50a293590ad963d9cbf3a0
        this.sync();
    }

    @Override
    public void clear() {
        this.init();
    }

    public void sync() {
        KEY.sync(this.player);
    }

    /**
     * 启动射击狂热模式
     * - 不给球棒，而是给双枪
     * - 设置psycho模式为type=1（狂暴皮肤）
     * - 无盾（armor=0）
     */
    public boolean startFrenzy() {
        SREPlayerPsychoComponent psychoComponent = SREPlayerPsychoComponent.KEY.get(player);
        if (psychoComponent.getPsychoTicks() > 0) {
            return false;
        }

        // 保存当前副手物品
<<<<<<< HEAD
        // this.savedOffhandItem = player.getOffhandItem().copy();

        // player.setItemInHand(InteractionHand.OFF_HAND, new
        // ItemStack(TMMItems.REVOLVER));
=======
//        this.savedOffhandItem = player.getOffhandItem().copy();
        this.savedMainhandItem = player.getMainHandItem().copy();

        // 给副手一把枪（双枪）
//        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(TMMItems.REVOLVER));
>>>>>>> 5f9025f0f6fa7536fb50a293590ad963d9cbf3a0

        // 确保主手也有枪
        if (!player.getMainHandItem().is(TMMItemTags.GUNS)) {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(TMMItems.REVOLVER));
        }

        // 设置psycho模式（不使用startPsycho避免给球棒）
        // psychoComponent.startPsycho();
        psychoComponent.setPsychoTicks(GameConstants.getPsychoTimer());
        psychoComponent.setArmour(0); // 无盾
        psychoComponent.type = 1; // 狂暴皮肤
        psychoComponent.sync();

        // 更新psycho计数
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
        gameWorldComponent.setPsychosActive(gameWorldComponent.getPsychosActive() + 1);

        // 触发状态栏
        if (player instanceof ServerPlayer serverPlayer) {
            ServerPlayNetworking.send(serverPlayer, new TriggerStatusBarPayload("Psycho"));
        }

        this.inFrenzy = true;
        this.sync();

        // 播放狂暴启动音效
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.0F, 1.5F);

        // 发送粒子特效
        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.FLAME,
                    player.getX(), player.getY() + 1, player.getZ(),
                    30, 0.5, 1.0, 0.5, 0.1);
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    player.getX(), player.getY() + 1, player.getZ(),
                    20, 0.5, 1.0, 0.5, 0.05);
        }

        return true;
    }

    /**
     * 停止射击狂热模式
     * 清理副手枪支，恢复原始副手物品
     */
    public void stopFrenzy() {
        if (!inFrenzy)
            return;

        this.inFrenzy = false;

<<<<<<< HEAD
=======
        // 清除副手枪支，恢复原来的物品
        ItemStack currentOffhand = player.getOffhandItem();
        if (currentOffhand.is(TMMItemTags.GUNS)) {
//            player.setItemInHand(InteractionHand.OFF_HAND, savedOffhandItem.copy());
        }
        if (player.getMainHandItem().is(TMMItemTags.GUNS)) {
            player.setItemInHand(InteractionHand.MAIN_HAND, savedMainhandItem.copy());
        }
//        this.savedOffhandItem = ItemStack.EMPTY;
        this.savedMainhandItem = ItemStack.EMPTY;

>>>>>>> 5f9025f0f6fa7536fb50a293590ad963d9cbf3a0
        // 重置psycho type
        SREPlayerPsychoComponent psychoComponent = SREPlayerPsychoComponent.KEY.get(player);
        psychoComponent.type = -1;
        psychoComponent.sync();

        this.sync();
    }

    /**
     * 检查玩家是否在狂暴状态
     */
    public static boolean isInFrenzy(Player player) {
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorldComponent.isRole(player, ModRoles.EXECUTIONER))
            return false;
        return KEY.get(player).inFrenzy;
    }

    @Override
    public void serverTick() {
        if (!inFrenzy)
            return;

        SREPlayerPsychoComponent psychoComponent = SREPlayerPsychoComponent.KEY.get(player);
        // 当psycho模式结束时，停止狂暴
        if (psychoComponent.getPsychoTicks() <= 0) {
            stopFrenzy();
            return;
        }

        // 双枪自动切换：当主手枪在冷却时，交换主副手
        // if (player.getMainHandItem().is(TMMItemTags.GUNS) && player.getOffhandItem().is(TMMItemTags.GUNS)) {
        //     if (player.getCooldowns().isOnCooldown(player.getMainHandItem().getItem())
        //             && !player.getCooldowns().isOnCooldown(player.getOffhandItem().getItem())) {
        //         ItemStack mainHand = player.getMainHandItem().copy();
        //         ItemStack offHand = player.getOffhandItem().copy();
        //         player.setItemInHand(InteractionHand.MAIN_HAND, offHand);
        //         player.setItemInHand(InteractionHand.OFF_HAND, mainHand);
        //     }
        // }

        // 每2秒发送一次粒子特效
        if (player.level() instanceof ServerLevel serverLevel && player.tickCount % 40 == 0) {
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    player.getX(), player.getY() + 0.5, player.getZ(),
                    5, 0.3, 0.5, 0.3, 0.02);
        }
    }

    @Override
    public void clientTick() {
        // 客户端跟踪
    }

    /**
     * 注册枪不掉落事件
     * 狂暴期间枪永远不掉落
     */
    public static void registerGunNoDropEvent() {
        AllowShootRevolverDrop.EVENT.register((player, target) -> {
            if (isInFrenzy(player)) {
                return AllowShootRevolverDrop.ShouldDropResult.FALSE;
            }
            return AllowShootRevolverDrop.ShouldDropResult.PASS;
        });
    }

    /**
     * 注册狂暴冷却减半事件
     * 狂暴期间射击冷却-50%
     */
    public static void registerFrenzyCooldownEvent() {
        OnRevolverUsed.EVENT.register((player, target) -> {
            if (isInFrenzy(player)) {
                ItemStack mainHandStack = player.getMainHandItem();
                if (mainHandStack.is(TMMItemTags.GUNS)) {
                    int baseCooldown = GameConstants.ITEM_COOLDOWNS.getOrDefault(
                            mainHandStack.getItem(),
                            GameConstants.ITEM_COOLDOWNS.getOrDefault(TMMItems.REVOLVER, 0));
                    // 冷却减半
                    player.getCooldowns().addCooldown(mainHandStack.getItem(), baseCooldown / 2);
                }

                // 击杀特效：华丽粒子
                if (target != null && player.level() instanceof ServerLevel serverLevel) {
                    // 火焰爆发特效
                    serverLevel.sendParticles(ParticleTypes.FLAME,
                            target.getX(), target.getY() + 1, target.getZ(),
                            15, 0.3, 0.5, 0.3, 0.1);
                    // 灵魂火焰特效
                    serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                            target.getX(), target.getY() + 1, target.getZ(),
                            10, 0.3, 0.5, 0.3, 0.05);
                    // 烟花爆炸特效
                    serverLevel.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                            target.getX(), target.getY() + 1.5, target.getZ(),
                            20, 0.5, 0.8, 0.5, 0.3);
                    // 末影粒子
                    serverLevel.sendParticles(ParticleTypes.PORTAL,
                            target.getX(), target.getY() + 1, target.getZ(),
                            25, 0.4, 0.6, 0.4, 0.5);

                    // 击杀音效
                    serverLevel.playSound(null, target.blockPosition().above(1),
                            SoundEvents.ENDER_DRAGON_SHOOT, SoundSource.PLAYERS, 0.8F, 1.2F);
                }
            }
        });
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("inFrenzy", this.inFrenzy);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.inFrenzy = tag.getBoolean("inFrenzy");
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
