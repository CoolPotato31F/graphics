package graphics;

import java.awt.*;

/**
 * A {@link Polygon} that additionally supports rotation around its
 * centroid. Rotation is always applied to the polygon's original,
 * un-rotated vertex positions (tracked separately in
 * {@code originalPoints}), so successive calls to {@link #rotate(double)}
 * set an absolute cumulative angle rather than compounding drift.
 */
public class RotatablePolygon extends Polygon{
    // NOTE: this class deliberately does NOT declare its own `points` field.
    // It used to (`private Point[] points`), which *shadowed* rather than
    // overrode Polygon.points -- fields don't participate in polymorphism in
    // Java. That meant every method RotatablePolygon inherited unmodified
    // from Polygon (move(), getXCords(), getYCords(), getPoints()) was
    // silently operating on the original, never-rotated Polygon.points array
    // instead of the one rotate()/drawPanel() actually used, so move() had no
    // visible effect on a RotatablePolygon and getXCords()/getYCords()
    // returned stale coordinates. Reusing the inherited protected
    // Polygon.points field fixes that: there is now exactly one array of
    // "current" vertices, and rotate() mutates the very same array that
    // move(), getXCords(), getYCords(), and drawPanel() all read.
    private Point[] originalPoints;
    private Point center;
    private double rotation = 0;
    
    /**
     * Constructs a RotatablePolygon from an array of points. The centroid
     * of these points becomes the pivot for future {@link #rotate(double)}
     * calls.
     *
     * @param p The vertices of the polygon, in order.
     */
    public RotatablePolygon(Point[] p) {
        super(p);  // Call the superclass constructor (Polygon); sets (inherited) points = p
        this.originalPoints = deepCopy(p);
        center = findCentroid();
    }

    /**
     * Copy constructor. Creates a new, undrawn RotatablePolygon with the
     * same current vertices, original (un-rotated) vertices, center, and
     * rotation angle as {@code other}.
     *
     * @param other The polygon to copy.
     */
    public RotatablePolygon(RotatablePolygon other) {
        super(other.getPoints()); // Call the superclass constructor (Polygon); sets (inherited) points = p
        this.originalPoints = deepCopy(other.originalPoints);
        this.center = new Point(other.center);
        this.rotation = other.rotation;
    }

    /**
     * Creates an independent, element-wise copy of an array of points.
     *
     * @param original The array to copy (may be {@code null}).
     * @return A new array of new {@code Point} instances with the same
     *         coordinates, or {@code null} if {@code original} was
     *         {@code null}.
     */
    private static Point[] deepCopy(Point[] original) {
        if (original == null) return null;

        Point[] copy = new Point[original.length];
        for (int i = 0; i < original.length; i++) {
            copy[i] = new Point(original[i].getX(), original[i].getY()); // Creating a new object for deep copy
        }
        return copy;
    }

    /**
     * Computes and caches the centroid (average position) of the polygon's
     * current vertices, used as the pivot point for rotation.
     *
     * @return The computed centroid.
     */
    private Point findCentroid() {
        double sumX = 0, sumY = 0;
        for (Point p : points) {
            sumX += p.getX();
            sumY += p.getY();
        }
        center = new Point((int) (sumX / points.length), (int) (sumY / points.length));
        return center;
    }

    /**
     * Gets the pivot point rotation is applied around. Computed on demand
     * if it hasn't been calculated yet.
     *
     * @return A copy of the polygon's center point.
     */
    public Point getCenter() {
        if (center == null) {
            return findCentroid();
        }
        return center.clone();
    }

    /**
     * Rotates the polygon to an absolute angle that is {@code degree}
     * degrees further than its current cumulative rotation, pivoting around
     * {@link #getCenter()}. Rotation is always computed from the original,
     * un-rotated vertex positions, so repeated calls accumulate correctly
     * without drifting due to floating-point error.
     *
     * @param degree The number of degrees to add to the current rotation.
     */
    public void rotate(double degree) {
        if (points == null || points.length == 0) {
            return; // Nothing to rotate
        }

        rotation += degree;
        rotation %= 360;

        double radians = Math.toRadians(rotation);
        double cosTheta = Math.cos(radians);
        double sinTheta = Math.sin(radians);

        for (int i = 0; i < originalPoints.length; i++) {
            double x = originalPoints[i].getX() - center.getX();
            double y = originalPoints[i].getY() - center.getY();

            double newX = x * cosTheta - y * sinTheta + center.getX();
            double newY = x * sinTheta + y * cosTheta + center.getY();

            points[i].moveTo(newX, newY); // Use setLocation and round
        }
    }

