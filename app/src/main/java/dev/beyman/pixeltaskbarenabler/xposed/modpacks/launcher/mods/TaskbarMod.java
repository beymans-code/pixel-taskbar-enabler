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
    private boolean isTransientTaskbar(Object context) {
        if (context == null) return false;
        try {
            return de.robv.android.xposed.XposedHelpers.getBooleanField(context, "mIsTransient");
        } catch (Throwable t) {
            return false;
        }
    }

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
        ReflectedClass TaskbarDragLayerClass = ReflectedClass.ofIfPossible("com.android.launcher3.taskbar.TaskbarDragLayer");
        ReflectedClass BaseDragLayerClass = ReflectedClass.ofIfPossible("com.android.launcher3.views.BaseDragLayer");
        ReflectedClass TaskbarStashControllerClass = ReflectedClass.ofIfPossible("com.android.launcher3.taskbar.TaskbarStashController");

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
        // Hook para escalar visualmente toda la capa contenedora del Taskbar a través del Canvas
        // Esto evita el doble escalado que ocurría al usar View.setScaleX/Y (que recortaba los bordes)
        if (TaskbarDragLayerClass != null) {
            TaskbarDragLayerClass
                    .before("dispatchDraw")
                    .run(param -> {
                        if (mSettings.taskbarMode == TaskbarSettings.TASKBAR_DEFAULT) return;
                        
                        android.view.View view = (android.view.View) param.thisObject;
                        if (!isTransientTaskbar(view.getContext())) return;
                        
                        float scale = mSettings.taskbarScale;
                        if (scale != 1.0f) {
                            android.graphics.Canvas canvas = (android.graphics.Canvas) param.args[0];
                            canvas.save();
                            canvas.scale(scale, scale, view.getWidth() / 2f, view.getHeight());
                        }
                    });

            TaskbarDragLayerClass
                    .after("dispatchDraw")
                    .run(param -> {
                        if (mSettings.taskbarMode == TaskbarSettings.TASKBAR_DEFAULT) return;
                        
                        android.view.View view = (android.view.View) param.thisObject;
                        if (!isTransientTaskbar(view.getContext())) return;
                        
                        float scale = mSettings.taskbarScale;
                        if (scale != 1.0f) {
                            android.graphics.Canvas canvas = (android.graphics.Canvas) param.args[0];
                            canvas.restore();
                        }
                    });
        }

        // Como escalamos la vista, los toques físicos ya no coinciden con la ubicación visual.
        // Aplicamos la matriz inversa a los eventos táctiles para que coincidan.
        if (BaseDragLayerClass != null) {
            BaseDragLayerClass
                    .before("dispatchTouchEvent")
                    .run(param -> {
                        if (mSettings.taskbarMode == TaskbarSettings.TASKBAR_DEFAULT) return;
                        
                        android.view.View view = (android.view.View) param.thisObject;
                        if (!isTransientTaskbar(view.getContext())) return;
                        
                        float scale = mSettings.taskbarScale;
                        if (scale != 1.0f) {
                            // Solo interceptar para la barra de tareas
                            if (view.getClass().getName().contains("TaskbarDragLayer")) {
                                android.view.MotionEvent event = (android.view.MotionEvent) param.args[0];
                                android.graphics.Matrix m = new android.graphics.Matrix();
                                // La matriz de toque debe ser el inverso de la matriz de escalado visual
                                m.setScale(1f / scale, 1f / scale, view.getWidth() / 2f, view.getHeight());
                                event.transform(m);
                            }
                        }
                    });
        }

        // Al achicar visualmente el Taskbar, debemos avisarle al sistema Android 
        // para que reduzca el inset y las aplicaciones puedan bajar a usar ese espacio extra.
        if (TaskbarStashControllerClass != null) {
            TaskbarStashControllerClass
                    .after("getContentHeightToReportToApps")
                    .run(param -> {
                        if (mSettings.taskbarMode == TaskbarSettings.TASKBAR_DEFAULT) return;
                        
                        Object activity = de.robv.android.xposed.XposedHelpers.getObjectField(param.thisObject, "mActivity");
                        if (!isTransientTaskbar(activity)) return;
                        
                        float scale = mSettings.taskbarScale;
                        if (scale != 1.0f) {
                            int result = (int) param.getResult();
                            param.setResult(Math.round(result * scale));
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
