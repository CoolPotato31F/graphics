# Java Graphics

## Overview
The **Graphics** package provides a simple yet powerful framework for creating graphical windows and rendering objects in Java. It supports various graphical elements such as shapes, text, images, and interactive input via mouse and keyboard.

## Features
- Basic graphical window (`GraphWin`) for rendering shapes and images
- `GraphicsObject` interface for managing graphical elements
- `Point` class for representing and drawing 2D points
- Interactive user input handling (keyboard and mouse), including named-key
  constants via `Key` and held-key polling via `isKeyPressed`
- Support for updating and refreshing graphical components
- Draw-order layering (`GraphWin.setLayer`) so an object can be pinned above
  or below others regardless of add/re-draw order
- Fullscreen support (`GraphWin.setFullscreen` / `toggleFullscreen`)
- A shared, single-threaded `Animator` driving every object's timed/eased
  `move`-style animations, using the `Easing` styles in `EasingStyle`/`EasingDirection`
- Whole-frame post-processing via the `Shader` interface and `GraphWin.addShader`
- Centralized shape-vs-shape `Collision` detection and simple resolution
- `PixelCanvas` for per-pixel/procedural rendering (pixel art, raycasting, generated images)
- `SkewedImage` for warping an image onto an arbitrary quadrilateral (not just
  the parallelograms a rotated/scaled `Image` supports)

## Installation
To use the **Graphics** package, add the `graphics` package to your Java project and ensure the necessary dependencies (such as `javax.swing` and `java.awt`) are included.

## Usage
### Creating a Graphical Window
```java
GraphWin window = new GraphWin("My Window", 500, 500, false);
```
This creates a 500x500 pixel window with the title "My Window".

### Drawing a Point
```java
Point point = new Point(100, 100);
point.draw(window);
```
This creates and draws a point at coordinates (100, 100).

### Drawing a Line
```java
Line line = new Line(new Point(450, 123), new Point(350, 150));
line.setWidth(4);
line.setType("dashed");
line.draw(window);
```
This creates a dashed line with a width of 4 pixels.

### Drawing a Circle
```java
Circle circ = new Circle(new Point(245, 180), 55);
circ.setFill(Color.RED);
circ.setWidth(3);
circ.draw(window);
```
This creates a Circle with a width of 3 and a filled color of red

### Drawing a Oval
```java
Oval oval = new Oval(new Point(30, 350), new Point(180, 450));
oval.setFill(Color.YELLOW);
oval.setWidth(10);
oval.setOutline(Color.BLUE);
oval.draw(window);
```
This creates a Oval with a width of 10 and a filled color of Yellow and a outline collor of Blue

### Drawing a Text
```java
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
```
This creates Text with a outline of 4 a box around it colored green a border of that box that is orange with aligned text in the center with the arial font at size 25

### Drawing a Polyon
```java
Point[] points = {new Point(350, 230), new Point(375, 300), new Point(245, 385)};
Polygon poly = new Polygon(points);
poly.setWidth(10);
poly.setFill(Color.MAGENTA);
poly.draw(window);
```
This creates a Polygon with a filled color Magenta with a width of 10

### Handling User Input
To capture key presses:
```java
String keyPressed = window.getKey();
System.out.println("Key Pressed: " + keyPressed);
if (keyPressed.equals(Key.ESCAPE)) {
    window.dispose();
}
```
To get the current mouse position:
```java
Point mousePos = window.getMouse();
System.out.println("Mouse Position: " + mousePos);
```

## Project Files
| File | Description |
|---|---|
| `GraphWin.java` | The graphical window: owns items, layers, shaders, and mouse/keyboard input. |
| `GraphicsObject.java` | Interface implemented by every drawable shape. |
| `Point.java` | A single 2D point. |
| `Line.java` | A line segment between two points. |
| `Circle.java` | A circle defined by center and radius. |
| `Oval.java` | An axis-aligned ellipse. |
| `Rectangle.java` | An axis-aligned rectangle. |
| `Polygon.java` | An arbitrary point-list polygon. |
| `RotatablePolygon.java` | A `Polygon` that can be rotated in place. |
| `Text.java` | Styled, aligned text with optional background/border/outline. |
| `Image.java` | A bitmap image supporting move/scale/rotate. |
| `SkewedImage.java` *(v0.0.5)* | An image warped onto four independent corner points. |
| `PixelCanvas.java` *(v0.0.5)* | A per-pixel-addressable canvas for procedural/pixel-art rendering. |
| `Animator.java` *(v0.0.6)* | Shared background driver for every object's timed, eased animations. |
| `Easing.java` *(v0.0.6)* | Shared easing-curve math used by `Animator`. |
| `EasingStyle.java` | Enum of easing curves (linear, sine, bounce, elastic, etc). |
| `EasingDirection.java` | Enum of easing directions (`IN`, `OUT`, `INOUT`). |
| `Shader.java` *(v0.0.9)* | Functional interface for whole-frame post-processing effects. |
| `Collision.java` *(v0.0.10)* | Centralized shape-vs-shape collision detection and simple resolution. |
| `Key.java` | Named constants for the key strings `GraphWin` reports. |
| `TestImage.jpg` | Sample image used by the `GraphWin` demo in `main()`. |
| `Dirt.jpg` *(v0.0.11)* | Additional sample texture/image asset. |
| `README.md` | This file. |

## Changelog
This tracks differences between this local copy of the package and the version
currently published on GitHub (still at Version 0.0.2). Everything below,
across every version number, is new relative to that published copy, and is
now reflected in `GraphWin.java`'s own `@version`/history comment as well
(currently at 0.0.11).

