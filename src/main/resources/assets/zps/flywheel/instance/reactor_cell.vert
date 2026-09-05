// One wall-touching cell of a fusion reactor. The mesh is a unit cube of six faces on the cell's
// sides; the instance says which of those sides are actually against a wall, and the
// rest are collapsed to a point so they never rasterise.
//
// The fragment stage needs the whole reactor's bounding box, its heat and its noise phase, which
// are identical on every cell of a reactor, plus how deep the cavity is behind this particular
// face. The seed is below one, so it shares a float with the whole-number depth.

// Bit per Minecraft Direction ordinal: down, up, north, south, west, east.
uint zps_faceBit(vec3 n) {
    if (n.y < -0.5) return 1u;
    if (n.y > 0.5) return 2u;
    if (n.z < -0.5) return 4u;
    if (n.z > 0.5) return 8u;
    if (n.x < -0.5) return 16u;
    return 32u;
}

// How far the cavity runs back from this face, in blocks; a byte per direction ordinal.
float zps_faceDepth(uvec2 depths, uint bit) {
    uint index = bit == 1u ? 0u : bit == 2u ? 1u : bit == 4u ? 2u : bit == 8u ? 3u : bit == 16u ? 4u : 5u;
    uint word = index < 4u ? depths.x : depths.y;
    return float((word >> (8u * (index & 3u))) & 255u);
}

void flw_instanceVertex(in FlwInstance i) {
    uint bit = zps_faceBit(flw_vertexNormal);
    bool onWall = (i.faces & bit) != 0u;

    // Faces sit exactly on the cell's sides, so at every edge two faces share a line and nothing
    // else: no strip drawn twice at a concave edge, no notch left open at a convex one.
    flw_vertexPos.xyz = onWall ? flw_vertexPos.xyz + i.pos : i.pos;

    flw_vertexColor = vec4(i.boxMin, i.params.x);                                  // box min, heat
    flw_vertexTexCoord = i.boxMax.xy;                                              // box max x, y
    flw_vertexLight = vec2(i.boxMax.z, zps_faceDepth(i.depths, bit) + i.params.y); // box max z, depth + seed
}
