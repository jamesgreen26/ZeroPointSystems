#include "zps:exhaust/batch.glsl"

// Culls by the whole batch's plume: a single particle's place costs as much as drawing it.
void flw_transformBoundingSphere(in FlwInstance i, inout vec3 center, inout float radius) {
    if (!zps_exhaustBatchBounds(i.slot, center, radius)) {
        center = vec3(1e9);
        radius = 0.0;
    }
}
