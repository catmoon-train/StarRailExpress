package cn.zbx1425.minopp.effect;

import cn.zbx1425.minopp.Mino;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

public class EffectEvents {

    public static final int EFFECT_RADIUS = 16;

    public static final EffectEvent.Type<SoundEffectEvent> SOUND = new EffectEvent.Type<>(Mino.id("sound"), SoundEffectEvent.STREAM_CODEC);
    public static final EffectEvent.Type<PlayerFireworkEffectEvent> PLAYER_FIREWORK = new EffectEvent.Type<>(Mino.id("player_firework"), PlayerFireworkEffectEvent.STREAM_CODEC);
    public static final EffectEvent.Type<PlayerGlowEffectEvent> PLAYER_GLOW = new EffectEvent.Type<>(Mino.id("player_glow"), PlayerGlowEffectEvent.STREAM_CODEC);
    public static final EffectEvent.Type<GrantRewardEffectEvent> GRANT_REWARD = new EffectEvent.Type<>(Mino.id("grant_reward"), GrantRewardEffectEvent.STREAM_CODEC);
    public static final EffectEvent.Type<SeatActionTakenEffectEvent> SEAT_ACTION_TAKEN = new EffectEvent.Type<>(Mino.id("seat_action_taken"), SeatActionTakenEffectEvent.STREAM_CODEC);

    public static final Map<ResourceLocation, EffectEvent.Type<?>> REGISTRY = Map.of(
            SOUND.id(), SOUND,
            PLAYER_FIREWORK.id(), PLAYER_FIREWORK,
            PLAYER_GLOW.id(), PLAYER_GLOW,
            GRANT_REWARD.id(), GRANT_REWARD,
            SEAT_ACTION_TAKEN.id(), SEAT_ACTION_TAKEN
    );
}
