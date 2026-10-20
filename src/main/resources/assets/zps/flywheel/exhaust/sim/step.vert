// One substep of exhaust physics, for every particle slot at once: read from one state buffer
// as attributes, written to another by transform feedback (ExhaustSimulation). A slot whose
// batch has just been born into is started at the nozzle; a live one is moved under drag, lift,
// swirl and, once cooled, the push of crowding, then collided against the terrain's sub-cells,
// the world's and then those of any moving grid near it (ExhaustGrids).

#include "zps:exhaust/common.glsl"
#include "zps:exhaust/sim/field.glsl"

layout(location = 0) in vec4 zps_inPosition;
layout(location = 1) in vec4 zps_inVelocity;
layout(location = 2) in uvec4 zps_inMeta;

out vec4 zps_outPosition;
out vec4 zps_outVelocity;
flat out uvec4 zps_outMeta;

/// The tick being simulated: the last substep ends exactly at it.
uniform uint zps_tick;
/// When this substep starts and how long it is, in ticks, relative to zps_tick.
uniform float zps_stepStart;
uniform float zps_stepLength;

// Collisions: how much of the speed into a surface comes back out, and how much speed along it
// each unit of that impact costs (Coulomb friction).
const float RESTITUTION = 0.2;
const float FRICTION = 0.3;
// The push of crowding, in blocks/tick^2 per particle-per-block of gradient, and its cap.
const float PRESSURE = 0.0012;
const float MAX_PUSH = 0.06;
// Swirl: a slow wander that grows as the smoke ages.
const float SWIRL = 0.015;
// Longest single move before collision is checked again: under a sub-cell, so nothing tunnels.
const float MAX_MOVE = 0.2;
const float SKIN = 0.001;

// Moving grids (Sable sublevels) near the plumes, laid out by ExhaustGrids: texel 0 the count,
// then per grid the anchor, the bounds' corners (relative to the simulation origin), and for each
// substep boundary the world to grid transform's columns and then the grid to world one's. A
// grid's block space is taken relative to its anchor, as plot positions are too far out for floats.
uniform usamplerBuffer zps_grids;
/// Which substep this is, so which pair of boundaries it runs between.
uniform int zps_gridSubstep;
const int ZPS_GRID_TEXELS = 3 + 4 * 8;

int zps_gridBase(int g) {
    return 1 + g * ZPS_GRID_TEXELS;
}

vec3 zps_gridVec(int texel) {
    return uintBitsToFloat(texelFetch(zps_grids, texel).xyz);
}

/// A grid's transform at a substep boundary: world to grid, or grid to world.
mat4 zps_gridMatrix(int g, int boundary, bool toWorld) {
    int at = zps_gridBase(g) + 3 + boundary * 8 + (toWorld ? 4 : 0);
    return mat4(uintBitsToFloat(texelFetch(zps_grids, at)), uintBitsToFloat(texelFetch(zps_grids, at + 1)),
            uintBitsToFloat(texelFetch(zps_grids, at + 2)), uintBitsToFloat(texelFetch(zps_grids, at + 3)));
}

/// Moves a particle by its velocity for dt, one axis at a time so it slides along whatever it
/// hits, in pieces short enough not to pass through a sub-cell.
void zps_move(inout vec3 position, inout vec3 velocity, float dt, inout uint hits) {
    vec3 travel = velocity * dt;
    float longest = max(abs(travel.x), max(abs(travel.y), abs(travel.z)));
    int pieces = clamp(int(ceil(longest / MAX_MOVE)), 1, 8);
    vec3 piece = travel / float(pieces);
    for (int i = 0; i < pieces; i++) {
        for (int k = 0; k < 3; k++) {
            int axis = k == 0 ? 1 : k == 1 ? 0 : 2; // y first, then x, then z
            if (piece[axis] == 0.0) {
                continue;
            }
            vec3 moved = position;
            moved[axis] += piece[axis];
            if (!zps_solid(moved)) {
                position = moved;
                continue;
            }
            // Stop against the face of the sub-cell entered.
            float entered = floor(moved[axis] * 4.0) / 4.0;
            position[axis] = piece[axis] > 0.0 ? entered - SKIN : entered + 0.25 + SKIN;
            float into = velocity[axis];
            velocity[axis] = -into * RESTITUTION;
            vec3 along = velocity;
            along[axis] = 0.0;
            float alongSpeed = length(along);
            if (alongSpeed > 0.0) {
                float loss = min(alongSpeed, FRICTION * abs(into) * (1.0 + RESTITUTION));
                velocity -= along / alongSpeed * loss;
            }
            piece[axis] = 0.0;
            hits++;
        }
    }
}

