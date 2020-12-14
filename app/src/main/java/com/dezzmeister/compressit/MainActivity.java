package com.dezzmeister.compressit;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.webkit.MimeTypeMap;

import com.dezzmeister.compressit.provider.CompressItFileProvider;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.initialization.InitializationStatus;
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class MainActivity extends AppCompatActivity {
    private static final int CHOOSE_FILES_REQUEST = 2;
    private static final int SEND_FILE_REQUEST = 3;
    private static final String ARCHIVE_FILE_NAME = "archive.zip";
    private File archiveFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        MobileAds.initialize(this, new OnInitializationCompleteListener() {
            @Override
            public void onInitializationComplete(InitializationStatus initializationStatus) {

            }
        });

        final AdView adView = findViewById(R.id.adView);
        final AdRequest adRequest = new AdRequest.Builder().build();
        adView.loadAd(adRequest);

        archiveFile = new File(getApplicationContext().getExternalFilesDir(null), ARCHIVE_FILE_NAME);

        chooseFiles();
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        final MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    private void chooseFiles() {
        final Intent chooseFile = new Intent(Intent.ACTION_GET_CONTENT);

        chooseFile.setType("*/*");
        chooseFile.addCategory(Intent.CATEGORY_OPENABLE);
        chooseFile.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);

        startActivityForResult(Intent.createChooser(chooseFile, "Choose file(s)"), CHOOSE_FILES_REQUEST);
    }

    private void sendFile() {
        final Intent sendIntent = new Intent(Intent.ACTION_SEND);
        final Uri fileUri = CompressItFileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".provider", archiveFile);

        sendIntent.setType("application/zip");
        sendIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
        sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(Intent.createChooser(sendIntent, "Share Compressed File"), SEND_FILE_REQUEST);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.zip_files: {
                chooseFiles();
                return true;
            }
            case R.id.send_files: {
                if (archiveFile.exists()) {
                    sendFile();
                } else {
                    chooseFiles();
                }
                return true;
            }
            default: {
                return super.onOptionsItemSelected(item);
            }
        }
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        switch (requestCode) {
            case CHOOSE_FILES_REQUEST: {
                if (resultCode != RESULT_OK) {
                    //chooseFile();
                    return;
                }

                if (data.getClipData() == null) {
                    zipFiles(archiveFile, data.getData());
                    sendFile();
                    return;
                }

                final ClipData clipData = data.getClipData();
                final Uri[] uris = new Uri[clipData.getItemCount()];

                for (int i = 0; i < clipData.getItemCount(); i++) {
                    final Uri fileUri = clipData.getItemAt(i).getUri();
                    uris[i] = fileUri;
                }

                zipFiles(archiveFile, uris);
                sendFile();
                return;
            }
            case SEND_FILE_REQUEST: {
                if (resultCode != RESULT_OK) {
                    //sendFile();
                }
                return;
            }
            default: {
                super.onActivityResult(resultCode, requestCode, data);
            }
        }
    }

    private void zipFiles(final File outputFile, final Uri ... uris) {
        final byte[] buffer = new byte[2048];

        try (final ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outputFile))) {
            for (final Uri uri : uris) {
                final String fileName = getFileName(uri);
                final File file = new File(outputFile, fileName);
                final ZipEntry entry = new ZipEntry(file.getName());

                zos.putNextEntry(entry);

                final InputStream is = getContentResolver().openInputStream(uri);
                int len;
                while ((len = is.read(buffer)) > 0) {
                    zos.write(buffer, 0, len);
                }
                is.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String getFileName(final Uri uri) {
        if (uri.getScheme().equals(ContentResolver.SCHEME_CONTENT)) {
            final Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                final String result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                cursor.close();
                return result;
            }
        }

        final int index = uri.getPath().lastIndexOf('/');
        if (index == -1) {
            final String extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());

            return System.nanoTime() + "." + extension;
        }

        return uri.getPath().substring(index + 1);
    }
}