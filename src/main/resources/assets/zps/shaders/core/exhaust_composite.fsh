#version 150

// Resolves the exhaust layer over the finished level. See ExhaustLayer.
uniform sampler2D Accum;
uniform sampler2D Reveal;
uniform sampler2D OpaqueDepth;
uniform sampler2D SceneDepth;
uniform mat4 InvProjMat;

out vec4 fragColor;

// How much exhaust shows through a translucent surface in front of it: water, glass, clouds.
// Vanilla blends those straight into the image, so their real opacity is not to be had here.
const float THROUGH_TRANSLUCENT = 0.5;
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

    // Anything else in front of the exhaust now is translucent: drawn after Flywheel and not opaque.
    vec2 ndc = (vec2(pixel) + 0.5) / vec2(textureSize(SceneDepth, 0)) * 2.0 - 1.0;
    vec4 view = InvProjMat * vec4(ndc, texelFetch(SceneDepth, pixel, 0).r * 2.0 - 1.0, 1.0);
    if (nearest > -view.z / view.w + DEPTH_EPSILON) {
        coverage *= THROUGH_TRANSLUCENT;
    }

    vec4 accum = texelFetch(Accum, pixel, 0);
    fragColor = vec4(accum.rgb / max(accum.a, 1e-5), coverage);
}
