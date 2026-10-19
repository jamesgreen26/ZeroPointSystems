#include "zps:exhaust/shade.glsl"

// Accumulation pass of weighted blended transparency (McGuire and Bavoil 2013). ExhaustLayer
// blends this additively, colour scaled by alpha, so the target sums rgb * a * w and a * w.
// Light and fog are applied after this by Flywheel, to the colour alone.
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
}
