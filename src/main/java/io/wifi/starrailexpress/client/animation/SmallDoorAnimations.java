package io.wifi.starrailexpress.client.animation;

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;

public class SmallDoorAnimations {
    public static final AnimationDefinition OPEN = AnimationDefinition.Builder.withLength(1f)
            .addAnimation("Door",
                    new AnimationChannel(AnimationChannel.Targets.POSITION,
                            new Keyframe(0.1f, KeyframeAnimations.posVec(0f, 0f, 0f),
                                    Interpolations.EASE_OUT_EXPO),
                            new Keyframe(0.7f, KeyframeAnimations.posVec(14f, 0f, 0f),
                                    Interpolations.EASE_OUT_EXPO))).build();
    public static final AnimationDefinition CLOSE = AnimationDefinition.Builder.withLength(1f)
            .addAnimation("Door",
                    new AnimationChannel(AnimationChannel.Targets.POSITION,
                            new Keyframe(0.1f, KeyframeAnimations.posVec(14f, 0f, 0f),
                                    Interpolations.EASE_OUT_EXPO),
                            new Keyframe(0.7f, KeyframeAnimations.posVec(0f, 0f, 0f),
                                    Interpolations.EASE_OUT_EXPO))).build();
}
