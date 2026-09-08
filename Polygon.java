package graphics;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;

/**
 * The Polygon class implements the GraphicsObject interface and represents
 * a drawable polygon defined by an arbitrary set of points.
 */
public class Polygon implements GraphicsObject {

    protected Point[] points;         // Vertices of the polygon
    protected Color fillColor;        // Fill color (null = unfilled)
    protected Color outlineColor = Color.BLACK; // Outline color
    protected int width = 1;          // Outline width
    protected GraphWin canvas;        // Reference to the canvas this is drawn on

    /**
     * Constructs a Polygon from an array of points.
     *
     * @param p The vertices of the polygon, in order.
     */
    public Polygon(Point[] p) {
        this.points = p;
    }

    /**
     * Draws the polygon on the given canvas.
     *
     * @param canvas The canvas on which the polygon will be drawn.
     * @throws IllegalStateException if the polygon is already drawn.
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
     * Removes the polygon from the canvas.
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
     * Sets the fill color of the polygon.
     *
     * @param color The color to fill the polygon with.
     */
    public void setFill(Color color) {
        this.fillColor = color;
    }

    /**
     * Sets the outline color of the polygon.
     *
     * @param color The outline color of the polygon.
     */
    public void setOutline(Color color) {
        this.outlineColor = color;
    }

    /**
     * Sets the outline width of the polygon.
     *
     * @param width The width of the outline.
     */
    public void setWidth(int width) {
        this.width = width;
    }

    /**
     * Gets the outline width of the polygon.
     *
     * @return The outline width.
     */
    public int getWidth() {
        return this.width;
    }

    /**
     * Gets the vertices of the polygon.
     *
     * @return The array of points defining the polygon.
     */
    public Point[] getPoints() {
        return points;
    }

    /**
     * Returns the x-coordinates of all vertices, in order, as an int array
     * (suitable for use with Graphics2D fillPolygon/drawPolygon).
     *
     * @return The x-coordinates of the polygon's vertices.
     */
    public int[] getXCords() {
        int[] xCoords = new int[points.length];
        for (int i = 0; i < points.length; i++) {
            xCoords[i] = (int) points[i].getX();
        }
        return xCoords;
    }

    /**
     * Returns the y-coordinates of all vertices, in order, as an int array
     * (suitable for use with Graphics2D fillPolygon/drawPolygon).
     *
     * @return The y-coordinates of the polygon's vertices.
     */
    public int[] getYCords() {
        int[] yCoords = new int[points.length];
        for (int i = 0; i < points.length; i++) {
            yCoords[i] = (int) points[i].getY();
        }
        return yCoords;
    }

    /**
     * Moves the polygon by the specified amount in the x and y directions.
     *
     * @param dx The amount to move along the x-axis.
     * @param dy The amount to move along the y-axis.
     */
    public void move(double dx, double dy) {
        for (Point p : points) {
            p.move(dx, dy);
        }
        if (this.canvas != null && this.canvas.autoflush) {
            this.canvas.repaint();
        }
    }

    /**
     * Moves the polygon smoothly over a given duration.
     *
     * @param dx   The total change in x-coordinate.
     * @param dy   The total change in y-coordinate.
     * @param time The duration (in seconds) for the movement.
     */
    public void move(double dx, double dy, double time) {
        move(dx, dy, time, EasingStyle.LINEAR, EasingDirection.IN);
    }

    /**
     * Smoothly moves the polygon from its current position by (dx, dy) over a
     * specified time using the given easing style and direction.
     *
     * @param dx              The total change in x-coordinate.
     * @param dy              The total change in y-coordinate.
     * @param time            The duration (in seconds) over which the movement should complete.
     * @param easingStyle     The easing function that dictates the acceleration curve.
     * @param easingDirection The direction of the easing (In, Out, or InOut).
     */
    public void move(double dx, double dy, double time, EasingStyle easingStyle, EasingDirection easingDirection) {
        final double[] startX = new double[points.length];
        final double[] startY = new double[points.length];
        for (int i = 0; i < points.length; i++) {
            startX[i] = points[i].getX();
            startY[i] = points[i].getY();
        }
        Animator.animate(this, time, easingStyle, easingDirection,
            progress -> {
                for (int i = 0; i < points.length; i++) {
                    points[i].moveTo(startX[i] + dx * progress, startY[i] + dy * progress);
                }
            },
            () -> {
                for (int i = 0; i < points.length; i++) {
                    points[i].moveTo(startX[i] + dx, startY[i] + dy);
                }
            },
            () -> this.canvas);
    }

    /**
     * Draws the polygon on a Graphics2D panel.
     *
     * @param graphics The Graphics2D object used for rendering.
     */
    @Override
    public void drawPanel(Graphics2D graphics) {
        int[] xCoords = getXCords();
        int[] yCoords = getYCords();

        if (fillColor != null) {
            graphics.setColor(fillColor);
            graphics.fillPolygon(xCoords, yCoords, points.length);
        }

        // JOIN_ROUND (rather than the default JOIN_MITER) rounds sharp
        // corners instead of letting the outline spike outward past the
        // vertex -- with an arbitrary polygon, corner angles can get tight
        // enough that a miter join would extend well beyond the shape.
        graphics.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.setColor(outlineColor);
        graphics.drawPolygon(xCoords, yCoords, points.length);
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder("Polygon(Points=[");
        for (Point p : points) {
            str.append(p.toString()).append(", ");
        }
        if (points.length > 0) {
            str.setLength(str.length() - 2);
        }
        str.append("])");
        return str.toString();
    }
}
