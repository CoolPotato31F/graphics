package graphics;

/**
 * String constants for the typeable key names returned by
 * {@link GraphWin#getKey()}, {@link GraphWin#checkKeys()}, and accepted by
 * {@link GraphWin#isKeyPressed(String)}.
 * <p>
 * Those methods all report keys using {@link java.awt.event.KeyEvent#getKeyText(int)},
 * which returns a human-readable, typeable name (e.g. {@code "Escape"},
 * {@code "Enter"}, {@code "A"}) rather than a raw, sometimes non-printable or
 * symbolic {@code char} (e.g. the escape key's char value used to show up as
 * the glyph {@code ⎋} instead of anything you could type or compare against
 * a literal). {@code Key} just gives names to those strings so callers don't
 * have to hardcode or guess them:
 * <pre>{@code
 * String pressed = window.getKey();
 * if (pressed.equals(Key.ESCAPE)) {
 *     // ...
 * }
 *
 * if (window.isKeyPressed(Key.SPACE)) {
 *     // ...
 * }
 * }</pre>
 * Not every possible key is listed here (keyboards vary quite a bit), but
 * every value listed is exactly the string {@code KeyEvent.getKeyText(...)}
 * produces for that key on a standard US keyboard layout, so it is always
 * safe to compare directly.
 */
public final class Key {

    private Key() {
        // Utility class; not instantiable.
    }

    // ----- Letters -----
    public static final String A = "A";
    public static final String B = "B";
    public static final String C = "C";
    public static final String D = "D";
    public static final String E = "E";
    public static final String F = "F";
    public static final String G = "G";
    public static final String H = "H";
    public static final String I = "I";
    public static final String J = "J";
    public static final String K = "K";
    public static final String L = "L";
    public static final String M = "M";
    public static final String N = "N";
    public static final String O = "O";
    public static final String P = "P";
    public static final String Q = "Q";
    public static final String R = "R";
    public static final String S = "S";
    public static final String T = "T";
    public static final String U = "U";
    public static final String V = "V";
    public static final String W = "W";
    public static final String X = "X";
    public static final String Y = "Y";
    public static final String Z = "Z";

    // ----- Digits (main row) -----
    public static final String DIGIT_0 = "0";
    public static final String DIGIT_1 = "1";
    public static final String DIGIT_2 = "2";
    public static final String DIGIT_3 = "3";
    public static final String DIGIT_4 = "4";
    public static final String DIGIT_5 = "5";
    public static final String DIGIT_6 = "6";
    public static final String DIGIT_7 = "7";
    public static final String DIGIT_8 = "8";
    public static final String DIGIT_9 = "9";

    // ----- Numpad -----
    public static final String NUMPAD_0 = "NumPad-0";
    public static final String NUMPAD_1 = "NumPad-1";
    public static final String NUMPAD_2 = "NumPad-2";
    public static final String NUMPAD_3 = "NumPad-3";
    public static final String NUMPAD_4 = "NumPad-4";
    public static final String NUMPAD_5 = "NumPad-5";
    public static final String NUMPAD_6 = "NumPad-6";
    public static final String NUMPAD_7 = "NumPad-7";
    public static final String NUMPAD_8 = "NumPad-8";
    public static final String NUMPAD_9 = "NumPad-9";

    // ----- Whitespace / editing -----
    public static final String SPACE = "Space";
    public static final String ENTER = "Enter";
    public static final String TAB = "Tab";
    public static final String BACKSPACE = "Backspace";
    public static final String DELETE = "Delete";
    public static final String ESCAPE = "Escape";
    public static final String INSERT = "Insert";

    // ----- Navigation -----
    public static final String UP = "Up";
    public static final String DOWN = "Down";
    public static final String LEFT = "Left";
    public static final String RIGHT = "Right";
    public static final String HOME = "Home";
    public static final String END = "End";
    public static final String PAGE_UP = "Page Up";
    public static final String PAGE_DOWN = "Page Down";

    // ----- Modifiers -----
    public static final String SHIFT = "Shift";
    public static final String CONTROL = "Ctrl";
    public static final String ALT = "Alt";
    public static final String ALT_GRAPH = "Alt Graph";
    public static final String META = "Meta";
    public static final String CAPS_LOCK = "Caps Lock";

    // ----- Function keys -----
    public static final String F1 = "F1";
    public static final String F2 = "F2";
    public static final String F3 = "F3";
    public static final String F4 = "F4";
    public static final String F5 = "F5";
    public static final String F6 = "F6";
    public static final String F7 = "F7";
    public static final String F8 = "F8";
    public static final String F9 = "F9";
    public static final String F10 = "F10";
    public static final String F11 = "F11";
    public static final String F12 = "F12";

    // ----- Punctuation (US layout; use GraphWin.isKeyPressed(int) with a
    //       KeyEvent.VK_* constant instead if these vary on your platform) -----
    public static final String MINUS = "Minus";
    public static final String EQUALS = "Equals";
    public static final String COMMA = "Comma";
    public static final String PERIOD = "Period";
    public static final String SLASH = "Slash";
    public static final String BACK_SLASH = "Back Slash";
    public static final String SEMICOLON = "Semicolon";
    public static final String QUOTE = "Quote";
    public static final String OPEN_BRACKET = "Open Bracket";
    public static final String CLOSE_BRACKET = "Close Bracket";
    public static final String BACK_QUOTE = "Back Quote";
}