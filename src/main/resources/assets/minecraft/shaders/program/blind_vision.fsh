#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;

uniform vec2 OutSize;
uniform float Strength;
uniform float Near;
uniform float Far;
uniform float TanHalfFov;
uniform float Aspect;

uniform vec4 Sound0;
uniform vec4 Sound1;
uniform vec4 Sound2;
uniform vec4 Sound3;
uniform vec4 Sound4;
uniform vec4 Sound5;
uniform vec4 Sound6;
uniform vec4 Sound7;
uniform vec4 Sound8;
uniform vec4 Sound9;
uniform vec4 Sound10;
uniform vec4 Sound11;
uniform vec4 Sound12;
uniform vec4 Sound13;
uniform vec4 Sound14;
uniform vec4 Sound15;

uniform vec4 Param0;
uniform vec4 Param1;
uniform vec4 Param2;
uniform vec4 Param3;
uniform vec4 Param4;
uniform vec4 Param5;
uniform vec4 Param6;
uniform vec4 Param7;
uniform vec4 Param8;
uniform vec4 Param9;
uniform vec4 Param10;
uniform vec4 Param11;
uniform vec4 Param12;
uniform vec4 Param13;
uniform vec4 Param14;
uniform vec4 Param15;

in vec2 texCoord;
out vec4 fragColor;

float luma(vec3 c) {
    return dot(c, vec3(0.299, 0.587, 0.114));
}

vec4 soundAt(int i) {
    if (i == 0) return Sound0;
    if (i == 1) return Sound1;
    if (i == 2) return Sound2;
    if (i == 3) return Sound3;
    if (i == 4) return Sound4;
    if (i == 5) return Sound5;
    if (i == 6) return Sound6;
    if (i == 7) return Sound7;
    if (i == 8) return Sound8;
    if (i == 9) return Sound9;
    if (i == 10) return Sound10;
    if (i == 11) return Sound11;
    if (i == 12) return Sound12;
    if (i == 13) return Sound13;
    if (i == 14) return Sound14;
    return Sound15;
}

vec4 paramAt(int i) {
    if (i == 0) return Param0;
    if (i == 1) return Param1;
    if (i == 2) return Param2;
    if (i == 3) return Param3;
    if (i == 4) return Param4;
    if (i == 5) return Param5;
    if (i == 6) return Param6;
    if (i == 7) return Param7;
    if (i == 8) return Param8;
    if (i == 9) return Param9;
    if (i == 10) return Param10;
    if (i == 11) return Param11;
    if (i == 12) return Param12;
    if (i == 13) return Param13;
    if (i == 14) return Param14;
    return Param15;
}

// OpenGL 透视：相机朝 -Z。与 AgentListenStepHandler.worldToScreen 同一套 view 空间。
vec3 reconstructView(vec2 uv, float depth) {
    float zNdc = depth * 2.0 - 1.0;
    float denom = Far + Near - zNdc * (Far - Near);
    float dist = (2.0 * Near * Far) / max(abs(denom), 1e-5);
    vec2 ndc = uv * 2.0 - 1.0;
    return vec3(ndc.x * TanHalfFov * Aspect * dist, ndc.y * TanHalfFov * dist, -dist);
}

float echoVisibility(vec4 sound, vec4 param, vec3 viewPos) {
    float radius = sound.w;
    float fade = clamp(param.x, 0.0, 1.0);
    if (radius < 0.4 || fade < 0.02) {
        return 0.0;
    }
    float dist = distance(viewPos, sound.xyz);
    // 只亮声源附近的表面，边缘放宽以免方块交界频闪。
    float inner = radius * 0.18;
    float vis = 1.0 - smoothstep(inner, radius, dist);
    return vis * vis * fade;
}

void main() {
    vec4 scene = texture(DiffuseSampler, texCoord);
    float s = clamp(Strength, 0.0, 1.0);
    if (s < 0.001) {
        fragColor = scene;
        return;
    }

    ivec2 depthSize = textureSize(DepthSampler, 0);
    ivec2 px = ivec2(gl_FragCoord.xy);
    px = clamp(px, ivec2(0), depthSize - 1);
    float depth = texelFetch(DepthSampler, px, 0).r;
    vec2 uv = (vec2(px) + 0.5) / vec2(depthSize);

    vec3 blinded = vec3(0.0);
    // 天空 / 清空的深度不要亮。近平面塌缩（脸上那团光）也不要亮。
    if (depth > 0.002 && depth < 0.9992) {
        vec3 viewPos = reconstructView(uv, depth);
        float viewDist = length(viewPos);
        if (viewPos.z < -0.35 && viewDist > 0.35 && viewDist < 96.0) {
            float vis = 0.0;
            float linger = 0.0;
            for (int i = 0; i < 16; i++) {
                vec4 sound = soundAt(i);
                vec4 param = paramAt(i);
                float contrib = echoVisibility(sound, param, viewPos);
                vis = vis + contrib - vis * contrib;
                linger = max(linger, contrib * clamp(param.z, 0.0, 1.0));
            }
            float gray = luma(scene.rgb);
            // 把方块本身染白，而不是糊一层屏幕光。
            vec3 whitened = mix(vec3(gray * 0.42), vec3(1.0), 0.58 + linger * 0.32);
            blinded = mix(vec3(0.0), whitened, vis);
        }
    }

    vec3 finalColor = mix(scene.rgb, blinded, s);
    fragColor = vec4(finalColor, scene.a);
}
