package graphics;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.util.concurrent.CountDownLatch;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * @author Kaiser Fechner
 * @version 0.0.11
 * Represents a graphical window where graphical objects can be drawn and interacted with.
 * Provides methods to add, remove, and update graphical objects like shapes, text, and images.
 * Supports mouse and keyboard input for interactive applications.
 *
 * All versions below are implemented unless explicitly stated in a above version.
 * |================= Version 0.0.11 =================| 09/07/2026
 * getKey() and checkKeys() now return key name Strings (via
 * KeyEvent.getKeyText, e.g. "Escape", "A") instead of a raw int key code or a
 * possibly non-printable char. Added isKeyPressed(String) and
 * isKeyPressed(int) for polling whether a specific key is currently held,
 * meant to be used together with the named constants in the Key class.
 * Added GraphWin.clearItems() and getElapsedTime().
 * items and keysPressed switched from plain ArrayList to CopyOnWriteArrayList
 * so input handling (on the AWT event thread) and rendering/game-loop reads
 * (on the caller's thread) can no longer race against each other.
 * Added the sample asset Dirt.jpg alongside the existing TestImage.jpg.
 * |================= Version 0.0.10 =================| 09/06/2026
 * Added the Collision class: centralized shape-vs-shape collision detection
 * shared by every GraphicsObject shape, in the same spirit as Easing and
 * Animator. A cheap axis-aligned bounding-box broad phase runs first, then
 * exact narrow-phase routines (circle-circle, circle-rectangle,
 * rectangle-rectangle, point/circle/rectangle-vs-polygon, polygon-polygon,
 * and every Oval combination) run only when the boxes overlap. Also
 * includes Collision.resolve() for simple "push apart" / "stop at the wall"
 * collision response via a minimum-translation vector.
 * |================= Version 0.0.9 =================| 09/05/2026
 * Added a whole-frame post-processing pipeline: the new Shader functional
 * interface plus GraphWin.addShader()/removeShader()/clearShaders()/getShaders().
 * Shaders run once per frame, in registration order, each receiving the
 * previous one's output. Registering the first shader switches rendering
 * through an offscreen buffer; programs that never call addShader() keep the
 * old zero-overhead draw-straight-to-the-panel path.
 * |================= Version 0.0.8 =================| 09/04/2026
 * Added fullscreen support: isFullscreen(), setFullscreen(boolean), and
 * toggleFullscreen() for borderless fullscreen, restoring the previous
 * windowed bounds when toggled back off.
 * |================= Version 0.0.7 =================| 09/03/2026
 * Added draw-order layering via setLayer(GraphicsObject, int): an object
 * given a higher layer is always drawn after (i.e. on top of) objects with a
 * lower one, regardless of add/re-add order. Anything never given an
 * explicit layer defaults to 0 and behaves exactly as before.
 * |================= Version 0.0.6 =================| 09/02/2026
 * Added the Animator and Easing classes: a centralized, single-threaded
 * animation driver shared by every animatable GraphicsObject (Point, Line,
 * Rectangle, Oval, Polygon, Circle, Image, Text, SkewedImage, PixelCanvas).
 * Previously, every timed move(dx, dy, time, ...) call spawned its own
 * brand-new Thread that busy-polled until the animation finished, with no
 * cap on the number of threads and no cancellation of an earlier animation
 * when a new one was started on the same object. Animator replaces all of
 * that with exactly one background thread total; animations are keyed by
 * owner so a new call on the same object automatically cancels/replaces the
 * previous one, and intermediate frames use a cheap coalesced repaint()
 * instead of a synchronous update() on every tick. Easing centralizes the
 * easing-curve math that used to be copy-pasted identically into every
 * animatable class.
 * |================= Version 0.0.5 =================| 08/31/2026
 * Added the PixelCanvas class: a rectangular grid of individually-colorable
 * pixels with its own resolution, independent of the on-screen size it's
 * displayed at -- for per-pixel/procedural rendering (raycasting, generated
 * images, pixel art, flood fills) rather than composing a scene out of
 * shape objects. Defaults to crisp nearest-neighbor scaling when the
 * display size differs from the resolution; call setPixelated(false) for
 * smooth/blurred scaling instead.
 * Added the SkewedImage class: an image mapped onto four independently
 * movable corner points instead of a single position/rotation/scale, so it
 * can be stretched into an arbitrary quadrilateral (e.g. a trapezoid), not
 * just the parallelograms an AffineTransform-based Image supports. Takes a
 * fast, hardware-accelerated path when the four corners still form a
 * parallelogram, and falls back to a cached, software-rasterized
 * perspective warp otherwise.
 * |================= Version 0.0.4 =================| 08/31/2026
 * Fix getCurrentMousePosition() never updating while a mouse button is held
 * down and the mouse is moved -- only mouseMoved was handled, not
 * mouseDragged, so dragging something via checkMouse() + getCurrentMousePosition()
 * would snap to the initial click point and then never follow the cursor.
 * |================= Version 0.0.3 =================| 08/26/2026
 * Fix key input issues on macOS where certain keys were not being recored.
 * Added the Key class to provide constants for key names, making it easier to check for specific key presses.
 * |================= Version 0.0.2 =================|
 * Added RotatablePolygon to the library.
 * The RotatablePolygon is castable to a Polygon which will just make it a
 * standard polygon with no ability to rotate.
 * |================= Version 0.0.1 =================|
 * Most simple version with just basic shapes and uses for those shape.
 * Very simple structure and all shapes are easily changed to adjust apperance
 */

public class GraphWin extends JFrame {

    /**
     * Serial version UID for serialization.
     */
    private static final long serialVersionUID = 9015929356158313978L;

    /**
     * A set of standard colors used in graphical objects.
     */
    public static final Color[] STANDARD_COLORS = {
        Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW,
        Color.CYAN, Color.MAGENTA, Color.BLACK,
        Color.LIGHT_GRAY, Color.DARK_GRAY, Color.ORANGE
    };

    private int width;
    private int height;
    public Panel panel;
    private CopyOnWriteArrayList<GraphicsObject> items;
    // Optional draw-layer per object: objects with a higher layer are
    // always drawn after (i.e. on top of) objects with a lower one,
    // regardless of add/re-add order. Anything never given an explicit
    // layer defaults to 0 and behaves exactly as before -- purely
    // insertion-order among itself and any other unlayered object. This
    // is what lets a caller guarantee something like "the grid is always
    // above the level geometry, and the HUD text is always above the
    // grid" even though doors, the grid, and the level all get
    // undrawn/redrawn (which normally bumps them to the very top of the
    // add order) at different, unpredictable times.
    private final Map<GraphicsObject, Integer> layers = new java.util.concurrent.ConcurrentHashMap<>();
    public boolean autoflush;
    private CountDownLatch latch;
    private Point mousePosition;
    private double deltaTime = 0;
    private long lastTime;
    private String lastKey = null;
    // Whether the window is currently in (borderless) fullscreen mode --
    // see setFullscreen -- and the exact windowed bounds to restore when
    // toggling back out of it.
    private boolean fullscreen = false;
    private java.awt.Rectangle windowedBounds;
    // CopyOnWriteArrayList (not a plain ArrayList) because this is written
    // from the AWT event thread (keyPressed/keyReleased) and read from
    // whatever thread calls checkKeys()/isKeyPressed() -- typically the
    // caller's main/game loop. A plain ArrayList being resized on one
    // thread while iterated/indexed on another can throw
    // ArrayIndexOutOfBoundsException or ConcurrentModificationException;
    // CopyOnWriteArrayList's snapshot semantics make every read here safe.
    private final CopyOnWriteArrayList<Integer> keysPressed = new CopyOnWriteArrayList<Integer>();
    private boolean mousePressed = false;

    // Post-processing shaders applied to the whole rendered frame, in
    // registration order -- see Shader for details. CopyOnWriteArrayList
    // for the same reason as keysPressed: shaders can be added/removed from
    // any thread while paintComponent() (running on the EDT) is iterating
    // over them.
    private final CopyOnWriteArrayList<Shader> shaders = new CopyOnWriteArrayList<Shader>();
    // Offscreen buffer scene objects are rendered into before shaders run.
    // Only allocated/used once at least one shader is registered, so
    // programs that never call addShader() keep the old zero-overhead
    // draw-straight-to-the-panel path.
    private BufferedImage frameBuffer;
    private double elapsedTime = 0;

    /**
     * Main method that demonstrates usage of GraphWin.
     *
     * @param args command-line arguments
     * @throws InterruptedException if interrupted during the program execution
     */
    public static void main(String[] args) throws InterruptedException {
        GraphWin window = new GraphWin("Testing", 500, 500, false);
        // Set the background color to cyan.
        window.setBackground(Color.CYAN);
        // Update the window to display the background.


        // Draw a point.
        Point point = new Point(100, 100);
        point.draw(window);

        // Draw various lines with different styles and widths.
        Line line = new Line(new Point(450, 123), new Point(350, 150));
        line.setWidth(4);
        line.setType("dashed");
        line.draw(window);

        line = new Line(new Point(450, 133), new Point(350, 160));
        line.setWidth(3);
        line.setType("dotted");
        line.draw(window);

        line = new Line(new Point(450, 113), new Point(350, 140));
        line.setWidth(3);
        line.setType("solid");
        line.draw(window);
        // Load and draw an image.
        Image image = new Image(new Point(450, 420), GraphWin.class.getResource("TestImage.jpg")); // Image initialization can take time.
        image.setScale(0.15);
        image.draw(window);

        // Draw a rectangle.
        Rectangle rect = new Rectangle(new Point(46, 200), new Point(146, 300));
        rect.setFill(Color.BLUE);
        rect.setWidth(3);
        rect.draw(window);

        // Draw a rotatable polygon.
        Point[] points = {new Point(350, 230), new Point(375, 300), new Point(245, 385)};
        RotatablePolygon poly = new RotatablePolygon(points);
        poly.rotate(40);
        poly.setWidth(15);
        poly.setFill(Color.MAGENTA);
        poly.draw(window);
        poly.getCenter().draw(window); //draw the center of the polygon.

        // Draw a circle.
        Circle circ = new Circle(new Point(245, 180), 55);
        circ.setFill(Color.RED);
        circ.setWidth(3);
        circ.draw(window);

        // Draw an oval.
        Oval oval = new Oval(new Point(30, 350), new Point(180, 450));
        oval.setFill(Color.YELLOW);
        oval.setWidth(10);
        oval.setOutline(Color.BLUE);
        oval.draw(window);

        // Draw text with various formatting.
        Text text = new Text("abcdefghijklmnopqrstuvwxyz\nABCDEFGHIJKLMNOPQRSTUFWXYZ\n1234567890!@#$%^&*()", new Point(250, 100));
        text.setFill(Color.BLUE);
        text.setOutlineWidth(4);
        text.setOutline(Color.BLACK);
        text.setBackground(Color.GREEN);
        text.setBorderWidth(2);
        text.setBorder(Color.ORANGE);
        text.setAlignment("center");
        text.setFont("Arial", Font.BOLD, 25);
        text.draw(window);

        // Text that shows whichever keys are currently held down, live.
        Text keysText = new Text("Keys: (none)", new Point(10, 30));
        keysText.setFill(Color.BLACK);
        keysText.setBackground(Color.WHITE);
        keysText.setBorder(Color.BLACK);
        keysText.setBorderWidth(1);
        keysText.setFont("Arial", Font.PLAIN, 16);
        keysText.draw(window);

        // Update the window to display all drawn objects.
        window.update();

        // Animation loop: rotate the polygon.
        while (window.isVisible()) {
            poly.rotate(100 * window.getDeltaTime());
            image.rotate(100 * window.getDeltaTime());
            window.setTitle("FPS: "+Math.round(1/window.getDeltaTime()));

            // Show whichever keys are currently pressed (e.g. Key.ESCAPE,
            // Key.SPACE, etc. can be compared against these same names).
            String[] pressed = window.checkKeys();
            keysText.setText(pressed.length == 0 ? "Keys: (none)" : "Keys: " + String.join(", ", pressed));

            window.update();
        }

        // Dispose of the window resources.
        window.dispose();
    }

    /**
     * Constructs a GraphWin window with a specified width, height, title, and autoflush setting.
     *
     * @param Name the title of the window
     * @param w the width of the window
     * @param h the height of the window
     * @param Autoflush whether the window should automatically flush and repaint
     */
    public GraphWin(String Name, int w, int h, boolean Autoflush) {
        super(Name);
        items = new CopyOnWriteArrayList<GraphicsObject>();
        width = w;
        height = h;
        autoflush = Autoflush;
        setVisible(true);
        Insets insets = getInsets();
        setSize(insets.left + insets.right + w, insets.top + insets.bottom + h);
        setResizable(true);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        // Without this, Swing's focus-traversal machinery intercepts Tab
        // (and Shift+Tab, Ctrl+Tab, etc.) to move focus between components
        // before it ever reaches the KeyListener below, so keyPressed()/
        // keyReleased() would silently never fire for Tab. Disabling focus
        // traversal keys on the window means Tab is delivered as a normal
        // key event instead, at the cost of Tab no longer moving focus
        // between AWT components (not a concern here since GraphWin doesn't
        // expose any focusable child components to tab between).
        setFocusTraversalKeysEnabled(false);
        this.panel = new Panel();
        this.panel.setPreferredSize(new Dimension(width, height));
        add(this.panel);

        latch = new CountDownLatch(1);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                latch.countDown();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    mousePressed = true;
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    mousePressed = false;
                }
            }
        });

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                String name = keyName(e.getKeyCode());
                if (name != null) { // ignore keys we don't have a canonical name for (e.g. Fn on many platforms never reports a real release either)
                    lastKey = name;
                    int keyString = e.getKeyCode();
                    if (!keysPressed.contains(keyString)) {
                        keysPressed.add(keyString);
                    }
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                int keyString = e.getKeyCode();

                keysPressed.remove(Integer.valueOf(keyString));
            }
        });
        mousePosition = new Point(-1, -1);

        addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                Insets insets = getInsets();
                mousePosition = new Point(e.getX() - insets.left, e.getY() - insets.top);
            }

            // AWT delivers mouseDragged (not mouseMoved) while a button is
            // held down and the mouse moves -- MouseMotionListener treats
            // "moving with a button held" and "moving with no button held"
            // as two different callbacks. Without this override,
            // mousePosition only ever updated on hover and stayed frozen at
            // wherever the mouse was when the button was first pressed, so
            // anything built on getCurrentMousePosition() + checkMouse()
            // (e.g. dragging an object) would snap to the initial click
            // point and then never actually follow the cursor.
            @Override
            public void mouseDragged(MouseEvent e) {
                Insets insets = getInsets();
                mousePosition = new Point(e.getX() - insets.left, e.getY() - insets.top);
            }
        });

        // Keeps width/height (used for the shader offscreen buffer's size
        // and returned by getWidth()/getHeight()) in sync whenever the
        // panel's actual on-screen size changes -- a live user resize
        // (now that the window is resizable) or a fullscreen toggle (see
        // setFullscreen) both land here. Without this, those fields --
        // and update()'s repaint region, which is defined directly in
        // terms of them -- would silently keep referring to whatever size
        // the window was constructed at, leaving newly-exposed area
        // unpainted after growing, or a stale oversized shader buffer
        // after shrinking.
        this.panel.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                syncSizeFromPanel();
            }
        });

        setVisible(true);
        update();
    }

    /**
     * Constructs a GraphWin window with a specified width, height, and title.
     * The autoflush setting is set to false by default.
     *
     * @param Name the title of the window
     * @param w the width of the window
     * @param h the height of the window
     */
    public GraphWin(String Name, int w, int h) {
        this(Name, w, h, true);
    }

    /**
     * Inner class to represent the panel within the window that handles rendering graphical objects.
     */
    private class Panel extends JPanel {
        /**
		 *
		 */
		private static final long serialVersionUID = -8207838817598203160L;

		public Panel() {
            super();
            setLayout(null);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            if (shaders.isEmpty()) {
                // No shaders registered -- draw straight to the panel like
                // before, with no offscreen buffer overhead.
                Graphics2D g2d = (Graphics2D) g;
                java.util.List<GraphicsObject> ordered = itemsInDrawOrder();
                for (int i = 0; i < ordered.size(); i++) {
                    ordered.get(i).drawPanel(g2d);
                }
                return;
            }

            // At least one shader is registered: render the scene into an
            // offscreen buffer first so shaders have a complete frame to
            // work with, then run it through each shader in order before
            // finally drawing the result to the screen.
            if (frameBuffer == null || frameBuffer.getWidth() != width || frameBuffer.getHeight() != height) {
                frameBuffer = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            }
            Graphics2D bufG = frameBuffer.createGraphics();
            bufG.setComposite(AlphaComposite.Clear);
            bufG.fillRect(0, 0, width, height);
            bufG.setComposite(AlphaComposite.SrcOver);
            bufG.setColor(getBackground());
            bufG.fillRect(0, 0, width, height);
            java.util.List<GraphicsObject> ordered = itemsInDrawOrder();
            for (int i = 0; i < ordered.size(); i++) {
                ordered.get(i).drawPanel(bufG);
            }
            bufG.dispose();

            BufferedImage result = frameBuffer;
            for (Shader shader : shaders) {
                result = shader.apply(result, elapsedTime);
            }

            ((Graphics2D) g).drawImage(result, 0, 0, null);
        }
    }

    /**
     * Registers a post-processing {@link Shader} that runs on the whole
     * rendered frame every update, after every {@link GraphicsObject} has
     * been drawn and before the frame is displayed. Shaders run in the
     * order they were added; each one receives the previous one's output.
     * <p>
     * Adding the first shader switches {@code GraphWin} to render through
     * an offscreen buffer rather than straight to the screen -- a small,
     * one-time cost, not a per-shader one.
     *
     * @param shader the shader to add
     */
    public void addShader(Shader shader) {
        shaders.add(shader);
    }

    /**
     * Removes a previously-added shader. Does nothing if the shader isn't
     * currently registered.
     *
     * @param shader the shader to remove
     */
    public void removeShader(Shader shader) {
        shaders.remove(shader);
    }

    /** Removes every registered shader, returning to the plain, unfiltered render. */
    public void clearShaders() {
        shaders.clear();
    }

    /**
     * Returns the shaders currently registered on this window, in the
     * order they run.
     *
     * @return a defensive copy of the current shader list
     */
    public ArrayList<Shader> getShaders() {
        return new ArrayList<Shader>(shaders);
    }

    /**
     * Retrieves the color at the specified point in the window.
     *
     * @param x the x-coordinate of the point
     * @param y the y-coordinate of the point
     * @return the color at the point, or null if no component exists at the point
     */
    public Color getColorAtPoint(int x, int y) {
        Component component = getComponentAt(x, y);
        if (component != null) {
            return component.getBackground();
        } else {
            return null;
        }
    }

    /**
     * Retrieves the current position of the mouse in the window.
     * This method blocks until the mouse is clicked.
     *
     * @return a Point object representing the current mouse position
     */
    public boolean getMouse() {
        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return true;
    }

    /**
     * Returns the current position of the mouse as a Point.
     *
     * @return the current mouse position
     */
    public Point getCurrentMousePosition() {
        return mousePosition;
    }

    /**
     * Waits for a key press and returns the key that was pressed.
     * This method blocks until a key is pressed.
     * <p>
     * The returned value is a fixed, typeable, platform-independent key name
     * (see {@link #keyName(int)}) matching the constants in {@link Key} and
     * used consistently by {@link #checkKeys()} / {@link #isKeyPressed(String)}
     * too, e.g. {@code "Escape"}, {@code "Enter"}, {@code "A"}, {@code "Space"}
     * -- never a raw or symbolic/glyph {@code char} such as some platforms'
     * native key text can produce.
     *
     * @return the key that was pressed, as a typeable String (see {@link Key})
     */
    public String getKey() {
        lastKey = null;
        while (lastKey == null) {
            if (!isDisplayable()) {
                throw new RuntimeException("getKey in closed window");
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        String key = lastKey;
        lastKey = null;
        return key;
    }

    /**
     * Checks if the left mouse button is being held down.
     *
     * @return true if the left mouse button is pressed, false otherwise
     */
    public boolean checkMouse() {
        return mousePressed;
    }
    /**
     * @return the current width of the GraphWin object
     */
    @Override
    public int getWidth() {
        return width;
    }

    /**
     * @return the current height of the GraphWin object
     */
    @Override
    public int getHeight() {
        return height;
    }

    /**
     * True if the window is currently in fullscreen mode (see {@link #setFullscreen}).
     */
    public boolean isFullscreen() {
        return fullscreen;
    }

    /**
     * Switches between a normal, decorated, resizable window and a
     * borderless window filling the entire screen.
     * <p>
     * This is "borderless fullscreen" (undecorated + sized to the
     * screen), not Java's exclusive full-screen mode
     * ({@code GraphicsDevice#setFullScreenWindow}) -- exclusive mode can
     * be flaky across platforms and multi-monitor setups and makes
     * toggling back out (or alt-tabbing) more fragile, whereas this style
     * is what most simple Swing/AWT games use and behaves consistently
     * everywhere. Safe to call at any time, e.g. bound to a key like F11
     * by whatever's using this window; a no-op if already in the
     * requested state.
     * <p>
     * Toggling back off restores the exact windowed position/size the
     * window had right before going fullscreen.
     */
    public void setFullscreen(boolean enable) {
        if (enable == fullscreen) {
            return;
        }
        fullscreen = enable;

        // setUndecorated() can only be called while the frame isn't
        // "displayable" -- dispose() tears down the native peer (not the
        // Component tree: this frame, its listeners, and panel all
        // survive) so it can be changed, then setVisible(true) recreates
        // the peer in the new state.
        if (enable) {
            windowedBounds = getBounds(); // remember exactly where/how big we were, to restore later
            dispose();
            setUndecorated(true);
            GraphicsDevice device = getGraphicsConfiguration() != null
                ? getGraphicsConfiguration().getDevice()
                : GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
            setBounds(device.getDefaultConfiguration().getBounds());
        } else {
            dispose();
            setUndecorated(false);
            if (windowedBounds != null) {
                setBounds(windowedBounds);
            }
        }
        setVisible(true);
        panel.requestFocusInWindow();

        // The panel's actual pixel size just changed -- keep width/height
        // (and the repaint region/shader buffer that depend on them) in
        // sync, exactly as a live user resize would via the
        // componentResized listener registered in the constructor (which
        // this dispose()/setVisible() cycle doesn't reliably re-trigger
        // on every platform, so it's done explicitly here too).
        syncSizeFromPanel();
    }

    /** Flips fullscreen on if it's currently off, or off if it's currently on. Convenience for a single key/menu binding. */
    public void toggleFullscreen() {
        setFullscreen(!fullscreen);
    }

    /**
     * Refreshes width/height from the panel's actual current on-screen
     * size and repaints. Called automatically whenever the panel is
     * resized -- by the user dragging the window's edges (now that the
     * window is resizable) or by {@link #setFullscreen} -- there's
     * normally no need to call this directly.
     */
    private void syncSizeFromPanel() {
        width = panel.getWidth();
        height = panel.getHeight();
        panel.revalidate();
        panel.repaint();
    }
    /**
     * Sets the background of the GraphWin
     *
     * @param clr the color to set the Background
     */
    @Override
    public void setBackground(Color clr) {
    	// Forces the background to also call the panel set Background
    	if (this.panel != null) {
    		super.setBackground(clr);
        	this.panel.setBackground(clr);
        	if (autoflush==true) {
        		update();
        	}
    	} else { // Forced to do this because this function gets called on GraphWin initialization
    		super.setBackground(clr);
    	}
    }

    /**
     * Removes a graphical object from the window.
     *
     * @param object the graphical object to remove
     */
    public void deleteItem(GraphicsObject object) {
        items.remove(object);
        layers.remove(object);
        if (autoflush==true) {
        	update();
        }
    }

    /**
     * Adds a graphical object to the window.
     *
     * @param object the graphical object to add
     */
    public void addItem(GraphicsObject object) {
        items.add(object);
        if (autoflush==true) {
        	update();
        }
    }


    /**
     * Returns all objects drawn on the window;
     *
     * @return Array of all items in the window
     */
    public ArrayList<GraphicsObject> getItems() {
        return new ArrayList<GraphicsObject>(items);
    }

    /**
     * Removes every graphical object from the window in one call.
     * <p>
     * Note this is <em>not</em> the same as {@code getItems().clear()} --
     * {@link #getItems()} intentionally returns a defensive copy (so
     * callers can't mutate the canvas's internal list by surprise), which
     * means calling {@code clear()} on that copy does nothing to what's
     * actually drawn. Games/animations that rebuild the whole scene every
     * frame (e.g. a raycaster redrawing every wall column) should call
     * this instead, or every object ever drawn will keep accumulating
     * forever and rendering will get progressively slower.
     */
    public void clearItems() {
        items.clear();
        layers.clear();
        if (autoflush) {
            update();
        }
    }

    /**
     * Assigns a draw layer to an object already (or about to be) added to
     * this window: objects with a higher layer are always drawn after --
     * i.e. on top of -- objects with a lower one, no matter what order
     * they were added or re-added in. Objects with no explicitly assigned
     * layer default to 0.
     * <p>
     * Within the same layer, draw order still just follows normal add
     * order (unaffected by this call either way), so this only needs to
     * be called once per object -- including once per *replacement*
     * object, if something is the kind of thing that gets undrawn and
     * rebuilt from scratch (a rebaked level, a rebuilt grid, refreshed
     * door sprites) -- not every frame.
     *
     * @param object the object to assign a layer to (safe to call before or after {@link #addItem})
     * @param layer  higher draws on top; can be negative
     */
    public void setLayer(GraphicsObject object, int layer) {
        layers.put(object, layer);
    }

    /**
     * A snapshot of {@link #items}, stably sorted by draw layer (see
     * {@link #setLayer}) -- objects with no assigned layer sort as 0.
     * Stable sorting means objects within the same layer keep whatever
     * relative order they're already in, so this only ever reorders
     * *across* layers, never within one.
     */
    private java.util.List<GraphicsObject> itemsInDrawOrder() {
        java.util.List<GraphicsObject> ordered = new ArrayList<>(items);
        ordered.sort(Comparator.comparingInt(o -> layers.getOrDefault(o, 0)));
        return ordered;
    }

    /**
     * Updates the window by calculating delta time and forcing a repaint.
     */
    public void update() {
        if (lastTime == 0) {
            lastTime = System.nanoTime();
        }
        deltaTime = (System.nanoTime() - lastTime) / 1_000_000_000.0;
        lastTime = System.nanoTime();
        elapsedTime += deltaTime;

        Runnable paintTask = () -> panel.paintImmediately(0, 0, width, height);

        if (SwingUtilities.isEventDispatchThread()) {
            paintTask.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(paintTask);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (java.lang.reflect.InvocationTargetException e) {
                e.printStackTrace();
            }
        }

        Toolkit.getDefaultToolkit().sync();
    }

    /**
     * Returns the time difference between the last update and the current update.
     *
     * @return the delta time in seconds
     */
    public double getDeltaTime() {
        return deltaTime;
    }

    /**
     * Returns the total time elapsed since this window was created, in
     * seconds -- the same clock value passed to every {@link Shader}'s
     * {@code apply(frame, time)} call. Useful for timestamping events
     * (e.g. a mouse click) on the same time domain a shader uses, so the
     * two stay in sync.
     *
     * @return seconds since this window was created
     */
    public double getElapsedTime() {
        return elapsedTime;
    }

    /**
     * Maps a {@code KeyEvent.VK_*} key code to a fixed, typeable,
     * platform-independent name matching the constants in {@link Key}.
     * <p>
     * This intentionally does <em>not</em> delegate to
     * {@link KeyEvent#getKeyText(int)}: on some platforms (notably macOS)
     * that method returns symbolic glyphs for certain keys -- e.g. an
     * escape-symbol glyph instead of the plain word {@code "Escape"}, or
     * arrow/graphic glyphs for modifier keys -- rather than ordinary,
     * typeable text. Keeping a small fixed table here means the name a
     * program sees is always plain text like {@code "Escape"} or
     * {@code "Shift"}, regardless of platform.
     * <p>
     * Returns {@code null} for key codes with no canonical name here. That
     * includes purely hardware-level keys such as Fn on many keyboards/OSes:
     * those are typically never delivered as a normal, trackable
     * {@code KEY_RELEASED} event, so a stray Fn press would otherwise get
     * stuck "held down" forever. Callers (both the key-pressed listener and
     * {@link #checkKeys()}/{@link #isKeyPressed(String)}) skip {@code null}
     * results, so such keys are simply never reported as pressed at all.
     *
     * @param keyCode a {@code KeyEvent.VK_*} key code
     * @return a canonical typeable name, or {@code null} if unrecognized
     */
    private static String keyName(int keyCode) {
        switch (keyCode) {
            // Letters
            case KeyEvent.VK_A: return Key.A;
            case KeyEvent.VK_B: return Key.B;
            case KeyEvent.VK_C: return Key.C;
            case KeyEvent.VK_D: return Key.D;
            case KeyEvent.VK_E: return Key.E;
            case KeyEvent.VK_F: return Key.F;
            case KeyEvent.VK_G: return Key.G;
            case KeyEvent.VK_H: return Key.H;
            case KeyEvent.VK_I: return Key.I;
            case KeyEvent.VK_J: return Key.J;
            case KeyEvent.VK_K: return Key.K;
            case KeyEvent.VK_L: return Key.L;
            case KeyEvent.VK_M: return Key.M;
            case KeyEvent.VK_N: return Key.N;
            case KeyEvent.VK_O: return Key.O;
            case KeyEvent.VK_P: return Key.P;
            case KeyEvent.VK_Q: return Key.Q;
            case KeyEvent.VK_R: return Key.R;
            case KeyEvent.VK_S: return Key.S;
            case KeyEvent.VK_T: return Key.T;
            case KeyEvent.VK_U: return Key.U;
            case KeyEvent.VK_V: return Key.V;
            case KeyEvent.VK_W: return Key.W;
            case KeyEvent.VK_X: return Key.X;
            case KeyEvent.VK_Y: return Key.Y;
            case KeyEvent.VK_Z: return Key.Z;

            // Digits (main row)
            case KeyEvent.VK_0: return Key.DIGIT_0;
            case KeyEvent.VK_1: return Key.DIGIT_1;
            case KeyEvent.VK_2: return Key.DIGIT_2;
            case KeyEvent.VK_3: return Key.DIGIT_3;
            case KeyEvent.VK_4: return Key.DIGIT_4;
            case KeyEvent.VK_5: return Key.DIGIT_5;
            case KeyEvent.VK_6: return Key.DIGIT_6;
            case KeyEvent.VK_7: return Key.DIGIT_7;
            case KeyEvent.VK_8: return Key.DIGIT_8;
            case KeyEvent.VK_9: return Key.DIGIT_9;

            // Numpad
            case KeyEvent.VK_NUMPAD0: return Key.NUMPAD_0;
            case KeyEvent.VK_NUMPAD1: return Key.NUMPAD_1;
            case KeyEvent.VK_NUMPAD2: return Key.NUMPAD_2;
            case KeyEvent.VK_NUMPAD3: return Key.NUMPAD_3;
            case KeyEvent.VK_NUMPAD4: return Key.NUMPAD_4;
            case KeyEvent.VK_NUMPAD5: return Key.NUMPAD_5;
            case KeyEvent.VK_NUMPAD6: return Key.NUMPAD_6;
            case KeyEvent.VK_NUMPAD7: return Key.NUMPAD_7;
            case KeyEvent.VK_NUMPAD8: return Key.NUMPAD_8;
            case KeyEvent.VK_NUMPAD9: return Key.NUMPAD_9;

            // Whitespace / editing
            case KeyEvent.VK_SPACE: return Key.SPACE;
            case KeyEvent.VK_ENTER: return Key.ENTER;
            case KeyEvent.VK_TAB: return Key.TAB;
            case KeyEvent.VK_BACK_SPACE: return Key.BACKSPACE;
            case KeyEvent.VK_DELETE: return Key.DELETE;
            case KeyEvent.VK_ESCAPE: return Key.ESCAPE;
            case KeyEvent.VK_INSERT: return Key.INSERT;

            // Navigation
            case KeyEvent.VK_UP: return Key.UP;
            case KeyEvent.VK_DOWN: return Key.DOWN;
            case KeyEvent.VK_LEFT: return Key.LEFT;
            case KeyEvent.VK_RIGHT: return Key.RIGHT;
            case KeyEvent.VK_HOME: return Key.HOME;
            case KeyEvent.VK_END: return Key.END;
            case KeyEvent.VK_PAGE_UP: return Key.PAGE_UP;
            case KeyEvent.VK_PAGE_DOWN: return Key.PAGE_DOWN;

            // Modifiers
            case KeyEvent.VK_SHIFT: return Key.SHIFT;
            case KeyEvent.VK_CONTROL: return Key.CONTROL;
            case KeyEvent.VK_ALT: return Key.ALT;
            case KeyEvent.VK_ALT_GRAPH: return Key.ALT_GRAPH;
            case KeyEvent.VK_META: return Key.META;
            case KeyEvent.VK_CAPS_LOCK: return Key.CAPS_LOCK;

            // Function keys
            case KeyEvent.VK_F1: return Key.F1;
            case KeyEvent.VK_F2: return Key.F2;
            case KeyEvent.VK_F3: return Key.F3;
            case KeyEvent.VK_F4: return Key.F4;
            case KeyEvent.VK_F5: return Key.F5;
            case KeyEvent.VK_F6: return Key.F6;
            case KeyEvent.VK_F7: return Key.F7;
            case KeyEvent.VK_F8: return Key.F8;
            case KeyEvent.VK_F9: return Key.F9;
            case KeyEvent.VK_F10: return Key.F10;
            case KeyEvent.VK_F11: return Key.F11;
            case KeyEvent.VK_F12: return Key.F12;

            // Punctuation (US layout)
            case KeyEvent.VK_MINUS: return Key.MINUS;
            case KeyEvent.VK_EQUALS: return Key.EQUALS;
            case KeyEvent.VK_COMMA: return Key.COMMA;
            case KeyEvent.VK_PERIOD: return Key.PERIOD;
            case KeyEvent.VK_SLASH: return Key.SLASH;
            case KeyEvent.VK_BACK_SLASH: return Key.BACK_SLASH;
            case KeyEvent.VK_SEMICOLON: return Key.SEMICOLON;
            case KeyEvent.VK_QUOTE: return Key.QUOTE;
            case KeyEvent.VK_OPEN_BRACKET: return Key.OPEN_BRACKET;
            case KeyEvent.VK_CLOSE_BRACKET: return Key.CLOSE_BRACKET;
            case KeyEvent.VK_BACK_QUOTE: return Key.BACK_QUOTE;

            default: return null; // unrecognized/hardware-only key (e.g. Fn) -- never reported as pressed
        }
    }

    /**
     * Returns the keys that are currently pressed, as their typeable string
     * names (e.g. holding 'D' and 'S' returns {"D", "S"}; holding Escape
     * returns {"Escape"}). These match the constants in {@link Key} and the
     * value returned by {@link #getKey()}, so they can be compared directly
     * against {@code Key.ESCAPE}, {@code Key.D}, etc. without worrying about
     * case.
     *
     * @return an array of the currently pressed keys' names
     */
    public String[] checkKeys() {
        // Iterate directly (rather than size()+get(i)) so this is safe even
        // if a key is pressed/released concurrently on the AWT event thread
        // mid-call -- CopyOnWriteArrayList's iterator works off a fixed
        // snapshot taken at this point, so it can never see a length change
        // partway through.
        ArrayList<String> keys = new ArrayList<String>();
        for (int code : keysPressed) {
            String name = keyName(code);
            if (name != null) {
                keys.add(name);
            }
        }
        return keys.toArray(new String[0]);
    }

    /**
     * Checks whether a specific key is currently being held down.
     * <p>
     * Matches against the same fixed, platform-independent names produced by
     * {@link #keyName(int)} (see {@link Key} for the exact strings), not the
     * OS-reported {@code KeyEvent.getKeyText(...)} value.
     *
     * @param key the key name to check (e.g. "D", "Space", "Left"), case-insensitive
     * @return true if the key is currently pressed, false otherwise
     */
    public boolean isKeyPressed(String key) {
        for (int code : keysPressed) {
            String name = keyName(code);
            if (name != null && name.equalsIgnoreCase(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks whether a specific key is currently being held down, using a raw
     * {@code KeyEvent.VK_*} key code. This is more reliable than the String
     * overload for symbol/punctuation keys (e.g. "+", "-", "="), since their
     * {@code KeyEvent.getKeyText(...)} name can vary by platform and JDK
     * version. Example: {@code window.isKeyPressed(KeyEvent.VK_EQUALS)}.
     *
     * @param keyCode the key code to check (a {@code java.awt.event.KeyEvent.VK_*} constant)
     * @return true if the key is currently pressed, false otherwise
     */
    public boolean isKeyPressed(int keyCode) {
        return keysPressed.contains(keyCode);
    }

    public void help() {
        try {
            Desktop desktop = Desktop.getDesktop();
            if (Desktop.isDesktopSupported() && desktop.isSupported(Desktop.Action.BROWSE)) {
            	System.out.println("Opening Java Graphics Wiki...");
                desktop.browse(new URI("https://github.com/CoolPotato31F/Java-Graphics/wiki"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
