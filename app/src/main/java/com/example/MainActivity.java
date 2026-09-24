package com.example;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
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
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.data.MovieDatabase;
import com.example.data.MovieRecord;
import com.example.data.ZohoPreferences;
import com.example.repository.MovieRepository;
import com.example.ui.CircleGaugeView;
import com.example.ui.MovieAdapter;
import com.example.util.UiUtils;
import com.example.viewmodel.ImportingState;
import com.example.viewmodel.MovieViewModel;
import com.example.viewmodel.MovieViewModelFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Java port of MainActivity.kt + DashboardScreen.kt.
 *
 * The Compose UI is reproduced with XML layouts (activity_main.xml plus one layout per
 * bottom tab) and driven from this Activity. Compose state (remember { mutableStateOf })
 * becomes plain fields, collectAsStateWithLifecycle() becomes LiveData observers and
 * viewModel(...) becomes ViewModelProvider with MovieViewModelFactory.
 */
public class MainActivity extends AppCompatActivity {

    // ---- ViewModel ----
    private MovieViewModel viewModel;
    private ZohoPreferences prefs;

    // ---- host views ----
    private FrameLayout contentHost;
    private View bottomActionContainer;
    private LinearLayout bottomActionButton;
    private TextView bottomActionIcon;
    private TextView bottomActionText;
    private View importProgress;
    private View appLockOverlay;

    // ---- tab views ----
    private View tabDashboard;
    private View tabSearch;
    private View tabSync;
    private View tabConfig;

    // ---- dashboard tab widgets ----
    private CircleGaugeView gauge;
    private TextView gaugePercent;
    private TextView gaugeCaption;
    private TextView optimizerCaption;
    private EditText dashSearchInput;
    private TextView dashSearchClear;
    private LinearLayout categoryChips;
    private TextView matchCount;
    private TextView selectAllLabel;
    private RecyclerView movieList;
    private View emptyState;
    private View emptyImport;
    private TextView btnUpgrade;

    private MovieAdapter dashAdapter;

    // ---- search tab widgets ----
    private EditText searchTabInput;
    private TextView searchTabClear;
    private LinearLayout searchHistoryRow;
    private LinearLayout searchCategoryChips;
    private LinearLayout statusRow;
    private LinearLayout searchBatchRow;
    private TextView searchSelectAll;
    private TextView searchCopy;
    private TextView searchClearSel;
    private TextView searchMatchBadge;
    private RecyclerView searchMovieList;
    private View searchEmpty;

    private MovieAdapter searchAdapter;

    // ---- sync tab widgets ----
    private TextView syncFolderValue;
    private TextView syncFileValue;
    private TextView syncCleansValue;
    private View btnFetchSync;
    private View btnBrowseUpload;
    private TextView terminalStatus;
    private TextView terminalHint;
    private View terminalExpand;

    // ---- config tab widgets ----
    private EditText cfgClientId;
    private EditText cfgClientSecret;
    private EditText cfgRefreshToken;
    private EditText cfgFolderId;
    private EditText cfgFileName;
    private EditText cfgExtension;
    private EditText cfgAccountsServer;
    private EditText cfgApiServer;
    private EditText cfgPin;
    private SwitchCompat cfgClearOld;
    private SwitchCompat cfgAppLock;
    private View cfgPinContainer;
    private View cfgSave;
    private View cfgAdvancedToggle;
    private View cfgAdvancedContainer;
    private TextView cfgAdvancedIcon;
    private TextView cfgAdvancedLabel;

    // ---- Compose-equivalent UI state ----
    private int activeBottomTab = 2; // 0: Search, 1: Sync Hub, 2: Dashboard, 3: Config
    private final Set<Integer> selectedMovieIds = new LinkedHashSet<>();
    private String searchTabQuery = "";
    private String searchTabSelectedCategory = null;
    private String searchTabSelectedStatus = "All";
    private final Set<Integer> searchTabSelectedMovieIds = new LinkedHashSet<>();
    private final List<String> searchHistory = new ArrayList<>();
    private boolean isClientIdVisible = false;
    private boolean isClientSecretVisible = false;
    private boolean isRefreshTokenVisible = false;
    private boolean showAdvanced = false;
    private boolean isUnlocked = true;
    private String terminalStatusText = "status: STANDBY\n> terminal idle.\n> waiting to fetch database workbook...";

