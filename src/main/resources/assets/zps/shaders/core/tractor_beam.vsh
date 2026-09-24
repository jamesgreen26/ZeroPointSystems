#version 150

#moj_import <fog.glsl>

// The Tractor Beam's hull: the side walls and end cap of each column, built afresh every frame. Positions arrive
// already relative to the camera. The rest says where the vertex is in the beam's own terms, which is what the
// pattern in the fragment stage is laid out in: UV0 is its place across the panel, in blocks from the panel's
// lowest corner, and UV2.x is how far out from the mouth it is, in 256ths of a block. It is sent as a whole
// number because those arrive exact, so that two faces sharing a vertex agree about it to the bit.

in vec3 Position;
in vec2 UV0;
in ivec2 UV2;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;

out vec2 across;
out float outward;
out vec4 vertexColor;
out float vertexDistance;

void main() {
    vec4 viewPos = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * viewPos;

    across = UV0;
    outward = float(UV2.x) / 256.0;
    vertexColor = Color;
    vertexDistance = fog_distance(ModelViewMat, Position, FogShape);
}
