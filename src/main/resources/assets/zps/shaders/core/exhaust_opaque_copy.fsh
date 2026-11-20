#version 330

// Copies the image as it stands once every opaque thing is drawn, for the exhaust composite: the
// depth as distance along the view direction in blocks, to hide exhaust behind it, and the colour,
// to take out of the finished image where exhaust goes under translucent surfaces. Both in one
// pass, as each pass away from the main target costs a store and reload of it on a tiled GPU.
uniform sampler2D SceneDepth;
uniform sampler2D SceneColor;
uniform mat4 InvProjMat;

layout(location = 0) out vec4 opaqueDepth;
layout(location = 1) out vec4 opaqueColor;

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
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    opaqueDepth = vec4(viewDepth(pixel), 0.0, 0.0, 1.0);
    opaqueColor = texelFetch(SceneColor, pixel, 0);
}
