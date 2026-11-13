package org.agmas.noellesroles.client.roulette;

import io.wifi.starrailexpress.SREClientConfig;
import net.exmo.sre.camera.client.AdvancedCameraDirector;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.content.block_entity.DevilRouletteTableEntity;
import org.agmas.noellesroles.game.minigame.roulette.*;
import org.agmas.noellesroles.init.NRSounds;

/** 按服务端时间越过关键帧时播放一次；粒子仅在本机生成。 */
public final class RouletteEffects {
    private static BlockPos shakeTable;
    private static long shakeAt = Long.MIN_VALUE / 2, heartbeatAt = Long.MIN_VALUE / 2;
    private RouletteEffects() {}

    static void tick(RouletteClient.State state, DevilRouletteTableEntity table, long now) {
        RouletteView v = state.view;
        float age = v.age(now, 0), old = state.lastAge;
        if (age <= old) return;
        if (cross(old, age, 2)) {
            switch (v.phase()) {
                case INTRO -> sound(table, SoundEvents.NOTE_BLOCK_PLING.value(), .35F, .65F);
                case RELOAD -> sound(table, SoundEvents.IRON_TRAPDOOR_OPEN, .45F, .8F);
                case SHOOT -> sound(table, SoundEvents.LEVER_CLICK, .3F, .7F);
                case ITEM -> itemStart(table, v.item());
                case ADRENALINE -> itemStart(table, RouletteItem.ADRENALINE);
                case HANDOFF -> sound(table, SoundEvents.WOODEN_TRAPDOOR_CLOSE, .25F, .7F);
                case ENDING -> sound(table, SoundEvents.BELL_BLOCK, .35F, .8F);
                default -> { }
            }
        }
        if (v.phase() == RoulettePhase.RELOAD) {
            for (int cue : new int[]{20, 26, 32, 38})
                if (cross(old, age, cue)) sound(table, NRSounds.SHOTGUNU_COCK, .35F, .85F + cue * .004F);
        }
        for (int side = 0; side < 2; side++) for (long arrived : state.arrivals[side])
            if (arrived > now - 1 && arrived <= now) sound(table, SoundEvents.ITEM_PICKUP, .25F, 1.15F);
        if (v.phase() == RoulettePhase.SHOOT && cross(old, age, 16))
            sound(table, NRSounds.SHOTGUNU_COCK, .65F, 1);
        if (v.phase() == RoulettePhase.ADRENALINE && cross(old, age, 19)) itemStart(table, v.stolen());
        int impact = v.phase().impact();
        boolean exposed = v.phase() == RoulettePhase.SHOOT ? v.shell() >= 0 : state.resultSeen;
        if (!state.impactDone && impact >= 0 && age >= impact && exposed) {
            state.impactDone = true;
            if (v.phase() == RoulettePhase.SHOOT) {
                if (v.shell() == 1) {
                    sound(table, NRSounds.SHOTGUN_FIRE, 1F, state.shotSaw ? 1.15F : .9F);
                    double side = v.actor().equals(v.front()) ? -1 : 1;
                    double direction = v.actor().equals(v.target()) ? side : -side;
                    burst(table, ParticleTypes.SMOKE, 8, 0, .73, side * .42 + direction * (state.shotSaw ? .4 : .73));
                    var mc = Minecraft.getInstance();
                    if (mc.player != null && v.target().equals(mc.player.getUUID())) shake(state.pos, now);
                } else sound(table, SoundEvents.STONE_BUTTON_CLICK_ON, .55F, 1.45F);
            } else {
                RouletteItem item = v.phase() == RoulettePhase.ADRENALINE ? v.stolen() : v.item();
                itemImpact(table, item, v, now);
            }
        }
        RouletteItem active = v.phase() == RoulettePhase.ADRENALINE ? v.stolen() : v.item();
        if ((v.phase() == RoulettePhase.ITEM || v.phase() == RoulettePhase.ADRENALINE)
                && age >= impact && age < impact + 8 && now % 2 == 0 && active != null) {
            double actor = v.actor().equals(v.front()) ? -1 : 1;
            switch (active) {
                case CIGARETTE -> burst(table, ParticleTypes.SMOKE, 1, .12, .65, actor * .55);
                case HANDSAW -> burst(table, ParticleTypes.ELECTRIC_SPARK, 2, 0, .3, -actor * .35);
                case INVERTER -> burst(table, ParticleTypes.ELECTRIC_SPARK, 2, 0, .4, 0);
                default -> { }
            }
        }
        state.lastAge = age;
    }

    private static boolean cross(float old, float now, int tick) { return old < tick && now >= tick; }

