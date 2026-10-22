package com.fbinstadownloader.app;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.yausername.ffmpeg.FFmpeg;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLException;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import com.yausername.youtubedl_android.YoutubeDLResponse;
import com.yausername.youtubedl_android.mapper.VideoFormat;
import com.yausername.youtubedl_android.mapper.VideoInfo;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import kotlin.Unit;
import kotlin.jvm.functions.Function3;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_STORAGE_PERMISSION = 1001;

    private ImageButton btnSettings;
    private EditText etVideoUrl;
    private Button btnPaste;
    private Button btnClear;
    private Button btnFetchFormats;

    private LinearLayout layoutLoading;
    private TextView tvLoadingMessage;

    private LinearLayout layoutVideoInfo;
    private ImageView ivThumbnail;
    private TextView tvPlatformBadge;
    private TextView tvVideoTitle;
    private TextView tvVideoMeta;
    private LinearLayout containerFormats;
    private Button btnDownloadSelected;

    private LinearLayout layoutDownloadProgress;
    private TextView tvDownloadStatusTitle;
    private TextView tvDownloadPercent;
    private ProgressBar progressBarDownload;
    private TextView tvDownloadLog;
    private Button btnCancelDownload;
    private Button btnOpenDownloaded;
    private TextView tvStorageInfo;

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private boolean isEngineReady = false;
    private boolean isUpdatingEngine = false;
    private boolean isFetching = false;
    private boolean isDownloading = false;
    private String engineVersion = "";
    private String currentProcessId = null;

    private String currentUrl = null;
    private VideoInfo currentVideoInfo = null;
    private final List<ResolutionOption> currentOptions = new ArrayList<>();
    private final List<View> optionViews = new ArrayList<>();
    private final List<RadioButton> optionRadios = new ArrayList<>();
    private int selectedOptionIndex = 0;
    private File lastDownloadedFile = null;

    private static class ResolutionOption {
        final String title;
        final String subtitle;
        final String badge;
        final String formatSelector;
        final boolean isAudioOnly;
        final int sortHeight;

        ResolutionOption(String title, String subtitle, String badge, String formatSelector, boolean isAudioOnly, int sortHeight) {
            this.title = title;
            this.subtitle = subtitle;
            this.badge = badge;
            this.formatSelector = formatSelector;
            this.isAudioOnly = isAudioOnly;
            this.sortHeight = sortHeight;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindViews();
        setupListeners();
        checkStoragePermissions();
        initDownloadEngine();
        handleIncomingIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    private void bindViews() {
        btnSettings = findViewById(R.id.btnSettings);
        etVideoUrl = findViewById(R.id.etVideoUrl);
        btnPaste = findViewById(R.id.btnPaste);
        btnClear = findViewById(R.id.btnClear);
        btnFetchFormats = findViewById(R.id.btnFetchFormats);

        layoutLoading = findViewById(R.id.layoutLoading);
        tvLoadingMessage = findViewById(R.id.tvLoadingMessage);

        layoutVideoInfo = findViewById(R.id.layoutVideoInfo);
        ivThumbnail = findViewById(R.id.ivThumbnail);
        tvPlatformBadge = findViewById(R.id.tvPlatformBadge);
        tvVideoTitle = findViewById(R.id.tvVideoTitle);
        tvVideoMeta = findViewById(R.id.tvVideoMeta);
        containerFormats = findViewById(R.id.containerFormats);
        btnDownloadSelected = findViewById(R.id.btnDownloadSelected);

        layoutDownloadProgress = findViewById(R.id.layoutDownloadProgress);
        tvDownloadStatusTitle = findViewById(R.id.tvDownloadStatusTitle);
        tvDownloadPercent = findViewById(R.id.tvDownloadPercent);
        progressBarDownload = findViewById(R.id.progressBarDownload);
        tvDownloadLog = findViewById(R.id.tvDownloadLog);
        btnCancelDownload = findViewById(R.id.btnCancelDownload);
        btnOpenDownloaded = findViewById(R.id.btnOpenDownloaded);
        tvStorageInfo = findViewById(R.id.tvStorageInfo);

        File dir = getDownloadDirectory();
        tvStorageInfo.setText("Downloads saved to: " + dir.getAbsolutePath());
    }

    private void setupListeners() {
        btnSettings.setOnClickListener(v -> showSettingsDialog());

        btnPaste.setOnClickListener(v -> pasteFromClipboard());

        btnClear.setOnClickListener(v -> {
            etVideoUrl.setText("");
            layoutVideoInfo.setVisibility(View.GONE);
            currentVideoInfo = null;
            currentOptions.clear();
        });

        btnFetchFormats.setOnClickListener(v -> {
            String rawInput = etVideoUrl.getText().toString().trim();
            String url = extractUrl(rawInput);
            if (TextUtils.isEmpty(url)) {
                Toast.makeText(this, "Please enter a valid Facebook or Instagram video/reel link", Toast.LENGTH_SHORT).show();
                return;
            }
            etVideoUrl.setText(url);
            hideKeyboard();
            fetchVideoResolutions(url);
        });

        btnDownloadSelected.setOnClickListener(v -> {
            if (currentOptions.isEmpty() || selectedOptionIndex < 0 || selectedOptionIndex >= currentOptions.size()) {
                Toast.makeText(this, "Please select a resolution first", Toast.LENGTH_SHORT).show();
                return;
            }
            startDownload(currentOptions.get(selectedOptionIndex));
        });

        btnCancelDownload.setOnClickListener(v -> cancelActiveDownload());

        btnOpenDownloaded.setOnClickListener(v -> openLastDownloadedFile());
    }

    private void showSettingsDialog() {
        LinearLayout dialogRoot = new LinearLayout(this);
        dialogRoot.setOrientation(LinearLayout.VERTICAL);
        dialogRoot.setBackgroundResource(R.drawable.bg_card);
        dialogRoot.setPadding(dp(22), dp(22), dp(22), dp(18));

        TextView tvTitle = new TextView(this);
        tvTitle.setText("Lunmella Settings");
        tvTitle.setTextColor(Color.parseColor("#F8FAFC"));
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        tvTitle.setTypeface(null, Typeface.BOLD);
        dialogRoot.addView(tvTitle);

        TextView tvDesc = new TextView(this);
        tvDesc.setText("Manage the download engine and storage preferences.");
        tvDesc.setTextColor(Color.parseColor("#94A3B8"));
        tvDesc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvDesc.setPadding(0, dp(4), 0, dp(16));
        dialogRoot.addView(tvDesc);

        final TextView tvEngineInfo = new TextView(this);
        String statusStr = isEngineReady
                ? ("Engine Status: Ready" + (!TextUtils.isEmpty(engineVersion) ? " (" + engineVersion + ")" : ""))
                : "Engine Status: Initializing...";
        tvEngineInfo.setText(statusStr);
        tvEngineInfo.setTextColor(Color.parseColor("#38BDF8"));
        tvEngineInfo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvEngineInfo.setPadding(0, 0, 0, dp(12));
        dialogRoot.addView(tvEngineInfo);

        final Button btnUpdate = new Button(this);
        btnUpdate.setText(isUpdatingEngine ? "Updating Engine..." : "Update Download Engine");
        btnUpdate.setAllCaps(false);
        btnUpdate.setTextColor(Color.WHITE);
        btnUpdate.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        btnUpdate.setTypeface(null, Typeface.BOLD);
        btnUpdate.setBackgroundResource(R.drawable.bg_button_primary);
        btnUpdate.setEnabled(!isUpdatingEngine);
        LinearLayout.LayoutParams updateParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(46)
        );
        updateParams.setMargins(0, 0, 0, dp(14));
        btnUpdate.setLayoutParams(updateParams);
        dialogRoot.addView(btnUpdate);

        TextView tvStorage = new TextView(this);
        tvStorage.setText("Save Location:\n" + getDownloadDirectory().getAbsolutePath());
        tvStorage.setTextColor(Color.parseColor("#CBD5E1"));
        tvStorage.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvStorage.setPadding(0, 0, 0, dp(16));
        dialogRoot.addView(tvStorage);

        Button btnClose = new Button(this);
        btnClose.setText("Close");
        btnClose.setAllCaps(false);
        btnClose.setTextColor(Color.parseColor("#E2E8F0"));
        btnClose.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        btnClose.setBackgroundResource(R.drawable.bg_button_secondary);
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(42)
        );
        btnClose.setLayoutParams(closeParams);
        dialogRoot.addView(btnClose);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogRoot)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        btnUpdate.setOnClickListener(v -> updateEngineFromSettings(tvEngineInfo, btnUpdate));
        btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void updateEngineFromSettings(final TextView tvEngineInfo, final Button btnUpdate) {
        if (!isEngineReady) {
            Toast.makeText(this, "Engine is still initializing, please wait...", Toast.LENGTH_SHORT).show();
            return;
        }
        if (isUpdatingEngine) {
            return;
        }

        isUpdatingEngine = true;
        btnUpdate.setEnabled(false);
        btnUpdate.setText("Checking for Updates...");
        tvEngineInfo.setText("Updating download engine...");

        executor.execute(() -> {
            try {
                YoutubeDL.UpdateStatus status = YoutubeDL.getInstance().updateYoutubeDL(
                        getApplicationContext(),
                        YoutubeDL.UpdateChannel._STABLE
                );
                final String version = YoutubeDL.getInstance().version(getApplicationContext());
                mainHandler.post(() -> {
                    isUpdatingEngine = false;
                    if (!TextUtils.isEmpty(version)) {
                        engineVersion = version;
                    }
                    btnUpdate.setEnabled(true);
                    btnUpdate.setText("Update Download Engine");
                    if (status == YoutubeDL.UpdateStatus.DONE) {
                        tvEngineInfo.setText("Engine Updated (" + engineVersion + ")");
                        Toast.makeText(MainActivity.this, "Download engine updated successfully!", Toast.LENGTH_SHORT).show();
                    } else {
                        tvEngineInfo.setText("Engine is up to date" + (!TextUtils.isEmpty(engineVersion) ? " (" + engineVersion + ")" : ""));
                        Toast.makeText(MainActivity.this, "Download engine is already up to date", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    isUpdatingEngine = false;
                    btnUpdate.setEnabled(true);
                    btnUpdate.setText("Update Download Engine");
                    tvEngineInfo.setText("Update failed: " + e.getMessage());
                    Toast.makeText(MainActivity.this, "Engine update failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null) return;
        if (Intent.ACTION_SEND.equals(intent.getAction()) && "text/plain".equals(intent.getType())) {
            String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (!TextUtils.isEmpty(sharedText)) {
                String extracted = extractUrl(sharedText);
                if (!TextUtils.isEmpty(extracted)) {
                    etVideoUrl.setText(extracted);
                    mainHandler.postDelayed(() -> {
                        if (isEngineReady && !isFetching) {
                            fetchVideoResolutions(extracted);
                        }
                    }, 600);
                }
            }
        }
    }

    private void initDownloadEngine() {
        executor.execute(() -> {
            try {
                YoutubeDL.getInstance().init(getApplicationContext());
                FFmpeg.getInstance().init(getApplicationContext());
                final String version = YoutubeDL.getInstance().version(getApplicationContext());
                mainHandler.post(() -> {
                    isEngineReady = true;
                    if (!TextUtils.isEmpty(version)) {
                        engineVersion = version;
                    }
                });
            } catch (YoutubeDLException e) {
                mainHandler.post(() -> Toast.makeText(
                        MainActivity.this,
                        "Failed to initialize download engine: " + e.getMessage(),
                        Toast.LENGTH_LONG
                ).show());
            }
        });
    }

    private void fetchVideoResolutions(final String url) {
        if (!isEngineReady) {
            Toast.makeText(this, "Initializing engine, please try again in a moment...", Toast.LENGTH_SHORT).show();
            return;
        }
        if (isFetching) {
            return;
        }

        isFetching = true;
        currentUrl = url;
        btnFetchFormats.setEnabled(false);
        btnFetchFormats.setText("Checking Qualities...");
        layoutVideoInfo.setVisibility(View.GONE);
        layoutLoading.setVisibility(View.VISIBLE);
        tvLoadingMessage.setText("Analyzing video link and available resolutions...");

        executor.execute(() -> {
            try {
                YoutubeDLRequest request = new YoutubeDLRequest(url);
                request.addOption("--no-playlist");
                request.addOption("--socket-timeout", 30);

                final VideoInfo info = YoutubeDL.getInstance().getInfo(request);
                final List<ResolutionOption> options = buildResolutionOptions(info);

                mainHandler.post(() -> {
                    isFetching = false;
                    btnFetchFormats.setEnabled(true);
                    btnFetchFormats.setText("Get Video Quality Options");
                    layoutLoading.setVisibility(View.GONE);
                    displayVideoInfoAndOptions(url, info, options);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    isFetching = false;
                    btnFetchFormats.setEnabled(true);
                    btnFetchFormats.setText("Get Video Quality Options");
                    layoutLoading.setVisibility(View.GONE);
                    showFallbackResolutionOptions(url);
                });
            }
        });
    }

    private List<ResolutionOption> buildResolutionOptions(VideoInfo info) {
        List<ResolutionOption> result = new ArrayList<>();

        result.add(new ResolutionOption(
                "Best Available Quality (Auto HD)",
                "Highest resolution video + audio merged (MP4)",
                "RECOMMENDED",
                "bestvideo+bestaudio/best",
                false,
                100000
        ));

        if (info != null && info.getFormats() != null && !info.getFormats().isEmpty()) {
            ArrayList<VideoFormat> formats = info.getFormats();

            List<ResolutionOption> specificVideoOptions = new ArrayList<>();
            Map<String, ResolutionOption> dedupMap = new LinkedHashMap<>();

            for (VideoFormat fmt : formats) {
                if (fmt == null) continue;
                String vcodec = fmt.getVcodec();
                String acodec = fmt.getAcodec();
                String formatId = fmt.getFormatId();
                String formatNote = fmt.getFormatNote();
                String ext = fmt.getExt() != null ? fmt.getExt() : "mp4";
                int width = fmt.getWidth();
                int height = fmt.getHeight();
                int fps = fmt.getFps();
                int tbr = fmt.getTbr();
                long fileSize = fmt.getFileSize() > 0 ? fmt.getFileSize() : fmt.getFileSizeApproximate();

                boolean hasVideo = vcodec != null && !"none".equalsIgnoreCase(vcodec);
                boolean hasAudio = acodec != null && !"none".equalsIgnoreCase(acodec);

                if (!hasVideo && (height > 0 || width > 0)) {
                    hasVideo = true;
                }
                if (!hasVideo && formatId != null) {
                    String lowerId = formatId.toLowerCase(Locale.US);
                    if (lowerId.contains("hd") || lowerId.contains("sd") || lowerId.contains("video")) {
                        hasVideo = true;
                    }
                }

                if (!hasVideo) {
                    continue;
                }

                String resLabel;
                int sortKey;
                if (height > 0 && width > 0) {
                    int shortSide = Math.min(width, height);
                    resLabel = shortSide + "p (" + width + "×" + height + ")";
                    sortKey = shortSide * 10 + (hasAudio ? 5 : 0);
                } else if (height > 0) {
                    resLabel = height + "p";
                    sortKey = height * 10 + (hasAudio ? 5 : 0);
                } else if (formatNote != null && !formatNote.trim().isEmpty()) {
                    resLabel = formatNote.trim().toUpperCase(Locale.US);
                    sortKey = resLabel.contains("HD") ? 7200 : (resLabel.contains("SD") ? 4800 : 3600);
                } else if (formatId != null) {
                    resLabel = "Quality " + formatId.toUpperCase(Locale.US);
                    sortKey = formatId.toLowerCase(Locale.US).contains("hd") ? 7200 : 4800;
                } else {
                    continue;
                }

                StringBuilder details = new StringBuilder();
                details.append(ext.toUpperCase(Locale.US));
                if (fps > 0) {
                    details.append(" • ").append(fps).append("fps");
                }
                if (tbr > 0) {
                    details.append(" • ").append(tbr).append(" kbps");
                }
                if (fileSize > 0) {
                    details.append(" • ~").append(formatFileSize(fileSize));
                }
                if (hasAudio) {
                    details.append(" • Audio+Video");
                } else {
                    details.append(" • Auto-merges Audio");
                }

                String badgeText = getQualityBadge(width, height, formatId, formatNote);

                String selector;
                if (formatId != null && !formatId.isEmpty()) {
                    if (hasAudio) {
                        selector = formatId + "/best";
                    } else {
                        selector = formatId + "+bestaudio/" + formatId + "/best";
                    }
                } else if (height > 0) {
                    selector = "bestvideo[height<=" + height + "]+bestaudio/best[height<=" + height + "]/best";
                } else {
                    selector = "bestvideo+bestaudio/best";
                }

                String dedupKey = resLabel + "_" + (hasAudio ? "av" : "vo");
                ResolutionOption existing = dedupMap.get(dedupKey);
                if (existing == null || tbr > 0) {
                    dedupMap.put(dedupKey, new ResolutionOption(
                            resLabel,
                            details.toString(),
                            badgeText,
                            selector,
                            false,
                            sortKey
                    ));
                }
            }

            specificVideoOptions.addAll(dedupMap.values());
            Collections.sort(specificVideoOptions, new Comparator<ResolutionOption>() {
                @Override
                public int compare(ResolutionOption o1, ResolutionOption o2) {
                    return Integer.compare(o2.sortHeight, o1.sortHeight);
                }
            });

            result.addAll(specificVideoOptions);
        }

        if (result.size() <= 1) {
            result.add(new ResolutionOption(
                    "1080p Full HD",
                    "MP4 • Up to 1080p resolution with audio",
                    "1080p",
                    "bestvideo[height<=1080]+bestaudio/best[height<=1080]/best",
                    false,
                    10800
            ));
            result.add(new ResolutionOption(
                    "720p HD",
                    "MP4 • Up to 720p HD resolution with audio",
                    "720p",
                    "bestvideo[height<=720]+bestaudio/best[height<=720]/hd/best",
                    false,
                    7200
            ));
            result.add(new ResolutionOption(
                    "480p SD",
                    "MP4 • Standard quality (Data Saver)",
                    "480p",
                    "bestvideo[height<=480]+bestaudio/best[height<=480]/sd/worst",
                    false,
                    4800
            ));
        }

        result.add(new ResolutionOption(
                "Audio Only (M4A)",
                "Extract best audio track from video/reel",
                "AUDIO",
                "bestaudio/best",
                true,
                10
        ));

        return result;
    }

    private String getQualityBadge(int width, int height, String formatId, String formatNote) {
        int shortSide = (width > 0 && height > 0) ? Math.min(width, height) : height;
        if (shortSide >= 1080) return "FULL HD";
        if (shortSide >= 720) return "HD";
        if (shortSide >= 480) return "SD";
        if (shortSide > 0) return shortSide + "p";
        if (formatId != null && formatId.toLowerCase(Locale.US).contains("hd")) return "HD";
        if (formatNote != null && formatNote.toLowerCase(Locale.US).contains("hd")) return "HD";
        return "VIDEO";
    }

    private void displayVideoInfoAndOptions(String url, VideoInfo info, List<ResolutionOption> options) {
        currentVideoInfo = info;
        currentOptions.clear();
        currentOptions.addAll(options);

        layoutVideoInfo.setVisibility(View.VISIBLE);

        String extractor = (info != null && info.getExtractorKey() != null)
                ? info.getExtractorKey().toUpperCase(Locale.US)
                : detectPlatformName(url);
        tvPlatformBadge.setText(extractor + " VIDEO / REEL");

        String title = (info != null && !TextUtils.isEmpty(info.getTitle()))
                ? info.getTitle()
                : "Facebook / Instagram Reel";
        tvVideoTitle.setText(title);

        StringBuilder meta = new StringBuilder();
        if (info != null && info.getDuration() > 0) {
            meta.append("Duration: ").append(formatDuration(info.getDuration()));
        }
        if (info != null && !TextUtils.isEmpty(info.getUploader())) {
            if (meta.length() > 0) meta.append(" • ");
            meta.append("By ").append(info.getUploader());
        }
        if (meta.length() == 0) {
            meta.append(options.size()).append(" download qualities available");
        }
        tvVideoMeta.setText(meta.toString());

        ivThumbnail.setImageResource(R.drawable.ic_launcher);
        if (info != null && !TextUtils.isEmpty(info.getThumbnail())) {
            loadThumbnailAsync(info.getThumbnail());
        }

        populateResolutionViews(options);
    }

    private void showFallbackResolutionOptions(String url) {
        List<ResolutionOption> fallback = buildResolutionOptions(null);
        currentVideoInfo = null;
        currentOptions.clear();
        currentOptions.addAll(fallback);

        layoutVideoInfo.setVisibility(View.VISIBLE);
        tvPlatformBadge.setText(detectPlatformName(url));
        tvVideoTitle.setText("Video Ready to Download");
        tvVideoMeta.setText("Choose your preferred quality below");
        ivThumbnail.setImageResource(R.drawable.ic_launcher);

        populateResolutionViews(fallback);
    }

    private void populateResolutionViews(List<ResolutionOption> options) {
        containerFormats.removeAllViews();
        optionViews.clear();
        optionRadios.clear();
        selectedOptionIndex = 0;

        for (int i = 0; i < options.size(); i++) {
            final int index = i;
            ResolutionOption opt = options.get(i);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(12), dp(14), dp(12));

            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            rowParams.setMargins(0, 0, 0, dp(10));
            row.setLayoutParams(rowParams);

            RadioButton rb = new RadioButton(this);
            rb.setClickable(false);
            rb.setFocusable(false);
            row.addView(rb);

            LinearLayout textCol = new LinearLayout(this);
            textCol.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams colParams = new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
            );
            colParams.setMarginStart(dp(10));
            textCol.setLayoutParams(colParams);

            TextView tvTitle = new TextView(this);
            tvTitle.setText(opt.title);
            tvTitle.setTextColor(Color.parseColor("#F8FAFC"));
            tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            tvTitle.setTypeface(null, Typeface.BOLD);

            TextView tvSub = new TextView(this);
            tvSub.setText(opt.subtitle);
            tvSub.setTextColor(Color.parseColor("#94A3B8"));
            tvSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);

            textCol.addView(tvTitle);
            textCol.addView(tvSub);
            row.addView(textCol);

            TextView tvBadge = new TextView(this);
            tvBadge.setText(opt.badge);
            tvBadge.setTextColor(Color.parseColor("#38BDF8"));
            tvBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            tvBadge.setTypeface(null, Typeface.BOLD);
            tvBadge.setBackgroundResource(R.drawable.bg_button_secondary);
            tvBadge.setPadding(dp(10), dp(4), dp(10), dp(4));
            row.addView(tvBadge);

            row.setOnClickListener(v -> selectResolutionOption(index));

            optionViews.add(row);
            optionRadios.add(rb);
            containerFormats.addView(row);
        }

        selectResolutionOption(0);
    }

    private void selectResolutionOption(int index) {
        selectedOptionIndex = index;
        for (int i = 0; i < optionViews.size(); i++) {
            boolean selected = (i == index);
            optionViews.get(i).setBackgroundResource(
                    selected ? R.drawable.bg_format_item_selected : R.drawable.bg_format_item
            );
            optionRadios.get(i).setChecked(selected);
        }
        if (index >= 0 && index < currentOptions.size()) {
            ResolutionOption chosen = currentOptions.get(index);
            btnDownloadSelected.setText("Download " + chosen.title);
        }
    }

    private void startDownload(final ResolutionOption option) {
        if (isDownloading) {
            Toast.makeText(this, "A download is already in progress", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(currentUrl)) {
            Toast.makeText(this, "Please enter a video link first", Toast.LENGTH_SHORT).show();
            return;
        }

        final File downloadDir = getDownloadDirectory();
        if (!downloadDir.exists()) {
            downloadDir.mkdirs();
        }

        isDownloading = true;
        lastDownloadedFile = null;
        currentProcessId = "dl_" + UUID.randomUUID().toString();

        btnDownloadSelected.setEnabled(false);
        layoutDownloadProgress.setVisibility(View.VISIBLE);
        btnOpenDownloaded.setVisibility(View.GONE);
        btnCancelDownload.setVisibility(View.VISIBLE);
        tvDownloadStatusTitle.setText("Downloading (" + option.badge + ")...");
        tvDownloadPercent.setText("0%");
        progressBarDownload.setIndeterminate(false);
        progressBarDownload.setProgress(0);
        tvDownloadLog.setText("Preparing media stream...");

        final long startTimeMs = System.currentTimeMillis();

        executor.execute(() -> {
            try {
                YoutubeDLRequest request = new YoutubeDLRequest(currentUrl);
                request.addOption("--no-playlist");
                request.addOption("--newline");
                request.addOption("-f", option.formatSelector);

                if (option.isAudioOnly) {
                    request.addOption("-x");
                    request.addOption("--audio-format", "m4a");
                } else {
                    request.addOption("--merge-output-format", "mp4");
                }

                String outputTemplate = new File(downloadDir, "%(title).100B_%(id)s.%(ext)s").getAbsolutePath();
                request.addOption("-o", outputTemplate);

                Function3<Float, Long, String, Unit> callback = new Function3<Float, Long, String, Unit>() {
                    @Override
                    public Unit invoke(Float progress, Long etaInSeconds, String line) {
                        final int pct = progress != null ? Math.max(0, Math.min(100, Math.round(progress))) : 0;
                        final long eta = etaInSeconds != null ? etaInSeconds : -1L;
                        mainHandler.post(() -> {
                            if (pct > 0) {
                                progressBarDownload.setIndeterminate(false);
                                progressBarDownload.setProgress(pct);
                                String etaStr = eta > 0 ? " (ETA " + eta + "s)" : "";
                                tvDownloadPercent.setText(pct + "%" + etaStr);
                                tvDownloadLog.setText("Downloading media... " + pct + "%" + etaStr);
                            }
                        });
                        return Unit.INSTANCE;
                    }
                };

                YoutubeDLResponse response = YoutubeDL.getInstance().execute(request, currentProcessId, callback);

                final File downloadedFile = findLatestDownloadedFile(downloadDir, startTimeMs);
                if (downloadedFile != null) {
                    MediaScannerConnection.scanFile(
                            getApplicationContext(),
                            new String[]{downloadedFile.getAbsolutePath()},
                            null,
                            null
                    );
                }

                mainHandler.post(() -> {
                    isDownloading = false;
                    currentProcessId = null;
                    btnDownloadSelected.setEnabled(true);
                    progressBarDownload.setIndeterminate(false);
                    progressBarDownload.setProgress(100);
                    tvDownloadPercent.setText("100%");
                    tvDownloadStatusTitle.setText("Download Complete!");
                    btnCancelDownload.setVisibility(View.GONE);

                    if (downloadedFile != null && downloadedFile.exists()) {
                        lastDownloadedFile = downloadedFile;
                        tvDownloadLog.setText("Saved to: " + downloadedFile.getAbsolutePath());
                        btnOpenDownloaded.setVisibility(View.VISIBLE);
                    } else {
                        tvDownloadLog.setText("Saved in: " + downloadDir.getAbsolutePath());
                    }
                    Toast.makeText(MainActivity.this, "Video downloaded successfully!", Toast.LENGTH_LONG).show();
                });
            } catch (YoutubeDL.CanceledException e) {
                mainHandler.post(() -> {
                    isDownloading = false;
                    currentProcessId = null;
                    btnDownloadSelected.setEnabled(true);
                    tvDownloadStatusTitle.setText("Download Canceled");
                    tvDownloadLog.setText("Download was canceled by user.");
                    btnCancelDownload.setVisibility(View.GONE);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    isDownloading = false;
                    currentProcessId = null;
                    btnDownloadSelected.setEnabled(true);
                    tvDownloadStatusTitle.setText("Download Failed");
                    tvDownloadPercent.setText("Error");
                    tvDownloadLog.setText("Could not download this link. Please verify the link is public or update the engine in Settings.");
                    btnCancelDownload.setVisibility(View.GONE);
                    Toast.makeText(MainActivity.this, "Download failed", Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void cancelActiveDownload() {
        if (isDownloading && currentProcessId != null) {
            YoutubeDL.getInstance().destroyProcessById(currentProcessId);
            Toast.makeText(this, "Canceling download...", Toast.LENGTH_SHORT).show();
        }
    }

    private void openLastDownloadedFile() {
        if (lastDownloadedFile == null || !lastDownloadedFile.exists()) {
            Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    lastDownloadedFile
            );
            String name = lastDownloadedFile.getName().toLowerCase(Locale.US);
            String mime = (name.endsWith(".m4a") || name.endsWith(".mp3")) ? "audio/*" : "video/*";
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mime);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Open Downloaded Media"));
        } catch (Exception e) {
            Toast.makeText(this, "Saved at: " + lastDownloadedFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
        }
    }

    private File getDownloadDirectory() {
        File publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File targetDir = new File(publicDownloads, "Lunmella");
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }
        if (targetDir.exists() && targetDir.canWrite()) {
            return targetDir;
        }
        File fallback = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        return fallback != null ? fallback : getFilesDir();
    }

    private File findLatestDownloadedFile(File dir, long sinceTimeMs) {
        File[] files = dir.listFiles();
        if (files == null || files.length == 0) return null;
        File newest = null;
        long newestMod = sinceTimeMs - 3000;
        for (File f : files) {
            if (f.isFile() && !f.getName().endsWith(".part") && !f.getName().endsWith(".ytdl")) {
                if (f.lastModified() >= newestMod) {
                    newestMod = f.lastModified();
                    newest = f;
                }
            }
        }
        return newest;
    }

    private void pasteFromClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null && clipboard.hasPrimaryClip()) {
            ClipData clip = clipboard.getPrimaryClip();
            if (clip != null && clip.getItemCount() > 0) {
                CharSequence text = clip.getItemAt(0).coerceToText(this);
                if (!TextUtils.isEmpty(text)) {
                    String url = extractUrl(text.toString());
                    etVideoUrl.setText(!TextUtils.isEmpty(url) ? url : text.toString().trim());
                    etVideoUrl.setSelection(etVideoUrl.getText().length());
                    return;
                }
            }
        }
        Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show();
    }

    private String extractUrl(String input) {
        if (TextUtils.isEmpty(input)) return "";
        Pattern urlPattern = Pattern.compile("(https?://[^\\s\"'<>]+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = urlPattern.matcher(input);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return input.trim();
    }

    private String detectPlatformName(String url) {
        if (url == null) return "VIDEO / REEL";
        String lower = url.toLowerCase(Locale.US);
        if (lower.contains("instagram.com") || lower.contains("instagr.am")) {
            return "INSTAGRAM REEL / VIDEO";
        }
        if (lower.contains("facebook.com") || lower.contains("fb.watch") || lower.contains("fb.com")) {
            return "FACEBOOK REEL / VIDEO";
        }
        return "SOCIAL MEDIA VIDEO";
    }

    private void loadThumbnailAsync(final String thumbUrl) {
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(thumbUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setDoInput(true);
                conn.connect();
                try (InputStream is = conn.getInputStream()) {
                    final Bitmap bmp = BitmapFactory.decodeStream(is);
                    if (bmp != null) {
                        mainHandler.post(() -> ivThumbnail.setImageBitmap(bmp));
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
    }

    private void checkStoragePermissions() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE},
                        REQ_STORAGE_PERMISSION
                );
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_STORAGE_PERMISSION) {
            File dir = getDownloadDirectory();
            tvStorageInfo.setText("Downloads saved to: " + dir.getAbsolutePath());
        }
    }

    private void hideKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        }
    }

    private String formatDuration(int totalSeconds) {
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb);
        double gb = mb / 1024.0;
        return String.format(Locale.US, "%.2f GB", gb);
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        ));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isDownloading && currentProcessId != null) {
            YoutubeDL.getInstance().destroyProcessById(currentProcessId);
        }
        executor.shutdownNow();
    }
}
