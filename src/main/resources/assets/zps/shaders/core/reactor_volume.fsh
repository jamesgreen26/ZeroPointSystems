#version 150

#moj_import <fog.glsl>

// The plasma inside a fusion reactor, as a volume.
//
// Every fragment lies on the cavity's inner surface. For the faces on the far side of the cavity
// from the camera, that fragment is exactly where the eye's ray leaves the cavity; where it entered
// is bounded by the reactor's bounding box, by how deep the cavity runs behind this face, and by
// the eye itself when it is inside. The fragment marches that segment through a scrolling noise
// field and adds a hot core from how close the ray passes to the centre. Near faces draw nothing,
// so each pixel gets the volume once.
//
// Everything is measured in the reactor's own frame, from the cavity's lowest corner. The mesh is
// already in that frame, and the eye arrives in it as a uniform: the model-view carries the frame
// to the view, moving grid and all, and the eye is the view's origin brought back through it. So
// the volume sits square in the cavity however a grid is tilted.
//
// Nothing here depends on which cell or face the fragment belongs to: only on where it is, where
// the eye is, and per-reactor constants. That is what keeps neighbouring quads seamless.

uniform sampler2D Sampler0;

uniform float FogStart;
uniform float FogEnd;

// The eye in the reactor's frame when w is 1. When w is 0 the projection is orthographic, there is
// no eye, and xyz is the one direction every ray runs in.
uniform vec4 Eye;
// The reactor's bounding box, which starts at the origin.
uniform vec3 BoxSize;
// Heat over ignition, the per-reactor noise phase, and the clock in seconds.
uniform vec3 Params;

in vec3 localPos;
in vec3 localNormal;
in float faceDepth;
in float vertexDistance;

out vec4 fragColor;

const int STEPS = 4;
const float NOISE_SCALE = 0.55;
// Scroll speed, in noise units per second. Constant on purpose: the phase is time times speed, so
// a speed that followed the (eased, ever-changing) heat would rescale the whole phase on every heat
// update and the pattern would jump by time times the change. The clock's wrap in
// ReactorGlowRenderer is worked out from this and the octaves in fbm2; change them together.
const float SCROLL_SPEED = 1.0;
const float NOISE_LO = 0.3;
const float NOISE_HI = 0.8;
// Density the volume carries everywhere, before the noise adds wisps.
const float BASE_DENSITY = 0.25;
// How quickly the plasma builds up along the ray, per block.
const float EXTINCTION = 0.55;
// Width, in blocks, of the clear band along the walls.
const float WALL_GAP = 0.4;
// How much the core follows the cavity's box rather than a sphere: 0 is round, 1 is the box's own
// shape.
const float SHAPE_CONFORMITY = 0.5;
// How far past a face's own cavity depth the march may reach at a glancing angle, in blocks.
const float DEPTH_SLACK = 0.5;
// Brightness of the core at the centre, and how sharply it falls off.
const float CORE_STRENGTH = 1.6;
const float CORE_POWER = 2.5;
const float CUTOFF = 0.004;
// How far back an orthographic ray is taken to start: past any reactor, and no further, since the
// march's precision is spent on the whole length.
const float ORTHO_DISTANCE = 64.0;

const float NOISE_SIZE = 256.0;

// 3D value noise from a 2D texture, so one bilinear fetch does the work of eight hashes.
//
// Sampler0 (zps:textures/special/noise.png) is white noise in red, with the same noise shifted by
// (37, 17) texels in green. A lattice point (x, y, z) lives at texel (x + 37 z, y + 17 z); the shift
// means the texel one z-slice up is already in the green channel of the same fetch. Bilinear
// filtering interpolates x and y, and the fraction in z is mixed by hand, with the fraction smoothed
// before the fetch.
float valueNoise(vec3 x) {
    vec3 p = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    vec2 uv = p.xy + vec2(37.0, 17.0) * p.z + f.xy;
    vec2 rg = textureLod(Sampler0, (uv + 0.5) / NOISE_SIZE, 0.0).rg;
    return mix(rg.r, rg.g, f.z);
}

// Two octaves; enough texture for a glow.
float fbm2(vec3 p) {
    return valueNoise(p) * 0.65 + valueNoise(p * 2.03 + 17.1) * 0.35;
}

