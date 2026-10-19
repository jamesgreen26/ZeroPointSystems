// Exhaust particles: what the simulation (ExhaustSimulation's own programs) and the drawing
// (Flywheel's instance and cull shaders) must agree on. Nothing here may use either side's own
// uniforms; the batch ring is the one sampler both bind, under the same name.
//
// The ring, written by ExhaustParticles, is RGBA32UI texels: a two-texel header, then one record
// of five texels per batch, a batch being the particles one thruster emits in one tick.
//
//   header 0  particles per batch         tumble (0 or 1)
//   header 1  simulation origin xyz (int) tick last simulated
//
//   t0  anchor block xyz (int)            spawn tick (LevelRenderer ticks)
//   t1  nozzle offset from anchor (float) seed
//   t2  exhaust direction (float, unit)   count
//   t3  emitter velocity, blocks/tick     exhaust speed, blocks/tick (float)
//   t4  orientation of the emitter's grid when the batch left, a unit quaternion xyzw (float)
//
// Everything is in world space: a batch takes its grid's pose once and never follows it. The
// simulation keeps positions relative to the simulation origin, a block fixed while the ring
// has anything in it, so they stay precise as floats.

uniform usamplerBuffer zps_exhaustBatches;

// Must match ExhaustParticles.MAX_LIFETIME_TICKS, whose plume bounds also assume the drag,
// buoyancy and spread below.
const float ZPS_EXHAUST_LIFETIME = 40.0;
const uint ZPS_EXHAUST_MAX_AGE = 64u;

const float ZPS_EXHAUST_DRAG = 0.1;       // per tick
const float ZPS_EXHAUST_BUOYANCY = 0.004; // blocks/tick^2
const float ZPS_EXHAUST_SPREAD = 0.16;    // cone radius per unit of travel
const float ZPS_EXHAUST_NOZZLE = 0.25;    // spawn jitter across the nozzle, blocks
const float ZPS_EXHAUST_SPIN = 0.25;      // fastest spin leaving the nozzle, radians/tick

// Independent random streams per particle, so each side draws only what it needs and adding a
// draw to one never shifts another.
const uint ZPS_EXHAUST_STREAM_LIFE = 0x00000000u;
const uint ZPS_EXHAUST_STREAM_MOTION = 0x68E31DA4u;
const uint ZPS_EXHAUST_STREAM_FORCES = 0x1B56C4E9u;
const uint ZPS_EXHAUST_STREAM_LOOK = 0xB5297A4Du;

struct ZpsExhaustBatch {
    ivec3 anchor;
    uint spawnTick;
    vec3 offset;
    uint seed;
    vec3 direction;
    uint count;
    vec3 emitterVelocity;
    float speed;
    vec4 orientation;
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

uint zps_exhaustStream(uint seed, uint index, uint stream) {
    return zps_hash(seed ^ (index * 0x9E3779B9u) ^ stream);
}

int zps_exhaustBatchSize() {
    return int(texelFetch(zps_exhaustBatches, 0).x);
}

bool zps_exhaustTumble() {
    return texelFetch(zps_exhaustBatches, 0).y != 0u;
}

ivec3 zps_exhaustSimOrigin() {
    return ivec3(texelFetch(zps_exhaustBatches, 1).xyz);
}

uint zps_exhaustSimTick() {
    return texelFetch(zps_exhaustBatches, 1).w;
}

ZpsExhaustBatch zps_exhaustBatch(int batch) {
    int base = 2 + batch * 5;
    uvec4 t0 = texelFetch(zps_exhaustBatches, base);
    uvec4 t1 = texelFetch(zps_exhaustBatches, base + 1);
    uvec4 t2 = texelFetch(zps_exhaustBatches, base + 2);
    uvec4 t3 = texelFetch(zps_exhaustBatches, base + 3);
    uvec4 t4 = texelFetch(zps_exhaustBatches, base + 4);
    ZpsExhaustBatch b;
    b.anchor = ivec3(t0.xyz);
    b.spawnTick = t0.w;
    b.offset = uintBitsToFloat(t1.xyz);
    b.seed = t1.w;
    b.direction = uintBitsToFloat(t2.xyz);
    b.count = t2.w;
    b.emitterVelocity = uintBitsToFloat(t3.xyz);
    b.speed = uintBitsToFloat(t3.w);
    b.orientation = uintBitsToFloat(t4);
    return b;
}

/// Where in its tick a particle is born: the batch is spread over the tick so the plume is
/// continuous rather than 20 puffs a second.
float zps_exhaustStagger(uint index, uint count) {
    return (float(index) + 0.5) / float(count);
}

float zps_exhaustLife(uint seed, uint index) {
    uint rng = zps_exhaustStream(seed, index, ZPS_EXHAUST_STREAM_LIFE);
    return ZPS_EXHAUST_LIFETIME * (0.75 + 0.5 * zps_rand(rng));
}

/// 1 white-hot through 0 cold smoke, by fraction of life.
float zps_exhaustHeatAt(float t) {
    return 1.0 - smoothstep(0.08, 0.30, t);
}

/// Where a particle leaves the nozzle, relative to its batch's anchor, and how fast.
void zps_exhaustSpawn(ZpsExhaustBatch b, uint index, float stagger, out vec3 position, out vec3 velocity) {
    uint rng = zps_exhaustStream(b.seed, index, ZPS_EXHAUST_STREAM_MOTION);
    vec3 helper = abs(b.direction.y) < 0.9 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0);
    vec3 u = normalize(cross(b.direction, helper));
    vec3 w = cross(b.direction, u);
    float angle = 6.2831853 * zps_rand(rng);
    float radial = sqrt(zps_rand(rng));
    vec3 across = u * cos(angle) + w * sin(angle);
    velocity = (b.direction + across * radial * ZPS_EXHAUST_SPREAD) * b.speed * (0.85 + 0.3 * zps_rand(rng))
            + b.emitterVelocity;
    position = b.offset + b.emitterVelocity * stagger
            + (u * (zps_rand(rng) - 0.5) + w * (zps_rand(rng) - 0.5)) * ZPS_EXHAUST_NOZZLE;
}

/// How far a particle has gone along its starting velocity under drag alone, in units of that
/// velocity: (1 - e^(-k*age)) / k. Spin slows by the same drag.
float zps_exhaustTravel(float age) {
    return (1.0 - exp(-ZPS_EXHAUST_DRAG * age)) / ZPS_EXHAUST_DRAG;
}
