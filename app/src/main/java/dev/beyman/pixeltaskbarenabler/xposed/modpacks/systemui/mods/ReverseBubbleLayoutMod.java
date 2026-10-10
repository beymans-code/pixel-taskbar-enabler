package dev.beyman.pixeltaskbarenabler.xposed.modpacks.systemui.mods;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import dev.beyman.pixeltaskbarenabler.xposed.utils.reflection.ReflectedClass;
import dev.beyman.pixeltaskbarenabler.xposed.utils.toolkit.Logger;
import de.robv.android.xposed.XposedHelpers;

/**
 * Reverses the classic (phone, portrait) bubble layout so it can be reached with one hand:
 * <ul>
 *     <li>The row of bubble icons is moved to the bottom of the screen.</li>
 *     <li>The expanded view grows from the top and ends right above the icons row.</li>
 *     <li>The "Manage" button is moved to the top of the expanded view and the pointer is hidden.</li>
 * </ul>
 * Landscape, large screen and bubble bar layouts are left untouched.
 * <p>
 * The currently selected bubble icon is also scaled up while the stack is expanded, so it is easy
 * to tell apart from the others.
 */
public class ReverseBubbleLayoutMod {
    private static final String TAG = "PTE-Bubble";
    private static final String POSITIONER = "com.android.wm.shell.bubbles.BubblePositioner";
    private static final String EXPANDED_VIEW = "com.android.wm.shell.bubbles.BubbleExpandedView";
    private static final String STACK_VIEW = "com.android.wm.shell.bubbles.BubbleStackView";

    /** Scale applied to the selected bubble icon while the stack is expanded. */
    private static final float SELECTED_SCALE = 1.18f;
    private static final long SCALE_ANIM_DURATION_MS = 180L;
    /** SystemUI's springs reset the scale during (un)expansion; re-check shortly after. */
    private static final long[] RECHECK_DELAYS_MS = {0L, 350L, 750L};

    private Context mContext;

    public ReverseBubbleLayoutMod(Context context) {
        mContext = context;
    }

    private static void log(String message) {
        Log.i(TAG, message);
        Logger.log(message);
    }

    // ---------------------------------------------------------------- positioner helpers

    /** True when the bubbles are shown in a horizontal row (phone in portrait, no bubble bar). */
    private static boolean isReversed(Object positioner) {
        try {
            if (XposedHelpers.getBooleanField(positioner, "mShowingInBubbleBar")) return false;
            return !(Boolean) XposedHelpers.callMethod(positioner, "showBubblesVertically");
        } catch (Throwable t) {
            return false;
        }
    }

    private static int intField(Object o, String name) {
        return XposedHelpers.getIntField(o, name);
    }

    /** Y (top) of the bubbles row when placed at the bottom, above the IME / navigation bar. */
    private static float bottomRowY(Object positioner) {
        Rect position = (Rect) XposedHelpers.getObjectField(positioner, "mPositionRect");
        int ime = (Integer) XposedHelpers.callMethod(positioner, "getImeHeight");
        return position.bottom - ime - intField(positioner, "mBubbleSize") - intField(positioner, "mExpandedViewPadding");
    }

    /** Y where the expanded view starts: the top of the usable area. */
    private static int expandedTopY(Object positioner) {
        Rect position = (Rect) XposedHelpers.getObjectField(positioner, "mPositionRect");
        return position.top + intField(positioner, "mExpandedViewPadding");
    }

    /** Maximum height the expanded view (task view) may use so it stays above the bubbles row. */
    private static int expandedMaxHeight(Object positioner, boolean overflow) {
        int padding = intField(positioner, "mExpandedViewPadding");
        int reserved = overflow ? padding : intField(positioner, "mManageButtonHeightIncludingMargins");
        float available = (bottomRowY(positioner) - padding) - expandedTopY(positioner)
                - reserved - intField(positioner, "mPointerMargin");
        return Math.max(0, (int) available);
    }

    // ---------------------------------------------------------------- expanded view helpers

