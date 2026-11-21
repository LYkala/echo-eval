package com.example.testapp.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 测试流程模型
 */
public class TestFlow {

    private String version;
    private String testId;
    private String testName;
    private String description;
    /** GUI / QA — QA 时不截图，评判依赖模型回复 */
    private String caseType = "GUI";
    private String targetPackage;
    private String targetActivity;
    private int timeout = 60000;
    private int exitBackCount = 2;  // 退出时按返回键次数，默认2次
    private List<TestStep> steps = new ArrayList<>();

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getTestId() {
        return testId;
    }

    public void setTestId(String testId) {
        this.testId = testId;
    }

    public String getTestName() {
        return testName;
    }

    public void setTestName(String testName) {
        this.testName = testName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCaseType() {
        return caseType;
    }

    public void setCaseType(String caseType) {
        this.caseType = caseType;
    }

    public String getTargetPackage() {
        return targetPackage;
    }

    public void setTargetPackage(String targetPackage) {
        this.targetPackage = targetPackage;
    }

    public String getTargetActivity() {
        return targetActivity;
    }

    public void setTargetActivity(String targetActivity) {
        this.targetActivity = targetActivity;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public int getExitBackCount() {
        return exitBackCount;
    }

    public void setExitBackCount(int exitBackCount) {
        this.exitBackCount = exitBackCount;
    }

    public List<TestStep> getSteps() {
        return steps;
    }

    public void setSteps(List<TestStep> steps) {
        this.steps = steps;
    }

    public void addStep(TestStep step) {
        this.steps.add(step);
    }
}