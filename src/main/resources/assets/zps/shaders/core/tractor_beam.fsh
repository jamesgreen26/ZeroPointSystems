#version 150

#moj_import <fog.glsl>

// The colour and pattern of the ion thruster's exhaust in Zero Point Labs (zpl:shaders/core/thrust.fsh), run the
// other way: a thruster's plume streams out of its nozzle, a Tractor Beam's streams into its mouth.
//
// The thruster's shader is written in terms of x, 0 at the nozzle and 1 at the tip of the plume, and y, once
// round the plume. Here x is the distance out from the mouth over the beam's set range, and y is the angle round
// the beam's axis. The colour and the fade follow x, so they span the beam however long it is; the stripes follow
// the distance in blocks, so they are the same size on every beam. Both are worked out from where the fragment is in the beam, not from which face it is on, so
// the stripes run unbroken round the corners of the hull and across the steps where a column is stopped short.
//
// Drawn additively, as the thruster is.

uniform float FogStart;
uniform float FogEnd;
// The two clocks the pattern runs on, each already wrapped to a turn: the one that streams the stripes along the
// beam, and the hundredfold slower one that winds them round it. Wrapped outside, in doubles, because a float
// that has been counting for hours no longer has the precision for an angle.
uniform vec2 Phases;
// x: the beam's set range in blocks, y: the panel's width in blocks
uniform vec2 Panel;
// 0 to 1: how far the beam has faded in since it started, or has left to fade since it stopped.
uniform float Glow;

in vec2 across;
in float outward;
in vec4 vertexColor;
in float vertexDistance;

out vec4 fragColor;

const float TAU = 6.2831853;
// Radians of stripe per block along the beam, and stripes round it. The thruster's shader has 14 radians along its
// whole plume, which is about 8 blocks long; a beam keeps that density per block instead, so that a long beam has
// more stripes along it rather than the same few stretched out.
const float STRIPES_PER_BLOCK = 14.0 / 8.0;
const float STRIPES_ROUND = 4.0;

void main() {
    float x = clamp(outward / max(Panel.x, 1.0), 0.0, 1.0);
    vec2 fromAxis = across - 0.5 * Panel.y;
    float y = atan(fromAxis.y, fromAxis.x) / TAU;

    // The thruster's "alpha": 1 at the nozzle, 0 at the tip. It sets the colour as well as the opacity.
    float a = 1.0 - x;

    // Pale violet at the mouth, through azure, to cyan where it fades out.
    vec3 colour = vec3(a * a * 0.5, max(1.0 - a, a * a * 0.5), 1.0);

    // The thruster's stripes move away from x = 0; with the sign of x turned round they move toward it.
    float stripes = 0.75 + 0.25 * sin(Phases.x + STRIPES_PER_BLOCK * outward + STRIPES_ROUND * TAU * y + Phases.y);

    float alpha = vertexColor.a * a * stripes * Glow;
    // Additive, so fog takes the beam toward nothing rather than toward the fog colour.
    alpha *= linear_fog_fade(vertexDistance, FogStart, FogEnd);
    fragColor = vec4(colour * vertexColor.rgb, alpha);
}
