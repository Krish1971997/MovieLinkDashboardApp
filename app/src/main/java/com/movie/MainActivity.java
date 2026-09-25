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
import com.movie.data.ZohoPreferences;
import com.movie.repository.MovieRepository;
import com.movie.scheduler.SyncRunLog;
import com.movie.scheduler.SyncScheduler;
import com.movie.scheduler.SyncStatus;
import com.movie.ui.CategoryAdapter;
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
 * 6-tab bottom nav:
 *   0 Dashboard (search page - screenshot 1)
 *   1 Sync Hub
 *   2 Setup (current dashboard - scheduler + mini console)
 *   3 Categories (scheduler categories moved out)
 *   4 Config
 *   5 Console (full scheduler log)
 */
public class MainActivity extends AppCompatActivity {

    private static final int TAB_DASHBOARD = 0;
    private static final int TAB_SYNC = 1;
    private static final int TAB_SETUP = 2;
    private static final int TAB_CATEGORIES = 3;
    private static final int TAB_CONFIG = 4;
    private static final int TAB_CONSOLE = 5;

    private MovieViewModel viewModel;
    private SettingsViewModel settingsViewModel;
    private ZohoPreferences prefs;

    private FrameLayout contentHost;
    private View importProgress;
    private View appLockOverlay;

    // Each tab holds its own inflated layout; we swap them in/out via contentHost.
    private View tabDashboard;
    private View tabSync;
    private View tabSetup;
    private View tabCategories;
    private View tabConfig;
    private View tabConsole;

    // ---- Dashboard (search page) widgets ----
    private EditText dashSearchInput;
    private RecyclerView dashMovieList;
    private View dashEmpty;
    private TextView dashMatchBadge;
    private com.movie.ui.MovieAdapter dashAdapter;
    private final List<com.movie.data.MovieRecord> dashFiltered = new ArrayList<>();

    // ---- Setup tab widgets ----
    private SwitchCompat schedEnabled;
    private SwitchCompat schedWifiOnly;
    private EditText schedInterval;
    private View btnRunNow;
    private View btnCancelRun;
    private TextView syncStatusText;
    private TextView lastRunText;
    private View miniConsoleCopy;
    private TextView terminalHint;
    private View terminalExpand;

    // ---- Categories tab widgets ----
    private EditText categorySearch;
    private TextView categoryCount;
    private TextView btnCategorySelectAll;
    private TextView btnCategoryRestore;
    private View btnCategoryAdd;
    private TextView categoryEmpty;
    private RecyclerView categoryList;
    private CategoryAdapter categoryAdapter;
    private final List<CategoryEntity> allCategories = new ArrayList<>();
    private String categoryQuery = "";

    // ---- Console tab widgets ----
    private TextView consoleLog;
    private TextView consoleSummary;
    private TextView runningSchedulersTitle;
    private LinearLayout runningSchedulersList;
    private View consoleCopy;
    private View consoleRefresh;
    private ScrollView consoleScroll;

    // ---- Config tab widgets (unchanged) ----
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

    // ---- Sync tab widgets (unchanged) ----
    private TextView syncFolderValue;
    private TextView syncFileValue;
    private TextView syncCleansValue;
    private View btnFetchSync;
    private View btnBrowseUpload;
    private TextView terminalStatus;

    // ---- UI state ----
    private int activeBottomTab = TAB_DASHBOARD;
    private boolean isClientIdVisible = false;
    private boolean isClientSecretVisible = false;
    private boolean isRefreshTokenVisible = false;
    private boolean showAdvanced = false;
    private boolean isUnlocked = true;
    private boolean syncingSwitchFromCode = false;
    private String terminalStatusText = "status: STANDBY\n> terminal idle.\n> waiting to fetch database workbook...";

