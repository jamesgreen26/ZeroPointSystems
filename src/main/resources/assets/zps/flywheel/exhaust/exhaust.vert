#include "zps:exhaust/batch.glsl"

void flw_instanceVertex(in FlwInstance i) {
    ZpsExhaustParticle p;
#ifdef _CLRWL_IS_SHADOW_PASS
    // A shader pack's shadow pass under Colorwheel: translucent smoke would cast solid cubes.
    bool drawn = false;
#else
    bool drawn = zps_exhaustParticle(i.slot, p);
#endif
    if (!drawn) {
        // Collapse the cube to a point: zero-area triangles are dropped before rasterising.
        flw_vertexPos = vec4(0.0, 0.0, 0.0, 1.0);
        flw_vertexTexCoord = vec2(0.0);
        return;
    }
    // One light per particle, from the block its centre is in, like a vanilla particle. The effect
    // tracks every section a plume can reach, so a miss means a bug and shows as black.
    vec2 light;
    if (!flw_lightFetch(ivec3(floor(p.position)) + flw_renderOrigin, light)) {
        light = vec2(0.0);
    }
    flw_vertexPos = vec4(p.position + p.rotation * flw_vertexPos.xyz * p.size, 1.0);
    flw_vertexNormal = p.rotation * flw_vertexNormal;
    flw_vertexColor = p.color;
    flw_vertexLight = light;
    // Read back by shade.glsl.
    flw_vertexTexCoord = vec2(p.heat, 0.0);
}
