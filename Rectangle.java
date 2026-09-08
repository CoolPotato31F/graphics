package graphics;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;

public class Rectangle implements GraphicsObject {

    private Point point1;
    private Point point2;
    private int width = 1;
    private int w;
    private int h;
    private GraphWin canvas;
    private Color fillColor;
    private Color outlineColor = Color.BLACK;

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

    @Override
    public void draw(GraphWin canvas) {
        if (this.canvas != null) {
            throw new Error("Object is already drawn");
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

    public void setFill(Color color) {
        this.fillColor = color;
    }

    public void setOutline(Color color) {
        this.outlineColor = color;
    }

    public Point getP1() {
        return point1;
    }

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

    public Point getSize() {
        return new Point(w, h);
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getWidth() {
        return this.width;
    }

    public void move(double dx, double dy) {
        point1.move(dx, dy);
        point2.move(dx, dy);
        if (this.canvas != null && this.canvas.autoflush) {
            this.canvas.repaint();
        }
    }

    public void move(double dx, double dy, double time) {
        move(dx, dy, time, EasingStyle.LINEAR, EasingDirection.IN);
    }

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

    @Override
    public void drawPanel(Graphics2D graphics) {
        if (fillColor != null) {
            graphics.setColor(fillColor);
            graphics.fillRect((int) point1.getX(), (int) point1.getY(), w, h);
        }
        graphics.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.setColor(outlineColor);
        graphics.drawRect((int) point1.getX(), (int) point1.getY(), w, h);
    }

    @Override
    public String toString() {
        return "Rectangle(" +
               "point1=" + point1 + ", " +
               "point2=" + point2 + ", " +
               "center=" + getCenter() + ", " +
               "width=" + width + ", " +
               "fillColor=" + (fillColor != null ? fillColor : "None") + ", " +
               "outlineColor=" + outlineColor + ")";
    }
}