/// Collides a particle's move this substep, from start to position, with the moving grids it
/// passes near. In each grid's own block space the move is the particle's motion less the hull's,
/// stepped axis by axis like the world's, so a hull sweeping into smoke catches it as surely as
/// smoke flying into the hull. On contact the bounce and friction act on the velocity relative to
/// the hull where it was touched, so a moving hull carries smoke along.
void zps_collideGrids(vec3 start, inout vec3 position, inout vec3 velocity, float dt, bool newborn, inout uint hits) {
    int count = int(texelFetch(zps_grids, 0).x);
    for (int g = 0; g < count; g++) {
        int base = zps_gridBase(g);
        vec3 low = min(start, position);
        vec3 high = max(start, position);
        if (any(greaterThan(low, zps_gridVec(base + 2))) || any(lessThan(high, zps_gridVec(base + 1)))) {
            continue;
        }
        mat4 fromEnd = zps_gridMatrix(g, zps_gridSubstep + 1, false);
        // A particle born this substep left its nozzle as the hull was by then, near enough its end.
        mat4 fromStart = newborn ? fromEnd : zps_gridMatrix(g, zps_gridSubstep, false);
        vec3 localStart = (fromStart * vec4(start, 1.0)).xyz;
        vec3 localEnd = (fromEnd * vec4(position, 1.0)).xyz;
        ivec3 anchor = ivec3(texelFetch(zps_grids, base).xyz);
        vec3 travel = localEnd - localStart;
        float longest = max(abs(travel.x), max(abs(travel.y), abs(travel.z)));
        int pieces = clamp(int(ceil(longest / MAX_MOVE)), 1, 8);
        vec3 piece = travel / float(pieces);
        vec3 local = localStart;
        vec3 normals = vec3(0.0);
        for (int i = 0; i < pieces; i++) {
            for (int k = 0; k < 3; k++) {
                int axis = k == 0 ? 1 : k == 1 ? 0 : 2;
                if (piece[axis] == 0.0) {
                    continue;
                }
                vec3 moved = local;
                moved[axis] += piece[axis];
                if (!zps_solidAt(anchor, moved)) {
                    local = moved;
                    continue;
                }
                float entered = floor(moved[axis] * 4.0) / 4.0;
                local[axis] = piece[axis] > 0.0 ? entered - SKIN : entered + 0.25 + SKIN;
                normals[axis] = piece[axis] > 0.0 ? -1.0 : 1.0;
                piece[axis] = 0.0;
            }
        }
        if (normals == vec3(0.0)) {
            continue;
        }
        mat4 toEnd = zps_gridMatrix(g, zps_gridSubstep + 1, true);
        position = (toEnd * vec4(local, 1.0)).xyz;
        // How the hull was moving where it was touched: that point at the start and end.
        vec3 hull = (position - (zps_gridMatrix(g, zps_gridSubstep, true) * vec4(local, 1.0)).xyz) / dt;
        vec3 relative = velocity - hull;
        for (int axis = 0; axis < 3; axis++) {
            if (normals[axis] == 0.0) {
                continue;
            }
            vec3 localNormal = vec3(0.0);
            localNormal[axis] = normals[axis];
            vec3 normal = normalize(mat3(toEnd) * localNormal);
            float into = dot(relative, normal);
            if (into >= 0.0) {
                continue;
            }
            relative -= (1.0 + RESTITUTION) * into * normal;
            vec3 along = relative - dot(relative, normal) * normal;
            float alongSpeed = length(along);
            if (alongSpeed > 0.0) {
                relative -= along / alongSpeed * min(alongSpeed, FRICTION * -into * (1.0 + RESTITUTION));
            }
            hits++;
        }
        velocity = relative + hull;
    }
}

