#include "zps:util/noise.glsl"

// The plasma inside a fusion reactor, as a volume.
//
// Every fragment lies on the cavity's inner surface. For the faces on the far side of the cavity
// from the camera, that fragment is exactly where the eye's ray leaves the cavity; where it entered
// is bounded by the reactor's bounding box, by how deep the cavity runs behind this face, and by
// the eye itself when it is inside. The fragment marches that segment through a scrolling noise
// field and adds a hot core from how close the ray passes to the centre. Near faces draw nothing,
// so each pixel gets the volume once.
//
// Everything is measured in the reactor's own frame, from the cavity's lowest corner. The vertex
// stage hands over the fragment's position in that frame, and the eye is brought into it by
// turning the world-space offset from the fragment to the camera back through the reactor's
// rotation. On the ground that rotation is the identity and the frame is the world's own; on a
// moving grid it is the grid's, so the volume sits square in the cavity however the grid is tilted.
//
// Nothing here depends on which cell or face the fragment belongs to: only on where it is, where
// the camera is, and per-reactor constants. That is what keeps neighbouring quads seamless.

const int STEPS = 4;
const float NOISE_SCALE = 0.55;
/**
 * Scroll speed, in noise units per second. Constant on purpose: the phase is time times speed,
 * so a speed that followed the (eased, ever-changing) heat would rescale the whole phase on every
 * heat update and the pattern would jump by time times the change.
 */
const float SCROLL_SPEED = 1.0;
const float NOISE_LO = 0.3;
const float NOISE_HI = 0.8;
/** Density the volume carries everywhere, before the noise adds wisps. */
const float BASE_DENSITY = 0.25;
/** How quickly the plasma builds up along the ray, per block. */
const float EXTINCTION = 0.55;
/** Width, in blocks, of the clear band along the walls. */
const float WALL_GAP = 0.4;
/**
 * How much the core follows the cavity's box rather than a sphere: 0 is round, 1 is the box's
 * own shape.
 */
const float SHAPE_CONFORMITY = 0.5;
/** How far past a face's own cavity depth the march may reach at a glancing angle, in blocks. */
const float DEPTH_SLACK = 0.5;
/** Brightness of the core at the centre, and how sharply it falls off. */
const float CORE_STRENGTH = 1.6;
const float CORE_POWER = 2.5;
const float CUTOFF = 0.004;

// Where the ray from `start` along `dir` enters the box, as a distance along the ray. Negative when
// the start is already inside.
float zps_boxEntry(vec3 start, vec3 dir, vec3 boxMin, vec3 boxMax) {
    vec3 invDir = 1.0 / dir;
    vec3 t1 = (boxMin - start) * invDir;
    vec3 t2 = (boxMax - start) * invDir;
    vec3 tMin = min(t1, t2);
    return max(max(tMin.x, tMin.y), tMin.z);
}

// Turns v by the inverse of the unit quaternion q: out of the world and into the reactor's frame.
vec3 zps_unrotate(vec4 q, vec3 v) {
    vec3 t = 2.0 * cross(q.xyz, v);
    return v - q.w * t + cross(q.xyz, t);
}

// Two signed 16-bit fixed-point values from one int, the low half first.
vec2 zps_unpackSnorm16(int bits) {
    return vec2(float((bits << 16) >> 16), float(bits >> 16)) / 32767.0;
}

void flw_materialFragment() {
    vec3 exitPoint = flw_vertexColor.rgb;
    float heat = flw_vertexColor.a;
    vec3 boxMin = vec3(0.0);
    vec3 boxMax = vec3(flw_vertexTexCoord, flw_vertexLight.x);
    float depth = floor(flw_vertexLight.y);
    float seed = fract(flw_vertexLight.y);

    if (heat < CUTOFF) {
        flw_fragColor = vec4(0.0);
        return;
    }

    // Near faces have their wall behind the camera's side; the far faces carry the volume. Both
    // the normal and the view ray are in world space here, and the test is the same in any frame.
    vec3 toExitWorld = flw_vertexPos.xyz - flw_cameraPos;
    float exitDistance = length(toExitWorld);
    vec3 normal = normalize(flw_vertexNormal);
    float facing = dot(normal, toExitWorld) / max(exitDistance, 1e-4);
    if (facing < 0.0) {
        flw_fragColor = vec4(0.0);
        return;
    }

    // Into the reactor's frame: the same ray, turned, and the eye placed back from the exit along it.
    vec4 q = normalize(vec4(zps_unpackSnorm16(flw_vertexOverlay.x), zps_unpackSnorm16(flw_vertexOverlay.y)));
    vec3 toExit = zps_unrotate(q, toExitWorld);
    vec3 dir = toExit / max(exitDistance, 1e-4);
    vec3 eye = exitPoint - toExit;

    // The ray is inside the cavity from where it crossed the box, but no further back than the
    // cavity actually runs behind this face, so concave shapes do not glow through their walls.
    float entryDistance = zps_boxEntry(eye, dir, boxMin, boxMax);
    float slab = (depth + DEPTH_SLACK) / max(facing, 0.2);
    entryDistance = clamp(max(entryDistance, exitDistance - slab), 0.0, exitDistance);
    float pathLength = exitDistance - entryDistance;
    if (pathLength < 1e-4) {
        flw_fragColor = vec4(0.0);
        return;
    }

    vec3 centre = 0.5 * (boxMin + boxMax);
    vec3 halfSize = 0.5 * (boxMax - boxMin);
    float t = flw_renderSeconds * SCROLL_SPEED;
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

        // Anchored to the reactor's own corner: the pattern stays put on the reactor, and the
        // coordinates stay small enough for float precision no matter where in the world it is.
        float n = zps_fbm2(p * NOISE_SCALE + drift);
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
    flw_fragColor = vec4(rgb, alpha);
}
