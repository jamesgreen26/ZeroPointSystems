#version 150

// Resolves the exhaust layer into the finished level. See ExhaustLayer.
uniform sampler2D Accum;
uniform sampler2D Reveal;
uniform sampler2D OpaqueDepth;
uniform sampler2D SceneDepth;
// The image as the level finished it, and as it stood before anything translucent was drawn. The
// finished image's alpha is how much the translucent surfaces drawn over the opaque scene cover,
// as ExhaustLayer zeroed it in between and translucent blending builds it back up.
uniform sampler2D Scene;
uniform sampler2D Opaque;
uniform mat4 InvProjMat;

out vec4 fragColor;

// Slack for comparing depths written by different passes, in blocks.
const float DEPTH_EPSILON = 0.05;

void main() {
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    vec4 reveal = texelFetch(Reveal, pixel, 0);
    float coverage = 1.0 - reveal.r;
    if (coverage < 1.0 / 255.0) {
        discard;
    }
    float nearest = reveal.a;

    // Block entity renderers draw solid things after Flywheel's exhaust passes, which could not
    // test against them.
    if (nearest > texelFetch(OpaqueDepth, pixel, 0).r + DEPTH_EPSILON) {
        discard;
    }

    vec4 accum = texelFetch(Accum, pixel, 0);
    vec3 exhaust = accum.rgb / max(accum.a, 1e-5);
    vec4 scene = texelFetch(Scene, pixel, 0);

    // Anything else in front of the exhaust now is translucent: drawn after Flywheel and not opaque.
    vec2 ndc = (vec2(pixel) + 0.5) / vec2(textureSize(SceneDepth, 0)) * 2.0 - 1.0;
    vec4 view = InvProjMat * vec4(ndc, texelFetch(SceneDepth, pixel, 0).r * 2.0 - 1.0, 1.0);
    if (nearest <= -view.z / view.w + DEPTH_EPSILON) {
        // In front of everything: over the finished image.
        fragColor = vec4(mix(scene.rgb, exhaust, coverage), 1.0);
        return;
    }
    // Behind translucent surfaces: put under them, in place of the part of the opaque scene they
    // let through. The finished image is the surfaces over the opaque scene, so swapping that
    // part for exhaust over the opaque scene is the same as having drawn the exhaust first.
    float through = 1.0 - scene.a;
    vec3 under = texelFetch(Opaque, pixel, 0).rgb;
    fragColor = vec4(scene.rgb + through * coverage * (exhaust - under), 1.0);
}
