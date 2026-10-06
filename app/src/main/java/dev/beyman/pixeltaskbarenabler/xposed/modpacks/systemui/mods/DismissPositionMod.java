package dev.beyman.pixeltaskbarenabler.xposed.modpacks.systemui.mods;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import dev.beyman.pixeltaskbarenabler.xposed.utils.reflection.ReflectedClass;
import dev.beyman.pixeltaskbarenabler.xposed.utils.toolkit.Logger;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import android.util.Log;

public class DismissPositionMod {
    private static final String TAG = "PTE-Bubble";

    public enum DismissPosition {
        BOTTOM_MIDDLE,
        TOP_MIDDLE,
        LEFT_MIDDLE,
        RIGHT_MIDDLE,
        CENTER
    }

    private Context mContext;
    // Configurable position
    private DismissPosition mPosition = DismissPosition.RIGHT_MIDDLE; // User requested parametrizable, right-middle as default

    public DismissPositionMod(Context context) {
        this.mContext = context;
        if (dev.beyman.pixeltaskbarenabler.xposed.XPrefs.Xprefs != null) {
            String positionStr = dev.beyman.pixeltaskbarenabler.xposed.XPrefs.Xprefs.getString("dismiss_position", "BOTTOM_MIDDLE");
            try {
                mPosition = DismissPosition.valueOf(positionStr);
            } catch (Exception e) {
                mPosition = DismissPosition.BOTTOM_MIDDLE;
            }
        }
    }

    private static void log(String message) {
        Log.i(TAG, message);
        Logger.log(message);
    }

    private static void logE(String message) {
        Log.e(TAG, message);
        Logger.log(message);
    }

    private static void logE(String message, Throwable t) {
        Log.e(TAG, message, t);
        Logger.log(message, t);
    }

    public void applyHooks() {
        ReflectedClass dismissViewClass = ReflectedClass.ofIfPossible("com.android.wm.shell.shared.bubbles.DismissView");
        if (dismissViewClass.getClazz() == null) {
            logE("DismissPositionMod: DismissView class not found");
            return;
        }

        try {
            // Hook setup method
            XposedHelpers.findAndHookMethod(
                    dismissViewClass.getClazz(),
                    "setup",
                    "com.android.wm.shell.shared.bubbles.DismissView$Config",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            FrameLayout dismissView = (FrameLayout) param.thisObject;
                            
                            View circle = (View) XposedHelpers.getObjectField(dismissView, "circle");
                            GradientDrawable gradient = (GradientDrawable) XposedHelpers.getObjectField(dismissView, "gradientDrawable");
                            
                            FrameLayout.LayoutParams dvParams = (FrameLayout.LayoutParams) dismissView.getLayoutParams();
                            FrameLayout.LayoutParams circleParams = (FrameLayout.LayoutParams) circle.getLayoutParams();
                            
                            int gradientHeight = dismissView.getResources().getDimensionPixelSize(
                                    XposedHelpers.getIntField(param.args[0], "floatingGradientHeightResId"));
                                    
                            switch (mPosition) {
                                case TOP_MIDDLE:
                                    dvParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
                                    dvParams.height = gradientHeight;
                                    dvParams.gravity = Gravity.TOP;
                                    
                                    circleParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                                    if (gradient != null) gradient.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
                                    break;
                                    
                                case LEFT_MIDDLE:
                                    dvParams.width = gradientHeight;
                                    dvParams.height = FrameLayout.LayoutParams.MATCH_PARENT;
                                    dvParams.gravity = Gravity.LEFT;
                                    
                                    circleParams.gravity = Gravity.LEFT | Gravity.CENTER_VERTICAL;
                                    if (gradient != null) gradient.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
                                    break;
                                    
                                case RIGHT_MIDDLE:
                                    dvParams.width = gradientHeight;
                                    dvParams.height = FrameLayout.LayoutParams.MATCH_PARENT;
                                    dvParams.gravity = Gravity.RIGHT;
                                    
                                    circleParams.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
                                    if (gradient != null) gradient.setOrientation(GradientDrawable.Orientation.RIGHT_LEFT);
                                    break;
                                    
                                case CENTER:
                                    dvParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
                                    dvParams.height = FrameLayout.LayoutParams.MATCH_PARENT;
                                    dvParams.gravity = Gravity.CENTER;
                                    
                                    circleParams.gravity = Gravity.CENTER;
                                    if (gradient != null) dismissView.setBackground(null);
                                    break;
                                    
                                case BOTTOM_MIDDLE:
                                default:
                                    dvParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
                                    dvParams.height = gradientHeight;
                                    dvParams.gravity = Gravity.BOTTOM;
                                    
                                    circleParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
                                    if (gradient != null) gradient.setOrientation(GradientDrawable.Orientation.BOTTOM_TOP);
                                    break;
                            }
                            
                            dismissView.setLayoutParams(dvParams);
                            circle.setLayoutParams(circleParams);
                            
                            // Remove translationY applied by default
                            if (mPosition != DismissPosition.BOTTOM_MIDDLE) {
                                circle.setTranslationY(0f);
                                circle.setTranslationX(0f);
                            }
                            
                            log("DismissPositionMod: setup hooked and applied position " + mPosition);
                        }
                    }
            );

            // Hook updatePadding to avoid overriding our custom padding
            XposedHelpers.findAndHookMethod(
                    dismissViewClass.getClazz(),
                    "updatePadding",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            if (mPosition != DismissPosition.BOTTOM_MIDDLE) {
                                FrameLayout dismissView = (FrameLayout) param.thisObject;
                                dismissView.setPadding(0, 0, 0, 0); // Clear padding so it doesn't offset our position
                                param.setResult(null); // skip original
                            }
                        }
                    }
            );
            
            // Hook updateResources to handle layout changes properly
            XposedHelpers.findAndHookMethod(
                    dismissViewClass.getClazz(),
                    "updateResources",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            if (mPosition != DismissPosition.BOTTOM_MIDDLE) {
                                FrameLayout dismissView = (FrameLayout) param.thisObject;
                                Object config = XposedHelpers.getObjectField(dismissView, "config");
                                if (config == null) return;
                                
                                int gradientHeight = dismissView.getResources().getDimensionPixelSize(
                                        XposedHelpers.getIntField(config, "floatingGradientHeightResId"));
                                        
                                FrameLayout.LayoutParams dvParams = (FrameLayout.LayoutParams) dismissView.getLayoutParams();
                                if (mPosition == DismissPosition.LEFT_MIDDLE || mPosition == DismissPosition.RIGHT_MIDDLE) {
                                    dvParams.width = gradientHeight;
                                    dvParams.height = FrameLayout.LayoutParams.MATCH_PARENT;
                                } else if (mPosition == DismissPosition.TOP_MIDDLE) {
                                    dvParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
                                    dvParams.height = gradientHeight;
                                } else if (mPosition == DismissPosition.CENTER) {
                                    dvParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
                                    dvParams.height = FrameLayout.LayoutParams.MATCH_PARENT;
                                }
                                dismissView.setLayoutParams(dvParams);
                            }
                        }
                    }
            );

            log("DismissPositionMod successfully applied.");
        } catch (Throwable t) {
            logE("Error hooking DismissView", t);
        }
    }
}
