package com.example.powerm3_108;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.File;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.widget.BaseAdapter;
import android.widget.ListView;

public class MainActivity extends AppCompatActivity {

    private TextView resultTextView;
    private TextView text_na_tablo;
    private TextView tableTitle;

    private EditText duty_cycle;

    private TableLayout tableLayout;

    private final float k = 0.1F;

    private static final int ROW_COUNT = 6;
    private static final double DEFAULT_COEFF = 2.5;
    private static final double DEFAULT_X = 2.5;
    private static final double DEFAULT_Y = 16.0;

    private static final String PREFS_NAME = "power_table_prefs";
    private static final String KEY_TABLE_TITLE = "table_title";
    private static final String KEY_DUTY_CYCLE = "duty_cycle";
    private static final String KEY_ACTIVE_ROW = "active_row";
    private static final String KEY_TABLE_LIST = "table_list";
    private static final String KEY_CURRENT_TABLE_NUMBER = "current_table_number";

    private final EditText[] coeffEdits = new EditText[ROW_COUNT];
    private final TextView[] resultXViews = new TextView[ROW_COUNT];
    private final TextView[] resultYViews = new TextView[ROW_COUNT];
    private final TextView[] numberViews = new TextView[ROW_COUNT];
    private final TableRow[] dataRows = new TableRow[ROW_COUNT];

    private final RowData[] rowsData = new RowData[ROW_COUNT];

    private int activeRowIndex = 0;
    private String currentTableTitle = "КИУ № 1";
    private String currentTableNumber = "1";

    private final DecimalFormat df = new DecimalFormat("#.##", new DecimalFormatSymbols(Locale.US));

    private boolean isInternalUpdate = false;

    private static class RowData {
        double coeff = DEFAULT_COEFF;
        double xValue = DEFAULT_X;
        double resultX = 0.0;
        double resultY = 0.0;
    }

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        resultTextView = findViewById(R.id.resultTextView);
        text_na_tablo = findViewById(R.id.text_na_tablo);
        tableTitle = findViewById(R.id.tableTitle);
        duty_cycle = findViewById(R.id.duty_cycle);

        Button add_up = findViewById(R.id.add_up);
        Button add_down = findViewById(R.id.add_down);
        Button btnSaveSend = findViewById(R.id.btnSaveSend);
        Button btnClearTable = findViewById(R.id.btnClearTable);
        tableLayout = findViewById(R.id.tableLayout);

        initRowsData();
        restoreState();

        if (tableTitle != null) {
            tableTitle.setText(currentTableTitle);
        }

        createTable();
        fillViewsFromData();
        sanitizeDutyCycleIfNeeded();
        updateAllRowsCalculation();
        selectRow(activeRowIndex);

        add_down.setOnClickListener(this::onClickDown);
        add_up.setOnClickListener(this::onClickUp);

        btnSaveSend.setOnClickListener(v -> confirmSaveAndShare());
        btnClearTable.setOnClickListener(v -> confirmClearTable());

        tableTitle.setOnClickListener(v -> showTableManagerDialog());