    private static boolean isReversedForView(ViewGroup expandedView) {
        try {
            Object positioner = XposedHelpers.getObjectField(expandedView, "mPositioner");
            if (positioner != null) return isReversed(positioner);
        } catch (Throwable ignored) {
        }
        // Positioner not attached yet: fall back to phone + portrait
        Configuration config = expandedView.getResources().getConfiguration();
        return config.orientation == Configuration.ORIENTATION_PORTRAIT && config.smallestScreenWidthDp < 600;
    }

    private static void applyExpandedViewLayout(ViewGroup expandedView) {
        try {
            View manage = (View) XposedHelpers.getObjectField(expandedView, "mManageButton");
            boolean reversed = isReversedForView(expandedView);

            if (manage != null && manage.getParent() == expandedView) {
                int wanted = reversed ? 0 : expandedView.getChildCount() - 1;
                if (expandedView.indexOfChild(manage) != wanted) {
                    expandedView.removeView(manage);
                    expandedView.addView(manage, reversed ? 0 : -1);
                }
            }

            if (reversed) {
                View pointer = (View) XposedHelpers.getObjectField(expandedView, "mPointerView");
                if (pointer != null) pointer.setVisibility(View.GONE);
            }
        } catch (Throwable t) {
            log("applyExpandedViewLayout failed: " + t);
        }
    }

    // ---------------------------------------------------------------- selected icon scale

    private static void applySelectedScale(ViewGroup stack) {
        try {
            ViewGroup container = (ViewGroup) XposedHelpers.getObjectField(stack, "mBubbleContainer");
            boolean expanded = XposedHelpers.getBooleanField(stack, "mIsExpanded");
            Object selected = XposedHelpers.getObjectField(stack, "mExpandedBubble");
            String selectedKey = (expanded && selected != null)
                    ? (String) XposedHelpers.callMethod(selected, "getKey") : null;
            Object dragged = null;
            try {
                Object magnetized = XposedHelpers.getObjectField(stack, "mMagnetizedObject");
                if (magnetized != null) dragged = XposedHelpers.getObjectField(magnetized, "underlyingObject");
            } catch (Throwable ignored) {
            }

            for (int i = 0; i < container.getChildCount(); i++) {
                View icon = container.getChildAt(i);
                if (icon == dragged) continue; // don't fight the drag-to-dismiss animation
                Object bubble = XposedHelpers.getObjectField(icon, "mBubble");
                String key = bubble != null ? (String) XposedHelpers.callMethod(bubble, "getKey") : null;
                float target = (selectedKey != null && selectedKey.equals(key)) ? SELECTED_SCALE : 1f;
                if (Math.abs(icon.getScaleX() - target) > 0.01f) {
                    icon.animate().scaleX(target).scaleY(target)
                            .setDuration(SCALE_ANIM_DURATION_MS).start();
                }
            }
        } catch (Throwable t) {
            log("applySelectedScale failed: " + t);
        }
    }

    private static void scheduleSelectedScale(ViewGroup stack) {
        for (long delay : RECHECK_DELAYS_MS) {
            if (delay == 0L) applySelectedScale(stack);
            else stack.postDelayed(() -> applySelectedScale(stack), delay);
        }
    }

    // ---------------------------------------------------------------- hooks

