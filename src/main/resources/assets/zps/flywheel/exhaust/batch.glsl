// Exhaust particles, shared by the instance shader and the indirect cull shader.
//
// A batch is the 64 particles one thruster emits in one tick. Its record is four RGBA32UI texels
// written by ExhaustParticles; every particle in it is worked out from that record, its index and
// the time, so nothing per particle is stored anywhere. Physics would replace zps_exhaustParticle's
// closed-form motion with a read from a state buffer stepped on the GPU; the record stays as the
// spawn data.
//
//   t0  anchor block xyz (int)            spawn tick (LevelRenderer ticks)
//   t1  nozzle offset from anchor (float) seed
//   t2  exhaust direction (float, unit)   count
//   t3  emitter velocity, blocks/tick     exhaust speed, blocks/tick (float)

uniform usamplerBuffer zps_exhaustBatches;

// Must match ExhaustParticles.BATCH_SIZE and MAX_LIFETIME_TICKS, whose plume bounds also assume
// the drag, buoyancy and spread below.
const int ZPS_EXHAUST_BATCH_SIZE = 64;
const float ZPS_EXHAUST_LIFETIME = 40.0;
const uint ZPS_EXHAUST_MAX_AGE = 64u;

const float ZPS_EXHAUST_DRAG = 0.1;       // per tick
const float ZPS_EXHAUST_BUOYANCY = 0.004; // blocks/tick^2
const float ZPS_EXHAUST_SPREAD = 0.16;    // cone radius per unit of travel
const float ZPS_EXHAUST_NOZZLE = 0.25;    // spawn jitter across the nozzle, blocks

struct ZpsExhaustParticle {
    vec3 position;  // relative to flw_renderOrigin
    float size;
    vec4 color;
    float heat;     // 1 white-hot through 0 cold smoke
};

uint zps_hash(uint x) {
    x ^= x >> 16;
    x *= 0x7feb352du;
    x ^= x >> 15;
    x *= 0x846ca68bu;
    x ^= x >> 16;
    return x;
}

float zps_rand(inout uint state) {
    state = zps_hash(state);
    return float(state >> 8) * (1.0 / 16777216.0);
}

vec4 zps_exhaustColor(float t) {
    const vec4 WHITE = vec4(1.00, 0.97, 0.85, 0.90);
    const vec4 YELLOW = vec4(1.00, 0.82, 0.32, 0.88);
    const vec4 ORANGE = vec4(1.00, 0.42, 0.08, 0.80);
    const vec4 EMBER = vec4(0.42, 0.17, 0.08, 0.65);
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

/// Where the batch's plume sits, for culling: false if the batch has nothing alive.
bool zps_exhaustBatchBounds(int slot, out vec3 center, out float radius) {
    int base = (slot / ZPS_EXHAUST_BATCH_SIZE) * 4;
    uvec4 t0 = texelFetch(zps_exhaustBatches, base);
    uvec4 t1 = texelFetch(zps_exhaustBatches, base + 1);
    uvec4 t2 = texelFetch(zps_exhaustBatches, base + 2);
    uvec4 t3 = texelFetch(zps_exhaustBatches, base + 3);
    if (t2.w == 0u || flw_ticks - t0.w > ZPS_EXHAUST_MAX_AGE) {
        return false;
    }
    float reach = uintBitsToFloat(t3.w) / ZPS_EXHAUST_DRAG;
    vec3 velocity = uintBitsToFloat(t3.xyz);
    center = vec3(ivec3(t0.xyz) - flw_renderOrigin) + uintBitsToFloat(t1.xyz)
            + uintBitsToFloat(t2.xyz) * reach * 0.5 + velocity * ZPS_EXHAUST_LIFETIME * 0.5;
    radius = reach + length(velocity) * ZPS_EXHAUST_LIFETIME + 4.0;
    return true;
}

bool zps_exhaustParticle(int slot, out ZpsExhaustParticle p) {
    int index = slot % ZPS_EXHAUST_BATCH_SIZE;
    int base = (slot / ZPS_EXHAUST_BATCH_SIZE) * 4;
    uvec4 t0 = texelFetch(zps_exhaustBatches, base);
    uvec4 t1 = texelFetch(zps_exhaustBatches, base + 1);
    uvec4 t2 = texelFetch(zps_exhaustBatches, base + 2);
    uvec4 t3 = texelFetch(zps_exhaustBatches, base + 3);

    uint count = t2.w;
    uint elapsed = flw_ticks - t0.w;
    if (uint(index) >= count || elapsed > ZPS_EXHAUST_MAX_AGE) {
        return false;
    }

    // Spread the batch over its tick so the plume is continuous rather than 20 puffs a second.
    float stagger = (float(index) + 0.5) / float(count);
    float age = float(elapsed) + flw_partialTick - stagger;

    uint rng = zps_hash(t1.w ^ (uint(index) * 0x9E3779B9u));
    float life = ZPS_EXHAUST_LIFETIME * (0.75 + 0.5 * zps_rand(rng));
    if (age < 0.0 || age >= life) {
        return false;
    }
    float t = age / life;

    vec3 dir = uintBitsToFloat(t2.xyz);
    vec3 emitterVelocity = uintBitsToFloat(t3.xyz);
    float speed = uintBitsToFloat(t3.w);

    vec3 helper = abs(dir.y) < 0.9 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0);
    vec3 u = normalize(cross(dir, helper));
    vec3 w = cross(dir, u);

    float angle = 6.2831853 * zps_rand(rng);
    float radial = sqrt(zps_rand(rng));
    vec3 across = u * cos(angle) + w * sin(angle);
    vec3 velocity = (dir + across * radial * ZPS_EXHAUST_SPREAD) * speed * (0.85 + 0.3 * zps_rand(rng))
            + emitterVelocity;
    vec3 start = vec3(ivec3(t0.xyz) - flw_renderOrigin) + uintBitsToFloat(t1.xyz)
            + emitterVelocity * stagger
            + (u * (zps_rand(rng) - 0.5) + w * (zps_rand(rng) - 0.5)) * ZPS_EXHAUST_NOZZLE;

    // Linear drag has a closed form: travel = v * (1 - e^(-k*age)) / k, and a constant lift under
    // the same drag rises at (b/k) * (age - travel).
    float travel = (1.0 - exp(-ZPS_EXHAUST_DRAG * age)) / ZPS_EXHAUST_DRAG;
    float lift = ZPS_EXHAUST_BUOYANCY * (0.5 + zps_rand(rng)) / ZPS_EXHAUST_DRAG * (age - travel);
    vec3 phase = vec3(zps_rand(rng), zps_rand(rng), zps_rand(rng)) * 6.2831853;
    vec3 swirl = vec3(sin(age * 0.15 + phase.x), 0.5 * sin(age * 0.13 + phase.y), sin(age * 0.17 + phase.z)) * 0.6 * t;

    p.position = start + velocity * travel + vec3(0.0, lift, 0.0) + swirl;
    p.size = mix(0.12, 0.65, sqrt(t)) * (0.7 + 0.6 * zps_rand(rng));
    float shifted = clamp(t + (zps_rand(rng) - 0.5) * 0.06, 0.0, 1.0);
    p.color = zps_exhaustColor(shifted);
    p.heat = 1.0 - smoothstep(0.08, 0.30, shifted);
    return true;
}