    private static void itemStart(DevilRouletteTableEntity table, RouletteItem item) {
        if (item == null) return;
        switch (item) {
            case MAGNIFYING_GLASS -> sound(table, SoundEvents.AMETHYST_BLOCK_CHIME, .25F, 1.8F);
            case BEER -> sound(table, SoundEvents.BOTTLE_FILL, .45F, .8F);
            case CIGARETTE -> sound(table, SoundEvents.FLINTANDSTEEL_USE, .35F, 1F);
            case HANDSAW -> sound(table, SoundEvents.GRINDSTONE_USE, .45F, .8F);
            case HANDCUFFS -> sound(table, SoundEvents.CHAIN_PLACE, .5F, 1F);
            case INVERTER -> sound(table, SoundEvents.RESPAWN_ANCHOR_CHARGE, .35F, 1.5F);
            case TELEPHONE -> sound(table, SoundEvents.NOTE_BLOCK_BELL.value(), .5F, 1.5F);
            case EXPIRED_MEDICINE -> sound(table, SoundEvents.BREWING_STAND_BREW, .5F, 1.4F);
            case ADRENALINE -> sound(table, SoundEvents.TRIPWIRE_CLICK_ON, .4F, 1.3F);
        }
    }

    private static void itemImpact(DevilRouletteTableEntity table, RouletteItem item, RouletteView v, long now) {
        if (item == null) return;
        switch (item) {
            case BEER -> { sound(table, SoundEvents.GENERIC_DRINK, .5F, .8F); sound(table, NRSounds.SHOTGUNU_COCK, .65F, 1F); }
            case CIGARETTE -> sound(table, SoundEvents.FIRE_EXTINGUISH, .2F, 1.7F);
            case HANDSAW -> sound(table, SoundEvents.ANVIL_LAND, .4F, 1.6F);
            case HANDCUFFS -> sound(table, SoundEvents.CHAIN_HIT, .5F, 1.1F);
            case INVERTER -> sound(table, SoundEvents.REDSTONE_TORCH_BURNOUT, .4F, 1.3F);
            case TELEPHONE -> sound(table, SoundEvents.NOTE_BLOCK_BELL.value(), .4F, .75F);
            case EXPIRED_MEDICINE -> {
                sound(table, SoundEvents.GENERIC_DRINK, .5F, 1F);
                if (v.delta() < 0 && Minecraft.getInstance().player != null
                        && v.actor().equals(Minecraft.getInstance().player.getUUID())) shake(table.getBlockPos(), now);
            }
            default -> { }
        }
        if (v.delta() > 0) burst(table, ParticleTypes.HAPPY_VILLAGER, 4, 0, .5, v.actor().equals(v.front()) ? -.8 : .8);
    }

    private static void sound(DevilRouletteTableEntity table, SoundEvent event, float volume, float pitch) {
        var level = Minecraft.getInstance().level;
        if (level != null) {
            BlockPos p = table.getBlockPos();
            level.playLocalSound(p.getX() + .5, p.getY() + .4, p.getZ() + .5, event, SoundSource.BLOCKS, volume, pitch, false);
        }
    }

    private static void burst(DevilRouletteTableEntity table, ParticleOptions type, int count, double x, double y, double z) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        Vec3 local = new Vec3(x, y, z).yRot((float) Math.toRadians(180 - table.getFacing().toYRot()));
        Vec3 origin = Vec3.atBottomCenterOf(table.getBlockPos()).add(local);
        for (int i = 0; i < count; i++) {
            var random = level.random;
            level.addParticle(type, origin.x + (random.nextDouble() - .5) * .12, origin.y,
                    origin.z + (random.nextDouble() - .5) * .12,
                    (random.nextDouble() - .5) * .03, .025 + random.nextDouble() * .03, (random.nextDouble() - .5) * .03);
        }
    }

    private static void shake(BlockPos pos, long now) { shakeTable = pos; shakeAt = now; }

    public static float shake(float partialTick) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.getCameraEntity() != mc.player
                || SREClientConfig.instance().disableScreenShake || AdvancedCameraDirector.shouldOverride()
                || AdvancedCameraDirector.isPresentationActive() || org.agmas.noellesroles.client.PriestHeavenClient.isOverheadCamera()
                || io.wifi.starrailexpress.client.SecurityCameraClientState.isInSecurityMode()) return 0;
        var state = shakeTable == null ? null : RouletteClient.get(shakeTable);
        if (state == null || !state.view.isParticipant(mc.player.getUUID())) return 0;
        float age = mc.level.getGameTime() - shakeAt + partialTick;
        return age < 0 || age >= 7 ? 0 : (1 - age / 7) * 1.15F;
    }

    static float hurtPulse(float partialTick) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || shakeTable == null) return 0;
        float age = mc.level.getGameTime() - shakeAt + partialTick;
        return age < 0 || age >= 10 ? 0 : (1 - age / 10) * .4F;
    }

    static void heartbeat(RouletteClient.State state, long now) {
        var mc = Minecraft.getInstance();
        if (state == null || mc.player == null || state.view.phase() == RoulettePhase.WAITING
                || state.view.phase() == RoulettePhase.ENDING || state.view.health(mc.player.getUUID()) > 2
                || state.view.health(mc.player.getUUID()) <= 0) return;
        int period = state.view.health(mc.player.getUUID()) == 1 ? 22 : 32;
        if (now - heartbeatAt >= period) {
            heartbeatAt = now;
            mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                    SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.PLAYERS, .18F, .55F, false);
        }
    }

    static void clearShake(BlockPos pos) { if (pos.equals(shakeTable)) { shakeTable = null; shakeAt = Long.MIN_VALUE / 2; } }
    static void clear() { shakeTable = null; shakeAt = heartbeatAt = Long.MIN_VALUE / 2; }
}
