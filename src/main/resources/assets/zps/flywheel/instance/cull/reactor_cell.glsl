// The mesh's own sphere is centred on the cell; the instance only translates it.
void flw_transformBoundingSphere(in FlwInstance i, inout vec3 center, inout float radius) {
    center += i.pos;
}
