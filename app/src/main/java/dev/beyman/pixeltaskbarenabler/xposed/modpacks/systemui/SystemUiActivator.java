package dev.beyman.pixeltaskbarenabler.xposed.modpacks.systemui;

import android.annotation.SuppressLint;
import android.content.Context;

import dev.beyman.pixeltaskbarenabler.xposed.XposedModPack;
import dev.beyman.pixeltaskbarenabler.xposed.annotations.SystemUiModPack;
import dev.beyman.pixeltaskbarenabler.xposed.modpacks.systemui.mods.ReverseBubbleLayoutMod;
import dev.beyman.pixeltaskbarenabler.xposed.modpacks.systemui.mods.DismissPositionMod;
import io.github.libxposed.api.XposedModuleInterface;

@SystemUiModPack
public class SystemUiActivator extends XposedModPack {

    public SystemUiActivator(Context context) {
        super(context);
    }

    @Override
    public void onPreferenceUpdated(String... Key) {
        // Handle preferences if needed
    }

    @SuppressLint("DiscouragedApi")
    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageReadyParam PRParam) throws Throwable {
        boolean enableReverseBubble = false;
        if (dev.beyman.pixeltaskbarenabler.xposed.XPrefs.Xprefs != null) {
            enableReverseBubble = dev.beyman.pixeltaskbarenabler.xposed.XPrefs.Xprefs.getBoolean("enable_reverse_bubble", false);
        }

        if (enableReverseBubble) {
            ReverseBubbleLayoutMod reverseBubbleLayoutMod = new ReverseBubbleLayoutMod(mContext);
            reverseBubbleLayoutMod.applyHooks();

            DismissPositionMod dismissPositionMod = new DismissPositionMod(mContext);
            dismissPositionMod.applyHooks();
        }
    }
}
