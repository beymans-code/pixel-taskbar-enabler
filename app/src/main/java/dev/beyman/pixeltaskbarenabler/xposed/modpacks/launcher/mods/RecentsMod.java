package dev.beyman.pixeltaskbarenabler.xposed.modpacks.launcher.mods;

import android.content.Context;
import android.view.ViewGroup;

import dev.beyman.pixeltaskbarenabler.xposed.modpacks.launcher.TaskbarActivator;
import dev.beyman.pixeltaskbarenabler.xposed.modpacks.launcher.TaskbarSettings;
import dev.beyman.pixeltaskbarenabler.xposed.utils.reflection.ReflectedClass;
import dev.beyman.pixeltaskbarenabler.xposed.utils.reflection.ReflectedClass.ReflectionConsumer;

import java.lang.reflect.Method;
import java.util.List;
import java.util.regex.Pattern;

import static dev.beyman.pixeltaskbarenabler.xposed.utils.SystemUtils.idOf;
import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.setObjectField;

/**
 * Submódulo encargado de manejar todo lo relacionado con la pantalla de Aplicaciones Recientes.
 * Esto incluye invertir el grid de recientes para facilitar el uso móvil (MobileRecents),
 * ajustar el layout de la barra de navegación tradicional (3 botones) e inyectar
 * aplicaciones recientes en el dock.
 */
public class RecentsMod extends BaseLauncherMod {

    public RecentsMod(TaskbarActivator activator, Context context, TaskbarSettings settings) {
        super(activator, context, settings);
    }

