#include "zps:exhaust/batch.glsl"

out float zps_exhaustHeat;

void flw_instanceVertex(in FlwInstance i) {
    ZpsExhaustParticle p;
    if (!zps_exhaustParticle(i.slot, p)) {
        // Collapse the cube to a point: zero-area triangles are dropped before rasterising.
        flw_vertexPos = vec4(0.0, 0.0, 0.0, 1.0);
        zps_exhaustHeat = 0.0;
        return;
    }
    // One light per particle, from the block its centre is in, like a vanilla particle. The effect
    // tracks every section a plume can reach, so a miss means a bug and shows as black.
    vec2 light;
    if (!flw_lightFetch(ivec3(floor(p.position)) + flw_renderOrigin, light)) {
        light = vec2(0.0);
    }
    flw_vertexPos = vec4(p.position + flw_vertexPos.xyz * p.size, 1.0);
    flw_vertexColor = p.color;
    flw_vertexLight = light;
    zps_exhaustHeat = p.heat;
}
