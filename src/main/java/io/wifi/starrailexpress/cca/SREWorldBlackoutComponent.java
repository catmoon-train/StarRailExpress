/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.cca;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMProperties;
import io.wifi.starrailexpress.index.TMMSounds;
import net.fabricmc.api.EnvType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;
import org.ladysnake.cca.api.v3.util.CheckEnvironment;

import java.util.ArrayList;
import java.util.List;

public class SREWorldBlackoutComponent implements AutoSyncedComponent, ServerTickingComponent {
    public static final ComponentKey<SREWorldBlackoutComponent> KEY = ComponentRegistry.getOrCreate(SRE.id("blackout"),
            SREWorldBlackoutComponent.class);
    private final Level world;
    public final List<BlackoutDetails> blackouts = new ArrayList<>();
    public int blackOutRemainingTicks = 0;
    /** 只同步本局黑夜标记；地图灯光原值单独保存，修电不能提前解除。 */
    private boolean nightEventActive;
    private final java.util.Map<BlockPos, NightLight> nightLights = new java.util.LinkedHashMap<>();

    private record NightLight(String blockId, boolean lit, boolean active) {}

    public boolean isNightEventActive() { return nightEventActive; }

    public void startNightEvent() {
        if (nightEventActive) return;
        reset();
        nightEventActive = true;
        enforceNightLights();
        blackOutRemainingTicks = 20;
        sync();
    }

    /** 局末专用：先撤销锁定，再按原值恢复；不走会被黑夜保护拦截的普通 reset。 */
    public void endNightEvent() {
        if (!nightEventActive && nightLights.isEmpty()) return;
        nightEventActive = false;
        restoreNightLights();
        blackOutRemainingTicks = 0;
        sync();
    }

