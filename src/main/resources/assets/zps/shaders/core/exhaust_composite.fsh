#version 330

// Resolves the exhaust layer into the finished level, by blending into it rather than reading a
// copy of it: a copy is a store and reload of the whole image on a tiled GPU. See ExhaustLayer.
// The image's alpha is how much the translucent surfaces drawn over the opaque scene cover, as
// ExhaustLayer zeroed it in between and translucent blending builds it back up.
//
// Each pixel is one of two cases, and ExhaustLayer draws this three times, each pass writing only
// its own case's pixels with its own blending. None of the passes changes the image's alpha.
//
// Which case a pixel is, the depth test decides: each pass writes the nearest exhaust's depth, and
// pass 0 tests LEQUAL against the depth buffer, passes 1 and 2 GREATER. Opaque things in front are
// discarded first, from the copy, so whatever is left in front in the depth buffer is translucent.
// The depth buffer cannot be sampled for this instead: it is the target's own, and on a tiled GPU
// what translucent surfaces wrote to it is still in tile memory, not in the texture.
//
//   0 in front of everything: over the finished image. Exhaust colour and coverage, blended by
//     source alpha, so mix(image, exhaust, coverage).
//   1 behind translucent surfaces, first: the opaque scene those surfaces let through, by
//     coverage, scaled by (1 - image alpha) and reverse-subtracted from the image.
//   2 behind translucent surfaces, second: exhaust colour by coverage, scaled the same and added.
//
// Together 1 and 2 put the exhaust under the surfaces in place of that much opaque scene:
// image + (1 - a) * coverage * (exhaust - opaque). Subtracting first keeps the image in range
// between the two: it holds at least (1 - a) * opaque, more than is taken away, whereas adding
// first could clip at white and lose what the subtraction then takes off.
uniform sampler2D Accum;
uniform sampler2D Reveal;
uniform sampler2D OpaqueDepth;
// The image as it stood before anything translucent was drawn.
uniform sampler2D Opaque;
// The level's projection. Not ProjMat, which vanilla sets to its own on every draw.
uniform mat4 ExhaustProjMat;
uniform int Pass;

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

    // The nearest exhaust, brought forward by the slack, as the depth buffer holds depth. The sky,
    // cleared to the far end, is behind any of it, also past an infinite far plane.
    vec4 clip = ExhaustProjMat * vec4(0.0, 0.0, -(nearest - DEPTH_EPSILON), 1.0);
    gl_FragDepth = clamp(clip.z / clip.w * 0.5 + 0.5, 0.0, 1.0);

    if (Pass == 1) {
        fragColor = vec4(coverage * texelFetch(Opaque, pixel, 0).rgb, 0.0);
        return;
    }
    vec4 accum = texelFetch(Accum, pixel, 0);
    vec3 exhaust = accum.rgb / max(accum.a, 1e-5);
    fragColor = Pass == 0 ? vec4(exhaust, coverage) : vec4(coverage * exhaust, 0.0);
}
