/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 会话消息请求类
 *
 * <p>封装发送消息所需的参数，用于 {@link SessionApi#sendMessage(String, SessionRequest)} 方法。</p>
 *
 * <p>使用示例：</p>
 * <pre>
 * SessionRequest request = new SessionRequest.Builder()
 *     .userInputText("你好")
 *     .userIntent(intentList)
 *     .userSpecifiedAgent("agent-001")
 *     .voiceId("voice-123")
 *     .build();
 * sessionApi.sendMessage(sessionId, request);
 * </pre>
 *
 * @since 2026-04-13
 */
public class SessionRequest {

    /**
     * 用户输入文本
     */
    private final String userInputText;

    /**
     * 用户输入对象编码，默认 -1
     */
    private final int userInputObjectCode;

    /**
     * 用户输入对象数据，默认空字符串
     */
    private final Object userInputObjectData;

    /**
     * 用户输入对象原始字符串
     *
     * <p>当 userInputObjectCode 和 userInputObjectData 无法满足特殊格式需求时，
     * 可直接设置原始字符串，优先级高于 code/data 组合。</p>
     */
    private final String userInputObjectRawString;

    /**
     * 用户意图列表，默认空列表
     */
    private final List<String> userIntent;

    /**
     * 用户指定的代理
     */
    private final String userSpecifiedAgent;

    /**
     * 语音ID
     */
    private final String voiceId;

    /**
     * 扩展参数映射
     */
    private final Map<String, String> params;

    /**
     * AISF消息输出个性化配置
     */
    private final OutputPersonality outputPersonality;

    /**
     * 私有构造函数，通过 Builder 创建实例
     *
     * @param builder 构建器
     */
    private SessionRequest(Builder builder) {
        this.userInputText = builder.userInputText;
        this.userInputObjectCode = builder.userInputObjectCode;
        this.userInputObjectData = builder.userInputObjectData;
        this.userInputObjectRawString = builder.userInputObjectRawString;
        this.userIntent = builder.userIntent != null ? builder.userIntent : Collections.emptyList();
        this.userSpecifiedAgent = builder.userSpecifiedAgent;
        this.voiceId = builder.voiceId;
        this.params = builder.params != null ? builder.params : Collections.emptyMap();
        this.outputPersonality = builder.outputPersonality;
    }

    /**
     * 获取用户输入文本
     *
     * @return 用户输入文本，可能为null
     */
    public String getUserInputText() {
        return userInputText;
    }

    /**
     * 获取用户输入对象编码
     *
     * @return 编码值，默认 -1
     */
    public int getUserInputObjectCode() {
        return userInputObjectCode;
    }

    /**
     * 获取用户输入对象数据
     *
     * @return 数据字符串，默认空字符串
     */
    public Object getUserInputObjectData() {
        return userInputObjectData;
    }

    /**
     * 获取用户输入对象原始字符串
     *
     * @return 原始字符串，可能为null
     */
    public String getUserInputObjectRawString() {
        return userInputObjectRawString;
    }

    /**
     * 获取用户意图列表
     *
     * @return 用户意图列表，不为null
     */
    public List<String> getUserIntent() {
        return userIntent;
    }

    /**
     * 获取用户指定的代理
     *
     * @return 代理标识，可能为null
     */
    public String getUserSpecifiedAgent() {
        return userSpecifiedAgent;
    }

    /**
     * 获取语音ID
     *
     * @return 语音ID，可能为null
     */
    public String getVoiceId() {
        return voiceId;
    }

    /**
     * 获取扩展参数映射
     *
     * @return 参数映射，不为null
     */
    public Map<String, String> getParams() {
        return params;
    }

    /**
     * 获取AISF消息输出个性化配置
     *
     * @return 个性化配置，可能为null
     */
    public OutputPersonality getOutputPersonality() {
        return outputPersonality;
    }

    /**
     * AISF消息输出个性化配置
     *
     * <p>封装情绪、年龄、性别等个性化参数，用于构造 UserInputObject (code=210000)。</p>
     *
     * @since 2026-07-22
     */
    public static class OutputPersonality {
        private final String emotion;
        private final Integer age;
        private final String gender;

        private OutputPersonality(Builder builder) {
            this.emotion = builder.emotion;
            this.age = builder.age;
            this.gender = builder.gender;
        }

        public String getEmotion() {
            return emotion;
        }

        public Integer getAge() {
            return age;
        }

        public String getGender() {
            return gender;
        }

        public boolean isEmpty() {
            return emotion == null && age == null && gender == null;
        }

        public static Builder newBuilder() {
            return new Builder();
        }

        public static class Builder {
            private String emotion;
            private Integer age;
            private String gender;

            public Builder emotion(String emotion) {
                this.emotion = emotion;
                return this;
            }

            public Builder age(Integer age) {
                this.age = age;
                return this;
            }

            public Builder gender(String gender) {
                this.gender = gender;
                return this;
            }

            public OutputPersonality build() {
                return new OutputPersonality(this);
            }
        }
    }

    /**
     * 创建新的构建器
     *
     * @return 构建器实例
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * SessionRequest 构建器
     *
     * <p>使用建造者模式构建 SessionRequest 对象。</p>
     */
    public static class Builder {
        private String userInputText;
        private int userInputObjectCode = -1;
        private Object userInputObjectData = null;
        private String userInputObjectRawString;
        private List<String> userIntent;
        private String userSpecifiedAgent;
        private String voiceId;
        private Map<String, String> params;
        private OutputPersonality outputPersonality;

        /**
         * 设置用户输入文本
         *
         * @param userInputText 用户输入文本
         * @return 构建器实例
         */
        public Builder userInputText(String userInputText) {
            this.userInputText = userInputText;
            return this;
        }

        /**
         * 设置用户输入对象编码
         *
         * @param code 编码值
         * @return 构建器实例
         */
        public Builder userInputObjectCode(int code) {
            this.userInputObjectCode = code;
            return this;
        }

        /**
         * 设置用户输入对象数据
         *
         * @param data 数据字符串
         * @return 构建器实例
         */
        public Builder userInputObjectData(Object data) {
            this.userInputObjectData = data;
            return this;
        }

        /**
         * 设置用户输入对象原始字符串
         *
         * <p>当 code/data 组合无法满足特殊格式需求时使用，
         * 优先级高于 userInputObjectCode/userInputObjectData。</p>
         *
         * @param rawString 原始字符串
         * @return 构建器实例
         */
        public Builder userInputObjectRawString(String rawString) {
            this.userInputObjectRawString = rawString;
            return this;
        }

        /**
         * 设置用户意图列表（替换）
         *
         * @param userIntent 用户意图列表
         * @return 构建器实例
         */
        public Builder userIntent(List<String> userIntent) {
            this.userIntent = userIntent;
            return this;
        }

        /**
         * 添加用户意图（支持多次调用，自动去重）
         *
         * @param userIntent 用户意图
         * @return 构建器实例
         */
        public Builder userIntent(String userIntent) {
            if (userIntent != null && !userIntent.isEmpty()) {
                if (this.userIntent == null) {
                    this.userIntent = new ArrayList<>();
                }
                if (!this.userIntent.contains(userIntent)) {
                    this.userIntent.add(userIntent);
                }
            }
            return this;
        }

        /**
         * 设置用户指定的代理
         *
         * @param userSpecifiedAgent 代理标识
         * @return 构建器实例
         */
        public Builder userSpecifiedAgent(String userSpecifiedAgent) {
            this.userSpecifiedAgent = userSpecifiedAgent;
            return this;
        }

        /**
         * 设置语音ID
         *
         * @param voiceId 语音ID
         * @return 构建器实例
         */
        public Builder voiceId(String voiceId) {
            this.voiceId = voiceId;
            return this;
        }

        /**
         * 设置扩展参数映射
         *
         * @param params 参数映射
         * @return 构建器实例
         */
        public Builder params(Map<String, String> params) {
            this.params = params;
            return this;
        }

        /**
         * 设置AISF消息输出个性化配置
         *
         * @param outputPersonality 个性化配置
         * @return 构建器实例
         */
        public Builder outputPersonality(OutputPersonality outputPersonality) {
            this.outputPersonality = outputPersonality;
            return this;
        }

        /**
         * 构建 SessionRequest 实例
         *
         * @return SessionRequest 实例
         */
        public SessionRequest build() {
            return new SessionRequest(this);
        }
    }
}
