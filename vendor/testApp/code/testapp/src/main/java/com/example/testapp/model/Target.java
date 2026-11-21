package com.example.testapp.model;

/**
 * 元素定位目标
 */
public class Target {

    private String strategy;
    private String value;
    private int index = 0;

    public String getStrategy() {
        return strategy;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    @Override
    public String toString() {
        return "Target{" +
                "strategy='" + strategy + '\'' +
                ", value='" + value + '\'' +
                ", index=" + index +
                '}';
    }
}