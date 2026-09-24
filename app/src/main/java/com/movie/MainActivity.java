package com.movie;

import android.Manifest;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.movie.data.CategoryEntity;
import com.movie.data.MovieDatabase;
import com.movie.data.MovieRecord;
import com.movie.data.ZohoPreferences;
import com.movie.repository.MovieRepository;
import com.movie.scheduler.SyncRunLog;
import com.movie.scheduler.SyncScheduler;
import com.movie.scheduler.SyncStatus;
import com.movie.ui.CategoryAdapter;
import com.movie.ui.CircleGaugeView;
import com.movie.util.UiUtils;
import com.movie.viewmodel.ImportingState;
import com.movie.viewmodel.MovieViewModel;
import com.movie.viewmodel.MovieViewModelFactory;
import com.movie.viewmodel.SettingsViewModel;
import com.movie.viewmodel.SettingsViewModelFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Java port of MainActivity.kt + DashboardScreen.kt, updated for this change request.
 *
 * <p>What changed:</p>
 * <ul>
 *   <li>Bottom navigation: the "Search" tab is GONE. "Dashboard" (with the dashboard icon) moved
 *       from 3rd place to 1st place. Remaining order: Dashboard, Sync Hub, Config.</li>
 *   <li>The old Dashboard page (movie list, gauge, premium banner, batch copy) was removed and
 *       replaced by the new Settings page (scheduler controls + category CRUD).</li>
 *   <li>Config page: BASE_URL at the very top, and the 4 Zoho/WorkDrive endpoints inside the
 *       "Generic region settings" section. The existing Client ID / Secret / Refresh Token /
 *       Folder ID fields are reused exactly as they were.</li>
 * </ul>
 */
public class MainActivity extends AppCompatActivity {

    // ---- ViewModel ----
    private MovieViewModel viewModel;
    private SettingsViewModel settingsViewModel;
    private ZohoPreferences prefs;

    // ---- host views ----
    private FrameLayout contentHost;
    private View importProgress;
    private View appLockOverlay;

    // ---- tab views (0 = Dashboard/Settings, 1 = Sync Hub, 2 = Config) ----
    private View tabDashboard;
    private View tabSync;
    private View tabConfig;

    // ---- settings tab widgets ----
    private SwitchCompat schedEnabled;
    private SwitchCompat schedWifiOnly;
    private EditText schedInterval;
    private View btnRunNow;
    private View btnCancelRun;
    private TextView syncStatusText;
    private TextView lastRunText;
    private EditText categorySearch;
    private TextView categoryCount;
    private TextView btnCategorySelectAll;
    private TextView btnCategoryRestore;
    private View btnCategoryAdd;
    private TextView categoryEmpty;
    private RecyclerView categoryList;

    private CategoryAdapter categoryAdapter;
    private final List<CategoryEntity> allCategories = new ArrayList<>();

    // ---- config tab widgets ----
    private EditText cfgBaseUrl;
    private EditText cfgClientId;
    private EditText cfgClientSecret;
    private EditText cfgRefreshToken;
    private EditText cfgFolderId;
    private EditText cfgFileName;
    private EditText cfgExtension;
    private EditText cfgAccountsServer;
    private EditText cfgApiServer;
    private EditText cfgZohoAccountsUrl;
    private EditText cfgWorkdriveApiUrl;
    private EditText cfgWorkdriveListUrl;
    private EditText cfgWorkdriveDownloadUrl;
    private EditText cfgPin;
    private SwitchCompat cfgClearOld;
    private SwitchCompat cfgAppLock;
    private View cfgPinContainer;
    private View cfgSave;
    private View cfgAdvancedToggle;
    private View cfgAdvancedContainer;
    private TextView cfgAdvancedIcon;
    private TextView cfgAdvancedLabel;

    // ---- sync tab widgets ----
    private TextView syncFolderValue;
    private TextView syncFileValue;
    private TextView syncCleansValue;
    private View btnFetchSync;
    private View btnBrowseUpload;
    private TextView terminalStatus;
    private TextView terminalHint;
    private View terminalExpand;

    // ---- Compose-equivalent UI state ----
    private int activeBottomTab = 0;
    private boolean isClientIdVisible = false;
    private boolean isClientSecretVisible = false;
    private boolean isRefreshTokenVisible = false;
    private boolean showAdvanced = false;
    private boolean isUnlocked = true;
    private boolean syncingSwitchFromCode = false;
    private String categoryQuery = "";
    private String terminalStatusText = "status: STANDBY\n> terminal idle.\n> waiting to fetch database workbook...";

    private ActivityResultLauncher<String[]> fileLauncher;
    private ActivityResultLauncher<String> notificationPermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = new ZohoPreferences(this);
        isUnlocked = !prefs.isAppLockEnabled();

        // database -> dao -> repository -> viewModel  (mirrors MainActivity.kt)
        MovieDatabase database = MovieDatabase.getDatabase(getApplicationContext());
        MovieRepository repository = new MovieRepository(database.movieDao());
        viewModel = new ViewModelProvider(this, new MovieViewModelFactory(repository))
                .get(MovieViewModel.class);
        settingsViewModel = new ViewModelProvider(this, new SettingsViewModelFactory(getApplication()))
                .get(SettingsViewModel.class);

