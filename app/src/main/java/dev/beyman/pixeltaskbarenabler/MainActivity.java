package dev.beyman.pixeltaskbarenabler;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.materialswitch.MaterialSwitch;

import dev.beyman.pixeltaskbarenabler.utils.AppUtils;

public class MainActivity extends AppCompatActivity {

    // =========================================================================
    // MARK: ENUMS & INNER CLASSES
    // =========================================================================

    public enum VibrateType {
        CLOCK_TICK,
        VIRTUAL_KEY
    }

    private static class SettingsState {
        String taskBarMode;
        boolean mobileRecents;
        int taskbarIconScale;
        int gridHeaderScale;
        int taskbarIconCount;

        SettingsState(String taskBarMode, boolean mobileRecents, int taskbarIconScale, int gridHeaderScale, int taskbarIconCount) {
            this.taskBarMode = taskBarMode;
            this.mobileRecents = mobileRecents;
            this.taskbarIconScale = taskbarIconScale;
            this.gridHeaderScale = gridHeaderScale;
            this.taskbarIconCount = taskbarIconCount;
        }
    }

    // =========================================================================
    // MARK: CLASS VARIABLES & FIELDS
    // =========================================================================

    // UI Elements
    private TextView logTextView;
    private androidx.core.widget.NestedScrollView logScrollView;
    private boolean isFabExpanded = false;

    // Formatting

    // Storage
    private SharedPreferences prefsNormal;
    private SharedPreferences prefsProtected;
    private LogManager logManager;

    // State Management (Undo/Redo)
    private final java.util.Stack<SettingsState> undoStack = new java.util.Stack<>();
    private final java.util.Stack<SettingsState> redoStack = new java.util.Stack<>();
    private boolean isRestoringState = false;
    FloatingActionButton btnUndo;
    FloatingActionButton btnRedo;
    FloatingActionButton btnApplyChanges;
    FloatingActionButton fabMainToggle;

    // =========================================================================
    // MARK: LIFECYCLE METHODS
    // =========================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        androidx.core.splashscreen.SplashScreen splashScreen = androidx.core.splashscreen.SplashScreen.installSplashScreen(this);
        final long splashStartTime = System.currentTimeMillis();
        splashScreen.setKeepOnScreenCondition(() -> (System.currentTimeMillis() - splashStartTime) < 2100);
        com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        com.google.android.material.appbar.AppBarLayout appBarLayout = findViewById(R.id.app_bar_layout);
        android.widget.LinearLayout headerTitle = findViewById(R.id.header_title);

        if (appBarLayout != null && headerTitle != null) {
            appBarLayout.addOnOffsetChangedListener((appBarLayout1, verticalOffset) -> {
                float percentage = Math.abs((float) verticalOffset / appBarLayout1.getTotalScrollRange());
                // scale from 1.0f to 0.5f depending on percentage
                float scale = 1.0f - (percentage * 0.3f);
                headerTitle.setScaleX(scale);
                headerTitle.setScaleY(scale);
                headerTitle.setTranslationY(verticalOffset >> 1);

                // Set pivot to the left center so it scales from the start edge
                headerTitle.setPivotX(0f);
                headerTitle.setPivotY(headerTitle.getHeight() / 2f);
            });
        }

        btnUndo = findViewById(R.id.btnUndo);
        btnRedo = findViewById(R.id.btnRedo);
        btnApplyChanges = findViewById(R.id.btnApplyChanges);
        fabMainToggle = findViewById(R.id.fabMainToggle);

        updateUndoRedoButtons();

        fabMainToggle.setOnClickListener(v -> toggleFabGroup(!isFabExpanded));

        if (btnUndo != null) {
            btnUndo.setOnClickListener(v -> {
                if (!undoStack.isEmpty()) {
                    if (currentUIState == null) currentUIState = getUiState();
                    redoStack.push(currentUIState);
                    applyState(undoStack.pop());
                }
            });
        }

        if (btnRedo != null) {
            btnRedo.setOnClickListener(v -> {
                if (!redoStack.isEmpty()) {
                    if (currentUIState == null) currentUIState = getUiState();
                    undoStack.push(currentUIState);
                    applyState(redoStack.pop());
                }
            });
        }

