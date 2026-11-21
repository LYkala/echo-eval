package com.example.devicesdkdemo;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import com.huawei.aisfsdkv2.api.AISFSDKClient;
import com.huawei.aisfsdkv2.api.memory.MemCategoryEnum;
import com.huawei.aisfsdkv2.api.memory.MemoryApi;
import com.huawei.aisfsdkv2.api.memory.MemoryInfo;
import com.huawei.aisfsdkv2.api.memory.MemoryListResult;
import com.huawei.aisfsdkv2.api.memory.MemoryPageInfo;
import com.huawei.aisfsdkv2.api.memory.PersonalClassifyMemoryInfo;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 记忆接口测试页面 - 折叠式设计
 */
public class MemoryTestActivity extends AppCompatActivity {

    private MemoryApi memoryApi;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    // 折叠项视图
    private LinearLayout contentGetMemoryList;
    private TextView titleGetMemoryList;
    private LinearLayout contentGetMemoryListWithPage;
    private TextView titleGetMemoryListWithPage;
    private LinearLayout contentGetClassifyMemory;
    private TextView titleGetClassifyMemory;
    private LinearLayout contentIsMemoryEnabled;
    private TextView titleIsMemoryEnabled;
    private LinearLayout contentSetMemoryEnabled;
    private TextView titleSetMemoryEnabled;
    private LinearLayout contentDeleteMemory;
    private TextView titleDeleteMemory;

    // 当前展开的项
    private LinearLayout currentExpandedContent = null;
    private TextView currentExpandedTitle = null;
    private String currentExpandedText = "";

    // 分类下拉列表选项
    private static final String[] CATEGORY_OPTIONS = {"ALL", "COMMON", "CORE", "SEMANTIC", "EPISODIC", "USER_INPUT"};
    private static final MemCategoryEnum[] CATEGORY_VALUES = {
            MemCategoryEnum.ALL,
            MemCategoryEnum.COMMON,
            MemCategoryEnum.CORE,
            MemCategoryEnum.SEMANTIC,
            MemCategoryEnum.EPISODIC,
            MemCategoryEnum.USER_INPUT
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_memory_test);

