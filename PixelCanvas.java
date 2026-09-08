package graphics;
 
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
 
/**
 * A rectangular canvas of individually-colorable pixels, each with its own
 * RGBA color -- a pixel with alpha 0 (the default for every pixel until you
 * set it) is fully transparent, so whatever is drawn behind the
 * {@code PixelCanvas} shows through it.
 * <p>
 * The canvas has two independent sizes, and this is the whole point of the
 * class:
 * <ul>
 *   <li><b>Resolution</b> -- the pixel grid's width/height (e.g. 32x32).
 *   Fixed at construction, since it's the canvas's actual data -- resizing
 *   it would mean deciding how to resample or discard existing pixels,
 *   which is a decision better left to the caller (build a new
 *   {@code PixelCanvas} and copy over whatever pixels make sense with
 *   {@link #getPixel}/{@link #setPixel} if you need that).</li>
 *   <li><b>Display size</b> -- how many actual screen pixels the canvas
 *   occupies, set independently via the constructor or {@link #setSize}
 *   and changeable at any time, same as {@link Image#setSize}. A 16x16
 *   resolution canvas displayed at 400x400 gives you 16x16 chunky "pixel
 *   art" pixels; the same resolution displayed at 16x16 gives you a 1:1
 *   pixel-for-pixel canvas.</li>
 * </ul>
 * By default the display is scaled with nearest-neighbor sampling (crisp,
 * blocky pixel-art look, matching what people expect from a low-resolution
 * canvas stretched larger) -- call {@link #setPixelated(boolean)
 * setPixelated(false)} for smooth/blurred bilinear scaling instead.
 * <p>
 * Individual {@link #setPixel} calls are cheap (just an array write); the
 * pixel data is only pushed into the actual renderable image once per frame,
 * the first time the canvas is drawn after a pixel changed -- so painting
 * many pixels between frames (e.g. an entire generated image, or a flood
 * fill) doesn't cost more than painting one.
 * <p>
 * A handful of drawing helpers ({@link #drawLine}, {@link #drawRect},
 * {@link #drawCircle}, {@link #fill}, {@link #clear}) are included for
 * convenience, plus {@link #screenToPixel} to map a mouse position (e.g.
 * from {@link GraphWin#getCurrentMousePosition()}) back to a pixel
 * coordinate -- handy for building a paint/pixel-editor tool on top of this.
 */
public class PixelCanvas implements GraphicsObject {
 
    private final int resolutionWidth, resolutionHeight;
    private final int[] pixels; // ARGB, row-major, length == resolutionWidth * resolutionHeight
 
    private int displayWidth, displayHeight;
    private Point position;
    private String alignment = "top-left"; // "top-left", "top-right", "bottom-left", "bottom-right", "center"
 
    private boolean pixelated = true; // nearest-neighbor scaling by default; false = smooth/bilinear
 
    private Color outlineColor = null;
    private int outlineWidth = 1;
 
    private GraphWin canvas; // the GraphWin this is drawn on (unrelated to the pixel data above)
 
    private final BufferedImage renderImage; // TYPE_INT_ARGB, resolutionWidth x resolutionHeight
    private boolean dirty = true; // true when `pixels` has changed since renderImage was last updated
 
    /**
     * Constructs a PixelCanvas with its display size equal to its
     * resolution (one screen pixel per canvas pixel).
     *
     * @param position          top-left (or per {@link #setAlignment}, whichever corner/center) anchor point
     * @param resolutionWidth   pixel grid width; must be positive
     * @param resolutionHeight  pixel grid height; must be positive
     */
    public PixelCanvas(Point position, int resolutionWidth, int resolutionHeight) {
        this(position, resolutionWidth, resolutionHeight, resolutionWidth, resolutionHeight);
    }
 