    /**
     * @return The polygon's vertices in their original, un-rotated
     *         positions (i.e. as they were before any {@link #rotate(double)}
     *         calls).
     */
    public Point[] getOriginalPoints() {
        return originalPoints;
    }

    /**
     * Moves the polygon by the specified amount. Overridden (rather than just
     * inherited) so that {@code originalPoints} and {@code center} -- the
     * reference frame {@link #rotate(double)} rotates around -- move along
     * with it. Without this, rotate() after a move() would rotate around the
     * polygon's old, pre-move center.
     */
    @Override
    public void move(double dx, double dy) {
        super.move(dx, dy); // translates the shared `points` array
        for (Point p : originalPoints) {
            p.move(dx, dy);
        }
        if (center != null) {
            center.move(dx, dy);
        }
    }

    /**
     * Moves the polygon smoothly over a given duration. See
     * {@link #move(double, double, double, EasingStyle, EasingDirection)}
     * for details.
     *
     * @param dx   The total change in x-coordinate.
     * @param dy   The total change in y-coordinate.
     * @param time The duration (in seconds) for the movement.
     */
    @Override
    public void move(double dx, double dy, double time) {
        move(dx, dy, time, EasingStyle.LINEAR, EasingDirection.IN);
    }

    /**
     * Note: the underlying animation (via the superclass) is eased and
     * gradual, but {@code originalPoints}/{@code center} are shifted
     * immediately here so that a {@link #rotate(double)} call issued right
     * after this returns uses the correct final reference frame. Calling
     * rotate() *while* the move animation is still in flight is not fully
     * supported (the two would race on the shared points array) and is
     * outside the scope of this fix.
     *
     * @param dx              The total change in x-coordinate.
     * @param dy              The total change in y-coordinate.
     * @param time            The duration (in seconds) over which the movement should complete.
     * @param easingStyle     The easing function that dictates the acceleration curve.
     * @param easingDirection The direction of the easing (In, Out, or InOut).
     */
    @Override
    public void move(double dx, double dy, double time, EasingStyle easingStyle, EasingDirection easingDirection) {
        super.move(dx, dy, time, easingStyle, easingDirection);
        for (Point p : originalPoints) {
            p.move(dx, dy);
        }
        if (center != null) {
            center.move(dx, dy);
        }
    }

    /**
     * Renders the (possibly rotated) polygon onto a {@code Graphics2D} panel.
     *
     * @param graphics The {@code Graphics2D} object used for rendering.
     */
    @Override
    public void drawPanel(Graphics2D graphics) {
        int[] xCoords = getXCords();
        int[] yCoords = getYCords();

        if (fillColor != null) {
            graphics.setColor(fillColor);
            graphics.fillPolygon(xCoords, yCoords, points.length);
        }

        // JOIN_ROUND/CAP_ROUND (rather than JOIN_MITER/CAP_BUTT) rounds
        // sharp corners instead of letting the outline spike outward past
        // the vertex -- with an arbitrary polygon, corner angles can get
        // tight enough that a miter join would extend well beyond the
        // shape. Call setSmooth(false) (inherited from Polygon) for
        // sharp/mitered corners instead.
        graphics.setStroke(smooth
                ? new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                : new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        graphics.setColor(outlineColor);
        graphics.drawPolygon(xCoords, yCoords, points.length);
    }

    /**
     * @return A human-readable summary of this polygon's current vertices,
     *         center, and rotation angle.
     */
    @Override
    public String toString() {
        StringBuilder str = new StringBuilder("RotatablePolygon(");
        
        // Add information about the current points of the polygon
        str.append("Points=[");
        for (Point p : points) {
            str.append(p.toString()).append(", ");
        }
        // Remove the last comma and space
        if (points.length > 0) {
            str.setLength(str.length() - 2);
        }
        str.append("], ");
        
        // Add center point
        str.append("Center=").append(center.toString()).append(", ");
        
        // Add rotation angle
        str.append("Rotation=").append(rotation).append("°");
        
        return str.append(")").toString();
    }
}