        initMemoryApi();
        initViews();
    }

    private void initMemoryApi() {
        try {
            memoryApi = AISFSDKClient.getMemoryApi();
        } catch (Exception e) {
            // 初始化失败
        }
    }

    private void initViews() {
        // 返回按钮
        Button btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        // 折叠项1: getMemoryList
        titleGetMemoryList = findViewById(R.id.title_get_memory_list);
        contentGetMemoryList = findViewById(R.id.content_get_memory_list);

        android.widget.Spinner spinnerCategory = findViewById(R.id.spinner_category);
        Button btnTest = findViewById(R.id.btn_test_get_memory_list);
        TextView tvOutput = findViewById(R.id.tv_output_get_memory_list);

        // 设置分类下拉列表
        android.widget.ArrayAdapter<String> categoryAdapter = new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, CATEGORY_OPTIONS);
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategory.setAdapter(categoryAdapter);

        titleGetMemoryList.setOnClickListener(v -> toggleItem(
                contentGetMemoryList, titleGetMemoryList, "测试 getMemoryList"));

        btnTest.setOnClickListener(v -> {
            int selectedPosition = spinnerCategory.getSelectedItemPosition();
            MemCategoryEnum category = CATEGORY_VALUES[selectedPosition];
            testGetMemoryList(category, tvOutput);
        });

        // 折叠项2: getMemoryListWithPage
        titleGetMemoryListWithPage = findViewById(R.id.title_get_memory_list_with_page);
        contentGetMemoryListWithPage = findViewById(R.id.content_get_memory_list_with_page);

        android.widget.Spinner spinnerCategoryPage = findViewById(R.id.spinner_category_page);
        android.widget.EditText etPage = findViewById(R.id.et_page);
        android.widget.EditText etPageSize = findViewById(R.id.et_page_size);
        Button btnTestWithPage = findViewById(R.id.btn_test_get_memory_list_with_page);
        TextView tvOutputWithPage = findViewById(R.id.tv_output_get_memory_list_with_page);

        // 设置分类下拉列表
        android.widget.ArrayAdapter<String> categoryAdapterPage = new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, CATEGORY_OPTIONS);
        categoryAdapterPage.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategoryPage.setAdapter(categoryAdapterPage);

        titleGetMemoryListWithPage.setOnClickListener(v -> toggleItem(
                contentGetMemoryListWithPage, titleGetMemoryListWithPage, "测试 getMemoryListWithPage"));

        btnTestWithPage.setOnClickListener(v -> {
            int selectedPosition = spinnerCategoryPage.getSelectedItemPosition();
            MemCategoryEnum category = CATEGORY_VALUES[selectedPosition];
            int page = parseIntOrDefault(etPage.getText().toString(), 1);
            int pageSize = parseIntOrDefault(etPageSize.getText().toString(), 10);
            testGetMemoryListWithPage(category, page, pageSize, tvOutputWithPage);
        });

        // 折叠项3: getPersonalClassifyMemory
        titleGetClassifyMemory = findViewById(R.id.title_get_classify_memory);
        contentGetClassifyMemory = findViewById(R.id.content_get_classify_memory);
        Button btnTestClassify = findViewById(R.id.btn_test_get_classify_memory);
        TextView tvOutputClassify = findViewById(R.id.tv_output_get_classify_memory);
        titleGetClassifyMemory.setOnClickListener(v -> toggleItem(
                contentGetClassifyMemory, titleGetClassifyMemory, "测试 getPersonalClassifyMemory"));

        btnTestClassify.setOnClickListener(v -> testGetClassifyMemory(tvOutputClassify));

        // 折叠项4: isMemoryEnabled
        titleIsMemoryEnabled = findViewById(R.id.title_is_memory_enabled);
        contentIsMemoryEnabled = findViewById(R.id.content_is_memory_enabled);
        Button btnTestIsEnabled = findViewById(R.id.btn_test_is_memory_enabled);
        TextView tvOutputIsEnabled = findViewById(R.id.tv_output_is_memory_enabled);

        titleIsMemoryEnabled.setOnClickListener(v -> toggleItem(
                contentIsMemoryEnabled, titleIsMemoryEnabled, "测试 isMemoryEnabled"));
        btnTestIsEnabled.setOnClickListener(v -> testIsMemoryEnabled(tvOutputIsEnabled));

        // 折叠项5: setMemoryEnabled
        titleSetMemoryEnabled = findViewById(R.id.title_set_memory_enabled);
        contentSetMemoryEnabled = findViewById(R.id.content_set_memory_enabled);

        android.widget.Spinner spinnerEnabled = findViewById(R.id.spinner_memory_enabled);
        Button btnTestSetEnabled = findViewById(R.id.btn_test_set_memory_enabled);
        TextView tvOutputSetEnabled = findViewById(R.id.tv_output_set_memory_enabled);

        android.widget.ArrayAdapter<String> enabledAdapter = new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, new String[]{"true", "false"});
        enabledAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerEnabled.setAdapter(enabledAdapter);

        titleSetMemoryEnabled.setOnClickListener(v -> toggleItem(
                contentSetMemoryEnabled, titleSetMemoryEnabled, "测试 setMemoryEnabled"));
        btnTestSetEnabled.setOnClickListener(v -> {
            boolean enabled = spinnerEnabled.getSelectedItemPosition() == 0;
            testSetMemoryEnabled(enabled, tvOutputSetEnabled);
        });

        // 折叠项6: deleteMemory
        titleDeleteMemory = findViewById(R.id.title_delete_memory);
        contentDeleteMemory = findViewById(R.id.content_delete_memory);

        android.widget.Spinner spinnerCategoryDelete = findViewById(R.id.spinner_category_delete);
        android.widget.EditText etMemoryId = findViewById(R.id.et_memory_id);
        Button btnTestDelete = findViewById(R.id.btn_test_delete_memory);
        TextView tvOutputDelete = findViewById(R.id.tv_output_delete_memory);

        android.widget.ArrayAdapter<String> categoryAdapterDelete = new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, CATEGORY_OPTIONS);
        categoryAdapterDelete.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategoryDelete.setAdapter(categoryAdapterDelete);

        titleDeleteMemory.setOnClickListener(v -> toggleItem(
                contentDeleteMemory, titleDeleteMemory, "测试 deleteMemory"));
        btnTestDelete.setOnClickListener(v -> {
            int selectedPosition = spinnerCategoryDelete.getSelectedItemPosition();
            MemCategoryEnum category = CATEGORY_VALUES[selectedPosition];
            String memoryId = etMemoryId.getText().toString().trim();
            testDeleteMemory(category, memoryId, tvOutputDelete);
        });
    }

    private int parseIntOrDefault(String str, int defaultValue) {
        try {
            return Integer.parseInt(str.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * 切换折叠项展开/折叠状态
     */
    private void toggleItem(LinearLayout contentView, TextView titleView, String title) {
        if (contentView.getVisibility() == View.VISIBLE) {
            // 当前已展开，折叠它
            contentView.setVisibility(View.GONE);
            titleView.setText("▶ " + title);
            currentExpandedContent = null;
            currentExpandedTitle = null;
            currentExpandedText = "";
        } else {
            // 先折叠其他项
            if (currentExpandedContent != null) {
                currentExpandedContent.setVisibility(View.GONE);
                currentExpandedTitle.setText("▶ " + currentExpandedText);
            }
            // 展开当前项
            contentView.setVisibility(View.VISIBLE);
            titleView.setText("▼ " + title);
            currentExpandedContent = contentView;
            currentExpandedTitle = titleView;
            currentExpandedText = title;
        }
    }

    /**
     * 测试 getMemoryList 接口
     */
    private void testGetMemoryList(MemCategoryEnum category, TextView outputView) {
        if (memoryApi == null) {
            outputView.setText("错误: MemoryApi 未初始化");
            return;
        }

        outputView.setText("测试中...");

        final MemCategoryEnum finalCategory = category;
        new Thread(() -> {
            long startTime = System.currentTimeMillis();
            try {
                List<MemoryInfo> result = memoryApi.getMemoryList(finalCategory);
                long duration = System.currentTimeMillis() - startTime;
                mainHandler.post(() -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append("[").append(getTimestamp()).append("] 测试成功\n");
                    sb.append("响应时间: ").append(duration).append("ms\n\n");

                    if (result == null || result.isEmpty()) {
                        sb.append("结果: 记忆列表为空\n");
                    } else {
                        sb.append("结果: 共 ").append(result.size()).append(" 条记忆\n\n");
                        for (int i = 0; i < result.size(); i++) {
                            MemoryInfo info = result.get(i);
                            sb.append("[").append(i + 1).append("]\n");
                            sb.append("  ID: ").append(info.getId()).append("\n");
                            sb.append("  Content: ").append(info.getContent()).append("\n");
                            sb.append("  Detail: ").append(info.getDetail()).append("\n");
                            sb.append("  Date: ").append(info.getDate()).append("\n");
                            sb.append("  Type: ").append(info.getMemType()).append("\n\n");
                        }
                    }
                    outputView.setText(sb.toString());
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    outputView.setText("[" + getTimestamp() + "] 测试失败\n\n错误: " + e.getMessage());
                });
            }
        }).start();
    }

    private String getTimestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(new Date());
    }

    /**
     * 测试 getMemoryListWithPage 接口
     */
    private void testGetMemoryListWithPage(MemCategoryEnum category, int page, int pageSize, TextView outputView) {
        if (memoryApi == null) {
            outputView.setText("错误: MemoryApi 未初始化");
            return;
        }

        outputView.setText("测试中...");

        final MemCategoryEnum finalCategory = category;
        final int finalPage = page;
        final int finalPageSize = pageSize;
        new Thread(() -> {
            long startTime = System.currentTimeMillis();
            try {
                MemoryListResult result = memoryApi.getMemoryListWithPage(finalCategory, finalPage, finalPageSize);
                long duration = System.currentTimeMillis() - startTime;
                mainHandler.post(() -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append("[").append(getTimestamp()).append("] 测试成功\n");
                    sb.append("响应时间: ").append(duration).append("ms\n\n");

                    if (result == null) {
                        sb.append("结果: 返回为空\n");
                    } else {
                        sb.append("=== 分页信息 ===\n");
                        MemoryPageInfo pageInfo = result.getPageInfo();
                        if (pageInfo != null) {
                            sb.append("当前页: ").append(pageInfo.getPage()).append("\n");
                            sb.append("每页数量: ").append(pageInfo.getLimit()).append("\n");
                            sb.append("总记录数: ").append(pageInfo.getTotalSize()).append("\n");
                            sb.append("是否有下一页: ").append(pageInfo.getHasNext()).append("\n\n");
                        }

                        List<MemoryInfo> memoryList = result.getMemoryList();
                        if (memoryList == null || memoryList.isEmpty()) {
                            sb.append("=== 记忆列表 ===\n");
                            sb.append("结果: 记忆列表为空\n");
                        } else {
                            sb.append("=== 记忆列表 (共 ").append(memoryList.size()).append(" 条) ===\n\n");
                            for (int i = 0; i < memoryList.size(); i++) {
                                MemoryInfo info = memoryList.get(i);
                                sb.append("[").append(i + 1).append("]\n");
                                sb.append("  ID: ").append(info.getId()).append("\n");
                                sb.append("  Content: ").append(info.getContent()).append("\n");
                                sb.append("  Detail: ").append(info.getDetail()).append("\n");
                                sb.append("  Date: ").append(info.getDate()).append("\n");
                                sb.append("  Type: ").append(info.getMemType()).append("\n\n");
                            }
                        }
                    }
                    outputView.setText(sb.toString());
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    outputView.setText("[" + getTimestamp() + "] 测试失败\n\n错误: " + e.getMessage());
                });
            }
        }).start();
    }

    /**
     * 测试 getPersonalClassifyMemory 接口
     */
    private void testGetClassifyMemory(TextView outputView) {
        if (memoryApi == null) {
            outputView.setText("错误: MemoryApi 未初始化");
            return;
        }
        outputView.setText("测试中...");
        new Thread(() -> {
            long startTime = System.currentTimeMillis();
            try {
                Map<String, List<PersonalClassifyMemoryInfo>> result = memoryApi.getPersonalClassifyMemory();
                long duration = System.currentTimeMillis() - startTime;
                mainHandler.post(() -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append("[").append(getTimestamp()).append("] 测试成功\n");
                    sb.append("响应时间: ").append(duration).append("ms\n\n");
                    if (result == null || result.isEmpty()) {
                        sb.append("结果: 分类记忆为空\n");
                    } else {
                        sb.append("结果: 共 ").append(result.size()).append(" 个分类\n\n");
                        int categoryIndex = 1;
                        for (Map.Entry<String, List<PersonalClassifyMemoryInfo>> entry : result.entrySet()) {
                            String categoryName = entry.getKey();
                            List<PersonalClassifyMemoryInfo> memoryList = entry.getValue();
                            sb.append("=== 分类 [").append(categoryIndex++).append("]: ").append(categoryName).append(" ===\n");
                            if (memoryList == null || memoryList.isEmpty()) {
                                sb.append("  该分类下无记忆\n\n");
                            } else {
                                sb.append("  记忆数量: ").append(memoryList.size()).append("\n");
                                for (int i = 0; i < memoryList.size(); i++) {
                                    PersonalClassifyMemoryInfo info = memoryList.get(i);
                                    sb.append("  [").append(i + 1).append("]\n");
                                    sb.append("    ID: ").append(info.getId()).append("\n");
                                    sb.append("    Topic: ").append(info.getTopic()).append("\n");
                                    sb.append("    Content: ").append(info.getContent()).append("\n");
                                    sb.append("    MemType: ").append(info.getMemType()).append("\n\n");
                                }
                            }
                        }
                    }
                    outputView.setText(sb.toString());
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    outputView.setText("[" + getTimestamp() + "] 测试失败\n\n错误: " + e.getMessage());
                });
            }
        }).start();
    }

    /**
     * 测试 isMemoryEnabled 接口
     */
    private void testIsMemoryEnabled(TextView outputView) {
        if (memoryApi == null) {
            outputView.setText("错误: MemoryApi 未初始化");
            return;
        }
        outputView.setText("测试中...");
        new Thread(() -> {
            long startTime = System.currentTimeMillis();
            try {
                boolean enabled = memoryApi.isMemoryEnabled();
                long duration = System.currentTimeMillis() - startTime;
                mainHandler.post(() -> {
                    String result = "[" + getTimestamp() + "] 测试成功\n"
                            + "响应时间: " + duration + "ms\n\n"
                            + "isMemoryEnabled = " + enabled;
                    outputView.setText(result);
                });
            } catch (Exception e) {
                mainHandler.post(() ->
                        outputView.setText("[" + getTimestamp() + "] 测试失败\n\n错误: " + e.getMessage()));
            }
        }).start();
    }

    /**
     * 测试 setMemoryEnabled 接口
     */
    private void testSetMemoryEnabled(boolean enabled, TextView outputView) {
        if (memoryApi == null) {
            outputView.setText("错误: MemoryApi 未初始化");
            return;
        }
        outputView.setText("测试中...");
        final boolean finalEnabled = enabled;
        new Thread(() -> {
            long startTime = System.currentTimeMillis();
            try {
                memoryApi.setMemoryEnabled(finalEnabled);
                long duration = System.currentTimeMillis() - startTime;
                mainHandler.post(() -> {
                    String result = "[" + getTimestamp() + "] 测试成功\n"
                            + "响应时间: " + duration + "ms\n\n"
                            + "setMemoryEnabled(" + finalEnabled + ") 调用完成";
                    outputView.setText(result);
                });
            } catch (Exception e) {
                mainHandler.post(() ->
                        outputView.setText("[" + getTimestamp() + "] 测试失败\n\n错误: " + e.getMessage()));
            }
        }).start();
    }

    /**
     * 测试 deleteMemory 接口
     */
    private void testDeleteMemory(MemCategoryEnum category, String memoryId, TextView outputView) {
        if (memoryApi == null) {
            outputView.setText("错误: MemoryApi 未初始化");
            return;
        }
        if (memoryId.isEmpty()) {
            outputView.setText("错误: memoryId 不能为空");
            return;
        }
        outputView.setText("测试中...");
        final MemCategoryEnum finalCategory = category;
        final String finalMemoryId = memoryId;
        new Thread(() -> {
            long startTime = System.currentTimeMillis();
            try {
                boolean success = memoryApi.deleteMemory(finalCategory, finalMemoryId);
                long duration = System.currentTimeMillis() - startTime;
                mainHandler.post(() -> {
                    String result = "[" + getTimestamp() + "] 测试成功\n"
                            + "响应时间: " + duration + "ms\n\n"
                            + "category = " + finalCategory + "\n"
                            + "memoryId = " + finalMemoryId + "\n"
                            + "删除结果 = " + success;
                    outputView.setText(result);
                });
            } catch (Exception e) {
                mainHandler.post(() ->
                        outputView.setText("[" + getTimestamp() + "] 测试失败\n\n错误: " + e.getMessage()));
            }
        }).start();
    }
}

