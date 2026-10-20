// Exhaust particles as drawn: shared by the instance shader and the indirect cull shader.
//
// Where a particle is comes from the simulation (ExhaustSimulation): two state buffers, the state
// at the end of the last simulated tick and the one before, blended by the partial tick as
// vanilla blends an entity's old and new position. How it looks (colour, size, heat, tumble) is
// worked out from its age, as before. See common.glsl for the batch ring.
//
// A state slot is three RGBA32UI texels: position (float bits, relative to the simulation origin),
// velocity (float bits, blocks/tick), and x the spawn tick of the batch the slot was last started
// for, y 1 while alive, z its collision count.

#include "zps:exhaust/common.glsl"

uniform usamplerBuffer zps_exhaustStatePrev;
uniform usamplerBuffer zps_exhaustStateCur;

struct ZpsExhaustParticle {
    vec3 position;  // relative to flw_renderOrigin
    float size;
    mat3 rotation;  // the cube's orientation
    vec4 color;
    float heat;     // 1 white-hot through 0 cold smoke
};

// Particles fade out as they come to fill the view, by size over distance from the camera, and
// are not drawn at all once faded: a cube up against the eye covers much of the screen, and every
// pixel of a translucent particle is paid for, however faint. Whatever their size, none is drawn
// within a block of the camera. Young fire is spared: the fade comes in with age, by fraction of
// life, so it is partial from the first age here and full from the second.
const float ZPS_EXHAUST_NEAR_FADE_FROM_AGE = 0.25;
const float ZPS_EXHAUST_NEAR_FADE_FULL_AGE = 0.5;
const float ZPS_EXHAUST_NEAR_FADE_START = 0.12;
const float ZPS_EXHAUST_NEAR_FADE_END = 0.35;
const float ZPS_EXHAUST_NEAR_CLEAR = 1.0;      // blocks
const float ZPS_EXHAUST_NEAR_CLEAR_FADE = 2.5; // blocks
const float ZPS_EXHAUST_MIN_ALPHA = 0.01;

/// A particle's colour by fraction of life: its batch's fire, then the same smoke for every colour.
/// Cold gas has no fire: a pale puff that thins out sooner than smoke.
vec4 zps_exhaustColor(uint color, float t) {
    if (color == ZPS_EXHAUST_COLD) {
        vec4 c = mix(vec4(0.92, 0.95, 1.00, 0.50), vec4(0.80, 0.82, 0.85, 0.30), smoothstep(0.0, 0.5, t));
        c.a *= 1.0 - smoothstep(0.35, 1.0, t);
        return c;
    }
    vec4 WHITE = vec4(1.00, 0.97, 0.85, 0.90);
    vec4 YELLOW = vec4(1.00, 0.82, 0.32, 0.88);
    vec4 ORANGE = vec4(1.00, 0.42, 0.08, 0.80);
    vec4 EMBER = vec4(0.42, 0.17, 0.08, 0.65);
    if (color == ZPS_EXHAUST_BLUE) {
        WHITE = vec4(0.88, 0.96, 1.00, 0.90);
        YELLOW = vec4(0.45, 0.82, 1.00, 0.88);
        ORANGE = vec4(0.12, 0.38, 1.00, 0.80);
        EMBER = vec4(0.08, 0.12, 0.42, 0.65);
    }
    const vec4 DARK = vec4(0.20, 0.19, 0.18, 0.55);
    const vec4 GREY = vec4(0.42, 0.42, 0.42, 0.40);
    vec4 c = mix(WHITE, YELLOW, smoothstep(0.00, 0.06, t));
    c = mix(c, ORANGE, smoothstep(0.06, 0.15, t));
    c = mix(c, EMBER, smoothstep(0.15, 0.27, t));
    c = mix(c, DARK, smoothstep(0.27, 0.38, t));
    c = mix(c, GREY, smoothstep(0.38, 0.65, t));
    c.a *= 1.0 - smoothstep(0.55, 1.0, t);
    return c;
}

/// The rotation a unit quaternion stands for.
mat3 zps_quaternionRotation(vec4 q) {
    vec3 q2 = q.xyz * 2.0;
    float xx = q.x * q2.x, yy = q.y * q2.y, zz = q.z * q2.z;
    float xy = q.x * q2.y, xz = q.x * q2.z, yz = q.y * q2.z;
    float wx = q.w * q2.x, wy = q.w * q2.y, wz = q.w * q2.z;
    return mat3(
            1.0 - yy - zz, xy + wz, xz - wy,
            xy - wz, 1.0 - xx - zz, yz + wx,
            xz + wy, yz - wx, 1.0 - xx - yy);
}

/// Rotation by angle about a unit axis (Rodrigues).
mat3 zps_rotation(vec3 axis, float angle) {
    float c = cos(angle);
    float s = sin(angle);
    vec3 a = axis * (1.0 - c);
    return mat3(
            a.x * axis.x + c, a.x * axis.y + s * axis.z, a.x * axis.z - s * axis.y,
            a.y * axis.x - s * axis.z, a.y * axis.y + c, a.y * axis.z + s * axis.x,
            a.z * axis.x + s * axis.y, a.z * axis.y - s * axis.x, a.z * axis.z + c);
}

