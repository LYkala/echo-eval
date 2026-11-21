/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.model;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 会话消息头部信息类
 *
 * <p>继承 HeaderMsgInfo，添加会话特有的 seqNum 和 teamEnvironmentId 字段。</p>
 */
public class SessionHeaderMsgInfo extends HeaderMsgInfo {
    private String seqNum;
    private String teamEnvironmentId;
    public String getSeqNum() {
        return seqNum;
    }
    public void setSeqNum(String seqNum) {
        this.seqNum = seqNum;
    }
    public String getTeamEnvironmentId() {
        return teamEnvironmentId;
    }
    public void setTeamEnvironmentId(String teamEnvironmentId) {
        this.teamEnvironmentId = teamEnvironmentId;
    }
    /**
     * 转换为 JSON 对象
     *
     * <p>包含父类的14个标准字段，以及 seqNum 和 teamEnvironmentId。</p>
     */
    @Override
    public JSONObject toJson() throws JSONException {
        JSONObject json = super.toJson();
        json.put("SeqNum", seqNum != null ? seqNum : "");
        json.put("TeamEnvironmentID", teamEnvironmentId != null ? teamEnvironmentId : "");
        return json;
    }
}
