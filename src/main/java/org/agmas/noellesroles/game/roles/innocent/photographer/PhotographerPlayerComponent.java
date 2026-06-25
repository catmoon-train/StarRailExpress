package org.agmas.noellesroles.game.roles.innocent.photographer;

import io.wifi.starrailexpress.api.RoleComponent;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;

/**
 * 摄影师（Photographer，乘客阵营）组件。
 *
 * <p>仅用于记录本局内画框（照片框）的购买次数，以实现"一局最多购买两次"的限制。
 * 穿越画框的位置存储复用 exposure 照片自带的 Frame 数据，传送/冷却逻辑见
 * {@link PhotographerFrameEvents}。
 */
public class PhotographerPlayerComponent implements RoleComponent {

    public static final ComponentKey<PhotographerPlayerComponent> KEY = ModComponents.PHOTOGRAPHER;

    private final Player player;

    /** 本局已购买画框的次数。 */
    private int framesBought = 0;

    public PhotographerPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer p) {
        return p == this.player;
    }

    @Override
    public void init() {
        this.framesBought = 0;
    }

    @Override
    public void clear() {
        init();
    }

    /** 是否还能再购买画框（受配置上限约束）。 */
    public boolean canBuyFrame() {
        return this.framesBought < NoellesRolesConfig.HANDLER.instance().photographerFrameMaxBuy;
    }

    /** 记录一次画框购买。 */
    public void recordFrameBought() {
        this.framesBought++;
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("framesBought", this.framesBought);
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.framesBought = tag.getInt("framesBought");
    }
}
