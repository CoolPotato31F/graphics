package graphics;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import javax.imageio.ImageIO;


/**
 * Represents an image that can be drawn onto a graphical window.
 */
public class Image implements GraphicsObject {

    private Point position; // Position stored as a Point
    private int width, height; // Image dimensions
    private BufferedImage image;
    private GraphWin canvas;
    private Color outlineColor = null; // Outline color (null means no outline)
    private int outlineWidth = 1; // Outline thickness
    private String alignment = "center"; // Default alignment4
    private double rotation = 0;
    private BufferedImage original;
    private String filePath;

    // Custom rotation pivot ("center"), as used by setCenter()/rotate().
    // Stored as a fraction of the *source* (original) image's width/height
    // rather than raw pixels, so the pivot stays correct even after
    // setScale() changes the source image's dimensions.
    private boolean hasCustomCenter = false;
    private double pivotXFrac = 0.5;
    private double pivotYFrac = 0.5;
    // Where the pivot currently lands within the (possibly rotated) `image`
    // bitmap's own top-left-origin coordinate space. Recomputed every time
    // rotate() (or setCenter(), or setScale()) rebuilds `image`. When no
    // custom center has been set this just tracks width/2, height/2, which
    // reproduces the original center-of-bounding-box rotation behavior.
    private double renderPivotX;
    private double renderPivotY;

