package dev.beyman.pixeltaskbarenabler.service;



import android.app.Service;
import android.content.Context;
import android.content.Intent;

import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;

import androidx.annotation.Nullable;

import com.topjohnwu.superuser.Shell;

import java.util.Arrays;
import java.util.List;

import dev.beyman.pixeltaskbarenabler.IPixelTaskbarEnablerProxy;
import dev.beyman.pixeltaskbarenabler.PixelTaskbarEnabler;
import dev.beyman.pixeltaskbarenabler.R;
import dev.beyman.pixeltaskbarenabler.Constants;

public class PixelTaskbarEnablerProxy extends Service {
	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		return new PixelTaskbarEnablerProxyIPC(this);
	}

	class PixelTaskbarEnablerProxyIPC extends IPixelTaskbarEnablerProxy.Stub
	{
		/** @noinspection unused*/
		String TAG = getClass().getSimpleName();

		private final List<String> rootAllowedPacks;
		private final boolean rootGranted;

		private PixelTaskbarEnablerProxyIPC(Context context)
		{
			try {
				Shell.setDefaultBuilder(Shell.Builder.create().setFlags(Shell.FLAG_MOUNT_MASTER));
			}
			catch (Throwable ignored){}
			rootGranted = Shell.getShell().isRoot();

			if(!rootGranted)
			{
				context.sendBroadcast(new Intent(Constants.ACTION_KSU_ACQUIRE_ROOT));
			}

			rootAllowedPacks = Arrays.asList(context.getResources().getStringArray(R.array.root_requirement));
		}

		/** @noinspection RedundantThrows*/
		@Override
		public String[] runRootCommand(String command) throws RemoteException {
			try {
				ensureEnvironment();

				List<String> result = Shell.cmd(command).exec().getOut();
				return result.toArray(new String[0]);
			}
			catch (Throwable t)
			{
				return new String[0];
			}
		}


		private void ensureEnvironment() throws RemoteException {
			if(!rootGranted)
			{
				throw new RemoteException("Root permission denied");
			}

			ensureSecurity(Binder.getCallingUid());
		}

		private void ensureSecurity(int uid) throws RemoteException {
			for (String packageName : getPackageManager().getPackagesForUid(uid)) {
				if(rootAllowedPacks.contains(packageName))
					return;
			}
			throw new RemoteException("You do know you're not supposed to use this service. So...");
		}
	}
}
