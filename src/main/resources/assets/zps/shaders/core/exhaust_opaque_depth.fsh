#version 150

// Copies the depth buffer as it stands once every opaque thing is drawn, as distance along the
// view direction in blocks, for the exhaust composite to hide what sits behind it.
uniform sampler2D SceneDepth;
uniform mat4 InvProjMat;

out vec4 fragColor;

float viewDepth(ivec2 pixel) {
    vec2 ndc = (vec2(pixel) + 0.5) / vec2(textureSize(SceneDepth, 0)) * 2.0 - 1.0;
    vec4 view = InvProjMat * vec4(ndc, texelFetch(SceneDepth, pixel, 0).r * 2.0 - 1.0, 1.0);
    // Sky, where nothing wrote depth: with a far plane at infinity, as Genesis sets one, the
    // cleared depth lies past it and would come back negative.
    if (view.w <= 0.0) {
        return 1e30;
    }
    return -view.z / view.w;
}

void main() {
    fragColor = vec4(viewDepth(ivec2(gl_FragCoord.xy)), 0.0, 0.0, 1.0);
}