    private ActivityResultLauncher<String[]> fileLauncher;
    private ActivityResultLauncher<String> notificationPermissionLauncher;
    private final android.os.Handler consoleTickHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable consoleTicker = new Runnable() {
        @Override public void run() {
            if (activeBottomTab == TAB_CONSOLE) refreshConsole();
            consoleTickHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = new ZohoPreferences(this);
        isUnlocked = !prefs.isAppLockEnabled();

        MovieDatabase database = MovieDatabase.getDatabase(getApplicationContext());
        MovieRepository repository = new MovieRepository(database.movieDao());
        viewModel = new ViewModelProvider(this, new MovieViewModelFactory(repository))
                .get(MovieViewModel.class);
        settingsViewModel = new ViewModelProvider(this, new SettingsViewModelFactory(getApplication()))
                .get(SettingsViewModel.class);

        bindViews();
        setupFilePicker();
        setupBottomNav();
        setupDashboardTab();
        setupSyncTab();
        setupSetupTab();
        setupCategoriesTab();
        setupConsoleTab();
        setupConfigTab();
        observeViewModel();
        setupSchedulerUi();

        if (!isUnlocked) showAppLock();
        consoleTickHandler.postDelayed(consoleTicker, 1000);
        selectTab(TAB_DASHBOARD);
    }

    // ================================================================
    //  View binding + setup
    // ================================================================

    private void bindViews() {
        contentHost = findViewById(R.id.content_host);
        importProgress = findViewById(R.id.import_progress);
        appLockOverlay = findViewById(R.id.app_lock_overlay);

        findViewById(R.id.btn_help).setOnClickListener(v -> showSheetLayoutDialog());
        findViewById(R.id.btn_add).setOnClickListener(v -> showAddMovieDialog());
        findViewById(R.id.btn_options).setOnClickListener(v -> showOptionsMenu(v));
    }

    private void setupFilePicker() {
        fileLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                (ActivityResultCallback<Uri>) uri -> {
                    if (uri != null) viewModel.importFile(MainActivity.this, uri);
                });

        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> { /* run continues regardless */ });
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
        findViewById(R.id.tab_dashboard).setOnClickListener(v -> selectTab(TAB_DASHBOARD));
        findViewById(R.id.tab_sync).setOnClickListener(v -> selectTab(TAB_SYNC));
        findViewById(R.id.tab_setup).setOnClickListener(v -> selectTab(TAB_SETUP));
        findViewById(R.id.tab_categories).setOnClickListener(v -> selectTab(TAB_CATEGORIES));
        findViewById(R.id.tab_config).setOnClickListener(v -> selectTab(TAB_CONFIG));
        findViewById(R.id.tab_console).setOnClickListener(v -> selectTab(TAB_CONSOLE));
    }

    private void selectTab(int tab) {
        activeBottomTab = tab;
        contentHost.removeAllViews();
        switch (tab) {
            case TAB_DASHBOARD:  contentHost.addView(tabDashboard);  refreshDashboard(); break;
            case TAB_SYNC:       contentHost.addView(tabSync);       refreshSyncUi();    break;
            case TAB_SETUP:      contentHost.addView(tabSetup);      refreshSchedulerAndConsole(); break;
            case TAB_CATEGORIES: contentHost.addView(tabCategories); refreshCategoryCount(); break;
            case TAB_CONFIG:     contentHost.addView(tabConfig);     loadConfigIntoFields(); break;
            case TAB_CONSOLE:    contentHost.addView(tabConsole);    refreshConsole();    break;
        }
        updateTabHighlight();
    }

    private void refreshSchedulerAndConsole() {
        refreshSchedulerStatus(SyncStatus.current());
        refreshLastRunLine();
        // also push current log into mini console so we stay in sync with the Console tab
        String history = SyncRunLog.snapshot(this);
        if (history != null && !history.isEmpty() && syncingSwitchFromCode == false) {
            // append history above the in-line status text (cheap, no fancy merging)
            history = history + "\n\n" + (syncStatusText.getText() == null ? "" : syncStatusText.getText().toString());
        }
    }

    private void updateTabHighlight() {
        int[] tabIds = {
                R.id.tab_dashboard, R.id.tab_sync, R.id.tab_setup,
                R.id.tab_categories, R.id.tab_config, R.id.tab_console
        };
        int[] iconIds = {
                R.id.tab_dashboard_icon, R.id.tab_sync_icon, R.id.tab_setup_icon,
                R.id.tab_categories_icon, R.id.tab_config_icon, R.id.tab_console_icon
        };
        int[] labelIds = {
                R.id.tab_dashboard_label, R.id.tab_sync_label, R.id.tab_setup_label,
                R.id.tab_categories_label, R.id.tab_config_label, R.id.tab_console_label
        };
        for (int i = 0; i < tabIds.length; i++) {
            boolean active = (i == activeBottomTab);
            int color = ContextCompat.getColor(this,
                    active ? R.color.bubble_blue : R.color.bubble_text_secondary);
            ((TextView) findViewById(iconIds[i])).setTextColor(color);
            ((TextView) findViewById(labelIds[i])).setTextColor(color);
        }
    }

    // ================================================================
    //  Tab 0 : Dashboard (search page - screenshot 1)
    // ================================================================

    private void setupDashboardTab() {
        tabDashboard = LayoutInflater.from(this).inflate(R.layout.view_tab_search, contentHost, false);
        dashSearchInput = tabDashboard.findViewById(R.id.search_tab_input);
        dashMovieList = tabDashboard.findViewById(R.id.search_movie_list);
        dashEmpty = tabDashboard.findViewById(R.id.search_empty);
        dashMatchBadge = tabDashboard.findViewById(R.id.search_match_badge);

        dashMovieList.setLayoutManager(new LinearLayoutManager(this));
        dashAdapter = new com.movie.ui.MovieAdapter(this, new com.movie.ui.MovieAdapter.Listener() {
            @Override
            public void onToggleCheck(com.movie.data.MovieRecord m, boolean c) { /* no-op */ }
            @Override
            public void onDelete(com.movie.data.MovieRecord m) {
                viewModel.deleteMovie(m.getId());
            }
        });
        dashMovieList.setAdapter(dashAdapter);

        dashSearchInput.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void onTextChanged(String s) { refreshDashboard(); }
        });

        viewModel.getAllMovies().observe(this, movies -> refreshDashboard());
    }

    private void refreshDashboard() {
        if (dashAdapter == null) return;
        String query = dashSearchInput.getText() == null
                ? "" : dashSearchInput.getText().toString().trim().toLowerCase(Locale.ROOT);
        List<com.movie.data.MovieRecord> src = viewModel.getAllMovies().getValue();
        dashFiltered.clear();
        if (src != null) {
            for (com.movie.data.MovieRecord m : src) {
                if (query.isEmpty()
                        || m.getName().toLowerCase(Locale.ROOT).contains(query)
                        || m.getCategory().toLowerCase(Locale.ROOT).contains(query)
                        || m.getSublink().toLowerCase(Locale.ROOT).contains(query)) {
                    dashFiltered.add(m);
                }
            }
        }
        dashAdapter.submit(dashFiltered);
        dashEmpty.setVisibility(dashFiltered.isEmpty() ? View.VISIBLE : View.GONE);
        dashMovieList.setVisibility(dashFiltered.isEmpty() ? View.GONE : View.VISIBLE);
        if (dashMatchBadge != null) {
            dashMatchBadge.setText(dashFiltered.size() + " Matches");
        }
    }

    // ================================================================
    //  Tab 2 : Setup (current Dashboard content)
    // ================================================================

    private void setupSetupTab() {
        tabSetup = LayoutInflater.from(this).inflate(R.layout.view_tab_setup, contentHost, false);

        schedEnabled = tabSetup.findViewById(R.id.scheduler_enabled);
        schedWifiOnly = tabSetup.findViewById(R.id.scheduler_wifi_only);
        schedInterval = tabSetup.findViewById(R.id.scheduler_interval);
        btnRunNow = tabSetup.findViewById(R.id.btn_run_now);
        btnCancelRun = tabSetup.findViewById(R.id.btn_cancel_run);
        syncStatusText = tabSetup.findViewById(R.id.sync_status_text);
        lastRunText = tabSetup.findViewById(R.id.last_run_text);
        miniConsoleCopy = tabSetup.findViewById(R.id.mini_console_copy);
        terminalHint = new TextView(this);
        terminalExpand = tabSetup.findViewById(R.id.terminal_expand);

        setupSchedulerSwitchesAndActions();

        miniConsoleCopy.setOnClickListener(v -> {
            SyncRunLog.copyToClipboard(MainActivity.this);
            String liveText = buildSyncStatusText(SyncRunLog.load(MainActivity.this));
            if (liveText != null && !liveText.isEmpty()) {
                UiUtils.copyToClipboard(MainActivity.this, liveText, "Mini Console");
            }
            Toast.makeText(this, "Console snapshot copied ✓", Toast.LENGTH_SHORT).show();
        });

        terminalExpand.setOnClickListener(v -> {
            selectTab(TAB_CONSOLE);
        });
    }

    private void setupSchedulerSwitchesAndActions() {
        schedEnabled.setOnCheckedChangeListener((b, isChecked) -> {
            if (syncingSwitchFromCode) return;
            settingsViewModel.setSchedulerEnabled(isChecked);
            SyncRunLog.appendLine(MainActivity.this,
                    "Automatic scheduler " + (isChecked ? "ENABLED" : "DISABLED"));
            if (isChecked) {
                requestNotificationPermissionIfNeeded();
                SyncScheduler.enable(MainActivity.this);
                Toast.makeText(this,
                        "Automatic scheduler ENABLED - runs every "
                                + settingsViewModel.getSchedulerIntervalMinutes() + " minutes",
                        Toast.LENGTH_LONG).show();
            } else {
                SyncScheduler.disable(MainActivity.this);
                Toast.makeText(this, "Automatic scheduler DISABLED",
                        Toast.LENGTH_SHORT).show();
            }
            refreshLastRunLine();
        });

        schedWifiOnly.setOnCheckedChangeListener((b, isChecked) -> {
            settingsViewModel.setSchedulerWifiOnly(isChecked);
            SyncRunLog.appendLine(MainActivity.this,
                    "Wi-Fi-only policy " + (isChecked ? "ENABLED" : "DISABLED"));
            SyncScheduler.rescheduleIfEnabled(MainActivity.this);
        });

        schedInterval.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) commitSchedulerInterval();
        });

        btnRunNow.setOnClickListener(v -> {
            int ticked = enabledCategoryCountLocal();
            if (ticked == 0) {
                Toast.makeText(MainActivity.this,
                        "Tick at least one category before running the scheduler. Open Categories tab.",
                        Toast.LENGTH_LONG).show();
                return;
            }
            commitSchedulerInterval();
            requestNotificationPermissionIfNeeded();
            SyncScheduler.runNow(MainActivity.this);
            refreshSchedulerStatus(SyncStatus.current());
            Toast.makeText(this,
                    "Sync run queued. It can take 30-45 minutes; progress appears in the notification and the console below.",
                    Toast.LENGTH_LONG).show();
        });

        btnCancelRun.setOnClickListener(v -> {
            SyncScheduler.cancelRunning(MainActivity.this);
            Toast.makeText(this, "Sync run cancelled", Toast.LENGTH_SHORT).show();
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
        SyncRunLog.appendLine(this, "Run interval set to " + minutes + " min");
        SyncScheduler.rescheduleIfEnabled(this);
    }

    private void setupSchedulerUi() {
        settingsViewModel.observeCategories().observe(this, (Observer<List<CategoryEntity>>) categories -> {
            allCategories.clear();
            if (categories != null) allCategories.addAll(categories);
            applyCategoryFilter();
        });

        settingsViewModel.observeSyncStatus().observe(this, this::refreshSchedulerStatus);

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

    private String buildSyncStatusText(SyncStatus s) {
        SyncStatus x = s == null ? SyncStatus.idle() : s;
        StringBuilder sb = new StringBuilder();
        sb.append("status: ").append(x.getPhase().name()).append('\n');
        sb.append("> step: ").append(x.getStep()).append('\n');
        if (x.getTotal() > 0) {
            sb.append("> progress: ").append(x.getProcessed()).append('/').append(x.getTotal())
                    .append(" (").append(x.getPercent()).append("%)\n");
        }
        if (!x.getDetail().isEmpty()) sb.append("> ").append(x.getDetail()).append('\n');
        if (x.getStartedAt() > 0)  sb.append("> started : ").append(SyncRunLog.formatTime(x.getStartedAt())).append('\n');
        if (x.getFinishedAt() > 0) sb.append("> finished: ").append(SyncRunLog.formatTime(x.getFinishedAt()));
        return sb.toString();
    }

    private void refreshSchedulerStatus(SyncStatus status) {
        if (syncStatusText == null) return;
        syncStatusText.setText(buildSyncStatusText(status));

        SyncStatus s = status == null ? SyncStatus.idle() : status;
        int color;
        switch (s.getPhase()) {
            case SUCCESS:    color = R.color.terminal_green;   break;
            case ERROR:      color = R.color.terminal_red;     break;
            case RUNNING: case QUEUED: color = R.color.terminal_amber; break;
            default:         color = R.color.terminal_cyan;    break;
        }
        syncStatusText.setTextColor(ContextCompat.getColor(this, color));

        boolean busy = s.isRunning();
        btnRunNow.setEnabled(!busy);
        btnRunNow.setAlpha(busy ? 0.5f : 1f);
        btnCancelRun.setVisibility(busy ? View.VISIBLE : View.GONE);
        importProgress.setVisibility(busy ? View.VISIBLE : View.GONE);

        // Mirror into the Console tab so both pages render the same tail.
        if (consoleLog != null) refreshConsole();
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

    // ================================================================
    //  Tab 3 : Categories
    // ================================================================

    private void setupCategoriesTab() {
        tabCategories = LayoutInflater.from(this).inflate(R.layout.view_tab_categories, contentHost, false);
        categorySearch = tabCategories.findViewById(R.id.category_search);
        categoryCount = tabCategories.findViewById(R.id.category_count);
        btnCategorySelectAll = tabCategories.findViewById(R.id.btn_category_select_all);
        btnCategoryRestore = tabCategories.findViewById(R.id.btn_category_restore);
        btnCategoryAdd = tabCategories.findViewById(R.id.btn_category_add);
        categoryEmpty = tabCategories.findViewById(R.id.category_empty);
        categoryList = tabCategories.findViewById(R.id.category_list);

        categoryList.setLayoutManager(new LinearLayoutManager(this));
        categoryAdapter = new CategoryAdapter(new CategoryAdapter.Listener() {
            @Override public void onToggleEnabled(CategoryEntity c, boolean enabled) {
                settingsViewModel.setCategoryEnabled(c, enabled);
                SyncRunLog.appendLine(MainActivity.this,
                        "Category " + c.getName() + " " + (enabled ? "TICKED" : "UNTICKED"));
            }
            @Override public void onEdit(CategoryEntity c) { showCategoryDialog(c); }
            @Override public void onDelete(CategoryEntity c) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Delete category")
                        .setMessage("Remove \"" + c.getPath() + "\" from the scheduler list?")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Delete", (d, w) -> {
                            settingsViewModel.deleteCategory(c);
                            Toast.makeText(MainActivity.this, "Category deleted",
                                    Toast.LENGTH_SHORT).show();
                            SyncRunLog.appendLine(MainActivity.this, "Deleted category " + c.getName());
                        }).show();
            }
        });
        categoryList.setAdapter(categoryAdapter);

        categorySearch.addTextChangedListener(new SimpleTextWatcher() {
            @Override public void onTextChanged(String s) {
                categoryQuery = s;
                applyCategoryFilter();
            }
        });

        btnCategoryAdd.setOnClickListener(v -> showCategoryDialog(null));

        btnCategorySelectAll.setOnClickListener(v -> {
            boolean allTicked = allCategoriesTicked();
            settingsViewModel.setAllCategoriesEnabled(!allTicked);
            for (CategoryEntity c : allCategories) c.setEnabled(!allTicked);
            applyCategoryFilter();
            SyncRunLog.appendLine(MainActivity.this,
                    allTicked ? "All categories UNTICKED" : "All categories TICKED");
            Toast.makeText(this,
                    !allTicked ? "All categories ticked" : "All categories unticked",
                    Toast.LENGTH_SHORT).show();
        });

        btnCategoryRestore.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Restore default list")
                    .setMessage("Replace the current category list with the original "
                            + "subcategory array? Your custom entries will be lost.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Restore", (d, w) -> {
                        settingsViewModel.restoreDefaultCategories();
                        SyncRunLog.appendLine(MainActivity.this, "Default category list restored");
                        Toast.makeText(this, "Default categories restored",
                                Toast.LENGTH_SHORT).show();
                    }).show();
        });
    }

    private boolean allCategoriesTicked() {
        if (allCategories.isEmpty()) return false;
        for (CategoryEntity c : allCategories) if (!c.isEnabled()) return false;
        return true;
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
                        (DialogInterface.OnClickListener) (dialog, which) -> {
                            String raw = path.getText().toString();
                            String normalized = CategoryEntity.normalizePath(raw);
                            if (normalized.length() <= 2) {
                                Toast.makeText(this,
                                        "Category path cannot be empty", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            if (entity == null) {
                                settingsViewModel.addCategory(normalized);
                                SyncRunLog.appendLine(MainActivity.this,
                                        "Added category " + normalized);
                                Toast.makeText(this,
                                        "Category added (unticked by default)",
                                        Toast.LENGTH_SHORT).show();
                            } else {
                                entity.setPath(normalized);
                                entity.setDisplayName(CategoryEntity.displayNameFor(normalized));
                                settingsViewModel.updateCategory(entity);
                                Toast.makeText(this, "Category updated",
                                        Toast.LENGTH_SHORT).show();
                            }
                        }).show();
    }

    // ================================================================
    //  Tab 5 : Console (full scheduler log)
    // ================================================================

    private View consoleClear; // Declared/Initialized consoleClear

    private void setupConsoleTab() {
        tabConsole = LayoutInflater.from(this).inflate(R.layout.view_tab_console, contentHost, false);
        consoleLog = tabConsole.findViewById(R.id.console_log);
        consoleSummary = tabConsole.findViewById(R.id.console_summary);
        runningSchedulersTitle = tabConsole.findViewById(R.id.running_schedulers_title);
        runningSchedulersList = tabConsole.findViewById(R.id.running_schedulers_list);
        consoleCopy = tabConsole.findViewById(R.id.console_copy);
        consoleRefresh = tabConsole.findViewById(R.id.console_refresh);
        consoleClear = tabConsole.findViewById(R.id.console_clear); // Binding console_clear
        consoleScroll = tabConsole.findViewById(R.id.console_scroll);

        consoleCopy.setOnClickListener(v -> {
            SyncRunLog.copyToClipboard(MainActivity.this);
            Toast.makeText(this, "Full log copied ✓", Toast.LENGTH_SHORT).show();
        });
        consoleRefresh.setOnClickListener(v -> refreshConsole());

        // Added listener for Clear action
        consoleClear.setOnClickListener(v -> {
            SyncRunLog.clearLog(MainActivity.this);
            refreshConsole();
            Toast.makeText(this, "Console logs cleared ✓", Toast.LENGTH_SHORT).show();
        });
    }

    private void refreshConsole() {
        if (consoleLog == null) return;
        String full = SyncRunLog.snapshot(this);
        consoleLog.setText(full.isEmpty() ? "status: STANDBY\n> no logs yet. Run the scheduler to see live output." : full);

        SyncStatus saved = SyncRunLog.load(this);
        StringBuilder sb = new StringBuilder();
        sb.append("phase: ").append(saved.getPhase().name())
          .append("  |  step: ").append(saved.getStep());
        if (saved.getTotal() > 0) {
            sb.append("  |  ").append(saved.getProcessed()).append('/').append(saved.getTotal())
              .append(" (").append(saved.getPercent()).append("%)");
        }
        sb.append("  |  runs: ").append(SyncRunLog.getRunCount(this));
        consoleSummary.setText(sb.toString());
        refreshRunningSchedulers(saved);

        if (consoleScroll != null) consoleScroll.post(() -> consoleScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void refreshRunningSchedulers(SyncStatus s) {
        if (runningSchedulersList == null) return;
        runningSchedulersList.removeAllViews();
        boolean running = s != null && s.isRunning();
        runningSchedulersTitle.setText("Running Schedulers (" + (running ? 1 : 0) + ")");
        if (running) {
            addRunningSchedulerRow(runningSchedulersList, s);
        } else {
            TextView empty = new TextView(this);
            empty.setText("No schedulers currently running.");
            empty.setTextColor(ContextCompat.getColor(this, R.color.bubble_text_secondary));
            empty.setTextSize(11f);
            runningSchedulersList.addView(empty);
        }
    }

    private void addRunningSchedulerRow(LinearLayout host, SyncStatus s) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.bg_card_12);
        row.setPadding(dp(10), dp(10), dp(10), dp(10));
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView name = new TextView(this);
        name.setText("movie_link_sync_work");
        name.setTextColor(ContextCompat.getColor(this, R.color.white));
        name.setTextSize(12f);
        name.setTypeface(null, android.graphics.Typeface.BOLD);
        info.addView(name);

        TextView started = new TextView(this);
        started.setText("Started: " + SyncRunLog.formatTime(s.getStartedAt()));
        started.setTextColor(ContextCompat.getColor(this, R.color.bubble_text_secondary));
        started.setTextSize(10f);
        info.addView(started);

        TextView elapsed = new TextView(this);
        elapsed.setText("Running for: " + formatElapsed(s.getStartedAt()));
        elapsed.setTextColor(ContextCompat.getColor(this, R.color.terminal_amber));
        elapsed.setTextSize(10f);
        elapsed.setTypeface(android.graphics.Typeface.MONOSPACE);
        info.addView(elapsed);

        row.addView(info);

        TextView stop = new TextView(this);
        stop.setText("Stop");
        stop.setTextColor(0xFFFF5252);
        stop.setTextSize(11f);
        stop.setTypeface(null, android.graphics.Typeface.BOLD);
        stop.setGravity(android.view.Gravity.CENTER);
        stop.setBackgroundResource(R.drawable.bg_card_16);
        stop.setPadding(dp(12), dp(8), dp(12), dp(8));
        stop.setClickable(true);
        stop.setFocusable(true);
        stop.setOnClickListener(v -> {
            SyncScheduler.cancelRunning(MainActivity.this);
            Toast.makeText(this, "Sync run cancelled", Toast.LENGTH_SHORT).show();
            refreshConsole();
            refreshSchedulerStatus(SyncStatus.current());
        });
        row.addView(stop);

        host.addView(row);
    }

    private String formatElapsed(long startedAt) {
        if (startedAt <= 0) return "--:--";
        long elapsedMs = Math.max(0, System.currentTimeMillis() - startedAt);
        long totalSec = elapsedMs / 1000;
        long h = totalSec / 3600, m = (totalSec % 3600) / 60, sec = totalSec % 60;
        return h > 0
                ? String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, sec)
                : String.format(Locale.getDefault(), "%02d:%02d", m, sec);
    }

    // ================================================================
    //  Tab 1 : Sync Hub (unchanged from base version)
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
        View terminalExpand = tabSync.findViewById(R.id.terminal_expand);

        btnFetchSync.setOnClickListener(v ->
                viewModel.syncFromZoho(MainActivity.this));
        btnBrowseUpload.setOnClickListener(v -> openFilePicker());
        terminalExpand.setOnClickListener(v -> showErrorDetailDialog(terminalStatusText));
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
    //  Tab 4 : Config (unchanged from base version)
    // ================================================================

    private void setupConfigTab() {
        tabConfig = LayoutInflater.from(this).inflate(R.layout.view_tab_config, contentHost, false);

        cfgBaseUrl            = tabConfig.findViewById(R.id.config_base_url);
        cfgClientId           = tabConfig.findViewById(R.id.config_client_id);
        cfgClientSecret       = tabConfig.findViewById(R.id.config_client_secret);
        cfgRefreshToken       = tabConfig.findViewById(R.id.config_refresh_token);
        cfgFolderId           = tabConfig.findViewById(R.id.config_folder_id);
        cfgFileName           = tabConfig.findViewById(R.id.config_file_name);
        cfgExtension          = tabConfig.findViewById(R.id.config_extension);
        cfgAccountsServer     = tabConfig.findViewById(R.id.config_accounts_server);
        cfgApiServer          = tabConfig.findViewById(R.id.config_api_server);
        cfgZohoAccountsUrl    = tabConfig.findViewById(R.id.config_zoho_accounts_url);
        cfgWorkdriveApiUrl    = tabConfig.findViewById(R.id.config_workdrive_api_url);
        cfgWorkdriveListUrl   = tabConfig.findViewById(R.id.config_workdrive_list_url);
        cfgWorkdriveDownloadUrl= tabConfig.findViewById(R.id.config_workdrive_download_url);
        cfgPin                = tabConfig.findViewById(R.id.config_pin);
        cfgClearOld           = tabConfig.findViewById(R.id.config_clear_old);
        cfgAppLock            = tabConfig.findViewById(R.id.config_app_lock);
        cfgPinContainer       = tabConfig.findViewById(R.id.config_pin_container);
        cfgSave               = tabConfig.findViewById(R.id.config_save);
        cfgAdvancedToggle     = tabConfig.findViewById(R.id.config_advanced_toggle);
        cfgAdvancedContainer  = tabConfig.findViewById(R.id.config_advanced_container);
        cfgAdvancedIcon       = tabConfig.findViewById(R.id.config_advanced_icon);
        cfgAdvancedLabel      = tabConfig.findViewById(R.id.config_advanced_label);

        setupVisibilityToggle(tabConfig.findViewById(R.id.config_client_id_toggle), cfgClientId, true);
        setupVisibilityToggle(tabConfig.findViewById(R.id.config_client_secret_toggle), cfgClientSecret, false);
        setupVisibilityToggle(tabConfig.findViewById(R.id.config_refresh_token_toggle), cfgRefreshToken, false);

        cfgAppLock.setOnCheckedChangeListener((b, c) ->
                cfgPinContainer.setVisibility(c ? View.VISIBLE : View.GONE));

        cfgAdvancedToggle.setOnClickListener(v -> {
            showAdvanced = !showAdvanced;
            cfgAdvancedContainer.setVisibility(showAdvanced ? View.VISIBLE : View.GONE);
            cfgAdvancedIcon.setText(showAdvanced ? "expand_less" : "expand_more");
            cfgAdvancedLabel.setText(showAdvanced
                    ? "Generic region settings"
                    : "Generic region settings (Advanced Regional Hostings)");
        });

        cfgSave.setOnClickListener(v -> saveConfig());
    }

    private void setupVisibilityToggle(View toggle, final EditText field, final boolean isClientId) {
        toggle.setOnClickListener(v -> {
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
        });
    }

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

        if (cfgAppLock.isChecked()) {
            String pin = cfgPin.getText().toString().trim();
            if (pin.length() < 4) {
                Toast.makeText(this,
                        "ERROR: Security PIN code is too short! Must be at least 4 digits.",
                        Toast.LENGTH_LONG).show();
                return;
            }
        }

        p.setBaseUrl(cfgBaseUrl.getText().toString());
        p.setClientId(cfgClientId.getText().toString().trim());
        p.setClientSecret(cfgClientSecret.getText().toString().trim());
        p.setRefreshToken(cfgRefreshToken.getText().toString().trim());
        p.setFolderId(cfgFolderId.getText().toString().trim());

        p.setFileName(cfgFileName.getText().toString().trim());
        p.setDefaultExtension(cfgExtension.getText().toString().trim());
        p.setAccountsServer(cfgAccountsServer.getText().toString().trim());
        p.setApiServer(cfgApiServer.getText().toString().trim());

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
        SyncRunLog.appendLine(this, "Config tab saved");
    }

    // ================================================================
    //  LiveData observers
    // ================================================================

    private void observeViewModel() {
        viewModel.getImportingState().observe(this, this::handleImportingState);
    }

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
                .setPositiveButton("Insert Entry", (d, w) -> {
                    String n = name.getText().toString();
                    if (n.trim().isEmpty()) {
                        Toast.makeText(this, "Movie title is required!",
                                Toast.LENGTH_SHORT).show();
                    } else {
                        viewModel.addManualMovie(
                                n,
                                sublink.getText().toString(),
                                category.getText().toString(),
                                link.getText().toString(),
                                pageUrl.getText().toString(),
                                null);
                        Toast.makeText(this, "Record successfully inserted!",
                                Toast.LENGTH_SHORT).show();
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
                .setPositiveButton("Copy Details", (d, w) -> {
                    UiUtils.copyToClipboard(this,
                            detail == null ? "" : detail, "System Log Detail");
                    Toast.makeText(this, "Copied to clipboard!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Close", null)
                .show();
    }

    /** Options dropdown */
    private void showOptionsMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("Import Spreadsheets (.xlsx/.csv)");
        menu.getMenu().add("Run Scheduler Now");
        menu.getMenu().add("Open Console Tab");
        menu.getMenu().add("Reset Entire Index");
        menu.setOnMenuItemClickListener(item -> {
            String title = String.valueOf(item.getTitle());
            if (title.startsWith("Import")) {
                openFilePicker();
            } else if (title.startsWith("Run")) {
                selectTab(TAB_SETUP);
                btnRunNow.performClick();
            } else if (title.startsWith("Open Console")) {
                selectTab(TAB_CONSOLE);
            } else {
                viewModel.clearAllData();
                Toast.makeText(this,
                        "Movie database entirely wiped!", Toast.LENGTH_SHORT).show();
            }
            return true;
        });
        menu.show();
    }

    // ================================================================
    //  App lock screen
    // ================================================================

    private void showAppLock() {
        appLockOverlay.setVisibility(View.VISIBLE);

        final LinearLayout dotsRow = appLockOverlay.findViewById(R.id.lock_dots);
        final TextView message = appLockOverlay.findViewById(R.id.lock_message);
        final LinearLayout keypad = appLockOverlay.findViewById(R.id.lock_keypad);

        final String correctPin = prefs.getAppLockPasscode();
        final StringBuilder entered = new StringBuilder();

        final Runnable renderDots = () -> {
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
        };
        renderDots.run();

        keypad.removeAllViews();
        String[][] rows = { {"1","2","3"}, {"4","5","6"}, {"7","8","9"}, {"C","0","◀"} };
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

                keyView.setOnClickListener(v -> {
                    message.setText(R.string.lock_hint);
                    message.setTextColor(ContextCompat.getColor(this, R.color.slate_500));
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
                });

                cell.addView(keyView);
                row.addView(cell);
            }
            keypad.addView(row);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        consoleTickHandler.removeCallbacks(consoleTicker);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    /** Java equivalent of a Kotlin lambda for TextWatcher. */
    private abstract static class SimpleTextWatcher implements android.text.TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        @Override public void afterTextChanged(android.text.Editable s) { onTextChanged(s.toString()); }
        public abstract void onTextChanged(String text);
    }
}
