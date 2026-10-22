// Only ever paired with the exhaust instance type, which puts each particle's heat in the texture
// coordinate. A varying of its own would not survive a shader pack's geometry stage under Colorwheel,
// which forwards only Flywheel's own attributes; the texture is plain white, so nothing is lost.

/// 1 white-hot through 0 cold smoke.
float zps_exhaustHeat() {
    return flw_vertexTexCoord.x;
}

/// The particle's colour and opacity at this fragment: block-style face shading for smoke, none
/// for fire, which is emissive and flat. Faces of a tumbling cube are tilted, so the block shades
/// (top 1, bottom 0.5, north/south 0.8, east/west 0.6) are blended by how much the face points
/// each way.
vec4 zps_exhaustShade() {
    vec3 n = normalize(flw_vertexNormal);
    float face = dot(n * n, vec3(0.6, n.y > 0.0 ? 1.0 : 0.5, 0.8));
    vec4 color = flw_vertexColor;
    color.rgb *= mix(face, 1.0, zps_exhaustHeat());
    return color;
}

/// Distance from the eye along the view direction, in blocks.
float zps_exhaustViewDepth() {
    // Undone through the projection itself rather than its near and far: a far plane at infinity,
    // as Genesis sets one, makes the usual 2nf / (f + n - z(f - n)) infinity over infinity.
    return flw_projection[3][2] / ((2.0 * gl_FragCoord.z - 1.0) + flw_projection[2][2]);
}
