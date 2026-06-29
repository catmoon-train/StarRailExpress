package org.agmas.noellesroles.game.roles.neutral.cuckoo;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.player.Player;
import com.mojang.math.Transformation;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.jetbrains.annotations.NotNull;

public class CuckooPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {

    public static final ComponentKey<CuckooPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "cuckoo"),
            CuckooPlayerComponent.class);

    private final Player player;

    public int startPlayers = 0;
    public int requiredEggs = 5;
    /** 场上当前存活的蛋数量 */
    public int survivingEggs = 0;
    public int placeCooldown = 0;

    public CuckooPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {
        survivingEggs = 0;
        placeCooldown = 0;
        if (player.level() instanceof ServerLevel serverLevel) {
            var gameWorld = SREGameWorldComponent.KEY.get(serverLevel);
            startPlayers = gameWorld.getPlayerCount();
            requiredEggs = Math.max(4, (startPlayers * 3) / 8 - 1);
        }
        sync();
    }

    @Override
    public void clear() {
        init();
    }

    public void sync() {
        KEY.sync(player);
    }

    @Override
    public void clientTick() {
        if (placeCooldown > 0) placeCooldown--;
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer sp)) return;
        var level = sp.serverLevel();
        var gameWorld = SREGameWorldComponent.KEY.get(level);
        if (!gameWorld.isRole(player, ModRoles.CUCKOO)) return;
        if (!gameWorld.isRunning() || !GameUtils.isPlayerAliveAndSurvival(player)) return;

        if (placeCooldown > 0) placeCooldown--;
    }

    public boolean canPlaceEgg() {
        return placeCooldown == 0 && player.onGround() && survivingEggs < requiredEggs + 2;
    }

    public boolean placeEgg(ServerPlayer serverPlayer) {
        if (!canPlaceEgg()) {
            if (placeCooldown == 0 && !serverPlayer.onGround()) {
                serverPlayer.displayClientMessage(Component.translatable("message.noellesroles.cuckoo.place_fail_air"), true);
            } else if (placeCooldown == 0 && survivingEggs >= requiredEggs + 2) {
                serverPlayer.displayClientMessage(Component.translatable("message.noellesroles.cuckoo.place_fail_max"), true);
            }
            return false;
        }
        if (!(serverPlayer.level() instanceof ServerLevel level)) return false;

        var aabb = serverPlayer.getBoundingBox().inflate(10.0, 3.0, 10.0);
        if (CuckooEggData.hasNearbyEgg(level, aabb)) {
            serverPlayer.displayClientMessage(Component.translatable("message.noellesroles.cuckoo.place_fail_near"), true);
            return false;
        }

        var egg = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, level);
        egg.setBlockState(net.minecraft.world.level.block.Blocks.SNIFFER_EGG.defaultBlockState());
        egg.setPos(serverPlayer.getX(), serverPlayer.getY() + 0.15, serverPlayer.getZ());
        egg.setTransformation(new Transformation(
                new Vector3f(0, 0, 0),
                new Quaternionf(new AxisAngle4f(0, 0, 1, 0)),
                new Vector3f(0.5f, 0.5f, 0.5f),
                new Quaternionf(new AxisAngle4f(0, 0, 1, 0))
        ));

        level.addFreshEntity(egg);

        CuckooEggData.registerEgg(egg, serverPlayer.getUUID());

        survivingEggs++;
        placeCooldown = 20 * 20;
        sync();
        serverPlayer.displayClientMessage(Component.translatable("message.noellesroles.cuckoo.place_success"), true);
        return true;
    }

    public void onEggBroken(Entity egg) {
        survivingEggs = Math.max(0, survivingEggs - 1);
        sync();
        if (player instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.cuckoo.egg_broken"), false);
        }
    }

    public static boolean checkCuckooVictory(ServerLevel serverLevel) {
        var gameWorld = SREGameWorldComponent.KEY.get(serverLevel);
        for (var p : serverLevel.players()) {
            if (!gameWorld.isRole(p, ModRoles.CUCKOO)) continue;
            CuckooPlayerComponent comp = KEY.get(p);
            if (comp != null && comp.survivingEggs >= comp.requiredEggs && comp.requiredEggs > 0) {
                RoleUtils.customWinnerWin(serverLevel, GameUtils.WinStatus.CUSTOM, ModRoles.CUCKOO_ID.getPath(), java.util.OptionalInt.of(ModRoles.CUCKOO.color()));
                return true;
            }
        }
        return false;
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("SurvivingEggs", survivingEggs);
        tag.putInt("RequiredEggs", requiredEggs);
        tag.putInt("PlaceCooldown", placeCooldown);
        CuckooEggData.writeServerSync(tag, player.getUUID());
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        survivingEggs = tag.getInt("SurvivingEggs");
        requiredEggs = tag.getInt("RequiredEggs");
        placeCooldown = tag.getInt("PlaceCooldown");
        CuckooEggData.readClientSync(tag);
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