    /**
     * Constructs a PixelCanvas with an explicit display size, independent of
     * its resolution.
     *
     * @param position          top-left (or per {@link #setAlignment}, whichever corner/center) anchor point
     * @param resolutionWidth   pixel grid width; must be positive
     * @param resolutionHeight  pixel grid height; must be positive
     * @param displayWidth      on-screen width in pixels; must be positive
     * @param displayHeight     on-screen height in pixels; must be positive
     */
    public PixelCanvas(Point position, int resolutionWidth, int resolutionHeight, int displayWidth, int displayHeight) {
        if (resolutionWidth <= 0 || resolutionHeight <= 0) {
            throw new IllegalArgumentException("resolutionWidth/resolutionHeight must be positive");
        }
        if (displayWidth <= 0 || displayHeight <= 0) {
            throw new IllegalArgumentException("displayWidth/displayHeight must be positive");
        }
        this.position = position;
        this.resolutionWidth = resolutionWidth;
        this.resolutionHeight = resolutionHeight;
        this.displayWidth = displayWidth;
        this.displayHeight = displayHeight;
        this.pixels = new int[resolutionWidth * resolutionHeight]; // all 0 == fully transparent black
        this.renderImage = new BufferedImage(resolutionWidth, resolutionHeight, BufferedImage.TYPE_INT_ARGB);
    }
 
    // ---------------------------------------------------------------
    // Pixel data
    // ---------------------------------------------------------------
 
    /**
     * Sets one pixel's color, including alpha. Out-of-bounds coordinates are
     * silently ignored (rather than throwing) so generative drawing code --
     * Bresenham lines, flood fills, procedural noise -- doesn't need to
     * clip itself against the canvas edges by hand.
     *
     * @param x     pixel column, 0 .. {@link #getResolutionWidth()} - 1
     * @param y     pixel row, 0 .. {@link #getResolutionHeight()} - 1
     * @param red   0-255
     * @param green 0-255
     * @param blue  0-255
     * @param alpha 0-255 (0 = fully transparent/see-through, 255 = fully opaque)
     */
    public void setPixel(int x, int y, int red, int green, int blue, int alpha) {
        if (x < 0 || x >= resolutionWidth || y < 0 || y >= resolutionHeight) {
            return;
        }
        int argb = (alpha << 24) | (red << 16) | (green << 8) | blue;
        pixels[y * resolutionWidth + x] = argb;
        dirty = true;
    }
 
    /** Sets one pixel's color from a {@link Color} (its alpha is used as-is). Out-of-bounds is a silent no-op. */
    public void setPixel(int x, int y, Color color) {
        if (x < 0 || x >= resolutionWidth || y < 0 || y >= resolutionHeight) {
            return;
        }
        pixels[y * resolutionWidth + x] = color.getRGB(); // Color.getRGB() already packs alpha
        dirty = true;
    }
 
    /**
     * Gets one pixel's current color.
     *
     * @return the pixel's color (alpha 0 if never set), or {@code null} if (x, y) is out of bounds
     */
    public Color getPixel(int x, int y) {
        if (x < 0 || x >= resolutionWidth || y < 0 || y >= resolutionHeight) {
            return null;
        }
        return new Color(pixels[y * resolutionWidth + x], true); // true = include alpha
    }
 
    /** Sets every pixel to the given color (including alpha). */
    public void fill(Color color) {
        int argb = color.getRGB();
        java.util.Arrays.fill(pixels, argb);
        dirty = true;
    }
 
    /** Sets every pixel fully transparent, i.e. makes the whole canvas see-through again. */
    public void clear() {
        java.util.Arrays.fill(pixels, 0);
        dirty = true;
    }
 
