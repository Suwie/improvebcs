package org.smartregister.path.activity;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.widget.DrawerLayout;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import org.smartregister.domain.FetchStatus;
import org.smartregister.immunization.db.VaccineRepo;
import org.smartregister.path.R;
import org.smartregister.path.application.VaccinatorApplication;
import org.smartregister.path.domain.CoverageHolder;
import org.smartregister.path.domain.CumulativeIndicator;
import org.smartregister.path.domain.NamedObject;
import org.smartregister.path.receiver.CoverageDropoutBroadcastReceiver;
import org.smartregister.path.repository.CumulativeIndicatorRepository;
import org.smartregister.path.toolbar.LocationSwitcherToolbar;
import org.w3c.dom.Text;

import java.io.Serializable;
import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lecho.lib.hellocharts.formatter.SimpleAxisValueFormatter;
import lecho.lib.hellocharts.model.Axis;
import lecho.lib.hellocharts.model.AxisValue;
import lecho.lib.hellocharts.model.Line;
import lecho.lib.hellocharts.model.LineChartData;
import lecho.lib.hellocharts.model.PointValue;
import lecho.lib.hellocharts.model.ValueShape;
import lecho.lib.hellocharts.model.Viewport;
import lecho.lib.hellocharts.view.LineChartView;

/**
 * Created by keyman on 03/01/17.
 */
public class FacilityCumulativeCoverageReportActivity extends BaseReportActivity implements CoverageDropoutBroadcastReceiver.CoverageDropoutServiceListener {

    public static final String HOLDER = "HOLDER";
    public static final String VACCINE = "VACCINE";
    private static final String START = "start";
    private static final String END = "end";

    CoverageHolder holder = null;
    VaccineRepo.Vaccine vaccine = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        setTitle("");

