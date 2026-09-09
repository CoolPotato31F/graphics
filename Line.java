package graphics;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;

/**
 * Represents a line segment between two points, with customizable
 * width, outline color, and line style (solid, dotted, dashed).
 */
public class Line implements GraphicsObject {

    private Point point1;
    private Point point2;
    private int width = 1; // Default width
    private Color outlineColor = Color.BLACK; // Default color
    private String lineType = "solid"; // Default line type (solid)
    private GraphWin canvas;

    /**
     * Constructs a Line between two specified points.
     *
     * @param p1 the starting point of the line
     * @param p2 the ending point of the line
     */
    public Line(Point p1, Point p2) {
        this.point1 = p1;
        this.point2 = p2;
    }

    /**
     * Copy constructor. Creates a new, undrawn Line with the same endpoints
     * and styling as {@code other}.
     *
     * @param other The line to copy.
     */
    public Line(Line other) {
        this.point1 = new Point(other.point1);
        this.point2 = new Point(other.point2);
        this.width = other.width;
        this.outlineColor = other.outlineColor;
        this.lineType = other.lineType;
    }

    /**
     * Sets the outline color of the line.
     *
     * @param color the new outline color
     */
    public void setOutline(Color color) {
        this.outlineColor = color;
    }

    /**
     * Sets the first point of the line.
     *
     * @param point the new first point
     */
    public void setP1(Point point) {
        this.point1 = point;
    }

    /**
     * Sets the second point of the line.
     *
     * @param point the new second point
     */
    public void setP2(Point point) {
        this.point2 = point;
    }

    /**
     * Gets the first (start) point of the line.
     *
     * @return the first point
     */
    public Point getP1() {
        return point1;
    }

    /**
     * Gets the second (end) point of the line.
     *
     * @return the second point
     */
    public Point getP2() {
        return point2;
    }

    /**
     * Moves the line by a given offset.
     *
     * @param dx the change in x position
     * @param dy the change in y position
     */
    public void move(double dx, double dy) {
        point1.move(point1.getX() + dx, point1.getY() + dy);
        point2.move(point2.getX() + dx, point2.getY() + dy);
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
     * Gets the width (thickness) of the line.
     *
     * @return the width of the line
     */
    public int getWidth() {
        return this.width;
    }

    /**
     * Sets the width (thickness) of the line.
     *
     * @param width the new width of the line
     */
    public void setWidth(int width) {
        this.width = width;
    }

    /**
     * Sets the type of the line (solid, dotted, or dashed).
     *
     * @param type the type of the line ("solid", "dotted", or "dashed")
     */
    public void setType(String type) {
        if (type.equals("solid") || type.equals("dotted") || type.equals("dashed")) {
            this.lineType = type;
        } else {
            throw new IllegalArgumentException("Invalid line type: " + type + "\nValid types: \"solid\", \"dashed\", \"dotted\"");
        }
    }



    /**
     * Calculates and returns the length of the line.
     *
     * @return the length of the line
     */
    public double getLength() {
        return Math.sqrt(Math.pow(point1.getX() - point2.getX(), 2) +
                         Math.pow(point1.getY() - point2.getY(), 2));
    }

    /**
     * Draws the line on the provided graphical window.
     *
     * @param canvas the {@code GraphWin} where the line will be drawn
     * @throws IllegalStateException if the object is already drawn
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
     * Removes the line from the graphical window.
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
     * Draws the line on a {@code Graphics2D} panel.
     *
     * @param graphics the {@code Graphics2D} object used for rendering
     */
    @Override
    public void drawPanel(Graphics2D graphics) {
        BasicStroke stroke = null;

        // Adjust the dash pattern based on lineType
        if (lineType.equals("solid")) {
            // JOIN_ROUND has no effect on a single 2-point segment (there's
            // no corner to miter), but matches the stroke style used
            // everywhere else for consistency.
            stroke = new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        } else if (lineType.equals("dotted")) {
            // Ensure the gap between dots is adjusted based on width
            float dashLength = 1f; // Length of dashes
            float gapLength = width*2f;  // Gap between dashes/dots
            stroke = new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1.0f, new float[] {dashLength, gapLength}, 0.0f);
        } else if (lineType.equals("dashed")) {
            float dashLength = width*3f; // Length of dashes
            float gapLength = width*1.5f;  // Gap between dashes/dots
            // Ensure the gap between dashes is adjusted based on width
            stroke = new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1.0f, new float[] {dashLength, gapLength}, 0.0f);
        }

        graphics.setStroke(stroke);
        graphics.setColor(outlineColor);
        graphics.drawLine((int) point1.getX(), (int) point1.getY(),
                          (int) point2.getX(), (int) point2.getY());
    }

    /**
     * @return A human-readable summary of this line's endpoints and styling.
     */
    @Override
    public String toString() {
        return String.format("Line(point1=%s, point2=%s, width=%d, outlineColor=%s, lineType=%s)",
                             point1.toString(), point2.toString(), width, outlineColor.toString(), lineType);
    }
}