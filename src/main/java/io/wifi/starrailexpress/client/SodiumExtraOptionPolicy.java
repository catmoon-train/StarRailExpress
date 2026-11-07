package io.wifi.starrailexpress.client;

import me.flashyreese.mods.sodiumextra.client.config.SodiumExtraGameOptions;

/** Extra 的受限配置同时约束磁盘加载、保存及运行读取，不让隐藏按钮成为唯一保护。 */
public final class SodiumExtraOptionPolicy {
    private SodiumExtraOptionPolicy() { }

    public static SodiumExtraGameOptions enforce(SodiumExtraGameOptions options) {
        // 构造与 sanitize 完成后调用；不替换分组对象，保持配置页面捕获的引用有效。
        var animation = options.animationSettings;
        animation.animation = animation.water = animation.lava = animation.fire = animation.portal = true;
        animation.blockAnimations = animation.sculkSensor = true;
        var particles = options.particleSettings;
        particles.particles = particles.rainSplash = particles.blockBreak = particles.blockBreaking = true;
        // 缺省本就启用；删除拒绝项比每个粒子渲染时重新遍历所有注册 ID 更轻。
        if (!particles.otherMap.isEmpty()) particles.otherMap.clear();
        var details = options.detailSettings;
        details.sky = details.sun = details.moon = details.stars = details.rainSnow = details.biomeColors = details.skyColors = true;
        var render = options.renderSettings;
        render.lightUpdates = render.itemFrame = render.armorStand = render.painting = render.piston = true;
        render.beaconBeam = render.enchantingTableBook = render.itemFrameNameTag = render.playerNameTag = true;
        render.limitBeaconBeamHeight = false;
        var fog = render.fogSettings;
        fog.advanced = fog.multiDimensionFogControl = false;
        fog.atmospheric.distanceChunks = 0;
        fog.atmospheric.startPercent = fog.atmospheric.cloudFogPercent = 100;
        fog.atmospheric.shapeMode = SodiumExtraGameOptions.FogShapeMode.VANILLA;
        if (!fog.dimensionOverrides.isEmpty()) fog.dimensionOverrides.clear();
        var protectedFog = fog.protectedGameplay;
        protectedFog.enabledWhenAllowed = false;
        protectedFog.blindnessDistanceBlocks = protectedFog.darknessDistanceBlocks = protectedFog.lavaDistanceBlocks = 0;
        protectedFog.powderSnowDistanceBlocks = protectedFog.waterDistanceBlocks = 0;
        options.extraSettings.cloudHeightOverride = options.extraSettings.paniniProjection = options.extraSettings.instantSneak = false;
        options.extraSettings.preventShaders = false;
        return options;
    }
}
