#version 150

uniform sampler2D Sampler0;

uniform sampler2D LodDepth;
uniform usampler2D LodStencil;
uniform int LodEnabled;
uniform int LodReverseZ;
uniform int LodZeroOne;
in vec2 uv;
in vec4 vertexColor;
in vec4 lodClip;
out vec4 fragColor;

void main() {
    bool texturedSmoke = uv.x > 1.5;
    vec4 sprite = texturedSmoke ? texture(Sampler0, uv - vec2(2.0, 0.0)) : vec4(1.0);
    float alpha = texturedSmoke ? sprite.a : 1.0 - smoothstep(0.08, 1.0, length(uv * 2.0 - 1.0));
    if (alpha * vertexColor.a < 0.004) discard;
    if (LodEnabled != 0 && lodClip.w > 0.000001) {
        vec3 ndc = lodClip.xyz / lodClip.w;
        vec2 screenUv = ndc.xy * 0.5 + 0.5;
        float objectDepth = LodZeroOne != 0 ? ndc.z : ndc.z * 0.5 + 0.5;
        if (all(greaterThanEqual(screenUv, vec2(0.0))) && all(lessThan(screenUv, vec2(1.0)))
                && objectDepth >= 0.0 && objectDepth <= 1.0) {
            ivec2 texel = ivec2(screenUv * vec2(textureSize(LodDepth, 0)));
            uint stencil = texelFetch(LodStencil, texel, 0).r;
            if ((stencil & 1u) != 0u) {
                float depth = texelFetch(LodDepth, texel, 0).r;
                bool validDepth = LodReverseZ != 0 ? depth > 0.000001 : depth < 0.999999;
                bool behind = LodReverseZ != 0 ? objectDepth < depth - 0.000001 : objectDepth > depth + 0.000001;
                if (validDepth && behind) discard;
            }
        }
    }
    fragColor = vec4(vertexColor.rgb * sprite.rgb, vertexColor.a * alpha);
}
