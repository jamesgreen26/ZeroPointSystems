package g_mungus.zps.exhaust;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.engine_room.flywheel.api.material.Material;
import g_mungus.zps.ZPSMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.io.IOException;

/**
 * Exhaust is drawn into a layer of its own and composited over the finished level, so that it sorts
 * against itself and against everything translucent the level draws after Flywheel: water, glass,
 * particles, clouds. Within the layer, weighted blended transparency (McGuire and Bavoil 2013) makes
 * the order particles are drawn in irrelevant, between thrusters as much as within one plume.
 *
 * <p>Each frame with exhaust in it:
 * <ol>
 *     <li>{@code AFTER_SKY}: the layer's targets are sized to the main target and cleared.</li>
 *     <li>Flywheel's draws: {@link #afterSetup} sends the two exhaust materials' draws into the
 *     layer, with the blending weighted transparency needs. The accumulation target sums weighted
 *     colour; the revealage target multiplies down transmittance and keeps the nearest exhaust
 *     depth. Both are tested against the main depth buffer, which they share.</li>
 *     <li>{@code AFTER_BLOCK_ENTITIES}: the depth buffer is copied once every opaque thing is in
 *     it, block entity renderers having drawn after Flywheel.</li>
 *     <li>{@code AFTER_LEVEL}: one fullscreen pass resolves the layer over the image. Exhaust behind
 *     something opaque is dropped; behind something translucent it is dimmed.</li>
 * </ol>
 *
 * <p>Depths are compared per pixel at the nearest exhaust, so a translucent surface cutting through a
 * plume puts all of that pixel's exhaust on one side of it. With Fabulous graphics the level keeps
 * translucent terrain out of the main depth buffer, so exhaust is never dimmed by it.
 */
public final class ExhaustLayer {

    /** Beyond any view distance, in blocks: what an empty pixel's nearest exhaust depth is. */
    private static final float NO_EXHAUST = 60000f;

    private static @Nullable ShaderInstance opaqueDepthShader;
    private static @Nullable ShaderInstance compositeShader;

    private static int width;
    private static int height;
    private static int sharedDepth;
    private static int accumTexture;
    private static int revealTexture;
    private static int opaqueTexture;
    private static int accumFramebuffer;
    private static int revealFramebuffer;
    private static int opaqueFramebuffer;

    /** Whether the targets are cleared and sized for this frame, so exhaust may be drawn into them. */
    private static boolean open;
    private static boolean drawn;
    /** The framebuffer an exhaust draw replaced, while one is under way; 0 otherwise. */
    private static int redirectedFrom;
    private static boolean redirecting;

    private ExhaustLayer() {
    }

