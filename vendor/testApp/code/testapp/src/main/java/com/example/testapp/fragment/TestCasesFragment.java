package com.example.testapp.fragment;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.testapp.R;
import com.example.testapp.TestCaseViewerActivity;
import com.google.gson.Gson;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 测试用例列表Fragment
 */
public class TestCasesFragment extends Fragment {

    private ListView listView;
    private TextView tvEmpty;
    private File testcasesDir;
    private List<File> testFiles = new ArrayList<>();
    private ArrayAdapter<String> adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_testcases, container, false);

        listView = view.findViewById(R.id.listView);
        tvEmpty = view.findViewById(R.id.tvEmpty);

        // 测试用例目录
        testcasesDir = new File(requireContext().getFilesDir(), "testflows");

        setupListView();
        loadTestCases();

        return view;
    }

    private void setupListView() {
        adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_list_item_1,
                new ArrayList<>());
        listView.setAdapter(adapter);

        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (position < testFiles.size()) {
                File file = testFiles.get(position);
                openTestCase(file);
            }
        });

        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            if (position < testFiles.size()) {
                File file = testFiles.get(position);
                showFileOptionsDialog(file);
                return true;
            }
            return false;
        });
    }

    /**
     * 加载测试用例列表
     */
    public void loadTestCases() {
        testFiles.clear();

        if (testcasesDir.exists() && testcasesDir.isDirectory()) {
            File[] files = testcasesDir.listFiles((dir, name) -> name.endsWith(".json"));
            if (files != null) {
                testFiles.addAll(Arrays.asList(files));
                // 按修改时间排序（最新的在前）
                Collections.sort(testFiles, (f1, f2) ->
                        Long.compare(f2.lastModified(), f1.lastModified()));
            }
        }

        // 更新适配器
        List<String> fileNames = new ArrayList<>();
        for (File file : testFiles) {
            fileNames.add(file.getName());
        }
        adapter.clear();
        adapter.addAll(fileNames);
        adapter.notifyDataSetChanged();

        // 显示空提示
        if (testFiles.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            listView.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            listView.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 打开测试用例查看
     */
    private void openTestCase(File file) {
        Intent intent = new Intent(requireContext(), TestCaseViewerActivity.class);
        intent.putExtra("file_path", file.getAbsolutePath());
        intent.putExtra("file_name", file.getName());
        startActivity(intent);
    }

    /**
     * 显示文件操作对话框
     */
    private void showFileOptionsDialog(File file) {
        String[] options = {"查看", "删除"};

        new AlertDialog.Builder(requireContext())
                .setTitle(file.getName())
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            openTestCase(file);
                            break;
                        case 1:
                            confirmDeleteFile(file);
                            break;
                    }
                })
                .show();
    }

    /**
     * 确认删除文件
     */
    private void confirmDeleteFile(File file) {
        new AlertDialog.Builder(requireContext())
                .setTitle("删除文件")
                .setMessage("确定要删除 \"" + file.getName() + "\" 吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    if (file.delete()) {
                        Toast.makeText(requireContext(), "文件已删除", Toast.LENGTH_SHORT).show();
                        loadTestCases();
                    } else {
                        Toast.makeText(requireContext(), "删除失败", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 创建新的测试文件
     */
    public void createNewTestCase() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_new_testcase, null);
        EditText etFileName = dialogView.findViewById(R.id.etFileName);

        new AlertDialog.Builder(requireContext())
                .setTitle("新建测试用例")
                .setView(dialogView)
                .setPositiveButton("创建", (dialog, which) -> {
                    String fileName = etFileName.getText().toString().trim();
                    if (TextUtils.isEmpty(fileName)) {
                        Toast.makeText(requireContext(), "请输入文件名", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (!fileName.endsWith(".json")) {
                        fileName += ".json";
                    }

                    if (createEmptyTestFile(fileName)) {
                        Toast.makeText(requireContext(), "文件已创建: " + fileName, Toast.LENGTH_SHORT).show();
                        loadTestCases();
                    } else {
                        Toast.makeText(requireContext(), "创建文件失败", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 创建空的测试文件
     */
    private boolean createEmptyTestFile(String fileName) {
        try {
            if (!testcasesDir.exists()) {
                testcasesDir.mkdirs();
            }

            File file = new File(testcasesDir, fileName);

            // 创建一个空的测试用例模板
            String template = "{\n" +
                    "  \"name\": \"测试用例名称\",\n" +
                    "  \"description\": \"测试用例描述\",\n" +
                    "  \"steps\": []\n" +
                    "}";

            try (FileWriter writer = new FileWriter(file)) {
                writer.write(template);
            }

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // 每次显示时刷新列表
        loadTestCases();
    }
}