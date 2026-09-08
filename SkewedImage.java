package graphics;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;

/**
 * An image warped onto an arbitrary quadrilateral, given as four independent
 * corner {@link Point}s (top-left, top-right, bottom-right, bottom-left, in
 * that order). Unlike {@link Image} -- which can only be translated, scaled,
 * or rotated, all transforms {@link AffineTransform} can express -- each
 * corner of a {@code SkewedImage} can be dragged anywhere, and the image
 * content stretches/warps to fill whatever quad the four corners describe.
 * <p>
 * <b>Performance -- please read before using this on a lot of images or
 * animating it every frame.</b> {@link Graphics2D#drawImage} and
 * {@link AffineTransform} are hardware-accelerated, but they can only
 * represent affine transforms (translate/rotate/scale/shear) -- geometrically,
 * that means they can map a rectangle onto any <em>parallelogram</em>, but
 * never onto an arbitrary quadrilateral (e.g. a trapezoid, or any quad where
 * the four corners are moved independently) -- that needs a full projective
 * ("perspective") transform, and Java2D has no built-in, hardware-accelerated
 * way to draw an image through one.
 * <p>
 * So this class does two things depending on the current corners:
 * <ul>
 *   <li><b>If the four corners currently form a parallelogram</b> (including
 *   the common cases of a plain rectangle, or a shear/rotate of one), it
 *   takes the fast path: builds a 3-point {@link AffineTransform} and calls
 *   {@code drawImage} directly, exactly like {@link Image} does. This is
 *   cheap and hardware-accelerated -- animating this case every frame is
 *   fine.</li>
 *   <li><b>Otherwise</b> (a true 4-independent-corner warp), it falls back to
 *   a software per-pixel perspective warp: for every pixel in the
 *   destination quad's bounding box, it inverse-maps back to a source pixel
 *   and bilinear-samples it. This is plain CPU work, single-threaded, and
 *   its cost scales with the on-screen area of the quad -- for a few hundred
 *   pixels square it's essentially instant, but a full-window-sized image
 *   warped every frame (e.g. its corners animated with {@code moveX(...,
 *   time, ...)}) can noticeably cost FPS, especially with several of them on
 *   screen at once. To keep this cheap for the common case, the warped
 *   result is cached and only rebuilt when the corner points actually move
 *   -- a {@code SkewedImage} that is drawn once and left alone (or only has
 *   its parallelogram-shaped corners animated) costs one warp, not one per
 *   frame.</li>
 * </ul>
 * If you know you only need shearing/rotation/scaling (no independent
 * corners), keeping the corners as a parallelogram gets you the fast path
 * for free. If you need true independent-corner warping animated every
 * frame, test it with your actual image sizes -- shrinking the source image
 * first (e.g. with an image editor, or a one-time {@code setScale}-like
 * downsize) directly shrinks the per-frame cost.
 * <p>
 * <b>Many instances of the same file/URL are cheap.</b> The decoded pixels
 * (an {@code int[]} the size of the image, plus the TYPE_INT_ARGB copy used
 * by the fast path) are decoded once per unique file path or URL and shared
 * -- by reference, never copied or mutated afterward -- by every
 * {@code SkewedImage} constructed from that same source, keyed by the exact
 * string/URL passed in. So e.g. 100 {@code SkewedImage}s all built from the
 * same {@code "photo.jpg"} cost one decode and one pixel array total, not
 * 100 -- important if you're building something like a field of many
 * image-textured objects. (Different-looking paths that happen to resolve
 * to the same file, e.g. a relative path vs. its absolute form, are treated
 * as different cache entries and decoded separately -- pass the exact same
 * string/URL to share.)
 */
public class SkewedImage implements GraphicsObject {

    /** Decoded, ready-to-sample image data shared by every SkewedImage built from the same source. */
    private static final class SharedImage {
        final BufferedImage buffered; // TYPE_INT_ARGB, used for the affine fast path
        final int[] pixels;           // buffered's pixels, pre-extracted once for fast sampling
        final int width, height;

        SharedImage(BufferedImage buffered, int[] pixels, int width, int height) {
            this.buffered = buffered;
            this.pixels = pixels;
            this.width = width;
            this.height = height;
        }
    }

