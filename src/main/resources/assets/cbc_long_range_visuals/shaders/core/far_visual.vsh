#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in ivec2 UV2;
uniform sampler2D Sampler2;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat4 ClipToLod;
out vec2 uv;
out vec4 vertexColor;
out vec4 lodClip;

void main() {
    vec4 clip = ProjMat * ModelViewMat * vec4(Position, 1.0);
    lodClip = ClipToLod * clip;
    // Preserve vanilla depth within its frustum. Far effects sit just inside the far plane;
    // the independent Voxy depth comparison below still uses their true world position.
    gl_Position = clip;
    if (clip.w > 0.0) gl_Position.z = min(clip.z, clip.w * 0.999999);
    uv = UV0;
    vertexColor = Color * texelFetch(Sampler2, UV2 / 16, 0);
}