### Version 0.0.11 — 09/07/2026
- `getKey()` / `checkKeys()` now return key names, not raw codes/chars —
  both report human-readable, typeable strings (via `KeyEvent.getKeyText`,
  e.g. `"Escape"`, `"A"`) instead of an `int` key code or a possibly
  non-printable `char`.
- Added `GraphWin.isKeyPressed(String)` / `isKeyPressed(int)` for polling a
  specific key, meant to be used together with the named constants in the
  new `Key` class (`Key.SPACE`, `Key.ESCAPE`, ...) so callers don't have to
  guess or hardcode the strings.
- Added `GraphWin.clearItems()` and `getElapsedTime()`.
- `items` and `keysPressed` switched from a plain `ArrayList` to a
  `CopyOnWriteArrayList` so input handling (on the AWT event thread) and
  rendering/game-loop reads (on the caller's thread) can no longer race
  against each other.
- Added the sample asset `Dirt.jpg` alongside the existing `TestImage.jpg`.

### Version 0.0.10 — 09/06/2026
- Added `Collision`: centralized shape-vs-shape collision detection shared
  by every `GraphicsObject` shape, in the same spirit as `Easing` and
  `Animator`. A cheap bounding-box broad phase runs first, then exact
  narrow-phase tests (circle-circle, circle-rectangle, rectangle-rectangle,
  point/circle/rectangle-vs-polygon, polygon-polygon, and every `Oval`
  combination) run only once the boxes overlap. `Collision.resolve()` adds
  simple "push apart"/"stop at the wall" collision response via a
  minimum-translation vector.

### Version 0.0.9 — 09/05/2026
- Added a whole-frame post-processing pipeline: the new `Shader` functional
  interface plus `GraphWin.addShader()`/`removeShader()`/`clearShaders()`/
  `getShaders()`. Shaders run once per frame, in registration order, each
  receiving the previous one's output. Registering the first shader switches
  rendering through an offscreen buffer; programs that never call
  `addShader()` keep the old zero-overhead direct-to-screen path.

### Version 0.0.8 — 09/04/2026
- Added fullscreen support: `isFullscreen()`, `setFullscreen(boolean)`, and
  `toggleFullscreen()` for borderless fullscreen, restoring the previous
  windowed bounds when toggled back off.

### Version 0.0.7 — 09/03/2026
- Added draw-order layering via `setLayer(GraphicsObject, int)`: an object
  given a higher layer is always drawn after (i.e. on top of) objects with a
  lower one, regardless of add/re-add order. Anything never given an
  explicit layer defaults to 0 and behaves exactly as before.

### Version 0.0.6 — 09/02/2026
- Added `Animator` and `Easing`: a centralized, single-threaded animation
  driver shared by every animatable `GraphicsObject` (`Point`, `Line`,
  `Rectangle`, `Oval`, `Polygon`, `Circle`, `Image`, `Text`, `SkewedImage`,
  `PixelCanvas`). Previously, every timed `move(dx, dy, time, ...)` call
  spawned its own brand-new `Thread` that busy-polled until the animation
  finished, with no cap on the number of threads and no cancellation of an
  earlier animation when a new one started on the same object. `Animator`
  replaces all of that with exactly one background thread total; animations
  are keyed by owner so a new call on the same object automatically
  cancels/replaces the previous one, and intermediate frames use a cheap
  coalesced `repaint()` instead of a synchronous `update()` on every tick.
  `Easing` centralizes the easing-curve math that used to be copy-pasted
  identically into every animatable class.

### Version 0.0.5 — 08/31/2026
- Added `PixelCanvas`: a rectangular grid of individually-colorable pixels
  with its own resolution, independent of the on-screen size it's displayed
  at — for per-pixel/procedural rendering (raycasting, generated images,
  pixel art, flood fills) rather than composing a scene out of shape objects.
  Defaults to crisp nearest-neighbor scaling when the display size differs
  from the resolution; call `setPixelated(false)` for smooth/blurred scaling
  instead.
- Added `SkewedImage`: an image mapped onto four independently movable corner
  points instead of a single position/rotation/scale, so it can be stretched
  into an arbitrary quadrilateral (e.g. a trapezoid), not just the
  parallelograms an `AffineTransform`-based `Image` supports. Takes a fast,
  hardware-accelerated path when the four corners still form a
  parallelogram, and falls back to a cached, software-rasterized perspective
  warp otherwise.

### Version 0.0.4 — 08/31/2026
- Fixed `getCurrentMousePosition()` never updating while a mouse button is
  held down and the mouse is moved — only `mouseMoved` was handled, not
  `mouseDragged`, so dragging something via `checkMouse()` +
  `getCurrentMousePosition()` would snap to the initial click point and then
  never follow the cursor.

### Version 0.0.3 — 08/26/2026
- Fixed key input issues on macOS where certain keys were not being recorded.
- Added the `Key` class to provide constants for key names, making it easier
  to check for specific key presses.

### Version 0.0.2
- Added `RotatablePolygon` to the library. `RotatablePolygon` is castable to
  a `Polygon`, which just makes it a standard polygon with no ability to rotate.

### Version 0.0.1
- Basic graphical objects and rendering functionality.

## License
This package is licensed under the MIT License.

## Author
**Kaiser Fechner**

For any issues or feature requests, please open an issue on GitHub.

![image](https://github.com/user-attachments/assets/a3f3c404-8fee-4ea2-89d5-b352040b3f31)
