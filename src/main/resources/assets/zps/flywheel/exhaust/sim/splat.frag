flat in float zps_weight;

out vec4 zps_fragColor;

void main() {
    zps_fragColor = vec4(zps_weight, 0.0, 0.0, 0.0);
}
