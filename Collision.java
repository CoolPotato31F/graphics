package graphics;

import java.util.Locale;

/**
 * Centralized collision-detection utility shared by every {@link GraphicsObject}
 * shape, in the same spirit as {@link Easing} and {@link Animator}: a
 * stateless class with no instances, so collision math lives in exactly one
 * place instead of being copy-pasted (or reinvented slightly differently)
 * wherever a program needs it.
 * <p>
 * There are two layers here:
 * <ul>
 *   <li><b>Broad phase</b> -- {@link #getBounds} computes a cheap axis-aligned
 *   bounding box ({@link Bounds}) for a shape, and {@link Bounds#intersects}
 *   is a cheap box-vs-box overlap test. {@link #intersects(GraphicsObject, GraphicsObject)}
 *   always runs this first so it can bail out immediately for pairs that
 *   obviously aren't touching, without doing any per-shape math.</li>
 *   <li><b>Narrow phase</b> -- exact shape-vs-shape routines
 *   ({@link #circleCircle}, {@link #circleRectangle}, {@link #rectangleRectangle},
 *   {@link #pointInRectangle}, {@link #pointInPolygon}, {@link #circlePolygon},
 *   {@link #polygonPolygon}, {@link #rectanglePolygon}) that only run once the
 *   broad phase says the boxes overlap.</li>
 * </ul>
 * {@link #intersects(GraphicsObject, GraphicsObject)} dispatches to the right
 * narrow-phase routine automatically based on the runtime types of the two
 * objects passed in (this also covers {@link RotatablePolygon}, since it
 * extends {@link Polygon}). {@link Oval} has exact routines against every
 * other shape except {@link Line}; for a pair with no exact routine at all
 * (currently just anything involving {@link Line}), the broad-phase
 * bounding-box result is used as the answer -- an approximation, but a
 * documented and predictable one.
 * <p>
 * Detection only, deliberately: this class answers "are these two things
 * touching", not "what should happen because they are". {@link #resolve}
 * goes one step further and computes a minimum-translation vector (how far,
 * and in which direction, to move one shape so the two boxes just touch
 * instead of overlap) for simple "push apart" / "stop at the wall"
 * responses, but bouncing, sliding, damage, scoring, etc. are all up to the
 * caller.
 */
public final class Collision {

    private Collision() {
        // Utility class; not instantiable.
    }

    // =====================================================================
    // Axis-aligned bounding box
    // =====================================================================

    /**
     * A simple axis-aligned bounding box: the smallest upright rectangle
     * that fully contains a shape. Used both as the cheap "broad phase"
     * overlap test in {@link #intersects(GraphicsObject, GraphicsObject)}
     * and as the fallback collision test for shape pairs with no exact
     * narrow-phase routine below.
     */
    public static final class Bounds {
        public final double minX, minY, maxX, maxY;

        /**
         * Constructs a bounding box from two corner coordinates, given in
         * either order (they're normalized internally).
         *
         * @param x1 one corner's x-coordinate
         * @param y1 one corner's y-coordinate
         * @param x2 the opposite corner's x-coordinate
         * @param y2 the opposite corner's y-coordinate
         */
        public Bounds(double x1, double y1, double x2, double y2) {
            // Normalize in case the two corners were supplied out of order.
            this.minX = Math.min(x1, x2);
            this.maxX = Math.max(x1, x2);
            this.minY = Math.min(y1, y2);
            this.maxY = Math.max(y1, y2);
        }

        /** @return the width of this bounding box. */
        public double getWidth() {
            return maxX - minX;
        }

        /** @return the height of this bounding box. */
        public double getHeight() {
            return maxY - minY;
        }

        /** True if this box and {@code other} overlap (touching edges count as overlapping). */
        public boolean intersects(Bounds other) {
            return minX <= other.maxX && maxX >= other.minX
                && minY <= other.maxY && maxY >= other.minY;
        }

