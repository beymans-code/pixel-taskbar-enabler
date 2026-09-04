package dev.beyman.pixeltaskbarenabler.xposed.modpacks.launcher;

import static dev.beyman.pixeltaskbarenabler.xposed.XPrefs.Xprefs;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import dev.beyman.pixeltaskbarenabler.xposed.utils.SystemUtils;

/**
 * Clase encargada de gestionar y centralizar el estado de las configuraciones del módulo.
 * Lee los valores de las preferencias compartidas (XPrefs) y proporciona variables
 * tipadas para que el resto de los módulos (mods) las consuman fácilmente.
 */
public class TaskbarSettings {

    public static final int TASKBAR_DEFAULT = 0;
    public static final int TASKBAR_ON = 1;
    public static final int TASKBAR_OFF = 2;

    public int taskbarMode = TASKBAR_DEFAULT;
    public boolean enableRecentsGrid = false;
    public boolean mobileRecents = false;
    public float taskbarIconScale = 1.0f;
    public float taskbarScale = 1.0f;
    public float gridHeaderScale = 0.70f;
    public int taskbarIconCount = 4;

    public TaskbarSettings() {
        // Initialize defaults or load initial state
        update();
    }

    /**
     * Extrae todos los valores actualizados desde XPrefs y los formatea.
     * Si la clave que provocó el evento está relacionada a los colores, dispara el reinicio
     * o la palanca de colores oscuros para refrescar UI del sistema.
     * @param key Array de claves modificadas (opcional).
     */
    public void update(String... key) {
        taskbarMode = Integer.parseInt(Xprefs.getString("taskBarMode", String.valueOf(TASKBAR_DEFAULT)));
        enableRecentsGrid = Xprefs.getBoolean("enable_recents_grid", false);
        taskbarIconScale = Xprefs.getInt("taskbar_icon_scale", 100) / 100f;
        taskbarScale = Xprefs.getInt("taskbar_scale", 100) / 100f;
        gridHeaderScale = Xprefs.getInt("grid_header_scale", 70) / 100f;
        taskbarIconCount = Math.min(Xprefs.getInt("taskbar_icon_count", 4), 5);
        mobileRecents = Xprefs.getBoolean("mobile_recents", false);
    }
}
