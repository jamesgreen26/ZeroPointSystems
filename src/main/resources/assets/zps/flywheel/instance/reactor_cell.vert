// One wall-touching cell of a fusion reactor. The mesh is a unit cube of six faces just inside
// the cell's sides; the instance says which of those sides are actually against a wall, and the
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

// Must match ReactorFlywheel.INSET, the distance the mesh already sits off its own wall.
const float INSET = 0.03;

// Pull a face's edge in to the inset where the side beyond that edge is also a wall. Otherwise two
// faces meeting at an edge both run past each other's plane and the strip between them is drawn
// twice. Where the side beyond is open cavity the edge stays on the cell boundary, meeting the
// next cell's face exactly.
float zps_trim(float m, uint faces, uint negativeBit, uint positiveBit) {
    if (m < 0.5 && (faces & negativeBit) != 0u) return INSET;
    if (m > 0.5 && (faces & positiveBit) != 0u) return 1.0 - INSET;
    return m;
}

void flw_instanceVertex(in FlwInstance i) {
    vec3 n = flw_vertexNormal;
    uint bit = zps_faceBit(n);
    bool onWall = (i.faces & bit) != 0u;

    vec3 m = flw_vertexPos.xyz;
    if (abs(n.x) < 0.5) m.x = zps_trim(m.x, i.faces, 16u, 32u);
    if (abs(n.y) < 0.5) m.y = zps_trim(m.y, i.faces, 1u, 2u);
    if (abs(n.z) < 0.5) m.z = zps_trim(m.z, i.faces, 4u, 8u);

    flw_vertexPos.xyz = onWall ? m + i.pos : i.pos;

    flw_vertexColor = vec4(i.boxMin, i.params.x);                                  // box min, heat
    flw_vertexTexCoord = i.boxMax.xy;                                              // box max x, y
    flw_vertexLight = vec2(i.boxMax.z, zps_faceDepth(i.depths, bit) + i.params.y); // box max z, depth + seed
}
