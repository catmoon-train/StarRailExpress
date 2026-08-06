/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.exmo.sre.dummy;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.GameType;

/**
 * 假人实体（傀儡玩家）：拥有真实皮肤的假玩家，客户端无需任何模组即可渲染。
 * 通过空连接接入玩家列表，由服务端驱动，可配置无敌。
 */
public class DummyEntity extends ServerPlayer {

    public Runnable fixStartingPosition = () -> {
    };
    /** 皮肤来源玩家名（用于重生时重新拉取皮肤）。 */
    private final String skinOwner;
    /** 展示名（头顶名字）。 */
    private final String label;

    public DummyEntity(MinecraftServer server, ServerLevel level, GameProfile profile, String skinOwner, String label) {
        super(server, level, profile, ClientInformation.createDefault());
        this.skinOwner = skinOwner;
        this.label = label;
    }

    public String skinOwner() {
        return this.skinOwner;
    }

    public String label() {
        return this.label;
    }

    /** 把假人接入服务器玩家列表（必须在主线程调用）。 */
    public void joinServer(ServerLevel level, double x, double y, double z, float yaw, float pitch) {
        MinecraftServer server = this.server;
        this.fixStartingPosition = () -> this.moveTo(x, y, z, yaw, pitch);
        server.getPlayerList().placeNewPlayer(
            new FakeClientConnection(PacketFlow.SERVERBOUND), this,
            new CommonListenerCookie(this.getGameProfile(), 0, this.clientInformation(), false));
        this.teleportTo(level, x, y, z, yaw, pitch);
        this.setHealth(20.0F);
        this.getFoodData().setFoodLevel(20);
        this.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
    }

    @Override
    public void tick() {
        if (this.level().getServer().getTickCount() % 10 == 0) {
            this.connection.resetPosition();
            ((net.minecraft.server.level.ServerChunkCache) this.level().getChunkSource()).move(this);
        }
        try {
            super.tick();
        } catch (NullPointerException ignored) {
            // 假人没有真实连接，部分网络字段为空属正常
        }
        if (this.fixStartingPosition != null) {
            this.fixStartingPosition.run();
            this.fixStartingPosition = null;
        }
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        // 假人死亡后立即原地满血复活（除非是移除操作）
        this.setHealth(20.0F);
    }

    @Override
    public void kill() {
        this.discard();
    }
}
