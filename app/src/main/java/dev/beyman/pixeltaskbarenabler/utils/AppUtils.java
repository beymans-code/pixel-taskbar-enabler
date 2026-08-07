package dev.beyman.pixeltaskbarenabler.utils;

import android.content.Intent;
import com.topjohnwu.superuser.Shell;
import dev.beyman.pixeltaskbarenabler.PixelTaskbarEnabler;
import dev.beyman.pixeltaskbarenabler.Constants;

public class AppUtils {
	public static void restart(String what) {
		switch (what.toLowerCase())
		{
			case "system":
				Shell.cmd("am start -a android.intent.action.REBOOT").exec();
				break;
			case "zygote":
			case "android":
				Shell.cmd("kill $(pidof zygote)").submit();
				Shell.cmd("kill $(pidof zygote64)").submit();
				break;
			default:
				Shell.cmd(String.format("killall %s", what)).exec();
		}
	}

	public static void restartSelf(String reason) {
		Intent intent = PixelTaskbarEnabler.get().getBaseContext().getPackageManager()
				                .getLaunchIntentForPackage(PixelTaskbarEnabler.get().getBaseContext().getPackageName());

		if (intent != null) {
			if(reason != null)
			{
				intent.putExtra(Constants.LAUNCH_REASON_EXTRA, reason);
			}
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
			PixelTaskbarEnabler.get().startActivity(intent);
		}

		android.os.Process.killProcess(android.os.Process.myPid());
		System.exit(0);
	}
}