        /** True if the point (x, y) falls within this box. */
        public boolean contains(double x, double y) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY;
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT, "Bounds(minX=%.2f, minY=%.2f, maxX=%.2f, maxY=%.2f)", minX, minY, maxX, maxY);
        }
    }

    /**
     * Computes the axis-aligned bounding box of a {@link GraphicsObject}.
     * Supported out of the box: {@link Point}, {@link Circle},
     * {@link Rectangle}, {@link Oval}, {@link Line}, and {@link Polygon}
     * (which {@link RotatablePolygon} extends, so rotated polygons work too
     * -- their bounds are recomputed fresh from the current, post-rotation
     * point positions every call).
     *
     * @throws UnsupportedOperationException for shape types with no bounds
     *         logic defined here ({@link Image}, {@link SkewedImage},
     *         {@link PixelCanvas}, {@link Text}) -- for those, build a
     *         {@link Bounds} by hand from their own position/width/height
     *         getters.
     */
    public static Bounds getBounds(GraphicsObject object) {
        if (object instanceof Circle) {
            Circle c = (Circle) object;
            double x = c.getCenter().getX(), y = c.getCenter().getY(), r = c.getRadius();
            return new Bounds(x - r, y - r, x + r, y + r);
        }
        if (object instanceof Rectangle) {
            Rectangle r = (Rectangle) object;
            return new Bounds(r.getP1().getX(), r.getP1().getY(), r.getP2().getX(), r.getP2().getY());
        }
        if (object instanceof Oval) {
            Oval o = (Oval) object;
            return new Bounds(o.getP1().getX(), o.getP1().getY(), o.getP2().getX(), o.getP2().getY());
        }
        if (object instanceof Polygon) { // covers RotatablePolygon too, since it extends Polygon
            Polygon p = (Polygon) object;
            double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
            for (Point vertex : p.getPoints()) {
                minX = Math.min(minX, vertex.getX());
                maxX = Math.max(maxX, vertex.getX());
                minY = Math.min(minY, vertex.getY());
                maxY = Math.max(maxY, vertex.getY());
            }
            return new Bounds(minX, minY, maxX, maxY);
        }
        if (object instanceof Line) {
            Line l = (Line) object;
            return new Bounds(l.getP1().getX(), l.getP1().getY(), l.getP2().getX(), l.getP2().getY());
        }
        if (object instanceof Point) {
            Point p = (Point) object;
            return new Bounds(p.getX(), p.getY(), p.getX(), p.getY());
        }
        throw new UnsupportedOperationException(
            "Collision.getBounds() does not support " + object.getClass().getSimpleName()
            + " -- compute its bounds manually (e.g. from its position/width/height getters) "
            + "and construct a Collision.Bounds directly.");
    }

    // =====================================================================
    // Broad-phase / generic dispatch
    // =====================================================================

    /**
     * True if two graphical objects overlap. Runs the cheap {@link Bounds}
     * check first, then -- only if the boxes overlap -- dispatches to an
     * exact narrow-phase routine based on the two objects' runtime types.
     * <p>
     * Exact pairs: Circle-Circle, Circle-Rectangle, Rectangle-Rectangle,
     * Circle-Polygon, Rectangle-Polygon, Polygon-Polygon, Point-Circle,
     * Point-Rectangle, Point-Polygon, and every Oval combination (Oval-Oval,
     * Oval-Circle, Oval-Rectangle, Oval-Polygon, Oval-Point) -- these treat
     * the oval as an actual ellipse, not its bounding box. "Polygon" here
     * also matches {@link RotatablePolygon}. Polygon-vs-Polygon and
     * Rectangle-vs-Polygon use the Separating Axis Theorem, which assumes
     * both polygons are convex (true of any regular shape you'd hand it, but
     * not guaranteed for an arbitrary self-intersecting or concave point
     * list).
     * <p>
     * Any other pair (currently: one side is a {@link Line}) has no exact
     * routine here, so the already-computed bounding-box overlap is
     * returned as-is -- an approximation, not a false positive on shapes
     * that are actually touching, but it can report a collision for two
     * shapes whose boxes overlap while the shapes themselves don't quite.
     *
     * @throws UnsupportedOperationException if either object's type has no
     *         bounds defined (see {@link #getBounds})
     */
    public static boolean intersects(GraphicsObject a, GraphicsObject b) {
        if (a == b) {
            return false; // an object never collides with itself
        }

        Bounds boundsA = getBounds(a);
        Bounds boundsB = getBounds(b);
        if (!boundsA.intersects(boundsB)) {
            return false; // broad phase already rules it out
        }

        if (a instanceof Circle && b instanceof Circle) {
            return circleCircle((Circle) a, (Circle) b);
        }
        if (a instanceof Circle && b instanceof Rectangle) {
            return circleRectangle((Circle) a, (Rectangle) b);
        }
        if (a instanceof Rectangle && b instanceof Circle) {
            return circleRectangle((Circle) b, (Rectangle) a);
        }
        if (a instanceof Rectangle && b instanceof Rectangle) {
            return rectangleRectangle((Rectangle) a, (Rectangle) b);
        }
        if (a instanceof Point && b instanceof Circle) {
            return Point.checkCollisionPointXCircle((Point) a, (Circle) b);
        }
        if (a instanceof Circle && b instanceof Point) {
            return Point.checkCollisionPointXCircle((Point) b, (Circle) a);
        }
        if (a instanceof Point && b instanceof Rectangle) {
            return pointInRectangle((Point) a, (Rectangle) b);
        }
        if (a instanceof Rectangle && b instanceof Point) {
            return pointInRectangle((Point) b, (Rectangle) a);
        }
        if (a instanceof Point && b instanceof Polygon) {
            return pointInPolygon((Point) a, (Polygon) b);
        }
        if (a instanceof Polygon && b instanceof Point) {
            return pointInPolygon((Point) b, (Polygon) a);
        }
        if (a instanceof Circle && b instanceof Polygon) {
            return circlePolygon((Circle) a, (Polygon) b);
        }
        if (a instanceof Polygon && b instanceof Circle) {
            return circlePolygon((Circle) b, (Polygon) a);
        }
        if (a instanceof Rectangle && b instanceof Polygon) {
            return rectanglePolygon((Rectangle) a, (Polygon) b);
        }
        if (a instanceof Polygon && b instanceof Rectangle) {
            return rectanglePolygon((Rectangle) b, (Polygon) a);
        }
        if (a instanceof Polygon && b instanceof Polygon) {
            return polygonPolygon((Polygon) a, (Polygon) b);
        }
        if (a instanceof Oval && b instanceof Oval) {
            return ovalOval((Oval) a, (Oval) b);
        }
        if (a instanceof Circle && b instanceof Oval) {
            return circleOval((Circle) a, (Oval) b);
        }
        if (a instanceof Oval && b instanceof Circle) {
            return circleOval((Circle) b, (Oval) a);
        }
        if (a instanceof Rectangle && b instanceof Oval) {
            return ovalRectangle((Oval) b, (Rectangle) a);
        }
        if (a instanceof Oval && b instanceof Rectangle) {
            return ovalRectangle((Oval) a, (Rectangle) b);
        }
        if (a instanceof Polygon && b instanceof Oval) {
            return ovalPolygon((Oval) b, (Polygon) a);
        }
        if (a instanceof Oval && b instanceof Polygon) {
            return ovalPolygon((Oval) a, (Polygon) b);
        }
        if (a instanceof Point && b instanceof Oval) {
            return pointInOval((Point) a, (Oval) b);
        }
        if (a instanceof Oval && b instanceof Point) {
            return pointInOval((Point) b, (Oval) a);
        }

        // No exact routine for this pair (a Line is involved) -- the
        // broad-phase AABB test above already passed, so that's the answer.
        // Swap in exact segment-vs-X math here if you need pixel-perfect
        // results against a Line.
        return true;
    }

    // =====================================================================
    // Exact narrow-phase routines
    // =====================================================================

    /** True if two circles overlap (the distance between their centers is at most the sum of their radii). */
    public static boolean circleCircle(Circle a, Circle b) {
        double dx = a.getCenter().getX() - b.getCenter().getX();
        double dy = a.getCenter().getY() - b.getCenter().getY();
        double radiusSum = a.getRadius() + b.getRadius();
        return dx * dx + dy * dy <= radiusSum * radiusSum;
    }

    /** True if a circle and an axis-aligned rectangle overlap. */
    public static boolean circleRectangle(Circle circle, Rectangle rect) {
        double cx = circle.getCenter().getX(), cy = circle.getCenter().getY();
        // The closest point on the rectangle to the circle's center is just
        // the center clamped into the rectangle's range on each axis.
        double closestX = clamp(cx, rect.getP1().getX(), rect.getP2().getX());
        double closestY = clamp(cy, rect.getP1().getY(), rect.getP2().getY());
        double dx = cx - closestX, dy = cy - closestY;
        double radius = circle.getRadius();
        return dx * dx + dy * dy <= radius * radius;
    }

    /** True if two axis-aligned rectangles overlap. */
    public static boolean rectangleRectangle(Rectangle a, Rectangle b) {
        return a.getP1().getX() <= b.getP2().getX() && a.getP2().getX() >= b.getP1().getX()
            && a.getP1().getY() <= b.getP2().getY() && a.getP2().getY() >= b.getP1().getY();
    }

    /** True if a point falls within (or on the edge of) an axis-aligned rectangle. */
    public static boolean pointInRectangle(Point point, Rectangle rect) {
        return point.getX() >= rect.getP1().getX() && point.getX() <= rect.getP2().getX()
            && point.getY() >= rect.getP1().getY() && point.getY() <= rect.getP2().getY();
    }

    /**
     * Ray-casting point-in-polygon test: casts a ray from the point out to
     * +infinity along the x-axis and counts how many polygon edges it
     * crosses. An odd number of crossings means the point is inside. Works
     * for any simple polygon (convex or concave), unlike the SAT-based
     * routines below which require convexity.
     */
    public static boolean pointInPolygon(Point point, Polygon polygon) {
        Point[] vertices = polygon.getPoints();
        boolean inside = false;
        double px = point.getX(), py = point.getY();
        for (int i = 0, j = vertices.length - 1; i < vertices.length; j = i++) {
            double xi = vertices[i].getX(), yi = vertices[i].getY();
            double xj = vertices[j].getX(), yj = vertices[j].getY();
            boolean edgeStraddlesRay = (yi > py) != (yj > py);
            if (edgeStraddlesRay) {
                double xCrossing = xi + (py - yi) / (yj - yi) * (xj - xi);
                if (px < xCrossing) {
                    inside = !inside;
                }
            }
        }
        return inside;
    }

    /**
     * True if a circle overlaps a convex polygon: either the circle's
     * center is inside the polygon, or some edge of the polygon passes
     * within {@code radius} of the center.
     */
    public static boolean circlePolygon(Circle circle, Polygon polygon) {
        Point center = circle.getCenter();
        if (pointInPolygon(center, polygon)) {
            return true;
        }
        double radius = circle.getRadius();
        Point[] vertices = polygon.getPoints();
        for (int i = 0, j = vertices.length - 1; i < vertices.length; j = i++) {
            double distance = distanceToSegment(center.getX(), center.getY(),
                vertices[j].getX(), vertices[j].getY(), vertices[i].getX(), vertices[i].getY());
            if (distance <= radius) {
                return true;
            }
        }
        return false;
    }

    /** True if an axis-aligned rectangle overlaps a convex polygon (exact, via SAT). */
    public static boolean rectanglePolygon(Rectangle rect, Polygon polygon) {
        return polygonPolygon(rectangleAsPolygon(rect), polygon);
    }

    // =====================================================================
    // Oval (ellipse) routines
    // =====================================================================
    //
    // An Oval is an axis-aligned ellipse -- not its bounding box -- so all
    // of the routines below treat it as one. The key trick used throughout:
    // an ellipse with center (cx, cy) and semi-axes (rx, ry) is exactly the
    // image of the unit circle (center (0,0), radius 1) under the invertible
    // map T(x, y) = ((x - cx) / rx, (y - cy) / ry). Since T is a bijection,
    // two regions overlap in the original coordinates if and only if their
    // images overlap after applying T -- so "does shape S touch this oval"
    // can always be answered by transforming S with T and asking "does the
    // transformed S touch the unit circle at the origin" instead. That's an
    // exact reduction, not an approximation, and it lets Oval-vs-Rectangle
    // and Oval-vs-Polygon reuse the existing circlePolygon() routine outright
    // rather than needing separate ellipse-specific geometry.
    //
    // Circle-vs-ellipse (needed directly for Oval-Circle, and to finish the
    // Oval-Oval case after the transform above turns one oval into a unit
    // circle) doesn't reduce this way, since anisotropic scaling turns a
    // circle into an ellipse rather than leaving it a circle. That case is
    // handled with an explicit point-to-ellipse-boundary distance below.

    /** True if a point falls within (or on the boundary of) an oval. */
    public static boolean pointInOval(Point point, Oval oval) {
        return pointInEllipse(point.getX(), point.getY(),
            ovalCenterX(oval), ovalCenterY(oval), ovalRadiusX(oval), ovalRadiusY(oval));
    }

    /** True if a circle and an oval (an actual ellipse, not its bounding box) overlap. */
    public static boolean circleOval(Circle circle, Oval oval) {
        double cx = circle.getCenter().getX(), cy = circle.getCenter().getY();
        return circleVsEllipse(cx, cy, circle.getRadius(),
            ovalCenterX(oval), ovalCenterY(oval), ovalRadiusX(oval), ovalRadiusY(oval));
    }

    /** True if two ovals (actual ellipses) overlap. */
    public static boolean ovalOval(Oval a, Oval b) {
        double acx = ovalCenterX(a), acy = ovalCenterY(a), arx = ovalRadiusX(a), ary = ovalRadiusY(a);
        // Transform b into a's frame, where a becomes the unit circle at the
        // origin (see the class comment above this section) -- since that's
        // an anisotropic scale with no rotation, b (axis-aligned) stays an
        // axis-aligned ellipse, just with a new center and semi-axes.
        double bcx = (ovalCenterX(b) - acx) / arx;
        double bcy = (ovalCenterY(b) - acy) / ary;
        double brx = ovalRadiusX(b) / arx;
        double bry = ovalRadiusY(b) / ary;
        return circleVsEllipse(0, 0, 1, bcx, bcy, brx, bry);
    }

    /** True if an oval (an actual ellipse, not its bounding box) and an axis-aligned rectangle overlap. */
    public static boolean ovalRectangle(Oval oval, Rectangle rect) {
        return ovalPolygon(oval, rectangleAsPolygon(rect));
    }

    /** True if an oval (an actual ellipse, not its bounding box) and a convex polygon overlap. */
    public static boolean ovalPolygon(Oval oval, Polygon polygon) {
        double cx = ovalCenterX(oval), cy = ovalCenterY(oval), rx = ovalRadiusX(oval), ry = ovalRadiusY(oval);
        Point[] vertices = polygon.getPoints();
        Point[] transformed = new Point[vertices.length];
        for (int i = 0; i < vertices.length; i++) {
            transformed[i] = new Point((vertices[i].getX() - cx) / rx, (vertices[i].getY() - cy) / ry);
        }
        // The oval is now exactly the unit circle at the origin in this
        // transformed space -- reuse circlePolygon() rather than duplicating
        // its point-in-polygon + edge-distance logic.
        return circlePolygon(new Circle(new Point(0, 0), 1), new Polygon(transformed));
    }

    private static double ovalCenterX(Oval oval) {
        return (oval.getP1().getX() + oval.getP2().getX()) / 2.0;
    }

    private static double ovalCenterY(Oval oval) {
        return (oval.getP1().getY() + oval.getP2().getY()) / 2.0;
    }

    private static double ovalRadiusX(Oval oval) {
        return Math.abs(oval.getP2().getX() - oval.getP1().getX()) / 2.0;
    }

    private static double ovalRadiusY(Oval oval) {
        return Math.abs(oval.getP2().getY() - oval.getP1().getY()) / 2.0;
    }

    /** True if the point (px, py) falls within (or on) the ellipse centered at (cx, cy) with semi-axes (rx, ry). */
    private static boolean pointInEllipse(double px, double py, double cx, double cy, double rx, double ry) {
        double nx = (px - cx) / rx;
        double ny = (py - cy) / ry;
        return nx * nx + ny * ny <= 1.0;
    }

    /** True if a circle (center, radius) overlaps an ellipse (center, semi-axes). */
    private static boolean circleVsEllipse(double ccx, double ccy, double radius,
                                            double ecx, double ecy, double erx, double ery) {
        if (pointInEllipse(ccx, ccy, ecx, ecy, erx, ery)) {
            return true; // circle's center is inside the ellipse (also covers the ellipse being entirely inside the circle, since the ellipse is convex)
        }
        return distanceToEllipseBoundary(ccx, ccy, ecx, ecy, erx, ery) <= radius;
    }

    /**
     * Shortest distance from an external point to an ellipse's boundary.
     * <p>
     * An ellipse boundary is convex, so the squared-distance-to-boundary
     * function has a single global minimum as you sweep the parametric angle
     * around from 0 to 2*PI -- except that for a point close to the center
     * of a very elongated ellipse, that function can have more than one
     * local minimum (the two "ends" competing to be closest). Rather than a
     * single local search (e.g. plain Newton's method) that can converge to
     * the wrong one, this does a coarse scan across the whole boundary to
     * find the right neighborhood, then refines with golden-section search
     * -- which only assumes unimodality within that neighborhood, not
     * globally -- down to effectively floating-point precision.
     */
    private static double distanceToEllipseBoundary(double px, double py, double cx, double cy, double rx, double ry) {
        int coarseSteps = 180;
        double bestTheta = 0;
        double bestDistSq = Double.POSITIVE_INFINITY;
        for (int i = 0; i < coarseSteps; i++) {
            double theta = 2 * Math.PI * i / coarseSteps;
            double distSq = ellipseBoundaryDistanceSquared(theta, px, py, cx, cy, rx, ry);
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                bestTheta = theta;
            }
        }

        double step = 2 * Math.PI / coarseSteps;
        double lo = bestTheta - step, hi = bestTheta + step;
        double resphi = 2 - (1 + Math.sqrt(5)) / 2; // golden ratio conjugate, ~0.382
        double x1 = lo + resphi * (hi - lo);
        double x2 = hi - resphi * (hi - lo);
        double f1 = ellipseBoundaryDistanceSquared(x1, px, py, cx, cy, rx, ry);
        double f2 = ellipseBoundaryDistanceSquared(x2, px, py, cx, cy, rx, ry);
        for (int i = 0; i < 50; i++) {
            if (f1 < f2) {
                hi = x2;
                x2 = x1;
                f2 = f1;
                x1 = lo + resphi * (hi - lo);
                f1 = ellipseBoundaryDistanceSquared(x1, px, py, cx, cy, rx, ry);
            } else {
                lo = x1;
                x1 = x2;
                f1 = f2;
                x2 = hi - resphi * (hi - lo);
                f2 = ellipseBoundaryDistanceSquared(x2, px, py, cx, cy, rx, ry);
            }
        }
        return Math.sqrt(Math.min(f1, f2));
    }

    /**
     * Squared distance from the point (px, py) to the point on the ellipse
     * boundary (center (cx, cy), semi-axes (rx, ry)) at parametric angle
     * {@code theta}. Used as the objective function that
     * {@link #distanceToEllipseBoundary} minimizes over {@code theta}.
     */
    private static double ellipseBoundaryDistanceSquared(double theta, double px, double py, double cx, double cy, double rx, double ry) {
        double dx = cx + rx * Math.cos(theta) - px;
        double dy = cy + ry * Math.sin(theta) - py;
        return dx * dx + dy * dy;
    }

    /**
     * Separating Axis Theorem test for two convex polygons. For every edge
     * of both shapes, projects both polygons onto the axis perpendicular to
     * that edge; if the projections don't overlap on any single axis, that
     * axis "separates" the shapes and they cannot be colliding. If no
     * separating axis is found after checking every edge of both polygons,
     * they must overlap.
     */
    public static boolean polygonPolygon(Polygon a, Polygon b) {
        return !hasSeparatingAxis(a, b) && !hasSeparatingAxis(b, a);
    }

    private static boolean hasSeparatingAxis(Polygon edgeSource, Polygon other) {
        Point[] vertices = edgeSource.getPoints();
        for (int i = 0, j = vertices.length - 1; i < vertices.length; j = i++) {
            // The axis perpendicular to this edge is a candidate separating axis.
            double edgeX = vertices[i].getX() - vertices[j].getX();
            double edgeY = vertices[i].getY() - vertices[j].getY();
            double axisX = -edgeY, axisY = edgeX;

            double[] rangeA = projectOntoAxis(edgeSource, axisX, axisY);
            double[] rangeB = projectOntoAxis(other, axisX, axisY);
            if (rangeA[1] < rangeB[0] || rangeB[1] < rangeA[0]) {
                return true; // found a gap -- the shapes cannot be touching
            }
        }
        return false;
    }

    private static double[] projectOntoAxis(Polygon polygon, double axisX, double axisY) {
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        for (Point vertex : polygon.getPoints()) {
            double projection = vertex.getX() * axisX + vertex.getY() * axisY;
            min = Math.min(min, projection);
            max = Math.max(max, projection);
        }
        return new double[] { min, max };
    }

    /**
     * Converts a {@link Rectangle} into an equivalent four-vertex
     * {@link Polygon} (corners in order), so rectangle-vs-polygon and
     * rectangle-vs-oval cases can reuse the polygon/circle routines instead
     * of duplicating their geometry.
     */
    private static Polygon rectangleAsPolygon(Rectangle rect) {
        Point p1 = rect.getP1(), p2 = rect.getP2();
        return new Polygon(new Point[] {
            new Point(p1.getX(), p1.getY()),
            new Point(p2.getX(), p1.getY()),
            new Point(p2.getX(), p2.getY()),
            new Point(p1.getX(), p2.getY())
        });
    }

    /** Shortest distance from point (px, py) to the segment (x1,y1)-(x2,y2). */
    private static double distanceToSegment(double px, double py, double x1, double y1, double x2, double y2) {
        double dx = x2 - x1, dy = y2 - y1;
        double lengthSquared = dx * dx + dy * dy;
        double t = lengthSquared == 0 ? 0 : clamp(((px - x1) * dx + (py - y1) * dy) / lengthSquared, 0, 1);
        double closestX = x1 + t * dx, closestY = y1 + t * dy;
        double ddx = px - closestX, ddy = py - closestY;
        return Math.sqrt(ddx * ddx + ddy * ddy);
    }

    /** Clamps {@code value} into the inclusive range [{@code min}, {@code max}]. */
    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    // =====================================================================
    // Simple collision response (minimum translation vector)
    // =====================================================================

    /**
     * Result of resolving a collision between two bounding boxes: how far,
     * and in which direction, box {@code a} would need to move so the two
     * boxes just touch instead of overlap. {@code normalX}/{@code normalY}
     * point away from {@code b}, i.e. the direction {@code a} should move;
     * move {@code a} by {@code (normalX * depth, normalY * depth)} to
     * separate the two boxes along whichever axis needs the smaller nudge.
     * <p>
     * This is deliberately simple (box-vs-box, one axis at a time) rather
     * than a full physics response -- it's meant for "stop at the wall" /
     * "push apart" style behavior, not realistic bouncing off an angled
     * surface. For a rotated polygon, the box used is its current AABB, so
     * the push direction is an approximation, not the exact polygon normal.
     */
    public static final class Resolution {
        /** Whether the two boxes were actually overlapping. */
        public final boolean collided;
        /** The direction (as a unit-ish axis vector) {@code a} should move to separate from {@code b}. */
        public final double normalX, normalY;
        /** How far along {@code (normalX, normalY)} to move {@code a} to just clear {@code b}. */
        public final double depth;

        private Resolution(boolean collided, double normalX, double normalY, double depth) {
            this.collided = collided;
            this.normalX = normalX;
            this.normalY = normalY;
            this.depth = depth;
        }

        /** Shared, immutable result for "no collision" -- avoids allocating a fresh instance every non-colliding call. */
        private static final Resolution NONE = new Resolution(false, 0, 0, 0);
    }

    /**
     * Resolves a collision between two bounding boxes directly.
     *
     * @param a the box to compute a push-out direction/distance for
     * @param b the box {@code a} is being pushed out of
     * @return the resolution, or {@link Resolution#collided} {@code false} if the boxes don't overlap
     */
    public static Resolution resolve(Bounds a, Bounds b) {
        if (!a.intersects(b)) {
            return Resolution.NONE;
        }
        double overlapX = Math.min(a.maxX, b.maxX) - Math.max(a.minX, b.minX);
        double overlapY = Math.min(a.maxY, b.maxY) - Math.max(a.minY, b.minY);

        double aCenterX = (a.minX + a.maxX) / 2, bCenterX = (b.minX + b.maxX) / 2;
        double aCenterY = (a.minY + a.maxY) / 2, bCenterY = (b.minY + b.maxY) / 2;

        if (overlapX < overlapY) {
            double direction = aCenterX < bCenterX ? -1 : 1;
            return new Resolution(true, direction, 0, overlapX);
        } else {
            double direction = aCenterY < bCenterY ? -1 : 1;
            return new Resolution(true, 0, direction, overlapY);
        }
    }

    /**
     * Convenience overload: resolves a collision between two
     * {@link GraphicsObject}s via their bounding boxes.
     *
     * @param a the object to compute a push-out direction/distance for
     * @param b the object {@code a} is being pushed out of
     * @return the resolution, or {@link Resolution#collided} {@code false} if the boxes don't overlap
     */
    public static Resolution resolve(GraphicsObject a, GraphicsObject b) {
        return resolve(getBounds(a), getBounds(b));
    }
}