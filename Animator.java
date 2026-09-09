package graphics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.DoubleConsumer;
import java.util.function.Supplier;

/**
 * Centralized, single-threaded animation driver shared by every animatable
 * {@link GraphicsObject} (Point, Line, Rectangle, Oval, Polygon, Circle,
 * Image, Text).
 * <p>
 * Previously, every timed {@code move(dx, dy, time, ...)} call spawned its
 * own brand-new {@code Thread} that busy-polled every 10ms until the
 * animation finished. That meant:
 * <ul>
 *   <li>An unbounded number of OS threads could be created (one per call,
 *       with nothing tracking or capping them) &mdash; effectively a leak
 *       under normal repeated-call usage.</li>
 *   <li>Calling {@code move()} again on the same object before the first
 *       animation finished caused two threads to race on the same fields,
 *       with no cancellation of the earlier one.</li>
 *   <li>Every animation independently forced a synchronous
 *       {@code GraphWin.update()} (paintImmediately + Toolkit.sync) roughly
 *       100 times per second, which does not scale as the number of
 *       simultaneously-animating objects grows.</li>
 * </ul>
 * {@code Animator} fixes all three: there is exactly one background thread
 * total (a single-threaded {@link ScheduledExecutorService}) that advances
 * every active animation on each tick, animations are keyed by "owner" so a
 * new call on the same object automatically cancels/replaces the previous
 * one, and intermediate frames use a cheap coalesced {@code repaint()}
 * instead of a synchronous {@code update()} on every tick.
 */
public final class Animator {

    private static final long TICK_MS = 10;

    private static final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "graphics-animator");
            t.setDaemon(true); // never blocks JVM shutdown
            return t;
        });

    // Keyed by the owning GraphicsObject instance (identity equality, since
    // none of these classes override equals/hashCode) so a new animation on
    // the same object replaces rather than races the previous one.
    private static final Map<Object, Animation> active = new ConcurrentHashMap<>();

    static {
        scheduler.scheduleAtFixedRate(Animator::tick, TICK_MS, TICK_MS, TimeUnit.MILLISECONDS);
    }

    private Animator() {
        // Utility class; not instantiable.
    }

    /**
     * Starts (or replaces) a timed, eased animation owned by {@code owner}.
     *
     * @param owner           the object this animation belongs to; used so that a
     *                        second call for the same object cancels the first
     *                        instead of racing it
     * @param time            duration in seconds
     * @param style           easing curve
     * @param direction       easing direction
     * @param onProgress      called on every tick with the eased progress in [0, 1)
     * @param onFinish        called exactly once, after the last tick, to set the
     *                        exact final values (bypassing easing entirely, so
     *                        floating-point rounding can never leave the object
     *                        short of its target)
     * @param canvasSupplier  supplies the object's current canvas (may return
     *                        null if the object isn't drawn); queried fresh on
     *                        every tick since an object can be drawn/undrawn
     *                        while animating
     */
    public static void animate(Object owner, double time, EasingStyle style, EasingDirection direction,
                                DoubleConsumer onProgress, Runnable onFinish,
                                Supplier<GraphWin> canvasSupplier) {
        if (time <= 0) {
            // Nothing to animate; jump straight to the end state.
            cancel(owner);
            onFinish.run();
            GraphWin canvas = canvasSupplier.get();
            if (canvas != null) {
                canvas.update();
            }
            return;
        }
        active.put(owner, new Animation(time, style, direction, onProgress, onFinish, canvasSupplier));
    }

    /**
     * Cancels any in-flight animation owned by {@code owner}, leaving it at
     * its current (partially-animated) position. Classes call this from
     * {@code undraw()} so an animation doesn't keep running-in-the-background
     * pointlessly after the object has been removed from its canvas.
     */
    public static void cancel(Object owner) {
        active.remove(owner);
    }

    private static void tick() {
        if (active.isEmpty()) {
            return;
        }
        for (Map.Entry<Object, Animation> entry : active.entrySet()) {
            Animation anim = entry.getValue();
            boolean finished = anim.step();
            if (finished) {
                // Remove only if it's still the same animation instance (guards
                // against a rare race where it was already replaced this tick).
                active.remove(entry.getKey(), anim);
            }
        }
    }

    /** Internal bookkeeping for a single in-flight animation. */
    private static final class Animation {
        private final long startTime = System.nanoTime();
        private final long durationNanos;
        private final EasingStyle style;
        private final EasingDirection direction;
        private final DoubleConsumer onProgress;
        private final Runnable onFinish;
        private final Supplier<GraphWin> canvasSupplier;

        Animation(double time, EasingStyle style, EasingDirection direction,
                  DoubleConsumer onProgress, Runnable onFinish, Supplier<GraphWin> canvasSupplier) {
            this.durationNanos = (long) (time * 1_000_000_000L);
            this.style = style;
            this.direction = direction;
            this.onProgress = onProgress;
            this.onFinish = onFinish;
            this.canvasSupplier = canvasSupplier;
        }

        /** Advances this animation by one tick. Returns true if it has finished. */
        boolean step() {
            long elapsed = System.nanoTime() - startTime;
            double linearProgress = durationNanos <= 0 ? 1.0 : (double) elapsed / durationNanos;
            GraphWin canvas = canvasSupplier.get();

            if (linearProgress >= 1.0) {
                onFinish.run();
                if (canvas != null) {
                    canvas.update(); // guarantee the final frame is flushed exactly once
                }
                return true;
            }

            double eased = Easing.apply(linearProgress, style, direction);
            onProgress.accept(eased);
            if (canvas != null) {
                // Cheap, coalesced repaint for intermediate frames rather than a
                // synchronous update() on every tick.
                canvas.repaint();
            }
            return false;
        }
    }
}