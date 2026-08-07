package dev.beyman.pixeltaskbarenabler.xposed.modpacks.launcher.mods;

import android.content.Context;

import dev.beyman.pixeltaskbarenabler.xposed.modpacks.launcher.TaskbarActivator;
import dev.beyman.pixeltaskbarenabler.xposed.modpacks.launcher.TaskbarSettings;

/**
 * Clase base para todos los módulos de modificación del Launcher.
 * Proporciona acceso a las referencias comunes necesarias (Context, TaskbarActivator, 
 * TaskbarSettings) y define el contrato {@link #applyHooks()} que cada submódulo debe implementar.
 */
public abstract class BaseLauncherMod {

    protected final Context mContext;
    protected final TaskbarActivator mActivator;
    protected final TaskbarSettings mSettings;

    public BaseLauncherMod(TaskbarActivator activator, Context context, TaskbarSettings settings) {
        this.mActivator = activator;
        this.mContext = context;
        this.mSettings = settings;
    }

    public abstract void applyHooks() throws Throwable;
}
