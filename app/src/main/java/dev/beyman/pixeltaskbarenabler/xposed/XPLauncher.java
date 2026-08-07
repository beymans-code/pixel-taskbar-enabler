package dev.beyman.pixeltaskbarenabler.xposed;

import static android.content.Context.CONTEXT_IGNORE_SECURITY;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.setObjectField;
import static dev.beyman.pixeltaskbarenabler.BuildConfig.APPLICATION_ID;
import static dev.beyman.pixeltaskbarenabler.xposed.XPrefs.Xprefs;
import static dev.beyman.pixeltaskbarenabler.xposed.utils.BootLoopProtector.isBootLooped;

import android.annotation.SuppressLint;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.os.IBinder;
import android.os.RemoteException;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;
import dev.beyman.pixeltaskbarenabler.BuildConfig;
import dev.beyman.pixeltaskbarenabler.Constants;
import dev.beyman.pixeltaskbarenabler.IPixelTaskbarEnablerProxy;
import dev.beyman.pixeltaskbarenabler.R;
import dev.beyman.pixeltaskbarenabler.service.PixelTaskbarEnablerProxy;
import dev.beyman.pixeltaskbarenabler.xposed.utils.SystemUtils;
import dev.beyman.pixeltaskbarenabler.xposed.utils.reflection.ReflectedClass;
import dev.beyman.pixeltaskbarenabler.xposed.utils.toolkit.Logger;

public class XPLauncher extends XposedModule implements ServiceConnection {
	public static String processName = "";
	public static boolean isSystemServer = false;

	public static ArrayList<XposedModPack> runningMods = new ArrayList<>();
	public Context mContext = null;
	@SuppressLint("StaticFieldLeak")
	static XPLauncher instance;

	private CountDownLatch rootProxyCountdown = new CountDownLatch(1);
	private static IPixelTaskbarEnablerProxy rootProxyIPC;
	private static final Queue<ProxyRunnable> proxyQueue = new LinkedList<>();

	public static Resources moduleResources;

	public XPLauncher()
	{
		instance = this;
		Logger.setXposedInterface(this);
	}

	@Override
	public void onModuleLoaded(@NonNull ModuleLoadedParam param) {
		super.onModuleLoaded(param);

		processName = param.getProcessName();
		isSystemServer = param.isSystemServer();
	}

	@Override
	public void onSystemServerStarting(@NonNull XposedModuleInterface.SystemServerStartingParam SSSP)
	{
		ReflectedClass.setFrameworkClassloader(SSSP.getClassLoader());
	}


	@Override
	public void onPackageReady(@NonNull PackageReadyParam PRParam){
		ReflectedClass.setDefaultXposedInterface(this);


		if (isSystemServer) {
			ReflectedClass PhoneWindowManagerClass = ReflectedClass.of("com.android.server.policy.PhoneWindowManager");

			PhoneWindowManagerClass
					.before("init")
					.run(instance,param -> {
						try {
							if (mContext == null) {
								mContext = (Context) param.args[0];

								moduleResources = mContext.createPackageContext(APPLICATION_ID, CONTEXT_IGNORE_SECURITY)
										.getResources();

								XPrefs.init(mContext);

								CompletableFuture.runAsync(() -> waitForXprefsLoad(PRParam));
							}
						} catch (Throwable t) {
							Logger.log(t);
						}
					});
		}

		if(!isSystemServer) {
			ReflectedClass.of(Instrumentation.class)
					.after("newApplication")
					.run(this, param -> {
				try {
					if (mContext == null) {
						mContext = (Context) param.args[param.args.length - 1];

						moduleResources = mContext.createPackageContext(APPLICATION_ID, CONTEXT_IGNORE_SECURITY)
								                  .getResources();

						XPrefs.init(mContext);

						waitForXprefsLoad(PRParam);
					}
				} catch (Throwable t) {
					Logger.log(t);
				}
			});
		}
	}

	private void waitForXprefsLoad(PackageReadyParam PRParam) {
		while (true) {
			try {
				Xprefs.getBoolean("LoadTestBooleanValue", false);
				break;
			} catch (Throwable ignored) {
				SystemUtils.threadSleep(1000);
			}
		}

		Logger.log(String.format("Loading PixelTaskbarEnabler version: %s on %s", BuildConfig.VERSION_NAME, PRParam.getPackageName()));
		try {
			Logger.log("PixelTaskbarEnabler Records: " + Xprefs.getAll().size());
		} catch (Throwable ignored) {
		}

		onXPrefsReady(PRParam);
	}