        btnApplyChanges.setOnClickListener(v -> {
            vibrate(v, VibrateType.VIRTUAL_KEY);
            
            MaterialSwitch currentTaskbarSwitch = findViewById(R.id.taskbar_switch);
            MaterialSwitch currentMobileRecentsSwitch = findViewById(R.id.mobile_recents_switch);
            com.google.android.material.slider.Slider currentIconScaleSlider = findViewById(R.id.icon_scale_slider);
            com.google.android.material.slider.Slider currentGridHeaderScaleSlider = findViewById(R.id.grid_header_scale_slider);
            com.google.android.material.slider.Slider currentTaskbarIconCountSlider = findViewById(R.id.taskbar_icon_count_slider);

            String oldTaskBarMode = prefsProtected.getString("taskBarMode", "0");
            int oldIconScale = prefsProtected.getInt("taskbar_icon_scale", 85);
            int oldHeaderScale = prefsProtected.getInt("grid_header_scale", 70);
            int oldIconCount = prefsProtected.getInt("taskbar_icon_count", 4);
            boolean oldMobileRecents = prefsProtected.getBoolean("mobile_recents", false);

            String newTaskBarMode = currentTaskbarSwitch.isChecked() ? "1" : "0";
            int newIconScale = (int) currentIconScaleSlider.getValue();
            int newHeaderScale = (int) currentGridHeaderScaleSlider.getValue();
            int newIconCount = (int) currentTaskbarIconCountSlider.getValue();
            boolean newMobileRecents = currentMobileRecentsSwitch.isChecked();

            SharedPreferences.Editor editorNormal = prefsNormal.edit();
            SharedPreferences.Editor editorProtected = prefsProtected.edit();
            boolean changed = false;

            if (!newTaskBarMode.equals(oldTaskBarMode)) {
                editorNormal.putString("taskBarMode", newTaskBarMode).putBoolean("IsPrefsInitiated", true);
                editorProtected.putString("taskBarMode", newTaskBarMode).putBoolean("IsPrefsInitiated", true);
                logMessage(currentTaskbarSwitch.isChecked() ? R.string.taskbar_enabled : R.string.taskbar_disabled);
                changed = true;
            }

            if (newIconScale != oldIconScale) {
                editorNormal.putInt("taskbar_icon_scale", newIconScale);
                editorProtected.putInt("taskbar_icon_scale", newIconScale);
                logMessage(R.string.icon_scale_saved, newIconScale);
                changed = true;
            }

            if (newHeaderScale != oldHeaderScale) {
                editorNormal.putInt("grid_header_scale", newHeaderScale);
                editorProtected.putInt("grid_header_scale", newHeaderScale);
                logMessage(R.string.header_scale_saved, newHeaderScale);
                changed = true;
            }

            if (newIconCount != oldIconCount) {
                editorNormal.putInt("taskbar_icon_count", newIconCount);
                editorProtected.putInt("taskbar_icon_count", newIconCount);
                changed = true;
            }

            if (newMobileRecents != oldMobileRecents) {
                editorNormal.putBoolean("mobile_recents", newMobileRecents);
                editorProtected.putBoolean("mobile_recents", newMobileRecents);
                logMessage(newMobileRecents ? R.string.mobile_recents_enabled : R.string.mobile_recents_disabled);
                changed = true;
            }

            if (changed) {
                editorNormal.apply();
                editorProtected.apply();
                restartLauncher();
            }
            
            setButtonEnabled(false);
        });

        MaterialSwitch taskbarSwitch = findViewById(R.id.taskbar_switch);

        findViewById(R.id.btnShowLogs).setOnClickListener(v -> {
            vibrate(v, VibrateType.VIRTUAL_KEY);
            showLogsBottomSheet();
        });
        findViewById(R.id.btnShowInstructions).setOnClickListener(v -> {
            vibrate(v, VibrateType.VIRTUAL_KEY);
            showInstructionsBottomSheet();
        });

        findViewById(R.id.btnShowAbout).setOnClickListener(v -> {
            vibrate(v, VibrateType.VIRTUAL_KEY);
            showAboutBottomSheet();
        });

        findViewById(R.id.btnChangeIcon).setOnClickListener(v -> {
            vibrate(v, VibrateType.VIRTUAL_KEY);
            showSettingsDialog();
        });

        // Write to both regular and device-protected storage to ensure RemotePreferences reads it regardless of DirectBoot state.
        String prefName = BuildConfig.APPLICATION_ID + "_preferences";
        prefsNormal = getSharedPreferences(prefName, MODE_PRIVATE);
        prefsProtected = createDeviceProtectedStorageContext().getSharedPreferences(prefName, MODE_PRIVATE);

        logManager = new LogManager(this, prefsProtected, prefsNormal);

        // Since logs view is not immediately available, we don't need to populate logTextView here.
        // It will be populated when showLogsBottomSheet() is called.

        // TaskbarActivator reads taskBarMode as String! "1" is ON, "0" is OFF
        String currentMode = prefsProtected.getString("taskBarMode", "0");
        boolean isEnabled = "1".equals(currentMode);

        MaterialSwitch mobileRecentsSwitch = findViewById(R.id.mobile_recents_switch);

        taskbarSwitch.setChecked(isEnabled);
        mobileRecentsSwitch.setEnabled(isEnabled);

        boolean isMobileRecentsEnabled = prefsProtected.getBoolean("mobile_recents", false);
        mobileRecentsSwitch.setChecked(isMobileRecentsEnabled);

