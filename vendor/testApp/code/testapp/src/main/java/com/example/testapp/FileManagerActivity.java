package com.example.testapp;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.example.testapp.fragment.LogsFragment;
import com.example.testapp.fragment.ScreenshotsFragment;
import com.example.testapp.fragment.TestCasesFragment;
import com.google.android.material.tabs.TabLayout;

/**
 * 文件管理界面
 */
public class FileManagerActivity extends AppCompatActivity {

    private TabLayout tabLayout;
    private Fragment currentFragment;
    private TestCasesFragment testCasesFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_manager);

        // 设置Toolbar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("文件管理");
        }

        tabLayout = findViewById(R.id.tabLayout);

        // 预先创建Fragment实例
        testCasesFragment = new TestCasesFragment();

        // 设置默认显示测试用例
        showFragment(testCasesFragment);

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                switch (tab.getPosition()) {
                    case 0:
                        showFragment(testCasesFragment);
                        break;
                    case 1:
                        showFragment(new ScreenshotsFragment());
                        break;
                    case 2:
                        showFragment(new LogsFragment());
                        break;
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
    }

    private void showFragment(Fragment fragment) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.container, fragment);
        transaction.commit();
        currentFragment = fragment;
    }

    /**
     * 刷新测试用例列表
     */
    public void refreshTestCases() {
        if (testCasesFragment != null) {
            testCasesFragment.loadTestCases();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_file_manager, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        } else if (item.getItemId() == R.id.action_add) {
            if (testCasesFragment != null) {
                testCasesFragment.createNewTestCase();
            }
            return true;
        } else if (item.getItemId() == R.id.action_refresh) {
            if (currentFragment instanceof TestCasesFragment) {
                ((TestCasesFragment) currentFragment).loadTestCases();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}