    public void applyHooks() throws Throwable {
        ReflectedClass positionerClass = ReflectedClass.ofIfPossible(POSITIONER);
        ReflectedClass expandedViewClass = ReflectedClass.ofIfPossible(EXPANDED_VIEW);
        log("ReverseBubbleLayoutMod: positioner=" + (positionerClass.getClazz() != null)
                + " expandedView=" + (expandedViewClass.getClazz() != null));

        // Bubbles row -> bottom of the screen
        positionerClass.after("getExpandedBubbleXY").run(param -> {
            try {
                Object positioner = param.thisObject;
                PointF point = param.getResult();
                if (point != null && isReversed(positioner)) {
                    point.y = bottomRowY(positioner);
                }
            } catch (Throwable t) {
                log("getExpandedBubbleXY hook failed: " + t);
            }
        });

        // Expanded view -> starts at the top
        positionerClass.after("getExpandedViewYTopAligned").run(param -> {
            try {
                if (isReversed(param.thisObject)) param.setResult(expandedTopY(param.thisObject));
            } catch (Throwable t) {
                log("getExpandedViewYTopAligned hook failed: " + t);
            }
        });

        positionerClass.after("getExpandedViewY").run(param -> {
            try {
                Object positioner = param.thisObject;
                if (!isReversed(positioner)) return;
                Object provider = param.args[0];
                boolean overflow = provider == null
                        || "Overflow".equals(XposedHelpers.callMethod(provider, "getKey"));
                if (overflow) {
                    // "Recent bubbles" window: stick it to the icons row so the thumb reaches it
                    float height = (Float) XposedHelpers.callMethod(positioner, "getExpandedViewHeight", provider);
                    int max = expandedMaxHeight(positioner, true);
                    float used = height == -1f ? max : Math.min(height, max);
                    float y = bottomRowY(positioner) - intField(positioner, "mExpandedViewPadding")
                            - intField(positioner, "mPointerMargin") - used;
                    param.setResult(Math.max(y, expandedTopY(positioner)));
                } else {
                    param.setResult((float) expandedTopY(positioner));
                }
            } catch (Throwable t) {
                log("getExpandedViewY hook failed: " + t);
            }
        });

        // ...and ends right above the bubbles row
        positionerClass.after("getMaxExpandedViewHeight").run(param -> {
            try {
                if (isReversed(param.thisObject)) {
                    param.setResult(expandedMaxHeight(param.thisObject, (Boolean) param.args[0]));
                }
            } catch (Throwable t) {
                log("getMaxExpandedViewHeight hook failed: " + t);
            }
        });

        positionerClass.after("getTaskViewRestBounds").run(param -> {
            try {
                Object positioner = param.thisObject;
                if (isReversed(positioner)) {
                    Rect rect = (Rect) param.args[0];
                    int top = expandedTopY(positioner);
                    rect.set(rect.left, top, rect.right, top + expandedMaxHeight(positioner, false));
                }
            } catch (Throwable t) {
                log("getTaskViewRestBounds hook failed: " + t);
            }
        });

        // Manage button -> top of the expanded view, pointer hidden
        expandedViewClass.after("onFinishInflate").run(param -> applyExpandedViewLayout(param.getThisObject()));
        expandedViewClass.after("initialize").run(param -> applyExpandedViewLayout(param.getThisObject()));
        expandedViewClass.after("updateDimensions").run(param -> applyExpandedViewLayout(param.getThisObject()));
        expandedViewClass.after("updateManageButtonIfExists").run(param -> applyExpandedViewLayout(param.getThisObject()));
        expandedViewClass.after("updatePointerViewIfExists").run(param -> applyExpandedViewLayout(param.getThisObject()));
        expandedViewClass.after("lambda$setPointerPosition$0").run(param -> applyExpandedViewLayout(param.getThisObject()));

        // Selected bubble icon -> bigger than the rest
        ReflectedClass stackViewClass = ReflectedClass.ofIfPossible(STACK_VIEW);
        for (String method : new String[]{"setSelectedBubble", "setExpanded", "updateExpandedView",
                "updateBubbleOrderInternal", "updateBubbleShadows"}) {
            stackViewClass.after(method).run(param -> scheduleSelectedScale(param.getThisObject()));
        }

        // Initial bubble position -> bottom right
        positionerClass.after("getStartPosition").run(param -> {
            try {
                Object positioner = param.thisObject;
                PointF point = param.getResult();
                if (point != null && isReversed(positioner)) {
                    RectF allowable = (RectF) XposedHelpers.callMethod(positioner, "getAllowableStackPositionRegion", 1);
                    point.x = allowable.right;
                    point.y = allowable.bottom;
                }
            } catch (Throwable t) {
                log("getStartPosition hook failed: " + t);
            }
        });
    }
}