        bindViews();
        setupFilePicker();
        setupBottomNav();
        setupSettingsTab();
        setupSyncTab();
        setupConfigTab();
        observeViewModel();
        setupSchedulerUi();

        if (!isUnlocked) {
            showAppLock();
        }
        selectTab(0);
    }

    // ================================================================
    //  view binding + setup
    // ================================================================

    private void bindViews() {
        contentHost = findViewById(R.id.content_host);
        importProgress = findViewById(R.id.import_progress);
        appLockOverlay = findViewById(R.id.app_lock_overlay);

        findViewById(R.id.btn_help).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSheetLayoutDialog();
            }
        });
        findViewById(R.id.btn_add).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAddMovieDialog();
            }
        });
        findViewById(R.id.btn_options).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showOptionsMenu(v);
            }
        });
    }

    private void setupFilePicker() {
        fileLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                new ActivityResultCallback<Uri>() {
                    @Override
                    public void onActivityResult(Uri uri) {
                        if (uri != null) {
                            viewModel.importFile(MainActivity.this, uri);
                        }
                    }
                });

        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                new ActivityResultCallback<Boolean>() {
                    @Override
                    public void onActivityResult(Boolean granted) {
                        // A background run still works without it; the notification is just hidden.
                    }
                });
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    private void openFilePicker() {
        fileLauncher.launch(new String[]{
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "text/comma-separated-values",
                "text/plain"
        });
    }

    private void setupBottomNav() {
        findViewById(R.id.tab_dashboard).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectTab(0);
            }
        });
        findViewById(R.id.tab_sync).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectTab(1);
            }
        });
        findViewById(R.id.tab_config).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectTab(2);
            }
        });
    }

    private void selectTab(int tab) {
        activeBottomTab = tab;

        contentHost.removeAllViews();
        if (tab == 0) contentHost.addView(tabDashboard);
        else if (tab == 1) contentHost.addView(tabSync);
        else contentHost.addView(tabConfig);

        if (tab == 2) {
            loadConfigIntoFields();
        }
        if (tab == 1) {
            refreshSyncUi();
        }
        if (tab == 0) {
            refreshSchedulerStatus(SyncStatus.current());
            refreshCategoryCount();
        }
        updateTabHighlight();
    }

    private void updateTabHighlight() {
        int[] tabIds = {R.id.tab_dashboard, R.id.tab_sync, R.id.tab_config};
        int[] iconIds = {R.id.tab_dashboard_icon, R.id.tab_sync_icon, R.id.tab_config_icon};
        int[] labelIds = {R.id.tab_dashboard_label, R.id.tab_sync_label, R.id.tab_config_label};

        for (int i = 0; i < tabIds.length; i++) {
            boolean active = (i == activeBottomTab);
            int color = ContextCompat.getColor(this,
                    active ? R.color.bubble_blue : R.color.bubble_text_secondary);
            ((TextView) findViewById(iconIds[i])).setTextColor(color);
            ((TextView) findViewById(labelIds[i])).setTextColor(color);
        }
    }

    // ================================================================
    //  Tab 0 : Settings page (replaces the old Dashboard page)
    // ================================================================

    private void setupSettingsTab() {
        tabDashboard = LayoutInflater.from(this).inflate(R.layout.view_tab_settings, contentHost, false);

        schedEnabled = tabDashboard.findViewById(R.id.scheduler_enabled);
        schedWifiOnly = tabDashboard.findViewById(R.id.scheduler_wifi_only);
        schedInterval = tabDashboard.findViewById(R.id.scheduler_interval);
        btnRunNow = tabDashboard.findViewById(R.id.btn_run_now);
        btnCancelRun = tabDashboard.findViewById(R.id.btn_cancel_run);
        syncStatusText = tabDashboard.findViewById(R.id.sync_status_text);
        lastRunText = tabDashboard.findViewById(R.id.last_run_text);
        categorySearch = tabDashboard.findViewById(R.id.category_search);
        categoryCount = tabDashboard.findViewById(R.id.category_count);
        btnCategorySelectAll = tabDashboard.findViewById(R.id.btn_category_select_all);
        btnCategoryRestore = tabDashboard.findViewById(R.id.btn_category_restore);
        btnCategoryAdd = tabDashboard.findViewById(R.id.btn_category_add);
        categoryEmpty = tabDashboard.findViewById(R.id.category_empty);
        categoryList = tabDashboard.findViewById(R.id.category_list);

        categoryList.setLayoutManager(new LinearLayoutManager(this));
        categoryList.setNestedScrollingEnabled(false);

        categoryAdapter = new CategoryAdapter(new CategoryAdapter.Listener() {
            @Override
            public void onToggleEnabled(CategoryEntity category, boolean enabled) {
                settingsViewModel.setCategoryEnabled(category, enabled);
                // Reflect the new state locally so the counter updates instantly.
                category.setEnabled(enabled);
                refreshCategoryCount();
            }

            @Override
            public void onEdit(CategoryEntity category) {
                showCategoryDialog(category);
            }

            @Override
            public void onDelete(final CategoryEntity category) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Delete category")
                        .setMessage("Remove \"" + category.getPath() + "\" from the scheduler list?")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                settingsViewModel.deleteCategory(category);
                                Toast.makeText(MainActivity.this, "Category deleted",
                                        Toast.LENGTH_SHORT).show();
                            }
                        })
                        .show();
            }
        });
        categoryList.setAdapter(categoryAdapter);

        categorySearch.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void onTextChanged(String text) {
                categoryQuery = text;
                applyCategoryFilter();
            }
        });

        btnCategoryAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCategoryDialog(null);
            }
        });

        btnCategorySelectAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean allTicked = allCategoriesTicked();
                settingsViewModel.setAllCategoriesEnabled(!allTicked);
                for (CategoryEntity c : allCategories) c.setEnabled(!allTicked);
                applyCategoryFilter();
                Toast.makeText(MainActivity.this,
                        !allTicked ? "All categories ticked" : "All categories unticked",
                        Toast.LENGTH_SHORT).show();
            }
        });

        btnCategoryRestore.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Restore default list")
                        .setMessage("Replace the current category list with the original "
                                + "subcategory array? Your custom entries will be lost.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Restore", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                settingsViewModel.restoreDefaultCategories();
                                Toast.makeText(MainActivity.this,
                                        "Default categories restored", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .show();
            }
        });

        // Scheduler switches
        schedEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (syncingSwitchFromCode) return;
            settingsViewModel.setSchedulerEnabled(isChecked);
            if (isChecked) {
                requestNotificationPermissionIfNeeded();
                SyncScheduler.enable(MainActivity.this);
                Toast.makeText(MainActivity.this,
                        "Automatic scheduler ENABLED - runs every "
                                + settingsViewModel.getSchedulerIntervalMinutes() + " minutes",
                        Toast.LENGTH_LONG).show();
            } else {
                SyncScheduler.disable(MainActivity.this);
                Toast.makeText(MainActivity.this, "Automatic scheduler DISABLED",
                        Toast.LENGTH_SHORT).show();
            }
            refreshLastRunLine();
        });

        schedWifiOnly.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settingsViewModel.setSchedulerWifiOnly(isChecked);
            SyncScheduler.rescheduleIfEnabled(MainActivity.this);
        });

        schedInterval.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) commitSchedulerInterval();
            }
        });

        btnRunNow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (enabledCategoryCountLocal() == 0) {
                    Toast.makeText(MainActivity.this,
                            "Tick at least one category before running the scheduler.",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                commitSchedulerInterval();
                requestNotificationPermissionIfNeeded();
                SyncScheduler.runNow(MainActivity.this);
                Toast.makeText(MainActivity.this,
                        "Sync run queued. It can take 30-45 minutes; progress appears in the "
                                + "notification and in the status box below.",
                        Toast.LENGTH_LONG).show();
            }
        });

        btnCancelRun.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SyncScheduler.cancelRunning(MainActivity.this);
                Toast.makeText(MainActivity.this, "Sync run cancelled", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void commitSchedulerInterval() {
        String raw = schedInterval.getText().toString().trim();
        int minutes;
        try {
            minutes = Integer.parseInt(raw);
        } catch (Exception e) {
            minutes = settingsViewModel.getSchedulerIntervalMinutes();
        }
        if (minutes < ZohoPreferences.MIN_SCHEDULER_INTERVAL_MINUTES) {
            minutes = ZohoPreferences.MIN_SCHEDULER_INTERVAL_MINUTES;
            Toast.makeText(this, "Minimum interval is "
                    + ZohoPreferences.MIN_SCHEDULER_INTERVAL_MINUTES + " minutes", Toast.LENGTH_SHORT).show();
        }
        settingsViewModel.setSchedulerIntervalMinutes(minutes);
        schedInterval.setText(String.valueOf(minutes));
        SyncScheduler.rescheduleIfEnabled(this);
    }

    private void setupSchedulerUi() {
        settingsViewModel.observeCategories().observe(this, new Observer<List<CategoryEntity>>() {
            @Override
            public void onChanged(List<CategoryEntity> categories) {
                allCategories.clear();
                if (categories != null) allCategories.addAll(categories);
                applyCategoryFilter();
            }
        });

        settingsViewModel.observeSyncStatus().observe(this, new Observer<SyncStatus>() {
            @Override
            public void onChanged(SyncStatus status) {
                refreshSchedulerStatus(status);
            }
        });

        schedWifiOnly.setChecked(settingsViewModel.isSchedulerWifiOnly());
        schedInterval.setText(String.valueOf(settingsViewModel.getSchedulerIntervalMinutes()));

        syncingSwitchFromCode = true;
        schedEnabled.setChecked(settingsViewModel.isSchedulerEnabled()
                && SyncScheduler.isEnabled(this));
        syncingSwitchFromCode = false;

        settingsViewModel.refreshPersistedStatus();
        refreshSchedulerStatus(SyncStatus.current());
        refreshLastRunLine();
    }

    private boolean allCategoriesTicked() {
        if (allCategories.isEmpty()) return false;
        for (CategoryEntity c : allCategories) {
            if (!c.isEnabled()) return false;
        }
        return true;
    }

    private int enabledCategoryCountLocal() {
        int ticked = 0;
        for (CategoryEntity c : allCategories) if (c.isEnabled()) ticked++;
        return ticked;
    }

    private void refreshCategoryCount() {
        if (categoryCount == null) return;
        int ticked = 0;
        for (CategoryEntity c : allCategories) if (c.isEnabled()) ticked++;
        categoryCount.setText(ticked + " of " + allCategories.size() + " categories ticked");
        boolean allTicked = !allCategories.isEmpty() && ticked == allCategories.size();
        btnCategorySelectAll.setText(allTicked ? "Deselect all" : "Select all");
    }

    private void applyCategoryFilter() {
        if (categoryAdapter == null) return;
        String q = categoryQuery == null ? "" : categoryQuery.trim().toLowerCase(Locale.ROOT);
        List<CategoryEntity> filtered = new ArrayList<>();
        for (CategoryEntity c : allCategories) {
            if (q.isEmpty()
                    || c.getName().toLowerCase(Locale.ROOT).contains(q)
                    || c.getPath().toLowerCase(Locale.ROOT).contains(q)) {
                filtered.add(c);
            }
        }
        categoryAdapter.submit(filtered);
        categoryEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        categoryList.setVisibility(filtered.isEmpty() ? View.GONE : View.VISIBLE);
        refreshCategoryCount();
    }

    private void refreshSchedulerStatus(SyncStatus status) {
        if (syncStatusText == null) return;
        SyncStatus s = status == null ? SyncStatus.idle() : status;

        StringBuilder sb = new StringBuilder();
        sb.append("status: ").append(s.getPhase().name()).append('\n');
        sb.append("> step: ").append(s.getStep()).append('\n');
        if (s.getTotal() > 0) {
            sb.append("> progress: ").append(s.getProcessed()).append('/').append(s.getTotal())
                    .append(" (").append(s.getPercent()).append("%)\n");
        }
        if (!s.getDetail().isEmpty()) {
            sb.append("> ").append(s.getDetail()).append('\n');
        }
        if (s.getStartedAt() > 0) {
            sb.append("> started : ").append(SyncRunLog.formatTime(s.getStartedAt())).append('\n');
        }
        if (s.getFinishedAt() > 0) {
            sb.append("> finished: ").append(SyncRunLog.formatTime(s.getFinishedAt()));
        }
        syncStatusText.setText(sb.toString().trim());

        int color;
        switch (s.getPhase()) {
            case SUCCESS:
                color = R.color.terminal_green;
                break;
            case ERROR:
                color = R.color.terminal_red;
                break;
            case RUNNING:
            case QUEUED:
                color = R.color.terminal_amber;
                break;
            default:
                color = R.color.terminal_cyan;
                break;
        }
        syncStatusText.setTextColor(ContextCompat.getColor(this, color));

        boolean busy = s.isRunning();
        btnRunNow.setEnabled(!busy);
        btnRunNow.setAlpha(busy ? 0.5f : 1f);
        btnCancelRun.setVisibility(busy ? View.VISIBLE : View.GONE);
        importProgress.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    private void refreshLastRunLine() {
        if (lastRunText == null) return;
        SyncStatus saved = SyncRunLog.load(this);
        StringBuilder sb = new StringBuilder();
        sb.append("Automatic scheduler: ")
                .append(settingsViewModel.isSchedulerEnabled() ? "ENABLED" : "DISABLED");
        sb.append("  |  every ").append(settingsViewModel.getSchedulerIntervalMinutes()).append(" min");
        sb.append("\nnetwork: ").append(settingsViewModel.isSchedulerWifiOnly() ? "Wi-Fi only" : "any");
        sb.append("  |  finished runs: ").append(SyncRunLog.getRunCount(this));
        if (saved.getFinishedAt() > 0) {
            sb.append("\nlast result: ").append(saved.getPhase().name())
                    .append(" at ").append(SyncRunLog.formatTime(saved.getFinishedAt()));
        }
        lastRunText.setText(sb.toString());
    }

    /** Add (entity == null) or edit an existing category. */
    private void showCategoryDialog(final CategoryEntity entity) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(16), dp(20), dp(0));

        TextView hint = new TextView(this);
        hint.setText("Enter the category path exactly as it appears on the site, "
                + "e.g. /tamil-2026-movies/");
        hint.setTextSize(11f);
        hint.setTextColor(ContextCompat.getColor(this, R.color.bubble_text_secondary));
        content.addView(hint);

        final EditText path = new EditText(this);
        path.setHint("/tamil-2026-movies/");
        path.setTextSize(13f);
        path.setSingleLine(true);
        path.setText(entity == null ? "" : entity.getPath());
        path.setBackgroundResource(R.drawable.bg_card_12);
        path.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        path.setLayoutParams(lp);
        content.addView(path);

        new AlertDialog.Builder(this)
                .setTitle(entity == null ? "Add category" : "Edit category")
                .setView(content)
                .setNegativeButton("Cancel", null)
                .setPositiveButton(entity == null ? "Add" : "Save",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                String raw = path.getText().toString();
                                String normalized = CategoryEntity.normalizePath(raw);
                                if (normalized.length() <= 2) {
                                    Toast.makeText(MainActivity.this,
                                            "Category path cannot be empty", Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                if (entity == null) {
                                    settingsViewModel.addCategory(normalized);
                                    Toast.makeText(MainActivity.this,
                                            "Category added (unticked by default)",
                                            Toast.LENGTH_SHORT).show();
                                } else {
                                    entity.setPath(normalized);
                                    entity.setName(CategoryEntity.displayNameFor(normalized));
                                    settingsViewModel.updateCategory(entity);
                                    Toast.makeText(MainActivity.this, "Category updated",
                                            Toast.LENGTH_SHORT).show();
                                }
                            }
                        })
                .show();
    }

    // ================================================================
    //  Tab 1 : Sync Hub
    // ================================================================

    private void setupSyncTab() {
        tabSync = LayoutInflater.from(this).inflate(R.layout.view_tab_sync, contentHost, false);

        syncFolderValue = tabSync.findViewById(R.id.sync_folder_value);
        syncFileValue = tabSync.findViewById(R.id.sync_file_value);
        syncCleansValue = tabSync.findViewById(R.id.sync_cleans_value);
        btnFetchSync = tabSync.findViewById(R.id.btn_fetch_sync);
        btnBrowseUpload = tabSync.findViewById(R.id.btn_browse_upload);
        terminalStatus = tabSync.findViewById(R.id.terminal_status);
        terminalHint = tabSync.findViewById(R.id.terminal_hint);
        terminalExpand = tabSync.findViewById(R.id.terminal_expand);

        btnFetchSync.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                viewModel.syncFromZoho(MainActivity.this);
            }
        });
        btnBrowseUpload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openFilePicker();
            }
        });
        terminalExpand.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showErrorDetailDialog(terminalStatusText);
            }
        });
    }

    private void refreshSyncUi() {
        String folder = prefs.getFolderId();
        syncFolderValue.setText(folder.isEmpty() ? "None"
                : (folder.length() > 10 ? folder.substring(0, 10) + "..." : folder));
        String fname = prefs.getFileName().isEmpty() ? "movies" : prefs.getFileName();
        syncFileValue.setText(fname + "." + prefs.getDefaultExtension());
        syncCleansValue.setText(prefs.isClearOldDataBeforeUpload() ? "YES" : "NO");
        syncCleansValue.setTextColor(ContextCompat.getColor(this,
                prefs.isClearOldDataBeforeUpload() ? R.color.bubble_green : R.color.bubble_blue));
    }

    // ================================================================
    //  Tab 2 : Config
    // ================================================================

    private void setupConfigTab() {
        tabConfig = LayoutInflater.from(this).inflate(R.layout.view_tab_config, contentHost, false);

        cfgBaseUrl = tabConfig.findViewById(R.id.config_base_url);
        cfgClientId = tabConfig.findViewById(R.id.config_client_id);
        cfgClientSecret = tabConfig.findViewById(R.id.config_client_secret);
        cfgRefreshToken = tabConfig.findViewById(R.id.config_refresh_token);
        cfgFolderId = tabConfig.findViewById(R.id.config_folder_id);
        cfgFileName = tabConfig.findViewById(R.id.config_file_name);
        cfgExtension = tabConfig.findViewById(R.id.config_extension);
        cfgAccountsServer = tabConfig.findViewById(R.id.config_accounts_server);
        cfgApiServer = tabConfig.findViewById(R.id.config_api_server);
        cfgZohoAccountsUrl = tabConfig.findViewById(R.id.config_zoho_accounts_url);
        cfgWorkdriveApiUrl = tabConfig.findViewById(R.id.config_workdrive_api_url);
        cfgWorkdriveListUrl = tabConfig.findViewById(R.id.config_workdrive_list_url);
        cfgWorkdriveDownloadUrl = tabConfig.findViewById(R.id.config_workdrive_download_url);
        cfgPin = tabConfig.findViewById(R.id.config_pin);
        cfgClearOld = tabConfig.findViewById(R.id.config_clear_old);
        cfgAppLock = tabConfig.findViewById(R.id.config_app_lock);
        cfgPinContainer = tabConfig.findViewById(R.id.config_pin_container);
        cfgSave = tabConfig.findViewById(R.id.config_save);
        cfgAdvancedToggle = tabConfig.findViewById(R.id.config_advanced_toggle);
        cfgAdvancedContainer = tabConfig.findViewById(R.id.config_advanced_container);
        cfgAdvancedIcon = tabConfig.findViewById(R.id.config_advanced_icon);
        cfgAdvancedLabel = tabConfig.findViewById(R.id.config_advanced_label);

        setupVisibilityToggle(tabConfig.findViewById(R.id.config_client_id_toggle), cfgClientId, true);
        setupVisibilityToggle(tabConfig.findViewById(R.id.config_client_secret_toggle), cfgClientSecret, false);
        setupVisibilityToggle(tabConfig.findViewById(R.id.config_refresh_token_toggle), cfgRefreshToken, false);

        cfgAppLock.setOnCheckedChangeListener((buttonView, isChecked) ->
                cfgPinContainer.setVisibility(isChecked ? View.VISIBLE : View.GONE));

        cfgAdvancedToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAdvanced = !showAdvanced;
                cfgAdvancedContainer.setVisibility(showAdvanced ? View.VISIBLE : View.GONE);
                cfgAdvancedIcon.setText(showAdvanced ? "expand_less" : "expand_more");
                cfgAdvancedLabel.setText(showAdvanced
                        ? "Generic region settings"
                        : "Generic region settings (Advanced Regional Hostings)");
            }
        });

        cfgSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveConfig();
            }
        });
    }

    private void setupVisibilityToggle(View toggle, final EditText field, final boolean isClientId) {
        toggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean visible;
                if (isClientId) {
                    isClientIdVisible = !isClientIdVisible;
                    visible = isClientIdVisible;
                } else if (field == cfgClientSecret) {
                    isClientSecretVisible = !isClientSecretVisible;
                    visible = isClientSecretVisible;
                } else {
                    isRefreshTokenVisible = !isRefreshTokenVisible;
                    visible = isRefreshTokenVisible;
                }
                field.setInputType(visible
                        ? InputType.TYPE_CLASS_TEXT
                        : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                field.setSelection(field.getText().length());
                ((TextView) v).setText(visible ? "visibility_off" : "visibility");
            }
        });
    }

    /** LaunchedEffect(activeBottomTab) { if (activeBottomTab == 2) { reload from prefs } } */
    private void loadConfigIntoFields() {
        ZohoPreferences p = new ZohoPreferences(this);
        cfgBaseUrl.setText(p.getBaseUrl());
        cfgClientId.setText(p.getClientId());
        cfgClientSecret.setText(p.getClientSecret());
        cfgRefreshToken.setText(p.getRefreshToken());
        cfgFolderId.setText(p.getFolderId());
        cfgFileName.setText(p.getFileName());
        cfgExtension.setText(p.getDefaultExtension());
        cfgAccountsServer.setText(p.getAccountsServer());
        cfgApiServer.setText(p.getApiServer());
        cfgZohoAccountsUrl.setText(p.getZohoAccountsUrl());
        cfgWorkdriveApiUrl.setText(p.getWorkdriveApiUrl());
        cfgWorkdriveListUrl.setText(p.getWorkdriveListUrl());
        cfgWorkdriveDownloadUrl.setText(p.getWorkdriveDownloadUrl());
        cfgClearOld.setChecked(p.isClearOldDataBeforeUpload());
        cfgAppLock.setChecked(p.isAppLockEnabled());
        cfgPin.setText(p.getAppLockPasscode());
        cfgPinContainer.setVisibility(p.isAppLockEnabled() ? View.VISIBLE : View.GONE);
    }

    private void saveConfig() {
        ZohoPreferences p = new ZohoPreferences(this);

        // PIN validation check before saving
        if (cfgAppLock.isChecked()) {
            String pin = cfgPin.getText().toString().trim();
            if (pin.length() < 4) {
                Toast.makeText(this,
                        "ERROR: Security PIN code is too short! Must be at least 4 digits.",
                        Toast.LENGTH_LONG).show();
                return;
            }
        }

        // BASE_URL (MovieScraperAPI) - toggled at the top of the Config page
        p.setBaseUrl(cfgBaseUrl.getText().toString());

        // Existing (reused) credentials
        p.setClientId(cfgClientId.getText().toString().trim());
        p.setClientSecret(cfgClientSecret.getText().toString().trim());
        p.setRefreshToken(cfgRefreshToken.getText().toString().trim());
        p.setFolderId(cfgFolderId.getText().toString().trim());

        p.setFileName(cfgFileName.getText().toString().trim());
        p.setDefaultExtension(cfgExtension.getText().toString().trim());
        p.setAccountsServer(cfgAccountsServer.getText().toString().trim());
        p.setApiServer(cfgApiServer.getText().toString().trim());

        // NEW endpoints (UploadFileAPI)
        p.setZohoAccountsUrl(cfgZohoAccountsUrl.getText().toString());
        p.setWorkdriveApiUrl(cfgWorkdriveApiUrl.getText().toString());
        p.setWorkdriveListUrl(cfgWorkdriveListUrl.getText().toString());
        p.setWorkdriveDownloadUrl(cfgWorkdriveDownloadUrl.getText().toString());

        p.setClearOldDataBeforeUpload(cfgClearOld.isChecked());
        p.setAppLockEnabled(cfgAppLock.isChecked());
        String pin = cfgPin.getText().toString().trim();
        p.setAppLockPasscode(pin.isEmpty() ? "1234" : pin);

        Toast.makeText(this, "Keys and security configurations secured!", Toast.LENGTH_SHORT).show();
        refreshSyncUi();
        refreshLastRunLine();
    }

    // ================================================================
    //  LiveData observers (replacing collectAsStateWithLifecycle)
    // ================================================================

    private void observeViewModel() {
        viewModel.getImportingState().observe(this, new Observer<ImportingState>() {
            @Override
            public void onChanged(ImportingState state) {
                handleImportingState(state);
            }
        });
    }

    /** LaunchedEffect(importingState) { ... } */
    private void handleImportingState(ImportingState state) {
        if (state == null) return;

        if (state.isLoading()) {
            importProgress.setVisibility(View.VISIBLE);
            terminalStatusText = "status: CONNECTING\n"
                    + "> refreshing active oauth credentials...\n"
                    + "> scanning files inside cloud folder: "
                    + (prefs.getFolderId().length() > 12
                    ? prefs.getFolderId().substring(0, 12) : prefs.getFolderId()) + "...\n"
                    + "> matching sheets naming convention for '" + prefs.getFileName() + "'...\n"
                    + "> downloading file contents stream...";
            setTerminal(terminalStatusText, R.color.terminal_amber, false);

        } else if (state.isSuccess()) {
            importProgress.setVisibility(View.GONE);
            terminalStatusText = "status: SUCCESS\n"
                    + "> transaction approved.\n"
                    + "> downloaded movie index matched: OK\n"
                    + "> old records purged: "
                    + (prefs.isClearOldDataBeforeUpload() ? "YES" : "NO") + "\n"
                    + "> compiled database table: inserted " + state.getCount() + " records.";
            setTerminal(terminalStatusText, R.color.terminal_green, false);
            Toast.makeText(this, "Successfully imported " + state.getCount() + " movies!",
                    Toast.LENGTH_LONG).show();
            viewModel.resetImportState();

        } else if (state.isError()) {
            importProgress.setVisibility(View.GONE);
            terminalStatusText = "status: TERMINATED_ERROR\n"
                    + "> error code: SYSTEM_FAULT\n"
                    + "> message: " + state.getMessage() + "\n"
                    + "> database rollbacked to state: SAFE";
            setTerminal(terminalStatusText, R.color.terminal_red, true);
            Toast.makeText(this, "Import failed: " + state.getMessage(), Toast.LENGTH_LONG).show();
            showErrorDetailDialog(state.getMessage());
            viewModel.resetImportState();
        }
    }

    private void setTerminal(String text, int colorRes, boolean showHint) {
        terminalStatus.setText(text);
        terminalStatus.setTextColor(ContextCompat.getColor(this, colorRes));
        terminalHint.setVisibility(showHint ? View.VISIBLE : View.GONE);
    }

    // ================================================================
    //  dialogs
    // ================================================================

    /** Help dialog: "Spreadsheet Layout Map" */
    private void showSheetLayoutDialog() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        content.setPadding(pad, pad, pad, pad);

        TextView intro = new TextView(this);
        intro.setText("The parser scans the workbook header row. If no header row is identified, "
                + "columns are automatically aligned index-wise to these standard columns:");
        intro.setTextSize(12f);
        intro.setTextColor(ContextCompat.getColor(this, R.color.bubble_text_secondary));
        content.addView(intro);

        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setBackgroundResource(R.drawable.bg_sheet_grid);
        grid.setPadding(dp(8), dp(8), dp(8), dp(8));
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gp.topMargin = dp(14);
        grid.setLayoutParams(gp);

        addSheetRow(grid, "Cell Index", "Mapped Field Name", true);
        addSheetRow(grid, "Column B", "Movie Title", false);
        addSheetRow(grid, "Column C", "Sublink Path (/dacoit...)", false);
        addSheetRow(grid, "Column D", "Category Stream (tamil-2026-movies)", false);
        addSheetRow(grid, "Column E", "Direct Hub Link URL (Priority Copy)", false);
        addSheetRow(grid, "Column F", "Landing Reference Page Link", false);
        content.addView(grid);

        new AlertDialog.Builder(this)
                .setTitle("Spreadsheet Layout Map")
                .setView(content)
                .setPositiveButton("Acknowledge Plan", null)
                .show();
    }

    private void addSheetRow(LinearLayout host, String col, String desc, boolean isHeader) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        if (isHeader) row.setBackgroundResource(R.drawable.bg_sheet_header);
        row.setPadding(dp(6), dp(4), dp(6), dp(4));

        TextView c = new TextView(this);
        c.setText(col);
        c.setTextSize(11f);
        c.setTypeface(c.getTypeface(), android.graphics.Typeface.BOLD);
        c.setTextColor(ContextCompat.getColor(this,
                isHeader ? R.color.bubble_text_primary : R.color.bubble_blue));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(80),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        c.setLayoutParams(lp);
        row.addView(c);

        TextView d = new TextView(this);
        d.setText(desc);
        d.setTextSize(11f);
        d.setTextColor(ContextCompat.getColor(this,
                isHeader ? R.color.bubble_text_primary : R.color.bubble_text_secondary));
        row.addView(d);

        host.addView(row);
    }

    /** "Add Index Record Manually" dialog */
    private void showAddMovieDialog() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        final EditText name = addDialogField(content, "Movie Title (Col B)");
        final EditText sublink = addDialogField(content, "Sublink Path (Col C)");
        final EditText category = addDialogField(content, "Category (Col D)");
        final EditText link = addDialogField(content, "Link URL (Col E)");
        final EditText pageUrl = addDialogField(content, "Portal Page URL (Col F)");

        new AlertDialog.Builder(this)
                .setTitle("Add Index Record Manually")
                .setView(content)
                .setNegativeButton("Discard", null)
                .setPositiveButton("Insert Entry", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String n = name.getText().toString();
                        if (n.trim().isEmpty()) {
                            Toast.makeText(MainActivity.this,
                                    "Movie title is required!", Toast.LENGTH_SHORT).show();
                        } else {
                            viewModel.addManualMovie(
                                    n,
                                    sublink.getText().toString(),
                                    category.getText().toString(),
                                    link.getText().toString(),
                                    pageUrl.getText().toString(),
                                    null);
                            Toast.makeText(MainActivity.this,
                                    "Record successfully inserted!", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .show();
    }

    private EditText addDialogField(LinearLayout host, String hint) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setTextSize(13f);
        field.setSingleLine(true);
        field.setBackgroundResource(R.drawable.bg_card_12);
        field.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        field.setLayoutParams(lp);
        host.addView(field);
        return field;
    }

    /** "System Sync Detail Log" dialog */
    private void showErrorDetailDialog(final String detail) {
        ScrollView scroll = new ScrollView(this);
        TextView tv = new TextView(this);
        tv.setText(detail == null ? "" : detail);
        tv.setTypeface(android.graphics.Typeface.MONOSPACE);
        tv.setTextSize(11f);
        tv.setTextColor(ContextCompat.getColor(this, R.color.terminal_cyan));
        tv.setPadding(dp(12), dp(12), dp(12), dp(12));
        scroll.addView(tv);
        scroll.setBackgroundResource(R.drawable.bg_terminal_inner);

        new AlertDialog.Builder(this)
                .setTitle("System Sync Detail Log")
                .setView(scroll)
                .setPositiveButton("Copy Details", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        UiUtils.copyToClipboard(MainActivity.this,
                                detail == null ? "" : detail, "System Log Detail");
                        Toast.makeText(MainActivity.this, "Copied to clipboard!",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    /** Options dropdown: import / run scheduler / reset index */
    private void showOptionsMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("Import Spreadsheets (.xlsx/.csv)");
        menu.getMenu().add("Run Scheduler Now");
        menu.getMenu().add("Reset Entire Index");
        menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(android.view.MenuItem item) {
                String title = String.valueOf(item.getTitle());
                if (title.startsWith("Import")) {
                    openFilePicker();
                } else if (title.startsWith("Run")) {
                    btnRunNow.performClick();
                } else {
                    viewModel.clearAllData();
                    Toast.makeText(MainActivity.this,
                            "Movie database entirely wiped!", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
        });
        menu.show();
    }

    // ================================================================
    //  App lock screen (AppLockScreen composable)
    // ================================================================

    private void showAppLock() {
        appLockOverlay.setVisibility(View.VISIBLE);

        final LinearLayout dotsRow = appLockOverlay.findViewById(R.id.lock_dots);
        final TextView message = appLockOverlay.findViewById(R.id.lock_message);
        final LinearLayout keypad = appLockOverlay.findViewById(R.id.lock_keypad);

        final String correctPin = prefs.getAppLockPasscode();
        final StringBuilder entered = new StringBuilder();

        final Runnable renderDots = new Runnable() {
            @Override
            public void run() {
                dotsRow.removeAllViews();
                int total = Math.max(4, correctPin.length());
                for (int i = 0; i < total; i++) {
                    View dot = new View(MainActivity.this);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(14), dp(14));
                    lp.setMarginEnd(dp(14));
                    dot.setLayoutParams(lp);
                    dot.setBackgroundResource(i < entered.length()
                            ? R.drawable.bg_dot_filled : R.drawable.bg_dot_empty);
                    dotsRow.addView(dot);
                }
            }
        };
        renderDots.run();

        keypad.removeAllViews();
        String[][] rows = {
                {"1", "2", "3"},
                {"4", "5", "6"},
                {"7", "8", "9"},
                {"C", "0", "◀"}
        };

        for (final String[] keys : rows) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rp.topMargin = dp(16);
            row.setLayoutParams(rp);

            for (final String key : keys) {
                boolean functional = key.equals("C") || key.equals("◀");

                FrameLayout cell = new FrameLayout(this);
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                cell.setLayoutParams(cp);

                TextView keyView = new TextView(this);
                FrameLayout.LayoutParams kp = new FrameLayout.LayoutParams(dp(68), dp(68));
                kp.gravity = android.view.Gravity.CENTER;
                keyView.setLayoutParams(kp);
                keyView.setGravity(android.view.Gravity.CENTER);
                keyView.setBackgroundResource(
                        functional ? R.drawable.bg_lock_key_fn : R.drawable.bg_lock_key);
                if (key.equals("◀")) {
                    keyView.setText("backspace");
                    keyView.setTextSize(20f);
                    keyView.setTextColor(ContextCompat.getColor(this, R.color.slate_400));
                    keyView.setTypeface(
                            androidx.core.content.res.ResourcesCompat.getFont(this, R.font.material_icons));
                } else {
                    keyView.setText(key);
                    keyView.setTextSize(22f);
                    keyView.setTextColor(ContextCompat.getColor(this,
                            functional ? R.color.slate_400 : R.color.white));
                }

                keyView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        message.setText(R.string.lock_hint);
                        message.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.slate_500));
                        if (key.equals("C")) {
                            entered.setLength(0);
                        } else if (key.equals("◀")) {
                            if (entered.length() > 0) entered.setLength(entered.length() - 1);
                        } else if (entered.length() < correctPin.length()) {
                            entered.append(key);
                            if (entered.length() == correctPin.length()) {
                                if (entered.toString().equals(correctPin)) {
                                    appLockOverlay.setVisibility(View.GONE);
                                    isUnlocked = true;
                                } else {
                                    message.setText("ACCESS_DENIED: Invalid PIN");
                                    message.setTextColor(0xFFEF4444);
                                    entered.setLength(0);
                                }
                            }
                        }
                        renderDots.run();
                    }
                });

                cell.addView(keyView);
                row.addView(cell);
            }
            keypad.addView(row);
        }
    }

    // ================================================================
    //  small helpers
    // ================================================================

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    /** Java equivalent of a Kotlin lambda for TextWatcher. */
    private abstract static class SimpleTextWatcher implements android.text.TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(android.text.Editable s) {
            onTextChanged(s.toString());
        }

        public abstract void onTextChanged(String text);
    }
}
