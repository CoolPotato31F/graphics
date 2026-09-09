package graphics;

/**
 * Shared easing-function evaluator, used by all animatable {@link GraphicsObject}s.
 * <p>
 * This used to be copy-pasted (identically) into every animatable class
 * (Point, Line, Rectangle, Oval, Polygon, Circle, Image, Text). Centralizing it
 * here means there is exactly one place to fix bugs or add new easing styles.
 */
public final class Easing {

    private Easing() {
        // Utility class; not instantiable.
    }

    /**
     * Computes the eased progress for a given linear progress {@code t} (0..1),
     * easing style, and easing direction.
     *
     * @param t               linear progress, expected in [0, 1]
     * @param style           the easing curve to apply
     * @param easingDirection IN, OUT, or INOUT
     * @return the eased progress, nominally in [0, 1] (some styles such as BACK
     *         or ELASTIC may briefly overshoot outside that range by design)
     */
    public static double apply(double t, EasingStyle style, EasingDirection easingDirection) {
        switch (easingDirection) {
            case OUT:
                // Reverse the easing by applying (1 - easing(1 - t))
                return 1 - apply(1 - t, style, EasingDirection.IN);
            case INOUT:
                // First half uses In, second half uses Out
                return t < 0.5
                    ? apply(t * 2, style, EasingDirection.IN) / 2
                    : 1 - apply((1 - t) * 2, style, EasingDirection.IN) / 2;
            case IN:
            default:
                switch (style) {
                    case LINEAR:
                        return t;
                    case SINE:
                        return 1 - Math.cos(t * Math.PI / 2);
                    case QUAD:
                        return t * t;
                    case CUBIC:
                        return t * t * t;
                    case QUART:
                        return t * t * t * t;
                    case QUINT:
                        return t * t * t * t * t;
                    case EXPONENTIAL:
                        return t == 0 ? 0 : Math.pow(2, 10 * (t - 1));
                    case CIRCULAR:
                        return 1 - Math.sqrt(1 - t * t);
                    case BACK:
                        double s = 1.70158; // Default overshoot amount for "back" easing
                        return t * t * ((s + 1) * t - s);
                    case ELASTIC:
                        if (t == 0 || t == 1) return t;
                        double p = 0.3; // Period of oscillation
                        return -Math.pow(2, 10 * (t - 1)) * Math.sin((t - 1.1) * (2 * Math.PI) / p);
                    case BOUNCE:
                        if (t > (1 - 1 / 2.75)) {
                            t = 1 - t;
                            return 1 - (7.5625 * t * t);
                        } else if (t > (1 - 2 / 2.75)) {
                            t = 1 - t - (1.5 / 2.75);
                            return 1 - (7.5625 * t * t + 0.75);
                        } else if (t > (1 - 2.5 / 2.75)) {
                            t = 1 - t - (2.25 / 2.75);
                            return 1 - (7.5625 * t * t + 0.9375);
                        } else {
                            t = 1 - t - (2.625 / 2.75);
                            return 1 - (7.5625 * t * t + 0.984375);
                        }
                    default:
                        return t; // Default to linear if the easing type is unknown
                }
        }
    }
}