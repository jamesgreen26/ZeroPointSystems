// One substep of exhaust physics, for every particle slot at once: read from one state buffer
// as attributes, written to another by transform feedback (ExhaustSimulation). A slot whose
// batch has just been born into is started at the nozzle; a live one is moved under drag, lift,
// swirl and, once cooled, the push of crowding, then collided against the terrain's sub-cells.

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
    if (zps_inMeta.x != b.spawnTick) {
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
        float lift = ZPS_EXHAUST_BUOYANCY * (0.5 + zps_rand(forces));
        vec3 phase = vec3(zps_rand(forces), zps_rand(forces), zps_rand(forces)) * 6.2831853;
        vec3 swirl = vec3(sin(age * 0.15 + phase.x), 0.5 * sin(age * 0.13 + phase.y), sin(age * 0.17 + phase.z))
                * SWIRL * t;
        // Crowding pushes only cooled smoke: the hot jet keeps its shape out of the nozzle.
        vec3 push = -zps_densityGradient(position) * PRESSURE * (1.0 - zps_exhaustHeatAt(t));
        float pushLength = length(push);
        if (pushLength > MAX_PUSH) {
            push *= MAX_PUSH / pushLength;
        }
        velocity *= exp(-ZPS_EXHAUST_DRAG * dt);
        velocity += (vec3(0.0, lift, 0.0) + swirl + push) * dt;
        zps_move(position, velocity, dt, hits);
    }

    zps_outPosition = vec4(position, 0.0);
    zps_outVelocity = vec4(velocity, 0.0);
    zps_outMeta = uvec4(b.spawnTick, alive, hits, 0u);
}
