package g_mungus.zps.client.reactor;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexBuffer;
import g_mungus.zps.ZPSMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.io.IOException;

/**
 * Draws the reactor glow: a vanilla core shader over a {@link ReactorGlowMesh}, one draw per
 * reactor. {@link ClientReactors} calls it from the level render for every reactor; anything else
 * that wants the effect, a ponder scene or a preview, calls {@link #draw} itself with a mesh and
 * wherever it wants it.
 *
 * <p>Additive, bright, depth-tested but not depth-written, pulled in front of the walls it lies on by polygon offset. It has to be drawn before translucent
 * blocks, which do write depth: the glass goes over the glow, not the glow over the glass.
 */
public final class ReactorGlowRenderer {

    private static final ResourceLocation NOISE = ZPSMod.resource("textures/special/noise.png");
    /**
     * The shader clock wraps here. A float runs out of precision well before a world's age does,
     * and 25600 seconds is the shortest period at which both noise octaves' scroll, one at 1 and
     * one at 2.03 lattice cells a second, has gone a whole number of times round the 256-cell
     * lattice, so the wrap does not show.
     */
    private static final int CLOCK_PERIOD_TICKS = 25600 * 20;

    private static @Nullable ShaderInstance shader;

    private ReactorGlowRenderer() {
    }

    public static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(
                new ShaderInstance(event.getResourceProvider(), ZPSMod.resource("reactor_volume"),
                        DefaultVertexFormat.POSITION_COLOR_NORMAL),
                loaded -> shader = loaded);
    }

    /** The shader clock for a level: its game time in seconds, wrapped where the wrap does not show. */
    public static float seconds(Level level, float partialTick) {
        return (level.getGameTime() % CLOCK_PERIOD_TICKS + partialTick) / 20f;
    }

    /**
     * Draws one glow.
     *
     * @param modelView  takes the mesh's frame, blocks measured from the cavity's lowest corner, to
     *                   view space; rotation and translation only
     * @param projection perspective or orthographic; with the latter the volume is marched along
     *                   the one view direction instead of from an eye
     * @param heat       chamber temperature over ignition temperature
     * @param seed       noise phase, 0 to 1, so that neighbouring reactors do not match
     * @param seconds    the animation clock; see {@link #seconds}
     * @param fog        whether the level's fog applies, which outside the level render it does not
     */
    public static void draw(ReactorGlowMesh mesh, Matrix4fc modelView, Matrix4fc projection,
                            float heat, float seed, float seconds, boolean fog) {
        ShaderInstance shader = ReactorGlowRenderer.shader;
        if (shader == null) {
            return;
        }

        // The eye is the origin of view space; brought back through the model-view it is the eye in
        // the reactor's frame. An orthographic view has no eye, only the direction it looks in.
        Matrix4f inverse = modelView.invert(new Matrix4f());
        boolean perspective = projection.m23() != 0f;
        Vector3f eye = perspective
                ? inverse.transformPosition(new Vector3f())
                : inverse.transformDirection(new Vector3f(0f, 0f, -1f)).normalize();
        shader.safeGetUniform("Eye").set(eye.x, eye.y, eye.z, perspective ? 1f : 0f);
        shader.safeGetUniform("BoxSize").set(mesh.size().x(), mesh.size().y(), mesh.size().z());
        shader.safeGetUniform("Params").set(heat, seed, seconds);

        // The texture is the noise lattice; linear filtering is what makes smooth noise of it.
        Minecraft.getInstance().getTextureManager().getTexture(NOISE).setFilter(true, false);
        RenderSystem.setShaderTexture(0, NOISE);

        float fogStart = RenderSystem.getShaderFogStart();
        if (!fog) {
            RenderSystem.setShaderFogStart(Float.MAX_VALUE);
        }
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.polygonOffset(-1f, -10f);
        RenderSystem.enablePolygonOffset();

        VertexBuffer buffer = mesh.buffer();
        buffer.bind();
        buffer.drawWithShader(new Matrix4f(modelView), new Matrix4f(projection), shader);
        VertexBuffer.unbind();

        RenderSystem.polygonOffset(0f, 0f);
        RenderSystem.disablePolygonOffset();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.setShaderFogStart(fogStart);
    }
}
