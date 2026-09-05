#include "zps:util/noise.glsl"

// The plasma inside a fusion reactor, as a volume.
//
// Every fragment lies on the cavity's inner surface. For the faces on the far side of the cavity
// from the camera, that fragment is exactly where the eye's ray leaves the cavity; where it entered
// is where the ray crossed the reactor's bounding box, or the eye itself when it is inside. The
// fragment marches that segment through a scrolling noise field and adds a hot core from how close
// the ray passes to the centre. Near faces draw nothing, so each pixel gets the volume once.
//
// Nothing here depends on which cell or face the fragment belongs to: only on where it is, where
// the camera is, and per-reactor constants. That is what keeps neighbouring quads seamless.

const int STEPS = 12;
const float NOISE_SCALE = 0.55;
const float SCROLL_BASE = 0.25;
const float SCROLL_HEAT = 0.3;
const float NOISE_LO = 0.3;
const float NOISE_HI = 0.8;
/** Density the volume carries everywhere, before the noise adds wisps. */
const float BASE_DENSITY = 0.25;
/** How quickly the plasma builds up along the ray, per block. */
const float EXTINCTION = 0.55;
/** Width, in blocks, of the clear band along the walls. */
const float WALL_GAP = 0.6;
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

void flw_materialFragment() {
    vec3 boxMin = flw_vertexColor.rgb;
    float heat = flw_vertexColor.a;
    vec3 boxMax = vec3(flw_vertexTexCoord, flw_vertexLight.x);
    float seed = flw_vertexLight.y;

    if (heat < CUTOFF) {
        flw_fragColor = vec4(0.0);
        return;
    }

    vec3 eye = flw_cameraPos;
    vec3 exitPoint = flw_vertexPos.xyz;
    vec3 toExit = exitPoint - eye;
    float exitDistance = length(toExit);
    vec3 dir = toExit / max(exitDistance, 1e-4);

    // Near faces have their wall behind the camera's side; the far faces carry the volume.
    if (dot(normalize(flw_vertexNormal), dir) < 0.0) {
        flw_fragColor = vec4(0.0);
        return;
    }

    float entryDistance = clamp(zps_boxEntry(eye, dir, boxMin, boxMax), 0.0, exitDistance);
    float pathLength = exitDistance - entryDistance;
    if (pathLength < 1e-4) {
        flw_fragColor = vec4(0.0);
        return;
    }

    vec3 centre = 0.5 * (boxMin + boxMax);
    vec3 halfSize = 0.5 * (boxMax - boxMin);
    float t = flw_renderSeconds * (SCROLL_BASE + SCROLL_HEAT * min(heat, 2.0));
    vec3 drift = vec3(0.0, -t, seed * 7.3);
    vec3 worldOffset = vec3(flw_renderOrigin);

    // March the segment, front to back.
    float stepLength = pathLength / float(STEPS);
    float density = 0.0;
    for (int i = 0; i < STEPS; i++) {
        vec3 p = eye + dir * (entryDistance + stepLength * (float(i) + 0.5));
        // Clear band along the walls, measured against the reactor's box.
        vec3 toWall = halfSize - abs(p - centre);
        float wallDistance = min(min(toWall.x, toWall.y), toWall.z);
        float gap = smoothstep(0.0, WALL_GAP, wallDistance);

        float n = zps_fbm2((p + worldOffset) * NOISE_SCALE + drift);
        float wisps = smoothstep(NOISE_LO, NOISE_HI, n);
        density += (BASE_DENSITY + (1.0 - BASE_DENSITY) * wisps) * gap * stepLength;
    }
    float volume = 1.0 - exp(-density * EXTINCTION);

    // The core: how close the ray passes to the centre, within the part of it that is inside.
    float along = clamp(dot(centre - eye, dir), entryDistance, exitDistance);
    vec3 nearest = eye + dir * along;
    float coreDistance = length((nearest - centre) / max(halfSize, vec3(0.5)));
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
