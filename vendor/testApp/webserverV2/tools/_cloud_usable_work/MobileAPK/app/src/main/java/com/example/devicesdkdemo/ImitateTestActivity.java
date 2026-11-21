package com.example.devicesdkdemo;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import com.huawei.aisfsdkv2.api.AISFSDKClient;
import com.huawei.aisfsdkv2.api.timertask.CreateScheduleTaskRequest;
import com.huawei.aisfsdkv2.api.timertask.DeleteScheduleTaskRequest;
import com.huawei.aisfsdkv2.api.timertask.ScheduleAction;
import com.huawei.aisfsdkv2.api.timertask.ScheduleInfo;
import com.huawei.aisfsdkv2.api.timertask.ScheduleTypeEnum;
import com.huawei.aisfsdkv2.api.timertask.TaskAbilityInfo;
import com.huawei.aisfsdkv2.api.timertask.TaskAbilityResult;
import com.huawei.aisfsdkv2.api.timertask.TaskSchedule;
import com.huawei.aisfsdkv2.api.timertask.TimerTaskApi;
import com.huawei.aisfsdkv2.api.timertask.TimerTaskModel;
import com.huawei.aisfsdkv2.api.timertask.UpdateScheduleTaskRequest;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 定时任务接口测试页面
 */
public class ImitateTestActivity extends AppCompatActivity {

    private TimerTaskApi timerTaskApi;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    // 折叠项视图
    private LinearLayout contentGetTaskAbilityInfoList;
    private TextView titleGetTaskAbilityInfoList;
    private LinearLayout contentGetScheduleTaskList;
    private TextView titleGetScheduleTaskList;
    private LinearLayout contentCreateScheduleTask;
    private TextView titleCreateScheduleTask;
    private LinearLayout contentDeleteScheduleTask;
    private TextView titleDeleteScheduleTask;
    private LinearLayout contentUpdateScheduleTask;
    private TextView titleUpdateScheduleTask;

    // 当前展开的项
    private LinearLayout currentExpandedContent = null;
    private TextView currentExpandedTitle = null;
    private String currentExpandedText = "";

