cp app\release\PixelTaskbarEnabler.apk MagiskModBase\system\priv-app\PixelTaskbarEnabler

cd MagiskModBase

zip -r -9 -q ..\PixelTaskbarEnabler.zip *.*

rm -Rf system\priv-app\PixelTaskbarEnabler\PixelTaskbarEnabler.apk