package io.wifi.starrailexpress.client.mirror;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.RemotePlayer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 镜中的玩家倒影。
 *
 * <p>它是一个只用于渲染的空壳：位置、朝向、姿态、装备全部由
 * {@link MirrorReflection} 每 tick 直接写入，自身不推进任何逻辑。
 */
public class MirrorPlayerCopy extends RemotePlayer {

    private final UUID sourceId;

    public MirrorPlayerCopy(ClientLevel level, GameProfile profile, UUID sourceId) {
        super(level, profile);
        this.sourceId = sourceId;
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setSilent(true);
    }

    /**
     * 副本必须持有一个全新的 UUID：{@code EntityLookup.add()} 一旦发现 UUID 重复就直接 return，
     * 连 {@code byId} 都不写，副本会被静默丢弃、永远不渲染。
     * 所以皮肤改为按源玩家的 UUID 查 {@link PlayerInfo}，而不是靠自己的 UUID。
     */
    @Override
    public @Nullable PlayerInfo getPlayerInfo() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection == null ? null : connection.getPlayerInfo(this.sourceId);
    }

    @Override
    public void tick() {
    }

    @Override
    public void aiStep() {
    }

    /** 玩家贴着镜面时倒影会与本体重合，必须杜绝一切碰撞/交互耦合。 */
    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    /** 镜子只反射外观，不反射名牌——否则能直接读出身后是谁。 */
    @Override
    public boolean shouldShowName() {
        return false;
    }

    @Override
    public boolean isSpectator() {
        return false;
    }

    @Override
    public boolean isCreative() {
        return false;
    }
}