void main() {
    zps_outPosition = zps_inPosition;
    zps_outVelocity = zps_inVelocity;
    zps_outMeta = zps_inMeta;

    int slot = gl_VertexID;
    int batchSize = zps_exhaustBatchSize();
    ZpsExhaustBatch b = zps_exhaustBatch(slot / batchSize);
    uint index = uint(slot % batchSize);
    if (index >= b.count) {
        zps_outMeta.y = 0u;
        return;
    }
    float stagger = zps_exhaustStagger(index, b.count);
    float born = float(int(b.spawnTick - zps_tick)) - 1.0 + stagger;
    float end = zps_stepStart + zps_stepLength;
    if (born >= end) {
        // Not born yet: the slot keeps whatever it held, which drawing ignores.
        return;
    }
    float life = zps_exhaustLife(b.seed, index);
    float age = end - born;
    if (age >= life) {
        zps_outMeta = uvec4(b.spawnTick, 0u, zps_inMeta.z, 0u);
        return;
    }

    vec3 position = zps_inPosition.xyz;
    vec3 velocity = zps_inVelocity.xyz;
    uint alive = zps_inMeta.y & 1u;
    uint hits = zps_inMeta.z;
    float dt = zps_stepLength;
    bool newborn = zps_inMeta.x != b.spawnTick;
    if (newborn) {
        // Born during this substep: from the nozzle, for only the part of it since.
        vec3 offset;
        zps_exhaustSpawn(b, index, stagger, offset, velocity);
        position = vec3(b.anchor - zps_exhaustSimOrigin()) + offset;
        dt = end - born;
        alive = 1u;
        hits = 0u;
    }
    if (alive != 0u && zps_solid(position)) {
        // Inside a block: born into one, or one was placed on it.
        alive = 0u;
    }

    if (alive != 0u) {
        float t = age / life;
        uint forces = zps_exhaustStream(b.seed, index, ZPS_EXHAUST_STREAM_FORCES);
        // Everything moving the smoke along is in blocks, so scaled with its grid.
        float lift = ZPS_EXHAUST_BUOYANCY * (0.5 + zps_rand(forces)) * b.scale;
        vec3 phase = vec3(zps_rand(forces), zps_rand(forces), zps_rand(forces)) * 6.2831853;
        vec3 swirl = vec3(sin(age * 0.15 + phase.x), 0.5 * sin(age * 0.13 + phase.y), sin(age * 0.17 + phase.z))
                * SWIRL * t * b.scale;
        // Crowding pushes only cooled smoke: the hot jet keeps its shape out of the nozzle.
        vec3 push = -zps_densityGradient(position) * PRESSURE * (1.0 - zps_exhaustHeatAt(t));
        float pushLength = length(push);
        if (pushLength > MAX_PUSH) {
            push *= MAX_PUSH / pushLength;
        }
        push *= b.scale;
        velocity *= exp(-ZPS_EXHAUST_DRAG * dt);
        velocity += (vec3(0.0, lift, 0.0) + swirl + push) * dt;
        vec3 start = position;
        zps_move(position, velocity, dt, hits);
        zps_collideGrids(start, position, velocity, dt, newborn, hits);
    }

    zps_outPosition = vec4(position, 0.0);
    zps_outVelocity = vec4(velocity, 0.0);
    zps_outMeta = uvec4(b.spawnTick, alive, hits, 0u);
}
