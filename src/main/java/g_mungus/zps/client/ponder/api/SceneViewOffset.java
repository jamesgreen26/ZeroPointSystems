package g_mungus.zps.client.ponder.api;

/**
 * A shift of a ponder scene's view, in blocks along the scene's own axes. Ponder's scene transform
 * carries one through {@code SceneTransformMixin}; it starts at nothing with every playthrough,
 * since Ponder makes a new transform each time a scene begins.
 */
public interface SceneViewOffset {
    void zps$setViewOffset(float x, float y, float z);
}
