package com.example.testapp.model;

/**
 * 测试步骤模型
 */
public class TestStep {

    private String stepId;
    private String description;
    private String action;
    private Target target;
    private Params params;

    public String getStepId() {
        return stepId;
    }

    public void setStepId(String stepId) {
        this.stepId = stepId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Target getTarget() {
        return target;
    }

    public void setTarget(Target target) {
        this.target = target;
    }

    public Params getParams() {
        return params;
    }

    public void setParams(Params params) {
        this.params = params;
    }

    @Override
    public String toString() {
        return "TestStep{" +
                "stepId='" + stepId + '\'' +
                ", action='" + action + '\'' +
                ", description='" + description + '\'' +
                '}';
    }
}