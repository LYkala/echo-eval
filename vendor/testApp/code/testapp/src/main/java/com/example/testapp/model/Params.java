package com.example.testapp.model;

/**
 * 动作参数
 */
public class Params {

    private String text;
    private String direction;
    private int distance = 0;
    private int duration = 300;
    private int timeout = 5000;
    private int x = 0;
    private int y = 0;
    private int startX = 0;
    private int startY = 0;
    private int endX = 0;
    private int endY = 0;
    private boolean clearFirst = false;
    private String saveName;
    private int maxScrolls = 0;
    private String clarifyMessage;
    /** 语音助手测试注入：广播 action 与显式组件。 */
    private String broadcastAction;
    private String component;

    public String getClarifyMessage() {
        return clarifyMessage;
    }

    public void setClarifyMessage(String clarifyMessage) {
        this.clarifyMessage = clarifyMessage;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public int getDistance() {
        return distance;
    }

    public void setDistance(int distance) {
        this.distance = distance;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public int getStartX() {
        return startX;
    }

    public void setStartX(int startX) {
        this.startX = startX;
    }

    public int getStartY() {
        return startY;
    }

    public void setStartY(int startY) {
        this.startY = startY;
    }

    public int getEndX() {
        return endX;
    }

    public void setEndX(int endX) {
        this.endX = endX;
    }

    public int getEndY() {
        return endY;
    }

    public void setEndY(int endY) {
        this.endY = endY;
    }

    public boolean isClearFirst() {
        return clearFirst;
    }

    public void setClearFirst(boolean clearFirst) {
        this.clearFirst = clearFirst;
    }

    public String getSaveName() {
        return saveName;
    }

    public void setSaveName(String saveName) {
        this.saveName = saveName;
    }

    public int getMaxScrolls() {
        return maxScrolls;
    }

    public void setMaxScrolls(int maxScrolls) {
        this.maxScrolls = maxScrolls;
    }

    public String getBroadcastAction() {
        return broadcastAction;
    }

    public void setBroadcastAction(String broadcastAction) {
        this.broadcastAction = broadcastAction;
    }

    public String getComponent() {
        return component;
    }

    public void setComponent(String component) {
        this.component = component;
    }
}