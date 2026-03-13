package org.agmas.noellesroles.component;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.StarGameWorldComponent;
import io.wifi.starrailexpress.game.GameFunctions;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.player.Player;

import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

public class GlitchRobotPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {

    public static final ComponentKey<GlitchRobotPlayerComponent> KEY = ComponentRegistry
            .getOrCreate(Noellesroles.id("glitch_robot"), GlitchRobotPlayerComponent.class);

    private final Player player;
    public int glitchTimer = 0;

    public GlitchRobotPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public void reset() {
        this.glitchTimer = 0;
        this.sync();
    }

    @Override
    public void clear() {
        this.reset();
    }

    @Override
    public void serverTick() {
        StarGameWorldComponent gameWorld = StarGameWorldComponent.KEY.get(player.level());
        if (!gameWorld.isRole(player, ModRoles.GLITCH_ROBOT)) {
            return;
        }
        if (!GameFunctions.isPlayerAliveAndSurvival(player)) {
            return;
        }

        // 故障计时器
        glitchTimer++;
        if (glitchTimer >= 600) { // 30秒
            glitchTimer = 0;
            // 缓慢 10 (Amplifier 9), 3.5秒 (70 ticks)
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 70, 9, false, false, true));
        }
    }

    /**
     * 被击倒时调用，生成缓慢效果云
     */
    public static void onKnockOut(Player victim) {
        if (victim instanceof ServerPlayer sp) {
            // 创建半径为4的缓慢2效果云，持续5秒（100 ticks）
            // var command = "execute at @s run summon area_effect_cloud ~ ~ ~
            // {Radius:4,Duration:100,RadiusOnUse:0f,RadiusPerTick:0f,WaitTime:0,potion_contents:{custom_effects:[{id:\"slowness\",amplifier:1,duration:100,ambient:false,show_icon:false,show_particles:false}]},custom_particle:{type:\"dust\",color:15924992,scale:1}}";
            // try {
            // sp.getServer().getCommands().performPrefixedCommand(sp.createCommandSourceStack(),
            // command);
            // } catch (Exception e) {
            // LoggerFactory.getLogger(GlitchRobotPlayerComponent.class).warn(
            // "Failed to execute : " + command + ", error: " + e.getMessage());
            // }
            AreaEffectCloud cloud = new AreaEffectCloud(sp.level(), sp.getX(), sp.getY(),
                    sp.getZ());

            cloud.setRadius(6.0F);
            cloud.setDuration(100); // 5秒
            cloud.setRadiusOnUse(0.0F);
            cloud.setRadiusPerTick(0.0F);
            cloud.setWaitTime(0);
            cloud.setParticle(ParticleTypes.EFFECT);
            cloud.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 2,
                    false, false, true));
            sp.level().addFreshEntity(cloud);
        }
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.glitchTimer = tag.getInt("glitchTimer");
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("glitchTimer", this.glitchTimer);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return this.player == player;
    }

    public void sync() {
        ModComponents.GLITCH_ROBOT.sync(this.player);
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void clientTick() {
        var gameComp = StarGameWorldComponent.KEY.maybeGet(player.level()).orElse(null);
        if (gameComp == null || !gameComp.isRole(player, ModRoles.GLITCH_ROBOT)) {
            return;
        }
        if (!player.getSlot(103).get().is(ModItems.NIGHT_VISION_GLASSES))
            player.removeEffect(MobEffects.NIGHT_VISION);
    }
}