        com.google.android.material.slider.Slider iconScaleSlider = findViewById(R.id.icon_scale_slider);
        TextView iconScaleLabel = findViewById(R.id.icon_scale_label);
        int currentScale = prefsProtected.getInt("taskbar_icon_scale", 85);
        iconScaleSlider.setValue(currentScale);
        iconScaleLabel.setText(getString(R.string.icon_scale_label_format, currentScale));
        iconScaleSlider.setEnabled(isEnabled);

        com.google.android.material.slider.Slider gridHeaderScaleSlider = findViewById(R.id.grid_header_scale_slider);
        TextView gridHeaderScaleLabel = findViewById(R.id.grid_header_scale_label);
        int currentGridHeaderScale = prefsProtected.getInt("grid_header_scale", 70);
        gridHeaderScaleSlider.setValue(currentGridHeaderScale);
        gridHeaderScaleLabel.setText(getString(R.string.grid_header_scale_label_format, currentGridHeaderScale));
        gridHeaderScaleSlider.setEnabled(isEnabled);

        com.google.android.material.slider.Slider taskbarIconCountSlider = findViewById(R.id.taskbar_icon_count_slider);
        TextView taskbarIconCountLabel = findViewById(R.id.taskbar_icon_count_label);
        int currentIconCount = prefsProtected.getInt("taskbar_icon_count", 4);
        taskbarIconCountSlider.setValue(currentIconCount);
        taskbarIconCountLabel.setText(getString(R.string.taskbar_icon_count_label_format, currentIconCount));
        taskbarIconCountSlider.setEnabled(isEnabled);

        setupSliderTouchInterception(iconScaleSlider);
        setupSliderTouchInterception(gridHeaderScaleSlider);
        setupSliderTouchInterception(taskbarIconCountSlider);

        taskbarSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            vibrate(buttonView, VibrateType.CLOCK_TICK);
            if (isRestoringState) return;
            saveStateForUndo();

            mobileRecentsSwitch.setEnabled(isChecked);
            iconScaleSlider.setEnabled(isChecked);
            gridHeaderScaleSlider.setEnabled(isChecked);
            taskbarIconCountSlider.setEnabled(isChecked);