    // Keyed by "path:<filePath>" or "url:<external form>" so a file path and
    // a URL never collide even if they'd resolve to the same bytes.
    private static final Map<String, SharedImage> IMAGE_CACHE = new ConcurrentHashMap<>();

    private BufferedImage original; // == the cached SharedImage's buffered image; never mutated
    private int[] srcPixels;        // == the cached SharedImage's pixels; never mutated
    private int srcW, srcH;

    private Point topLeft, topRight, bottomRight, bottomLeft;
    private GraphWin canvas;
    private String filePath;
    private Color outlineColor = null;
    private int outlineWidth = 1;

    // Warp cache: rebuilt only when the corners have actually changed since
    // the last render, so a static (or parallelogram-only-animated, which
    // never touches this cache) SkewedImage isn't re-warped every frame.
    private BufferedImage warpedCache;
    private int cacheOriginX, cacheOriginY;
    private double cachedTLx, cachedTLy, cachedTRx, cachedTRy,
                   cachedBRx, cachedBRy, cachedBLx, cachedBLy;
    private boolean cacheValid = false;

    /**
     * Constructs a SkewedImage from a file path, mapped onto the given four
     * corners.
     *
     * @param filePath    path to the image file (same lookup rules as {@link Image})
     * @param topLeft     the point the image's top-left corner is stretched to
     * @param topRight    the point the image's top-right corner is stretched to
     * @param bottomRight the point the image's bottom-right corner is stretched to
     * @param bottomLeft  the point the image's bottom-left corner is stretched to
     */
    public SkewedImage(String filePath, Point topLeft, Point topRight, Point bottomRight, Point bottomLeft) {
        this.filePath = filePath;
        applyShared(IMAGE_CACHE.computeIfAbsent("path:" + filePath, k -> {
            try {
                return toShared(loadImage(filePath));
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }));
        this.topLeft = topLeft;
        this.topRight = topRight;
        this.bottomRight = bottomRight;
        this.bottomLeft = bottomLeft;
    }

    /**
     * Constructs a SkewedImage from a resource/URL, mapped onto the given
     * four corners.
     */
    public SkewedImage(URL filePath, Point topLeft, Point topRight, Point bottomRight, Point bottomLeft) {
        this.filePath = filePath.getFile();
        applyShared(IMAGE_CACHE.computeIfAbsent("url:" + filePath.toExternalForm(), k -> {
            try {
                return toShared(ImageIO.read(filePath));
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }));
        this.topLeft = topLeft;
        this.topRight = topRight;
        this.bottomRight = bottomRight;
        this.bottomLeft = bottomLeft;
    }

    /** Same file-lookup behavior as {@link Image#loadImage}. */
    private BufferedImage loadImage(String filePath) throws IOException {
        File file = new File(filePath);
        if (file.isAbsolute() && file.exists()) {
            return ImageIO.read(file);
        }

        URL resource = this.getClass().getResource(filePath);
        if (resource != null) {
            return ImageIO.read(resource);
        }

        file = new File(System.getProperty("user.dir"), filePath);
        if (file.exists()) {
            return ImageIO.read(file);
        }

        throw new IOException("Image file not found: " + filePath);
    }

    /**
     * Converts the source image to TYPE_INT_ARGB (if it isn't already) and
     * pre-extracts its pixels into a plain int[] once, so the per-pixel warp
     * doesn't pay BufferedImage.getRGB's per-call overhead on every sample.
     * Called at most once per unique source (see {@link #IMAGE_CACHE}), not
     * once per instance.
     */
    private static SharedImage toShared(BufferedImage img) {
        if (img.getType() != BufferedImage.TYPE_INT_ARGB) {
            BufferedImage converted = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = converted.createGraphics();
            g2d.drawImage(img, 0, 0, null);
            g2d.dispose();
            img = converted;
        }
        int w = img.getWidth();
        int h = img.getHeight();
        int[] pixels = img.getRGB(0, 0, w, h, null, 0, w);
        return new SharedImage(img, pixels, w, h);
    }

    /** Points this instance's image fields at a (possibly null, if loading failed) shared, cached image. */
    private void applyShared(SharedImage shared) {
        if (shared == null) {
            return; // load failed; fields stay at their zero/null defaults, same as the old failure behavior
        }
        this.original = shared.buffered;
        this.srcPixels = shared.pixels;
        this.srcW = shared.width;
        this.srcH = shared.height;
    }

    // ---------------------------------------------------------------
    // Corner access
    // ---------------------------------------------------------------

    public Point getTopLeft() {
        return topLeft;
    }

    public Point getTopRight() {
        return topRight;
    }

    public Point getBottomRight() {
        return bottomRight;
    }

    public Point getBottomLeft() {
        return bottomLeft;
    }

    /** @return the four corners, in order: top-left, top-right, bottom-right, bottom-left. */
    public Point[] getPoints() {
        return new Point[] { topLeft, topRight, bottomRight, bottomLeft };
    }

    /** Replaces the top-left corner point. */
    public void setTopLeft(Point p) {
        this.topLeft = p;
    }

    /** Replaces the top-right corner point. */
    public void setTopRight(Point p) {
        this.topRight = p;
    }

    /** Replaces the bottom-right corner point. */
    public void setBottomRight(Point p) {
        this.bottomRight = p;
    }

    /** Replaces the bottom-left corner point. */
    public void setBottomLeft(Point p) {
        this.bottomLeft = p;
    }

    /**
     * Sets all four corners at once.
     *
     * @param topLeft     the new top-left corner
     * @param topRight    the new top-right corner
     * @param bottomRight the new bottom-right corner
     * @param bottomLeft  the new bottom-left corner
     */
    public void setPoints(Point topLeft, Point topRight, Point bottomRight, Point bottomLeft) {
        this.topLeft = topLeft;
        this.topRight = topRight;
        this.bottomRight = bottomRight;
        this.bottomLeft = bottomLeft;
        if (canvas != null) {
            canvas.update();
        }
    }

    // ---------------------------------------------------------------
    // Outline (matches Image's outline support)
    // ---------------------------------------------------------------

    public void setOutline(Color color) {
        this.outlineColor = color;
    }

    public void setOutlineWidth(int width) {
        this.outlineWidth = width;
    }

    // ---------------------------------------------------------------
    // Moving all four corners together (mirrors Image.move)
    // ---------------------------------------------------------------

    /**
     * Moves all four corners by the same offset, i.e. translates the whole
     * skewed image without changing its shape.
     */
    public void move(double dx, double dy) {
        topLeft.move(dx, dy);
        topRight.move(dx, dy);
        bottomRight.move(dx, dy);
        bottomLeft.move(dx, dy);
        if (canvas != null) {
            canvas.update();
        }
    }

    /** Moves all four corners smoothly by the same offset over {@code time} seconds. */
    public void move(double dx, double dy, double time) {
        move(dx, dy, time, EasingStyle.LINEAR, EasingDirection.IN);
    }

    /**
     * Smoothly moves all four corners by (dx, dy) over a specified time
     * using the given easing style and direction.
     */
    public void move(double dx, double dy, double time, EasingStyle easingStyle, EasingDirection easingDirection) {
        final Point[] corners = { topLeft, topRight, bottomRight, bottomLeft };
        final double[] startX = new double[4];
        final double[] startY = new double[4];
        for (int i = 0; i < 4; i++) {
            startX[i] = corners[i].getX();
            startY[i] = corners[i].getY();
        }
        Animator.animate(this, time, easingStyle, easingDirection,
            progress -> {
                for (int i = 0; i < 4; i++) {
                    corners[i].moveTo(startX[i] + dx * progress, startY[i] + dy * progress);
                }
            },
            () -> {
                for (int i = 0; i < 4; i++) {
                    corners[i].moveTo(startX[i] + dx, startY[i] + dy);
                }
            },
            () -> this.canvas);
    }

    // ---------------------------------------------------------------
    // Moving individual corners
    // ---------------------------------------------------------------

    public void moveTopLeft(double dx, double dy) {
        topLeft.move(dx, dy);
        if (canvas != null) {
            canvas.update();
        }
    }

    public void moveTopRight(double dx, double dy) {
        topRight.move(dx, dy);
        if (canvas != null) {
            canvas.update();
        }
    }

    public void moveBottomRight(double dx, double dy) {
        bottomRight.move(dx, dy);
        if (canvas != null) {
            canvas.update();
        }
    }

    public void moveBottomLeft(double dx, double dy) {
        bottomLeft.move(dx, dy);
        if (canvas != null) {
            canvas.update();
        }
    }

    public void moveTopLeft(double dx, double dy, double time) {
        moveTopLeft(dx, dy, time, EasingStyle.LINEAR, EasingDirection.IN);
    }

    public void moveTopRight(double dx, double dy, double time) {
        moveTopRight(dx, dy, time, EasingStyle.LINEAR, EasingDirection.IN);
    }

    public void moveBottomRight(double dx, double dy, double time) {
        moveBottomRight(dx, dy, time, EasingStyle.LINEAR, EasingDirection.IN);
    }

    public void moveBottomLeft(double dx, double dy, double time) {
        moveBottomLeft(dx, dy, time, EasingStyle.LINEAR, EasingDirection.IN);
    }

    public void moveTopLeft(double dx, double dy, double time, EasingStyle style, EasingDirection direction) {
        animateCorner(topLeft, dx, dy, time, style, direction);
    }

    public void moveTopRight(double dx, double dy, double time, EasingStyle style, EasingDirection direction) {
        animateCorner(topRight, dx, dy, time, style, direction);
    }

    public void moveBottomRight(double dx, double dy, double time, EasingStyle style, EasingDirection direction) {
        animateCorner(bottomRight, dx, dy, time, style, direction);
    }

    public void moveBottomLeft(double dx, double dy, double time, EasingStyle style, EasingDirection direction) {
        animateCorner(bottomLeft, dx, dy, time, style, direction);
    }

    /**
     * Shared timed-move implementation for a single corner. Uses the corner
     * {@link Point} itself as the Animator key (so re-calling this on the
     * same corner replaces rather than races the previous animation) and
     * this SkewedImage's own canvas as the repaint target (the corner Points
     * are never drawn on their own, so their internal canvas field is never
     * set).
     */
    private void animateCorner(Point corner, double dx, double dy, double time,
                                EasingStyle style, EasingDirection direction) {
        final double startX = corner.getX();
        final double startY = corner.getY();
        Animator.animate(corner, time, style, direction,
            progress -> corner.moveTo(startX + dx * progress, startY + dy * progress),
            () -> corner.moveTo(startX + dx, startY + dy),
            () -> this.canvas);
    }

    // ---------------------------------------------------------------
    // draw / undraw
    // ---------------------------------------------------------------

    @Override
    public void draw(GraphWin canvas) {
        if (this.canvas != null) {
            throw new IllegalStateException("Object is already drawn");
        }
        this.canvas = canvas;
        canvas.addItem(this);
    }

    @Override
    public void undraw() {
        if (canvas != null) {
            canvas.deleteItem(this);
            this.canvas = null;
        }
        Animator.cancel(this);
        Animator.cancel(topLeft);
        Animator.cancel(topRight);
        Animator.cancel(bottomRight);
        Animator.cancel(bottomLeft);
    }

    // ---------------------------------------------------------------
    // Rendering
    // ---------------------------------------------------------------

    @Override
    public void drawPanel(Graphics2D graphics) {
        double x0 = topLeft.getX(), y0 = topLeft.getY();
        double x1 = topRight.getX(), y1 = topRight.getY();
        double x2 = bottomRight.getX(), y2 = bottomRight.getY();
        double x3 = bottomLeft.getX(), y3 = bottomLeft.getY();

        // dx3/dy3 measure how far the quad deviates from a parallelogram:
        // in a true parallelogram, corner3 = corner0 + (corner2 - corner1),
        // i.e. x0 - x1 + x2 - x3 == 0 (and same for y). When that holds (or
        // is within half a pixel of holding), the mapping is a plain affine
        // transform and we can use the fast, hardware-accelerated path.
        double dx3 = x0 - x1 + x2 - x3;
        double dy3 = y0 - y1 + y2 - y3;

        if (Math.abs(dx3) < 0.5 && Math.abs(dy3) < 0.5) {
            drawParallelogramFast(graphics, x0, y0, x1, y1, x3, y3);
        } else {
            drawPerspectiveWarp(graphics, x0, y0, x1, y1, x2, y2, x3, y3);
        }

        if (outlineColor != null) {
            graphics.setColor(outlineColor);
            graphics.setStroke(new BasicStroke(outlineWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int[] xs = { (int) x0, (int) x1, (int) x2, (int) x3 };
            int[] ys = { (int) y0, (int) y1, (int) y2, (int) y3 };
            graphics.drawPolygon(xs, ys, 4);
        }
    }

    /**
     * Fast path: the quad is a parallelogram, so a single 3-point
     * {@link AffineTransform} maps the source image onto it exactly, and
     * Java2D draws it with the same hardware-accelerated path {@link Image}
     * uses. No caching needed -- this is cheap enough to redo every frame.
     */
    private void drawParallelogramFast(Graphics2D graphics, double x0, double y0,
                                        double x1, double y1, double x3, double y3) {
        // Maps source (0,0) -> (x0,y0), (srcW,0) -> (x1,y1), (0,srcH) -> (x3,y3).
        double m00 = (x1 - x0) / srcW;
        double m10 = (y1 - y0) / srcW;
        double m01 = (x3 - x0) / srcH;
        double m11 = (y3 - y0) / srcH;
        AffineTransform t = new AffineTransform(m00, m10, m01, m11, x0, y0);
        graphics.drawImage(original, t, null);
        cacheValid = false; // fast path never uses the warp cache; invalidate in case corners un-parallelogram later
    }

    /**
     * Slow path: a true 4-independent-corner perspective warp, done in
     * software. Rebuilds {@link #warpedCache} only if the corners have
     * changed since the last call.
     */
    private void drawPerspectiveWarp(Graphics2D graphics, double x0, double y0, double x1, double y1,
                                      double x2, double y2, double x3, double y3) {
        if (!cacheValid || warpedCache == null
                || x0 != cachedTLx || y0 != cachedTLy
                || x1 != cachedTRx || y1 != cachedTRy
                || x2 != cachedBRx || y2 != cachedBRy
                || x3 != cachedBLx || y3 != cachedBLy) {
            rebuildWarp(x0, y0, x1, y1, x2, y2, x3, y3);
        }
        if (warpedCache != null) {
            graphics.drawImage(warpedCache, cacheOriginX, cacheOriginY, null);
        }
    }

    /**
     * Computes the projective ("perspective") map from the unit square
     * (0,0)-(1,0)-(1,1)-(0,1) to the quad (x0,y0)-(x1,y1)-(x2,y2)-(x3,y3)
     * (Heckbert's classic unit-square-to-quad derivation), then rasterizes
     * the destination quad's bounding box by inverse-mapping each pixel back
     * to a fractional (u, v) in the unit square, converting that to a source
     * pixel, and bilinear-sampling it.
     */
    private void rebuildWarp(double x0, double y0, double x1, double y1,
                              double x2, double y2, double x3, double y3) {
        double dx1 = x1 - x2, dx2 = x3 - x2, dx3 = x0 - x1 + x2 - x3;
        double dy1 = y1 - y2, dy2 = y3 - y2, dy3 = y0 - y1 + y2 - y3;

        double a, b, c = x0, d, e, f = y0, g, h;

        double denom = dx1 * dy2 - dy1 * dx2;
        if (denom == 0) {
            // Degenerate (collinear corners); fall back to treating it like
            // a parallelogram rather than producing garbage/NaNs.
            g = 0;
            h = 0;
        } else {
            g = (dx3 * dy2 - dy3 * dx2) / denom;
            h = (dx1 * dy3 - dy1 * dx3) / denom;
        }
        a = x1 - x0 + g * x1;
        b = x3 - x0 + h * x3;
        d = y1 - y0 + g * y1;
        e = y3 - y0 + h * y3;

        int minX = (int) Math.floor(Math.min(Math.min(x0, x1), Math.min(x2, x3)));
        int maxX = (int) Math.ceil(Math.max(Math.max(x0, x1), Math.max(x2, x3)));
        int minY = (int) Math.floor(Math.min(Math.min(y0, y1), Math.min(y2, y3)));
        int maxY = (int) Math.ceil(Math.max(Math.max(y0, y1), Math.max(y2, y3)));
        int bw = Math.max(1, maxX - minX);
        int bh = Math.max(1, maxY - minY);

        BufferedImage out = new BufferedImage(bw, bh, BufferedImage.TYPE_INT_ARGB);
        int[] outPixels = new int[bw * bh];

        for (int py = 0; py < bh; py++) {
            double Y = minY + py + 0.5;
            int rowBase = py * bw;
            for (int px = 0; px < bw; px++) {
                double X = minX + px + 0.5;

                // Invert X = (a*u + b*v + c) / (g*u + h*v + 1),
                //        Y = (d*u + e*v + f) / (g*u + h*v + 1)
                // by solving the resulting 2x2 linear system in (u, v).
                double A1 = a - X * g, B1 = b - X * h, C1 = X - c;
                double A2 = d - Y * g, B2 = e - Y * h, C2 = Y - f;
                double det = A1 * B2 - A2 * B1;
                if (det == 0) {
                    continue; // leave transparent
                }
                double u = (C1 * B2 - C2 * B1) / det;
                double v = (A1 * C2 - A2 * C1) / det;

                if (u < 0 || u > 1 || v < 0 || v > 1) {
                    continue; // outside the quad; leave transparent
                }

                double sx = u * (srcW - 1);
                double sy = v * (srcH - 1);
                outPixels[rowBase + px] = sampleBilinear(sx, sy);
            }
        }

        out.setRGB(0, 0, bw, bh, outPixels, 0, bw);

        this.warpedCache = out;
        this.cacheOriginX = minX;
        this.cacheOriginY = minY;
        this.cachedTLx = x0; this.cachedTLy = y0;
        this.cachedTRx = x1; this.cachedTRy = y1;
        this.cachedBRx = x2; this.cachedBRy = y2;
        this.cachedBLx = x3; this.cachedBLy = y3;
        this.cacheValid = true;
    }

    /** Bilinear-samples {@link #srcPixels} at the fractional source coordinate (sx, sy). */
    private int sampleBilinear(double sx, double sy) {
        int x0 = (int) Math.floor(sx);
        int y0 = (int) Math.floor(sy);
        int x1 = Math.min(x0 + 1, srcW - 1);
        int y1 = Math.min(y0 + 1, srcH - 1);
        x0 = Math.max(0, Math.min(x0, srcW - 1));
        y0 = Math.max(0, Math.min(y0, srcH - 1));
        double fx = sx - x0;
        double fy = sy - y0;

        int c00 = srcPixels[y0 * srcW + x0];
        int c10 = srcPixels[y0 * srcW + x1];
        int c01 = srcPixels[y1 * srcW + x0];
        int c11 = srcPixels[y1 * srcW + x1];

        double w00 = (1 - fx) * (1 - fy);
        double w10 = fx * (1 - fy);
        double w01 = (1 - fx) * fy;
        double w11 = fx * fy;

        int a = clamp(channel(c00, 24) * w00 + channel(c10, 24) * w10 + channel(c01, 24) * w01 + channel(c11, 24) * w11);
        int r = clamp(channel(c00, 16) * w00 + channel(c10, 16) * w10 + channel(c01, 16) * w01 + channel(c11, 16) * w11);
        int gr = clamp(channel(c00, 8) * w00 + channel(c10, 8) * w10 + channel(c01, 8) * w01 + channel(c11, 8) * w11);
        int b = clamp(channel(c00, 0) * w00 + channel(c10, 0) * w10 + channel(c01, 0) * w01 + channel(c11, 0) * w11);

        return (a << 24) | (r << 16) | (gr << 8) | b;
    }

    private static int channel(int argb, int shift) {
        return (argb >> shift) & 0xFF;
    }

    private static int clamp(double v) {
        int i = (int) Math.round(v);
        return Math.max(0, Math.min(255, i));
    }

    @Override
    public String toString() {
        return String.format("SkewedImage(filePath=%s, topLeft=%s, topRight=%s, bottomRight=%s, bottomLeft=%s)",
            filePath, topLeft, topRight, bottomRight, bottomLeft);
    }
}