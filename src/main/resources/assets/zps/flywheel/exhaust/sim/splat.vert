// Counts particles per block into the field's density (ExhaustSimulation), cloud-in-cell: each
// particle is drawn as eight points, one per nearest block centre, weighted trilinearly by how
// close it is. Drawn instanced: the instance is the particle, the vertex the corner. Counting a
// particle wholly into its own block would make the density a step function, whose gradient
// favours the cell diagonals; spread like this, the push of a pile comes out radial.
// Points outside the field are dropped by clipping.

#include "zps:exhaust/common.glsl"
#include "zps:exhaust/sim/field.glsl"

// Per instance (attribute divisor 1): one particle's state.
layout(location = 0) in vec4 zps_inPosition;
layout(location = 2) in uvec4 zps_inMeta;

/// What one particle counts for: particles per tick are normalised, so the push of crowding is
/// the same whatever the configured rate.
uniform float zps_splatWeight;

flat out float zps_weight;

void main() {
    zps_weight = 0.0;
    gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
    int slot = gl_InstanceID;
    int batchSize = zps_exhaustBatchSize();
    ZpsExhaustBatch b = zps_exhaustBatch(slot / batchSize);
    if (uint(slot % batchSize) >= b.count || zps_inMeta.x != b.spawnTick || (zps_inMeta.y & 1u) == 0u) {
        return;
    }
    vec3 cell = zps_inPosition.xyz - 0.5;
    ivec3 corner = ivec3(gl_VertexID & 1, (gl_VertexID >> 1) & 1, (gl_VertexID >> 2) & 1);
    vec3 t = fract(cell);
    vec3 weights = mix(1.0 - t, t, vec3(corner));
    ivec3 block = zps_exhaustSimOrigin() + ivec3(floor(cell)) + corner;
    ivec3 section = zps_floorDiv16(block);
    int fieldSlot = zps_fieldSlot(section);
    if (fieldSlot < 0) {
        return;
    }
    zps_weight = zps_splatWeight * weights.x * weights.y * weights.z;
    vec2 texel = vec2(zps_densityTexel(fieldSlot, block - section * 16)) + 0.5;
    gl_Position = vec4(texel / vec2(ZPS_FIELD_DENSITY_SIZE) * 2.0 - 1.0, 0.0, 1.0);
}
