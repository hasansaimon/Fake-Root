package com.rootme;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.*;
import android.util.Log;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final String TAG = "RootMe";
    private static final int PICK_APK_REQUEST = 100;

    private TextView tvStatus;
    private Button btnSetup, btnPatch, btnDownload, btnInstalledApps;
    private ProgressBar progressBar;
    private ScrollView scrollView;

    // -----------------------------------------------------------
    //  LIFECYCLE
    // -----------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus        = findViewById(R.id.tvStatus);
        btnSetup        = findViewById(R.id.btnSetup);
        btnPatch        = findViewById(R.id.btnPatch);
        btnDownload     = findViewById(R.id.btnDownload);
        btnInstalledApps = findViewById(R.id.btnInstalledApps);
        progressBar     = findViewById(R.id.progressBar);
        scrollView      = findViewById(R.id.scrollView);

        updateStatus("Initializing...");

        if (!isTermuxInstalled()) {
            updateStatus("❌ Termux not installed\n\nPlease install Termux from F-Droid first.");
            btnSetup.setEnabled(false);
            btnPatch.setEnabled(false);
            btnDownload.setEnabled(false);
            btnInstalledApps.setEnabled(false);
        } else {
            updateStatus("✅ Termux detected\n\nReady to proceed.");
        }

        btnSetup.setOnClickListener(v -> handleSetup());
        btnPatch.setOnClickListener(v -> pickApkFile());
        btnInstalledApps.setOnClickListener(v -> showInstalledAppsPicker());
        btnDownload.setOnClickListener(v -> updateStatus("Download feature coming soon"));
    }

    // -----------------------------------------------------------
    //  TERMUX SETUP  (officially supported RUN_COMMAND intent)
    // -----------------------------------------------------------

    private void handleSetup() {
        btnSetup.setEnabled(false);
        showProgress(true);
        updateStatus("⏳ Copying setup script…");

        try {
            File scriptFile = getSharedFile("setup_rootspoofer.sh");
            copyAssetToFile("setup_rootspoofer.sh", scriptFile);
            scriptFile.setReadable(true, false);
            scriptFile.setExecutable(true, false);

            updateStatus("⏳ Launching Termux…\n\nWait for Termux to open.");

            launchTermuxCommand(scriptFile.getAbsolutePath(), null, null);

            updateStatus("✅ Setup started in Termux\n\nCheck Termux for progress.\nThis may take 10–30 minutes.");
        } catch (IOException e) {
            Log.e(TAG, "Setup error", e);
            updateStatus("❌ Error: " + e.getMessage());
            btnSetup.setEnabled(true);
        } finally {
            showProgress(false);
        }
    }

    // -----------------------------------------------------------
    //  APK FILE PICKER
    // -----------------------------------------------------------

    private void pickApkFile() {
        btnPatch.setEnabled(false);
        updateStatus("⏳ Select APK to patch…");

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        // Broad MIME – many file managers do not recognise
        // application/vnd.android.package-archive
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES,
            new String[]{"application/vnd.android.package-archive"});
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent, PICK_APK_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != PICK_APK_REQUEST) return;

        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            updateStatus("❌ No APK selected");
            btnPatch.setEnabled(true);
            return;
        }

        Uri uri = data.getData();

        // Persist read permission so the URI works across device reboots
        try {
            final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
            getContentResolver().takePersistableUriPermission(uri, takeFlags);
        } catch (SecurityException e) {
            Log.w(TAG, "Could not persist URI permission", e);
            // Non-fatal – permission still lasts for the current session
        }

        handleApkUri(uri);
    }

    // -----------------------------------------------------------
    //  INSTALLED-APPS PICKER
    // -----------------------------------------------------------

    private void showInstalledAppsPicker() {
        btnInstalledApps.setEnabled(false);
        updateStatus("⏳ Loading installed apps…");

        new Thread(() -> {
            try {
                PackageManager pm = getPackageManager();
                List<ApplicationInfo> installedApps =
                    pm.getInstalledApplications(PackageManager.GET_META_DATA);
                List<AppInfo> apps = new ArrayList<>();

                for (ApplicationInfo app : installedApps) {
                    if (app.publicSourceDir != null
                        && app.publicSourceDir.endsWith(".apk")
                        && (app.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                        // Exclude system apps – they have non-standard APK layouts
                        apps.add(new AppInfo(
                            app.loadLabel(pm).toString(),
                            app.packageName,
                            app.publicSourceDir
                        ));
                    }
                }

                apps.sort((a, b) -> a.label.compareToIgnoreCase(b.label));

                runOnUiThread(() -> showAppSelectionDialog(apps));
            } catch (Exception e) {
                Log.e(TAG, "Error loading installed apps", e);
                runOnUiThread(() -> {
                    updateStatus("❌ Error loading installed apps: " + e.getMessage());
                    btnInstalledApps.setEnabled(true);
                });
            }
        }).start();
    }

    private void showAppSelectionDialog(List<AppInfo> apps) {
        btnInstalledApps.setEnabled(true);

        String[] displayNames = new String[apps.size()];
        for (int i = 0; i < apps.size(); i++) {
            displayNames[i] = apps.get(i).label + " (" + apps.get(i).packageName + ")";
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog);
        builder.setTitle("Select Installed App");
        builder.setItems(displayNames, (dialog, which) -> {
            AppInfo selected = apps.get(which);
            copyInstalledAppApk(selected);
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void copyInstalledAppApk(AppInfo app) {
        btnInstalledApps.setEnabled(false);
        showProgress(true);
        updateStatus("⏳ Copying APK for " + app.label + "…\n\nPath: " + app.apkPath);

        new Thread(() -> {
            try {
                File sourceFile = new File(app.apkPath);
                String filename = "target_" + app.packageName
                    + "_" + System.currentTimeMillis() + ".apk";
                File outFile = getSharedFile(filename);

                copyFile(sourceFile, outFile);
                outFile.setReadable(true, false);

                long sizeMb = outFile.length() / 1024 / 1024;

                runOnUiThread(() -> {
                    updateStatus("✅ " + app.label + " APK copied (" + sizeMb + "MB)"
                        + "\n\nLaunching Termux…");

                    launchTermuxCommand(
                        "/data/data/com.termux/files/usr/bin/bash",
                        new String[]{
                            "-c",
                            "cd $HOME/rootspoofer && bash rootspoofer.sh "
                                + outFile.getAbsolutePath()
                        },
                        outFile.getParentFile().getAbsolutePath()
                    );
                    btnInstalledApps.setEnabled(true);
                });
            } catch (IOException e) {
                Log.e(TAG, "Error copying installed-app APK", e);
                runOnUiThread(() -> {
                    updateStatus("❌ Error: " + e.getMessage());
                    btnInstalledApps.setEnabled(true);
                });
            } finally {
                runOnUiThread(() -> showProgress(false));
            }
        }).start();
    }

    // -----------------------------------------------------------
    //  URI APK HANDLER  (from ACTION_OPEN_DOCUMENT)
    // -----------------------------------------------------------

    private void handleApkUri(Uri uri) {
        showProgress(true);
        updateStatus("⏳ Copying APK…");

        new Thread(() -> {
            try {
                String filename  = getFilenameFromUri(uri);
                File   outFile   = getSharedFile("target_" + System.currentTimeMillis() + "_" + filename);

                InputStream  in  = getContentResolver().openInputStream(uri);
                if (in == null) {
                    runOnUiThread(() -> {
                        updateStatus("❌ Could not read APK file");
                        btnPatch.setEnabled(true);
                    });
                    return;
                }

                FileOutputStream out = new FileOutputStream(outFile);
                byte[] buf = new byte[8192];
                int len;
                long totalSize = 0;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                    totalSize += len;
                }
                out.close();
                in.close();

                outFile.setReadable(true, false);
                long sizeMb = totalSize / 1024 / 1024;

                runOnUiThread(() -> {
                    updateStatus("✅ APK copied (" + sizeMb + "MB)"
                        + "\n\nLaunching Termux…\nCheck Termux for progress.");

                    launchTermuxCommand(
                        "/data/data/com.termux/files/usr/bin/bash",
                        new String[]{
                            "-c",
                            "cd $HOME/rootspoofer && bash rootspoofer.sh "
                                + outFile.getAbsolutePath()
                        },
                        outFile.getParentFile().getAbsolutePath()
                    );
                    btnPatch.setEnabled(true);
                });
            } catch (IOException e) {
                Log.e(TAG, "Error handling APK URI", e);
                runOnUiThread(() -> {
                    updateStatus("❌ Error: " + e.getMessage());
                    btnPatch.setEnabled(true);
                });
            } finally {
                runOnUiThread(() -> showProgress(false));
            }
        }).start();
    }

    // -----------------------------------------------------------
    //  TERMUX RUN_COMMAND
    // -----------------------------------------------------------

    private void launchTermuxCommand(String path, String[] args, String workdir) {
        Intent intent = new Intent("com.termux.RUN_COMMAND");
        intent.setClassName("com.termux", "com.termux.app.RunCommandService");
        intent.putExtra("com.termux.RUN_COMMAND_PATH", path);
        if (args != null) {
            intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", args);
        }
        if (workdir != null) {
            intent.putExtra("com.termux.RUN_COMMAND_WORKDIR", workdir);
        }
        intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", false);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch Termux RUN_COMMAND", e);
            updateStatus("❌ Failed to launch Termux: " + e.getMessage()
                + "\n\nMake sure Termux v0.118+ is installed.");
        }
    }

    // -----------------------------------------------------------
    //  FILE HELPERS
    // -----------------------------------------------------------

    /**
     * Returns a writable file in a location accessible to both our app and
     * Termux.  We try Downloads (shared external storage) first; if that is
     * not available we fall back to our cache directory (with world-readable
     * bits set so Termux can still read it).
     */
    private File getSharedFile(String filename) throws IOException {
        // Try shared Downloads directory
        File dir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS);
        File file = new File(dir, filename);
        try {
            if (!dir.exists()) dir.mkdirs();
            if (file.exists()) file.delete();
            file.createNewFile();
            return file;
        } catch (IOException e) {
            Log.w(TAG, "Cannot write to Downloads, falling back to cache dir", e);
        }

        // Fallback: app cache + world-readable flags
        dir = getCacheDir();
        dir.setExecutable(true, false);       // make cache dir traversable
        file = new File(dir, filename);
        if (file.exists()) file.delete();
        file.createNewFile();
        file.setReadable(true, false);
        return file;
    }

    private void copyAssetToFile(String assetName, File outFile) throws IOException {
        InputStream  in  = getAssets().open(assetName);
        FileOutputStream out = new FileOutputStream(outFile);
        byte[] buf = new byte[8192];
        int len;
        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        out.close();
        in.close();
    }

    private void copyFile(File src, File dst) throws IOException {
        FileInputStream  in  = new FileInputStream(src);
        FileOutputStream out = new FileOutputStream(dst);
        byte[] buf = new byte[8192];
        int len;
        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        out.close();
        in.close();
    }

    private String getFilenameFromUri(Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try {
                String[] projection = {"_display_name"};
                var cursor = getContentResolver().query(uri, projection, null, null, null);
                if (cursor != null) {
                    int index = cursor.getColumnIndex("_display_name");
                    if (index >= 0 && cursor.moveToFirst()) {
                        result = cursor.getString(index);
                    }
                    cursor.close();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting filename from URI", e);
            }
        }
        if (result == null) {
            result = uri.getPath();
            if (result != null) {
                int cut = result.lastIndexOf('/');
                if (cut != -1) {
                    result = result.substring(cut + 1);
                }
            }
        }
        return result != null ? result : "unknown.apk";
    }

    // -----------------------------------------------------------
    //  MISC HELPERS
    // -----------------------------------------------------------

    private boolean isTermuxInstalled() {
        try {
            getPackageManager().getPackageInfo("com.termux", 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void updateStatus(String message) {
        runOnUiThread(() -> {
            tvStatus.setText(message);
            scrollView.post(() ->
                scrollView.fullScroll(ScrollView.FOCUS_DOWN));
        });
    }

    private void showProgress(boolean visible) {
        runOnUiThread(() ->
            progressBar.setVisibility(visible ? View.VISIBLE : View.GONE));
    }

    // -----------------------------------------------------------
    //  DATA HOLDER
    // -----------------------------------------------------------

    private static class AppInfo {
        final String label;
        final String packageName;
        final String apkPath;

        AppInfo(String label, String packageName, String apkPath) {
            this.label       = label;
            this.packageName = packageName;
            this.apkPath     = apkPath;
        }
    }
}