    // createScheduleTask 下拉列表
    private Spinner spinnerScheduleType;
    private Spinner spinnerActionType;
    // 选中的值
    private String selectedScheduleType = "once";
    private String selectedActionType = "SKILL";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_imitate_test);

        initTimerTaskApi();
        initViews();
    }

    private void initTimerTaskApi() {
        try {
            timerTaskApi = AISFSDKClient.getTimerTaskApi();
        } catch (Exception e) {
            // 初始化失败
        }
    }

    private void initViews() {
        // 返回按钮
        Button btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        // 折叠项1: getTaskAbilityInfoList
        titleGetTaskAbilityInfoList = findViewById(R.id.title_get_task_ability_info_list);
        contentGetTaskAbilityInfoList = findViewById(R.id.content_get_task_ability_info_list);
        Button btnTestGetTaskAbilityInfoList = findViewById(R.id.btn_test_get_task_ability_info_list);
        TextView tvOutputGetTaskAbilityInfoList = findViewById(R.id.tv_output_get_task_ability_info_list);

        titleGetTaskAbilityInfoList.setOnClickListener(v -> toggleItem(
                contentGetTaskAbilityInfoList, titleGetTaskAbilityInfoList, "测试 getTaskAbilityInfoList"));
        btnTestGetTaskAbilityInfoList.setOnClickListener(v ->
                testGetTaskAbilityInfoList(tvOutputGetTaskAbilityInfoList));

        // 折叠项2: getScheduleTaskList
        titleGetScheduleTaskList = findViewById(R.id.title_get_schedule_task_list);
        contentGetScheduleTaskList = findViewById(R.id.content_get_schedule_task_list);
        Button btnTestGetScheduleTaskList = findViewById(R.id.btn_test_get_schedule_task_list);
        TextView tvOutputGetScheduleTaskList = findViewById(R.id.tv_output_get_schedule_task_list);

        titleGetScheduleTaskList.setOnClickListener(v -> toggleItem(
                contentGetScheduleTaskList, titleGetScheduleTaskList, "测试 getScheduleTaskList"));
        btnTestGetScheduleTaskList.setOnClickListener(v ->
                testGetScheduleTaskList(tvOutputGetScheduleTaskList));

        // 折叠项3: createScheduleTask
        titleCreateScheduleTask = findViewById(R.id.title_create_schedule_task);
        contentCreateScheduleTask = findViewById(R.id.content_create_schedule_task);
        Button btnTestCreateScheduleTask = findViewById(R.id.btn_test_create_schedule_task);
        TextView tvOutputCreateScheduleTask = findViewById(R.id.tv_output_create_schedule_task);

        // 初始化 Schedule type 下拉列表
        spinnerScheduleType = findViewById(R.id.spinner_schedule_type);
        ArrayAdapter<String> scheduleTypeAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, new String[]{"once", "recurring"});
        scheduleTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerScheduleType.setAdapter(scheduleTypeAdapter);
        spinnerScheduleType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedScheduleType = position == 0 ? "once" : "recurring";
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // 初始化 Action type 下拉列表
        spinnerActionType = findViewById(R.id.spinner_action_type);
        ArrayAdapter<String> actionTypeAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, new String[]{"SKILL", "API", "Agent"});
        actionTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerActionType.setAdapter(actionTypeAdapter);
        spinnerActionType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String[] types = {"SKILL", "API", "Agent"};
                selectedActionType = types[position];
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // 设置 triggerTime 默认值为明天同一时间
        EditText etTriggerTime = findViewById(R.id.et_trigger_time);
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.add(java.util.Calendar.DAY_OF_MONTH, 1);  // 加1天
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault());
        etTriggerTime.setText(sdf.format(calendar.getTime()));

        titleCreateScheduleTask.setOnClickListener(v -> toggleItem(
                contentCreateScheduleTask, titleCreateScheduleTask, "测试 createScheduleTask"));
        btnTestCreateScheduleTask.setOnClickListener(v ->
                testCreateScheduleTask(tvOutputCreateScheduleTask));

        // 折叠项4: deleteScheduleTask
        titleDeleteScheduleTask = findViewById(R.id.title_delete_schedule_task);
        contentDeleteScheduleTask = findViewById(R.id.content_delete_schedule_task);
        Button btnTestDeleteScheduleTask = findViewById(R.id.btn_test_delete_schedule_task);
        TextView tvOutputDeleteScheduleTask = findViewById(R.id.tv_output_delete_schedule_task);

        titleDeleteScheduleTask.setOnClickListener(v -> toggleItem(
                contentDeleteScheduleTask, titleDeleteScheduleTask, "测试 deleteScheduleTask"));
        btnTestDeleteScheduleTask.setOnClickListener(v ->
                testDeleteScheduleTask(tvOutputDeleteScheduleTask));

        // 折叠项5: updateScheduleTask
        titleUpdateScheduleTask = findViewById(R.id.title_update_schedule_task);
        contentUpdateScheduleTask = findViewById(R.id.content_update_schedule_task);
        Button btnTestUpdateScheduleTask = findViewById(R.id.btn_test_update_schedule_task);
        TextView tvOutputUpdateScheduleTask = findViewById(R.id.tv_output_update_schedule_task);

        titleUpdateScheduleTask.setOnClickListener(v -> toggleItem(
                contentUpdateScheduleTask, titleUpdateScheduleTask, "测试 updateScheduleTask"));
        btnTestUpdateScheduleTask.setOnClickListener(v ->
                testUpdateScheduleTask(tvOutputUpdateScheduleTask));
    }

    /**
     * 切换折叠项展开/折叠状态
     */
    private void toggleItem(LinearLayout contentView, TextView titleView, String title) {
        if (contentView.getVisibility() == View.VISIBLE) {
            contentView.setVisibility(View.GONE);
            titleView.setText("▶ " + title);
            currentExpandedContent = null;
            currentExpandedTitle = null;
            currentExpandedText = "";
        } else {
            if (currentExpandedContent != null) {
                currentExpandedContent.setVisibility(View.GONE);
                currentExpandedTitle.setText("▶ " + currentExpandedText);
            }
            contentView.setVisibility(View.VISIBLE);
            titleView.setText("▼ " + title);
            currentExpandedContent = contentView;
            currentExpandedTitle = titleView;
            currentExpandedText = title;
        }
    }

    /**
     * 测试 getTaskAbilityInfoList 接口
     */
    private void testGetTaskAbilityInfoList(TextView outputView) {
        if (timerTaskApi == null) {
            outputView.setText("错误: TimerTaskApi 未初始化");
            return;
        }
        outputView.setText("测试中...");
        new Thread(() -> {
            long startTime = System.currentTimeMillis();
            try {
                TaskAbilityResult result = timerTaskApi.getTaskAbilityInfoList();
                long duration = System.currentTimeMillis() - startTime;
                mainHandler.post(() -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append("[").append(getTimestamp()).append("] 测试成功\n");
                    sb.append("响应时间: ").append(duration).append("ms\n\n");
                    if (result == null || result.getTaskAbilityInfoMap() == null
                            || result.getTaskAbilityInfoMap().isEmpty()) {
                        sb.append("结果: 任务能力列表为空\n");
                    } else {
                        Map<String, List<TaskAbilityInfo>> map = result.getTaskAbilityInfoMap();
                        sb.append("结果: 共 ").append(map.size()).append(" 个任务分类\n\n");
                        int idx = 1;
                        for (Map.Entry<String, List<TaskAbilityInfo>> entry : map.entrySet()) {
                            sb.append("=== 分类 [").append(idx++).append("]: ").append(entry.getKey()).append(" ===\n");
                            List<TaskAbilityInfo> abilities = entry.getValue();
                            if (abilities == null || abilities.isEmpty()) {
                                sb.append("  该分类下无能力\n\n");
                            } else {
                                for (int i = 0; i < abilities.size(); i++) {
                                    TaskAbilityInfo ability = abilities.get(i);
                                    sb.append("  [").append(i + 1).append("]\n");
                                    sb.append("    ID: ").append(ability.getId()).append("\n");
                                    sb.append("    ShowLabel: ").append(ability.getShowLabel()).append("\n");
                                    sb.append("    IsDefault: ").append(ability.isDefault()).append("\n");
                                    sb.append("    Description: ").append(ability.getDescription()).append("\n\n");
                                }
                            }
                        }
                    }
                    outputView.setText(sb.toString());
                });
            } catch (Exception e) {
                mainHandler.post(() ->
                        outputView.setText("[" + getTimestamp() + "] 测试失败\n\n错误: " + e.getMessage()));
            }
        }).start();
    }

    /**
     * 测试 getScheduleTaskList 接口
     */
    private void testGetScheduleTaskList(TextView outputView) {
        if (timerTaskApi == null) {
            outputView.setText("错误: TimerTaskApi 未初始化");
            return;
        }
        outputView.setText("测试中...");
        new Thread(() -> {
            long startTime = System.currentTimeMillis();
            try {
                TimerTaskModel<List<TaskSchedule>> model = timerTaskApi.getScheduleTaskList();
                long duration = System.currentTimeMillis() - startTime;
                mainHandler.post(() -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append("[").append(getTimestamp()).append("] ");
                    if (model == null || !model.isSuccess()) {
                        sb.append("测试失败\n");
                        sb.append("retCode: ").append(model != null ? model.getRetCode() : -1).append("\n");
                        sb.append("retMsg: ").append(model != null ? model.getRetMsg() : "null");
                        outputView.setText(sb.toString());
                        return;
                    }
                    sb.append("测试成功\n");
                    sb.append("响应时间: ").append(duration).append("ms\n\n");
                    List<TaskSchedule> result = model.getData();
                    if (result == null || result.isEmpty()) {
                        sb.append("结果: 定时任务列表为空\n");
                    } else {
                        sb.append("结果: 共 ").append(result.size()).append(" 条定时任务\n\n");
                        for (int i = 0; i < result.size(); i++) {
                            TaskSchedule task = result.get(i);
                            sb.append("[").append(i + 1).append("]\n");
                            sb.append("  TaskId: ").append(task.getTaskId()).append("\n");
                            sb.append("  Enable: ").append(task.isEnable()).append("\n");
                            ScheduleInfo schedule = task.getSchedule();
                            if (schedule != null) {
                                sb.append("  Schedule:\n");
                                sb.append("    Type: ").append(schedule.getType()).append("\n");
                                sb.append("    TriggerTime: ").append(schedule.getTriggerTime()).append("\n");
                                sb.append("    Cron: ").append(schedule.getCron()).append("\n");
                                sb.append("    Description: ").append(schedule.getDescription()).append("\n");
                            }
                            ScheduleAction action = task.getAction();
                            if (action != null) {
                                sb.append("  Action:\n");
                                sb.append("    Type: ").append(action.getType()).append("\n");
                                sb.append("    Title: ").append(action.getTitle()).append("\n");
                                sb.append("    Body: ").append(action.getBody()).append("\n");
                            }
                            sb.append("\n");
                        }
                    }
                    outputView.setText(sb.toString());
                });
            } catch (Exception e) {
                mainHandler.post(() ->
                        outputView.setText("[" + getTimestamp() + "] 测试失败\n\n错误: " + e.getMessage()));
            }
        }).start();
    }

    /**
     * 测试 createScheduleTask 接口
     */
    private void testCreateScheduleTask(TextView outputView) {
        if (timerTaskApi == null) {
            outputView.setText("错误: TimerTaskApi 未初始化");
            return;
        }

        // 获取输入
        EditText etTriggerTime = findViewById(R.id.et_trigger_time);
        EditText etCron = findViewById(R.id.et_cron);
        EditText etScheduleDesc = findViewById(R.id.et_schedule_description);
        EditText etActionTitle = findViewById(R.id.et_action_title);
        EditText etActionBody = findViewById(R.id.et_action_body);

        String scheduleType = selectedScheduleType;
        String triggerTime = etTriggerTime.getText().toString().trim();
        String cron = etCron.getText().toString().trim();
        String scheduleDesc = etScheduleDesc.getText().toString().trim();
        String actionType = selectedActionType;
        String actionTitle = etActionTitle.getText().toString().trim();
        String actionBody = etActionBody.getText().toString().trim();

        outputView.setText("测试中...");
        new Thread(() -> {
            long startTime = System.currentTimeMillis();
            try {
                // 构建 ScheduleInfo
                ScheduleInfo scheduleInfo = new ScheduleInfo();
                scheduleInfo.setType(ScheduleTypeEnum.fromValue(scheduleType));
                scheduleInfo.setTriggerTime(triggerTime);
                scheduleInfo.setCron(cron);
                scheduleInfo.setDescription(scheduleDesc);

                // 构建 ScheduleAction
                ScheduleAction scheduleAction = new ScheduleAction();
                scheduleAction.setType(actionType);
                scheduleAction.setTitle(actionTitle);
                scheduleAction.setBody(actionBody);

                // 构建 CreateScheduleTaskRequest
                CreateScheduleTaskRequest request = new CreateScheduleTaskRequest();
                request.setEnable(true);
                request.setSchedule(scheduleInfo);
                request.setAction(scheduleAction);

                TimerTaskModel<String> model = timerTaskApi.createScheduleTask(request);
                long duration = System.currentTimeMillis() - startTime;

                mainHandler.post(() -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append("[").append(getTimestamp()).append("] ");
                    if (model.isSuccess()) {
                        sb.append("测试成功\n");
                        sb.append("响应时间: ").append(duration).append("ms\n\n");
                        sb.append("taskId: ").append(model.getData());
                    } else {
                        sb.append("测试失败\n");
                        sb.append("retCode: ").append(model.getRetCode()).append("\n");
                        sb.append("retMsg: ").append(model.getRetMsg());
                    }
                    outputView.setText(sb.toString());
                });
            } catch (Exception e) {
                mainHandler.post(() ->
                        outputView.setText("[" + getTimestamp() + "] 测试失败\n\n错误: " + e.getMessage()));
            }
        }).start();
    }

    /**
     * 测试 deleteScheduleTask 接口
     */
    private void testDeleteScheduleTask(TextView outputView) {
        if (timerTaskApi == null) {
            outputView.setText("错误: TimerTaskApi 未初始化");
            return;
        }

        EditText etTaskId = findViewById(R.id.et_delete_task_id);
        String taskId = etTaskId.getText().toString().trim();

        if (taskId.isEmpty()) {
            outputView.setText("错误: taskId 不能为空");
            return;
        }

        outputView.setText("测试中...");
        new Thread(() -> {
            long startTime = System.currentTimeMillis();
            try {
                DeleteScheduleTaskRequest request = new DeleteScheduleTaskRequest(taskId);
                TimerTaskModel<Boolean> model = timerTaskApi.deleteScheduleTask(request);
                long duration = System.currentTimeMillis() - startTime;

                mainHandler.post(() -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append("[").append(getTimestamp()).append("] ");
                    if (model.isSuccess() && Boolean.TRUE.equals(model.getData())) {
                        sb.append("测试成功\n");
                        sb.append("响应时间: ").append(duration).append("ms\n\n");
                        sb.append("taskId: ").append(taskId).append("\n");
                        sb.append("删除结果: 成功");
                    } else {
                        sb.append("测试失败\n");
                        sb.append("retCode: ").append(model.getRetCode()).append("\n");
                        sb.append("retMsg: ").append(model.getRetMsg());
                    }
                    outputView.setText(sb.toString());
                });
            } catch (Exception e) {
                mainHandler.post(() ->
                        outputView.setText("[" + getTimestamp() + "] 测试失败\n\n错误: " + e.getMessage()));
            }
        }).start();
    }

    /**
     * 测试 updateScheduleTask 接口
     */
    private void testUpdateScheduleTask(TextView outputView) {
        if (timerTaskApi == null) {
            outputView.setText("错误: TimerTaskApi 未初始化");
            return;
        }

        // 获取输入
        EditText etTaskId = findViewById(R.id.et_update_task_id);
        EditText etEnable = findViewById(R.id.et_update_enable);
        EditText etScheduleType = findViewById(R.id.et_update_schedule_type);
        EditText etTriggerTime = findViewById(R.id.et_update_trigger_time);
        EditText etCron = findViewById(R.id.et_update_cron);
        EditText etScheduleDesc = findViewById(R.id.et_update_schedule_description);
        EditText etActionType = findViewById(R.id.et_update_action_type);
        EditText etActionTitle = findViewById(R.id.et_update_action_title);
        EditText etActionBody = findViewById(R.id.et_update_action_body);

        String taskId = etTaskId.getText().toString().trim();
        String enableStr = etEnable.getText().toString().trim();
        String scheduleType = etScheduleType.getText().toString().trim();
        String triggerTime = etTriggerTime.getText().toString().trim();
        String cron = etCron.getText().toString().trim();
        String scheduleDesc = etScheduleDesc.getText().toString().trim();
        String actionType = etActionType.getText().toString().trim();
        String actionTitle = etActionTitle.getText().toString().trim();
        String actionBody = etActionBody.getText().toString().trim();

        if (taskId.isEmpty()) {
            outputView.setText("错误: taskId 不能为空");
            return;
        }

        boolean enable = Boolean.parseBoolean(enableStr);

        outputView.setText("测试中...");
        new Thread(() -> {
            long startTime = System.currentTimeMillis();
            try {
                // 构建 ScheduleInfo
                ScheduleInfo scheduleInfo = new ScheduleInfo();
                scheduleInfo.setType(ScheduleTypeEnum.fromValue(scheduleType));
                scheduleInfo.setTriggerTime(triggerTime);
                scheduleInfo.setCron(cron);
                scheduleInfo.setDescription(scheduleDesc);

                // 构建 ScheduleAction
                ScheduleAction scheduleAction = new ScheduleAction();
                scheduleAction.setType(actionType);
                scheduleAction.setTitle(actionTitle);
                scheduleAction.setBody(actionBody);

                // 构建 UpdateScheduleTaskRequest
                UpdateScheduleTaskRequest request = new UpdateScheduleTaskRequest();
                request.setTaskId(taskId);
                request.setEnable(enable);
                request.setSchedule(scheduleInfo);
                request.setAction(scheduleAction);

                TimerTaskModel<Boolean> model = timerTaskApi.updateScheduleTask(request);
                long duration = System.currentTimeMillis() - startTime;

                mainHandler.post(() -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append("[").append(getTimestamp()).append("] ");
                    if (model.isSuccess() && Boolean.TRUE.equals(model.getData())) {
                        sb.append("测试成功\n");
                        sb.append("响应时间: ").append(duration).append("ms\n\n");
                        sb.append("taskId: ").append(taskId).append("\n");
                        sb.append("更新结果: 成功");
                    } else {
                        sb.append("测试失败\n");
                        sb.append("retCode: ").append(model.getRetCode()).append("\n");
                        sb.append("retMsg: ").append(model.getRetMsg());
                    }
                    outputView.setText(sb.toString());
                });
            } catch (Exception e) {
                mainHandler.post(() ->
                        outputView.setText("[" + getTimestamp() + "] 测试失败\n\n错误: " + e.getMessage()));
            }
        }).start();
    }

    private String getTimestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(new Date());
    }
}