    private void enforceNightLights() {
        for (BlockPos point : GameUtils.resetPoints) {
            if (!world.hasChunkAt(point)) continue;
            BlockState state = world.getBlockState(point);
            if (!state.hasProperty(BlockStateProperties.LIT) || !state.hasProperty(TMMProperties.ACTIVE)) continue;
            nightLights.putIfAbsent(point.immutable(), new NightLight(
                    BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),
                    state.getValue(BlockStateProperties.LIT), state.getValue(TMMProperties.ACTIVE)));
            if (state.getValue(BlockStateProperties.LIT) || state.getValue(TMMProperties.ACTIVE)) {
                world.setBlockAndUpdate(point, state.setValue(BlockStateProperties.LIT, false)
                        .setValue(TMMProperties.ACTIVE, false));
            }
        }
    }

    private void restoreNightLights() {
        for (var entry : nightLights.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState state = world.getBlockState(pos);
            NightLight original = entry.getValue();
            if (BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().equals(original.blockId())
                    && state.hasProperty(BlockStateProperties.LIT) && state.hasProperty(TMMProperties.ACTIVE)) {
                world.setBlockAndUpdate(pos, state.setValue(BlockStateProperties.LIT, original.lit())
                        .setValue(TMMProperties.ACTIVE, original.active()));
            }
        }
        nightLights.clear();
    }

    public SREWorldBlackoutComponent(Level world) {
        this.world = world;
    }

    public void reset() {
        if (nightEventActive && SREGameWorldComponent.KEY.get(world).getGameStatus()
                == SREGameWorldComponent.GameStatus.ACTIVE) {
            enforceNightLights();
            return;
        }
        if (nightEventActive || !nightLights.isEmpty()) {
            nightEventActive = false;
            restoreNightLights();
        }
        for (BlackoutDetails detail : this.blackouts)
            detail.end(this.world);
        this.blackouts.clear();
        this.blackOutRemainingTicks = 0;

        sync();
    }

    @Override
    public void serverTick() {
        if (nightEventActive) {
            if (SREGameWorldComponent.KEY.get(world).getGameStatus() != SREGameWorldComponent.GameStatus.ACTIVE) {
                endNightEvent();
            } else {
                blackOutRemainingTicks = 20;
                // 只写实际改变的灯；每秒兜底发现新加载区块，不制造无限时长的普通停电记录。
                if (world.getGameTime() % 20 == 0) enforceNightLights();
                return;
            }
        }
        if (SREGameTimeComponent.KEY.get(world).isTimeFrozen()) {
            return;
        }
        this.blackOutRemainingTicks = 0;
        if (!this.blackouts.isEmpty()) {
            for (int i = 0; i < this.blackouts.size(); i++) {
                BlackoutDetails detail = this.blackouts.get(i);
                detail.tick(this.world);
                if (detail.time <= 0) {
                    detail.end(this.world);
                    this.blackouts.remove(i);
                    i--;
                } else {
                    this.blackOutRemainingTicks = Math.max(this.blackOutRemainingTicks, detail.time);
                }
            }
            if (this.blackouts.isEmpty()) {
                this.blackOutRemainingTicks = 0;
                sync();
            }
        }
    }

    public boolean isBlackoutActive() {
        return nightEventActive || this.blackOutRemainingTicks > 0;
    }

    public boolean triggerBlackout() {
        return triggerBlackout(true);
    }

    /**
     * 以center为中心，触发半径为distance的关灯
     * 
     * @param center
     * @param distance
     * @param haveSound
     * @param duration
     * @return
     */
    public boolean triggerBlackout(BlockPos center, int distance, boolean haveSound, int duration) {
        if (nightEventActive) return false;
        for (var pos : GameUtils.resetPoints) {
            if (pos.distSqr(center) > distance * distance)
                continue;
            BlockState state = this.world.getBlockState(pos);
            if (!state.hasProperty(BlockStateProperties.LIT) || !state.hasProperty(TMMProperties.ACTIVE))
                continue;
            int maxFloatRange = (int) ((float) duration * GameConstants.getBlackoutRandomRangePercent());
            maxFloatRange = Math.max(1, maxFloatRange);
            int randomInt = this.world.random.nextInt(0,
                    maxFloatRange);
            randomInt = Math.min(duration, randomInt);
            BlackoutDetails detail = new BlackoutDetails(pos, duration - randomInt,
                    state.getValue(BlockStateProperties.LIT));
            detail.init(this.world);
            this.blackouts.add(detail);
            this.blackOutRemainingTicks = Math.max(this.blackOutRemainingTicks, detail.time);
        }
        if (haveSound)
            playBlackoutSound();

        sync();
        return true;
    }

    public boolean triggerBlackout(boolean haveSound, int duration) {
        if (nightEventActive) return false;

        for (var pos : GameUtils.resetPoints) {
            BlockState state = this.world.getBlockState(pos);
            if (!state.hasProperty(BlockStateProperties.LIT) || !state.hasProperty(TMMProperties.ACTIVE))
                continue;
            int maxFloatRange = (int) ((float) duration * GameConstants.getBlackoutRandomRangePercent());
            maxFloatRange = Math.max(1, maxFloatRange);
            int randomInt = this.world.random.nextInt(0,
                    maxFloatRange);
            randomInt = Math.min(duration, randomInt);
            BlackoutDetails detail = new BlackoutDetails(pos, duration - randomInt,
                    state.getValue(BlockStateProperties.LIT));
            detail.init(this.world);
            this.blackouts.add(detail);
            this.blackOutRemainingTicks = Math.max(this.blackOutRemainingTicks, detail.time);
        }
        if (haveSound)
            playBlackoutSound();
        sync();
        return true;
    }

    public void sync() {
        KEY.sync(this.world);
    }

    public void playBlackoutSound() {
        if (this.world instanceof ServerLevel serverWorld) {
            for (ServerPlayer player : serverWorld.players()) {
                if (GameUtils.isPlayerAliveAndSurvival(player)) {
                    final var role = SREGameWorldComponent.KEY.get(world).getRole(player);
                    if (role != null) {
                        if ((!role.canUseKiller() && !role.isNeutralForKiller()
                                && !role.canIgnoreBlackout(player))) {
                            player.addEffect(
                                    new MobEffectInstance(MobEffects.BLINDNESS, 200, 0, false, false, false));
                            player.addEffect(
                                    new MobEffectInstance(MobEffects.DARKNESS, 200, 0, false, false, false));
                        }
                    }
                    player.connection.send(new ClientboundSoundPacket(
                            BuiltInRegistries.SOUND_EVENT.wrapAsHolder(TMMSounds.AMBIENT_BLACKOUT),
                            SoundSource.PLAYERS,
                            player.getX(), player.getY(), player.getZ(), 100f, 1f, player.getRandom().nextLong()));
                }
            }

        }
    }

    public static int getMaxDuration(Level world) {
        int duration = (int) (GameConstants.getBlackoutMaxDuration());
        return duration;
    }

    public boolean triggerBlackout(boolean haveSound) {
        if (this.blackOutRemainingTicks > 0)
            return false;
        int duration = getMaxDuration(world);
        return triggerBlackout(haveSound, duration);
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        ListTag list = new ListTag();
        for (BlackoutDetails detail : this.blackouts)
            list.add(detail.writeToNbt());
        tag.put("blackouts", list);
        ListTag night = new ListTag();
        for (var entry : nightLights.entrySet()) {
            CompoundTag light = new CompoundTag();
            light.putLong("pos", entry.getKey().asLong());
            light.putString("block", entry.getValue().blockId());
            light.putBoolean("lit", entry.getValue().lit());
            light.putBoolean("active", entry.getValue().active());
            night.add(light);
        }
        tag.put("nightLights", night);
    }

    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("bt", blackOutRemainingTicks);
        tag.putBoolean("nightEvent", nightEventActive);
    }

    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        nightEventActive = tag.getBoolean("nightEvent");
        if (tag.contains("bt")) {
            blackOutRemainingTicks = tag.getInt("bt");
        } else {
            blackOutRemainingTicks = 0;
        }

    }

    public void writeToSyncNbtWithPlayer(CompoundTag tag, HolderLookup.Provider registryLookup,
            ServerPlayer recipient) {
        writeToSyncNbt(tag, registryLookup);
    }

    @Override
    public void writeSyncPacket(RegistryFriendlyByteBuf buf, ServerPlayer recipient) {
        CompoundTag tag = new CompoundTag();
        this.writeToSyncNbtWithPlayer(tag, buf.registryAccess(), recipient);
        buf.writeNbt(tag);
    }

    @Override
    @CheckEnvironment(EnvType.CLIENT)
    public void applySyncPacket(RegistryFriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        if (tag != null) {
            this.readFromSyncNbt(tag, buf.registryAccess());
        }
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        // 服务器重启不恢复旧对局；留下的灯光快照只用于回滚地图，不能让黑夜污染下一局。
        nightEventActive = false;
        nightLights.clear();
        for (Tag element : tag.getList("nightLights", 10)) {
            CompoundTag light = (CompoundTag) element;
            nightLights.put(BlockPos.of(light.getLong("pos")), new NightLight(light.getString("block"),
                    light.getBoolean("lit"), light.getBoolean("active")));
        }
        restoreNightLights();
        this.blackouts.clear();
        for (Tag element : tag.getList("blackouts", 10)) {
            BlackoutDetails detail = new BlackoutDetails((CompoundTag) element);
            detail.init(this.world);
            this.blackouts.add(detail);
        }

    }

    public static class BlackoutDetails {
        private final BlockPos pos;
        private final boolean original;
        private int time;

        public BlackoutDetails(BlockPos pos, int time, boolean original) {
            this.pos = pos;
            this.time = time;
            this.original = original;
        }

        public BlackoutDetails(@NotNull CompoundTag tag) {
            this.pos = new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
            this.time = tag.getInt("time");
            this.original = tag.getBoolean("original");
        }

        public void init(@NotNull Level world) {
            BlockState state = world.getBlockState(this.pos);
            if (!state.hasProperty(BlockStateProperties.LIT) || !state.hasProperty(TMMProperties.ACTIVE))
                return;
            world.setBlockAndUpdate(this.pos,
                    state.setValue(BlockStateProperties.LIT, false).setValue(TMMProperties.ACTIVE, false));
            world.playSound(null, this.pos, TMMSounds.BLOCK_LIGHT_TOGGLE, SoundSource.BLOCKS, 0.5f, 1f);
        }

        public void end(@NotNull Level world) {
            BlockState state = world.getBlockState(this.pos);
            if (!state.hasProperty(BlockStateProperties.LIT) || !state.hasProperty(TMMProperties.ACTIVE))
                return;
            world.setBlockAndUpdate(this.pos,
                    state.setValue(BlockStateProperties.LIT, this.original).setValue(TMMProperties.ACTIVE, true));
            world.playSound(null, this.pos, TMMSounds.BLOCK_LIGHT_TOGGLE, SoundSource.BLOCKS, 0.5f, 0.5f);
        }

        public void tick(Level world) {
            if (this.time > 0)
                this.time--;
            if (this.time > 4)
                return;
            BlockState state = world.getBlockState(this.pos);
            if (!state.hasProperty(BlockStateProperties.LIT) || !state.hasProperty(TMMProperties.ACTIVE))
                return;
            switch (this.time) {
                case 0 -> this.end(world);
                case 1, 3 -> {
                    world.setBlockAndUpdate(this.pos, state.setValue(BlockStateProperties.LIT, false));
                    world.playSound(null, this.pos, TMMSounds.BLOCK_BUTTON_TOGGLE_NO_POWER, SoundSource.BLOCKS, 0.1f,
                            1f);
                }
                case 2, 5 -> {
                    world.setBlockAndUpdate(this.pos, state.setValue(BlockStateProperties.LIT, true));
                    world.playSound(null, this.pos, TMMSounds.BLOCK_BUTTON_TOGGLE_NO_POWER, SoundSource.BLOCKS, 0.1f,
                            1f);
                }
            }
        }

        public CompoundTag writeToNbt() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("x", this.pos.getX());
            tag.putInt("y", this.pos.getY());
            tag.putInt("z", this.pos.getZ());
            tag.putInt("time", this.time);
            tag.putBoolean("original", this.original);
            return tag;
        }
    }

    public static SREWorldBlackoutComponent getInstance(Player player) {
        return KEY.get(player.level());
    }

    public static SREWorldBlackoutComponent getInstance(Level level) {
        return KEY.get(level);
    }

    public void stopBlackout() {
        reset();
    }
}