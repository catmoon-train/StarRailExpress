#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;

uniform vec2 OutSize;
uniform float EffectStrength;
uniform float Near;
uniform float Far;
uniform float TanHalfFov;
uniform float Aspect;
uniform float ScreenBrightness;

uniform vec4 Sound0;
uniform vec4 Param0;
uniform vec4 Sound1;
uniform vec4 Param1;
uniform vec4 Sound2;
uniform vec4 Param2;
uniform vec4 Sound3;
uniform vec4 Param3;
uniform vec4 Sound4;
uniform vec4 Param4;
uniform vec4 Sound5;
uniform vec4 Param5;
uniform vec4 Sound6;
uniform vec4 Param6;
uniform vec4 Sound7;
uniform vec4 Param7;
uniform vec4 Sound8;
uniform vec4 Param8;
uniform vec4 Sound9;
uniform vec4 Param9;
uniform vec4 Sound10;
uniform vec4 Param10;
uniform vec4 Sound11;
uniform vec4 Param11;
uniform vec4 Sound12;
uniform vec4 Param12;
uniform vec4 Sound13;
uniform vec4 Param13;
uniform vec4 Sound14;
uniform vec4 Param14;
uniform vec4 Sound15;
uniform vec4 Param15;

in vec2 texCoord;
out vec4 fragColor;

vec3 reconstructView(vec2 uv, float depth) {
    float zNdc = depth * 2.0 - 1.0;
    float denominator = Far + Near - zNdc * (Far - Near);
    float distanceToSurface = (2.0 * Near * Far) / max(abs(denominator), 0.00001);
    vec2 ndc = uv * 2.0 - 1.0;
    return vec3(ndc.x * TanHalfFov * Aspect * distanceToSurface,
            ndc.y * TanHalfFov * distanceToSurface, -distanceToSurface);
}

float surfaceDistance(vec2 uv, float depth) {
    return length(reconstructView(uv, depth));
}

float sceneEdge(vec2 uv) {
    ivec2 size = textureSize(DepthSampler, 0);
    vec2 step = 1.0 / vec2(size);
    float leftDepth = surfaceDistance(uv - vec2(step.x, 0.0), texture(DepthSampler, uv - vec2(step.x, 0.0)).r);
    float rightDepth = surfaceDistance(uv + vec2(step.x, 0.0), texture(DepthSampler, uv + vec2(step.x, 0.0)).r);
    float upDepth = surfaceDistance(uv + vec2(0.0, step.y), texture(DepthSampler, uv + vec2(0.0, step.y)).r);
    float downDepth = surfaceDistance(uv - vec2(0.0, step.y), texture(DepthSampler, uv - vec2(0.0, step.y)).r);
    float depthEdge = abs(leftDepth - rightDepth) + abs(upDepth - downDepth);

    vec3 leftColor = texture(DiffuseSampler, uv - vec2(step.x, 0.0)).rgb;
    vec3 rightColor = texture(DiffuseSampler, uv + vec2(step.x, 0.0)).rgb;
    vec3 upColor = texture(DiffuseSampler, uv + vec2(0.0, step.y)).rgb;
    vec3 downColor = texture(DiffuseSampler, uv - vec2(0.0, step.y)).rgb;
    float colorEdge = length(leftColor - rightColor) + length(upColor - downColor);
    return clamp(depthEdge * 0.8 + colorEdge * 0.7, 0.0, 1.0);
}

vec4 soundAt(int index) {
    if (index == 0) return Sound0;
    if (index == 1) return Sound1;
    if (index == 2) return Sound2;
    if (index == 3) return Sound3;
    if (index == 4) return Sound4;
    if (index == 5) return Sound5;
    if (index == 6) return Sound6;
    if (index == 7) return Sound7;
    if (index == 8) return Sound8;
    if (index == 9) return Sound9;
    if (index == 10) return Sound10;
    if (index == 11) return Sound11;
    if (index == 12) return Sound12;
    if (index == 13) return Sound13;
    if (index == 14) return Sound14;
    return Sound15;
}

vec4 paramAt(int index) {
    if (index == 0) return Param0;
    if (index == 1) return Param1;
    if (index == 2) return Param2;
    if (index == 3) return Param3;
    if (index == 4) return Param4;
    if (index == 5) return Param5;
    if (index == 6) return Param6;
    if (index == 7) return Param7;
    if (index == 8) return Param8;
    if (index == 9) return Param9;
    if (index == 10) return Param10;
    if (index == 11) return Param11;
    if (index == 12) return Param12;
    if (index == 13) return Param13;
    if (index == 14) return Param14;
    return Param15;
}

void main() {
    vec4 scene = texture(DiffuseSampler, texCoord);
    float depth = texture(DepthSampler, texCoord).r;
    if (EffectStrength < 0.001) {
        fragColor = scene;
        return;
    }
    if (depth <= 0.002 || depth >= 0.9992) {
        fragColor = vec4(0.0, 0.0, 0.0, scene.a);
        return;
    }

    vec3 viewPosition = reconstructView(texCoord, depth);
    float viewDistance = length(viewPosition);
    if (viewPosition.z > -0.35 || viewDistance <= 0.35 || viewDistance >= 96.0) {
        fragColor = vec4(0.0, 0.0, 0.0, scene.a);
        return;
    }

    float outline = sceneEdge(texCoord);
    float colorFade = 0.0;
    float colorOutline = 0.0;
    float colorBlindness = 0.0;

    // The Forge shader uses a std140 array of 384 Source records. The portable
    // Fabric path keeps the most recent 16 records in ordinary vec4 uniforms.
    for (int i = 0; i < 16; i++) {
        vec4 source = soundAt(i);
        // Param = (unused padding, volume, age, random), matching Forge's cfg.
        vec4 config = paramAt(i);
        if (source.w <= 0.0 || config.y <= 0.0) {
            continue;
        }

        float fade = distance(source.xyz, viewPosition) / source.w;
        if (fade <= 1.0) {
            float agedFade = clamp(1.0 - ((1.0 - fade * fade) - config.z) * min(0.75, config.y), 0.0, 1.0);
            float fadeOutline = agedFade * agedFade * agedFade + 0.01;
            agedFade += 0.01;
            colorOutline += clamp(1.0 - fadeOutline, 0.0, 1.0) * outline;
            colorFade += clamp(1.0 - agedFade, 0.0, 1.0);
        }

        // Sounds within eight blocks expose the nearby body more strongly.
        float sourceDistance = length(source.xyz);
        if (sourceDistance < 8.0) {
            fade = distance(source.xyz, viewPosition) * 2.0 / min(source.w, 8.0);
            if (fade <= 1.0) {
                fade = clamp(1.0 - ((1.0 - fade * fade) - config.z) * config.y, 0.0, 1.0) + 0.01;
                colorBlindness += clamp(1.0 - fade, 0.0, 1.0);
            }
        }
    }

    float color = 1.0 - 1.0 / (colorFade + 1.0) + colorOutline;
    colorBlindness = 1.0 - 1.0 / (colorBlindness + 1.0);
    color = mix(color, colorBlindness, EffectStrength);
    color = pow(min(color, 1.0), 1.0 / (ScreenBrightness + 0.5));
    fragColor = vec4(vec3(color), scene.a);
}
