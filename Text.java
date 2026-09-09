package graphics;

import java.awt.*;
import java.awt.font.GlyphVector;


/**
 * Represents drawable, possibly multi-line text with configurable font,
 * fill color, outline, alignment, and an optional background rectangle
 * with its own border.
 */
public class Text implements GraphicsObject {
    private String content;
    private Point position;
    private Font font = new Font("Arial", Font.PLAIN, 25);
    private Color textFillColor = Color.BLACK; // Text fill color
    private Color rectangleFillColor = null; // Rectangle background color
    private Color borderColor = Color.BLACK; // Rectangle border color
    private Color textOutlineColor = Color.BLACK; // Text outline color
    private int borderWidth = 0;
    private int textOutlineWidth = 0;
    private GraphWin canvas;
    private String alignment = "left"; // Default alignment

    /**
     * Constructs a Text object.
     * 
     * @param content  The text string.
     * @param position The top-left position of the text.
     */
    public Text(String content, Point position) {
        this.content = content;
        this.position = position;
    }

    /**
     * Copy constructor for creating a new Text object based on another Text object.
     * @param other The Text object to copy from.
     */
    public Text(Text other) {
        this.content = other.content;
        this.position = new Point(other.position);
        this.font = other.font;
        this.textFillColor = other.textFillColor;
        this.rectangleFillColor = other.rectangleFillColor;
        this.borderColor = other.borderColor;
        this.textOutlineColor = other.textOutlineColor;
        this.borderWidth = other.borderWidth;
        this.textOutlineWidth = other.textOutlineWidth;
        this.alignment = other.alignment;
    }

    /**
     * Updates the text content shown, e.g. for live-updating displays
     * (scores, key presses, timers) without having to undraw/redraw a new
     * {@code Text} object every frame.
     *
     * @param content The new text string.
     */
    public void setText(String content) {
        this.content = content;
        if (canvas != null && canvas.autoflush) {
            canvas.repaint();
        }
    }

    /**
     * Gets the current text content.
     *
     * @return The text string currently displayed.
     */
    public String getText() {
        return content;
    }
    