    /**
     * Aplica los hooks relacionados con el layout de Recientes, incluyendo navegación,
     * inversión de grid y sincronización de aplicaciones en el Taskbar.
     */
    @Override
    public void applyHooks() throws Throwable {
        ReflectedClass AbstractNavButtonLayoutterClass = ReflectedClass.ofIfPossible("com.android.launcher3.taskbar.navbutton.AbstractNavButtonLayoutter");
        ReflectedClass RecentsViewClass = ReflectedClass.ofIfPossible("com.android.quickstep.views.RecentsView");
        ReflectedClass OverviewActionsViewClass = ReflectedClass.ofIfPossible("com.android.quickstep.views.OverviewActionsView");
        ReflectedClass TaskViewClass = ReflectedClass.ofIfPossible("com.android.quickstep.views.TaskView");
        ReflectedClass BaseContainerInterfaceClass = ReflectedClass.ofIfPossible("com.android.quickstep.BaseContainerInterface");
        ReflectedClass RecentAppsControllerClass = ReflectedClass.ofIfPossible("com.android.launcher3.taskbar.TaskbarRecentAppsController");
        ReflectedClass TaskbarViewClass = ReflectedClass.ofIfPossible("com.android.launcher3.taskbar.TaskbarView");

        if (RecentsViewClass != null) {
            // Fuerza al RecentsView a no usar diseño de cuadrícula genérico cuando MobileRecents está activado.
            RecentsViewClass.before("showAsGrid").run(param -> {
                if (mSettings.mobileRecents) {
                    param.setResult(false);
                }
            });
            RecentsViewClass.before("setOverviewGridEnabled").run(param -> {
                if (mSettings.mobileRecents && param.args.length > 0) {
                    param.args[0] = false;
                }
            });

            // Invierte manualmente el cálculo de posiciones Y de cada vista de tarea (TaskView) 
            // dentro del RecentsView para que las apps salgan en la parte inferior de la pantalla.
            RecentsViewClass.after("updateGridProperties").run(param -> {
                if (param.args.length == 1) {
                    try {
                        Object recentsView = param.thisObject;
                        boolean showAsGrid = (boolean) callMethod(recentsView, "showAsGrid");
                        if (showAsGrid) {
                            Object mTopRowIdSet = getObjectField(recentsView, "mTopRowIdSet");

                            int taskCount = (int) callMethod(recentsView, "getTaskViewCount");
                            java.util.List<Integer> newTopRowIds = new java.util.ArrayList<>();

                            for (int i = 0; i < taskCount; i++) {
                                Object taskView = callMethod(recentsView, "getTaskViewAt", i);
                                if (taskView != null) {
                                    boolean isLargeTile = (boolean) callMethod(taskView, "isLargeTile");
                                    if (!isLargeTile) {
                                        int taskId = (int) callMethod(taskView, "getTaskViewId");
                                        boolean wasInTopRow = (boolean) callMethod(mTopRowIdSet, "contains", taskId);
                                        if (!wasInTopRow) {
                                            newTopRowIds.add(taskId);
                                        }
                                    }
                                }
                            }

                            Object mArray = getObjectField(mTopRowIdSet, "mArray");
                            de.robv.android.xposed.XposedHelpers.setIntField(mArray, "mSize", 0);

                            for (Integer id : newTopRowIds) {
                                callMethod(mTopRowIdSet, "add", id);
                            }

                            float topBottomRowHeightDiff = (float) de.robv.android.xposed.XposedHelpers.getFloatField(recentsView, "mTopBottomRowHeightDiff");
                            for (int i = 0; i < taskCount; i++) {
                                Object taskView = callMethod(recentsView, "getTaskViewAt", i);
                                if (taskView != null) {
                                    boolean isLargeTile = (boolean) callMethod(taskView, "isLargeTile");
                                    if (!isLargeTile) {
                                        int taskId = (int) callMethod(taskView, "getTaskViewId");
                                        boolean isTopRow = (boolean) callMethod(mTopRowIdSet, "contains", taskId);
                                        float newTranslationY = isTopRow ? 0.0f : topBottomRowHeightDiff;
                                        callMethod(taskView, "setGridTranslationY", newTranslationY);
                                    }
                                }
                            }
                        }
                    } catch (Throwable e) {
                        dev.beyman.pixeltaskbarenabler.xposed.utils.toolkit.Logger.log("Error invirtiendo cuadrícula: " + e.getMessage());
                    }
                }
            });
        }

        // Bloquea el layout de Grid en diversos estados del Launcher 
        // (Overview, Background, RecentsState) para forzar vistas de carrusel móvil.
        ReflectionConsumer displayOverviewTasksAsGridHook = param -> {
            if (mSettings.mobileRecents) {
                param.setResult(false);
            }
        };

        ReflectedClass.ofIfPossible("com.android.launcher3.uioverrides.states.OverviewState")
                .before("displayOverviewTasksAsGrid").run(displayOverviewTasksAsGridHook);

        ReflectedClass.ofIfPossible("com.android.launcher3.uioverrides.states.BackgroundAppState")
                .before("displayOverviewTasksAsGrid").run(displayOverviewTasksAsGridHook);

        ReflectedClass.ofIfPossible("com.android.quickstep.fallback.RecentsState")
                .before("displayOverviewTasksAsGrid").run(displayOverviewTasksAsGridHook);


        ReflectionConsumer isLargeScreenToggleHookBefore = param -> {
            if (mSettings.mobileRecents) {
                try {
                    Object deviceProfile = param.args[1];
                    Object deviceProperties = getObjectField(deviceProfile, "deviceProperties");
                    de.robv.android.xposed.XposedHelpers.setBooleanField(deviceProperties, "isLargeScreen", false);
                } catch (Throwable ignored) {
                }
            }
        };

        ReflectionConsumer isLargeScreenToggleHookAfter = param -> {
            if (mSettings.mobileRecents) {
                try {
                    Object deviceProfile = param.args[1];
                    Object deviceProperties = getObjectField(deviceProfile, "deviceProperties");
                    de.robv.android.xposed.XposedHelpers.setBooleanField(deviceProperties, "isLargeScreen", true);
                } catch (Throwable ignored) {
                }
            }
        };

        // Engaña a BaseContainerInterfaceClass temporalmente para calcular tamaños 
        // modales basándose en pantalla pequeña, y luego restaura isLargeScreen a true.
        if (BaseContainerInterfaceClass != null) {
            BaseContainerInterfaceClass.before("calculateTaskSize").run(isLargeScreenToggleHookBefore);
            BaseContainerInterfaceClass.after("calculateTaskSize").run(isLargeScreenToggleHookAfter);
            BaseContainerInterfaceClass.before("calculateModalTaskSize").run(isLargeScreenToggleHookBefore);
            BaseContainerInterfaceClass.after("calculateModalTaskSize").run(isLargeScreenToggleHookAfter);

            BaseContainerInterfaceClass.before("calculateGridTaskSize").run(param -> {
                if (mSettings.mobileRecents) {
                    try {
                        Object context = param.args[0];
                        Object deviceProfile = param.args[1];
                        android.graphics.Rect rect = (android.graphics.Rect) param.args[2];
                        Object recentsPagedOrientationHandler = param.args[3];
                        Object baseContainer = param.thisObject;

                        callMethod(baseContainer, "calculateTaskSize", context, deviceProfile, rect, recentsPagedOrientationHandler);
                        param.setResult(null);
                    } catch (Throwable ignored) {
                    }
                }
            });
        }

        // Escala los iconos en la parte superior (cabecera) de la vista de Recientes
        // según la preferencia gridHeaderScale.
        if (TaskViewClass != null) {
            TaskViewClass.before("isGridTask").run(param -> {
                if (mSettings.mobileRecents) param.setResult(false);
            });
            TaskViewClass.after("onLayout").run(param -> {
                try {
                    boolean isGridTask = (boolean) callMethod(param.thisObject, "isGridTask");
                    if (isGridTask) {
                        java.util.List<?> taskContainers = (java.util.List<?>) callMethod(param.thisObject, "getTaskContainers");
                        if (taskContainers != null) {
                            for (Object container : taskContainers) {
                                android.view.View iconView = (android.view.View) callMethod(container, "getIconView");
                                if (iconView != null) {
                                    iconView.setScaleX(mSettings.gridHeaderScale);
                                    iconView.setScaleY(mSettings.gridHeaderScale);
                                }
                            }
                        }
                    }
                } catch (Throwable ignored) {
                }
            });
        }

        // Añade márgenes en la parte inferior de OverviewActionsView (Ej. Botón de "Borrar todo") 
        // para dar espacio a la interfaz inferior generada por MobileRecents.
        if (OverviewActionsViewClass != null) {
            OverviewActionsViewClass.before("updateHiddenFlags").run(param -> {
                if (mSettings.mobileRecents && (int) param.args[0] == 32) {
                    param.args[1] = false;
                }
            });
            OverviewActionsViewClass.after("getBottomMargin").run(param -> {
                if (mSettings.mobileRecents) {
                    int margin = (int) param.getResult();
                    param.setResult(margin + 150);
                }
            });
        }

        // Filtra y elimina las aplicaciones que están en pantalla dividida (Split Screen) 
        // de mostrarse en la sección de aplicaciones recientes del Taskbar.
        if (TaskbarViewClass != null) {
            TaskbarViewClass
                    .before("updateRecents")
                    .run(param -> {
                        @SuppressWarnings("unchecked")
                        List<Object> recents = (List<Object>) param.args[1];
                        param.args[1] = recents.stream().filter(t -> !t.getClass().getName().contains("Split")).toList();
                    });
        }
    }
}
