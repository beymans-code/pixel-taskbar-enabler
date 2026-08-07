package dev.beyman.pixeltaskbarenabler.xposed.utils;

import static dev.beyman.pixeltaskbarenabler.utils.ExtendedSharedPreferences.IS_PREFS_INITIATED_KEY;

import android.content.Context;

import com.crossbowffs.remotepreferences.RemotePreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class ExtendedRemotePreferences extends RemotePreferences {
	public List<OnSharedPreferenceChangeListener> mOnSharedPreferenceChangeListeners = Collections.synchronizedList(new ArrayList<>());
	boolean mIsPrefsInitiated;

	//must be declared as field or it will be disposed by GC
	OnSharedPreferenceChangeListener l = (sharedPreferences, key) -> {
		if(IS_PREFS_INITIATED_KEY.equals(key))
		{
			mIsPrefsInitiated = getBoolean(IS_PREFS_INITIATED_KEY, false);
		}

		if(mIsPrefsInitiated && !mOnSharedPreferenceChangeListeners.isEmpty())
		{
			mOnSharedPreferenceChangeListeners.forEach(listener -> listener.onSharedPreferenceChanged(sharedPreferences, key));
		}
	};
	private boolean mListenerRegistered = false;

	/** @noinspection unused*/
	public ExtendedRemotePreferences(Context context, String authority, String prefFileName) {
		super(context, authority, prefFileName);
	}

	public ExtendedRemotePreferences(Context context, String authority, String prefFileName, boolean strictMode) {
		super(context, authority, prefFileName, strictMode);
	}

	private void initListener() {
		mIsPrefsInitiated = super.getBoolean(IS_PREFS_INITIATED_KEY, false);

		super.registerOnSharedPreferenceChangeListener(l);

		mListenerRegistered = true;
	}



	@Override
	public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
		if(!mListenerRegistered)
			initListener();

		mOnSharedPreferenceChangeListeners.add(listener);
	}

	@Override
	public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
		mOnSharedPreferenceChangeListeners.remove(listener);
	}
}
