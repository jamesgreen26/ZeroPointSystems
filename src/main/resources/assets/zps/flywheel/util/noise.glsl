// Texture-less 3D value noise. Integer-style hashing on fract(), no sin(), so every driver
// agrees on the result: the fragment shader runs several times per pixel under order-independent
// transparency and each run must land on the same colour.

float zps_hash13(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.x + p.y) * p.z);
}

float zps_valueNoise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    vec3 w = f * f * (3.0 - 2.0 * f);

    float n000 = zps_hash13(i);
    float n001 = zps_hash13(i + vec3(0.0, 0.0, 1.0));
    float n010 = zps_hash13(i + vec3(0.0, 1.0, 0.0));
    float n011 = zps_hash13(i + vec3(0.0, 1.0, 1.0));
    float n100 = zps_hash13(i + vec3(1.0, 0.0, 0.0));
    float n101 = zps_hash13(i + vec3(1.0, 0.0, 1.0));
    float n110 = zps_hash13(i + vec3(1.0, 1.0, 0.0));
    float n111 = zps_hash13(i + vec3(1.0, 1.0, 1.0));

    float y0 = mix(mix(n000, n001, w.z), mix(n010, n011, w.z), w.y);
    float y1 = mix(mix(n100, n101, w.z), mix(n110, n111, w.z), w.y);
    return mix(y0, y1, w.x);
}

// Two octaves; enough texture for a glow, cheap enough to run three times per pixel.
float zps_fbm2(vec3 p) {
    return zps_valueNoise(p) * 0.65 + zps_valueNoise(p * 2.03 + 17.1) * 0.35;
}
