package dev.beyman.pixeltaskbarenabler.xposed.modpacks.launcher;

import android.annotation.SuppressLint;
import android.content.Context;

import dev.beyman.pixeltaskbarenabler.xposed.XposedModPack;
import dev.beyman.pixeltaskbarenabler.xposed.annotations.LauncherModPack;
import dev.beyman.pixeltaskbarenabler.xposed.modpacks.launcher.mods.IconsMod;
import dev.beyman.pixeltaskbarenabler.xposed.modpacks.launcher.mods.RecentsMod;
import dev.beyman.pixeltaskbarenabler.xposed.modpacks.launcher.mods.TaskbarMod;
import dev.beyman.pixeltaskbarenabler.xposed.utils.reflection.ReflectedClass;

import java.util.Arrays;
import java.util.List;

import io.github.libxposed.api.XposedModuleInterface;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.getIntField;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.getStaticObjectField;

/**
 * Módulo principal de Xposed para el Launcher. Actúa como el coordinador central que
 * inicializa el estado de las configuraciones (TaskbarSettings) e instancia y aplica 
 * los distintos submódulos (TaskbarMod, RecentsMod, IconsMod).
 * También provee métodos de utilidad global para los submódulos.
 */
@LauncherModPack
public class TaskbarActivator extends XposedModPack {

    private TaskbarSettings mSettings;
    private ReflectedClass TopTaskTrackerClass;
    private Object mCurrentTopTask;
    private Object mDeviceProfile;

    public TaskbarActivator(Context context) {
        super(context);
        mSettings = new TaskbarSettings();
    }

    /**
     * Invocado cuando las preferencias (XPrefs) cambian.
     * Delega la actualización del estado a TaskbarSettings.
     * @param Key La clave(s) de la preferencia que ha cambiado.
     */
    @Override
    public void onPreferenceUpdated(String... Key) {
        mSettings.update(Key);
    }

    /**
     * Invocado cuando el paquete del Launcher se carga en memoria.
     * Inicializa las referencias principales e instancia y ejecuta los módulos.
     */
    @SuppressLint("DiscouragedApi")
    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageReadyParam PRParam) throws Throwable {
        TopTaskTrackerClass = ReflectedClass.ofIfPossible("com.android.quickstep.TopTaskTracker");

        List<dev.beyman.pixeltaskbarenabler.xposed.modpacks.launcher.mods.BaseLauncherMod> mods = Arrays.asList(
                new TaskbarMod(this, mContext, mSettings),
                new RecentsMod(this, mContext, mSettings),
                new IconsMod(this, mContext, mSettings)
        );

        for (dev.beyman.pixeltaskbarenabler.xposed.modpacks.launcher.mods.BaseLauncherMod mod : mods) {
            mod.applyHooks();
        }
    }

    /**
     * Guarda la referencia a la instancia actual de DeviceProfile.
     */
    public void setDeviceProfile(Object deviceProfile) {
        this.mDeviceProfile = deviceProfile;
    }

    /**
     * Obtiene la referencia a la instancia actual de DeviceProfile.
     */
    public Object getDeviceProfile() {
        return mDeviceProfile;
    }

    /**
     * Obtiene el número de íconos que se muestran actualmente en el Hotseat (dock) 
     * basado en el perfil del dispositivo.
     */
    public int getNumShownHotseatIcons() {
        if (mDeviceProfile == null) return mSettings.taskbarIconCount;
        Object hotSeatProfile = getObjectField(mDeviceProfile, "mHotseatProfile");
        return getIntField(hotSeatProfile, "numShownIcons");
    }

    /**
     * Obliga a la actualización y caché de la tarea superior en ejecución.
     */
    public void updateCurrentTopTask() {
        if (TopTaskTrackerClass == null) return;
        Object INSTANCE = getStaticObjectField(TopTaskTrackerClass.getClazz(), "INSTANCE");
        Object topTaskTracker = callMethod(INSTANCE, "get", mContext);
        mCurrentTopTask = callMethod(topTaskTracker, "getCachedTopTask", true, mContext.getDisplay().getDisplayId());
    }

    /**
     * Obtiene la tarea superior en ejecución almacenada en caché.
     */
    public Object getCurrentTopTask() {
        return mCurrentTopTask;
    }

    /**
     * Comprueba si el usuario se encuentra actualmente fuera de la pantalla de inicio (Home Screen).
     * @return true si la tarea superior no es el Home.
     */
    public boolean notInHomeScreen() {
        if (mCurrentTopTask == null) return true;
        return !((boolean) callMethod(mCurrentTopTask, "isHomeTask"));
    }

    @SuppressWarnings("unused")
    public void onComputeInternalInsets(Object thisObject, Object internalInsetsInfo) {
        Object mOverlayController = getObjectField(getObjectField(thisObject, "mContainer"), "mOverlayController");
        Object mOverlayContext = getObjectField(mOverlayController, "mOverlayContext");
        if (mOverlayContext == null) return;
        Object mDragController = getObjectField(mOverlayContext, "mDragController");
        Object mTaskbarContext = getObjectField(mOverlayController, "mTaskbarContext");
        Object mControllers = getObjectField(mTaskbarContext, "mControllers");
        Object taskbarDragController = getObjectField(mControllers, "taskbarDragController");
        if ((mOverlayContext == null || !de.robv.android.xposed.XposedHelpers.getBooleanField(mDragController, "mIsSystemDragInProgress")) && !de.robv.android.xposed.XposedHelpers.getBooleanField(taskbarDragController, "mIsSystemDragInProgress")) {
            return;
        }
        callMethod(getIntField(internalInsetsInfo, "touchableRegion"), "setEmpty");
        callMethod(internalInsetsInfo, "setTouchableInsets", 3); //from android.view.ViewTreeObserver
    }
}
