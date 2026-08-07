package dev.beyman.pixeltaskbarenabler.xposed.modpacks.launcher.mods;

import android.content.Context;

import dev.beyman.pixeltaskbarenabler.xposed.modpacks.launcher.TaskbarActivator;
import dev.beyman.pixeltaskbarenabler.xposed.modpacks.launcher.TaskbarSettings;
import dev.beyman.pixeltaskbarenabler.xposed.utils.reflection.ReflectedClass;

import static de.robv.android.xposed.XposedHelpers.getObjectField;

/**
 * Submódulo encargado de inyectar y forzar la habilitación de la Barra de Tareas (Taskbar).
 * Contiene los hooks que modifican las propiedades del dispositivo (ej. hacerse pasar por Tablet 
 * o Pantalla Grande) y aplica soluciones (workarounds) para visibilidad en la pantalla de inicio.
 */
public class TaskbarMod extends BaseLauncherMod {

    public TaskbarMod(TaskbarActivator activator, Context context, TaskbarSettings settings) {
        super(activator, context, settings);
    }

    /**
     * Aplica los hooks necesarios para forzar la inicialización y despliegue del Taskbar.
     */
    @Override
    public void applyHooks() throws Throwable {
        ReflectedClass FlagsClass = ReflectedClass.ofIfPossible("com.android.launcher3.Flags");
        ReflectedClass DisplayControllerInfoClass = ReflectedClass.ofIfPossible("com.android.launcher3.display.LauncherDisplayInfo");
        ReflectedClass TaskbarConfigurationClass = ReflectedClass.ofIfPossible("com.android.launcher3.deviceprofile.TaskbarConfiguration");
        ReflectedClass DevicePropertiesClass = ReflectedClass.ofIfPossible("com.android.launcher3.deviceprofile.DeviceProperties");
        ReflectedClass TaskbarOverlayDragLayerClass = ReflectedClass.ofIfPossible("com.android.launcher3.taskbar.overlay.TaskbarOverlayDragLayer");
        ReflectedClass KeyboardQuickSwitchControllerClass = ReflectedClass.ofIfPossible("com.android.launcher3.taskbar.KeyboardQuickSwitchController");
        ReflectedClass StateControllerClass = ReflectedClass.ofIfPossible("com.android.launcher3.taskbar.TaskbarLauncherStateController");
        ReflectedClass QuickSwitchStateClass = ReflectedClass.ofIfPossible("com.android.launcher3.uioverrides.states.QuickSwitchState");
        ReflectedClass TaskbarUiControllerClass = ReflectedClass.ofIfPossible("com.android.launcher3.taskbar.FallbackTaskbarUIController");
        ReflectedClass TaskbarProfileClass = ReflectedClass.ofIfPossible("com.android.launcher3.deviceprofile.TaskbarProfile");

        // Activa la *flag* nativa de Android 15 para forzar la barra de tareas en teléfonos.
        if (FlagsClass != null) {
            FlagsClass.before("enableTaskbarOnPhones").run(param -> {
                if (mSettings.taskbarMode == TaskbarSettings.TASKBAR_ON) param.setResult(true);
            });
        }

        // Modifica la información del controlador de pantalla (DisplayController)
        // para decirle al sistema operativo que este dispositivo es una tablet.
        if (DisplayControllerInfoClass != null) {
            DisplayControllerInfoClass
                    .before("isTablet")
                    .run(param -> {
                        if (mSettings.taskbarMode == TaskbarSettings.TASKBAR_DEFAULT) return;
                        param.setResult(mSettings.taskbarMode == TaskbarSettings.TASKBAR_ON);
                    });
        }

        // Activa la bandera interna "isTaskbarPresent" dentro de la configuración
        // específica del Taskbar en el perfil del dispositivo (DeviceProfile).
        if (TaskbarConfigurationClass != null) {
            TaskbarConfigurationClass
                    .afterConstruction()
                    .run(param -> {
                        if (mSettings.taskbarMode == TaskbarSettings.TASKBAR_ON) {
                            try {
                                de.robv.android.xposed.XposedHelpers.setBooleanField(param.thisObject, "isTaskbarPresent", true);
                            } catch (Throwable ignored) {
                            }
                        }
                    });
        }

        // Intercepta las propiedades del dispositivo recién creado (DeviceProperties) 
        // para forzar las banderas: isPhone=false, isTablet=true, isLargeScreen=true y isTaskbarPresent=true.
        if (DevicePropertiesClass != null) {
            DevicePropertiesClass
                    .afterConstruction()
                    .run(param -> {
                        if (mSettings.taskbarMode == TaskbarSettings.TASKBAR_ON) {
                            try {
                                de.robv.android.xposed.XposedHelpers.setBooleanField(param.thisObject, "isPhone", false);
                            } catch (Throwable ignored) {
                            }
                            try {
                                de.robv.android.xposed.XposedHelpers.setBooleanField(param.thisObject, "isTablet", true);
                            } catch (Throwable ignored) {
                            } // For Android 15 and below
                            try {
                                de.robv.android.xposed.XposedHelpers.setBooleanField(param.thisObject, "isLargeScreen", true);
                            } catch (Throwable ignored) {
                            } // For Android 17+
                            try {
                                de.robv.android.xposed.XposedHelpers.setBooleanField(param.thisObject, "isTaskbarPresent", true);
                            } catch (Throwable ignored) {
                            }
                        }
                    });
        }

    }
}
