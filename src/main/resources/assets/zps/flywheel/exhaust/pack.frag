#include "zps:exhaust/shade.glsl"

// Under a shader pack (Colorwheel), exhaust is plain translucency: the pack lights, fogs and
// composites it like any other translucent Flywheel geometry, so this is called from inside the
// pack's own fragment shader. Fire is lit as full light; how bright that is is the pack's call.
void flw_materialFragment() {
    vec4 color = zps_exhaustShade();
    if (color.a < 1.0 / 255.0) {
        discard;
    }
    flw_fragColor = color;
    flw_fragLight = mix(flw_fragLight, vec2(1.0), zps_exhaustHeat());
}
