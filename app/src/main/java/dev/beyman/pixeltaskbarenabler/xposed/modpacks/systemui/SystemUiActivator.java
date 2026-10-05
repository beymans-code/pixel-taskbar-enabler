package dev.beyman.pixeltaskbarenabler.xposed.modpacks.systemui;

import android.annotation.SuppressLint;
import android.content.Context;

import dev.beyman.pixeltaskbarenabler.xposed.XposedModPack;
import dev.beyman.pixeltaskbarenabler.xposed.annotations.SystemUiModPack;
import dev.beyman.pixeltaskbarenabler.xposed.modpacks.systemui.mods.ReverseBubbleLayoutMod;
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
        ReverseBubbleLayoutMod reverseBubbleLayoutMod = new ReverseBubbleLayoutMod(mContext);
        reverseBubbleLayoutMod.applyHooks();
    }
}
