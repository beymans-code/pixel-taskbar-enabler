package dev.beyman.pixeltaskbarenabler;

import static dev.beyman.pixeltaskbarenabler.BuildConfig.APPLICATION_ID;

import android.content.Context;
import android.content.Intent;

import java.util.Arrays;
import java.util.List;

import dev.beyman.pixeltaskbarenabler.xposed.utils.BootLoopProtector;

public final class Constants {
	public static final String ACTION_HOME = APPLICATION_ID + ".ACTION_HOME";
	public static final String ACTION_BACK = APPLICATION_ID + ".ACTION_BACK";
	public static final String ACTION_SLEEP = APPLICATION_ID + ".ACTION_SLEEP";
	public static final String ACTION_SWITCH_APP_PROFILE = APPLICATION_ID + ".ACTION_SWITCH_APP_PROFILE";
	public static final String ACTION_PROFILE_SWITCH_AVAILABLE = APPLICATION_ID + ".ACTION_PROFILE_SWITCH_AVAILABLE";
	public static final String ACTION_CHECK_XPOSED_ENABLED = APPLICATION_ID + ".ACTION_CHECK_XPOSED_ENABLED";
	public static final String ACTION_XPOSED_CONFIRMED = APPLICATION_ID + ".ACTION_XPOSED_CONFIRMED";
	public static final String ACTION_KSU_ACQUIRE_ROOT = APPLICATION_ID + "ACTION_KSU_ACQUIRE_ROOT";

	public static String DEFAULT_PREFS_FILE_NAME = BuildConfig.APPLICATION_ID + "_preferences";


	public static final String LAUNCHER_PACKAGE = "com.google.android.apps.nexuslauncher";

	public static final String LAUNCH_REASON_EXTRA = "LAUNCH_REASON";
	public static final String LAUNCH_REASON_XPOSED_SERVICE_FAIL = "XPOSED_SERVICE_FAIL";
	public static final String PX_ROOT_EXTRA = "EXTRA_PX_ROOT";


	public static final List<String> PREF_UPDATE_EXCLUSIONS = Arrays.asList(BootLoopProtector.LOAD_TIME_KEY_KEY, BootLoopProtector.PACKAGE_STRIKE_KEY_KEY);


	public static Intent getAppProfileSwitchIntent()
	{
		return new Intent()
				.setAction(Constants.ACTION_SWITCH_APP_PROFILE)
				.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
	}

}
