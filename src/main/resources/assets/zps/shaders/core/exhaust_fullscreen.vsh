#version 150

// A quad already in clip space, covering the screen.
in vec3 Position;

void main() {
    gl_Position = vec4(Position.xy, 0.0, 1.0);
}
