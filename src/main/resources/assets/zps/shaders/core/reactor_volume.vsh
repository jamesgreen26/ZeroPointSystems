#version 150

#moj_import <fog.glsl>

// The reactor glow: a coat over the cavity's wall faces, baked into one buffer per reactor. Positions are in the reactor's own frame, measured from the cavity's lowest
// corner, which is the frame the fragment stage marches in; ModelViewMat carries that frame to the
// eye, moving grid and all. The red channel holds how deep the cavity runs behind the face, in
// blocks.

in vec3 Position;
in vec4 Color;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;

out vec3 localPos;
out vec3 localNormal;
out float faceDepth;
out float vertexDistance;

void main() {
    vec4 viewPos = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * viewPos;

    localPos = Position;
    localNormal = Normal;
    faceDepth = Color.r * 255.0;
    vertexDistance = fog_distance(ModelViewMat, Position, FogShape);
}
