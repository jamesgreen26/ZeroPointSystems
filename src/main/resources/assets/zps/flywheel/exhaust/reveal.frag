#include "zps:exhaust/shade.glsl"

// Revealage pass of weighted blended transparency. ExhaustLayer blends rgb multiplicatively, so
// the target's red ends up as the product of every fragment's (1 - a): how much of what is behind
// shows through. Alpha is blended by minimum, leaving the nearest exhaust depth for the composite.
// The material has no light and no fog, so these values arrive exactly as written.
void flw_materialFragment() {
    vec4 color = zps_exhaustShade();
    if (color.a < 1.0 / 255.0) {
        discard;
    }
    flw_fragColor = vec4(vec3(color.a), zps_exhaustViewDepth());
}
