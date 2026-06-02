package org.agmas.noellesroles.game.roles.killer.warlock;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.*;

public class WarlockPlayerComponent implements RoleComponent, ServerTickingComponent {
    public static final ComponentKey<WarlockPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "warlock"),
            WarlockPlayerComponent.class);

    public static final int MARK_COOLDOWN = 60 * 20;
    public static final int KILL_COOLDOWN = 150 * 20;
    public static final double MARK_RANGE = 4.0D;
    public static final double KILL_RANGE = 3.0D;
    /** 蹲下技能可发动的最大距离（与标记目标的距离） */
    public static final double HEX_KILL_RANGE = 20.0D;

    private final Player player;
    public UUID markedTarget;
    public int markCooldown;
    public int killCooldown;

    public WarlockPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() { return player; }

    @Override
    public void init() {
        markedTarget = null;
        markCooldown = 60 * 20;
        killCooldown = 60 * 20;
        sync();
    }

    @Override
    public void clear() { init(); }

    public void sync() { KEY.sync(player); }

    public boolean isActiveWarlock() {
        if (player == null || player.level().isClientSide()) return false;
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        return gameWorld.isRole(player, ModRoles.WARLOCK);
    }

    // --- skill: mark ---
    public boolean tryMark(ServerPlayer target) {
        if (!(player instanceof ServerPlayer sp)) return false;
        if (!isActiveWarlock()) return false;
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) return false;
        if (!GameUtils.isPlayerAliveAndSurvival(target)) return false;
        if (target == sp) return false;
        if (markCooldown > 0) return false;
        if (player.distanceTo(target) > MARK_RANGE) return false;

        markedTarget = target.getUUID();
        markCooldown = MARK_COOLDOWN;
        sync();
        return true;
    }

    // --- skill: hex kill ---
    public ServerPlayer tryHexKill() {
        if (!(player instanceof ServerPlayer sp)) return null;
        if (!isActiveWarlock()) return null;
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) return null;
        if (killCooldown > 0) return null;
        if (markedTarget == null) return null;

        ServerPlayer marked = sp.server.getPlayerList().getPlayer(markedTarget);
        if (marked == null || !GameUtils.isPlayerAliveAndSurvival(marked)) {
            markedTarget = null;
            sync();
            return null;
        }

        // 距离检查：咒法师必须在标记目标20格范围内才能发动咒杀
        if (player.distanceTo(marked) > HEX_KILL_RANGE) {
            return null;
        }

        // Find victim near marked player
        for (Player p : marked.serverLevel().players()) {
            if (p == sp || p == marked) continue;
            if (!GameUtils.isPlayerAliveAndSurvival(p)) continue;
            if (p.distanceToSqr(marked) <= KILL_RANGE * KILL_RANGE) {
                killCooldown = KILL_COOLDOWN;
                markedTarget = null;
                sync();
                return (ServerPlayer) p;
            }
        }
        return null;
    }

    @Override
    public void serverTick() {
        if (!isActiveWarlock()) return;
        if (markCooldown > 0) { markCooldown--; sync(); }
        if (killCooldown > 0) { killCooldown--; sync(); }
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider lookup) {
        tag.putInt("markCooldown", markCooldown);
        tag.putInt("killCooldown", killCooldown);
        if (markedTarget != null) tag.putString("markedTarget", markedTarget.toString());
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider lookup) {
        markCooldown = tag.getInt("markCooldown");
        killCooldown = tag.getInt("killCooldown");
        markedTarget = tag.contains("markedTarget") ? UUID.fromString(tag.getString("markedTarget")) : null;
    }

    @Override public void writeToNbt(CompoundTag tag, HolderLookup.Provider lookup) {}
    @Override public void readFromNbt(CompoundTag tag, HolderLookup.Provider lookup) {}
}