    /**
     * Instantly moves the text by the specified x and y distances.
     *
     * @param dx The distance to move along the x-axis.
     * @param dy The distance to move along the y-axis.
     */
    public void move(double dx, double dy) {
        position.move(position.getX() + dx, position.getY() + dy);
        if (canvas != null && canvas.autoflush) {
            canvas.repaint();
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

    // Setters to change text, rectangle, and outline properties

    /**
     * Sets the font used to render the text.
     *
     * @param font The new font.
     */
    public void setFont(Font font) {
        this.font = font;
    }

    /**
     * Sets the font used to render the text by name, style, and size.
     *
     * @param fontName The font family name (e.g. "Arial").
     * @param style    The font style, one of the {@link Font} style constants
     *                 (e.g. {@code Font.PLAIN}, {@code Font.BOLD}).
     * @param size     The point size of the font.
     */
    public void setFont(String fontName, int style, int size) {
        this.font = new Font(fontName, style, size);
    }

    /**
     * Sets the fill color of the text itself.
     *
     * @param color The new text fill color.
     */
    public void setFill(Color color) {
        this.textFillColor = color; // Changes the color of the text
    }

    /**
     * Sets the fill color of the background rectangle drawn behind the
     * text. Pass {@code null} to draw no background rectangle.
     *
     * @param color The new background fill color, or {@code null} for none.
     */
    public void setBackground(Color color) {
        this.rectangleFillColor = color; // Changes the background color of the rectangle
    }

    /**
     * Sets the color of the background rectangle's border. Only visible if
     * {@link #setBorderWidth(int)} is greater than zero.
     *
     * @param color The new border color.
     */
    public void setBorder(Color color) {
        this.borderColor = color; // Changes the border color of the rectangle
    }

    /**
     * Sets the width of the background rectangle's border. A width of zero
     * (the default) draws no border.
     *
     * @param width The new border width.
     */
    public void setBorderWidth(int width) {
        this.borderWidth = width; // Changes the rectangle's border width
    }

    /**
     * Sets the color of the text's outline. Only visible if
     * {@link #setOutlineWidth(int)} is greater than zero.
     *
     * @param color The new text outline color.
     */
    public void setOutline(Color color) {
        this.textOutlineColor = color; // Changes the text's outline color
    }

    /**
     * Sets the width of the text's outline. A width of zero (the default)
     * draws no outline.
     *
     * @param width The new text outline width.
     */
    public void setOutlineWidth(int width) {
        this.textOutlineWidth = width; // Changes the text's outline width
    }

    /**
     * Sets the horizontal alignment of the text relative to its position.
     *
     * @param alignment One of {@code "left"}, {@code "center"}, or {@code "right"}.
     * @throws IllegalArgumentException if {@code alignment} is not one of the valid values.
     */
    public void setAlignment(String alignment) {
        // Set alignment to one of "left", "center", or "right"
        if (alignment.equals("left") || alignment.equals("center") || alignment.equals("right")) {
            this.alignment = alignment;
        } else {
            throw new IllegalArgumentException("Invalid alignment: Use 'left', 'center', or 'right'");
        }
    }

    /**
     * Draws the text on the given canvas.
     *
     * @param canvas The canvas on which the text will be drawn.
     * @throws IllegalStateException if the text is already drawn.
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
     * Removes the text from the canvas.
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
     * Renders the text (and its optional background/border/outline) onto a
     * {@code Graphics2D} panel, handling multi-line content and alignment.
     *
     * @param graphics The {@code Graphics2D} object used for rendering.
     */
    @Override
    public void drawPanel(Graphics2D graphics) {
        // Remember the original settings to restore later
        Color originalColor = graphics.getColor();
        Stroke originalStroke = graphics.getStroke();
        RenderingHints originalHints = graphics.getRenderingHints();

        graphics.setFont(font);

        String[] lines = content.split("\\n"); // Split content into lines
        FontMetrics metrics = graphics.getFontMetrics(font); // font doesn't change per-line; compute once

        int totalTextHeight = 0;
        int maxWidth = 0;
        int[] lineWidths = new int[lines.length];

        // Calculate total height and max width
        for (int i = 0; i < lines.length; i++) {
            int textWidth = metrics.stringWidth(lines[i]);
            int textHeight = metrics.getHeight();
            totalTextHeight += textHeight;
            maxWidth = Math.max(maxWidth, textWidth);
            lineWidths[i] = textWidth;
        }

        int x = (int) position.getX();
        int y = (int) position.getY();

        // Adjust x-coordinate based on alignment
        if (alignment.equals("center")) {
            x -= maxWidth / 2;
        } else if (alignment.equals("right")) {
            x -= maxWidth;
        }

        // Draw filled rectangle background
        if (rectangleFillColor != null) {
            graphics.setColor(rectangleFillColor);
            graphics.fillRect(x - 5, y - totalTextHeight, maxWidth + 10, totalTextHeight + 5);
        }

        // Draw border rectangle
        if (borderWidth > 0) {
            // JOIN_ROUND avoids sharp miter spikes at tight corners; a
            // plain axis-aligned rectangle's 90-degree corners are never
            // sharp enough to trigger it, but this keeps every shape's
            // stroke consistent.
            graphics.setStroke(new BasicStroke(borderWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            graphics.setColor(borderColor);
            graphics.drawRect(x - 5, y - totalTextHeight, maxWidth + 10, totalTextHeight + 5);
        }

        int yOffset = 0; // Offset for each line

        for (int i = 0; i < lines.length; i++) {
            int textHeight = metrics.getHeight();

            int lineX = x;

            // Adjust lineX based on line width and overall alignment
            if (alignment.equals("center")) {
                lineX = x + (maxWidth - lineWidths[i]) / 2;
            } else if (alignment.equals("right")) {
                lineX = x + (maxWidth - lineWidths[i]);
            }

            int lineY = y - totalTextHeight + yOffset + metrics.getAscent();

            // Draw text outline
            if (textOutlineWidth > 0) {
                GlyphVector glyphVector = font.createGlyphVector(graphics.getFontRenderContext(), lines[i]);
                Shape textShape = glyphVector.getOutline(lineX, lineY);

                // Activate anti-aliasing for text rendering
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

                // JOIN_ROUND (rather than the default JOIN_MITER) is what
                // fixes the "outline spikes past sharp letterforms" issue --
                // glyph outlines have plenty of sharp corners (the points of
                // a V, W, A, etc.), and a miter join extends those corners
                // outward proportional to how sharp the angle is.
                graphics.setStroke(new BasicStroke(textOutlineWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                graphics.setColor(textOutlineColor);
                graphics.draw(textShape); // Draw the outline
            }

            // Draw filled text
            graphics.setStroke(new BasicStroke(1)); // Reset stroke to default for text filling
            graphics.setColor(textFillColor); // Set text fill color
            graphics.drawString(lines[i], lineX, lineY); // Fill the text shape with color

            yOffset += textHeight; // Increment yOffset for the next line
        }

        // Restore original settings
        graphics.setColor(originalColor);
        graphics.setStroke(originalStroke);
        graphics.setRenderingHints(originalHints);
    }
    
    /**
     * @return A human-readable summary of this text's content, position,
     *         font, colors, and alignment.
     */
    @Override
    public String toString() {
        StringBuilder str = new StringBuilder("Text(");
        
        // Add content
        str.append("Content='").append(content).append("', ");
        
        // Add position
        str.append("Position=").append(position.toString()).append(", ");
        
        // Add font details
        str.append("Font=").append(font.getName()).append(", Size=").append(font.getSize()).append(", ");
        
        // Add colors
        str.append("TextFillColor=").append(textFillColor.toString()).append(", ");
        str.append("RectangleFillColor=").append(rectangleFillColor != null ? rectangleFillColor.toString() : "None").append(", ");
        str.append("BorderColor=").append(borderColor.toString()).append(", ");
        str.append("TextOutlineColor=").append(textOutlineColor.toString()).append(", ");
        
        // Add border width and outline width
        str.append("BorderWidth=").append(borderWidth).append(", ");
        str.append("TextOutlineWidth=").append(textOutlineWidth).append(", ");
        
        // Add alignment
        str.append("Alignment=").append(alignment);
        
        return str.append(")").toString();
    }
}