        duty_cycle.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isInternalUpdate) return;

                sanitizeDutyCycleIfNeeded();
                updateAllRowsCalculation();
                updateMainCalculation();
                saveState();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        View mainView = findViewById(R.id.main);
        if (mainView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(mainView, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return insets;
            });
        }
    }

    private void initRowsData() {
        for (int i = 0; i < ROW_COUNT; i++) {
            rowsData[i] = new RowData();
        }
    }

    private void restoreState() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        currentTableNumber = prefs.getString(KEY_CURRENT_TABLE_NUMBER, "1");
        currentTableTitle = buildTableTitle(currentTableNumber);

        activeRowIndex = prefs.getInt(KEY_ACTIVE_ROW, 0);

        String savedDuty = prefs.getString(KEY_DUTY_CYCLE, df.format(DEFAULT_Y));
        if (duty_cycle != null) {
            duty_cycle.setText(savedDuty);
        }

        loadTableDataForCurrentTable();

        if (activeRowIndex < 0 || activeRowIndex >= ROW_COUNT) {
            activeRowIndex = 0;
        }
    }

    private void loadTableDataForCurrentTable() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        for (int i = 0; i < ROW_COUNT; i++) {
            rowsData[i].coeff = Double.longBitsToDouble(
                    prefs.getLong(getCoeffKey(currentTableNumber, i), Double.doubleToLongBits(DEFAULT_COEFF))
            );

            rowsData[i].xValue = Double.longBitsToDouble(
                    prefs.getLong(getXKey(currentTableNumber, i), Double.doubleToLongBits(DEFAULT_X))
            );
        }

        double dutyValue = Double.longBitsToDouble(
                prefs.getLong(getDutyKey(currentTableNumber), Double.doubleToLongBits(DEFAULT_Y))
        );

        if (duty_cycle != null) {
            isInternalUpdate = true;
            duty_cycle.setText(df.format(dutyValue));
            isInternalUpdate = false;
        }
    }
    
    private void saveTableDataForCurrentTable() {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();

        for (int i = 0; i < ROW_COUNT; i++) {
            editor.putLong(
                    getCoeffKey(currentTableNumber, i),
                    Double.doubleToLongBits(rowsData[i].coeff)
            );

            editor.putLong(
                    getXKey(currentTableNumber, i),
                    Double.doubleToLongBits(rowsData[i].xValue)
            );
        }

        double dutyValue = getDoubleFromEditText(duty_cycle, DEFAULT_Y);
        editor.putLong(
                getDutyKey(currentTableNumber),
                Double.doubleToLongBits(dutyValue)
        );

        editor.apply();
    }

    private String getCoeffKey(String tableNumber, int index) {
        return "coeff_table_" + tableNumber + "_" + index;
    }

    private String getXKey(String tableNumber, int index) {
        return "x_table_" + tableNumber + "_" + index;
    }

    private String getDutyKey(String tableNumber) {
        return "duty_table_" + tableNumber;
    }

    private java.util.ArrayList<String> getTableNumberList() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String raw = prefs.getString(KEY_TABLE_LIST, "");

        java.util.ArrayList<String> list = new java.util.ArrayList<>();

        if (!raw.isEmpty()) {
            String[] items = raw.split(";");
            for (String item : items) {
                String trimmed = item.trim();
                if (!trimmed.isEmpty() && !list.contains(trimmed)) {
                    list.add(trimmed);
                }
            }
        }

        if (list.isEmpty()) {
            list.add("1");
        }

        return list;
    }

    private void saveTableNumberList(java.util.ArrayList<String> list) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(";");
            sb.append(list.get(i));
        }

        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        editor.putString(KEY_TABLE_LIST, sb.toString());
        editor.apply();
    }

    private void showAddNewTableDialog() {
        final EditText input = new EditText(this);
        input.setHint("Введите номер");
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setPadding(32, 24, 32, 24);

        new AlertDialog.Builder(this)
                .setTitle("Добавить новый")
                .setView(input)
                .setPositiveButton("OK", (dialog, which) -> {
                    String newNumber = input.getText().toString().trim();

                    if (newNumber.isEmpty()) {
                        Toast.makeText(this, "Номер таблицы пустой", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    java.util.ArrayList<String> list = getTableNumberList();
                    if (list.contains(newNumber)) {
                        Toast.makeText(this, "Такой номер уже существует", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    list.add(newNumber);
                    saveTableNumberList(list);

                    currentTableNumber = newNumber;
                    currentTableTitle = buildTableTitle(currentTableNumber);

                    for (int i = 0; i < ROW_COUNT; i++) {
                        rowsData[i].coeff = DEFAULT_COEFF;
                        rowsData[i].xValue = DEFAULT_X;
                    }

                    if (duty_cycle != null) {
                        isInternalUpdate = true;
                        duty_cycle.setText(df.format(DEFAULT_Y));
                        isInternalUpdate = false;
                    }

                    saveTableDataForCurrentTable();
                    updateCoeffViewsFromData();

                    if (tableTitle != null) {
                        tableTitle.setText(currentTableTitle);
                    }

                    updateAllRowsCalculation();
                    updateMainCalculation();
                    saveState();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void selectTableByNumber(String tableNumber) {
        saveTableDataForCurrentTable();

        currentTableNumber = tableNumber;
        currentTableTitle = buildTableTitle(currentTableNumber);

        loadTableDataForCurrentTable();
        updateCoeffViewsFromData();

        if (tableTitle != null) {
            tableTitle.setText(currentTableTitle);
        }

        updateAllRowsCalculation();
        updateMainCalculation();
        saveState();
    }

    private void renameTable(String oldNumber) {
        final EditText input = new EditText(this);
        input.setHint("Введите новый номер");
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(oldNumber);
        input.setSelection(input.getText().length());

        new AlertDialog.Builder(this)
                .setTitle("Изменить номер")
                .setView(input)
                .setPositiveButton("OK", (dialog, which) -> {
                    String newNumber = input.getText().toString().trim();

                    if (newNumber.isEmpty()) {
                        Toast.makeText(this, "Номер пустой", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (newNumber.equals(oldNumber)) {
                        return;
                    }

                    java.util.ArrayList<String> list = getTableNumberList();
                    if (list.contains(newNumber)) {
                        Toast.makeText(this, "Такой номер уже существует", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                    SharedPreferences.Editor editor = prefs.edit();

                    for (int i = 0; i < ROW_COUNT; i++) {
                        long coeffValue = prefs.getLong(
                                getCoeffKey(oldNumber, i),
                                Double.doubleToLongBits(DEFAULT_COEFF)
                        );
                        editor.putLong(getCoeffKey(newNumber, i), coeffValue);
                        editor.remove(getCoeffKey(oldNumber, i));

                        long xValue = prefs.getLong(
                                getXKey(oldNumber, i),
                                Double.doubleToLongBits(DEFAULT_X)
                        );
                        editor.putLong(getXKey(newNumber, i), xValue);
                        editor.remove(getXKey(oldNumber, i));
                    }

                    long dutyValue = prefs.getLong(
                            getDutyKey(oldNumber),
                            Double.doubleToLongBits(DEFAULT_Y)
                    );
                    editor.putLong(getDutyKey(newNumber), dutyValue);
                    editor.remove(getDutyKey(oldNumber));


                    int index = list.indexOf(oldNumber);
                    if (index >= 0) {
                        list.set(index, newNumber);
                    }

                    saveTableNumberList(list);
                    editor.apply();

                    if (oldNumber.equals(currentTableNumber)) {
                        currentTableNumber = newNumber;
                        currentTableTitle = buildTableTitle(currentTableNumber);

                        if (tableTitle != null) {
                            tableTitle.setText(currentTableTitle);
                        }

                        loadTableDataForCurrentTable();
                        updateCoeffViewsFromData();
                        updateAllRowsCalculation();
                        updateMainCalculation();
                    }

                    saveState();
                    Toast.makeText(this, "Номер изменён", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void deleteTable(String tableNumber) {
        java.util.ArrayList<String> list = getTableNumberList();

        if (list.size() <= 1) {
            Toast.makeText(this, "Нельзя удалить последнюю таблицу", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Удаление")
                .setMessage("Удалить " + buildTableTitle(tableNumber) + "?")
                .setPositiveButton("Да", (dialog, which) -> {
                    SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();

                    for (int i = 0; i < ROW_COUNT; i++) {
                        editor.remove(getCoeffKey(tableNumber, i));
                        editor.remove(getXKey(tableNumber, i));
                    }

                    editor.remove(getDutyKey(tableNumber));

                    list.remove(tableNumber);
                    saveTableNumberList(list);

                    if (tableNumber.equals(currentTableNumber)) {
                        currentTableNumber = list.get(0);
                        currentTableTitle = buildTableTitle(currentTableNumber);

                        loadTableDataForCurrentTable();
                        updateCoeffViewsFromData();

                        if (tableTitle != null) {
                            tableTitle.setText(currentTableTitle);
                        }

                        updateAllRowsCalculation();
                        updateMainCalculation();
                    }

                    editor.apply();
                    saveState();

                    Toast.makeText(this, "Таблица удалена", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Нет", null)
                .show();
    }

    private class TableListAdapter extends BaseAdapter {

        private final java.util.ArrayList<String> tableNumbers;
        private final AlertDialog parentDialog;

        TableListAdapter(java.util.ArrayList<String> tableNumbers, AlertDialog parentDialog) {
            this.tableNumbers = tableNumbers;
            this.parentDialog = parentDialog;
        }

        @Override
        public int getCount() {
            return tableNumbers.size();
        }

        @Override
        public Object getItem(int position) {
            return tableNumbers.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, android.view.ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = getLayoutInflater().inflate(R.layout.item_table_entry, parent, false);
            }

            TextView tvTableName = view.findViewById(R.id.tvTableName);
            Button btnEditTable = view.findViewById(R.id.btnEditTable);
            Button btnDeleteTable = view.findViewById(R.id.btnDeleteTable);

            String tableNumber = tableNumbers.get(position);
            tvTableName.setText(buildTableTitle(tableNumber));

            tvTableName.setOnClickListener(v -> {
                selectTableByNumber(tableNumber);
                parentDialog.dismiss();
            });

            btnEditTable.setOnClickListener(v -> {
                parentDialog.dismiss();
                renameTable(tableNumber);
            });

            btnDeleteTable.setOnClickListener(v -> {
                parentDialog.dismiss();
                deleteTable(tableNumber);
            });

            return view;
        }
    }

    private void updateCoeffViewsFromData() {
        isInternalUpdate = true;

        for (int i = 0; i < ROW_COUNT; i++) {
            if (coeffEdits[i] != null) {
                coeffEdits[i].setText(formatCoeff3(rowsData[i].coeff));
            }
        }

        isInternalUpdate = false;
    }

    private void showTableManagerDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_table_selector, null);
        Button btnAddNewTable = dialogView.findViewById(R.id.btnAddNewTable);
        ListView listViewTables = dialogView.findViewById(R.id.listViewTables);

        java.util.ArrayList<String> tableNumbers = getTableNumberList();

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Таблицы")
                .setView(dialogView)
                .setNegativeButton("Закрыть", null)
                .create();

        TableListAdapter adapter = new TableListAdapter(tableNumbers, dialog);
        listViewTables.setAdapter(adapter);

        btnAddNewTable.setOnClickListener(v -> {
            dialog.dismiss();
            showAddNewTableDialog();
        });

        dialog.show();
    }

    private void saveState() {
        saveTableDataForCurrentTable();

        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();

        editor.putString(KEY_CURRENT_TABLE_NUMBER, currentTableNumber);
        editor.putString(KEY_TABLE_TITLE, currentTableTitle);
        editor.putString(KEY_DUTY_CYCLE, duty_cycle != null ? duty_cycle.getText().toString().trim() : df.format(DEFAULT_Y));
        editor.putInt(KEY_ACTIVE_ROW, activeRowIndex);

        for (int i = 0; i < ROW_COUNT; i++) {
            editor.putLong("row_x_" + i, Double.doubleToLongBits(rowsData[i].xValue));
        }

        editor.apply();
    }

    private void createTable() {
        if (tableLayout == null) return;

        tableLayout.removeAllViews();

        TableRow headerRow = new TableRow(this);
        headerRow.addView(createHeaderTextView("№ Л", true));
        headerRow.addView(createHeaderTextView("Коэфф.", false));
        headerRow.addView(createHeaderTextView("Р средняя", false));
        headerRow.addView(createHeaderTextView("Р импульс", false));
        tableLayout.addView(headerRow);

        for (int i = 0; i < ROW_COUNT; i++) {
            final int index = i;

            TableRow row = new TableRow(this);
            dataRows[i] = row;

            TextView numberView = createCellTextView(String.valueOf(i + 1), true);
            numberViews[i] = numberView;
            row.addView(numberView);

            EditText coeffEdit = new EditText(this);
            coeffEdit.setHint("2.5");
            coeffEdit.setGravity(Gravity.CENTER);
            coeffEdit.setPadding(12, 12, 12, 12);
            coeffEdit.setMinWidth(dpToPx(72));
            coeffEdit.setEms(6);
            coeffEdit.setInputType(InputType.TYPE_CLASS_NUMBER
                    | InputType.TYPE_NUMBER_FLAG_DECIMAL
                    | InputType.TYPE_NUMBER_FLAG_SIGNED);
            coeffEdit.setTextColor(Color.BLACK);
            coeffEdit.setHintTextColor(Color.DKGRAY);
            coeffEdit.setBackgroundResource(android.R.drawable.edit_text);

            coeffEdit.setOnClickListener(v -> selectRow(index));
            coeffEdit.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    selectRow(index);
                }
            });

            coeffEdit.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (isInternalUpdate) return;

                    if (activeRowIndex != index) {
                        selectRow(index);
                    }

                    rowsData[index].coeff = getDoubleFromEditText(coeffEdits[index], DEFAULT_COEFF);
                    updateRowCalculation(index);

                    if (index == activeRowIndex) {
                        updateMainCalculation();
                    }

                    saveState();
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            });

            coeffEdits[i] = coeffEdit;
            row.addView(coeffEdit);

            TextView resultX = createCellTextView("0", false);
            resultXViews[i] = resultX;
            row.addView(resultX);

            TextView resultY = createCellTextView("0", false);
            resultYViews[i] = resultY;
            row.addView(resultY);

            row.setOnClickListener(v -> selectRow(index));
            numberView.setOnClickListener(v -> selectRow(index));
            resultX.setOnClickListener(v -> selectRow(index));
            resultY.setOnClickListener(v -> selectRow(index));

            tableLayout.addView(row);
        }
    }

    private void fillViewsFromData() {
        isInternalUpdate = true;
        for (int i = 0; i < ROW_COUNT; i++) {
            if (coeffEdits[i] != null) {
                coeffEdits[i].setText(formatCoeff3(rowsData[i].coeff));
            }
        }
        isInternalUpdate = false;
    }

    private TextView createHeaderTextView(String text, boolean isFirstColumn) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(20, 20, 20, 20);
        tv.setTextSize(14f);
        tv.setTextColor(Color.WHITE);
        tv.setBackgroundColor(0xFF1B4332);

        TableRow.LayoutParams params = new TableRow.LayoutParams(
                isFirstColumn ? dpToPx(72) : TableRow.LayoutParams.WRAP_CONTENT,
                TableRow.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(4, 4, 4, 4);
        tv.setLayoutParams(params);
        tv.setMinWidth(isFirstColumn ? dpToPx(72) : 0);

        return tv;
    }

    private TextView createCellTextView(String text, boolean isFirstColumn) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(20, 20, 20, 20);
        tv.setTextSize(16f);
        tv.setTextColor(Color.BLACK);
        tv.setBackgroundColor(0xFFF1FAEE);

        TableRow.LayoutParams params = new TableRow.LayoutParams(
                isFirstColumn ? dpToPx(72) : TableRow.LayoutParams.WRAP_CONTENT,
                TableRow.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(4, 4, 4, 4);
        tv.setLayoutParams(params);
        tv.setMinWidth(isFirstColumn ? dpToPx(72) : 0);

        return tv;
    }

    private void selectRow(int index) {
        if (index < 0 || index >= ROW_COUNT) return;

        activeRowIndex = index;

        for (int i = 0; i < ROW_COUNT; i++) {
            if (dataRows[i] == null || numberViews[i] == null || resultXViews[i] == null || resultYViews[i] == null) {
                continue;
            }

            if (i == activeRowIndex) {
                dataRows[i].setBackgroundColor(0xFFB7E4C7);
                numberViews[i].setBackgroundColor(0xFF95D5B2);
                resultXViews[i].setBackgroundColor(0xFFD8F3DC);
                resultYViews[i].setBackgroundColor(0xFFD8F3DC);
            } else {
                dataRows[i].setBackgroundColor(Color.TRANSPARENT);
                numberViews[i].setBackgroundColor(0xFFF1FAEE);
                resultXViews[i].setBackgroundColor(0xFFF1FAEE);
                resultYViews[i].setBackgroundColor(0xFFF1FAEE);
            }
        }

        if (text_na_tablo != null) {
            text_na_tablo.setText(df.format(rowsData[index].xValue));
        }

        updateMainCalculation();
        saveState();
    }

    private void updateMainCalculation() {
        if (resultTextView == null || text_na_tablo == null) return;

        double resultX = rowsData[activeRowIndex].resultX;
        text_na_tablo.setText(df.format(rowsData[activeRowIndex].xValue));
        resultTextView.setText(df.format(resultX));
    }

    private void onClickDown(View view) {
        double newValue = rowsData[activeRowIndex].xValue - k;
        if (newValue < 0) {
            newValue = 0;
        }

        rowsData[activeRowIndex].xValue = roundToOneDecimal(newValue);

        updateRowCalculation(activeRowIndex);
        updateMainCalculation();
        saveState();
    }

    private void onClickUp(View view) {
        double newValue = rowsData[activeRowIndex].xValue + k;
        rowsData[activeRowIndex].xValue = roundToOneDecimal(newValue);

        updateRowCalculation(activeRowIndex);
        updateMainCalculation();
        saveState();
    }

    private void updateAllRowsCalculation() {
        for (int i = 0; i < ROW_COUNT; i++) {
            if (coeffEdits[i] != null) {
                rowsData[i].coeff = getDoubleFromEditText(coeffEdits[i], DEFAULT_COEFF);
            }
            updateRowCalculation(i);
        }
    }

    private void updateRowCalculation(int index) {
        double y = getDoubleFromEditText(duty_cycle, DEFAULT_Y);

        rowsData[index].resultX = rowsData[index].coeff * rowsData[index].xValue;
        rowsData[index].resultY = rowsData[index].resultX * y;

        if (resultXViews[index] != null) {
            resultXViews[index].setText(df.format(rowsData[index].resultX));
        }
        if (resultYViews[index] != null) {
            resultYViews[index].setText(df.format(rowsData[index].resultY));
        }
    }

    private void sanitizeDutyCycleIfNeeded() {
        if (duty_cycle == null) return;

        String text = duty_cycle.getText().toString().trim().replace(",", ".");
        if (text.isEmpty()) return;

        try {
            double value = Double.parseDouble(text);
            if (value < 0) {
                isInternalUpdate = true;
                duty_cycle.setText("0");
                duty_cycle.setSelection(duty_cycle.getText().length());
                isInternalUpdate = false;
                Toast.makeText(this, "Q не может быть меньше нуля", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception ignored) {
        }
    }

    private void confirmSaveAndShare() {
        new AlertDialog.Builder(this)
                .setTitle("Сохранение и отправка")
                .setMessage("Сохранить таблицу в Excel-совместимый файл и открыть окно отправки?")
                .setPositiveButton("Да", (dialog, which) -> saveXlsHtmlAndShare())
                .setNegativeButton("Нет", null)
                .show();
    }

    private String extractTableNumber() {
        if (currentTableTitle == null) return "unknown";

        String number = currentTableTitle.trim();

        if (number.startsWith("КИУ № ")) {
            number = number.substring("КИУ № ".length()).trim();
        } else if (number.startsWith("КИУ №")) {
            number = number.substring("КИУ №".length()).trim();
        }

        if (number.isEmpty()) {
            return "unknown";
        }

        return number.replaceAll("[^0-9A-Za-z_-]", "_");
    }

    private void saveXlsHtmlAndShare() {
        try {
            String dateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
            String tableNumber = extractTableNumber();
            String fileName = "Power_" + tableNumber + "_" +
                    new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".html";

            File dir = getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS);
            if (dir == null) {
                throw new Exception("Папка Documents недоступна");
            }

            if (!dir.exists() && !dir.mkdirs()) {
                throw new Exception("Не удалось создать папку Documents");
            }

            File xlsFile = new File(dir, fileName);

            String dutyText = duty_cycle != null ? duty_cycle.getText().toString().trim() : "";

            StringBuilder html = new StringBuilder();
            html.append("<html>");
            html.append("<head>");
            html.append("<meta charset=\"UTF-8\">");
            html.append("</head>");
            html.append("<body>");
            html.append("<table border='1' cellspacing='0' cellpadding='6' style='border-collapse:collapse; text-align:center; font-family:Arial;'>");

            html.append("<tr>")
                    .append("<td colspan='5' style='font-size:16pt; font-weight:bold; background:#d9ead3; text-align:center; vertical-align:middle;'>")
                    .append(escapeHtml(currentTableTitle))
                    .append("</td>")
                    .append("</tr>");

            html.append("<tr>")
                    .append("<td style='font-weight:bold;'>Дата и время</td>")
                    .append("<td colspan='4'>")
                    .append(escapeHtml(dateTime))
                    .append("</td>")
                    .append("</tr>");

            html.append("<tr>")
                    .append("<td style='font-weight:bold;'>Скважность Q</td>")
                    .append("<td colspan='4'>")
                    .append(escapeHtml(dutyText))
                    .append("</td>")
                    .append("</tr>");

            html.append("<tr><td colspan='5' style='background:#ffffff; height:12px;'></td></tr>");

            html.append("<tr style='font-weight:bold; background:#b6d7a8; text-align:center; vertical-align:middle;'>")
                    .append("<td>№ Л</td>")
                    .append("<td>Коэфф.</td>")
                    .append("<td>М3-108</td>")
                    .append("<td>Р среднее</td>")
                    .append("<td>Р импульс</td>")
                    .append("</tr>");

            for (int i = 0; i < ROW_COUNT; i++) {
                html.append("<tr>")
                        .append("<td style='text-align:center; vertical-align:middle; mso-number-format:\"0\";'>")
                        .append(i + 1)
                        .append("</td>")

                        .append("<td style='text-align:center; vertical-align:middle; mso-number-format:\"\\@\";'>")
                        .append(formatCoeff3ForExcel(rowsData[i].coeff))
                        .append("</td>")

                        .append("<td style='text-align:center; vertical-align:middle; mso-number-format:\"\\@\";'>")
                        .append(formatForExcel(rowsData[i].xValue))
                        .append("</td>")

                        .append("<td style='text-align:center; vertical-align:middle; background:#d9ead3; mso-number-format:\"\\@\";'>")
                        .append(formatForExcelOneDecimalNoRound(rowsData[i].resultX))
                        .append("</td>")

                        .append("<td style='text-align:center; vertical-align:middle; mso-number-format:\"\\@\";'>")
                        .append(formatForExcelOneDecimalNoRound(rowsData[i].resultY))
                        .append("</td>")

                        .append("</tr>");
            }

            html.append("</table>");
            html.append("</body>");
            html.append("</html>");

            java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(
                    new java.io.FileOutputStream(xlsFile),
                    java.nio.charset.StandardCharsets.UTF_8
            );

            writer.write(html.toString());
            writer.flush();
            writer.close();

            if (!xlsFile.exists() || xlsFile.length() == 0) {
                throw new Exception("Файл не был создан или пустой");
            }

            Uri uri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".provider",
                    xlsFile
            );

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/html");
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, currentTableTitle);
            shareIntent.putExtra(Intent.EXTRA_TEXT, "Файл таблицы: " + currentTableTitle);
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            Toast.makeText(this, "Файл сохранён: " + xlsFile.getName(), Toast.LENGTH_LONG).show();
            startActivity(Intent.createChooser(shareIntent, "Отправить файл"));

        } catch (Exception e) {
            Toast.makeText(this, "Ошибка: " + e.getClass().getSimpleName() + ": " + e.getMessage(), Toast.LENGTH_LONG).show();

            new AlertDialog.Builder(this)
                    .setTitle("Ошибка")
                    .setMessage("Не удалось сохранить или отправить файл: "
                            + e.getClass().getName() + " "
                            + e.getMessage())
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    private String formatForExcel(double value) {
        return df.format(value).replace(".", ",");
    }

    // Метод форматирования числа для сохранения: "(value * 100)) / 100.0;" - это значит будет записано два знака после запятой; "(value * 10)) / 10.0;" - при таком значении будет писаться один знак после запятой.
    private String formatForExcelOneDecimalNoRound(double value) {
        double truncated = ((long) (value * 100)) / 100.0;
        return String.valueOf(truncated).replace(".", ",");
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private void confirmClearTable() {
        new AlertDialog.Builder(this)
                .setTitle("Очистить таблицу")
                .setMessage("Сбросить все значения таблицы к значениям по умолчанию?")
                .setPositiveButton("Да", (dialog, which) -> clearTable())
                .setNegativeButton("Нет", null)
                .show();
    }

    private void clearTable() {
        isInternalUpdate = true;

        currentTableTitle = "КИУ № 1";
        if (tableTitle != null) {
            tableTitle.setText(currentTableTitle);
        }

        if (duty_cycle != null) {
            duty_cycle.setText(df.format(DEFAULT_Y));
        }

        for (int i = 0; i < ROW_COUNT; i++) {
            rowsData[i].coeff = DEFAULT_COEFF;
            rowsData[i].xValue = DEFAULT_X;
            if (coeffEdits[i] != null) {
                coeffEdits[i].setText(df.format(DEFAULT_COEFF));
            }
        }

        activeRowIndex = 0;
        isInternalUpdate = false;

        updateAllRowsCalculation();
        selectRow(activeRowIndex);
        saveState();

        Toast.makeText(this, "Таблица очищена", Toast.LENGTH_SHORT).show();
    }

    private double getDoubleFromEditText(EditText editText, double defaultValue) {
        if (editText == null) return defaultValue;

        String text = editText.getText().toString().trim().replace(",", ".");
        if (text.isEmpty()) return defaultValue;

        try {
            return Double.parseDouble(text);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private double roundToOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveState();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        saveState();
    }

    private String buildTableTitle(String tableNumber) {
        return "КИУ № " + tableNumber;
    }

    private String formatCoeff3(double value) {
        return String.format(Locale.US, "%.3f", value);
    }

    private String formatCoeff3ForExcel(double value) {
        return String.format(Locale.US, "%.3f", value).replace(".", ",");
    }

}
