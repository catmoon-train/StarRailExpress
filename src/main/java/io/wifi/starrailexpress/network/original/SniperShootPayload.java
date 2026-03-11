package io.wifi.starrailexpress.network.original;

import io.wifi.starrailexpress.cca.GameWorldComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameFunctions;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.item.SniperRifleItem;
import io.wifi.starrailexpress.network.PacketTracker;
import io.wifi.starrailexpress.SRE;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;


public record SniperShootPayload(Action action, int targetOrShooterId) implements CustomPacketPayload {
    public static final Type<SniperShootPayload> TYPE = new Type<>(SRE.id("sniper_shoot"));
    public static final StreamCodec<FriendlyByteBuf, SniperShootPayload> STREAM_CODEC = StreamCodec.ofMember(
            SniperShootPayload::write,
            SniperShootPayload::new
    );

    private SniperShootPayload(FriendlyByteBuf buf) {
        this(
            buf.readEnum(Action.class),
            buf.readInt()
        );
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeEnum(action);
        buf.writeInt(targetOrShooterId);
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

                    // 设置冷却时间 - 任何射击行为都应该设置冷却
                    if (!player.isCreative())
                        player.getCooldowns().addCooldown(mainHandStack.getItem(),
                                GameConstants.ITEM_COOLDOWNS.getOrDefault(mainHandStack.getItem(), 0));

                    // 消耗一颗子弹
                    SniperRifleItem.consumeAmmo(mainHandStack);

                    // 播放射击声音 - 全场都能听到
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            TMMSounds.ITEM_SNIPER_RIFLE_SHOOT, SoundSource.MASTER, 10f,
                            1f + player.getRandom().nextFloat() * .1f - .05f);

                    // 发送枪口粒子效果
                    for (ServerPlayer tracking : PlayerLookup.tracking(player))
                        PacketTracker.sendToClient(tracking, new ShootMuzzleS2CPayload(player.getId()));

                    // 处理目标命中
                    if (player.serverLevel().getEntity(payload.targetOrShooterId()) instanceof Player target
                            && target.distanceTo(player) < 200.0) {
                        var game = GameWorldComponent.KEY.get(player.level());

                        // 检查角色权限
                        final var role = game.getRole(player);
                        if (role != null) {
                            if (!role.onGunHit(player, target)) {
                                return;
                            }
                        }

                        // 计算距离（50米 = 50个方块）
                        double distance = player.distanceTo(target);
                        boolean longRangeKill = distance >= 50.0;

                        // 如果是远距离射击（50米以上），移除目标的护盾
                        if (longRangeKill) {
                            var bartenderComponent = io.wifi.starrailexpress.cca.BartenderPlayerComponent.KEY.get(target);
                            if (bartenderComponent != null && bartenderComponent.getArmor() > 0) {
                                bartenderComponent.armor = 0;
                                bartenderComponent.sync();
                                // 触发护盾破碎事件
                                io.wifi.starrailexpress.event.OnShieldBroken.EVENT.invoker().onShieldBroken(target, player);
                            }
                        }

                        // 击杀逻辑 - 类似左轮手枪
                        if (game.isInnocent(target) && !player.isCreative()) {
                            if (game.isInnocent(player) && player.getRandom().nextFloat() <= game.getBackfireChance()) {
                                // 反向击发
                                GameFunctions.killPlayer(player, true, player, GameConstants.DeathReasons.SNIPER_RIFLE_BACKFIRE);
                                return;
                            } else {
                                // 掉落左轮手枪
                                player.getInventory().clearOrCountMatchingItems((s) -> s.is(TMMItems.SNIPER_RIFLE), 1, player.getInventory());
                                player.drop(TMMItems.REVOLVER.getDefaultInstance(), false, false);
                            }
                        }

                        GameFunctions.killPlayer(target, true, player, GameConstants.DeathReasons.SNIPER_RIFLE);
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
