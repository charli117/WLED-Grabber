package com.abrenoch.wledgrabber.tv.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.abrenoch.wledgrabber.tv.R;

/**
 * One focusable settings row for the TV layout editor.
 *
 * <p>Left/Right adjust the value in place, OK opens the exact-value path, and
 * number rows accelerate while the key is held. The activity owns validation;
 * this view only reports intent.</p>
 */
public final class LayoutValueRow extends LinearLayout {
    public enum Kind {
        NUMBER,
        CHOICE,
        TOGGLE,
        ACTION
    }

    public interface Listener {
        /** @param delta direction and magnitude requested by the key repeat. */
        void onAdjust(LayoutValueRow row, int delta);

        void onSelect(LayoutValueRow row);
    }

    private static final int REPEAT_MEDIUM = 10;
    private static final int REPEAT_FAST = 30;

    private final TextView titleView;
    private final TextView valueView;
    private Kind kind;
    private String fieldKey;
    private Listener listener;
    private android.view.View nextDownView;

    /** Used by Android when the row is inflated from XML. */
    public LayoutValueRow(Context context) {
        this(context, null);
    }

    public LayoutValueRow(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        kind = Kind.NUMBER;
        fieldKey = "";
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setClickable(true);
        setMinimumHeight(dp(52));
        setPadding(dp(16), dp(10), dp(16), dp(10));
        setBackgroundResource(R.drawable.layout_editor_row_background);
        LayoutInflater.from(context).inflate(R.layout.layout_editor_row, this, true);
        titleView = findViewById(R.id.layout_row_title);
        valueView = findViewById(R.id.layout_row_value);
    }

    private LayoutValueRow(Context context, Kind kind, String fieldKey, CharSequence title,
                           Listener listener) {
        this(context);
        this.kind = kind;
        this.fieldKey = fieldKey;
        titleView.setText(title);
        this.listener = listener;
        updateContentDescription();
    }

    public static LayoutValueRow number(Context context, String fieldKey, CharSequence title,
                                        Listener listener) {
        return new LayoutValueRow(context, Kind.NUMBER, fieldKey, title, listener);
    }

    public static LayoutValueRow choice(Context context, String fieldKey, CharSequence title,
                                        Listener listener) {
        return new LayoutValueRow(context, Kind.CHOICE, fieldKey, title, listener);
    }

    public static LayoutValueRow toggle(Context context, String fieldKey, CharSequence title,
                                        Listener listener) {
        return new LayoutValueRow(context, Kind.TOGGLE, fieldKey, title, listener);
    }

    public static LayoutValueRow action(Context context, String actionKey, CharSequence title,
                                        Listener listener) {
        return new LayoutValueRow(context, Kind.ACTION, actionKey, title, listener);
    }

    public Kind kind() {
        return kind;
    }

    public String fieldKey() {
        return fieldKey;
    }

    public void setTitle(CharSequence title) {
        titleView.setText(title);
        updateContentDescription();
    }

    public CharSequence title() {
        return titleView.getText();
    }

    public void setDisplayValue(CharSequence value) {
        valueView.setText(value);
        updateContentDescription();
    }

    public CharSequence displayValue() {
        return valueView.getText();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /**
     * A row inside a ScrollView can lose the Down key to the scroll container
     * before focus search runs; the final row points straight at the footer.
     */
    public void setNextDownView(android.view.View view) {
        this.nextDownView = view;
    }

    private void updateContentDescription() {
        if (kind == Kind.ACTION) {
            CharSequence value = valueView.getText();
            setContentDescription(value == null || value.length() == 0
                    ? titleView.getText()
                    : titleView.getText() + ", " + value);
        } else {
            setContentDescription(titleView.getText() + ", " + valueView.getText());
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (isHorizontalKey(keyCode)) {
            if (kind != Kind.ACTION && listener != null) {
                if (kind != Kind.NUMBER && event.getRepeatCount() > 0) {
                    // A held key must not flip a toggle/choice on every repeat;
                    // only the initial press changes those values.
                    return true;
                }
                int direction = keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1;
                int magnitude = kind == Kind.NUMBER ? stepForRepeat(event.getRepeatCount()) : 1;
                listener.onAdjust(this, direction * magnitude);
            }
            // Single-column list: keep focus on the row instead of letting the
            // framework search for a neighbour.
            return true;
        }
        if (isConfirmKey(keyCode)) {
            performClick();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && nextDownView != null) {
            nextDownView.requestFocus();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (isHorizontalKey(keyCode) || isConfirmKey(keyCode)) {
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean performClick() {
        super.performClick();
        if (listener != null) {
            listener.onSelect(this);
        }
        return true;
    }

    private static boolean isHorizontalKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT;
    }

    private static boolean isConfirmKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    private static int stepForRepeat(int repeatCount) {
        if (repeatCount >= REPEAT_FAST) {
            return 10;
        }
        if (repeatCount >= REPEAT_MEDIUM) {
            return 5;
        }
        return 1;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
