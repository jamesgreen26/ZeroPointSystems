// Only ever paired with the exhaust instance type, which writes this.
in float zps_exhaustHeat;

/// The particle's colour and opacity at this fragment: block-style face shading for smoke, none
/// for fire, which is emissive and flat.
vec4 zps_exhaustShade() {
    vec3 n = flw_vertexNormal;
    float face = n.y > 0.5 ? 1.0 : n.y < -0.5 ? 0.5 : abs(n.z) > 0.5 ? 0.8 : 0.6;
    vec4 color = flw_vertexColor;
    color.rgb *= mix(face, 1.0, zps_exhaustHeat);
    return color;
}

/// Distance from the eye along the view direction, in blocks.
float zps_exhaustViewDepth() {
    // Flywheel's linearize_depth, which material shaders may be compiled ahead of.
    float near = _flw_cullData.znear;
    float far = _flw_cullData.zfar;
    return 2.0 * near * far / (far + near - (2.0 * gl_FragCoord.z - 1.0) * (far - near));
}
