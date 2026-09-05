// 3D value noise from a 2D texture, so one bilinear fetch does the work of eight hashes.
//
// The material's diffuse texture (zps:textures/special/noise.png) is white noise in red, with the
// same noise shifted by (37, 17) texels in green. A lattice point (x, y, z) lives at texel
// (x + 37 z, y + 17 z); the shift means the texel one z-slice up is already in the green channel
// of the same fetch. Bilinear filtering interpolates x and y, and the fraction in z is mixed by
// hand. Smoothing the fraction before the fetch gives the same curve the old hashed noise had.
//
// Deterministic per fragment, which the transparency modes that re-run the shader need.

const float ZPS_NOISE_SIZE = 256.0;

float zps_valueNoise(vec3 x) {
    vec3 p = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    vec2 uv = p.xy + vec2(37.0, 17.0) * p.z + f.xy;
    vec2 rg = textureLod(flw_diffuseTex, (uv + 0.5) / ZPS_NOISE_SIZE, 0.0).rg;
    return mix(rg.r, rg.g, f.z);
}

// Two octaves; enough texture for a glow.
float zps_fbm2(vec3 p) {
    return zps_valueNoise(p) * 0.65 + zps_valueNoise(p * 2.03 + 17.1) * 0.35;
}