        LocationSwitcherToolbar toolbar = (LocationSwitcherToolbar) getToolbar();
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(FacilityCumulativeCoverageReportActivity.this, CoverageReportsActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                finish();
            }
        });

        ((TextView) toolbar.findViewById(R.id.title)).setText(getString(R.string.facility_cumulative_coverage_report));

        Serializable serializable = getIntent().getSerializableExtra(HOLDER);
        if (serializable != null && serializable instanceof CoverageHolder) {
            holder = (CoverageHolder) serializable;
        }

        vaccine = (VaccineRepo.Vaccine) getIntent().getSerializableExtra(VACCINE);
        String vaccineName = vaccine.display();
        if (vaccine.equals(VaccineRepo.Vaccine.penta1) || vaccine.equals(VaccineRepo.Vaccine.penta3)) {
            vaccineName = VaccineRepo.Vaccine.penta1.display() + " + 3 ";
        } else if (vaccine.equals(VaccineRepo.Vaccine.bcg) || vaccine.equals(VaccineRepo.Vaccine.measles1) || vaccine.equals(VaccineRepo.Vaccine.mr1)) {
            vaccineName = VaccineRepo.Vaccine.bcg.display() + " + " + VaccineRepo.Vaccine.measles1.display() + "/" + VaccineRepo.Vaccine.mr1.display();
        }

        TextView textView = (TextView) findViewById(R.id.report_title);
        textView.setText(String.format(getString(R.string.facility_cumulative_title), BaseReportActivity.getYear(holder.getDate()), vaccineName));

        TextView csoTargetView = (TextView) findViewById(R.id.cso_target_value);
        TextView csoTargetMonthlyView = (TextView) findViewById(R.id.cso_target_monthly_value);

        if (holder.getSize() != null) {
            Long csoTargetMonthly = holder.getSize() / 12;
            csoTargetView.setText(String.valueOf(holder.getSize()));
            csoTargetMonthlyView.setText(String.valueOf(csoTargetMonthly));
        } else {
            csoTargetView.setText("0");
            csoTargetMonthlyView.setText("0");
        }
    }

    @Override
    public void onSyncStart() {
        super.onSyncStart();
    }

    @Override
    public void onResume() {
        super.onResume();
        final DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        LinearLayout hia2 = (LinearLayout) drawer.findViewById(R.id.coverage_reports);
        hia2.setBackgroundColor(getResources().getColor(R.color.tintcolor));

        refresh(true);

        CoverageDropoutBroadcastReceiver.getInstance().addCoverageDropoutServiceListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        CoverageDropoutBroadcastReceiver.getInstance().removeCoverageDropoutServiceListener(this);
    }

    @Override
    public void onSyncComplete(FetchStatus fetchStatus) {
        super.onSyncComplete(fetchStatus);
    }

    @Override
    protected int getContentView() {
        return R.layout.activity_facility_cumulative_coverage_report;
    }

    @Override
    protected int getDrawerLayoutId() {
        return R.id.drawer_layout;
    }

    @Override
    protected int getToolbarId() {
        return LocationSwitcherToolbar.TOOLBAR_ID;
    }

    @Override
    protected Class onBackActivity() {
        return null;
    }

    @Override
    public void onServiceFinish(String actionType) {
        if (CoverageDropoutBroadcastReceiver.TYPE_GENERATE_CUMULATIVE_INDICATORS.equals(actionType)) {
            refresh(false);
        }
    }

    private void refreshMonitoring(List<CumulativeIndicator> startCumulativeIndicators, List<CumulativeIndicator> endCumulativeIndicators) {
        boolean isComparison = endCumulativeIndicators != null;

        long leftPartitions = 15;
        long csoTarget = 0L;
        long csoTargetMonthly = 0L;
        if (holder.getSize() != null) {
            csoTarget = holder.getSize();
            csoTargetMonthly = csoTarget / 12;
        }

        String[] months = new DateFormatSymbols().getShortMonths();
        Map<String, Long> startValueMap = new LinkedHashMap<>();
        Map<String, Long> endValueMap = new LinkedHashMap<>();

        // Axis
        List<AxisValue> bottomAxisValues = new ArrayList<>();
        List<AxisValue> topAxisValues = new ArrayList<>();
        List<AxisValue> leftAxisValues = new ArrayList<>();
        List<AxisValue> rightAxisValues = new ArrayList<>();

        for (int i = 0; i < leftPartitions; i++) {
            float currentMonlthyTarget = csoTargetMonthly * i;
            AxisValue leftValue = new AxisValue(currentMonlthyTarget);
            leftValue.setLabel(String.valueOf((int) currentMonlthyTarget));
            leftAxisValues.add(leftValue);

            if (i < months.length) {
                AxisValue curValue = new AxisValue((float) i);
                curValue.setLabel(months[i].toUpperCase());
                bottomAxisValues.add(curValue);

                topAxisValues.add(new AxisValue((float) i).setLabel(""));
            }

            if (i >= 1 && i <= 5) {
                float value = csoTarget * (0.25f * i);
                AxisValue rightValue = new AxisValue(value);
                rightValue.setLabel(String.format(getString(R.string.coverage_percentage), 25 * i));
                rightAxisValues.add(rightValue);
            }
        }

        // Lines
        List<Line> lines = new ArrayList<>();
        lines.add(generateLine(0.25f, csoTargetMonthly));
        lines.add(generateLine(0.5f, csoTargetMonthly));
        lines.add(generateLine(0.75f, csoTargetMonthly));
        lines.add(generateLine(1f, csoTargetMonthly));

        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("MMM");
        for (CumulativeIndicator cumulativeIndicator : startCumulativeIndicators) {
            Date month = cumulativeIndicator.getMonthAsDate();
            String monthString = simpleDateFormat.format(month);
            startValueMap.put(monthString, cumulativeIndicator.getValue());
        }

        if (isComparison) {
            for (CumulativeIndicator cumulativeIndicator : endCumulativeIndicators) {
                Date month = cumulativeIndicator.getMonthAsDate();
                String monthString = simpleDateFormat.format(month);
                endValueMap.put(monthString, cumulativeIndicator.getValue());
            }
        }

        List<PointValue> startValues = new ArrayList<>();
        List<PointValue> endValues = new ArrayList<>();

        startValues.add(new PointValue(0f, 0f));
        endValues.add(new PointValue(0f, 0f));

        boolean checkCurrentTime = false;

        Calendar calendar = null;
        Date currentDate = null;

        int year = BaseReportActivity.getYear(holder.getDate());
        int currentYear = BaseReportActivity.getYear(new Date());
        if (year >= currentYear) {
            checkCurrentTime = true;
            calendar = Calendar.getInstance();
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.DAY_OF_YEAR, 1);

            currentDate = new Date();
        }

        for (int i = 0; i < 12; i++) {
            if (checkCurrentTime) {
                if (currentDate.before(calendar.getTime())) {
                    break;
                }
                calendar.add(Calendar.MONTH, 1);
            }

            float x = 0.5f + i;
            float y = 0L;
            float z = 0L;
            for (int j = i; j >= 0; j--) {
                Long startValue = startValueMap.get(months[j]);
                if (startValue != null) {
                    y += startValue;
                }
                if (isComparison) {
                    Long endValue = endValueMap.get(months[j]);
                    if (endValue != null) {
                        z += endValue;
                    }
                }
            }
            startValues.add(new PointValue(x, y));
            if (isComparison) {
                endValues.add(new PointValue(x, z));
            }

        }

        lines.add(new Line(startValues).
                setColor(getResources().getColor(R.color.cumulative_blue_line)).
                setHasPoints(true).
                setHasLabels(false).
                setShape(ValueShape.CIRCLE).
                setHasLines(true).
                setStrokeWidth(2));

        if (isComparison) {
            lines.add(new Line(endValues).
                    setColor(getResources().getColor(R.color.cumulative_red_line)).
                    setHasPoints(true).
                    setHasLabels(false).
                    setShape(ValueShape.CIRCLE).
                    setHasLines(true).
                    setStrokeWidth(2));
        }

        LineChartData data = new LineChartData();
        data.setLines(lines);

        data.setAxisXBottom(new Axis(bottomAxisValues).setMaxLabelChars(3).setHasLines(false).setHasTiltedLabels(false).setFormatter(new MonthValueFormatter()));
        data.setAxisYLeft(new Axis(leftAxisValues).setHasLines(true).setHasTiltedLabels(false));
        data.setAxisXTop(new Axis(topAxisValues).setHasLines(true).setHasTiltedLabels(false));
        data.setAxisYRight(new Axis(rightAxisValues).setMaxLabelChars(5).setHasLines(false).setHasTiltedLabels(false));

        // Chart
        LineChartView monitoringChart = (LineChartView) findViewById(R.id.monitoring_chart);
        monitoringChart.setLineChartData(data);
        monitoringChart.setViewportCalculationEnabled(false);
        resetViewport(monitoringChart, csoTargetMonthly, leftPartitions);

        updateTableLayout(startValueMap, endValueMap, months, isComparison);
    }

    private void resetViewport(LineChartView chart, long csoTargetMonthly, long leftPartitions) {
        // Reset viewport height range to (0,100)
        Viewport v = chart.getMaximumViewport();
        v.set(v.left, csoTargetMonthly * leftPartitions, v.right, 0);
        chart.setMaximumViewport(v);
        chart.setCurrentViewport(v);
    }

    private Line generateLine(float percentageDecimal, long csoTargetMonthly) {
        List<PointValue> values = new ArrayList<>();
        for (int i = 0; i <= 12; i++) {
            float y = csoTargetMonthly * i * percentageDecimal;
            PointValue pointValue = new PointValue();
            pointValue.set(i, y);
            values.add(pointValue);
        }

        Line line = new Line(values);
        line.setHasLines(true);
        line.setHasPoints(false);
        line.setStrokeWidth(1);
        return line;
    }

    private void updateTableLayout(Map<String, Long> startValueMap, Map<String, Long> endValueMap, String[] months, boolean isComparison) {

        TableRow startTotalValueRow = (TableRow) findViewById(R.id.total_1);
        TableRow startCumValueRow = (TableRow) findViewById(R.id.cum_1);

        TableRow total2ValueRow = (TableRow) findViewById(R.id.total_2);
        TableRow cum2ValueRow = (TableRow) findViewById(R.id.cum_2);

        if (isComparison) {
            total2ValueRow.setVisibility(View.VISIBLE);
            cum2ValueRow.setVisibility(View.VISIBLE);
        }

        for (int i = 0; i < startTotalValueRow.getChildCount(); i++) {
            TextView startTotalValue = (TextView) startTotalValueRow.getChildAt(i);
            TextView startCumValue = (TextView) startCumValueRow.getChildAt(i);

            TextView endTotalValue = (TextView) total2ValueRow.getChildAt(i);
            TextView endCumValue = (TextView) cum2ValueRow.getChildAt(i);

            endTotalValue.setTextColor(getResources().getColor(R.color.cumulative_red_line));
            endCumValue.setTextColor(getResources().getColor(R.color.cumulative_red_line));

            if (i == 0) {
                if (isComparison) {
                    VaccineRepo.Vaccine startVaccine = vaccine;
                    VaccineRepo.Vaccine endVaccine = vaccine;
                    if (vaccine.equals(VaccineRepo.Vaccine.penta1) || vaccine.equals(VaccineRepo.Vaccine.penta3)) {
                        startVaccine = VaccineRepo.Vaccine.penta1;
                        endVaccine = VaccineRepo.Vaccine.penta3;
                    }
                    if (vaccine.equals(VaccineRepo.Vaccine.bcg) || vaccine.equals(VaccineRepo.Vaccine.measles1)) {
                        startVaccine = VaccineRepo.Vaccine.bcg;
                        endVaccine = VaccineRepo.Vaccine.measles1;
                    }
                    startTotalValue.setText(String.format(getString(R.string.total_vaccine), startVaccine.display()));
                    startCumValue.setText(String.format(getString(R.string.total_cum), startVaccine.display()));

                    endTotalValue.setText(String.format(getString(R.string.total_vaccine), endVaccine.display()));
                    endCumValue.setText(String.format(getString(R.string.total_cum), endVaccine.display()));

                } else {
                    startTotalValue.setText(String.format(getString(R.string.total_vaccine), vaccine.display()));
                    startCumValue.setText(String.format(getString(R.string.total_cum), vaccine.display()));

                }
            } else {
                Long startValue = startValueMap.get(months[i - 1]);
                if (startValue == null) {
                    startValue = 0L;
                }
                startTotalValue.setText(String.valueOf(startValue));

                if (isComparison) {
                    Long endValue = endValueMap.get(months[i - 1]);
                    if (endValue == null) {
                        endValue = 0L;
                    }
                    endTotalValue.setText(String.valueOf(endValue));
                }

                Long cumStartValue = 0L;
                Long cumEndValue = 0L;
                for (int j = i; j >= 1; j--) {
                    startValue = startValueMap.get(months[j - 1]);
                    if (startValue != null) {
                        cumStartValue += startValue;
                    }

                    if (isComparison) {
                        Long endValue = endValueMap.get(months[j - 1]);
                        if (endValue != null) {
                            cumEndValue += endValue;
                        }

                    }
                }
                startCumValue.setText(String.valueOf(cumStartValue));

                if (isComparison) {
                    endCumValue.setText(String.valueOf(cumEndValue));
                }
            }

        }
    }

    @Override
    protected Map<String, NamedObject<?>> generateReportBackground() {
        VaccineRepo.Vaccine startVaccine = vaccine;
        VaccineRepo.Vaccine endVaccine = null;
        if (vaccine.equals(VaccineRepo.Vaccine.penta1) || vaccine.equals(VaccineRepo.Vaccine.penta3)) {
            startVaccine = VaccineRepo.Vaccine.penta1;
            endVaccine = VaccineRepo.Vaccine.penta3;
        } else if (vaccine.equals(VaccineRepo.Vaccine.bcg) || vaccine.equals(VaccineRepo.Vaccine.measles1)) {
            startVaccine = VaccineRepo.Vaccine.bcg;
            endVaccine = VaccineRepo.Vaccine.measles1;
        }

        String orderBy = CumulativeIndicatorRepository.COLUMN_MONTH + " ASC ";

        CumulativeIndicatorRepository cumulativeIndicatorRepository = VaccinatorApplication.getInstance().cumulativeIndicatorRepository();
        List<CumulativeIndicator> startCumulativeIndicators = cumulativeIndicatorRepository.findByVaccineAndCumulativeId(generateVaccineName(startVaccine), holder.getId(), orderBy);
        List<CumulativeIndicator> endCumulativeIndicators = null;
        if (endVaccine != null) {
            endCumulativeIndicators = cumulativeIndicatorRepository.findByVaccineAndCumulativeId(generateVaccineName(endVaccine), holder.getId(), orderBy);
        }

        Map<String, NamedObject<?>> map = new HashMap<>();
        NamedObject<List<CumulativeIndicator>> startedNamedObject = new NamedObject<>(START, startCumulativeIndicators);
        map.put(startedNamedObject.name, startedNamedObject);

        NamedObject<List<CumulativeIndicator>> endNamedObject = new NamedObject<>(END, endCumulativeIndicators);
        map.put(endNamedObject.name, endNamedObject);

        return map;

    }

    @Override
    protected void generateReportUI(Map<String, NamedObject<?>> map, boolean userAction) {
        List<CumulativeIndicator> startCumulativeIndicators = new ArrayList<>();
        List<CumulativeIndicator> endCumulativeIndicators = null;

        if (map.containsKey(START)) {
            NamedObject<?> namedObject = map.get(START);
            if (namedObject != null) {
                startCumulativeIndicators = (List<CumulativeIndicator>) namedObject.object;
            }
        }

        if (map.containsKey(END)) {
            NamedObject<?> namedObject = map.get(END);
            if (namedObject != null) {
                endCumulativeIndicators = (List<CumulativeIndicator>) namedObject.object;
            }
        }

        refreshMonitoring(startCumulativeIndicators, endCumulativeIndicators);
    }

    ////////////////////////////////////////////////////////////////
    // Inner classes
    ////////////////////////////////////////////////////////////////

    private static class MonthValueFormatter extends SimpleAxisValueFormatter {

        @Override
        public int formatValueForManualAxis(char[] formattedValue, AxisValue axisValue) {
            axisValue.setValue(axisValue.getValue() + 0.5f);
            return super.formatValueForManualAxis(formattedValue, axisValue);
        }

    }
}