	private void onXPrefsReady(PackageReadyParam PRParam) {
		if (isBootLooped(PRParam.getPackageName())) {
			Logger.log(String.format("PixelTaskbarEnabler: Possible bootloop in %s. Will not load for now", PRParam.getPackageName()));
			return;
		}

		new SystemUtils(mContext);
		XPrefs.setPackagePrefs(PRParam.getPackageName());

		loadModPacks(PRParam);

		XPrefs.onContentProviderLoaded();
	}

	private void loadModPacks(PackageReadyParam PRParam) {
		ReflectedClass.setDefaultClassloader(PRParam.getClassLoader());

		if (Arrays.asList(moduleResources.getStringArray(R.array.root_requirement)).contains(PRParam.getPackageName())) {
			forceConnectRootService();
		}

		ModPacks.getModPacks()
				.forEach(modPackData -> {
					String partOfProcessName = modPackData.targetsMainProcess ? "" : modPackData.childProcessName;

					if((modPackData.targetPackage.equals(PRParam.getPackageName()) || modPackData.targetPackage.isEmpty())
							   && processName.contains(partOfProcessName))
					{
						//noinspection unchecked
						loadModPack((Class<? extends XposedModPack>) modPackData.clazz, PRParam);
					}
				});
	}

	private void loadModPack(Class<? extends XposedModPack> thisClass, PackageReadyParam PRParam) {
		try {
			XposedModPack instance = thisClass.getConstructor(Context.class).newInstance(mContext);
			try {
				instance.onPreferenceUpdated();
			} catch (Throwable ignored) {
			}

			instance.onPackageLoaded(PRParam);
			runningMods.add(instance);
		} catch (Throwable T) {
			Logger.log("Start Error Dump - Occurred in " + thisClass.getName());
			Logger.log(T);
		}
	}

	private void forceConnectRootService() {
		new Thread(() -> {
			while (SystemUtils.UserManager() == null
					       || !SystemUtils.UserManager().isUserUnlocked()) //device is still CE encrypted
			{
				SystemUtils.threadSleep(2000);
			}
			SystemUtils.threadSleep(5000); //wait for the unlocked account to settle down a bit

			while (rootProxyIPC == null) {
				connectRootService();
				SystemUtils.threadSleep(5000);
			}
		}).start();
	}

	private void connectRootService() {
		try {
			Intent intent = new Intent();
			intent.setComponent(new ComponentName(APPLICATION_ID, PixelTaskbarEnablerProxy.class.getName()));
			mContext.bindService(intent, instance, Context.BIND_AUTO_CREATE | Context.BIND_ADJUST_WITH_ACTIVITY);
		} catch (Throwable t) {
			Logger.log(t);
		}
	}

	@Override
	public void onServiceConnected(ComponentName name, IBinder service) {
		rootProxyIPC = IPixelTaskbarEnablerProxy.Stub.asInterface(service);
		rootProxyCountdown.countDown();

		synchronized (proxyQueue) {
			while (!proxyQueue.isEmpty()) {
				try {
					Objects.requireNonNull(proxyQueue.poll()).run(rootProxyIPC);
				} catch (Throwable ignored) {
				}
			}
		}
	}

	@Override
	public void onServiceDisconnected(ComponentName name) {
		rootProxyIPC = null;

		forceConnectRootService();
	}

	public static IPixelTaskbarEnablerProxy getRootProviderProxy() {
		if (rootProxyIPC == null) {
			instance.rootProxyCountdown = new CountDownLatch(1);
			instance.forceConnectRootService();
			try {
				//noinspection ResultOfMethodCallIgnored
				instance.rootProxyCountdown.await(5, TimeUnit.SECONDS);
			} catch (Throwable ignored) {
			}
		}
		return rootProxyIPC;
	}

	public static void enqueueProxyCommand(ProxyRunnable runnable) {
		if (rootProxyIPC != null) {
			try {
				runnable.run(rootProxyIPC);
			} catch (RemoteException ignored) {
			}
		} else {
			synchronized (proxyQueue) {
				proxyQueue.add(runnable);
			}
			instance.forceConnectRootService();
		}
	}

	public interface ProxyRunnable {
		void run(IPixelTaskbarEnablerProxy proxy) throws RemoteException;
	}
}