    /** Draws a straight line of pixels from (x0, y0) to (x1, y1) using Bresenham's algorithm. */
    public void drawLine(int x0, int y0, int x1, int y1, Color color) {
        int dx = Math.abs(x1 - x0), sx = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0), sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        int x = x0, y = y0;
        while (true) {
            setPixel(x, y, color);
            if (x == x1 && y == y1) {
                break;
            }
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                x += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y += sy;
            }
        }
    }
 
    /**
     * Draws a rectangle of pixels with its top-left corner at (x, y).
     *
     * @param filled true to fill the whole rectangle, false to draw just its 1-pixel outline
     */
    public void drawRect(int x, int y, int width, int height, Color color, boolean filled) {
        if (filled) {
            for (int py = y; py < y + height; py++) {
                for (int px = x; px < x + width; px++) {
                    setPixel(px, py, color);
                }
            }
        } else {
            drawLine(x, y, x + width - 1, y, color);
            drawLine(x, y + height - 1, x + width - 1, y + height - 1, color);
            drawLine(x, y, x, y + height - 1, color);
            drawLine(x + width - 1, y, x + width - 1, y + height - 1, color);
        }
    }

    /**
     * Copies a loaded image's pixels (including alpha) directly into the
     * canvas's pixel grid, top-left corner at (x, y), clipped to the
     * canvas -- no scaling. Handy for compositing tile textures, sprites,
     * or anything else loaded from a file into a scene baked with this
     * canvas.
     */
    public void drawImage(BufferedImage image, int x, int y) {
        int w = image.getWidth();
        int h = image.getHeight();
        for (int iy = 0; iy < h; iy++) {
            int py = y + iy;
            if (py < 0 || py >= resolutionHeight) {
                continue;
            }
            for (int ix = 0; ix < w; ix++) {
                int px = x + ix;
                if (px < 0 || px >= resolutionWidth) {
                    continue;
                }
                pixels[py * resolutionWidth + px] = image.getRGB(ix, iy);
            }
        }
        dirty = true;
    }

    /**
     * Same as {@link #drawImage(BufferedImage, int, int)}, but scales the
     * image (nearest-neighbor, to stay crisp for pixel art) to exactly
     * {@code width}x{@code height} first -- for compositing a texture
     * whose file dimensions don't match the space it needs to fill, e.g. a
     * tile texture that isn't exactly your tile size.
     */
    public void drawImage(BufferedImage image, int x, int y, int width, int height) {
        if (width == image.getWidth() && height == image.getHeight()) {
            drawImage(image, x, y);
            return;
        }
        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = scaled.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2d.drawImage(image, 0, 0, width, height, null);
        g2d.dispose();
        drawImage(scaled, x, y);
    }
 
    /**
     * Same as {@link #drawImage(BufferedImage, int, int, int, int)}, but
     * alpha-composites ("over") each pixel onto whatever is already baked
     * into the canvas instead of overwriting it outright -- for stamping a
     * sprite that has transparent regions (a prop, an icon) onto a canvas
     * that already has something opaque underneath it (floor/wall
     * texture), so the transparent parts let that underlying bake show
     * through instead of punching a see-through hole in it.
     */
    public void drawImageBlended(BufferedImage image, int x, int y, int width, int height) {
        BufferedImage scaled;
        if (width == image.getWidth() && height == image.getHeight()) {
            scaled = image;
        } else {
            scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = scaled.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g2d.drawImage(image, 0, 0, width, height, null);
            g2d.dispose();
        }

        int w = scaled.getWidth();
        int h = scaled.getHeight();
        for (int iy = 0; iy < h; iy++) {
            int py = y + iy;
            if (py < 0 || py >= resolutionHeight) {
                continue;
            }
            for (int ix = 0; ix < w; ix++) {
                int px = x + ix;
                if (px < 0 || px >= resolutionWidth) {
                    continue;
                }
                int srcArgb = scaled.getRGB(ix, iy);
                int srcAlpha = (srcArgb >>> 24) & 0xFF;
                if (srcAlpha == 0) {
                    continue; // fully transparent -- leave whatever's already there untouched
                }
                int index = py * resolutionWidth + px;
                if (srcAlpha == 255) {
                    pixels[index] = srcArgb;
                    continue;
                }
                int dstArgb = pixels[index];
                int dstAlpha = (dstArgb >>> 24) & 0xFF;
                double srcA = srcAlpha / 255.0;
                double dstA = dstAlpha / 255.0;
                double outA = srcA + dstA * (1 - srcA);
                int outArgb;
                if (outA <= 1e-6) {
                    outArgb = 0;
                } else {
                    int srcR = (srcArgb >> 16) & 0xFF, srcG = (srcArgb >> 8) & 0xFF, srcB = srcArgb & 0xFF;
                    int dstR = (dstArgb >> 16) & 0xFF, dstG = (dstArgb >> 8) & 0xFF, dstB = dstArgb & 0xFF;
                    int outR = (int) Math.round((srcR * srcA + dstR * dstA * (1 - srcA)) / outA);
                    int outG = (int) Math.round((srcG * srcA + dstG * dstA * (1 - srcA)) / outA);
                    int outB = (int) Math.round((srcB * srcA + dstB * dstA * (1 - srcA)) / outA);
                    outArgb = ((int) Math.round(outA * 255) << 24) | (outR << 16) | (outG << 8) | outB;
                }
                pixels[index] = outArgb;
            }
        }
        dirty = true;
    }

    /**
     * Draws a texture-filled thick "capsule" -- a line segment from
     * (x1, y1) to (x2, y2) with rounded ends -- for walls, roads, cables,
     * or anything else that's "a shape running along a path" rather than
     * sitting in an axis-aligned rectangle.
     * <p>
     * The shape (rounded-end capsule, via true distance-to-segment) is
     * oriented along the line as you'd expect, but the texture itself is
     * sampled by absolute canvas position and tiles as a fixed backdrop
     * -- it does <em>not</em> rotate or skew with the segment's angle, so
     * a brick or tile texture stays upright and consistent across walls
     * running in different directions, the way real wallpaper would.
     * <p>
     * If {@code outlineColor} is non-null, a border of that color
     * {@code outlineWidth} pixels wide is drawn just inside the capsule's
     * whole boundary, rounded ends included. Pass null to skip it.
     *
     * @param thickness total width of the line, i.e. {@code thickness/2} on each side of the center
     */
    public void drawTexturedThickLine(BufferedImage texture, double x1, double y1, double x2, double y2, double thickness) {
        drawTexturedThickLine(texture, x1, y1, x2, y2, thickness, null, 0);
    }

    /** @see #drawTexturedThickLine(BufferedImage, double, double, double, double, double) */
    public void drawTexturedThickLine(BufferedImage texture, double x1, double y1, double x2, double y2, double thickness,
                                       Color outlineColor, double outlineWidth) {
        double dx = x2 - x1, dy = y2 - y1;
        double length = Math.sqrt(dx * dx + dy * dy);
        if (length < 1e-9) {
            return;
        }
        double alongX = dx / length, alongY = dy / length;   // unit vector along the segment
        double halfThickness = thickness / 2.0;

        // Bounding box, generously padded by halfThickness on every side
        // so the rounded end caps (which extend past x1,y1/x2,y2, not just
        // sideways) are fully covered -- the per-pixel distance check
        // below is what actually shapes the capsule precisely.
        int minX = Math.max(0, (int) Math.floor(Math.min(x1, x2) - halfThickness));
        int maxX = Math.min(resolutionWidth - 1, (int) Math.ceil(Math.max(x1, x2) + halfThickness));
        int minY = Math.max(0, (int) Math.floor(Math.min(y1, y2) - halfThickness));
        int maxY = Math.min(resolutionHeight - 1, (int) Math.ceil(Math.max(y1, y2) + halfThickness));

        int texWidth = texture.getWidth(), texHeight = texture.getHeight();
        int outlineArgb = outlineColor != null ? outlineColor.getRGB() : 0;

        for (int py = minY; py <= maxY; py++) {
            for (int px = minX; px <= maxX; px++) {
                double relX = px + 0.5 - x1;
                double relY = py + 0.5 - y1;
                double along = relX * alongX + relY * alongY;     // distance along the segment (for the shape test only)

                // True distance to the segment (clamping "along" to the
                // segment's extent) is what gives the ends their rounding
                // -- past either end, this falls back to distance-to-endpoint.
                double clampedAlong = Math.max(0, Math.min(length, along));
                double capDeltaX = relX - clampedAlong * alongX;
                double capDeltaY = relY - clampedAlong * alongY;
                double distance = Math.sqrt(capDeltaX * capDeltaX + capDeltaY * capDeltaY);
                if (distance > halfThickness) {
                    continue;
                }

                if (outlineColor != null && distance > halfThickness - outlineWidth) {
                    pixels[py * resolutionWidth + px] = outlineArgb;
                    continue;
                }

                // Sampled by absolute canvas position, not by (along,
                // across) -- the texture tiles as a fixed world-aligned
                // backdrop and the capsule just cuts a window into it, so
                // it doesn't rotate/skew as the wall's own angle changes.
                int texX = Math.floorMod(px, texWidth);
                int texY = Math.floorMod(py, texHeight);
                pixels[py * resolutionWidth + px] = texture.getRGB(texX, texY);
            }
        }
        dirty = true;
    }

    /**
     * Marks every canvas pixel within {@code thickness/2} of the segment
     * (x1,y1)-(x2,y2) as {@code true} in the given coverage array
     * (row-major, {@link #getResolutionWidth()} x {@link #getResolutionHeight()}
     * -- same size and indexing as the canvas's own pixel grid). Doesn't
     * touch the canvas's actual pixels at all.
     * <p>
     * Marking several overlapping/touching capsules into the <em>same</em>
     * array and then calling {@link #strokeMaskBoundary} builds their
     * <em>union's</em> outline rather than each one's individual outline
     * -- which is what makes intersecting walls merge seamlessly instead
     * of each drawing its own border across the other.
     */
    public void markCapsuleCoverage(boolean[] coverage, double x1, double y1, double x2, double y2, double thickness) {
        double dx = x2 - x1, dy = y2 - y1;
        double length = Math.sqrt(dx * dx + dy * dy);
        if (length < 1e-9) {
            return;
        }
        double alongX = dx / length, alongY = dy / length;
        double halfThickness = thickness / 2.0;

        int minX = Math.max(0, (int) Math.floor(Math.min(x1, x2) - halfThickness));
        int maxX = Math.min(resolutionWidth - 1, (int) Math.ceil(Math.max(x1, x2) + halfThickness));
        int minY = Math.max(0, (int) Math.floor(Math.min(y1, y2) - halfThickness));
        int maxY = Math.min(resolutionHeight - 1, (int) Math.ceil(Math.max(y1, y2) + halfThickness));

        for (int py = minY; py <= maxY; py++) {
            for (int px = minX; px <= maxX; px++) {
                double relX = px + 0.5 - x1;
                double relY = py + 0.5 - y1;
                double along = relX * alongX + relY * alongY;
                double clampedAlong = Math.max(0, Math.min(length, along));
                double capDeltaX = relX - clampedAlong * alongX;
                double capDeltaY = relY - clampedAlong * alongY;
                double distance = Math.sqrt(capDeltaX * capDeltaX + capDeltaY * capDeltaY);
                if (distance <= halfThickness) {
                    coverage[py * resolutionWidth + px] = true;
                }
            }
        }
    }

    /**
     * Strokes an outline along the boundary of a coverage mask built by
     * (one or more calls to) {@link #markCapsuleCoverage} -- every
     * {@code true} pixel that has an uncovered pixel within
     * {@code outlineWidth} of it gets painted {@code outlineColor}.
     * Internal seams where two marked shapes overlap or touch are, by
     * definition, surrounded by other {@code true} pixels on every side,
     * so they're left alone -- only the union's true outer silhouette
     * gets outlined.
     */
    public void strokeMaskBoundary(boolean[] coverage, Color outlineColor, int outlineWidth) {
        int radiusSquared = outlineWidth * outlineWidth;
        for (int y = 0; y < resolutionHeight; y++) {
            for (int x = 0; x < resolutionWidth; x++) {
                if (!coverage[y * resolutionWidth + x]) {
                    continue;
                }
                boolean nearEdge = false;
                for (int dy = -outlineWidth; dy <= outlineWidth && !nearEdge; dy++) {
                    for (int dx = -outlineWidth; dx <= outlineWidth && !nearEdge; dx++) {
                        if (dx * dx + dy * dy > radiusSquared) {
                            continue;
                        }
                        int nx = x + dx, ny = y + dy;
                        boolean neighborCovered = nx >= 0 && nx < resolutionWidth && ny >= 0 && ny < resolutionHeight
                            && coverage[ny * resolutionWidth + nx];
                        if (!neighborCovered) {
                            nearEdge = true;
                        }
                    }
                }
                if (nearEdge) {
                    setPixel(x, y, outlineColor);
                }
            }
        }
    }

    /**
     * Fills an axis-aligned rectangle with a texture tiled by
     * <em>absolute canvas position</em> rather than by the rectangle's own
     * corner -- so several rectangles sharing the same texture (e.g.
     * adjacent floor tiles) line up seamlessly across their shared edges,
     * the way wallpaper would, instead of each restarting the tiling
     * pattern fresh at its own top-left corner.
     */
    public void fillTiledRect(BufferedImage texture, int x, int y, int width, int height) {
        int texWidth = texture.getWidth(), texHeight = texture.getHeight();
        int minX = Math.max(0, x), maxX = Math.min(resolutionWidth - 1, x + width - 1);
        int minY = Math.max(0, y), maxY = Math.min(resolutionHeight - 1, y + height - 1);
        for (int py = minY; py <= maxY; py++) {
            int texY = Math.floorMod(py, texHeight);
            for (int px = minX; px <= maxX; px++) {
                int texX = Math.floorMod(px, texWidth);
                pixels[py * resolutionWidth + px] = texture.getRGB(texX, texY);
            }
        }
        dirty = true;
    }

    /**
     * Draws a circle of pixels centered at (centerX, centerY) using the
     * midpoint circle algorithm.
     *
     * @param filled true to fill the whole disc, false to draw just its 1-pixel outline
     */
    public void drawCircle(int centerX, int centerY, int radius, Color color, boolean filled) {
        int x = radius, y = 0, err = 0;
        while (x >= y) {
            if (filled) {
                drawLine(centerX - x, centerY + y, centerX + x, centerY + y, color);
                drawLine(centerX - x, centerY - y, centerX + x, centerY - y, color);
                drawLine(centerX - y, centerY + x, centerX + y, centerY + x, color);
                drawLine(centerX - y, centerY - x, centerX + y, centerY - x, color);
            } else {
                setPixel(centerX + x, centerY + y, color);
                setPixel(centerX + y, centerY + x, color);
                setPixel(centerX - y, centerY + x, color);
                setPixel(centerX - x, centerY + y, color);
                setPixel(centerX - x, centerY - y, color);
                setPixel(centerX - y, centerY - x, color);
                setPixel(centerX + y, centerY - x, color);
                setPixel(centerX + x, centerY - y, color);
            }
            y += 1;
            if (err <= 0) {
                err += 2 * y + 1;
            }
            if (err > 0) {
                x -= 1;
                err -= 2 * x + 1;
            }
        }
    }
 
    public int getResolutionWidth() {
        return resolutionWidth;
    }
 
    public int getResolutionHeight() {
        return resolutionHeight;
    }
 
    // ---------------------------------------------------------------
    // Display size / position (independent of resolution/pixel data)
    // ---------------------------------------------------------------
 
    public int getDisplayWidth() {
        return displayWidth;
    }
 
    public int getDisplayHeight() {
        return displayHeight;
    }
 
    /** Resizes the canvas on screen. Does not touch pixel data or resolution. */
    public void setSize(int displayWidth, int displayHeight) {
        if (displayWidth <= 0 || displayHeight <= 0) {
            throw new IllegalArgumentException("displayWidth/displayHeight must be positive");
        }
        this.displayWidth = displayWidth;
        this.displayHeight = displayHeight;
        if (canvas != null) {
            canvas.update();
        }
    }
 
    /** Scales the current display size by a factor (e.g. 2.0 doubles it, 0.5 halves it). */
    public void scale(double factor) {
        setSize(Math.max(1, (int) Math.round(displayWidth * factor)), Math.max(1, (int) Math.round(displayHeight * factor)));
    }
 
    /**
     * Sets the anchor alignment of {@code position} relative to the
     * rendered canvas, exactly like {@link Image#setAlignment}.
     * Supported values: "top-left", "top-right", "bottom-left", "bottom-right", "center".
     */
    public void setAlignment(String alignment) {
        if (!alignment.equals("top-left") && !alignment.equals("top-right") &&
            !alignment.equals("bottom-left") && !alignment.equals("bottom-right") &&
            !alignment.equals("center")) {
            throw new IllegalArgumentException("Invalid alignment value: " + alignment);
        }
        this.alignment = alignment;
    }
 
    private int getAlignedX() {
        switch (alignment) {
            case "top-right":
            case "bottom-right":
                return (int) (position.getX() - displayWidth);
            case "center":
                return (int) (position.getX() - displayWidth / 2);
            default: // "top-left", "bottom-left"
                return (int) position.getX();
        }
    }
 
    private int getAlignedY() {
        switch (alignment) {
            case "bottom-left":
            case "bottom-right":
                return (int) (position.getY() - displayHeight);
            case "center":
                return (int) (position.getY() - displayHeight / 2);
            default: // "top-left", "top-right"
                return (int) position.getY();
        }
    }
 
    /**
     * Maps a point in screen space (e.g. {@link GraphWin#getCurrentMousePosition()})
     * to the pixel coordinate underneath it, accounting for the canvas's
     * current position, alignment, display size, and resolution.
     *
     * @return {@code {pixelX, pixelY}}, or {@code null} if the screen point isn't over this canvas
     */
    public int[] screenToPixel(double screenX, double screenY) {
        double relativeX = screenX - getAlignedX();
        double relativeY = screenY - getAlignedY();
        if (relativeX < 0 || relativeX >= displayWidth || relativeY < 0 || relativeY >= displayHeight) {
            return null;
        }
        int px = (int) (relativeX / displayWidth * resolutionWidth);
        int py = (int) (relativeY / displayHeight * resolutionHeight);
        px = Math.min(px, resolutionWidth - 1);
        py = Math.min(py, resolutionHeight - 1);
        return new int[] { px, py };
    }
 
    /**
     * Moves the canvas's position by the specified offset.
     *
     * @param dx the change in x position
     * @param dy the change in y position
     */
    public void move(double dx, double dy) {
        position.move(dx, dy);
        if (canvas != null) {
            canvas.update();
        }
    }
 
    /** Moves the canvas smoothly over a given duration. */
    public void move(double dx, double dy, double time) {
        move(dx, dy, time, EasingStyle.LINEAR, EasingDirection.IN);
    }
 
    /**
     * Smoothly moves the canvas from its current position by (dx, dy) over a
     * specified time using the given easing style and direction.
     */
    public void move(double dx, double dy, double time, EasingStyle easingStyle, EasingDirection easingDirection) {
        final double startX = this.position.getX();
        final double startY = this.position.getY();
        Animator.animate(this, time, easingStyle, easingDirection,
            progress -> this.position.moveTo(startX + dx * progress, startY + dy * progress),
            () -> this.position.moveTo(startX + dx, startY + dy),
            () -> this.canvas);
    }
 
    // ---------------------------------------------------------------
    // Appearance
    // ---------------------------------------------------------------
 
    /** Sets the outline color drawn around the canvas's display rectangle (null = no outline). */
    public void setOutline(Color color) {
        this.outlineColor = color;
    }
 
    public void setOutlineWidth(int width) {
        this.outlineWidth = width;
    }
 
    /**
     * Sets whether the canvas is scaled to its display size with crisp
     * nearest-neighbor sampling (true, the default -- chunky "pixel art"
     * pixels) or smooth bilinear interpolation (false -- blurred edges
     * between pixels).
     */
    public void setPixelated(boolean pixelated) {
        this.pixelated = pixelated;
    }
 
    public boolean isPixelated() {
        return pixelated;
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
    }
 
    // ---------------------------------------------------------------
    // Rendering
    // ---------------------------------------------------------------
 
    @Override
    public void drawPanel(Graphics2D graphics) {
        if (dirty) {
            renderImage.setRGB(0, 0, resolutionWidth, resolutionHeight, pixels, 0, resolutionWidth);
            dirty = false;
        }
 
        int x = getAlignedX();
        int y = getAlignedY();
 
        // Save/restore the interpolation hint rather than just setting it --
        // otherwise this canvas's nearest-neighbor (or bilinear) choice
        // would silently leak into whatever GraphicsObject GraphWin draws
        // next in the same repaint, since Graphics2D hints aren't reset
        // between drawPanel() calls.
        Object originalInterpolation = graphics.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
            pixelated ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
                      : RenderingHints.VALUE_INTERPOLATION_BILINEAR);
 
        graphics.drawImage(renderImage, x, y, displayWidth, displayHeight, null);
 
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
            originalInterpolation != null ? originalInterpolation : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
 
        if (outlineColor != null) {
            graphics.setColor(outlineColor);
            graphics.setStroke(new BasicStroke(outlineWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            graphics.drawRect(x, y, displayWidth, displayHeight);
        }
    }
 
    @Override
    public String toString() {
        return String.format(
            "PixelCanvas(position=%s, resolution=%dx%d, display=%dx%d, alignment=%s, pixelated=%b, outlineColor=%s)",
            position, resolutionWidth, resolutionHeight, displayWidth, displayHeight, alignment, pixelated,
            (outlineColor != null ? outlineColor.toString() : "none"));
    }
}