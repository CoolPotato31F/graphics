package graphics;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;

/**
 * Represents a rectangle defined by two opposite corner points, with
 * customizable fill, outline color, and outline width.
 */
public class Rectangle implements GraphicsObject {

    private Point point1;
    private Point point2;
    private int width = 1;
    private int w;
    private int h;
    private GraphWin canvas;
    private Color fillColor;
    private Color outlineColor = Color.BLACK;
    private boolean smooth = true; // true = rounded corners on the stroke (default), false = sharp/mitered

    /**
     * Constructs a Rectangle from two opposite corner points. The points
     * may be given in any order (top-left/bottom-right or vice versa); they
     * are normalized internally so {@code point1} is always the top-left
     * corner and {@code point2} the bottom-right.
     *
     * @param p1 One corner of the rectangle.
     * @param p2 The opposite corner of the rectangle.
     */
    public Rectangle(Point p1, Point p2) {
        Point np1 = new Point(0, 0);
        Point np2 = new Point(0, 0);

        if (p1.getX() > p2.getX()) {
            np1.move(p2.getX(), 0);
            np2.move(p1.getX(), 0);
        } else {
            np1.move(p1.getX(), 0);
            np2.move(p2.getX(), 0);
        }

        if (p1.getY() > p2.getY()) {
            np1.move(0, p2.getY());
            np2.move(0, p1.getY());
        } else {
            np1.move(0, p1.getY());
            np2.move(0, p2.getY());
        }

        this.point1 = np1;
        this.point2 = np2;
        w = (int) (np2.getX() - np1.getX());
        h = (int) (np2.getY() - np1.getY());
    }

    /**
     * Copy constructor. Creates a new, undrawn Rectangle with the same
     * corners and styling as {@code other}.
     *
     * @param other The rectangle to copy.
     */
    public Rectangle(Rectangle other) {
        this.point1 = new Point(other.point1);
        this.point2 = new Point(other.point2);
        this.width = other.width;
        this.w = other.w;
        this.h = other.h;
        this.fillColor = other.fillColor;
        this.outlineColor = other.outlineColor;
        this.smooth = other.smooth;
    }

    /**
     * Draws the rectangle on the given canvas.
     *
     * @param canvas The canvas on which the rectangle will be drawn.
     * @throws Error if the rectangle is already drawn on a canvas.
     */
    @Override
    public void draw(GraphWin canvas) {
        if (this.canvas != null) {
            throw new Error("Object is already drawn");
        }
        this.canvas = canvas;
        canvas.addItem(this);
    }

    /**
     * Removes the rectangle from the canvas.
     */
    @Override
    public void undraw() {
        if (canvas != null) {
            canvas.deleteItem(this);
            this.canvas = null;
        }
        Animator.cancel(this);
    }

    /**
     * Sets the fill color of the rectangle.
     *
     * @param color The color to fill the rectangle with.
     */
    public void setFill(Color color) {
        this.fillColor = color;
    }

    /**
     * Sets the outline color of the rectangle.
     *
     * @param color The new outline color.
     */
    public void setOutline(Color color) {
        this.outlineColor = color;
    }

    /**
     * @return The top-left corner of the rectangle.
     */
    public Point getP1() {
        return point1;
    }

    /**
     * @return The bottom-right corner of the rectangle.
     */
    public Point getP2() {
        return point2;
    }

    /**
     * Gets the center point of the rectangle, computed fresh from its
     * current corners every call (rather than cached) so it's always
     * correct after {@code move()} -- a previous version stored this in a
     * field that was (a) computed wrong to begin with (it held width/height,
     * not a midpoint) and (b) never updated by move(), so it went stale
     * after the very first move. Both are fixed by not caching it at all.
     *
     * @return The center point of the rectangle.
     */
    public Point getCenter() {
        return new Point((point1.getX() + point2.getX()) / 2.0, (point1.getY() + point2.getY()) / 2.0);
    }

    /**
     * @return The width and height of the rectangle, packed into a
     *         {@code Point} whose x is the width and y is the height.
     */
    public Point getSize() {
        return new Point(w, h);
    }

    /**
     * Sets the outline width of the rectangle.
     *
     * @param width The new outline width.
     */
    public void setWidth(int width) {
        this.width = width;
    }

    /**
     * @return The current outline width of the rectangle.
     */
    public int getWidth() {
        return this.width;
    }

    /**
     * Controls whether the outline's corners are drawn rounded or sharp.
     * With a thick outline (see {@link #setWidth(int)}), a rounded join
     * softens each corner into a curve; a sharp join comes to a crisp point
     * instead. Purely cosmetic -- has no effect on the rectangle's actual
     * geometry/collision bounds.
     *
     * @param smooth true for rounded corners (the default), false for
     *               sharp/mitered corners.
     */
    public void setSmooth(boolean smooth) {
        this.smooth = smooth;
    }

    /**
     * @return whether the outline is currently drawn with rounded corners.
     */
    public boolean isSmooth() {
        return smooth;
    }

    /**
     * Moves the rectangle by a specified amount in the x and y directions.
     *
     * @param dx The amount to move the rectangle along the x-axis.
     * @param dy The amount to move the rectangle along the y-axis.
     */
    public void move(double dx, double dy) {
        point1.move(dx, dy);
        point2.move(dx, dy);
        if (this.canvas != null && this.canvas.autoflush) {
            this.canvas.repaint();
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
     * Smoothly moves the rectangle from its current position by (dx, dy)
     * over a specified time using the given easing style and direction.
     *
     * @param dx              The total change in x-coordinate.
     * @param dy              The total change in y-coordinate.
     * @param time            The duration (in seconds) over which the movement should complete.
     * @param easingStyle     The easing function that dictates the acceleration curve.
     * @param easingDirection The direction of the easing (In, Out, or InOut).
     */
    public void move(double dx, double dy, double time, EasingStyle easingStyle, EasingDirection easingDirection) {
        final double startX = this.point1.getX();
        final double startY = this.point1.getY();
        final double startX2 = this.point2.getX();
        final double startY2 = this.point2.getY();
        Animator.animate(this, time, easingStyle, easingDirection,
            progress -> {
                this.point1.moveTo(startX + dx * progress, startY + dy * progress);
                this.point2.moveTo(startX2 + dx * progress, startY2 + dy * progress);
            },
            () -> {
                this.point1.moveTo(startX + dx, startY + dy);
                this.point2.moveTo(startX2 + dx, startY2 + dy);
            },
            () -> this.canvas);
    }

    /**
     * Renders the rectangle onto a {@code Graphics2D} panel.
     *
     * @param graphics The {@code Graphics2D} object used for rendering.
     */
    @Override
    public void drawPanel(Graphics2D graphics) {
        if (fillColor != null) {
            graphics.setColor(fillColor);
            graphics.fillRect((int) point1.getX(), (int) point1.getY(), w, h);
        }
        graphics.setStroke(smooth
                ? new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                : new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        graphics.setColor(outlineColor);
        graphics.drawRect((int) point1.getX(), (int) point1.getY(), w, h);
    }

    /**
     * @return A human-readable summary of this rectangle's corners and styling.
     */
    @Override
    public String toString() {
        return "Rectangle(" +
               "point1=" + point1 + ", " +
               "point2=" + point2 + ", " +
               "center=" + getCenter() + ", " +
               "width=" + width + ", " +
               "fillColor=" + (fillColor != null ? fillColor : "None") + ", " +
               "outlineColor=" + outlineColor + ", " +
               "smooth=" + smooth + ")";
    }
}