package com.rootme;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.widget.*;
import android.util.Log;
import java.io.*;

public class MainActivity extends Activity {

    private static final String TAG = "RootMe";
    private TextView tvStatus;
    private Button btnSetup, btnPatch, btnDownload;
    private ScrollView scrollView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);
        btnSetup = findViewById(R.id.btnSetup);
        btnPatch = findViewById(R.id.btnPatch);
        btnDownload = findViewById(R.id.btnDownload);
        scrollView = findViewById(R.id.scrollView);

        updateStatus("Initializing...");

        // Check if Termux is installed
        if (!isTermuxInstalled()) {
            updateStatus("❌ Termux not installed\n\nPlease install Termux from F-Droid first.");
            btnSetup.setEnabled(false);
            btnPatch.setEnabled(false);
            btnDownload.setEnabled(false);
        } else {
            updateStatus("✅ Termux detected\n\nReady to proceed.");
        }

        btnSetup.setOnClickListener(v -> handleSetup());
        btnPatch.setOnClickListener(v -> handlePatchAPK());
        btnDownload.setOnClickListener(v -> updateStatus("Download feature coming soon"));
    }

    private void handleSetup() {
        btnSetup.setEnabled(false);
        updateStatus("⏳ Copying setup script...\n\nLaunching Termux...");

        try {
            // Create downloads directory
            File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!downloadDir.exists()) {
                downloadDir.mkdirs();
            }

            // Copy setup script from assets
            InputStream in = getAssets().open("setup_rootspoofer.sh");
            File outFile = new File(downloadDir, "setup_rootspoofer.sh");
            FileOutputStream out = new FileOutputStream(outFile);
            
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            out.close();
            in.close();
            
            // Make executable
            outFile.setExecutable(true, false);

            // Launch Termux with the script
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setComponent(new ComponentName("com.termux", "com.termux.app.TermuxActivity"));
            intent.putExtra("com.termux.execute", "bash /storage/emulated/0/Download/setup_rootspoofer.sh");
            startActivity(intent);
            
            updateStatus("✅ Setup script copied\n\nRunning in Termux...\n\nThis may take 10-30 minutes.");
        } catch (IOException e) {
            Log.e(TAG, "Setup error", e);
            updateStatus("❌ Error: " + e.getMessage());
            btnSetup.setEnabled(true);
        }
    }

    private void handlePatchAPK() {
        btnPatch.setEnabled(false);
        updateStatus("⏳ Select APK to patch...");
        
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/vnd.android.package-archive");
        startActivityForResult(intent, 100);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == 100) {
            btnPatch.setEnabled(true);
            
            if (resultCode != RESULT_OK || data == null) {
                updateStatus("❌ No APK selected");
                return;
            }

            Uri uri = data.getData();
            if (uri == null) {
                updateStatus("❌ Invalid APK URI");
                return;
            }

            try {
                // Get filename
                String filename = getFilenameFromUri(uri);
                updateStatus("⏳ Processing: " + filename + "\n\n");

                // Copy to shared storage
                InputStream in = getContentResolver().openInputStream(uri);
                File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File outFile = new File(downloadDir, "target_" + System.currentTimeMillis() + ".apk");
                
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

                updateStatus("✅ APK copied (" + (totalSize / 1024 / 1024) + "MB)\n\nLaunching Termux...\n\nPatching may take 5-15 minutes.");

                // Launch Termux with patch command
                Intent patchIntent = new Intent(Intent.ACTION_VIEW);
                patchIntent.setComponent(new ComponentName("com.termux", "com.termux.app.TermuxActivity"));
                patchIntent.putExtra("com.termux.execute", 
                    "cd $HOME/rootspoofer && bash rootspoofer.sh " + outFile.getAbsolutePath());
                startActivity(patchIntent);
                
            } catch (IOException e) {
                Log.e(TAG, "Patch error", e);
                updateStatus("❌ Error: " + e.getMessage());
                btnPatch.setEnabled(true);
            }
        }
    }

    private String getFilenameFromUri(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try {
                String[] projection = {"_display_name"};
                var cursor = getContentResolver().query(uri, projection, null, null, null);
                if (cursor != null) {
                    int index = cursor.getColumnIndex("_display_name");
                    if (index >= 0) {
                        cursor.moveToFirst();
                        result = cursor.getString(index);
                    }
                    cursor.close();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting filename", e);
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    private boolean isTermuxInstalled() {
        try {
            getPackageManager().getPackageInfo("com.termux", 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void updateStatus(String message) {
        runOnUiThread(() -> {
            tvStatus.setText(message);
            scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
        });
    }
}
