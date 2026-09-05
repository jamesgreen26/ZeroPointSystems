// One wall-touching cell of a fusion reactor. The mesh is a unit cube of six faces just inside
// the cell's sides; the instance says which of those sides are actually against a wall, and the
// rest are collapsed to a point so they never rasterise.
//
// The fragment stage needs the whole reactor's bounding box, its heat and its noise phase. They
// are identical on every cell of a reactor, so they ride in the varyings unchanged.

// Bit per Minecraft Direction ordinal: down, up, north, south, west, east.
uint zps_faceBit(vec3 n) {
    if (n.y < -0.5) return 1u;
    if (n.y > 0.5) return 2u;
    if (n.z < -0.5) return 4u;
    if (n.z > 0.5) return 8u;
    if (n.x < -0.5) return 16u;
    return 32u;
}

void flw_instanceVertex(in FlwInstance i) {
    bool onWall = (i.faces & zps_faceBit(flw_vertexNormal)) != 0u;

    flw_vertexPos.xyz = onWall ? flw_vertexPos.xyz + i.pos : i.pos;

    flw_vertexColor = vec4(i.boxMin, i.params.x);          // box min, heat
    flw_vertexTexCoord = i.boxMax.xy;                      // box max x, y
    flw_vertexLight = vec2(i.boxMax.z, i.params.y);        // box max z, seed
}