    public static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(), ZPSMod.resource("exhaust_opaque_depth"),
                DefaultVertexFormat.POSITION), loaded -> opaqueDepthShader = loaded);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), ZPSMod.resource("exhaust_composite"),
                DefaultVertexFormat.POSITION), loaded -> compositeShader = loaded);
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        RenderLevelStageEvent.Stage stage = event.getStage();
        if (stage == RenderLevelStageEvent.Stage.AFTER_SKY) {
            open = ExhaustParticles.active() && prepare();
            drawn = false;
        } else if (stage == RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            // Flywheel resets its state after drawing, which ends any redirect; this is a backstop.
            endRedirect();
            if (drawn) {
                copyOpaqueDepth(event.getProjectionMatrix());
            }
        } else if (stage == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            if (drawn) {
                composite(event.getProjectionMatrix());
            }
            open = false;
            drawn = false;
        }
    }

    /** Before Flywheel sets up any draw: an exhaust draw before it is over. */
    public static void beforeSetup() {
        endRedirect();
    }

    /** After Flywheel sets up a draw: an exhaust draw goes to the layer, with the layer's blending. */
    public static void afterSetup(Material material) {
        boolean accum = material == ExhaustRendering.ACCUM_MATERIAL;
        if (!accum && material != ExhaustRendering.REVEAL_MATERIAL) {
            return;
        }
        if (!open) {
            // Nowhere to draw it: draw nothing rather than smear it over the level. Flywheel sets
            // the colour mask again for its next draw.
            RenderSystem.colorMask(false, false, false, false);
            return;
        }
        redirectedFrom = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        redirecting = true;
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, accum ? accumFramebuffer : revealFramebuffer);
        if (accum) {
            // rgb += rgb * (a * w), alpha += a * w
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        } else {
            // rgb *= 1 - a, alpha = min(alpha, depth)
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
            GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_MIN);
        }
        drawn = true;
    }

    /** After Flywheel has drawn everything. */
    public static void afterReset() {
        endRedirect();
    }

    private static void endRedirect() {
        if (!redirecting) {
            return;
        }
        redirecting = false;
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, redirectedFrom);
        GL14.glBlendEquation(GL14.GL_FUNC_ADD);
    }

    /** Sizes the targets to the main target, and clears them. False if they could not be made. */
    private static boolean prepare() {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (main.width != width || main.height != height) {
            delete();
            width = main.width;
            height = main.height;
            sharedDepth = main.getDepthTextureId();
            accumTexture = texture(GL30.GL_RGBA16F, GL11.GL_RGBA);
            revealTexture = texture(GL30.GL_RGBA16F, GL11.GL_RGBA);
            opaqueTexture = texture(GL30.GL_R32F, GL11.GL_RED);
            int depthAttachment = main.isStencilEnabled() ? GL30.GL_DEPTH_STENCIL_ATTACHMENT : GL30.GL_DEPTH_ATTACHMENT;
            accumFramebuffer = framebuffer(accumTexture, depthAttachment);
            revealFramebuffer = framebuffer(revealTexture, depthAttachment);
            opaqueFramebuffer = framebuffer(opaqueTexture, 0);
            if (accumFramebuffer == 0 || revealFramebuffer == 0 || opaqueFramebuffer == 0) {
                delete();
                return false;
            }
        }
        int previous = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        // The main depth texture is attached afresh every frame. Minecraft remakes the main target
        // on a GUI scale change even at the same size, and GL can give the new depth texture the
        // old one's name; a framebuffer keeps the deleted original alive, never cleared again, and
        // exhaust would be depth-tested against the last frame drawn into it, menus and all.
        sharedDepth = main.getDepthTextureId();
        int depthAttachment = main.isStencilEnabled() ? GL30.GL_DEPTH_STENCIL_ATTACHMENT : GL30.GL_DEPTH_ATTACHMENT;
        for (int framebuffer : new int[]{accumFramebuffer, revealFramebuffer}) {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
            GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, depthAttachment, GL11.GL_TEXTURE_2D, sharedDepth, 0);
        }
        RenderSystem.colorMask(true, true, true, true);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, accumFramebuffer);
        GL30.glClearBufferfv(GL11.GL_COLOR, 0, new float[]{0f, 0f, 0f, 0f});
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, revealFramebuffer);
        GL30.glClearBufferfv(GL11.GL_COLOR, 0, new float[]{1f, 1f, 1f, NO_EXHAUST});
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previous);
        return true;
    }

    private static int texture(int internalFormat, int format) {
        int id = GlStateManager._genTexture();
        GlStateManager._bindTexture(id);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internalFormat, width, height, 0, format, GL11.GL_FLOAT, (java.nio.ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL30.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL30.GL_CLAMP_TO_EDGE);
        GlStateManager._bindTexture(0);
        return id;
    }

    /** A framebuffer drawing to {@code color}, sharing the main depth buffer unless {@code depthAttachment} is 0. */
    private static int framebuffer(int color, int depthAttachment) {
        int previous = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int id = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, id);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, color, 0);
        if (depthAttachment != 0) {
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, depthAttachment, GL11.GL_TEXTURE_2D, sharedDepth, 0);
        }
        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previous);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            ZPSMod.LOGGER.error("Exhaust layer framebuffer incomplete: 0x{}", Integer.toHexString(status));
            GL30.glDeleteFramebuffers(id);
            return 0;
        }
        return id;
    }

    private static void delete() {
        for (int framebuffer : new int[]{accumFramebuffer, revealFramebuffer, opaqueFramebuffer}) {
            if (framebuffer != 0) {
                GL30.glDeleteFramebuffers(framebuffer);
            }
        }
        for (int texture : new int[]{accumTexture, revealTexture, opaqueTexture}) {
            if (texture != 0) {
                GlStateManager._deleteTexture(texture);
            }
        }
        accumFramebuffer = revealFramebuffer = opaqueFramebuffer = 0;
        accumTexture = revealTexture = opaqueTexture = 0;
        width = height = sharedDepth = 0;
    }

    private static void copyOpaqueDepth(Matrix4f projection) {
        ShaderInstance shader = opaqueDepthShader;
        if (shader == null) {
            return;
        }
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        shader.setSampler("SceneDepth", main.getDepthTextureId());
        shader.safeGetUniform("InvProjMat").set(new Matrix4f(projection).invert());
        RenderSystem.disableBlend();
        int previous = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, opaqueFramebuffer);
        drawFullscreen(shader);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previous);
    }

    private static void composite(Matrix4f projection) {
        ShaderInstance shader = compositeShader;
        if (shader == null) {
            return;
        }
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        shader.setSampler("Accum", accumTexture);
        shader.setSampler("Reveal", revealTexture);
        shader.setSampler("OpaqueDepth", opaqueTexture);
        shader.setSampler("SceneDepth", main.getDepthTextureId());
        shader.safeGetUniform("InvProjMat").set(new Matrix4f(projection).invert());
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        drawFullscreen(shader);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /** One quad over the whole target, neither depth-tested nor depth-written. */
    private static void drawFullscreen(ShaderInstance shader) {
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(() -> shader);
        BufferBuilder quad = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        quad.addVertex(-1f, -1f, 0f);
        quad.addVertex(1f, -1f, 0f);
        quad.addVertex(1f, 1f, 0f);
        quad.addVertex(-1f, 1f, 0f);
        BufferUploader.drawWithShader(quad.buildOrThrow());
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }
}
