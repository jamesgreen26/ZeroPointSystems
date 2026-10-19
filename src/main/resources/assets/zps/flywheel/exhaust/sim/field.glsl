// The exhaust field (ExhaustField): for each chunk section a plume can reach, the terrain at four
// sub-cells a side per block, and how many particles are in each block. Sections live in slots;
// an index maps a section to its slot.
//
//   index    64^3 RGBA32I, one flat hash table of cells, x fastest: slot + 1 (0 for an empty
//            cell, -1 for one emptied that lookups must probe past), then the section's own
//            coordinates. A section is found by linear probing from the hash of its coordinates.
//   terrain  RG32UI, 16x8x8 slots of 16^3 blocks: a 64-bit mask per block, bit x + 4z + 16y
//            set where that sub-cell is solid. x holds bits 0-31, y bits 32-63.
//   density  R16F 2048x2048, 32x32 slots of 64x64 texels: a section's 16 layers of 16x16 blocks
//            laid out four by four. Particles counted per block by ExhaustSimulation's splat.
//
// Positions here are relative to the simulation origin, so blocks are found from their integer
// part and the origin, never from a float world position.

uniform isampler3D zps_fieldIndex;
uniform usampler3D zps_fieldTerrain;
uniform sampler2D zps_fieldDensity;

const int ZPS_FIELD_INDEX = 64;
const int ZPS_FIELD_PROBES = 16;
const ivec2 ZPS_FIELD_DENSITY_SIZE = ivec2(2048, 2048);

/// In integers: plot blocks are around twenty million out, where floats skip odd numbers.
ivec3 zps_floorDiv16(ivec3 v) {
    return v >> 4;
}

/// The cell a section's lookup starts at. Matches ExhaustField.hash.
uint zps_fieldHash(ivec3 section) {
    uvec3 s = uvec3(section);
    uint h = s.x * 0x9E3779B1u ^ s.y * 0x85EBCA77u ^ s.z * 0xC2B2AE3Du;
    h ^= h >> 15u;
    h *= 0x2C1B3C6Du;
    h ^= h >> 12u;
    return h;
}

/// The slot holding a section, or -1 if it is not in the field.
int zps_fieldSlot(ivec3 section) {
    uint hash = zps_fieldHash(section);
    for (int probe = 0; probe < ZPS_FIELD_PROBES; probe++) {
        int cell = int((hash + uint(probe)) & uint(ZPS_FIELD_INDEX * ZPS_FIELD_INDEX * ZPS_FIELD_INDEX - 1));
        ivec4 entry = texelFetch(zps_fieldIndex, ivec3(cell % 64, (cell / 64) % 64, cell / 4096), 0);
        if (entry.x == 0) {
            return -1;
        }
        if (entry.x > 0 && entry.yzw == section) {
            return entry.x - 1;
        }
    }
    return -1;
}

ivec2 zps_densityTexel(int slot, ivec3 local) {
    return ivec2(slot % 32, slot / 32) * 64 + ivec2((local.z % 4) * 16 + local.x, (local.z / 4) * 16 + local.y);
}

/// Whether the sub-cell at a position is solid, the position relative to a block in the same
/// block space: the simulation origin for the world, a grid's anchor for a moving grid's plot.
/// Outside the field everything is air.
bool zps_solidAt(ivec3 origin, vec3 position) {
    ivec3 rel = ivec3(floor(position));
    ivec3 sub = clamp(ivec3(floor((position - vec3(rel)) * 4.0)), ivec3(0), ivec3(3));
    ivec3 block = origin + rel;
    ivec3 section = zps_floorDiv16(block);
    int slot = zps_fieldSlot(section);
    if (slot < 0) {
        return false;
    }
    ivec3 texel = ivec3(slot % 16, (slot / 16) % 8, slot / 128) * 16 + (block - section * 16);
    uvec2 mask = texelFetch(zps_fieldTerrain, texel, 0).xy;
    int bit = sub.x + 4 * sub.z + 16 * sub.y;
    uint word = bit < 32 ? mask.x : mask.y;
    return ((word >> uint(bit & 31)) & 1u) != 0u;
}

/// Whether the world's sub-cell at a position relative to the simulation origin is solid.
bool zps_solid(vec3 position) {
    return zps_solidAt(zps_exhaustSimOrigin(), position);
}

/// Particles in a block, counted at the start of this substep.
float zps_density(ivec3 block) {
    ivec3 section = zps_floorDiv16(block);
    int slot = zps_fieldSlot(section);
    return slot < 0 ? 0.0 : texelFetch(zps_fieldDensity, zps_densityTexel(slot, block - section * 16), 0).r;
}

/// Which way, and how steeply, particles crowd at a position, in particles per block per block:
/// the gradient of the density interpolated by quadratic B-splines over the 3x3x3 nearest block
/// centres. Near a pile its direction is close to straight away from it, at any angle. Linear
/// interpolation is no good for this: its tents multiply into a pyramid whose slope near the top
/// is equal on every axis, so every push came out at 45 degrees; differencing neighbours snapped
/// every push to an axis instead.
vec3 zps_densityGradient(vec3 position) {
    vec3 cell = position - 0.5;
    vec3 nearest = floor(cell + 0.5);
    vec3 d = cell - nearest;  // -0.5 to 0.5 from the nearest centre
    ivec3 base = zps_exhaustSimOrigin() + ivec3(nearest);
    // Weights for the centres at -1, 0 and +1 along each axis, and their slopes.
    vec3 w[3] = vec3[3](0.5 * (0.5 - d) * (0.5 - d), 0.75 - d * d, 0.5 * (0.5 + d) * (0.5 + d));
    vec3 s[3] = vec3[3](d - 0.5, -2.0 * d, d + 0.5);
    vec3 gradient = vec3(0.0);
    for (int k = 0; k < 3; k++) {
        for (int j = 0; j < 3; j++) {
            for (int i = 0; i < 3; i++) {
                float density = zps_density(base + ivec3(i - 1, j - 1, k - 1));
                gradient += density * vec3(
                        s[i].x * w[j].y * w[k].z,
                        w[i].x * s[j].y * w[k].z,
                        w[i].x * w[j].y * s[k].z);
            }
        }
    }
    return gradient;
}
