package io.wifi.starrailexpress.client.animation;

import java.util.ArrayList;
import java.util.List;

import org.joml.Vector3f;

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
                  Interpolations.EASE_OUT_EXPO)))
      .build();
  public static final AnimationDefinition CLOSE = AnimationDefinition.Builder.withLength(1f)
      .addAnimation("Door",
          new AnimationChannel(AnimationChannel.Targets.POSITION,
              new Keyframe(0.1f, KeyframeAnimations.posVec(14f, 0f, 0f),
                  Interpolations.EASE_OUT_EXPO),
              new Keyframe(0.7f, KeyframeAnimations.posVec(0f, 0f, 0f),
                  Interpolations.EASE_OUT_EXPO)))
      .build();

  public static final AnimationDefinition UP_OPEN = AnimationDefinition.Builder.withLength(1f)
      .addAnimation("UpDoor",
          new AnimationChannel(AnimationChannel.Targets.POSITION,
              new Keyframe(0.1f, KeyframeAnimations.posVec(0f, 0f, 0f),
                  Interpolations.EASE_OUT_EXPO),
              new Keyframe(0.7f, KeyframeAnimations.posVec(0f, 30f, 0f),
                  Interpolations.EASE_OUT_EXPO)))
      .build();
  public static final AnimationDefinition UP_CLOSE = AnimationDefinition.Builder.withLength(1f)
      .addAnimation("UpDoor",
          new AnimationChannel(AnimationChannel.Targets.POSITION,
              new Keyframe(0.1f, KeyframeAnimations.posVec(0f, 30f, 0f),
                  Interpolations.EASE_OUT_EXPO),
              new Keyframe(0.7f, KeyframeAnimations.posVec(0f, 0f, 0f),
                  Interpolations.EASE_OUT_EXPO)))
      .build();
  // 开关门动态生成动画（1/4圆弧，修正凹凸性，加速版）
  public static final AnimationDefinition PLANE_OPEN;
  public static final AnimationDefinition PLANE_CLOSE;

  static {
    int keyframeCount = 16; // 关键帧数量
    float duration = 0.6f; // 动画时长（秒），比原来快
    float startX = 0f, startZ = 0f;
    float endX = 14f, endZ = 9f;

    List<Keyframe> openKeyframes = new ArrayList<>();
    for (int i = 0; i < keyframeCount; i++) {
      float t = i / (float) (keyframeCount - 1); // 0..1
      float angle = (float) (Math.PI / 2 * t); // 0 到 90度
      // 修正凹凸性：先向外 (Z) 再向右 (X)
      float x = startX + (endX - startX) * (float) (1 - Math.cos(angle));
      float z = startZ + (endZ - startZ) * (float) Math.sin(angle);
      // 测试：t=0.5时，x≈0.2929*endX≈4.1，z≈0.7071*endZ≈4.95 → 先向外后向右
      Vector3f pos = KeyframeAnimations.posVec(x, 0f, z);
      float time = i * (duration / (keyframeCount - 1));
      openKeyframes.add(new Keyframe(time, pos, Interpolations.LINEAR));
    }

    // 关门动画（逆序）
    List<Keyframe> closeKeyframes = new ArrayList<>();
    for (int i = keyframeCount - 1; i >= 0; i--) {
      Keyframe kf = openKeyframes.get(i);
      float time = (keyframeCount - 1 - i) * (duration / (keyframeCount - 1));
      closeKeyframes.add(new Keyframe(time, kf.target(), Interpolations.LINEAR));
    }

    AnimationDefinition.Builder openBuilder = AnimationDefinition.Builder.withLength(duration);
    AnimationDefinition.Builder closeBuilder = AnimationDefinition.Builder.withLength(duration);

    openBuilder.addAnimation("PlaneDoor",
        new AnimationChannel(AnimationChannel.Targets.POSITION, openKeyframes.toArray(new Keyframe[0])));
    closeBuilder.addAnimation("PlaneDoor",
        new AnimationChannel(AnimationChannel.Targets.POSITION, closeKeyframes.toArray(new Keyframe[0])));

    PLANE_OPEN = openBuilder.build();
    PLANE_CLOSE = closeBuilder.build();
  }
}
