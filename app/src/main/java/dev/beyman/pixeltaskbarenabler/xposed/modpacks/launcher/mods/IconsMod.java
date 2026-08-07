package dev.beyman.pixeltaskbarenabler.xposed.modpacks.launcher.mods;

import android.content.Context;

import dev.beyman.pixeltaskbarenabler.xposed.modpacks.launcher.TaskbarActivator;
import dev.beyman.pixeltaskbarenabler.xposed.modpacks.launcher.TaskbarSettings;
import dev.beyman.pixeltaskbarenabler.xposed.utils.reflection.ReflectedClass;
import dev.beyman.pixeltaskbarenabler.xposed.utils.reflection.ReflectedClass.ReflectionConsumer;

import java.util.HashSet;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.getIntField;

/**
 * Submódulo responsable de las modificaciones visuales en los íconos del Launcher.
 * Ajusta la escala de los íconos (TaskbarIconSize), gestiona la cantidad de íconos que 
 * se muestran en el dock (TaskbarIconCount) y modifica los espacios y dimensiones de los botones.
 */
public class IconsMod extends BaseLauncherMod {

    public IconsMod(TaskbarActivator activator, Context context, TaskbarSettings settings) {
        super(activator, context, settings);
    }

    /**
     * Aplica los hooks necesarios para sobrescribir la escala y límites visuales de los iconos.
     */
    @Override
    public void applyHooks() throws Throwable {
        ReflectedClass TaskbarViewClass = ReflectedClass.ofIfPossible("com.android.launcher3.taskbar.TaskbarView");
        ReflectedClass TaskbarModelCallbacksClass = ReflectedClass.ofIfPossible("com.android.launcher3.taskbar.TaskbarModelCallbacks");
        ReflectedClass KeyboardQuickSwitchControllerClass = ReflectedClass.ofIfPossible("com.android.launcher3.taskbar.KeyboardQuickSwitchController");
        ReflectedClass TaskbarActivityContextClass = ReflectedClass.ofIfPossible("com.android.launcher3.taskbar.TaskbarActivityContext");
        ReflectedClass DeviceProfileClass = ReflectedClass.ofIfPossible("com.android.launcher3.DeviceProfile");
        ReflectedClass TaskbarProfileClass = ReflectedClass.ofIfPossible("com.android.launcher3.deviceprofile.TaskbarProfile");
        ReflectedClass TaskbarIconSizeClass = ReflectedClass.ofIfPossible("com.android.launcher3.taskbar.customization.TaskbarIconSize");
        ReflectedClass BubbleTextViewClass = ReflectedClass.ofIfPossible("com.android.launcher3.BubbleTextView");
        ReflectedClass TaskbarSpecsEvaluatorClass = ReflectedClass.ofIfPossible("com.android.launcher3.taskbar.customization.TaskbarSpecsEvaluator");
        ReflectedClass InvariantDeviceProfileClass = ReflectedClass.ofIfPossible("com.android.launcher3.InvariantDeviceProfile");
        ReflectedClass HotseatProfileClass = ReflectedClass.ofIfPossible("com.android.launcher3.deviceprofile.HotseatProfile");

        // Define la cantidad máxima de íconos mostrados en la barra de tareas.
        if (TaskbarViewClass != null) {
            TaskbarViewClass.after("calculateMaxNumIcons").run(param -> {
                if (mSettings.taskbarMode == TaskbarSettings.TASKBAR_ON) {
                    param.setResult(mSettings.taskbarIconCount + 3);
                }
            });
        }

        if (TaskbarModelCallbacksClass != null) {
            TaskbarModelCallbacksClass.before("commitHotseatItemUpdates").run(param -> {
                if (mSettings.taskbarMode == TaskbarSettings.TASKBAR_ON) {
                    Object[] originalPinned = (Object[]) param.args[0];
                    if (originalPinned != null) {
                        // Max 3 pinned apps
                        Object[] newPinned = originalPinned.clone();
                        int pinnedCount = 0;
                        for (int i = 0; i < newPinned.length; i++) {
                            if (newPinned[i] != null) {
                                if (pinnedCount >= 3) {
                                    newPinned[i] = null; // Remove excess pinned apps
                                } else {
                                    pinnedCount++;
                                }
                            }
                        }
                        param.args[0] = newPinned;
                        
                        // Recents
                        int maxRecent = Math.max(0, mSettings.taskbarIconCount - pinnedCount);
                        int recentCount = 0;
                        try {
                            Object mControllers = de.robv.android.xposed.XposedHelpers.getObjectField(param.thisObject, "mControllers");
                            Object taskbarRecentAppsController = de.robv.android.xposed.XposedHelpers.getObjectField(mControllers, "taskbarRecentAppsController");
                            java.util.List<?> allRecentTasks = (java.util.List<?>) de.robv.android.xposed.XposedHelpers.getObjectField(taskbarRecentAppsController, "allRecentTasks");
                            
                            java.util.List<Object> filteredRecents = new java.util.ArrayList<>();
                            if (allRecentTasks != null) {
                                for (Object groupTask : allRecentTasks) {
                                    if (filteredRecents.size() >= maxRecent) break;
                                    
                                    boolean isPinned = false;
                                    
                                    try {
                                        java.util.List<?> runningTaskIds = (java.util.List<?>) de.robv.android.xposed.XposedHelpers.getObjectField(taskbarRecentAppsController, "orderedRunningTaskIds");
                                        if (runningTaskIds != null) {
                                            for (Object taskId : runningTaskIds) {
                                                Boolean contains = (Boolean) de.robv.android.xposed.XposedHelpers.callMethod(groupTask, "containsTask", taskId);
                                                if (contains != null && contains) {
                                                    isPinned = true;
                                                    break;
                                                }
                                            }
                                        }
                                    } catch (Throwable t) { }
                                    
                                    if (isPinned) continue;
                                    
                                    for (Object pinnedItem : newPinned) {
                                        if (pinnedItem != null) {
                                            String pinnedPackage = (String) de.robv.android.xposed.XposedHelpers.callMethod(pinnedItem, "getTargetPackage");
                                            Object userHandle = de.robv.android.xposed.XposedHelpers.getObjectField(pinnedItem, "user");
                                            int pinnedUserId = (int) de.robv.android.xposed.XposedHelpers.callMethod(userHandle, "getIdentifier");
                                            
                                            Boolean contains = (Boolean) de.robv.android.xposed.XposedHelpers.callMethod(groupTask, "containsPackage", pinnedPackage, pinnedUserId);
                                            if (contains != null && contains) {
                                                isPinned = true;
                                                break;
                                            }
                                        }
                                    }
                                    if (!isPinned) {
                                        filteredRecents.add(groupTask);
                                    }
                                }
                            }
                            param.args[1] = filteredRecents;
                            recentCount = filteredRecents.size();
                            
                            // Forzamos a TaskbarRecentAppsController a actualizar su propia lista
                            // para que descargue y asigne los íconos (bitmaps) correctamente en la UI.
                            de.robv.android.xposed.XposedHelpers.setObjectField(taskbarRecentAppsController, "shownTasks", filteredRecents);
                            
                            try {
                                de.robv.android.xposed.XposedHelpers.callMethod(taskbarRecentAppsController, "fetchIcons", true);
                            } catch (Throwable t2) {
                                try {
                                    de.robv.android.xposed.XposedHelpers.callMethod(taskbarRecentAppsController, "fetchIcons", false);
                                } catch (Throwable t3) {
                                    // Ignore
                                }
                            }
                        } catch (Throwable t) {
                            java.util.List<?> recent = (java.util.List<?>) param.args[1];
                            if (recent != null && recent.size() > maxRecent) {
                                param.args[1] = new java.util.ArrayList<>(recent.subList(0, maxRecent));
                            }
                            recentCount = (recent == null) ? 0 : Math.min(recent.size(), maxRecent);
                        }
                        
                        // Suggestions (Handoff)
                        java.util.List<?> suggestions = (java.util.List<?>) param.args[2];
                        int maxSuggestions = Math.max(0, mSettings.taskbarIconCount - pinnedCount - recentCount);
                        if (suggestions != null && suggestions.size() > maxSuggestions) {
                            param.args[2] = new java.util.ArrayList<>(suggestions.subList(0, maxSuggestions));
                        }
                    }
                }
            });
        }

        // Oculta del QuickSwitch (Alt+Tab) la aplicación actual en uso.
        if (KeyboardQuickSwitchControllerClass != null) {
            KeyboardQuickSwitchControllerClass
                    .before("openQuickSwitchView")
                    .run(param -> {
                        @SuppressWarnings("unchecked")
                        HashSet<Object> exclusionList = (HashSet<Object>) param.args[1];
                        if (mActivator.notInHomeScreen()) {
                            exclusionList.add(callMethod(mActivator.getCurrentTopTask(), "getTaskId"));
                        }
                    });
        }



        // Intercepta DeviceProfile antes y después de su creación para 
        // manipular sus parámetros internos (isLargeScreen, numShownIcons, rowSpacing).
        if (DeviceProfileClass != null) {
            DeviceProfileClass.beforeConstruction().run(param -> {
                if (mSettings.taskbarMode == TaskbarSettings.TASKBAR_ON) {
                    try {
                        if (param.args.length >= 15) { // Android 17+
                            Object launcherDisplayInfo = param.args[1];
                            Object deviceProperties = param.args[2];
                            Object displayOptionSpec = param.args[4];

                            if (deviceProperties != null) {
                                de.robv.android.xposed.XposedHelpers.setBooleanField(deviceProperties, "isLargeScreen", true);
                                de.robv.android.xposed.XposedHelpers.setBooleanField(deviceProperties, "isPhone", false);
                                Object taskbarConfiguration = de.robv.android.xposed.XposedHelpers.getObjectField(deviceProperties, "taskbarConfiguration");
                                if (taskbarConfiguration != null) {
                                    de.robv.android.xposed.XposedHelpers.setBooleanField(taskbarConfiguration, "isTaskbarPresent", true);
                                }
                            }

                            android.content.Context ctx = (android.content.Context) de.robv.android.xposed.XposedHelpers.getObjectField(launcherDisplayInfo, "context");
                            if (ctx != null) {
                                Class<?> taskbarFactoryClass = de.robv.android.xposed.XposedHelpers.findClass("com.android.launcher3.deviceprofile.TaskbarProfile$Factory", launcherDisplayInfo.getClass().getClassLoader());
                                Object newTaskbarProfile = de.robv.android.xposed.XposedHelpers.callStaticMethod(
                                        taskbarFactoryClass,
                                        "createTaskbarProfile",
                                        ctx.getResources(),
                                        true, // isTransientTaskbar
                                        true,  // isTaskbarPresent
                                        displayOptionSpec
                                );
                                param.args[14] = newTaskbarProfile;
                            }
                        }
                    } catch (Throwable e) {
                        dev.beyman.pixeltaskbarenabler.xposed.utils.toolkit.Logger.log("TaskbarActivator Android 17 injection failed: " + e.getMessage());
                    }
                }
            });

            DeviceProfileClass.afterConstruction().run(param -> {
                if (mSettings.taskbarMode == TaskbarSettings.TASKBAR_DEFAULT) return;

                if (mSettings.taskbarMode == TaskbarSettings.TASKBAR_ON) {
                    mActivator.setDeviceProfile(param.thisObject);
                    
                    try {
                        de.robv.android.xposed.XposedHelpers.setBooleanField(param.thisObject, "isTablet", true);
                    } catch (Throwable ignored) {
                    }
                    try {
                        de.robv.android.xposed.XposedHelpers.setBooleanField(param.thisObject, "isTaskbarPresent", true);
                    } catch (Throwable ignored) {
                    }
                    try {
                        de.robv.android.xposed.XposedHelpers.setBooleanField(param.thisObject, "isPhone", false);
                    } catch (Throwable ignored) {
                    }

                    try {
                        Object overviewProfile = de.robv.android.xposed.XposedHelpers.getObjectField(param.thisObject, "overviewProfile");
                        if (overviewProfile != null) {
                            int currentRowSpacing = de.robv.android.xposed.XposedHelpers.getIntField(overviewProfile, "rowSpacing");
                            de.robv.android.xposed.XposedHelpers.setIntField(overviewProfile, "rowSpacing", currentRowSpacing + 100);
                        }
                    } catch (Throwable ignored) {
                    }

                    try {
                        Object inv = de.robv.android.xposed.XposedHelpers.getObjectField(param.thisObject, "inv");
                        if (inv != null)
                            de.robv.android.xposed.XposedHelpers.setIntField(inv, "numDatabaseHotseatIcons", mSettings.taskbarIconCount);
                    } catch (Throwable ignored) {
                    }
                    try {
                        Object hotseatProfile = de.robv.android.xposed.XposedHelpers.getObjectField(param.thisObject, "hotseatProfile");
                        if (hotseatProfile != null)
                            de.robv.android.xposed.XposedHelpers.setIntField(hotseatProfile, "numShownIcons", mSettings.taskbarIconCount);
                    } catch (Throwable ignored) {
                    }
                    try {
                        Object mHotseatProfile = de.robv.android.xposed.XposedHelpers.getObjectField(param.thisObject, "mHotseatProfile");
                        if (mHotseatProfile != null)
                            de.robv.android.xposed.XposedHelpers.setIntField(mHotseatProfile, "numShownIcons", mSettings.taskbarIconCount);
                    } catch (Throwable ignored) {
                    }
                }
            });
        }

        // Re-calcula y aplica la escala seleccionada por el usuario (TaskbarIconScale) a 
        // los diversos tamaños e íconos en el dock.
        if (TaskbarProfileClass != null) {
            TaskbarProfileClass
                    .afterConstruction()
                    .run(param -> {
                        if (mSettings.taskbarMode == TaskbarSettings.TASKBAR_ON) {
                            try {
                                int currentSize = de.robv.android.xposed.XposedHelpers.getIntField(param.thisObject, "iconSizePx");
                                de.robv.android.xposed.XposedHelpers.setIntField(param.thisObject, "iconSizePx", Math.round(currentSize * mSettings.taskbarIconScale));
                            } catch (Throwable ignored) {
                            }
                            try {
                                int currentDrawableSize = de.robv.android.xposed.XposedHelpers.getIntField(param.thisObject, "iconDrawableSizePx");
                                de.robv.android.xposed.XposedHelpers.setIntField(param.thisObject, "iconDrawableSizePx", Math.round(currentDrawableSize * mSettings.taskbarIconScale));
                            } catch (Throwable ignored) {
                            }
                        }
                    });
        }

        if (TaskbarIconSizeClass != null) {
            TaskbarIconSizeClass
                    .afterConstruction()
                    .run(param -> {
                        if (mSettings.taskbarMode == TaskbarSettings.TASKBAR_ON) {
                            try {
                                int s = getIntField(param.thisObject, "size");
                                de.robv.android.xposed.XposedHelpers.setIntField(param.thisObject, "size", Math.round(s * mSettings.taskbarIconScale));
                            } catch (Throwable ignored) {
                            }
                        }
                    });
        }

        if (TaskbarActivityContextClass != null) {
            TaskbarActivityContextClass
                    .afterConstruction()
                    .run(param -> {
                        if (mSettings.taskbarMode == TaskbarSettings.TASKBAR_ON) {
                            try {
                                Object taskbarDeviceProfile = de.robv.android.xposed.XposedHelpers.getObjectField(param.thisObject, "mDeviceProfile");
                                if (taskbarDeviceProfile != null) {
                                    int currentSize = de.robv.android.xposed.XposedHelpers.getIntField(taskbarDeviceProfile, "iconSizePx");
                                    de.robv.android.xposed.XposedHelpers.setIntField(taskbarDeviceProfile, "iconSizePx", Math.round(currentSize * mSettings.taskbarIconScale));

                                    int currentDrawableSize = de.robv.android.xposed.XposedHelpers.getIntField(taskbarDeviceProfile, "iconDrawableSizePx");
                                    de.robv.android.xposed.XposedHelpers.setIntField(taskbarDeviceProfile, "iconDrawableSizePx", Math.round(currentDrawableSize * mSettings.taskbarIconScale));
                                    
                                    try {
                                        Object hotseatProfile = de.robv.android.xposed.XposedHelpers.getObjectField(taskbarDeviceProfile, "hotseatProfile");
                                        if (hotseatProfile != null) {
                                            de.robv.android.xposed.XposedHelpers.setIntField(hotseatProfile, "numShownIcons", mSettings.taskbarIconCount);
                                        }
                                    } catch (Throwable ignored) {}
                                    
                                    try {
                                        Object mHotseatProfile = de.robv.android.xposed.XposedHelpers.getObjectField(taskbarDeviceProfile, "mHotseatProfile");
                                        if (mHotseatProfile != null) {
                                            de.robv.android.xposed.XposedHelpers.setIntField(mHotseatProfile, "numShownIcons", mSettings.taskbarIconCount);
                                        }
                                    } catch (Throwable ignored) {}
                                }
                            } catch (Throwable ignored) {
                            }

                            try {
                                Object mControllers = de.robv.android.xposed.XposedHelpers.getObjectField(param.thisObject, "mControllers");
                                Object taskbarViewController = de.robv.android.xposed.XposedHelpers.getObjectField(mControllers, "taskbarViewController");

                                int tSize = de.robv.android.xposed.XposedHelpers.getIntField(taskbarViewController, "mTransientIconSize");
                                de.robv.android.xposed.XposedHelpers.setIntField(taskbarViewController, "mTransientIconSize", Math.round(tSize * mSettings.taskbarIconScale));

                                int pSize = de.robv.android.xposed.XposedHelpers.getIntField(taskbarViewController, "mPersistentIconSize");
                                de.robv.android.xposed.XposedHelpers.setIntField(taskbarViewController, "mPersistentIconSize", Math.round(pSize * mSettings.taskbarIconScale));
                            } catch (Throwable ignored) {
                            }
                        }
                    });
        }

        // Fuerza a BubbleTextView a utilizar el tamaño reescalado para sus iconos y 
        // ajusta sus márgenes superiores/padding para centrar la visualización.
        if (BubbleTextViewClass != null) {
            BubbleTextViewClass
                    .afterConstruction()
                    .run(param -> {
                        if (mSettings.taskbarMode == TaskbarSettings.TASKBAR_ON) {
                            try {
                                int display = de.robv.android.xposed.XposedHelpers.getIntField(param.thisObject, "mDisplay");
                                if (display == 5) { // Taskbar
                                    int currentIconSize = de.robv.android.xposed.XposedHelpers.getIntField(param.thisObject, "mIconSize");
                                    de.robv.android.xposed.XposedHelpers.setIntField(param.thisObject, "mIconSize", Math.round(currentIconSize * mSettings.taskbarIconScale));
                                }
                            } catch (Throwable ignored) {
                            }
                        }
                    });

            BubbleTextViewClass
                    .after("onSizeChanged")
                    .run(param -> {
                        if (mSettings.taskbarMode == TaskbarSettings.TASKBAR_ON) {
                            try {
                                int display = de.robv.android.xposed.XposedHelpers.getIntField(param.thisObject, "mDisplay");
                                if (display == 5) { // Taskbar
                                    android.view.View view = (android.view.View) param.thisObject;
                                    int currentIconSize = de.robv.android.xposed.XposedHelpers.getIntField(param.thisObject, "mIconSize");
                                    int h = (int) param.args[1];
                                    int pt = (h - currentIconSize) / 2;
                                    if (pt > 0 && view.getPaddingTop() != pt) {
                                        view.setPadding(view.getPaddingLeft(), pt, view.getPaddingRight(), view.getPaddingBottom());
                                    }
                                }
                            } catch (Throwable ignored) {
                            }
                        }
                    });
        }

        // Escala el tamaño del botón 'App Drawer' (Cajón de Aplicaciones).
        if (TaskbarSpecsEvaluatorClass != null) {
            TaskbarSpecsEvaluatorClass
                    .after("getTaskbarAllAppsButtonIconViewWidth")
                    .run(param -> {
                        if (mSettings.taskbarMode == TaskbarSettings.TASKBAR_ON) {
                            float original = (float) param.getResult();
                            param.setResult(original * mSettings.taskbarIconScale);
                        }
                    });
            TaskbarSpecsEvaluatorClass
                    .after("getTaskbarAllAppsButtonIconViewHeight")
                    .run(param -> {
                        if (mSettings.taskbarMode == TaskbarSettings.TASKBAR_ON) {
                            float original = (float) param.getResult();
                            param.setResult(original * mSettings.taskbarIconScale);
                        }
                    });
            TaskbarSpecsEvaluatorClass
                    .after("getTaskbarAllAppsButtonWidth")
                    .run(param -> {
                        if (mSettings.taskbarMode == TaskbarSettings.TASKBAR_ON) {
                            float original = (float) param.getResult();
                            param.setResult(original * mSettings.taskbarIconScale);
                        }
                    });
            TaskbarSpecsEvaluatorClass
                    .after("getTaskbarAllAppsButtonHeight")
                    .run(param -> {
                        if (mSettings.taskbarMode == TaskbarSettings.TASKBAR_ON) {
                            float original = (float) param.getResult();
                            param.setResult(original * mSettings.taskbarIconScale);
                        }
                    });
        }

        if (InvariantDeviceProfileClass != null) {
            InvariantDeviceProfileClass
                    .after("initGrid")
                    .run(param -> {
                        if (mSettings.taskbarMode == TaskbarSettings.TASKBAR_ON) {
                            try {
                                de.robv.android.xposed.XposedHelpers.setIntField(param.thisObject, "numDatabaseHotseatIcons", mSettings.taskbarIconCount);
                            } catch (Throwable ignored) {
                            }
                        }
                    });
        }

        if (HotseatProfileClass != null) {
            HotseatProfileClass.afterConstruction().run(param -> {
                if (mSettings.taskbarMode == TaskbarSettings.TASKBAR_ON) {
                    try {
                        de.robv.android.xposed.XposedHelpers.setIntField(param.thisObject, "numShownIcons", mSettings.taskbarIconCount);
                        de.robv.android.xposed.XposedBridge.log("PTE: Successfully set HotseatProfile.numShownIcons to " + mSettings.taskbarIconCount);
                    } catch (Throwable t) {
                        de.robv.android.xposed.XposedBridge.log("PTE: Error setting numShownIcons: " + t.getMessage());
                    }
                }
            });
        }
    }
}
