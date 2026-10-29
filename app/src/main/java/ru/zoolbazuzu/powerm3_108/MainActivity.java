package ru.zoolbazuzu.powerm3_108;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spanned;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.FileProvider;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.io.File;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    // --- UI ---
    private TextView resultTextView;
    private TextView resultPulseTextView;
    private TextView text_na_tablo;
    private TextView tableTitle;
    private EditText duty_cycle;
    private LinearLayout tableContainer;

    private Button btn_reading_down_coarse, btn_reading_down_fine, btn_reading_up_fine, btn_reading_up_coarse;
    private TextView btn_q_preset;

    // --- Режимы ---
    private static final String MODE_KIU = "kiu";
    private static final String MODE_UV  = "uv";
    private static final String[] ALL_MODES = {MODE_KIU, MODE_UV};

    private static final double Q_KIU = 16.0;
    private static final double Q_UV  = 10.0;
    private static final double DEFAULT_COEFF_KIU = 2.5;
    private static final double DEFAULT_COEFF_UV  = 0.121;

    private static final int ROW_COUNT = 6;
    private static final double DEFAULT_X = 2.5;
    private static final double STEP_COARSE = 0.1;
    private static final double STEP_FINE = 0.01;

    // --- Storage ---
    private static final String PREFS_NAME = "power_table_prefs";
    private static final String KEY_TABLE_LIST = "table_list";
    private static final String KEY_CURRENT_TABLE_NUMBER = "current_table_number";
    private static final String KEY_ACTIVE_ROW = "active_row";
    private static final String KEY_MODE = "mode_table_";
    private static final String KEY_THEME = "theme";
    private static final String KEY_PROTECT_COEFF = "protect_coeff";
    private static final String KEY_DEFAULT_Q_KIU = "default_q_kiu";
    private static final String KEY_DEFAULT_Q_UV = "default_q_uv";
    private static final String KEY_DEFAULT_COEFF_KIU = "default_coeff_kiu";
    private static final String KEY_DEFAULT_COEFF_UV = "default_coeff_uv";

    // --- Данные ---
    private final RowData[] rowsData = new RowData[ROW_COUNT];
    private int activeRowIndex = 0;
    private int numberOffset = 0;
    private String currentTableNumber = "1";
    private String currentMode = MODE_KIU;

    private final EditText[] coeffEdits = new EditText[ROW_COUNT];
    private final EditText[] xEdits = new EditText[ROW_COUNT];
    private final TextView[] resultXViews = new TextView[ROW_COUNT];
    private final TextView[] resultYViews = new TextView[ROW_COUNT];
    private final TextView[] numberViews = new TextView[ROW_COUNT];

    private final DecimalFormat df = new DecimalFormat("#.##", new DecimalFormatSymbols(Locale.US));
    private final DecimalFormat displayDf = new DecimalFormat("0.00", new DecimalFormatSymbols(Locale.US));
    private boolean isInternalUpdate = false;

    private static class RowData {
        double coeff = DEFAULT_COEFF_KIU;
        double xValue = DEFAULT_X;
        double resultX = 0.0;
        double resultY = 0.0;
    }

    // ============ Режимы (helpers) ============

    private String prefixFor(String mode) { return MODE_UV.equals(mode) ? "УВ" : "КИУ"; }

    private String getStoredMode(String tableNumber) {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_MODE + tableNumber, MODE_KIU);
    }

    private String buildTableTitle(String tableNumber) {
        return prefixFor(getStoredMode(tableNumber)) + " № " + tableNumber;
    }

    private double defaultCoeffFor(String mode) {
        String key = MODE_UV.equals(mode) ? KEY_DEFAULT_COEFF_UV : KEY_DEFAULT_COEFF_KIU;
        double fallback = MODE_UV.equals(mode) ? DEFAULT_COEFF_UV : DEFAULT_COEFF_KIU;
        return Double.longBitsToDouble(getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getLong(key, Double.doubleToLongBits(fallback)));
    }

    // ============ Тема и настройки ============

    private boolean isDarkTheme() {
        return !"light".equals(getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_THEME, "dark"));
    }

    private void applyTheme() {
        AppCompatDelegate.setDefaultNightMode(
                isDarkTheme() ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
    }

    private boolean isCoeffProtectionOn() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(KEY_PROTECT_COEFF, true);
    }

    private double getDefaultQFor(String mode) {
        String key = MODE_UV.equals(mode) ? KEY_DEFAULT_Q_UV : KEY_DEFAULT_Q_KIU;
        double fallback = MODE_UV.equals(mode) ? Q_UV : Q_KIU;
        return Double.longBitsToDouble(getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getLong(key, Double.doubleToLongBits(fallback)));
    }

    private double qFor(String mode) { return getDefaultQFor(mode); }

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyTheme();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        resultTextView = findViewById(R.id.resultTextView);
        resultPulseTextView = findViewById(R.id.resultPulseTextView);
        text_na_tablo = findViewById(R.id.text_na_tablo);
        tableTitle = findViewById(R.id.tableTitle);
        duty_cycle = findViewById(R.id.duty_cycle);
        tableContainer = findViewById(R.id.table_container);

        btn_reading_down_coarse = findViewById(R.id.btn_reading_down_coarse);
        btn_reading_down_fine = findViewById(R.id.btn_reading_down_fine);
        btn_reading_up_fine = findViewById(R.id.btn_reading_up_fine);
        btn_reading_up_coarse = findViewById(R.id.btn_reading_up_coarse);
        btn_q_preset = findViewById(R.id.btn_q_preset);

        Button btnSaveSend = findViewById(R.id.btnSaveSend);
        Button btnClearTable = findViewById(R.id.btnClearTable);
        LinearLayout tableTitleBar = findViewById(R.id.tableTitleBar);

        initRowsData();
        buildTable();
        restoreState();

        if (tableTitle != null) tableTitle.setText(buildTableTitle(currentTableNumber));

        fillViewsFromData();
        updateNumberViews();
        updateAllRowsCalculation();
        updateMainCalculation();
        selectRow(activeRowIndex);

        duty_cycle.setFilters(new InputFilter[]{new QInputFilter()});
        duty_cycle.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                if (isInternalUpdate) return;
                sanitizeQIfNeeded();
                updateAllRowsCalculation();
                updateMainCalculation();
                saveState();
            }
        });

        btn_reading_down_coarse.setOnClickListener(v -> { haptic(); stepReading(-STEP_COARSE, 1); });
        btn_reading_down_fine.setOnClickListener(v -> { haptic(); stepReading(-STEP_FINE, 2); });
        btn_reading_up_fine.setOnClickListener(v -> { haptic(); stepReading(STEP_FINE, 2); });
        btn_reading_up_coarse.setOnClickListener(v -> { haptic(); stepReading(STEP_COARSE, 1); });
        btn_q_preset.setOnClickListener(v -> { haptic(); toggleMode(); });

        findViewById(R.id.btn_settings).setOnClickListener(v -> showSettingsDialog());

        if (tableTitleBar != null) tableTitleBar.setOnClickListener(v -> showTableManagerDialog());
        if (tableTitle != null) tableTitle.setOnClickListener(v -> showTableManagerDialog());

        btnSaveSend.setOnClickListener(v -> confirmSaveAndShare());
        btnClearTable.setOnClickListener(v -> confirmClearTable());

        // Отступы под статус-бар и навигационную панель (клавиатуру не трогаем)
        View mainView = findViewById(R.id.main);
        if (mainView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(mainView, (v, insets) -> {
                Insets sb = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                int base = dpToPx(16);
                v.setPadding(base + sb.left, base + sb.top, base + sb.right, base + sb.bottom);
                return insets;
            });
        }
    }

    // ============ Данные ============

    private void initRowsData() {
        for (int i = 0; i < ROW_COUNT; i++) rowsData[i] = new RowData();
    }

    private void restoreState() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        currentTableNumber = prefs.getString(KEY_CURRENT_TABLE_NUMBER, "1");
        currentMode = getStoredMode(currentTableNumber);
        activeRowIndex = prefs.getInt(KEY_ACTIVE_ROW, 0);
        loadTableDataForCurrentTable();
        if (activeRowIndex < 0 || activeRowIndex >= ROW_COUNT) activeRowIndex = 0;
    }

    private void loadTableDataForCurrentTable() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        for (int i = 0; i < ROW_COUNT; i++) {
            rowsData[i].coeff = Double.longBitsToDouble(
                    prefs.getLong(getCoeffKey(currentTableNumber, currentMode, i),
                            Double.doubleToLongBits(defaultCoeffFor(currentMode))));
            rowsData[i].xValue = Double.longBitsToDouble(
                    prefs.getLong(getXKey(currentTableNumber, currentMode, i),
                            Double.doubleToLongBits(DEFAULT_X)));
        }
        numberOffset = prefs.getInt(getNumOffsetKey(currentTableNumber), 0);
        double q = Double.longBitsToDouble(
                prefs.getLong(getDutyKey(currentTableNumber, currentMode),
                        Double.doubleToLongBits(qFor(currentMode))));
        if (duty_cycle != null) {
            isInternalUpdate = true;
            duty_cycle.setText(df.format(q));
            isInternalUpdate = false;
        }
    }

    private void saveState() {
        SharedPreferences.Editor ed = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        ed.putString(KEY_CURRENT_TABLE_NUMBER, currentTableNumber);
        ed.putInt(KEY_ACTIVE_ROW, activeRowIndex);
        ed.putString(KEY_MODE + currentTableNumber, currentMode);

        for (int i = 0; i < ROW_COUNT; i++) {
            ed.putLong(getCoeffKey(currentTableNumber, currentMode, i), Double.doubleToLongBits(rowsData[i].coeff));
            ed.putLong(getXKey(currentTableNumber, currentMode, i), Double.doubleToLongBits(rowsData[i].xValue));
        }
        ed.putLong(getDutyKey(currentTableNumber, currentMode), Double.doubleToLongBits(getQ()));
        ed.putInt(getNumOffsetKey(currentTableNumber), numberOffset);
        ed.apply();
    }

    private String getCoeffKey(String t, String mode, int i) { return "coeff_table_" + t + "_" + mode + "_" + i; }
    private String getXKey(String t, String mode, int i) { return "x_table_" + t + "_" + mode + "_" + i; }
    private String getDutyKey(String t, String mode) { return "duty_table_" + t + "_" + mode; }
    private String getNumOffsetKey(String t) { return "num_offset_table_" + t; }

    // ============ Таблица ============

    private void buildTable() {
        if (tableContainer == null) return;
        tableContainer.removeAllViews();

        int headerBg = getColor(R.color.table_header_bg);
        int headerText = getColor(R.color.table_header_text);
        int cellBg = getColor(R.color.table_row_bg);
        int numberText = getColor(R.color.text_secondary);
        int meterText = getColor(R.color.table_value_meter);
        int avgText = getColor(R.color.accent_cyan);
        int pulseText = getColor(R.color.accent_amber);
        int editBg = getColor(R.color.surface_panel_light);
        int editText = getColor(R.color.text_primary);

        LinearLayout header = createRow(false);
        TextView numH = createCellTextView(getString(R.string.header_num), headerText, headerBg);
        numH.setLayoutParams(numberLp());
        numH.setContentDescription(getString(R.string.num_toggle_hint));
        numH.setOnClickListener(v -> toggleNumbering());
        header.addView(numH);
        header.addView(headerCell(getString(R.string.header_coeff), headerBg, headerText));
        header.addView(headerCell(getString(R.string.header_meter), headerBg, headerText));
        header.addView(headerCell(getString(R.string.header_avg), headerBg, headerText));
        header.addView(headerCell(getString(R.string.header_pulse), headerBg, headerText));
        tableContainer.addView(header);

        for (int i = 0; i < ROW_COUNT; i++) {
            final int index = i;
            LinearLayout row = createRow(true);

            numberViews[i] = createCellTextView("", numberText, cellBg);
            numberViews[i].setLayoutParams(numberLp());
            numberViews[i].setOnClickListener(v -> selectRow(index));
            row.addView(numberViews[i]);

            // Коэффициент — инлайн-редактирование
            coeffEdits[i] = createEditCell(editBg, editText);
            coeffEdits[i].setLayoutParams(cellLp());
            coeffEdits[i].setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
            coeffEdits[i].setOnClickListener(v -> selectRow(index));
            coeffEdits[i].setOnFocusChangeListener((v, has) -> {
                if (has) { selectRow(index); }
                else { normalizeCoeffField(index); }
            });
            coeffEdits[i].addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                @Override public void afterTextChanged(Editable s) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (isInternalUpdate) return;

                    if (isCoeffProtectionOn() && count > before) {
                        String formatted = formatCoeff(s.toString());
                        if (!formatted.equals(s.toString())) {
                            isInternalUpdate = true;
                            coeffEdits[index].setText(formatted);
                            coeffEdits[index].setSelection(formatted.length());
                            isInternalUpdate = false;
                        }
                    }

                    rowsData[index].coeff = getDoubleFromEditText(coeffEdits[index], defaultCoeffFor(currentMode));
                    updateRowCalculation(index);
                    if (index == activeRowIndex) updateMainCalculation();
                    saveState();
                }
            });
            row.addView(coeffEdits[i]);

            // Показание (М3-108) — инлайн-редактирование
            xEdits[i] = createEditCell(editBg, meterText);
            xEdits[i].setLayoutParams(cellLp());
            xEdits[i].setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            xEdits[i].setOnClickListener(v -> selectRow(index));
            xEdits[i].setOnFocusChangeListener((v, has) -> {
                if (has) { selectRow(index); }
                else { normalizeReadingField(index); }
            });
            xEdits[i].addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                @Override public void afterTextChanged(Editable s) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (isInternalUpdate) return;

                    if (isCoeffProtectionOn() && count > before) {
                        String formatted = formatReading(s.toString());
                        if (!formatted.equals(s.toString())) {
                            isInternalUpdate = true;
                            xEdits[index].setText(formatted);
                            xEdits[index].setSelection(formatted.length());
                            isInternalUpdate = false;
                        }
                    }

                    rowsData[index].xValue = getDoubleFromEditText(xEdits[index], DEFAULT_X);
                    updateRowCalculation(index);
                    if (index == activeRowIndex) updateMainCalculation();
                    saveState();
                }
            });
            row.addView(xEdits[i]);

            resultXViews[i] = createCellTextView("0", avgText, cellBg);
            resultXViews[i].setLayoutParams(cellLp());
            resultXViews[i].setOnClickListener(v -> selectRow(index));
            row.addView(resultXViews[i]);

            resultYViews[i] = createCellTextView("0", pulseText, cellBg);
            resultYViews[i].setLayoutParams(cellLp());
            resultYViews[i].setOnClickListener(v -> selectRow(index));
            row.addView(resultYViews[i]);

            tableContainer.addView(row);
        }
    }

    private LinearLayout createRow(boolean dataRow) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (dataRow) lp.topMargin = dpToPx(2);
        row.setLayoutParams(lp);
        return row;
    }

    private TextView headerCell(String text, int bg, int fg) {
        TextView tv = createCellTextView(text, fg, bg);
        tv.setLayoutParams(cellLp());
        return tv;
    }

    private TextView createCellTextView(String text, int textColor, int bgColor) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dpToPx(6), dpToPx(10), dpToPx(6), dpToPx(10));
        tv.setTextSize(14f);
        tv.setTextColor(textColor);
        tv.setBackgroundColor(bgColor);
        return tv;
    }

    private EditText createEditCell(int bg, int textColor) {
        EditText e = new EditText(this);
        e.setGravity(Gravity.CENTER);
        e.setPadding(dpToPx(4), dpToPx(8), dpToPx(4), dpToPx(8));
        e.setTextSize(14f);
        e.setTextColor(textColor);
        e.setBackgroundColor(bg);
        e.setSingleLine(true);
        return e;
    }

    private LinearLayout.LayoutParams numberLp() {
        return new LinearLayout.LayoutParams(dpToPx(40), ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams cellLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dpToPx(1), 0, dpToPx(1), 0);
        return lp;
    }

    private void fillViewsFromData() {
        isInternalUpdate = true;
        for (int i = 0; i < ROW_COUNT; i++) {
            if (coeffEdits[i] != null) coeffEdits[i].setText(formatCoeff3(rowsData[i].coeff));
            if (xEdits[i] != null) xEdits[i].setText(displayDf.format(rowsData[i].xValue));
        }
        isInternalUpdate = false;
    }

    private void updateNumberViews() {
        for (int i = 0; i < ROW_COUNT; i++) {
            if (numberViews[i] != null) numberViews[i].setText(String.valueOf(i + 1 + numberOffset));
        }
    }

    private void toggleNumbering() {
        haptic();
        numberOffset = (numberOffset == 0) ? ROW_COUNT : 0;
        updateNumberViews();
        saveState();
    }

    // ============ Выбор строки и расчёт ============

    private void selectRow(int index) {
        if (index < 0 || index >= ROW_COUNT) return;
        activeRowIndex = index;

        int cellBg = getColor(R.color.table_row_bg);
        int activeBg = getColor(R.color.table_row_active_bg);
        int editBg = getColor(R.color.surface_panel_light);

        for (int i = 0; i < ROW_COUNT; i++) {
            boolean active = (i == activeRowIndex);
            int cb = active ? activeBg : cellBg;
            int eb = active ? activeBg : editBg;
            if (numberViews[i] != null) numberViews[i].setBackgroundColor(cb);
            if (coeffEdits[i] != null) coeffEdits[i].setBackgroundColor(eb);
            if (xEdits[i] != null) xEdits[i].setBackgroundColor(eb);
            if (resultXViews[i] != null) resultXViews[i].setBackgroundColor(cb);
            if (resultYViews[i] != null) resultYViews[i].setBackgroundColor(cb);
        }

        updateMainCalculation();
        saveState();
    }

    private void updateMainCalculation() {
        if (resultTextView == null || resultPulseTextView == null || text_na_tablo == null) return;
        RowData rd = rowsData[activeRowIndex];

        String newTablo = displayDf.format(rd.xValue);
        boolean tabloChanged = !newTablo.equals(text_na_tablo.getText().toString());
        text_na_tablo.setText(newTablo);

        resultTextView.setText(df.format(rd.resultX));
        resultPulseTextView.setText(df.format(rd.resultY));

        if (tabloChanged) animateDisplay();
    }

    private void updateAllRowsCalculation() {
        for (int i = 0; i < ROW_COUNT; i++) {
            if (coeffEdits[i] != null) rowsData[i].coeff = getDoubleFromEditText(coeffEdits[i], defaultCoeffFor(currentMode));
            if (xEdits[i] != null) rowsData[i].xValue = getDoubleFromEditText(xEdits[i], DEFAULT_X);
            updateRowCalculation(i);
        }
    }

    private void updateRowCalculation(int index) {
        double q = PowerCalculator.clampDutyCycle(getQ());
        rowsData[index].resultX = PowerCalculator.averagePower(rowsData[index].coeff, rowsData[index].xValue);
        rowsData[index].resultY = PowerCalculator.pulsePower(rowsData[index].coeff, rowsData[index].xValue, q);
        if (resultXViews[index] != null) resultXViews[index].setText(df.format(rowsData[index].resultX));
        if (resultYViews[index] != null) resultYViews[index].setText(df.format(rowsData[index].resultY));
    }

    // ============ Скважность Q и режимы ============

    private double getQ() {
        return getDoubleFromEditText(duty_cycle, qFor(currentMode));
    }

    private void setQ(double value) {
        value = PowerCalculator.clampDutyCycle(value);
        isInternalUpdate = true;
        duty_cycle.setText(df.format(value));
        duty_cycle.setSelection(duty_cycle.getText().length());
        isInternalUpdate = false;
        updateAllRowsCalculation();
        updateMainCalculation();
        saveState();
    }

    private void stepReading(double delta, int decimals) {
        double next = PowerCalculator.roundTo(rowsData[activeRowIndex].xValue + delta, decimals);
        if (next < 0) next = 0;
        rowsData[activeRowIndex].xValue = next;

        if (xEdits[activeRowIndex] != null) {
            isInternalUpdate = true;
            xEdits[activeRowIndex].setText(displayDf.format(next));
            isInternalUpdate = false;
        }

        updateRowCalculation(activeRowIndex);
        updateMainCalculation();
        saveState();
    }

    private void toggleMode() {
        saveState();
        currentMode = MODE_UV.equals(currentMode) ? MODE_KIU : MODE_UV;
        loadTableDataForCurrentTable();
        if (tableTitle != null) {
            tableTitle.setText(prefixFor(currentMode) + " № " + currentTableNumber);
        }
        fillViewsFromData();
        updateNumberViews();
        updateAllRowsCalculation();
        updateMainCalculation();
        saveState();
    }

    private void sanitizeQIfNeeded() {
        if (duty_cycle == null) return;
        String text = duty_cycle.getText().toString().trim().replace(",", ".");
        if (text.isEmpty()) return;
        try {
            double v = Double.parseDouble(text);
            if (v < 0) {
                isInternalUpdate = true;
                duty_cycle.setText("0");
                duty_cycle.setSelection(1);
                isInternalUpdate = false;
                Toast.makeText(this, R.string.toast_q_negative, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception ignored) {}
    }

    private static class QInputFilter implements InputFilter {
        @Override
        public CharSequence filter(CharSequence source, int start, int end,
                                   Spanned dest, int dstart, int dend) {
            String result = dest.subSequence(0, dstart)
                    + source.subSequence(start, end).toString()
                    + dest.subSequence(dend, dest.length()).toString();
            if (result.isEmpty()) return null;
            if (!result.matches("\\d{0,6}(\\.\\d{0,1})?")) return "";
            return null;
        }
    }

    /** Формат "D.dd(d)": первая цифра + точка + остальные (макс maxUserDigits). */
    private String formatDotted(String raw, int maxUserDigits) {
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.length() > maxUserDigits) {
            digits = digits.substring(0, maxUserDigits);
            toast(R.string.toast_coeff_protected);
        }
        if (digits.isEmpty()) return "";
        if (digits.length() == 1) return digits + ".";
        return digits.charAt(0) + "." + digits.substring(1);
    }

    /** Коэффициент в режиме УВ: фиксированный "0." + до 3 цифр. */
    private String formatCoeffUv(String raw) {
        String digits = raw.replaceAll("[^0-9]", "");
        String userDigits = digits.startsWith("0") ? digits.substring(1) : digits;
        if (userDigits.length() > 3) {
            userDigits = userDigits.substring(0, 3);
            toast(R.string.toast_coeff_protected);
        }
        if (userDigits.isEmpty()) return "";
        return "0." + userDigits;
    }

    /** Коэффициент: КИУ -> D.ddd, УВ -> 0.ddd */
    private String formatCoeff(String raw) {
        if (MODE_UV.equals(currentMode)) return formatCoeffUv(raw);
        return formatDotted(raw, 4);
    }

    /** Показание прибора: D.dd */
    private String formatReading(String raw) {
        return formatDotted(raw, 3);
    }

    /** При потере фокуса: пусто/0 -> значение по умолчанию, иначе дополняем до формата. */
    private void normalizeCoeffField(int index) {
        if (index < 0 || index >= ROW_COUNT || coeffEdits[index] == null) return;
        EditText e = coeffEdits[index];
        String raw = e.getText().toString().trim().replace(",", ".");
        double val = 0;
        boolean valid = false;
        if (!raw.isEmpty()) {
            try { val = Double.parseDouble(raw); valid = true; } catch (Exception ignored) {}
        }
        if (!valid || val == 0) val = defaultCoeffFor(currentMode);

        isInternalUpdate = true;
        e.setText(formatCoeff3(val));
        isInternalUpdate = false;

        rowsData[index].coeff = val;
        updateRowCalculation(index);
        if (index == activeRowIndex) updateMainCalculation();
        saveState();
    }

    /** При потере фокуса: пусто/0 -> DEFAULT_X, иначе дополняем до D.dd. */
    private void normalizeReadingField(int index) {
        if (index < 0 || index >= ROW_COUNT || xEdits[index] == null) return;
        EditText e = xEdits[index];
        String raw = e.getText().toString().trim().replace(",", ".");
        double val = 0;
        boolean valid = false;
        if (!raw.isEmpty()) {
            try { val = Double.parseDouble(raw); valid = true; } catch (Exception ignored) {}
        }
        if (!valid || val == 0) val = DEFAULT_X;

        isInternalUpdate = true;
        e.setText(displayDf.format(val));
        isInternalUpdate = false;

        rowsData[index].xValue = val;
        updateRowCalculation(index);
        if (index == activeRowIndex) updateMainCalculation();
        saveState();
    }

    // ============ Настройки ============

    private void showSettingsDialog() {
        View v = getLayoutInflater().inflate(R.layout.dialog_settings, null);
        final SwitchMaterial switchTheme = v.findViewById(R.id.switch_theme);
        final MaterialCheckBox checkProtect = v.findViewById(R.id.check_protect);
        final EditText etQKiu = v.findViewById(R.id.et_q_kiu);
        final EditText etCoeffKiu = v.findViewById(R.id.et_coeff_kiu);
        final EditText etQUv = v.findViewById(R.id.et_q_uv);
        final EditText etCoeffUv = v.findViewById(R.id.et_coeff_uv);

        final SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        final boolean wasDark = isDarkTheme();

        switchTheme.setChecked(wasDark);
        checkProtect.setChecked(isCoeffProtectionOn());
        etQKiu.setText(df.format(getDefaultQFor(MODE_KIU)));
        etCoeffKiu.setText(formatCoeff3(defaultCoeffFor(MODE_KIU)));
        etQUv.setText(df.format(getDefaultQFor(MODE_UV)));
        etCoeffUv.setText(formatCoeff3(defaultCoeffFor(MODE_UV)));

        new AlertDialog.Builder(this, R.style.Theme_PowerM3108_Dialog)
                .setTitle(R.string.settings_title)
                .setView(v)
                .setPositiveButton(R.string.dlg_ok, (d, w) -> {
                    boolean newDark = switchTheme.isChecked();
                    boolean protect = checkProtect.isChecked();

                    double qKiu = getDoubleFromEditText(etQKiu, -1);
                    double qUv = getDoubleFromEditText(etQUv, -1);
                    double coeffKiu = getDoubleFromEditText(etCoeffKiu, -1);
                    double coeffUv = getDoubleFromEditText(etCoeffUv, -1);

                    boolean invalid = false;
                    if (qKiu <= 0) { qKiu = Q_KIU; invalid = true; }
                    if (qUv <= 0) { qUv = Q_UV; invalid = true; }
                    if (coeffKiu <= 0) { coeffKiu = DEFAULT_COEFF_KIU; invalid = true; }
                    if (coeffUv <= 0) { coeffUv = DEFAULT_COEFF_UV; invalid = true; }

                    SharedPreferences.Editor ed = prefs.edit();
                    ed.putString(KEY_THEME, newDark ? "dark" : "light");
                    ed.putBoolean(KEY_PROTECT_COEFF, protect);
                    ed.putLong(KEY_DEFAULT_Q_KIU, Double.doubleToLongBits(qKiu));
                    ed.putLong(KEY_DEFAULT_Q_UV, Double.doubleToLongBits(qUv));
                    ed.putLong(KEY_DEFAULT_COEFF_KIU, Double.doubleToLongBits(coeffKiu));
                    ed.putLong(KEY_DEFAULT_COEFF_UV, Double.doubleToLongBits(coeffUv));
                    ed.apply();

                    if (invalid) {
                        toast(R.string.toast_settings_invalid);
                    }

                    if (newDark != wasDark) {
                        recreate();
                    }
                })
                .setNegativeButton(R.string.dlg_cancel, null)
                .show();
    }

    // ============ Управление таблицами ============

    private ArrayList<String> getTableNumberList() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String raw = prefs.getString(KEY_TABLE_LIST, "");
        ArrayList<String> list = new ArrayList<>();
        if (!raw.isEmpty()) {
            for (String item : raw.split(";")) {
                String t = item.trim();
                if (!t.isEmpty() && !list.contains(t)) list.add(t);
            }
        }
        if (list.isEmpty()) list.add("1");
        return list;
    }

    private void saveTableNumberList(ArrayList<String> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(";");
            sb.append(list.get(i));
        }
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putString(KEY_TABLE_LIST, sb.toString()).apply();
    }

    private void selectTableByNumber(String tableNumber) {
        saveState();
        currentTableNumber = tableNumber;
        currentMode = getStoredMode(tableNumber);
        loadTableDataForCurrentTable();
        if (tableTitle != null) tableTitle.setText(buildTableTitle(tableNumber));
        fillViewsFromData();
        updateNumberViews();
        updateAllRowsCalculation();
        updateMainCalculation();
        saveState();
    }

    private void showAddNewTableDialog() {
        final EditText input = new EditText(this);
        input.setHint(R.string.dlg_add_hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8));
        input.setTextColor(getColor(R.color.text_primary));
        input.setHintTextColor(getColor(R.color.text_secondary));
        input.setBackgroundColor(getColor(R.color.surface_panel_light));

        new AlertDialog.Builder(this, R.style.Theme_PowerM3108_Dialog)
                .setTitle(R.string.dlg_add_title)
                .setView(input)
                .setPositiveButton(R.string.dlg_ok, (d, w) -> {
                    String newNumber = input.getText().toString().trim();
                    if (newNumber.isEmpty()) { toast(R.string.dlg_empty); return; }
                    ArrayList<String> list = getTableNumberList();
                    if (list.contains(newNumber)) { toast(R.string.dlg_dup); return; }
                    list.add(newNumber);
                    saveTableNumberList(list);

                    currentTableNumber = newNumber;
                    currentMode = MODE_KIU;
                    numberOffset = 0;
                    for (int i = 0; i < ROW_COUNT; i++) {
                        rowsData[i].coeff = defaultCoeffFor(MODE_KIU);
                        rowsData[i].xValue = DEFAULT_X;
                    }
                    if (tableTitle != null) tableTitle.setText(buildTableTitle(currentTableNumber));

                    fillViewsFromData();
                    setQ(getDefaultQFor(MODE_KIU));
                    updateNumberViews();
                    saveState();
                })
                .setNegativeButton(R.string.dlg_cancel, null)
                .show();
    }

    private void renameTable(String oldNumber) {
        final EditText input = new EditText(this);
        input.setHint(R.string.dlg_add_hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(oldNumber);
        input.setSelection(input.getText().length());
        input.setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8));
        input.setTextColor(getColor(R.color.text_primary));
        input.setHintTextColor(getColor(R.color.text_secondary));
        input.setBackgroundColor(getColor(R.color.surface_panel_light));

        new AlertDialog.Builder(this, R.style.Theme_PowerM3108_Dialog)
                .setTitle(R.string.dlg_rename_title)
                .setView(input)
                .setPositiveButton(R.string.dlg_ok, (d, w) -> {
                    String newNumber = input.getText().toString().trim();
                    if (newNumber.isEmpty()) { toast(R.string.dlg_empty); return; }
                    if (newNumber.equals(oldNumber)) return;
                    ArrayList<String> list = getTableNumberList();
                    if (list.contains(newNumber)) { toast(R.string.dlg_dup); return; }

                    SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                    SharedPreferences.Editor ed = prefs.edit();

                    for (String mode : ALL_MODES) {
                        for (int i = 0; i < ROW_COUNT; i++) {
                            ed.putLong(getCoeffKey(newNumber, mode, i), prefs.getLong(getCoeffKey(oldNumber, mode, i), Double.doubleToLongBits(defaultCoeffFor(mode))));
                            ed.remove(getCoeffKey(oldNumber, mode, i));
                            ed.putLong(getXKey(newNumber, mode, i), prefs.getLong(getXKey(oldNumber, mode, i), Double.doubleToLongBits(DEFAULT_X)));
                            ed.remove(getXKey(oldNumber, mode, i));
                        }
                        ed.putLong(getDutyKey(newNumber, mode), prefs.getLong(getDutyKey(oldNumber, mode), Double.doubleToLongBits(qFor(mode))));
                        ed.remove(getDutyKey(oldNumber, mode));
                    }

                    ed.putInt(getNumOffsetKey(newNumber), prefs.getInt(getNumOffsetKey(oldNumber), 0));
                    ed.remove(getNumOffsetKey(oldNumber));
                    ed.putString(KEY_MODE + newNumber, prefs.getString(KEY_MODE + oldNumber, MODE_KIU));
                    ed.remove(KEY_MODE + oldNumber);

                    int idx = list.indexOf(oldNumber);
                    if (idx >= 0) list.set(idx, newNumber);
                    saveTableNumberList(list);
                    ed.apply();

                    if (oldNumber.equals(currentTableNumber)) {
                        currentTableNumber = newNumber;
                        if (tableTitle != null) tableTitle.setText(buildTableTitle(currentTableNumber));
                        loadTableDataForCurrentTable();
                        fillViewsFromData();
                        updateNumberViews();
                        updateAllRowsCalculation();
                        updateMainCalculation();
                    }
                    saveState();
                    toast(R.string.dlg_renamed);
                })
                .setNegativeButton(R.string.dlg_cancel, null)
                .show();
    }

    private void deleteTable(String tableNumber) {
        ArrayList<String> list = getTableNumberList();
        if (list.size() <= 1) { toast(R.string.dlg_delete_last); return; }

        new AlertDialog.Builder(this, R.style.Theme_PowerM3108_Dialog)
                .setTitle(R.string.dlg_delete_title)
                .setMessage("Удалить " + buildTableTitle(tableNumber) + "?")
                .setPositiveButton(R.string.dlg_yes, (d, w) -> {
                    SharedPreferences.Editor ed = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
                    for (String mode : ALL_MODES) {
                        for (int i = 0; i < ROW_COUNT; i++) {
                            ed.remove(getCoeffKey(tableNumber, mode, i));
                            ed.remove(getXKey(tableNumber, mode, i));
                        }
                        ed.remove(getDutyKey(tableNumber, mode));
                    }
                    ed.remove(getNumOffsetKey(tableNumber));
                    ed.remove(KEY_MODE + tableNumber);
                    list.remove(tableNumber);
                    saveTableNumberList(list);

                    if (tableNumber.equals(currentTableNumber)) {
                        currentTableNumber = list.get(0);
                        currentMode = getStoredMode(currentTableNumber);
                        loadTableDataForCurrentTable();
                        if (tableTitle != null) tableTitle.setText(buildTableTitle(currentTableNumber));
                        fillViewsFromData();
                        updateNumberViews();
                        updateAllRowsCalculation();
                        updateMainCalculation();
                    }
                    ed.apply();
                    saveState();
                    toast(R.string.dlg_deleted);
                })
                .setNegativeButton(R.string.dlg_no, null)
                .show();
    }

    private void showTableManagerDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_table_selector, null);
        Button btnAddNew = dialogView.findViewById(R.id.btnAddNewTable);
        ListView listView = dialogView.findViewById(R.id.listViewTables);

        ArrayList<String> tableNumbers = getTableNumberList();

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.Theme_PowerM3108_Dialog)
                .setTitle(R.string.dlg_tables)
                .setView(dialogView)
                .setNegativeButton(R.string.dlg_close, null)
                .create();

        listView.setAdapter(new TableListAdapter(tableNumbers, dialog));
        btnAddNew.setOnClickListener(v -> { dialog.dismiss(); showAddNewTableDialog(); });
        dialog.show();
    }

    private class TableListAdapter extends BaseAdapter {
        private final ArrayList<String> tableNumbers;
        private final AlertDialog parentDialog;

        TableListAdapter(ArrayList<String> tableNumbers, AlertDialog parentDialog) {
            this.tableNumbers = tableNumbers;
            this.parentDialog = parentDialog;
        }

        @Override public int getCount() { return tableNumbers.size(); }
        @Override public Object getItem(int p) { return tableNumbers.get(p); }
        @Override public long getItemId(int p) { return p; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) view = getLayoutInflater().inflate(R.layout.item_table_entry, parent, false);

            TextView tvName = view.findViewById(R.id.tvTableName);
            Button btnEdit = view.findViewById(R.id.btnEditTable);
            Button btnDelete = view.findViewById(R.id.btnDeleteTable);

            String num = tableNumbers.get(position);
            tvName.setText(buildTableTitle(num));
            tvName.setOnClickListener(v -> { selectTableByNumber(num); parentDialog.dismiss(); });
            btnEdit.setOnClickListener(v -> { parentDialog.dismiss(); renameTable(num); });
            btnDelete.setOnClickListener(v -> { parentDialog.dismiss(); deleteTable(num); });
            return view;
        }
    }

    // ============ Очистка ============

    private void confirmClearTable() {
        new AlertDialog.Builder(this, R.style.Theme_PowerM3108_Dialog)
                .setTitle(R.string.confirm_clear_title)
                .setMessage(R.string.confirm_clear_msg)
                .setPositiveButton(R.string.dlg_yes, (d, w) -> clearTable())
                .setNegativeButton(R.string.dlg_no, null)
                .show();
    }

    private void clearTable() {
        isInternalUpdate = true;
        duty_cycle.setText(df.format(qFor(currentMode)));
        for (int i = 0; i < ROW_COUNT; i++) {
            rowsData[i].coeff = defaultCoeffFor(currentMode);
            rowsData[i].xValue = DEFAULT_X;
            if (coeffEdits[i] != null) coeffEdits[i].setText(formatCoeff3(defaultCoeffFor(currentMode)));
            if (xEdits[i] != null) xEdits[i].setText(displayDf.format(DEFAULT_X));
        }
        numberOffset = 0;
        activeRowIndex = 0;
        isInternalUpdate = false;

        updateNumberViews();
        updateAllRowsCalculation();
        updateMainCalculation();
        selectRow(0);
        saveState();
        toast(R.string.toast_table_cleared);
    }

    // ============ Экспорт ============

    private void confirmSaveAndShare() {
        new AlertDialog.Builder(this, R.style.Theme_PowerM3108_Dialog)
                .setTitle(R.string.confirm_save_title)
                .setMessage(R.string.confirm_save_msg)
                .setPositiveButton(R.string.dlg_yes, (d, w) -> saveXlsHtmlAndShare())
                .setNegativeButton(R.string.dlg_no, null)
                .show();
    }

    private String extractTableNumber() {
        String n = currentTableNumber == null ? "unknown" : currentTableNumber.trim();
        if (n.isEmpty()) return "unknown";
        return n.replaceAll("[^0-9A-Za-z_-]", "_");
    }

    private void saveXlsHtmlAndShare() {
        try {
            String dateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
            String tableNumber = extractTableNumber();
            String title = buildTableTitle(currentTableNumber);
            String fileName = "Power_" + tableNumber + "_"
                    + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".html";

            File dir = getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS);
            if (dir == null) throw new Exception("Documents dir unavailable");
            if (!dir.exists() && !dir.mkdirs()) throw new Exception("Cannot create Documents dir");

            File file = new File(dir, fileName);
            String dutyText = duty_cycle != null ? duty_cycle.getText().toString().trim() : "";

            StringBuilder html = new StringBuilder();
            html.append("<html><head><meta charset=\"UTF-8\"></head><body>");
            html.append("<table border='1' cellspacing='0' cellpadding='6' style='border-collapse:collapse; text-align:center; font-family:Arial;'>");

            html.append("<tr><td colspan='5' style='font-size:16pt; font-weight:bold; background:#d9ead3;'>")
                    .append(escapeHtml(title)).append("</td></tr>");
            html.append("<tr><td style='font-weight:bold;'>Дата и время</td><td colspan='4'>")
                    .append(escapeHtml(dateTime)).append("</td></tr>");
            html.append("<tr><td style='font-weight:bold;'>Скважность Q</td><td colspan='4'>")
                    .append(escapeHtml(dutyText)).append("</td></tr>");
            html.append("<tr><td colspan='5' style='background:#ffffff; height:12px;'></td></tr>");

            html.append("<tr style='font-weight:bold; background:#b6d7a8;'>")
                    .append("<td>№ Л</td><td>Коэфф.</td><td>М3-108</td><td>Р среднее</td><td>Р импульс</td></tr>");

            for (int i = 0; i < ROW_COUNT; i++) {
                html.append("<tr>")
                        .append("<td>").append(i + 1 + numberOffset).append("</td>")
                        .append("<td>").append(formatCoeff3ForExcel(rowsData[i].coeff)).append("</td>")
                        .append("<td>").append(formatForExcel(rowsData[i].xValue)).append("</td>")
                        .append("<td style='background:#d9ead3;'>").append(formatForExcel(rowsData[i].resultX)).append("</td>")
                        .append("<td>").append(formatForExcel(rowsData[i].resultY)).append("</td>")
                        .append("</tr>");
            }

            html.append("</table></body></html>");

            java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(
                    new java.io.FileOutputStream(file), java.nio.charset.StandardCharsets.UTF_8);
            writer.write(html.toString());
            writer.flush();
            writer.close();

            if (!file.exists() || file.length() == 0) throw new Exception("File empty");

            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/html");
            share.putExtra(Intent.EXTRA_SUBJECT, title);
            share.putExtra(Intent.EXTRA_TEXT, "Файл таблицы: " + title);
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            toast(getString(R.string.toast_file_saved, file.getName()));
            startActivity(Intent.createChooser(share, "Отправить файл"));

        } catch (Exception e) {
            new AlertDialog.Builder(this, R.style.Theme_PowerM3108_Dialog)
                    .setTitle("Ошибка")
                    .setMessage("Не удалось сохранить или отправить файл: " + e.getMessage())
                    .setPositiveButton(R.string.dlg_ok, null)
                    .show();
        }
    }

    private String formatForExcel(double v) { return df.format(v).replace(".", ","); }
    private String formatCoeff3ForExcel(double v) { return String.format(Locale.US, "%.3f", v).replace(".", ","); }
    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    // ============ Утилиты ============

    private double getDoubleFromEditText(EditText et, double def) {
        if (et == null) return def;
        String t = et.getText().toString().trim().replace(",", ".");
        if (t.isEmpty()) return def;
        try { return Double.parseDouble(t); } catch (Exception e) { return def; }
    }

    private String formatCoeff3(double v) { return String.format(Locale.US, "%.3f", v); }

    private int dpToPx(int dp) { return Math.round(dp * getResources().getDisplayMetrics().density); }

    private void toast(int resId) { Toast.makeText(this, resId, Toast.LENGTH_SHORT).show(); }
    private void toast(String msg) { Toast.makeText(this, msg, Toast.LENGTH_LONG).show(); }

    private void haptic() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                VibratorManager vm = (VibratorManager) getSystemService(VIBRATOR_MANAGER_SERVICE);
                if (vm != null) {
                    Vibrator v = vm.getDefaultVibrator();
                    if (v != null && v.hasVibrator()) {
                        v.vibrate(VibrationEffect.createOneShot(14, VibrationEffect.DEFAULT_AMPLITUDE));
                    }
                }
            } else {
                @SuppressWarnings("deprecation")
                Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                if (v != null && v.hasVibrator()) v.vibrate(14);
            }
        } catch (Exception ignored) {}
    }

    private void animateDisplay() {
        if (text_na_tablo == null) return;
        text_na_tablo.animate().cancel();
        text_na_tablo.setScaleX(1f);
        text_na_tablo.setScaleY(1f);
        text_na_tablo.animate()
                .scaleX(1.10f)
                .scaleY(1.10f)
                .setDuration(80)
                .withEndAction(() -> text_na_tablo.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(140)
                        .start())
                .start();
    }

    @Override protected void onPause() { super.onPause(); saveState(); }
    @Override protected void onDestroy() { super.onDestroy(); saveState(); }
}
