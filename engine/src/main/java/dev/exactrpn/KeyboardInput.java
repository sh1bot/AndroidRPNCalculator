// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

/** Android keycode mapping kept platform independent for regression checks. */
public final class KeyboardInput {
    private KeyboardInput() {}
    public static String command(int code, int unicode, boolean ctrl, boolean alt, boolean meta) {
        if (alt || meta) return null;
        if (ctrl) {
            switch(code) { // Android KEYCODE_Z / D / S / L.
                case 122: return "HOME";
                case 123: return "END";
                case 50: return "PASTE";
                case 54: return "UNDO";
                case 32: return "DUP";
                case 47: return "SWAP";
                case 40: return "CLEAR";
                default: return null;
            }
        }
        switch(code) {
            case 21: return "LEFT";
            case 22: return "RIGHT";
            case 122: return "HOME";
            case 123: return "END";
            case 66: case 160: case 62: return "ENTER"; // Enter, numpad Enter, Space.
            case 67: return "BACK";
            case 112: return "DELETE"; // Forward Delete while editing; Drop in stack mode.
            case 111: return "ESCAPE";
            case 139: return "±"; // F9.
            case 154: return "÷";
            case 155: return "×";
            case 156: return "−";
            case 157: return "+";
            case 158: return ".";
        }
        if (code >= 144 && code <= 153) return Integer.toString(code - 144);
        if (Character.digit(unicode,10)>=0) return Integer.toString(Character.digit(unicode,10));
        switch(unicode) {
            case '.': return ".";
            case '+': return "+";
            case '-': case '−': return "−";
            case '*': case '×': return "×";
            case '/': case '÷': return "÷";
            case '^': return "xʸ";
            case 'r': return "√";
            case 'i': return "1/x";
            case 's': return "sin";
            case 'c': return "cos";
            case 't': return "tan";
            case 'l': return "ln";
            case 'p': return "π";
            case 'e': return "e";
            case 'E': return "eˣ";
            default: return null;
        }
    }
}



