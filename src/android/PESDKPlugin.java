package com.photoeditorsdk.cordova;

import android.app.Activity;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import android.util.Log;

import ly.img.android.PESDK;
import ly.img.android.sdk.models.state.PESDKConfig;
import ly.img.android.sdk.models.constant.Directory;
import ly.img.android.sdk.models.state.EditorLoadSettings;
import ly.img.android.sdk.models.state.EditorSaveSettings;
import ly.img.android.sdk.models.state.manager.SettingsList;
import ly.img.android.ui.activities.ImgLyIntent;
import ly.img.android.ui.activities.PhotoEditorBuilder;
import ly.img.android.sdk.models.config.CropAspectConfig;

public class PESDKPlugin extends CordovaPlugin {

    public static final int PESDK_EDITOR_RESULT = 1;
    public static boolean shouldSave = false;
    private static boolean didInitializeSDK = false;
    private CallbackContext callback = null;

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);

        if (!this.didInitializeSDK) {
            PESDK.init(cordova.getActivity().getApplication(), "android_license");
            this.didInitializeSDK = true;
        }
    }

    @Override
    public boolean execute(String action, JSONArray data, CallbackContext callbackContext) throws JSONException {
        if (action.equals("present")) {
            // Extract image path
            JSONObject options = data.getJSONObject(0);
            String filepath = options.optString("path", "");
            this.shouldSave = options.optBoolean("shouldSave", false);

            Log.e("PHOTO_EDITOR", String.valueOf(this.shouldSave));
            Log.e("PHOTO_EDITOR", filepath);

            Activity activity = this.cordova.getActivity();
            activity.runOnUiThread(this.present(activity, filepath, callbackContext));
            return true;
        } else {
            return false;
        }
    }

    private Runnable present(final Activity mainActivity, final String filepath, final CallbackContext callbackContext) {
        callback = callbackContext;
        final PESDKPlugin self = this;
        return new Runnable() {
            public void run() {
                if (mainActivity != null && filepath.length() > 0) {
                    SettingsList settingsList = new SettingsList();

                    settingsList
                        .getSettingsModel(EditorLoadSettings.class)
                        .setImageSourcePath(filepath.replace("file://", ""), true) // Load with delete protection true!
                        .getSettingsModel(EditorSaveSettings.class)
                        .setExportDir(Directory.DCIM, "test")
                        .setExportPrefix("result_")
                        .setJpegQuality(80, false)
                        .setSavePolicy(
                            EditorSaveSettings.SavePolicy.KEEP_SOURCE_AND_CREATE_OUTPUT_IF_NECESSARY
                        );

                    cordova.setActivityResultCallback(self);
                    new PhotoEditorBuilder(mainActivity)
                            .setSettingsList(settingsList)
                            .startActivityForResult(mainActivity, PESDK_EDITOR_RESULT);
                } else {
                    // Just open the camera
                    Intent intent = new Intent(mainActivity, CameraPreviewActivity.class);
                    callback = callbackContext;
                    cordova.startActivityForResult(self, intent, PESDK_EDITOR_RESULT);
                }
            }
        };
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        if (requestCode == PESDK_EDITOR_RESULT) {
            switch (resultCode){
                case Activity.RESULT_OK:
                    success(data);
                    break;
                case Activity.RESULT_CANCELED:
                    callback.error(""); // empty string signals cancellation
                    break;
                default:
                    callback.error("Media error (code " + resultCode + ")");
                    break;
            }
        }
    }

    private void success(Intent data) {
        String path = data.getStringExtra(ImgLyIntent.RESULT_IMAGE_PATH);

        if (this.shouldSave) {
            File mMediaFolder = new File(path);

            MediaScannerConnection.scanFile(cordova.getActivity().getApplicationContext(),
                    new String[]{mMediaFolder.getAbsolutePath()},
                    null,
                    new MediaScannerConnection.OnScanCompletedListener() {
                        public void onScanCompleted(String path, Uri uri) {
                            if (uri == null) {
                                callback.error("Media saving failed.");
                            } else {
                                try {
                                    JSONObject json = new JSONObject();
                                    json.put("url", Uri.fromFile(new File(path)));
                                    callback.success(json);
                                } catch (Exception e) {
                                    callback.error(e.getMessage());
                                }
                            }
                        }
                    }
            );
        } else {
            try {
                JSONObject json = new JSONObject();
                json.put("url", Uri.fromFile(new File(path)));
                callback.success(json);
            } catch (Exception e) {
                callback.error(e.getMessage());
            }
        }
    }

}