    private List<MovieRecord> lastAllMovies = new ArrayList<>();
    private List<MovieRecord> lastFilteredMovies = new ArrayList<>();
    private List<String> lastCategories = new ArrayList<>();

    private ActivityResultLauncher<String[]> fileLauncher;

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

        bindViews();
        setupFilePicker();
        setupBottomNav();
        setupDashboardTab();
        setupSearchTab();
        setupSyncTab();
        setupConfigTab();
        observeViewModel();

        if (!isUnlocked) {
            showAppLock();
        }
        selectTab(2);
    }

    // ================================================================
    //  view binding + setup
    // ================================================================

    private void bindViews() {
        contentHost = findViewById(R.id.content_host);
        bottomActionContainer = findViewById(R.id.bottom_action_container);
        bottomActionButton = findViewById(R.id.bottom_action_button);
        bottomActionIcon = findViewById(R.id.bottom_action_icon);
        bottomActionText = findViewById(R.id.bottom_action_text);
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
    }

    private void openFilePicker() {
        fileLauncher.launch(new String[]{
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "text/comma-separated-values",
                "text/plain"
        });
    }

    private void setupBottomNav() {
        findViewById(R.id.tab_search).setOnClickListener(new View.OnClickListener() {
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
        findViewById(R.id.tab_dashboard).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectTab(2);
                viewModel.selectCategory(null);
            }
        });
        findViewById(R.id.tab_config).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectTab(3);
            }
        });
    }

    private void selectTab(int tab) {
        activeBottomTab = tab;

        contentHost.removeAllViews();
        if (tab == 0) contentHost.addView(tabSearch);
        else if (tab == 1) contentHost.addView(tabSync);
        else if (tab == 2) contentHost.addView(tabDashboard);
        else contentHost.addView(tabConfig);

        // Giant gradient bottom action button is shown only on the Dashboard tab
        if (tab == 2) {
            bottomActionContainer.setVisibility(View.VISIBLE);
        } else {
            bottomActionContainer.setVisibility(View.GONE);
        }

        if (tab == 3) {
            loadConfigIntoFields();
        }
        if (tab == 2) {
            refreshDashboardUi();
        }
        updateTabHighlight();
    }

    private void updateTabHighlight() {
        int[] tabIds = {R.id.tab_search, R.id.tab_sync, R.id.tab_dashboard, R.id.tab_config};
        int[] iconIds = {R.id.tab_search_icon, R.id.tab_sync_icon, R.id.tab_dashboard_icon, R.id.tab_config_icon};
        int[] labelIds = {R.id.tab_search_label, R.id.tab_sync_label, R.id.tab_dashboard_label, R.id.tab_config_label};

        for (int i = 0; i < tabIds.length; i++) {
            boolean active = (i == activeBottomTab);
            int color = getResources().getColor(active ? R.color.bubble_blue : R.color.bubble_text_secondary);
            ((TextView) findViewById(iconIds[i])).setTextColor(color);
            ((TextView) findViewById(labelIds[i])).setTextColor(color);
        }
    }

    // ================================================================
    //  Dashboard tab (activeBottomTab == 2)
    // ================================================================

    private void setupDashboardTab() {
        tabDashboard = LayoutInflater.from(this).inflate(R.layout.view_tab_dashboard, contentHost, false);

        gauge = tabDashboard.findViewById(R.id.gauge);
        gaugePercent = tabDashboard.findViewById(R.id.gauge_percent);
        gaugeCaption = tabDashboard.findViewById(R.id.gauge_caption);
        optimizerCaption = tabDashboard.findViewById(R.id.optimizer_caption);
        dashSearchInput = tabDashboard.findViewById(R.id.search_input);
        dashSearchClear = tabDashboard.findViewById(R.id.search_clear);
        categoryChips = tabDashboard.findViewById(R.id.category_chips);
        matchCount = tabDashboard.findViewById(R.id.match_count);
        selectAllLabel = tabDashboard.findViewById(R.id.select_all);
        movieList = tabDashboard.findViewById(R.id.movie_list);
        emptyState = tabDashboard.findViewById(R.id.empty_state);
        emptyImport = tabDashboard.findViewById(R.id.empty_import);
        btnUpgrade = tabDashboard.findViewById(R.id.btn_upgrade);

        movieList.setLayoutManager(new LinearLayoutManager(this));
        movieList.setNestedScrollingEnabled(false);

        dashAdapter = new MovieAdapter(this, new MovieAdapter.Listener() {
            @Override
            public void onToggleCheck(MovieRecord movie, boolean checked) {
                if (checked) selectedMovieIds.add(movie.getId());
                else selectedMovieIds.remove(movie.getId());
                refreshDashboardUi();
            }

            @Override
            public void onDelete(MovieRecord movie) {
                viewModel.deleteMovie(movie.getId());
                selectedMovieIds.remove(movie.getId());
            }
        });
        movieList.setAdapter(dashAdapter);

        dashSearchInput.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void onTextChanged(String text) {
                viewModel.updateSearchQuery(text);
                dashSearchClear.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
            }
        });
        dashSearchClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                viewModel.updateSearchQuery("");
            }
        });

        selectAllLabel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (selectedMovieIds.size() == lastFilteredMovies.size()) {
                    selectedMovieIds.clear();
                } else {
                    for (MovieRecord m : lastFilteredMovies) selectedMovieIds.add(m.getId());
                }
                refreshDashboardUi();
            }
        });

        emptyImport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openFilePicker();
            }
        });

        btnUpgrade.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(MainActivity.this,
                        "Pro Sync features activated in manual sandbox!", Toast.LENGTH_SHORT).show();
            }
        });

        // Giant bottom action button: copies selected links, or opens the import picker
        bottomActionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!selectedMovieIds.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    int n = 0;
                    for (MovieRecord m : lastFilteredMovies) {
                        if (selectedMovieIds.contains(m.getId())) {
                            if (n > 0) sb.append("\n");
                            sb.append(m.getLink().isEmpty() ? m.getPageUrl() : m.getLink());
                            n++;
                        }
                    }
                    UiUtils.copyToClipboard(MainActivity.this, sb.toString(), "Batch Links");
                    Toast.makeText(MainActivity.this,
                            "Copied " + n + " links directly to clipboard!", Toast.LENGTH_LONG).show();
                } else {
                    openFilePicker();
                }
            }
        });
    }

    private void refreshDashboardUi() {
        lastFilteredMovies = viewModel.getFilteredMovies().getValue() == null
                ? new ArrayList<MovieRecord>()
                : viewModel.getFilteredMovies().getValue();

        boolean empty = lastFilteredMovies.isEmpty();
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        movieList.setVisibility(empty ? View.GONE : View.VISIBLE);

        dashAdapter.setSelectedIds(selectedMovieIds);
        dashAdapter.submit(lastFilteredMovies);

        matchCount.setText(lastFilteredMovies.size() + " records match filters");
        if (!lastFilteredMovies.isEmpty()) {
            selectAllLabel.setVisibility(View.VISIBLE);
            selectAllLabel.setText(selectedMovieIds.size() == lastFilteredMovies.size()
                    ? "Deselect all" : "Select all");
        } else {
            selectAllLabel.setVisibility(View.GONE);
        }

        // INDEX SPACE gauge
        int count = lastFilteredMovies.size();
        float progress = Math.min(1f, count / 250f);
        gauge.setProgress(progress);
        gaugePercent.setText(((int) (progress * 100)) + "%");
        gaugeCaption.setText(count + " of 250 links used");

        optimizerCaption.setText("Selected: " + selectedMovieIds.size()
                + " files ready to be copied or sanitized below. Automapping aligns your Excel files.");

        boolean isAnyChecked = !selectedMovieIds.isEmpty();
        bottomActionIcon.setText(isAnyChecked ? "done_all" : "cloud_upload");
        bottomActionText.setText(isAnyChecked
                ? "Copy Selected (" + selectedMovieIds.size() + " links)"
                : "Import New Movie File");
        bottomActionButton.setBackgroundResource(
                isAnyChecked ? R.drawable.bg_gradient_button : R.drawable.bg_gradient_button_soft);

        rebuildCategoryChips(categoryChips, null, new CategoryPicked() {
            @Override
            public void onPicked(String category) {
                viewModel.selectCategory(category);
            }
        });
    }

    // ================================================================
    //  Search tab (activeBottomTab == 0)
    // ================================================================

    private void setupSearchTab() {
        tabSearch = LayoutInflater.from(this).inflate(R.layout.view_tab_search, contentHost, false);

        searchTabInput = tabSearch.findViewById(R.id.search_tab_input);
        searchTabClear = tabSearch.findViewById(R.id.search_tab_clear);
        searchHistoryRow = tabSearch.findViewById(R.id.search_history_row);
        searchCategoryChips = tabSearch.findViewById(R.id.search_category_chips);
        statusRow = tabSearch.findViewById(R.id.status_row);
        searchBatchRow = tabSearch.findViewById(R.id.search_batch_row);
        searchSelectAll = tabSearch.findViewById(R.id.search_select_all);
        searchCopy = tabSearch.findViewById(R.id.search_copy);
        searchClearSel = tabSearch.findViewById(R.id.search_clear_sel);
        searchMatchBadge = tabSearch.findViewById(R.id.search_match_badge);
        searchMovieList = tabSearch.findViewById(R.id.search_movie_list);
        searchEmpty = tabSearch.findViewById(R.id.search_empty);

        searchMovieList.setLayoutManager(new LinearLayoutManager(this));
        searchAdapter = new MovieAdapter(this, new MovieAdapter.Listener() {
            @Override
            public void onToggleCheck(MovieRecord movie, boolean checked) {
                if (checked) searchTabSelectedMovieIds.add(movie.getId());
                else searchTabSelectedMovieIds.remove(movie.getId());
                refreshSearchUi();
            }

            @Override
            public void onDelete(MovieRecord movie) {
                viewModel.deleteMovie(movie.getId());
                searchTabSelectedMovieIds.remove(movie.getId());
            }
        });
        searchMovieList.setAdapter(searchAdapter);

        // searchHistory is initialised with the same presets as the Kotlin original
        searchHistory.add("2026");
        searchHistory.add("Amaran");
        searchHistory.add("Coolie");
        searchHistory.add("Vettaiyan");

        searchTabInput.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void onTextChanged(String text) {
                searchTabQuery = text;
                searchTabClear.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
                refreshSearchUi();
            }
        });
        searchTabClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                searchTabInput.setText("");
            }
        });

        searchSelectAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                List<MovieRecord> filtered = filterSearchTab();
                if (searchTabSelectedMovieIds.size() == filtered.size()) {
                    searchTabSelectedMovieIds.clear();
                } else {
                    for (MovieRecord m : filtered) searchTabSelectedMovieIds.add(m.getId());
                }
                refreshSearchUi();
            }
        });

        searchCopy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                List<MovieRecord> matchedSelected = new ArrayList<>();
                for (MovieRecord m : filterSearchTab()) {
                    if (searchTabSelectedMovieIds.contains(m.getId())) matchedSelected.add(m);
                }
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < matchedSelected.size(); i++) {
                    if (i > 0) sb.append("\n");
                    MovieRecord m = matchedSelected.get(i);
                    sb.append(m.getLink().isEmpty() ? m.getPageUrl() : m.getLink());
                }
                UiUtils.copyToClipboard(MainActivity.this, sb.toString(), "Batch Search Matches");
                Toast.makeText(MainActivity.this,
                        "Copied " + matchedSelected.size() + " search result links!",
                        Toast.LENGTH_LONG).show();
            }
        });

        searchClearSel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                searchTabSelectedMovieIds.clear();
                refreshSearchUi();
            }
        });

        buildStatusRow();
    }

    private List<MovieRecord> filterSearchTab() {
        List<MovieRecord> result = new ArrayList<>();
        String q = searchTabQuery.toLowerCase(Locale.ROOT);
        for (MovieRecord movie : lastAllMovies) {
            boolean matchesQuery = searchTabQuery.isEmpty()
                    || movie.getName().toLowerCase(Locale.ROOT).contains(q)
                    || movie.getCategory().toLowerCase(Locale.ROOT).contains(q)
                    || movie.getSublink().toLowerCase(Locale.ROOT).contains(q);
            boolean matchesCategory = searchTabSelectedCategory == null
                    || movie.getCategory().equalsIgnoreCase(searchTabSelectedCategory);

            boolean isTamilYear = movie.getCategory().contains("2026");
            boolean matchesStatus;
            if ("Completed".equals(searchTabSelectedStatus)) {
                matchesStatus = isTamilYear;
            } else if ("In process".equals(searchTabSelectedStatus)) {
                matchesStatus = !isTamilYear;
            } else {
                matchesStatus = true;
            }

            if (matchesQuery && matchesCategory && matchesStatus) result.add(movie);
        }
        return result;
    }

    private void refreshSearchUi() {
        List<MovieRecord> filtered = filterSearchTab();

        searchMatchBadge.setText(filtered.size() + " Matches");
        searchAdapter.setSelectedIds(searchTabSelectedMovieIds);
        searchAdapter.submit(filtered);

        searchEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        searchMovieList.setVisibility(filtered.isEmpty() ? View.GONE : View.VISIBLE);

        if (filtered.isEmpty()) {
            searchBatchRow.setVisibility(View.GONE);
        } else {
            searchBatchRow.setVisibility(View.VISIBLE);
            boolean all = searchTabSelectedMovieIds.size() == filtered.size();
            searchSelectAll.setText(all ? "Deselect Search Results" : "Select Search Results");

            if (searchTabSelectedMovieIds.isEmpty()) {
                searchCopy.setVisibility(View.GONE);
                searchClearSel.setVisibility(View.GONE);
            } else {
                searchCopy.setVisibility(View.VISIBLE);
                searchClearSel.setVisibility(View.VISIBLE);
                searchCopy.setText("Copy " + searchTabSelectedMovieIds.size());
            }
        }

        rebuildHistoryChips();
        rebuildCategoryChips(searchCategoryChips, searchTabSelectedCategory, new CategoryPicked() {
            @Override
            public void onPicked(String category) {
                searchTabSelectedCategory = category;
                refreshSearchUi();
            }
        });
        buildStatusRow();
    }

    private void rebuildHistoryChips() {
        searchHistoryRow.removeAllViews();
        TextView label = new TextView(this);
        label.setText("Recent Searches:");
        label.setTextSize(10f);
        label.setTextColor(getResources().getColor(R.color.bubble_text_muted));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(dp(4));
        label.setLayoutParams(lp);
        searchHistoryRow.addView(label);

        for (final String tag : searchHistory) {
            TextView chip = new TextView(this);
            chip.setText(tag);
            chip.setTextSize(10f);
            chip.setTextColor(getResources().getColor(R.color.bubble_text_primary));
            chip.setPadding(dp(10), dp(4), dp(10), dp(4));
            chip.setBackgroundResource(R.drawable.bg_history_chip);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cp.setMarginEnd(dp(6));
            chip.setLayoutParams(cp);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    searchTabInput.setText(tag);
                    searchTabInput.setSelection(tag.length());
                }
            });
            searchHistoryRow.addView(chip);
        }
    }

    private void buildStatusRow() {
        statusRow.removeAllViews();
        String[] statuses = {"All", "Completed", "In process"};
        for (final String status : statuses) {
            boolean selected = status.equals(searchTabSelectedStatus);
            TextView chip = new TextView(this);
            chip.setText(status);
            chip.setTextSize(10f);
            chip.setPadding(dp(10), dp(4), dp(10), dp(4));
            chip.setTextColor(getResources().getColor(selected ? R.color.bubble_blue : R.color.bubble_text_secondary));
            chip.setBackgroundResource(selected ? R.drawable.bg_status_chip_selected : 0);
            if (!selected) chip.setBackgroundColor(0x00000000);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(8));
            chip.setLayoutParams(lp);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    searchTabSelectedStatus = status;
                    refreshSearchUi();
                }
            });
            statusRow.addView(chip);
        }
    }

    // ================================================================
    //  Sync Hub tab (activeBottomTab == 1)
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
        syncCleansValue.setTextColor(getResources().getColor(
                prefs.isClearOldDataBeforeUpload() ? R.color.bubble_green : R.color.bubble_blue));
    }

    // ================================================================
    //  Config tab (activeBottomTab == 3)
    // ================================================================

    private void setupConfigTab() {
        tabConfig = LayoutInflater.from(this).inflate(R.layout.view_tab_config, contentHost, false);

        cfgClientId = tabConfig.findViewById(R.id.config_client_id);
        cfgClientSecret = tabConfig.findViewById(R.id.config_client_secret);
        cfgRefreshToken = tabConfig.findViewById(R.id.config_refresh_token);
        cfgFolderId = tabConfig.findViewById(R.id.config_folder_id);
        cfgFileName = tabConfig.findViewById(R.id.config_file_name);
        cfgExtension = tabConfig.findViewById(R.id.config_extension);
        cfgAccountsServer = tabConfig.findViewById(R.id.config_accounts_server);
        cfgApiServer = tabConfig.findViewById(R.id.config_api_server);
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

    /** LaunchedEffect(activeBottomTab) { if (activeBottomTab == 3) { reload from prefs } } */
    private void loadConfigIntoFields() {
        ZohoPreferences p = new ZohoPreferences(this);
        cfgClientId.setText(p.getClientId());
        cfgClientSecret.setText(p.getClientSecret());
        cfgRefreshToken.setText(p.getRefreshToken());
        cfgFolderId.setText(p.getFolderId());
        cfgFileName.setText(p.getFileName());
        cfgExtension.setText(p.getDefaultExtension());
        cfgAccountsServer.setText(p.getAccountsServer());
        cfgApiServer.setText(p.getApiServer());
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

        p.setClientId(cfgClientId.getText().toString().trim());
        p.setClientSecret(cfgClientSecret.getText().toString().trim());
        p.setRefreshToken(cfgRefreshToken.getText().toString().trim());
        p.setFolderId(cfgFolderId.getText().toString().trim());
        p.setFileName(cfgFileName.getText().toString().trim());
        p.setDefaultExtension(cfgExtension.getText().toString().trim());
        p.setAccountsServer(cfgAccountsServer.getText().toString().trim());
        p.setApiServer(cfgApiServer.getText().toString().trim());
        p.setClearOldDataBeforeUpload(cfgClearOld.isChecked());
        p.setAppLockEnabled(cfgAppLock.isChecked());
        String pin = cfgPin.getText().toString().trim();
        p.setAppLockPasscode(pin.isEmpty() ? "1234" : pin);

        Toast.makeText(this, "Keys and security configurations secured!", Toast.LENGTH_SHORT).show();
        refreshSyncUi();
    }

    // ================================================================
    //  LiveData observers (replacing collectAsStateWithLifecycle)
    // ================================================================

    private void observeViewModel() {
        viewModel.getFilteredMovies().observe(this, new Observer<List<MovieRecord>>() {
            @Override
            public void onChanged(List<MovieRecord> movies) {
                refreshDashboardUi();
            }
        });

        viewModel.getAllMovies().observe(this, new Observer<List<MovieRecord>>() {
            @Override
            public void onChanged(List<MovieRecord> movies) {
                lastAllMovies = movies == null ? new ArrayList<MovieRecord>() : movies;
                refreshSearchUi();
            }
        });

        viewModel.getCategories().observe(this, new Observer<List<String>>() {
            @Override
            public void onChanged(List<String> categories) {
                lastCategories = categories == null ? new ArrayList<String>() : categories;
                refreshDashboardUi();
                refreshSearchUi();
            }
        });

        viewModel.getSearchQuery().observe(this, new Observer<String>() {
            @Override
            public void onChanged(String query) {
                if (dashSearchInput != null && !dashSearchInput.getText().toString().equals(query)) {
                    dashSearchInput.setText(query);
                    dashSearchInput.setSelection(query.length());
                }
                dashSearchClear.setVisibility(
                        (query == null || query.isEmpty()) ? View.GONE : View.VISIBLE);
            }
        });

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
        terminalStatus.setTextColor(getResources().getColor(colorRes));
        terminalHint.setVisibility(showHint ? View.VISIBLE : View.GONE);
    }

    // ================================================================
    //  shared chip builder
    // ================================================================

    private interface CategoryPicked {
        void onPicked(String category);
    }

    private void rebuildCategoryChips(LinearLayout host, String selected, final CategoryPicked callback) {
        if (host == null) return;
        host.removeAllViews();

        // "All Categories" chip
        boolean allSelected = selected == null;
        TextView all = new TextView(this);
        all.setText("All Categories");
        all.setTextSize(11f);
        all.setTextColor(getResources().getColor(allSelected ? R.color.white : R.color.bubble_text_primary));
        all.setPadding(dp(14), dp(8), dp(14), dp(8));
        all.setBackgroundResource(allSelected ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.setMarginEnd(dp(8));
        all.setLayoutParams(alp);
        all.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                callback.onPicked(null);
            }
        });
        host.addView(all);

        for (final String category : lastCategories) {
            boolean isSelected = category.equals(selected);
            TextView chip = new TextView(this);
            chip.setText(category);
            chip.setTextSize(11f);
            chip.setPadding(dp(14), dp(8), dp(14), dp(8));
            chip.setTextColor(getResources().getColor(
                    isSelected ? R.color.white : R.color.bubble_text_primary));
            chip.setBackgroundResource(
                    isSelected ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(8));
            chip.setLayoutParams(lp);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    callback.onPicked(category);
                }
            });
            host.addView(chip);
        }
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
        intro.setTextColor(getResources().getColor(R.color.bubble_text_secondary));
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
        c.setTextColor(getResources().getColor(isHeader ? R.color.bubble_text_primary : R.color.bubble_blue));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(80), ViewGroup.LayoutParams.WRAP_CONTENT);
        c.setLayoutParams(lp);
        row.addView(c);

        TextView d = new TextView(this);
        d.setText(desc);
        d.setTextSize(11f);
        d.setTextColor(getResources().getColor(
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
                .setPositiveButton("Insert Entry", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
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
        tv.setTextColor(getResources().getColor(R.color.terminal_cyan));
        tv.setPadding(dp(12), dp(12), dp(12), dp(12));
        scroll.addView(tv);
        scroll.setBackgroundResource(R.drawable.bg_terminal_inner);

        new AlertDialog.Builder(this)
                .setTitle("System Sync Detail Log")
                .setView(scroll)
                .setPositiveButton("Copy Details", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        UiUtils.copyToClipboard(MainActivity.this,
                                detail == null ? "" : detail, "System Log Detail");
                        Toast.makeText(MainActivity.this, "Copied to clipboard!",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    /** Options dropdown: import / refresh samples / reset index */
    private void showOptionsMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("Import Spreadsheets (.xlsx/.csv)");
        menu.getMenu().add("Refresh Sample Databases");
        menu.getMenu().add("Reset Entire Index");
        menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(android.view.MenuItem item) {
                String title = String.valueOf(item.getTitle());
                if (title.startsWith("Import")) {
                    openFilePicker();
                } else if (title.startsWith("Refresh")) {
                    viewModel.clearAllData();
                    Toast.makeText(MainActivity.this,
                            "Index refreshed with cinema presets!", Toast.LENGTH_SHORT).show();
                } else {
                    viewModel.clearAllData();
                    selectedMovieIds.clear();
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
                    keyView.setTextColor(getResources().getColor(R.color.slate_400));
                    keyView.setTypeface(
                            androidx.core.content.res.ResourcesCompat.getFont(this, R.font.material_icons));
                } else {
                    keyView.setText(key);
                    keyView.setTextSize(22f);
                    keyView.setTextColor(getResources().getColor(
                            functional ? R.color.slate_400 : R.color.white));
                }

                keyView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        message.setText(R.string.lock_hint);
                        message.setTextColor(getResources().getColor(R.color.slate_500));
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
