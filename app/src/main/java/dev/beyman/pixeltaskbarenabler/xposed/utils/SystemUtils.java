package dev.beyman.pixeltaskbarenabler.xposed.utils;

import static android.content.res.Configuration.UI_MODE_NIGHT_YES;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.UserManager;

import androidx.annotation.Nullable;
import org.jetbrains.annotations.Contract;

import dev.beyman.pixeltaskbarenabler.BuildConfig;
import dev.beyman.pixeltaskbarenabler.xposed.XPLauncher;

public class SystemUtils {

	@SuppressLint("StaticFieldLeak")
	static SystemUtils instance;
	Context mContext;
	private UserManager mUserManager;

	public SystemUtils(Context context) {
		mContext = context;
		instance = this;
	}

	public static void restart(String what) {
		switch (what.toLowerCase())
		{
			case "system":
				runRootCommand("am start -a android.intent.action.REBOOT");
				break;
			case "zygote":
			case "android":
				runRootCommand("kill $(pidof zygote)");
				runRootCommand("kill $(pidof zygote64)");
				break;
			case "bootloader":
				runRootCommand("reboot bootloader");
				break;
			default:
				runRootCommand(String.format("killall %s", what));
		}
	}

	private static void runRootCommand(String command)
	{
		XPLauncher.enqueueProxyCommand(proxy -> {
			try {
				proxy.runRootCommand(command);
			} catch (Throwable ignored) {}
		});
	}

	@Nullable
	@Contract(pure = true)
	public static UserManager UserManager() {
		return instance == null
				? null
				: instance.getUserManager();
	}

	private UserManager getUserManager() {
		if (mUserManager == null) {
			try {
				mUserManager = (UserManager) mContext.getSystemService(Context.USER_SERVICE);
			} catch (Throwable t) {
				// ignored
			}
		}
		return mUserManager;
	}

	public static void threadSleep(int millis)
	{
		try {
			Thread.sleep(millis);
		} catch (Throwable ignored) {}
	}

	public static boolean isDarkMode() {
		return instance != null
				&& instance.getIsDark();
	}

	private boolean getIsDark() {
		return (mContext.getResources().getConfiguration().uiMode & UI_MODE_NIGHT_YES) == UI_MODE_NIGHT_YES;
	}

	static boolean darkSwitching = false;

	public static void doubleToggleDarkMode() {
		XPLauncher.enqueueProxyCommand(proxy -> {
			boolean isDark = isDarkMode();
			new Thread(() -> {
				try {
					while (darkSwitching) {
						Thread.currentThread().wait(100);
					}
					darkSwitching = true;

					proxy.runRootCommand("cmd uimode night " + (isDark ? "no" : "yes"));
					threadSleep(1000);
					proxy.runRootCommand("cmd uimode night " + (isDark ? "yes" : "no"));

					threadSleep(500);
					darkSwitching = false;
				} catch (Exception ignored) {
				}
			}).start();
		});
	}

	public static int idOf(String name) {
		return resourceIdOf(name, "id");
	}
	
	public static int dimenIdOf(String name)
	{
		return resourceIdOf(name, "dimen");
	}

	public static int resourceIdOf(String name, String type)
	{
		return instance.resourceIdOfInternal(name, type);
	}

	@SuppressLint("DiscouragedApi")
	private int resourceIdOfInternal(String name, String type)
	{
		return mContext.getResources().getIdentifier(name, type, mContext.getPackageName());
	}
}