// Where the ray from `start` along `dir` enters the box, as a distance along the ray. Negative when
// the start is already inside.
float boxEntry(vec3 start, vec3 dir, vec3 boxMin, vec3 boxMax) {
    vec3 invDir = 1.0 / dir;
    vec3 t1 = (boxMin - start) * invDir;
    vec3 t2 = (boxMax - start) * invDir;
    vec3 tMin = min(t1, t2);
    return max(max(tMin.x, tMin.y), tMin.z);
}

void main() {
    vec3 exitPoint = localPos;
    float heat = Params.x;
    float seed = Params.y;
    vec3 boxMin = vec3(0.0);
    vec3 boxMax = BoxSize;
    float depth = floor(faceDepth + 0.5);

    if (heat < CUTOFF) {
        discard;
    }

    vec3 eye = Eye.w > 0.5 ? Eye.xyz : exitPoint - normalize(Eye.xyz) * ORTHO_DISTANCE;
    vec3 toExit = exitPoint - eye;
    float exitDistance = length(toExit);
    vec3 dir = toExit / max(exitDistance, 1e-4);

    // Near faces have their wall behind the camera's side; the far faces carry the volume.
    float facing = dot(normalize(localNormal), dir);
    if (facing < 0.0) {
        discard;
    }

    // The ray is inside the cavity from where it crossed the box, but no further back than the
    // cavity actually runs behind this face, so concave shapes do not glow through their walls.
    float entryDistance = boxEntry(eye, dir, boxMin, boxMax);
    float slab = (depth + DEPTH_SLACK) / max(facing, 0.2);
    entryDistance = clamp(max(entryDistance, exitDistance - slab), 0.0, exitDistance);
    float pathLength = exitDistance - entryDistance;
    if (pathLength < 1e-4) {
        discard;
    }

    vec3 centre = 0.5 * (boxMin + boxMax);
    vec3 halfSize = 0.5 * (boxMax - boxMin);
    float t = Params.z * SCROLL_SPEED;
    vec3 drift = vec3(0.0, -t, seed * 7.3);

    // March the segment, front to back.
    float stepLength = pathLength / float(STEPS);
    float density = 0.0;
    for (int i = 0; i < STEPS; i++) {
        vec3 p = eye + dir * (entryDistance + stepLength * (float(i) + 0.5));
        // Clear band along the walls, measured against the reactor's box.
        vec3 toWall = halfSize - abs(p - centre);
        float wallDistance = min(min(toWall.x, toWall.y), toWall.z);
        float gap = smoothstep(0.0, WALL_GAP, wallDistance);

        float n = fbm2(p * NOISE_SCALE + drift);
        float wisps = smoothstep(NOISE_LO, NOISE_HI, n);
        density += (BASE_DENSITY + (1.0 - BASE_DENSITY) * wisps) * gap * stepLength;
    }
    float volume = 1.0 - exp(-density * EXTINCTION);

    // The core: how close the ray passes to the centre, within the part of it that is inside.
    float along = clamp(dot(centre - eye, dir), entryDistance, exitDistance);
    vec3 nearest = eye + dir * along;
    vec3 q3 = abs(nearest - centre) / max(halfSize, vec3(0.5));
    float round = length(q3);
    float boxy = max(max(q3.x, q3.y), q3.z);
    float coreDistance = mix(round, boxy, SHAPE_CONFORMITY);
    float core = CORE_STRENGTH * pow(max(0.0, 1.0 - coreDistance), CORE_POWER);

    // Below ignition a hot ember; at and past it, plasma that whitens and then goes electric blue.
    float lit = smoothstep(0.85, 1.15, heat);
    vec3 ember = mix(vec3(0.9, 0.15, 0.02), vec3(1.0, 0.55, 0.12), clamp(heat, 0.0, 1.0));
    vec3 plasma = mix(vec3(1.0, 0.85, 0.5), vec3(0.45, 0.85, 1.0), clamp((heat - 1.0) * 0.5, 0.0, 1.0));
    vec3 colour = mix(ember, plasma, lit);
    vec3 coreColour = mix(colour, vec3(1.0), 0.7);

    float strength = min(heat, 1.0) * (0.7 + 0.5 * min(heat, 2.0));
    vec3 rgb = (colour * volume + coreColour * core * volume) * strength;
    float alpha = clamp((volume + core * volume) * strength, 0.0, 1.0);

    // Drawn additively, so fog takes the glow toward nothing rather than toward the fog colour.
    alpha *= linear_fog_fade(vertexDistance, FogStart, FogEnd);
    fragColor = vec4(rgb, alpha);
}
