package graphics;

import java.awt.Graphics2D;


/**
 * The {@code GraphicsObject} interface defines methods for drawing and 
 * removing graphical objects from a canvas or panel.
 */
public interface GraphicsObject {
    
    /**
     * Draws the graphical object on the specified {@code GraphWin} canvas.
     * 
     * @param canvas The canvas on which to draw the object.
     */
    void draw(GraphWin canvas);

    /**
     * Removes the graphical object from the canvas.
     */
    void undraw();

    /**
     * Draws the graphical object on a {@code Graphics2D} panel.
     * This method is used for rendering in graphical environments.
     * 
     * @param graphics The {@code Graphics2D} object used for drawing.
     */
    void drawPanel(Graphics2D graphics);
}