#include "zps:exhaust/shade.glsl"

// Both targets of weighted blended transparency (McGuire and Bavoil 2013) in one draw. ExhaustLayer
// binds them as draw buffers 0 and 1 and blends each its own way (glBlendFuncSeparatei):
//
//   0 accumulation: blended additively, colour scaled by alpha, so it sums rgb * a * w and a * w.
//     This is flw_fragColor, which Flywheel lights and fogs after this function, colour alone.
//   1 revealage: rgb blended multiplicatively, so red ends up as the product of every fragment's
//     (1 - a), how much of what is behind shows through; alpha blended by minimum, leaving the
//     nearest exhaust depth for the composite. Written here, so unlit and unfogged.
//
// Flywheel binds its own output to location 0 before linking, so this one can take location 1.
layout(location = 1) out vec4 zps_exhaustReveal;

void flw_materialFragment() {
    vec4 color = zps_exhaustShade();
    if (color.a < 1.0 / 255.0) {
        discard;
    }
    // Equation 7 of the paper: nearer fragments count for more, so a near plume is not averaged
    // away by a far one behind it.
    float z = zps_exhaustViewDepth();
    float weight = clamp(10.0 / (1e-5 + pow(z / 5.0, 2.0) + pow(z / 200.0, 6.0)), 1e-2, 3e3);
    flw_fragColor = vec4(color.rgb, color.a * weight);
    flw_fragLight = mix(flw_fragLight, vec2(1.0), zps_exhaustHeat);
    zps_exhaustReveal = vec4(vec3(color.a), z);
}
