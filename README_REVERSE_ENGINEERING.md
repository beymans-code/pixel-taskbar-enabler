# Guía de Ingeniería Inversa: Pixel Launcher AI GENERATED (Taskbar Injection)

Esta guía explica paso a paso cómo se analizó el Pixel Launcher para inyectar el Taskbar (barra de tareas de tablet) en teléfonos Pixel, y cómo replicar este proceso para adaptarlo a futuras versiones de Android (como Android 17 y posteriores).

## 1. Extracción del APK del Launcher
Cada vez que Google lanza una nueva Beta o versión de Android, el Pixel Launcher sufre cambios. El primer paso es extraer el APK original del dispositivo.

1. Conecta tu dispositivo con depuración USB habilitada.
2. Encuentra la ruta del APK del Launcher:
   ```bash
   adb shell pm path com.google.android.apps.nexuslauncher
   ```
3. Descarga el APK a tu computadora:
   ```bash
   adb pull /ruta/del/paso/anterior/base.apk "Pixel Launcher.apk"
   ```

## 2. Descompilación del APK
Para entender qué cambió, necesitamos descompilar el APK a código fuente Java legible. La herramienta estándar para esto es **JADX**.

1. Descarga e instala [JADX](https://github.com/skylot/jadx).
2. Ejecuta la descompilación desde la terminal:
   ```bash
   jadx "Pixel Launcher.apk" -d "jadx_out_pixel"
   ```
3. Alternativamente, puedes usar **jadx-gui** para abrir el APK directamente y navegar por el código de forma visual, lo cual es muy recomendable para realizar búsquedas rápidas.

## 3. Identificando las Clases Clave (DeviceProfile)
El Taskbar en Android se activa basándose en las propiedades del dispositivo (si el sistema cree que es una tablet o pantalla grande). En el Pixel Launcher, la clase principal que controla esto es `DeviceProfile` y sus propiedades asociadas.

1. En JADX, busca la clase `com.android.launcher3.DeviceProfile`.
2. Revisa sus **constructores**. En versiones antiguas, el constructor era simple. En Android 17 (CP41), el constructor tiene más de 14 argumentos, e incluye clases nuevas como `DeviceProperties`, `LauncherDisplayInfo`, y genera un `TaskbarProfile`.
3. Busca propiedades como `isTablet`, `isTaskbarPresent`, `isLargeScreen`, y `isPhone`. Observa en qué clases se almacenan (ej. `DeviceProperties` o `TaskbarConfiguration`).

## 4. Analizando Cambios y Ofuscación
A medida que Android avanza, Google ofusca o elimina métodos (usando R8/ProGuard). 
- Si un método que antes usábamos para inyectar (ej. `getLeftCornerRadius`) ya no existe, el hook lanzará un `NoSuchMethodError` y el módulo se detendrá.
- **Paso a paso para versiones nuevas:**
  1. Abre el código fuente descompilado.
  2. Verifica si la clase sigue existiendo con el mismo nombre. Si `DeviceProfile` fue renombrada a algo como `b.a.c.a`, tendrás que buscarla basándote en las variables que contiene. Afortunadamente, Google rara vez ofusca las clases principales del paquete `launcher3`.
  3. Revisa la firma del método que intentamos interceptar (ej. el constructor de `DeviceProfile`). Si ahora requiere 15 argumentos en lugar de 14, debes ajustar el hook de Xposed (`param.args.length`).

## 5. Diseño del Hook en Xposed / LSPosed
Una vez que sabemos cómo funciona el nuevo Launcher, diseñamos el hook en el módulo:

1. **Punto de Inyección:** Usualmente, se hace un hook `beforeConstruction` o `afterConstruction` en `DeviceProfile`.
2. **Mutación de Variables:** 
   ```java
   // Ejemplo: Modificar los argumentos ANTES de que se guarde la configuración
   Object deviceProperties = param.args[2];
   XposedHelpers.setBooleanField(deviceProperties, "isLargeScreen", true);
   XposedHelpers.setBooleanField(deviceProperties, "isPhone", false);
   XposedHelpers.setBooleanField(deviceProperties, "isTaskbarPresent", true);
   ```
3. **Inyección de Dependencias:** Si el Taskbar requiere una nueva instancia de `TaskbarProfile`, busca la clase fábrica (`TaskbarProfile$Factory`), invoca su método estático usando Reflection y reemplaza el argumento del constructor.

## 6. Depuración y Resolución de Problemas (Troubleshooting)
El mayor problema al actualizar módulos para nuevas versiones de Android son las fallas silenciosas.

1. **Usa Toasts para Depurar:** Si la clase no se encuentra o el código se bloquea temprano, los logs a veces no se escriben en LSPosed. Insertar Toasts en pantalla te confirmará de inmediato hasta qué línea llegó tu código.
   ```java
   android.widget.Toast.makeText(mContext, "Llegó aquí!", Toast.LENGTH_SHORT).show();
   ```
2. **Protege tus Hooks (Try-Catch):** Nunca asumas que un método de otra clase existe. Si inyectas múltiples hooks en `onPackageLoaded`, envuelve cada uno en un bloque `try-catch` para que si uno falla (porque Google borró ese método), los demás sigan funcionando.
3. **Mide los Parámetros:** Imprime siempre `param.args.length` cuando hagas hook a un constructor. Google suele añadir o quitar dependencias en cada Beta de Android.

## Resumen del Flujo de Trabajo para el Futuro
Si en **Android 18** el Taskbar deja de funcionar:
1. Extrae el nuevo `Pixel Launcher.apk`.
2. Descompílalo con `jadx`.
3. Busca `DeviceProfile` y `TaskbarProfile`.
4. Mira cómo se construye ahora el objeto de configuración de la pantalla.
5. Ajusta las posiciones de `param.args[...]` en `TaskbarActivator.java` para interceptar y modificar los valores que dicen "soy un teléfono" a "soy una tablet de pantalla grande".
6. Compila, instala y revisa los Toasts/Logs si algo falla.