            String currentVal = prefsProtected.getString("taskBarMode", "0");
            boolean currentlyEnabled = "1".equals(currentVal);
            if (isChecked != currentlyEnabled) {
                setButtonEnabled(true);
            }
            updateCurrentUIState();
        });

        iconScaleSlider.addOnChangeListener((slider, value, fromUser) -> {
            iconScaleLabel.setText(getString(R.string.icon_scale_label_format, (int) value));
            if (fromUser) vibrate(slider, VibrateType.CLOCK_TICK);
        });

        iconScaleSlider.addOnSliderTouchListener(new com.google.android.material.slider.Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(@androidx.annotation.NonNull com.google.android.material.slider.Slider slider) {
                saveStateForUndo();
            }

            @Override
            public void onStopTrackingTouch(@androidx.annotation.NonNull com.google.android.material.slider.Slider slider) {
                vibrate(slider, VibrateType.CLOCK_TICK);
                int val = (int) slider.getValue();
                int currentVal = prefsProtected.getInt("taskbar_icon_scale", 85);
                if (val != currentVal) {
                    setButtonEnabled(true);
                }
                updateCurrentUIState();
            }
        });

        gridHeaderScaleSlider.addOnChangeListener((slider, value, fromUser) -> {
            gridHeaderScaleLabel.setText(getString(R.string.grid_header_scale_label_format, (int) value));
            if (fromUser) vibrate(slider, VibrateType.CLOCK_TICK);
        });

        gridHeaderScaleSlider.addOnSliderTouchListener(new com.google.android.material.slider.Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(@androidx.annotation.NonNull com.google.android.material.slider.Slider slider) {
                saveStateForUndo();
            }

            @Override
            public void onStopTrackingTouch(@androidx.annotation.NonNull com.google.android.material.slider.Slider slider) {
                vibrate(slider, VibrateType.CLOCK_TICK);
                int val = (int) slider.getValue();
                int currentVal = prefsProtected.getInt("grid_header_scale", 70);
                if (val != currentVal) {
                    setButtonEnabled(true);
                }
                updateCurrentUIState();
            }
        });

        taskbarIconCountSlider.addOnChangeListener((slider, value, fromUser) -> {
            taskbarIconCountLabel.setText(getString(R.string.taskbar_icon_count_label_format, (int) value));
            if (fromUser) vibrate(slider, VibrateType.CLOCK_TICK);
        });

        taskbarIconCountSlider.addOnSliderTouchListener(new com.google.android.material.slider.Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(@androidx.annotation.NonNull com.google.android.material.slider.Slider slider) {
                saveStateForUndo();
            }

            @Override
            public void onStopTrackingTouch(@androidx.annotation.NonNull com.google.android.material.slider.Slider slider) {
                vibrate(slider, VibrateType.CLOCK_TICK);
                int val = (int) slider.getValue();
                int currentVal = prefsProtected.getInt("taskbar_icon_count", 4);
                if (val != currentVal) {
                    setButtonEnabled(true);
                }
                updateCurrentUIState();
            }
        });

        mobileRecentsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            vibrate(buttonView, VibrateType.CLOCK_TICK);
            if (isRestoringState) return;
            saveStateForUndo();

            boolean currentVal = prefsProtected.getBoolean("mobile_recents", false);
            if (isChecked != currentVal) {
                setButtonEnabled(true);
            }
            updateCurrentUIState();
        });

        // Accordeons have been removed in favor of static cards.

        androidx.core.widget.NestedScrollView mainScrollView = findViewById(R.id.main_scroll_view);
        if (mainScrollView != null) {
            mainScrollView.setOnScrollChangeListener(new androidx.core.widget.NestedScrollView.OnScrollChangeListener() {
                boolean hitTop = true;
                boolean hitBottom = false;

                @Override
                public void onScrollChange(@androidx.annotation.NonNull androidx.core.widget.NestedScrollView v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
                    boolean canScrollUp = v.canScrollVertically(-1);
                    boolean canScrollDown = v.canScrollVertically(1);

                    if (!canScrollUp && !hitTop) {
                        hitTop = true;
                        vibrate(v, VibrateType.CLOCK_TICK);
                    } else if (canScrollUp) {
                        hitTop = false;
                    }

                    if (!canScrollDown && !hitBottom) {
                        hitBottom = true;
                        vibrate(v, VibrateType.CLOCK_TICK);
                    } else if (canScrollDown) {
                        hitBottom = false;
                    }
                }
            });
        }

        updateHeaderIcon();
        currentUIState = getUiState();
    }

    // =========================================================================
    // MARK: STATE MANAGEMENT & UNDO/REDO LOGIC
    // =========================================================================

    private SettingsState currentUIState;

    private SettingsState getUiState() {
        MaterialSwitch taskbarSwitch = findViewById(R.id.taskbar_switch);
        MaterialSwitch mobileRecentsSwitch = findViewById(R.id.mobile_recents_switch);
        com.google.android.material.slider.Slider iconScaleSlider = findViewById(R.id.icon_scale_slider);
        com.google.android.material.slider.Slider gridHeaderScaleSlider = findViewById(R.id.grid_header_scale_slider);
        com.google.android.material.slider.Slider taskbarIconCountSlider = findViewById(R.id.taskbar_icon_count_slider);

        return new SettingsState(
            taskbarSwitch != null && taskbarSwitch.isChecked() ? "1" : "0",
            mobileRecentsSwitch != null && mobileRecentsSwitch.isChecked(),
            iconScaleSlider != null ? (int) iconScaleSlider.getValue() : 85,
            gridHeaderScaleSlider != null ? (int) gridHeaderScaleSlider.getValue() : 70,
            taskbarIconCountSlider != null ? (int) taskbarIconCountSlider.getValue() : 4
        );
    }

    private void saveStateForUndo() {
        if (isRestoringState) return;
        if (currentUIState == null) currentUIState = getUiState();
        undoStack.push(currentUIState);
        redoStack.clear();
        updateUndoRedoButtons();
    }

    private void updateCurrentUIState() {
        if (!isRestoringState) {
            currentUIState = getUiState();
        }
    }

    private void applyState(SettingsState state) {
        isRestoringState = true;

        MaterialSwitch taskbarSwitch = findViewById(R.id.taskbar_switch);
        MaterialSwitch mobileRecentsSwitch = findViewById(R.id.mobile_recents_switch);
        com.google.android.material.slider.Slider iconScaleSlider = findViewById(R.id.icon_scale_slider);
        com.google.android.material.slider.Slider gridHeaderScaleSlider = findViewById(R.id.grid_header_scale_slider);
        com.google.android.material.slider.Slider taskbarIconCountSlider = findViewById(R.id.taskbar_icon_count_slider);

        boolean isEnabled = "1".equals(state.taskBarMode);
        if (taskbarSwitch != null) taskbarSwitch.setChecked(isEnabled);
        if (mobileRecentsSwitch != null) {
            mobileRecentsSwitch.setChecked(state.mobileRecents);
            mobileRecentsSwitch.setEnabled(isEnabled);
        }

        if (iconScaleSlider != null) {
            iconScaleSlider.setValue(state.taskbarIconScale);
            iconScaleSlider.setEnabled(isEnabled);
        }

        if (gridHeaderScaleSlider != null) {
            gridHeaderScaleSlider.setValue(state.gridHeaderScale);
            gridHeaderScaleSlider.setEnabled(isEnabled);
        }

        if (taskbarIconCountSlider != null) {
            taskbarIconCountSlider.setValue(state.taskbarIconCount);
            taskbarIconCountSlider.setEnabled(isEnabled);
        }

        currentUIState = state;
        isRestoringState = false;

        setButtonEnabled(true);
        updateUndoRedoButtons();
    }

    // =========================================================================
    // MARK: UI METHODS & BOTTOM SHEETS
    // =========================================================================

    private void updateHeaderIcon() {
        android.widget.ImageView headerIcon = findViewById(R.id.header_app_icon);
        if (headerIcon == null) return;

        int index = getCurrentIconIndex();
        if (index == 0) {
            headerIcon.setImageResource(R.mipmap.ic_launcher);
        } else if (index == 1) {
            headerIcon.setImageResource(R.mipmap.ic_launcher_themed);
        } else if (index == 2) {
            headerIcon.setImageResource(R.mipmap.ic_launcher_mono);
        }
    }

    private int getCurrentIconIndex() {
        android.content.ComponentName themedAlias = new android.content.ComponentName(this, getPackageName() + ".MainActivityThemed");
        android.content.ComponentName monoAlias = new android.content.ComponentName(this, getPackageName() + ".MainActivityMono");

        android.content.pm.PackageManager pm = getPackageManager();
        if (pm.getComponentEnabledSetting(monoAlias) == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
            return 2;
        } else if (pm.getComponentEnabledSetting(themedAlias) == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
            return 1;
        }
        return 0;
    }

    private void changeAppIcon(int index) {
        android.content.ComponentName defaultAlias = new android.content.ComponentName(this, getPackageName() + ".MainActivityDefault");
        android.content.ComponentName themedAlias = new android.content.ComponentName(this, getPackageName() + ".MainActivityThemed");
        android.content.ComponentName monoAlias = new android.content.ComponentName(this, getPackageName() + ".MainActivityMono");

        android.content.pm.PackageManager pm = getPackageManager();

        if (index == 0) {
            // Default icon
            pm.setComponentEnabledSetting(defaultAlias,
                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED, android.content.pm.PackageManager.DONT_KILL_APP);
            pm.setComponentEnabledSetting(themedAlias,
                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED, android.content.pm.PackageManager.DONT_KILL_APP);
            pm.setComponentEnabledSetting(monoAlias,
                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED, android.content.pm.PackageManager.DONT_KILL_APP);
            logMessage(R.string.icon_changed_default);
        } else if (index == 1) {
            // Themed icon
            pm.setComponentEnabledSetting(defaultAlias,
                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED, android.content.pm.PackageManager.DONT_KILL_APP);
            pm.setComponentEnabledSetting(themedAlias,
                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED, android.content.pm.PackageManager.DONT_KILL_APP);
            pm.setComponentEnabledSetting(monoAlias,
                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED, android.content.pm.PackageManager.DONT_KILL_APP);
            logMessage(R.string.icon_changed_themed);
        } else if (index == 2) {
            // Monochrome icon
            pm.setComponentEnabledSetting(defaultAlias,
                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED, android.content.pm.PackageManager.DONT_KILL_APP);
            pm.setComponentEnabledSetting(themedAlias,
                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED, android.content.pm.PackageManager.DONT_KILL_APP);
            pm.setComponentEnabledSetting(monoAlias,
                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED, android.content.pm.PackageManager.DONT_KILL_APP);
            logMessage(R.string.icon_changed_monochrome);
        }

        updateHeaderIcon();

        // MARK: Restart app to apply icon changes safely without being killed by the OS unexpectedly
        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        handler.postDelayed(() -> {
            android.content.Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
            if (intent == null) {
                intent = new android.content.Intent(this, MainActivity.class);
            }
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            overrideActivityTransition(android.app.Activity.OVERRIDE_TRANSITION_OPEN, android.R.anim.fade_in, android.R.anim.fade_out);
            finishAffinity();
        }, 1000);
    }

    @android.annotation.SuppressLint("InflateParams")
    private void showSettingsDialog() {
        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_settings, null);

        android.widget.RadioButton radioLangSystem = dialogView.findViewById(R.id.radio_lang_system);
        android.widget.RadioButton radioLangEn = dialogView.findViewById(R.id.radio_lang_en);
        android.widget.RadioButton radioLangEs = dialogView.findViewById(R.id.radio_lang_es);

        android.view.View rowLangSystem = dialogView.findViewById(R.id.row_lang_system);
        android.view.View rowLangEn = dialogView.findViewById(R.id.row_lang_en);
        android.view.View rowLangEs = dialogView.findViewById(R.id.row_lang_es);

        androidx.core.os.LocaleListCompat appLocales = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales();
        String currentLang = appLocales.isEmpty() ? "system" : appLocales.get(0).getLanguage();

        radioLangSystem.setChecked("system".equals(currentLang));
        radioLangEn.setChecked("en".equals(currentLang));
        radioLangEs.setChecked("es".equals(currentLang));

        android.widget.RadioButton radioDefault = dialogView.findViewById(R.id.radio_icon_default);
        android.widget.RadioButton radioThemed = dialogView.findViewById(R.id.radio_icon_themed);
        android.widget.RadioButton radioMono = dialogView.findViewById(R.id.radio_icon_mono);

        android.view.View rowDefault = dialogView.findViewById(R.id.row_default);
        android.view.View rowThemed = dialogView.findViewById(R.id.row_themed);
        android.view.View rowMono = dialogView.findViewById(R.id.row_mono);

        int currentSelection = getCurrentIconIndex();
        radioDefault.setChecked(currentSelection == 0);
        radioThemed.setChecked(currentSelection == 1);
        radioMono.setChecked(currentSelection == 2);

        com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        dialog.setContentView(dialogView);
        dialog.show();

        rowDefault.setOnClickListener(v -> {
            changeAppIcon(0);
            dialog.dismiss();
        });

        rowThemed.setOnClickListener(v -> {
            changeAppIcon(1);
            dialog.dismiss();
        });

        rowMono.setOnClickListener(v -> {
            changeAppIcon(2);
            dialog.dismiss();
        });

        rowLangSystem.setOnClickListener(v -> {
            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(androidx.core.os.LocaleListCompat.getEmptyLocaleList());
            logMessage(R.string.lang_changed);
            dialog.dismiss();
        });

        rowLangEn.setOnClickListener(v -> {
            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(androidx.core.os.LocaleListCompat.forLanguageTags("en"));
            logMessage(R.string.lang_changed);
            dialog.dismiss();
        });

        rowLangEs.setOnClickListener(v -> {
            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(androidx.core.os.LocaleListCompat.forLanguageTags("es"));
            logMessage(R.string.lang_changed);
            dialog.dismiss();
        });
    }

    @android.annotation.SuppressLint("InflateParams")
    private void showLogsBottomSheet() {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        android.view.View view = getLayoutInflater().inflate(R.layout.bottom_sheet_logs, null);
        dialog.setContentView(view);

        logTextView = view.findViewById(R.id.log_text_view);
        logScrollView = view.findViewById(R.id.log_scroll_view);

        logManager.setLogUpdateListener(() -> {
            String updatedLogs = logManager.buildLogString();
            if (!updatedLogs.isEmpty()) {
                updateLogText(updatedLogs);
                if (logScrollView != null) {
                    logScrollView.post(() -> logScrollView.fullScroll(ScrollView.FOCUS_UP));
                }
            } else {
                updateLogText(getString(R.string.no_logs));
            }
        });

        view.findViewById(R.id.btnClearLogs).setOnClickListener(v -> {
            logManager.clearLogs();
            Toast.makeText(this, R.string.logs_cleared, Toast.LENGTH_SHORT).show();
        });

        dialog.setOnDismissListener(d -> {
            logTextView = null;
            logScrollView = null;
            logManager.setLogUpdateListener(null);
        });

        String logsText = logManager.buildLogString();
        if (!logsText.isEmpty()) {
            updateLogText(logsText);
            logScrollView.post(() -> logScrollView.fullScroll(ScrollView.FOCUS_UP));
        } else {
            updateLogText(getString(R.string.no_logs));
        }

        dialog.show();
    }

    @android.annotation.SuppressLint("InflateParams")
    private void showInstructionsBottomSheet() {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        android.view.View view = getLayoutInflater().inflate(R.layout.bottom_sheet_instructions, null);

        android.widget.LinearLayout instructionsContainer = view.findViewById(R.id.instructions_list_container);
        if (instructionsContainer != null) {
            CharSequence[] instructions = {
                getText(R.string.instruction_0),
                getText(R.string.instruction_1),
                getText(R.string.instruction_2),
                getText(R.string.instruction_3),
                getText(R.string.instruction_4)
            };

            for (CharSequence instruction : instructions) {
                android.view.View itemView = getLayoutInflater().inflate(R.layout.item_feature, instructionsContainer, false);
                android.widget.TextView textView = itemView.findViewById(R.id.feature_text);
                if (textView != null) {
                    textView.setText(instruction);
                    textView.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
                }
                instructionsContainer.addView(itemView);
            }
        }

        dialog.setContentView(view);
        dialog.show();
    }

    @android.annotation.SuppressLint("InflateParams")
    private void showAboutBottomSheet() {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        android.view.View view = getLayoutInflater().inflate(R.layout.bottom_sheet_about, null);

        view.findViewById(R.id.btnPrimaryWeb).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://beyman.dev"));
            startActivity(intent);
            dialog.dismiss();
        });

        view.findViewById(R.id.btnCreditPixelXpert).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/siavash79/PixelXpert"));
            startActivity(intent);
            dialog.dismiss();
        });

        view.findViewById(R.id.btnCreditXposed).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/rovo89/XposedBridge"));
            startActivity(intent);
            dialog.dismiss();
        });

        view.findViewById(R.id.btnCreditVector).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/JingMatrix/Vector"));
            startActivity(intent);
            dialog.dismiss();
        });

        android.widget.LinearLayout featuresContainer = view.findViewById(R.id.features_list_container);
        if (featuresContainer != null) {
            String[] features = {
                getString(R.string.feature_1),
                getString(R.string.feature_2),
                getString(R.string.feature_3),
                getString(R.string.feature_4)
            };

            for (String feature : features) {
                android.view.View itemView = getLayoutInflater().inflate(R.layout.item_feature, featuresContainer, false);
                android.widget.TextView textView = itemView.findViewById(R.id.feature_text);
                if (textView != null) {
                    textView.setText(feature);
                }
                featuresContainer.addView(itemView);
            }
        }

        dialog.setContentView(view);
        dialog.show();
    }

    // =========================================================================
    // MARK: UTILITY METHODS
    // =========================================================================

    private void toggleFabGroup(boolean expand) {
        isFabExpanded = expand;

        FloatingActionButton fabMainToggle = findViewById(R.id.fabMainToggle);
        View btnUndo = findViewById(R.id.btnUndo);
        View btnRedo = findViewById(R.id.btnRedo);
        FloatingActionButton btnApplyChanges = findViewById(R.id.btnApplyChanges);

        if (isFabExpanded) {
            fabMainToggle.animate().rotation(135f).setDuration(250).start();

            btnUndo.setVisibility(View.VISIBLE);
            btnRedo.setVisibility(View.VISIBLE);
            btnApplyChanges.setVisibility(View.VISIBLE);

            btnUndo.setScaleX(0f); btnUndo.setScaleY(0f);
            btnRedo.setScaleX(0f); btnRedo.setScaleY(0f);
            btnApplyChanges.setScaleX(0f); btnApplyChanges.setScaleY(0f);

            btnUndo.animate().scaleX(1f).scaleY(1f).setDuration(250).start();
            btnRedo.animate().scaleX(1f).scaleY(1f).setDuration(250).setStartDelay(50).start();
            btnApplyChanges.animate().scaleX(1f).scaleY(1f).setDuration(250).setStartDelay(100).start();
        } else {
            fabMainToggle.animate().rotation(0f).setDuration(250).start();

            btnUndo.animate().scaleX(0f).scaleY(0f).setDuration(250).setStartDelay(100).start();
            btnRedo.animate().scaleX(0f).scaleY(0f).setDuration(250).setStartDelay(50).start();
            btnApplyChanges.animate().scaleX(0f).scaleY(0f).setDuration(250).setStartDelay(0).withEndAction(() -> {
                if (!isFabExpanded) {
                    btnUndo.setVisibility(View.GONE);
                    btnRedo.setVisibility(View.GONE);
                    btnApplyChanges.setVisibility(View.GONE);
                }
            }).start();
        }
    }

    private void vibrate(View v, VibrateType type) {
        int feedback;
        if (type == VibrateType.CLOCK_TICK) {
            feedback = android.view.HapticFeedbackConstants.CLOCK_TICK;
        } else {
            feedback = android.view.HapticFeedbackConstants.VIRTUAL_KEY;
        }
        v.performHapticFeedback(feedback);
    }

    private void restartLauncher() {
        logMessage(R.string.restarting_launcher);
        new Thread(() -> {
            if (com.topjohnwu.superuser.Shell.getShell().isRoot()) {
                com.topjohnwu.superuser.Shell.cmd("chmod -R 777 /data/data/" + BuildConfig.APPLICATION_ID + "/shared_prefs").exec();
                AppUtils.restart("com.google.android.apps.nexuslauncher");
                runOnUiThread(() -> logMessage(R.string.launcher_restarted_success));
            } else {
                runOnUiThread(() -> logMessage(R.string.error_root_denied));
            }
        }).start();
    }

    private void updateLogText(String logs) {
        if (logTextView == null) return;

        if (logs.isEmpty() || logs.equals(getString(R.string.no_logs))) {
            logTextView.setText(R.string.no_logs);
            return;
        }

        android.text.SpannableString spannable = new android.text.SpannableString(logs);
        int firstNewLine = logs.indexOf('\n');
        if (firstNewLine == -1) firstNewLine = logs.length();

        android.util.TypedValue typedValue = new android.util.TypedValue();
        getTheme().resolveAttribute(R.attr.colorTertiary, typedValue, true);
        int colorTertiary = typedValue.data;

        spannable.setSpan(new android.text.style.ForegroundColorSpan(colorTertiary), 0, firstNewLine, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, firstNewLine, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        logTextView.setText(spannable);
    }

    private void logMessage(int resId, Object... args) {
        logManager.logMessage(resId, args);
    }

    private void setButtonEnabled(boolean enabled) {
        // MARK: UPDATE BUTTON SAVE STYLES
        btnApplyChanges.setEnabled(enabled);

        int background = MaterialColors.getColor(btnApplyChanges, com.google.android.material.R.attr.colorOutlineVariant);
        int color = MaterialColors.getColor(btnApplyChanges, com.google.android.material.R.attr.colorOutline);

        if (enabled) {
            background = MaterialColors.getColor(btnApplyChanges, com.google.android.material.R.attr.colorPrimaryFixed);
            color = MaterialColors.getColor(btnApplyChanges, com.google.android.material.R.attr.colorOnPrimaryFixed);
        }

        btnApplyChanges.setBackgroundTintList(ColorStateList.valueOf(background));
        btnApplyChanges.setImageTintList(ColorStateList.valueOf(color));

        if(isFabExpanded == enabled) return;
        toggleFabGroup(enabled);
    }

    private void updateUndoRedoButtons() {
        if (btnUndo == null || btnRedo == null) return;

        int btnUndoBackground = MaterialColors.getColor(btnApplyChanges, com.google.android.material.R.attr.colorOutlineVariant);
        int btnUndoColor = MaterialColors.getColor(btnApplyChanges, com.google.android.material.R.attr.colorOutline);

        int btnRedoBackground = MaterialColors.getColor(btnApplyChanges, com.google.android.material.R.attr.colorOutlineVariant);
        int btnRedoColor = MaterialColors.getColor(btnApplyChanges, com.google.android.material.R.attr.colorOutline);

        if(!undoStack.isEmpty()) {
            btnUndoBackground = MaterialColors.getColor(btnApplyChanges, com.google.android.material.R.attr.colorSecondary);
            btnUndoColor = MaterialColors.getColor(btnApplyChanges, com.google.android.material.R.attr.colorOnSecondary);
        }

        if(!redoStack.isEmpty()) {
            btnRedoBackground = MaterialColors.getColor(btnApplyChanges, com.google.android.material.R.attr.colorTertiary);
            btnRedoColor = MaterialColors.getColor(btnApplyChanges, com.google.android.material.R.attr.colorOnTertiary);
        }

        btnUndo.setEnabled(!undoStack.isEmpty());
        btnRedo.setEnabled(!redoStack.isEmpty());

        btnUndo.setBackgroundTintList(ColorStateList.valueOf(btnUndoBackground));
        btnUndo.setImageTintList(ColorStateList.valueOf(btnUndoColor));
        btnRedo.setBackgroundTintList(ColorStateList.valueOf(btnRedoBackground));
        btnRedo.setImageTintList(ColorStateList.valueOf(btnRedoColor));
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private void setupSliderTouchInterception(com.google.android.material.slider.Slider slider) {
        slider.setOnTouchListener(new android.view.View.OnTouchListener() {
            private float startX;
            private float startY;
            private int touchSlop = android.view.ViewConfiguration.get(slider.getContext()).getScaledTouchSlop();
            private boolean isDragging = false;

            @Override
            public boolean onTouch(android.view.View v, android.view.MotionEvent event) {
                int action = event.getActionMasked();
                switch (action) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        startX = event.getX();
                        startY = event.getY();
                        isDragging = false;
                        return true; // Consume down to prevent instant snap

                    case android.view.MotionEvent.ACTION_MOVE:
                        if (!isDragging) {
                            float dx = Math.abs(event.getX() - startX);
                            float dy = Math.abs(event.getY() - startY);
                            
                            if (dx > touchSlop && dx > dy) {
                                // It's a horizontal drag
                                isDragging = true;
                                v.getParent().requestDisallowInterceptTouchEvent(true);
                                
                                // Synthesize ACTION_DOWN for the slider
                                android.view.MotionEvent downEvent = android.view.MotionEvent.obtain(event);
                                downEvent.setAction(android.view.MotionEvent.ACTION_DOWN);
                                v.onTouchEvent(downEvent);
                                downEvent.recycle();
                            }
                        }
                        
                        if (isDragging) {
                            v.onTouchEvent(event);
                        }
                        return true;

                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL:
                        if (isDragging) {
                            v.onTouchEvent(event);
                            v.getParent().requestDisallowInterceptTouchEvent(false);
                        } else if (action == android.view.MotionEvent.ACTION_UP) {
                            // It was a click!
                            android.view.MotionEvent downEvent = android.view.MotionEvent.obtain(event);
                            downEvent.setAction(android.view.MotionEvent.ACTION_DOWN);
                            v.onTouchEvent(downEvent);
                            downEvent.recycle();
                            v.onTouchEvent(event);
                        }
                        isDragging = false;
                        return true;
                }
                return false;
            }
        });
    }
}