/// Where the batch's plume can be, for culling: false if the batch has nothing alive. A generous
/// estimate from the batch alone; walls only ever keep particles closer in.
bool zps_exhaustBatchBounds(int slot, out vec3 center, out float radius) {
    ZpsExhaustBatch b = zps_exhaustBatch(slot / zps_exhaustBatchSize());
    if (b.count == 0u || flw_ticks - b.spawnTick > ZPS_EXHAUST_MAX_AGE) {
        return false;
    }
    float reach = b.speed / ZPS_EXHAUST_DRAG;
    center = vec3(b.anchor - flw_renderOrigin) + b.offset
            + b.direction * reach * 0.5 + b.emitterVelocity * ZPS_EXHAUST_LIFETIME * 0.5;
    radius = reach + length(b.emitterVelocity) * ZPS_EXHAUST_LIFETIME + b.width + 8.0 * b.scale;
    return true;
}

bool zps_exhaustParticle(int slot, out ZpsExhaustParticle p) {
    int batchSize = zps_exhaustBatchSize();
    ZpsExhaustBatch b = zps_exhaustBatch(slot / batchSize);
    uint index = uint(slot % batchSize);
    uint elapsed = flw_ticks - b.spawnTick;
    if (index >= b.count || elapsed > ZPS_EXHAUST_MAX_AGE) {
        return false;
    }
    float stagger = zps_exhaustStagger(index, b.count);
    float age = float(elapsed) + flw_partialTick - stagger;
    float life = zps_exhaustLife(b.seed, index);
    if (age < 0.0 || age >= life) {
        return false;
    }

    // Not started for this batch yet, or buried by a block: nothing to draw.
    uvec4 cur = texelFetch(zps_exhaustStateCur, slot * 3 + 2);
    if (cur.x != b.spawnTick || (cur.y & 1u) == 0u) {
        return false;
    }
    vec3 curPosition = uintBitsToFloat(texelFetch(zps_exhaustStateCur, slot * 3).xyz);
    vec3 curVelocity = uintBitsToFloat(texelFetch(zps_exhaustStateCur, slot * 3 + 1).xyz);
    // How far from the previous simulated tick to the current one this frame is: the partial
    // tick, or past 1 if a tick went by unsimulated, which then extrapolates.
    float s = float(int(flw_ticks - zps_exhaustSimTick())) + flw_partialTick;
    uvec4 prev = texelFetch(zps_exhaustStatePrev, slot * 3 + 2);
    vec3 position;
    if (prev.x == b.spawnTick && (prev.y & 1u) != 0u) {
        position = mix(uintBitsToFloat(texelFetch(zps_exhaustStatePrev, slot * 3).xyz), curPosition, s);
    } else {
        // Born during the last simulated tick: back along its velocity to where it was.
        position = curPosition + curVelocity * (s - 1.0);
    }
    p.position = vec3(zps_exhaustSimOrigin() - flw_renderOrigin) + position;

    float t = age / life;
    uint rng = zps_exhaustStream(b.seed, index, ZPS_EXHAUST_STREAM_LOOK);
    p.size = mix(0.12, 0.65, sqrt(t)) * (0.7 + 0.6 * zps_rand(rng)) * b.scale;
    float shifted = clamp(t + (zps_rand(rng) - 0.5) * 0.06, 0.0, 1.0);
    // A mixed burn: some of the particles are the unburnt gas, cold, and some burn blue, all through
    // the same plume.
    uint mixRng = zps_exhaustStream(b.seed, index, ZPS_EXHAUST_STREAM_MIX);
    float pick = zps_rand(mixRng);
    uint color = pick < b.coldFraction ? ZPS_EXHAUST_COLD
            : pick < b.coldFraction + b.blueFraction ? ZPS_EXHAUST_BLUE : b.color;
    p.color = zps_exhaustColor(color, shifted);
    p.heat = color == ZPS_EXHAUST_COLD ? 0.0 : zps_exhaustHeatAt(shifted);
    float distance = length(p.position - (flw_cameraPos - vec3(flw_renderOrigin)));
    float nearFade = (1.0 - smoothstep(ZPS_EXHAUST_NEAR_FADE_START, ZPS_EXHAUST_NEAR_FADE_END,
            p.size / max(distance, 1e-4)))
            * smoothstep(ZPS_EXHAUST_NEAR_CLEAR * b.scale, ZPS_EXHAUST_NEAR_CLEAR_FADE * b.scale, distance);
    p.color.a *= mix(1.0, nearFade, smoothstep(ZPS_EXHAUST_NEAR_FADE_FROM_AGE, ZPS_EXHAUST_NEAR_FADE_FULL_AGE, t));
    if (p.color.a < ZPS_EXHAUST_MIN_ALPHA) {
        return false;
    }

    // Square to the grid the batch left, as it was then. Tumbling adds a random axis, starting
    // angle and spin, the spin slowing under the same drag as the motion.
    mat3 grid = zps_quaternionRotation(b.orientation);
    if (!zps_exhaustTumble()) {
        p.rotation = grid;
        return true;
    }
    float z = zps_rand(rng) * 2.0 - 1.0;
    float around = 6.2831853 * zps_rand(rng);
    vec3 axis = vec3(sqrt(1.0 - z * z) * vec2(cos(around), sin(around)), z);
    float spin = ZPS_EXHAUST_SPIN * (zps_rand(rng) * 2.0 - 1.0);
    p.rotation = grid * zps_rotation(axis, 6.2831853 * zps_rand(rng) + spin * zps_exhaustTravel(age));
    return true;
}
