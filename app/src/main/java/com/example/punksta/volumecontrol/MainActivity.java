package com.example.punksta.volumecontrol;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.punksta.apps.libs.VolumeControl;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private VolumeControl control;
    private List<TypeListener> volumeListeners = new ArrayList<>();
    private NotificationManager notificationManager;


    private boolean ignoreRequests = false;
    private Handler mHandler = new Handler();

    private SharedPreferences preferences;
    private boolean darkTheme = false;


    private static String THEME_PREF_NAME = "DARK_THEME";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getPreferences(MODE_PRIVATE);
        this.darkTheme = preferences.getBoolean(THEME_PREF_NAME, false);
        setTheme(  this.darkTheme ? R.style.AppTheme_Dark : R.style.AppTheme);
        setContentView(R.layout.activity_main);
        control = new VolumeControl(this.getApplicationContext(), mHandler);
        buildUi();
    }





    private void goToMarket() {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + getPackageName())));
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        //skip
    }

    @Override
    public void onSaveInstanceState(Bundle outState, PersistableBundle outPersistentState) {
        //skip
    }

    private void buildUi() {
        LinearLayout scrollView = findViewById(R.id.audio_types_holder);
        scrollView.removeAllViews();
        LayoutInflater inflater = getLayoutInflater();


        Switch s = findViewById(R.id.dark_theme_switcher);

        s.setChecked(this.darkTheme);
        s.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                MainActivity.this.darkTheme = isChecked;
                preferences.edit().putBoolean(THEME_PREF_NAME, isChecked).apply();
                setTheme(isChecked ? R.style.AppTheme_Dark : R.style.AppTheme);
                recreate();
            }
        });

        findViewById(R.id.rate_app).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    goToMarket();
                } catch (Throwable e) {
                    Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });

        for (final AudioType type : AudioType.values()) {
            View view = inflater.inflate(R.layout.audiu_type_view, scrollView, false);
            final TextView title = view.findViewById(R.id.title);
            final TextView currentValue = view.findViewById(R.id.current_value);
            final SeekBar seekBar = view.findViewById(R.id.seek_bar);

            title.setText(type.displayName);

            seekBar.setMax(control.getMaxLevel(type.audioStreamName));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                seekBar.setMin(control.getMinLevel(type.audioStreamName));
            }
            seekBar.setProgress(control.getLevel(type.audioStreamName));

            final TypeListener volumeListener = new TypeListener(type.audioStreamName) {
                @Override
                public void onChangeIndex(int audioType, int currentLevel, int max) {
                    if (currentLevel < control.getMinLevel(type)) {
                        seekBar.setProgress(control.getMinLevel(type));
                    } else {
                        String str = "" + (currentLevel - control.getMinLevel(type)) + "/" + (max - control.getMinLevel(type));
                        currentValue.setText(str);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                            seekBar.setProgress(currentLevel, true);
                        else
                            seekBar.setProgress(currentLevel);
                    }
                }
            };

            volumeListeners.add(volumeListener);

            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    requireChangeVolume(type, progress);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {

                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {

                }
            });

            if (type.vibrateSettings != null) {
                view.findViewById(R.id.vibrate_text).setVisibility(View.GONE);
                view.findViewById(R.id.vibrate_level).setVisibility(View.GONE);
                view.<Spinner>findViewById(R.id.vibrate_level).setSelection(vibrateSettingToPosition(control.getVibrateType(type.vibrateSettings)));
                view.<Spinner>findViewById(R.id.vibrate_level).setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        int setting = vibrateSettingToValue(position);
                        control.setVibrateSettings(type.vibrateSettings, setting);
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {

                    }
                });
            } else {
                view.findViewById(R.id.vibrate_text).setVisibility(View.GONE);
                view.findViewById(R.id.vibrate_level).setVisibility(View.GONE);
            }

            scrollView.addView(view);
        }
    }

    static int vibrateSettingToValue(int position) {
        switch (position) {
            case 1:
                return AudioManager.VIBRATE_SETTING_OFF;
            case 2:
                return AudioManager.VIBRATE_SETTING_ONLY_SILENT;
            default:
            case 0:
                return AudioManager.VIBRATE_SETTING_ON;
        }
    }


    static int vibrateSettingToPosition(int setting) {
        switch (setting) {
            case AudioManager.VIBRATE_SETTING_OFF:
                return 1;
            case AudioManager.VIBRATE_SETTING_ONLY_SILENT:
                return 2;
            default:
            case AudioManager.VIBRATE_SETTING_ON:
                return 0;
        }
    }

    private Runnable unsetIgnoreRequests = new Runnable() {
        @Override
        public void run() {
            ignoreRequests = false;
        }
    };

    private void requireChangeVolume(AudioType audioType, int volume) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                !notificationManager.isNotificationPolicyAccessGranted()) {
            mHandler.postDelayed(unsetIgnoreRequests, 1000);
            if (!ignoreRequests) {
                Intent intent = new Intent(
                        android.provider.Settings
                                .ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);

                startActivity(intent);
                ignoreRequests = true;
            }
        } else {
            try {
                control.setVolumeLevel(audioType.audioStreamName, volume);
            } catch (Throwable throwable) {
                Toast.makeText(this, throwable.getMessage(), Toast.LENGTH_SHORT).show();
                throwable.printStackTrace();
            }
        }
    }


    @Override
    protected void onStart() {
        super.onStart();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        for (TypeListener listener : volumeListeners)
            control.registerVolumeListener(listener.type, listener, true);
    }

    @Override
    protected void onStop() {
        super.onStop();
        for (TypeListener volumeListener : volumeListeners)
            control.unRegisterVolumeListener(volumeListener.type, volumeListener);
        ignoreRequests = false;
        mHandler.removeCallbacks(unsetIgnoreRequests);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        volumeListeners.clear();
    }

    private abstract class TypeListener implements VolumeControl.VolumeListener {
        public final int type;

        protected TypeListener(int type) {
            this.type = type;
        }
    }


    private void onSilenceModeRequested() {
        for (AudioType a : AudioType.values()) {
            requireChangeVolume(a, control.getMinLevel(a.audioStreamName));
        }
       // control.requestRindgerMode(AudioManager.RINGER_MODE_SILENT);

    }


    private void onFullVolumeModeRequested() {
        for (AudioType a : AudioType.values()) {
            requireChangeVolume(a, control.getMaxLevel(a.audioStreamName));
        }
        // control.requestRindgerMode(AudioManager.RINGER_MODE_NORMAL);

    }


    private void onVibrateModeRequested() {
        for (AudioType a : AudioType.values()) {
            requireChangeVolume(a, control.getMinLevel(a.audioStreamName));
        }
        // control.requestRindgerMode(AudioManager.RINGER_MODE_VIBRATE);

    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.main_menu_silence:
                onSilenceModeRequested();
                return true;
            case R.id.main_menu_full_volume:
                onFullVolumeModeRequested();
                return true;
            case R.id.main_menu_vibrate:
                onVibrateModeRequested();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_screen, menu);
        return true;
    }
}
