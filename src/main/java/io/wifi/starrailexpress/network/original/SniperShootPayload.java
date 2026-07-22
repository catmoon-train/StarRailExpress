package io.wifi.starrailexpress.network.original;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.content.item.SniperRifleItem;
import io.wifi.starrailexpress.event.AllowShootRevolverDrop;
import io.wifi.starrailexpress.event.IsShootBackFire;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.network.PacketTracker;
import io.wifi.starrailexpress.util.HorseDamageUtil;
import io.wifi.starrailexpress.util.SREItemUtils;
import io.wifi.starrailexpress.util.Scheduler;
import io.wifi.starrailexpress.util.TrueFalseResult;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.content.block.scene.TrainTargetBlock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record SniperShootPayload(Action action, int targetOrShooterId, @Nullable BlockPos hitBlockPos)
        implements CustomPacketPayload {
    public static final Type<SniperShootPayload> TYPE = new Type<>(SRE.id("sniper_shoot"));
    public static final StreamCodec<FriendlyByteBuf, SniperShootPayload> STREAM_CODEC = StreamCodec.ofMember(
            SniperShootPayload::write,
            SniperShootPayload::new);

    public SniperShootPayload(Action action, int targetOrShooterId) {
        this(action, targetOrShooterId, null);
    }

    private SniperShootPayload(FriendlyByteBuf buf) {
        this(
                buf.readEnum(Action.class),
                buf.readInt(),
                buf.readBoolean() ? buf.readBlockPos() : null);
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeEnum(action);
        buf.writeInt(targetOrShooterId);
        buf.writeBoolean(hitBlockPos != null);
        if (hitBlockPos != null) {
            buf.writeBlockPos(hitBlockPos);
        }
    }

    public enum Action {
        SHOOT,
        RELOAD,
        INSTALL_SCOPE,
        UNINSTALL_SCOPE
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static class Receiver implements ServerPlayNetworking.PlayPayloadHandler<SniperShootPayload> {
        @Override
        public void receive(@NotNull SniperShootPayload payload, ServerPlayNetworking.@NotNull Context context) {
            ServerPlayer player = context.player();
            ItemStack mainHandStack = player.getMainHandItem();

            if (!mainHandStack.is(TMMItems.SNIPER_RIFLE))
                return;
            switch (payload.action()) {
                case SHOOT -> {
                    if (player.getCooldowns().isOnCooldown(mainHandStack.getItem()))
                        return;
                    if (SniperRifleItem.getAmmoCount(mainHandStack) <= 0)
                        return;

                    if (!player.isCreative()) {
                        player.getCooldowns().addCooldown(mainHandStack.getItem(),
                                GameConstants.ITEM_COOLDOWNS.getOrDefault(mainHandStack.getItem(), 0));
                    }

                    SniperRifleItem.consumeAmmo(mainHandStack);

                    // 同步修改后的弹药数量回客户端，防止客户端和服务端 DataComponent 不一致
                    player.inventoryMenu.broadcastChanges();

                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            TMMSounds.ITEM_SNIPER_RIFLE_SHOOT, SoundSource.MASTER, 10f,
                            1f + player.getRandom().nextFloat() * .1f - .05f);

                    for (ServerPlayer tracking : PlayerLookup.tracking(player))
                        PacketTracker.sendToClient(tracking, new ShootMuzzleS2CPayload(player.getId()));

                    // 处理方块命中（列车标靶）
                    if (payload.hitBlockPos() != null && player.serverLevel() != null
                            && player.serverLevel().getBlockState(payload.hitBlockPos())
                                    .getBlock() instanceof TrainTargetBlock) {
                        TrainTargetBlock.onHit(player.serverLevel(), payload.hitBlockPos());
                    }

                    // 处理实体命中
                    Entity hitEntity = player.serverLevel().getEntity(payload.targetOrShooterId());
                    if (hitEntity instanceof Player target && target.distanceTo(player) < 200.0) {
                        var game = SREGameWorldComponent.KEY.get(player.level());

                        final var role = game.getRole(player);
                        if (role != null) {
                            if (!role.onGunHit(player, target)) {
                                return;
                            }
                        }

                        double distance = player.distanceTo(target);
                        boolean longRangeKill = distance >= 50.0;

                        if (longRangeKill) {
                            var bartenderComponent = io.wifi.starrailexpress.cca.SREArmorPlayerComponent.KEY
                                    .get(target);
                            if (bartenderComponent != null && bartenderComponent.getArmor() > 0) {
                                bartenderComponent.armor = 0;
                                bartenderComponent.sync();
                                io.wifi.starrailexpress.event.OnShieldBroken.EVENT.invoker().onShieldBroken(target,
                                        player);
                            }
                        }
                        boolean backfire = false;
                        backfire = IsShootBackFire.EVENT.invoker().isShootBackFire(player, target);
                        boolean shouldDropRevolver = game.isInnocent(target) && !player.isCreative()
                                && mainHandStack.is(TMMItems.SNIPER_RIFLE);
                        var dropresult = AllowShootRevolverDrop.EVENT.invoker().allowDrop(player, target);
                        if (dropresult.equals(TrueFalseResult.FALSE)) {
                            shouldDropRevolver = false;
                        } else if (dropresult.equals(TrueFalseResult.TRUE)) {
                            shouldDropRevolver = true;
                        }
                        if (backfire) {
                            GameUtils.killPlayer(player, true, null, GameConstants.DeathReasons.SNIPER_RIFLE_BACKFIRE);
                        } else if (shouldDropRevolver) {
                            Scheduler.schedule(() -> {
                                {
                                    boolean flag = false;
                                    if (player.getMainHandItem().is(TMMItems.SNIPER_RIFLE)) {
                                        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                                        flag = true;
                                    } else if (SREItemUtils.clearItem(player, TMMItems.SNIPER_RIFLE, 1) >= 1) {
                                        flag = true;
                                    }

                                    if (flag) {
                                        ItemEntity item = player.drop(TMMItems.REVOLVER.getDefaultInstance(), false, false);
                                        if (item != null) {
                                             {
                                                item.setPickUpDelay(10);
                                            }
                                            item.setThrower(player);
                                        }
                                        PacketTracker.sendToClient(player, new GunDropPayload());
                                        SREPlayerMoodComponent.KEY.get(player).setMood(0);
                                    }
                                }
                            }, 1);
                        }
                        GameUtils.killPlayer(target, true, player, GameConstants.DeathReasons.SNIPER_RIFLE);
                    } else {
                        // 通用马匹伤害处理
                        HorseDamageUtil.tryDamageHorse(hitEntity, player, 20.0F, 200.0);
                    }
                }
                case RELOAD -> {
                    if (player.getCooldowns().isOnCooldown(mainHandStack.getItem()))
                        return;
                    if (SniperRifleItem.getAmmoCount(mainHandStack) >= SniperRifleItem.MAX_AMMO)
                        return;

                    // 检查是否有马格南子弹
                    boolean hasBullet = false;
                    for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                        ItemStack invStack = player.getInventory().getItem(i);
                        if (invStack.is(TMMItems.MAGNUM_BULLET)) {
                            hasBullet = true;
                            invStack.shrink(1); // 消耗一颗子弹
                            break;
                        }
                    }
                    if (!hasBullet)
                        return;

                    // 装填子弹
                    int currentAmmo = SniperRifleItem.getAmmoCount(mainHandStack);
                    SniperRifleItem.setAmmoCount(mainHandStack, currentAmmo + 1);

                    // 同步修改后的弹药数量回客户端，防止客户端和服务端 DataComponent 不一致
                    player.inventoryMenu.broadcastChanges();

                    // 播放装填声音
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            TMMSounds.ITEM_SNIPER_RIFLE_RELOAD, SoundSource.PLAYERS, 0.5f, 1f);

                    // 设置冷却时间（6秒）
                    if (!player.isCreative())
                        player.getCooldowns().addCooldown(mainHandStack.getItem(), 120);
                }
                case INSTALL_SCOPE -> {
                    if (player.getCooldowns().isOnCooldown(mainHandStack.getItem()))
                        return;
                    if (SniperRifleItem.hasScopeAttached(mainHandStack))
                        return;

                    // 检查是否有倍镜
                    boolean hasScope = false;
                    for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                        ItemStack invStack = player.getInventory().getItem(i);
                        if (invStack.is(TMMItems.SCOPE)) {
                            hasScope = true;
                            invStack.shrink(1); // 消耗一个倍镜
                            break;
                        }
                    }
                    if (!hasScope)
                        return;

                    // 安装倍镜
                    SniperRifleItem.setScopeAttached(mainHandStack, true);

                    // 发送倍镜状态更新给客户端
                    PacketTracker.sendToClient(player, new SniperScopeStateS2CPayload(true));

                    // 播放安装声音
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            TMMSounds.ITEM_SCOPE_ATTACH, SoundSource.PLAYERS, 0.5f, 1f);

                    // 设置冷却时间（1秒）
                    if (!player.isCreative())
                        player.getCooldowns().addCooldown(mainHandStack.getItem(), 20);
                }
                case UNINSTALL_SCOPE -> {
                    if (player.getCooldowns().isOnCooldown(mainHandStack.getItem()))
                        return;
                    if (!SniperRifleItem.hasScopeAttached(mainHandStack))
                        return;

                    // 卸载倍镜
                    SniperRifleItem.setScopeAttached(mainHandStack, false);

                    // 发送倍镜状态更新给客户端（这会通知客户端退出开镜状态）
                    PacketTracker.sendToClient(player, new SniperScopeStateS2CPayload(false));

                    // 给予倍镜物品
                    player.getInventory().add(TMMItems.SCOPE.getDefaultInstance());

                    // 播放卸载声音
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            TMMSounds.ITEM_SCOPE_DETACH, SoundSource.PLAYERS, 0.5f, 1f);

                    // 设置冷却时间（1秒）
                    if (!player.isCreative())
                        player.getCooldowns().addCooldown(mainHandStack.getItem(), 20);
                }
            }
        }
    }
}