    /**
     * Constructs an Image object with a specified file path and position.
     * 
     * @param filePath the path to the image file
     * @param position the position of the image (Point object)
     */
    public Image(Point position, String filePath) {
        try {
            this.image = loadImage(filePath);
            this.original = deepCopy(this.image);
            this.filePath = filePath;
            this.width = image.getWidth();
            this.height = image.getHeight();
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.position = position;
        this.renderPivotX = this.width / 2.0;
        this.renderPivotY = this.height / 2.0;
    }

    /**
     * Constructs an Image object with a specified resource URL and position.
     *
     * @param position the position of the image (Point object)
     * @param filePath the URL of the image resource
     */
    public Image(Point position, URL filePath) {
        try {
            this.image = ImageIO.read(filePath);
            this.original = deepCopy(this.image);
            this.filePath = filePath.getFile();
            this.width = image.getWidth();
            this.height = image.getHeight();
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.position = position;
        this.renderPivotX = this.width / 2.0;
        this.renderPivotY = this.height / 2.0;
    }

    /**
     * Copy constructor. Creates a new, undrawn Image with the same bitmap,
     * position, and styling (including rotation and any custom pivot) as
     * {@code other}.
     *
     * @param other The image to copy.
     */
    public Image(Image other) {
        this.position = new Point(other.position);
        this.width = other.width;
        this.height = other.height;
        this.image = deepCopy(other.image);
        this.original = deepCopy(other.original);
        this.canvas = other.canvas;
        this.outlineColor = other.outlineColor;
        this.outlineWidth = other.outlineWidth;
        this.alignment = other.alignment;
        this.rotation = other.rotation;
        this.filePath = other.filePath;
        this.hasCustomCenter = other.hasCustomCenter;
        this.pivotXFrac = other.pivotXFrac;
        this.pivotYFrac = other.pivotYFrac;
        this.renderPivotX = other.renderPivotX;
        this.renderPivotY = other.renderPivotY;
    }

    /**
     * Loads an image from a file path.
     * If the path is relative, it loads from the class's directory.
     * 
     * @param filePath the file path of the image
     * @return the loaded BufferedImage
     * @throws IOException if the image cannot be found or loaded
     */
    private BufferedImage loadImage(String filePath) throws IOException {
        File file = new File(filePath);
        // Check if the path is absolute
        if (file.isAbsolute() && file.exists()) {
            return ImageIO.read(file);
        } 
        
        // Try loading from the class's resource directory
        URL resource = this.getClass().getResource(filePath);
        if (resource != null) {
            return ImageIO.read(resource);
        }
        
        // Try loading from the current working directory
        file = new File(System.getProperty("user.dir"), filePath);
        if (file.exists()) {
            return ImageIO.read(file);
        }

        throw new IOException("Image file not found: " + filePath);
    }

    /**
     * Sets the outline color of the image.
     * 
     * @param color the new outline color
     */
    public void setOutline(Color color) {
        this.outlineColor = color;
    }

    /**
     * Sets the outline width (thickness).
     * 
     * @param width the width of the outline
     */
    public void setOutlineWidth(int width) {
        this.outlineWidth = width;
    }

    /**
     * Sets the size of the image to specific width and height.
     * 
     * @param width  the new width of the image
     * @param height the new height of the image
     */
    public void setSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    /**
     * Scales the image by a given factor.
     * 
     * @param scaleFactor the scale factor (e.g., 2.0 doubles the size, 0.5 halves it)
     */
    public void setScale(double scaleFactor) {
        this.width = (int) (original.getWidth() * scaleFactor);
        this.height = (int) (original.getHeight() * scaleFactor);
        BufferedImage resizedImage = new BufferedImage(this.width, this.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = resizedImage.createGraphics();

        // Nearest-neighbor keeps pixel-art sprites crisp instead of
        // smearing them (bilinear looks better for photos, worse for
        // low-res game art like this).
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2d.drawImage(original, 0, 0, this.width, this.height, null);
        g2d.dispose();

        original = resizedImage;

        // Rebuild `image`/width/height/renderPivot at the (possibly
        // nonzero) current rotation against the freshly-resized original,
        // so scaling doesn't undo an existing rotation or desync the pivot.
        applyRotation();
    }

    /**
     * Stretches the SOURCE bitmap's width and height independently (unlike
     * {@link #setScale}, which scales both by the same factor, and unlike
     * {@link #setSize(int, int)}, which only stretches the already-rotated
     * bitmap at draw time and would desync a custom {@link #setCenter}
     * pivot) -- for sprites that need to span a variable distance in one
     * direction without also changing thickness in the other, e.g. a door
     * resized to fit a wider opening without becoming proportionally
     * thicker. Recomputes the pivot correctly, same as {@link #setScale}.
     *
     * @param targetWidth  the new source width, in pixels
     * @param targetHeight the new source height, in pixels
     */
    public void stretchSource(int targetWidth, int targetHeight) {
        targetWidth = Math.max(1, targetWidth);
        targetHeight = Math.max(1, targetHeight);
        BufferedImage resizedImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = resizedImage.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2d.drawImage(original, 0, 0, targetWidth, targetHeight, null);
        g2d.dispose();

        original = resizedImage;
        applyRotation();
    }

    /**
     * Returns the width, in pixels, of the current <em>source</em> bitmap --
     * i.e. the file as loaded, adjusted for any prior {@link #setScale}
     * calls, but before rotation expands the on-screen bounding box. This
     * is the number {@link #setCenter}'s pivot coordinates and
     * {@link #setWidth}/{@link #setHeight}'s targets are measured against.
     */
    public int getSourceWidth() {
        return original.getWidth();
    }

    /** @see #getSourceWidth() */
    public int getSourceHeight() {
        return original.getHeight();
    }

    /**
     * Convenience for {@link #setScale}: resizes the image so its source
     * width becomes exactly {@code targetWidth} pixels, preserving aspect
     * ratio (i.e. scales height by the same factor).
     *
     * @param targetWidth the desired width, in pixels
     */
    public void setWidth(int targetWidth) {
        setScale(targetWidth / (double) original.getWidth());
    }

    /**
     * Convenience for {@link #setScale}: resizes the image so its source
     * height becomes exactly {@code targetHeight} pixels, preserving aspect
     * ratio (i.e. scales width by the same factor).
     *
     * @param targetHeight the desired height, in pixels
     */
    public void setHeight(int targetHeight) {
        setScale(targetHeight / (double) original.getHeight());
    }

    /**
     * Sets the point that {@link #rotate(double)} rotates the image around,
     * and that this image's {@code position} refers to from then on --
     * instead of the bounding-box corner/center the {@code alignment}
     * setting would otherwise use. This is what makes a character sprite
     * rotate about its head (or any other fixed point) rather than about
     * the middle of its bounding box.
     * <p>
     * Coordinates are given in pixels of the image's current source
     * bitmap (i.e. as if measured on the file straight out of
     * {@code setScale()}/before any rotation), with (0, 0) at the top-left
     * corner. For example, if a 24x30 sprite's head sits between pixel
     * (12, 5) and (13, 6), call {@code setCenter(12.5, 5.5)}.
     * <p>
     * Internally this is stored as a fraction of the source image's
     * width/height, so the pivot stays correct even if {@link #setScale}
     * is called afterwards. Call this once, right after construction,
     * before rotating.
     *
     * @param x the pivot's x-coordinate in the current source image's pixels
     * @param y the pivot's y-coordinate in the current source image's pixels
     */
    public void setCenter(double x, double y) {
        int srcWidth = original.getWidth();
        int srcHeight = original.getHeight();
        this.pivotXFrac = x / srcWidth;
        this.pivotYFrac = y / srcHeight;
        this.hasCustomCenter = true;
        applyRotation(); // recompute image/width/height/renderPivot around the new pivot
    }

    /**
     * Sets the alignment of the image.
     * Supported values: "top-left", "top-right", "bottom-left", "bottom-right", "center".
     * 
     * @param alignment the new alignment setting
     */
    
    public void setAlignment(String alignment) {
        if (!alignment.equals("top-left") && !alignment.equals("top-right") &&
            !alignment.equals("bottom-left") && !alignment.equals("bottom-right") &&
            !alignment.equals("center")) {
            throw new IllegalArgumentException("Invalid alignment value: " + alignment);
        }
        this.alignment = alignment;
    }

    /**
     * Calculates the adjusted x-coordinate based on alignment.
     */
    private int getAlignedX() {
        if (hasCustomCenter) {
            // position refers to the pivot, wherever it currently lands
            // within the (rotated) image bitmap -- alignment is ignored
            // once a custom center is in play, since "top-left of the
            // bounding box" isn't a meaningful anchor for a rotating sprite.
            return (int) Math.round(position.getX() - renderPivotX);
        }
        switch (alignment) {
            case "top-right":
            case "bottom-right":
                return (int) (position.getX() - width);
            case "center":
                return (int) (position.getX() - width / 2);
            default: // "top-left", "bottom-left"
                return (int) position.getX();
        }
    }

    /**
     * Calculates the adjusted y-coordinate based on alignment.
     */
    private int getAlignedY() {
        if (hasCustomCenter) {
            return (int) Math.round(position.getY() - renderPivotY);
        }
        switch (alignment) {
            case "bottom-left":
            case "bottom-right":
                return (int) (position.getY() - height);
            case "center":
                return (int) (position.getY() - height / 2);
            default: // "top-left", "top-right"
                return (int) position.getY();
        }
    }
    /**
     * Creates an independent pixel-level copy of a {@code BufferedImage},
     * so mutating the copy (or the original) never affects the other.
     *
     * @param img The image to copy.
     * @return A new {@code BufferedImage} with the same pixel data.
     */
    public BufferedImage deepCopy(BufferedImage img) {
        ColorModel cm = img.getColorModel();
        boolean isAlphaPremultiplied = cm.isAlphaPremultiplied();
        WritableRaster raster = img.copyData(null);
        return new BufferedImage(cm, raster, isAlphaPremultiplied, null);
    }
    
    /**
     * Moves the image by a given offset.
     *
     * @param dx the change in x position
     * @param dy the change in y position
     */
    public void move(double dx, double dy) {
        this.position.move(dx, dy);
        if (canvas != null) {
            canvas.update();
        }
    }

    /**
     * Moves the rectangle smoothly over a given duration.
     *
     * @param dx   The total change in x-coordinate.
     * @param dy   The total change in y-coordinate.
     * @param time The duration (in seconds) for the movement.
     */
    public void move(double dx, double dy, double time) {
        move(dx, dy, time, EasingStyle.LINEAR, EasingDirection.IN);
    }

    /**
     * Smoothly moves the point from its current position by (dx, dy) over a specified time
     * using the given easing style and direction.
     *
     * @param dx              The total change in x-coordinate.
     * @param dy              The total change in y-coordinate.
     * @param time            The duration (in seconds) over which the movement should complete.
     * @param easingStyle     The easing function that dictates the acceleration curve.
     * @param easingDirection The direction of the easing (In, Out, or InOut).
     */
    public void move(double dx, double dy, double time, EasingStyle easingStyle, EasingDirection easingDirection) {
        final double startX = this.position.getX();
        final double startY = this.position.getY();
        Animator.animate(this, time, easingStyle, easingDirection,
            progress -> this.position.moveTo(startX + dx * progress, startY + dy * progress),
            () -> this.position.moveTo(startX + dx, startY + dy),
            () -> this.canvas);
    }

    /**
     * Rotates the image by a specified angle in degrees, relative to its
     * current rotation. Rotates around the pivot set by {@link #setCenter}
     * if one has been set, otherwise around the bounding-box center (the
     * original behavior).
     * 
     * @param angle the angle in degrees to rotate the image
     */
    public void rotate(double angle) {
        rotation += angle;
        applyRotation();
    }

    /**
     * Sets the absolute rotation (in degrees), rather than rotating
     * relative to the current angle. Handy for "face the mouse" style code
     * where you compute a fresh target angle every frame instead of
     * accumulating deltas.
     *
     * @param angle the absolute angle in degrees
     */
    public void setRotation(double angle) {
        rotation = angle;
        applyRotation();
    }

    /** Returns the image's current absolute rotation, in degrees. */
    public double getRotation() {
        return rotation;
    }

    /**
     * Rebuilds {@code image}/{@code width}/{@code height} by rotating
     * {@code original} by {@code rotation} degrees around the pivot
     * ({@link #setCenter}'s point if set, otherwise the bounding-box
     * center), and records where that pivot lands in the rebuilt bitmap
     * ({@code renderPivotX}/{@code renderPivotY}) so {@link #getAlignedX}/
     * {@link #getAlignedY} can place it back at {@code position} on screen.
     * <p>
     * Called by {@link #rotate}, {@link #setRotation}, {@link #setCenter},
     * and {@link #setScale} -- anything that changes the angle or the
     * source bitmap the angle is measured against.
     */
    private void applyRotation() {
        double radians = Math.toRadians(rotation);
        int origWidth = original.getWidth();
        int origHeight = original.getHeight();

        double pivotX = hasCustomCenter ? pivotXFrac * origWidth : origWidth / 2.0;
        double pivotY = hasCustomCenter ? pivotYFrac * origHeight : origHeight / 2.0;

        // Rotate the image's four corners around the pivot to find the
        // exact bounding box of the rotated image, and where the
        // (rotation-invariant) pivot lands within that box.
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        double[][] corners = {
            {0, 0}, {origWidth, 0}, {0, origHeight}, {origWidth, origHeight}
        };
        for (double[] corner : corners) {
            double dx = corner[0] - pivotX;
            double dy = corner[1] - pivotY;
            double rx = dx * cos - dy * sin;
            double ry = dx * sin + dy * cos;
            minX = Math.min(minX, rx);
            maxX = Math.max(maxX, rx);
            minY = Math.min(minY, ry);
            maxY = Math.max(maxY, ry);
        }

        int newWidth = Math.max(1, (int) Math.ceil(maxX - minX));
        int newHeight = Math.max(1, (int) Math.ceil(maxY - minY));
        // Since the pivot is rotation-invariant (it maps to itself), its
        // location in the new bitmap is just how far the bounding box's
        // min corner sits from it.
        double newPivotX = -minX;
        double newPivotY = -minY;

        BufferedImage rotatedImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = rotatedImage.createGraphics();
        // Nearest-neighbor keeps rotated pixel-art sprites crisp instead
        // of blurring their edges every frame the angle changes.
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        AffineTransform transform = new AffineTransform();
        transform.translate(newPivotX, newPivotY); // pivot's target spot in the new bitmap
        transform.rotate(radians);                 // rotate about that spot
        transform.translate(-pivotX, -pivotY);      // ...having moved the pivot to the origin first

        g2d.drawImage(original, transform, null);
        g2d.dispose();

        image = rotatedImage;
        width = newWidth;
        height = newHeight;
        renderPivotX = newPivotX;
        renderPivotY = newPivotY;
    }

    /**
     * Draws the image on a graphical window.
     * 
     * @param canvas the {@code GraphWin} where the image will be drawn
     */
    @Override
    public void draw(GraphWin canvas) {
        if (this.canvas != null) {
            throw new IllegalStateException("Object is already drawn");
        }
        this.canvas = canvas;
        canvas.addItem(this);
    }

    /**
     * Removes the image from the graphical window.
     */
    @Override
    public void undraw() {
        if (canvas != null) {
            canvas.deleteItem(this);
            this.canvas = null;
        }
        Animator.cancel(this); // stop any in-flight animation now that this is off-canvas
    }

    /**
     * Draws the image on a {@code Graphics2D} panel.
     * 
     * @param graphics the {@code Graphics2D} object used for rendering
     */
    @Override
    public void drawPanel(Graphics2D graphics) {
        int x = getAlignedX();
        int y = getAlignedY();

        // Draw the image
        graphics.drawImage(image, x, y, width, height, null);

        // Draw an outline if it is set
        if (outlineColor != null) {
            graphics.setColor(outlineColor);
            // JOIN_ROUND avoids sharp miter spikes at tight corners; a
            // plain axis-aligned rectangle's 90-degree corners are never
            // sharp enough to trigger it, but this keeps every shape's
            // stroke consistent.
            graphics.setStroke(new BasicStroke(outlineWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            graphics.drawRect(x, y, width, height);
        }
    }
    
    /**
     * @return A human-readable summary of this image's file, position,
     *         size, and styling.
     */
    @Override
    public String toString() {
        return String.format("Image(filePath=%s, position=%s, width=%d, height=%d, outlineColor=%s, " + 
                             "outlineWidth=%d, alignment=%s, rotation=%.2f)",
                             filePath, position.toString(), width, height, 
                             (outlineColor != null ? outlineColor.toString() : "none"), 
                             outlineWidth, alignment, rotation);
    }
}