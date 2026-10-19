// The instance shader already lit each particle as a whole; fetching again per fragment would
// split a cube's faces across block boundaries.
void flw_shaderLight() {
}
