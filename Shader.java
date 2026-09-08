package graphics;

import java.awt.image.BufferedImage;

/**
 * Processes the fully-rendered frame of a {@link GraphWin} before it's
 * displayed on screen -- a "post-processing" or "screen-space" effect
 * applied to the whole scene at once, rather than to any single
 * {@link GraphicsObject}.
 * <p>
 * A {@code GraphWin} can have any number of shaders registered via
 * {@link GraphWin#addShader(Shader)}; they run once per frame, in the order
 * they were added, each one receiving the output of the previous one. If no
 * shaders are registered, {@code GraphWin} skips the offscreen buffer
 * entirely and draws objects straight to the screen as before, so there's no
 * performance cost for programs that don't use this feature.
 * <p>
 * Because this is a plain functional interface, a shader can be written as
 * a lambda for quick one-off effects:
 * <pre>{@code
 * window.addShader((frame, time) -> {
 *     int w = frame.getWidth(), h = frame.getHeight();
 *     int[] px = frame.getRGB(0, 0, w, h, null, 0, w);
 *     for (int i = 0; i < px.length; i++) {
 *         int a = px[i] & 0xFF000000;
 *         int r = 255 - ((px[i] >> 16) & 0xFF);
 *         int g = 255 - ((px[i] >> 8) & 0xFF);
 *         int b = 255 - (px[i] & 0xFF);
 *         px[i] = a | (r << 16) | (g << 8) | b;
 *     }
 *     frame.setRGB(0, 0, w, h, px, 0, w);
 *     return frame;
 * });
 * }</pre>
 * or as a named class when the effect takes parameters, keeps internal
 * state, or is meant to be reused across projects -- see
 * {@link ChromaticAberration} for an example.
 */
@FunctionalInterface
public interface Shader {

    /**
     * Processes one frame.
     * <p>
     * Implementations may either mutate {@code frame} in place and return
     * it, or allocate and return a brand-new {@code BufferedImage} (e.g.
     * when the effect needs to read pixels at their original, unmodified
     * positions while writing to different ones, like a pixel-shift or
     * warp effect). Either way, whatever is returned is what gets passed to
     * the next shader in the chain, and ultimately drawn to the window.
     * <p>
     * This runs synchronously on the Swing event dispatch thread as part of
     * every repaint, so it should avoid unnecessarily slow work (e.g.
     * reallocating a new {@code BufferedImage} every call when a reusable
     * field would do).
     *
     * @param frame the frame as rendered so far this update (either the raw
     *              scene, or the output of the previous shader in the chain)
     * @param time  seconds elapsed since the {@code GraphWin} was created,
     *              for shaders whose effect should animate or vary over time
     * @return the processed frame to hand to the next shader, or to display
     *         if this is the last one
     */
    BufferedImage apply(BufferedImage frame, double time);
}