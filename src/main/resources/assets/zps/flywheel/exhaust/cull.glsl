#include "zps:exhaust/batch.glsl"

// Culls by the whole batch's plume: a single particle's place costs as much as drawing it.
void flw_transformBoundingSphere(in FlwInstance i, inout vec3 center, inout float radius) {
#ifdef _CLRWL_IS_SHADOW_PASS
    // Colorwheel's shadow culling: exhaust casts no shadow (see exhaust.vert).
    bool drawn = false;
#else
    bool drawn = zps_exhaustBatchBounds(i.slot, center, radius);
#endif
    if (!drawn) {
        center = vec3(1e9);
        radius = 0.0;
    }
}
