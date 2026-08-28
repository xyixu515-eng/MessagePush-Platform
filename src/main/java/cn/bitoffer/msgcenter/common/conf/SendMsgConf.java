package cn.bitoffer.msgcenter.common.conf;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SendMsgConf {

    @Value("${send-msg-conf.mysql-as-mq}")
    private boolean mysqlAsMq;

    @Value("${send-msg-conf.open-cache}")
    private boolean openCache;

    @Value("${send-msg-conf.max-retry-count}")
    private int maxRetryCount;

    @Value("${send-msg-conf.email-account}")
    private String emailAccount;

    @Value("${send-msg-conf.email-auth-code}")
    private String emailAuthCode;

    @Value("${send-msg-conf.email-host}")
    private String emailHost;

    @Value("${send-msg-conf.email-port}")
    private String emailPort;

    // ============ 短信渠道配置（阿里云主 + 腾讯云容灾） ============
    // 主渠道：aliyun / tencent
    @Value("${send-msg-conf.sms.primary:aliyun}")
    private String smsPrimary;

    // 未配置密钥时是否降级为模拟发送（mock），便于本地无密钥演示
    @Value("${send-msg-conf.sms.mock-enabled:true}")
    private boolean smsMockEnabled;

    // 连续失败多少次后切换到备用渠道
    @Value("${send-msg-conf.sms.failover-threshold:3}")
    private int smsFailoverThreshold;

    // 阿里云短信
    @Value("${send-msg-conf.sms.aliyun.access-key-id:}")
    private String aliyunSmsAccessKeyId;
    @Value("${send-msg-conf.sms.aliyun.access-key-secret:}")
    private String aliyunSmsAccessKeySecret;
    @Value("${send-msg-conf.sms.aliyun.sign-name:}")
    private String aliyunSmsSignName;
    @Value("${send-msg-conf.sms.aliyun.template-code:}")
    private String aliyunSmsTemplateCode;

    // 腾讯云短信
    @Value("${send-msg-conf.sms.tencent.secret-id:}")
    private String tencentSmsSecretId;
    @Value("${send-msg-conf.sms.tencent.secret-key:}")
    private String tencentSmsSecretKey;
    @Value("${send-msg-conf.sms.tencent.sdk-app-id:}")
    private String tencentSmsSdkAppId;
    @Value("${send-msg-conf.sms.tencent.sign-name:}")
    private String tencentSmsSignName;
    @Value("${send-msg-conf.sms.tencent.template-id:}")
    private String tencentSmsTemplateId;

    // ============ 飞书渠道配置 ============
    @Value("${send-msg-conf.lark.app-id:}")
    private String larkAppId;
    @Value("${send-msg-conf.lark.app-secret:}")
    private String larkAppSecret;
    @Value("${send-msg-conf.lark.webhook-url:}")
    private String larkWebhookUrl;
    @Value("${send-msg-conf.lark.mock-enabled:true}")
    private boolean larkMockEnabled;


    public boolean isMysqlAsMq() {
        return mysqlAsMq;
    }

    public void setMysqlAsMq(boolean mysqlAsMq) {
        this.mysqlAsMq = mysqlAsMq;
    }

    public String getEmailAccount() {
        return emailAccount;
    }

    public void setEmailAccount(String emailAccount) {
        this.emailAccount = emailAccount;
    }

    public String getEmailAuthCode() {
        return emailAuthCode;
    }

    public void setEmailAuthCode(String emailAuthCode) {
        this.emailAuthCode = emailAuthCode;
    }

    public String getEmailHost() {
        return emailHost;
    }

    public void setEmailHost(String emailHost) {
        this.emailHost = emailHost;
    }

    public String getEmailPort() {
        return emailPort;
    }

    public void setEmailPort(String emailPort) {
        this.emailPort = emailPort;
    }

    public boolean isOpenCache() {
        return openCache;
    }

    public void setOpenCache(boolean openCache) {
        this.openCache = openCache;
    }

    public int getMaxRetryCount() {
        return maxRetryCount;
    }

    public void setMaxRetryCount(int maxRetryCount) {
        this.maxRetryCount = maxRetryCount;
    }

    public String getSmsPrimary() { return smsPrimary; }
    public boolean isSmsMockEnabled() { return smsMockEnabled; }
    public int getSmsFailoverThreshold() { return smsFailoverThreshold; }
    public String getAliyunSmsAccessKeyId() { return aliyunSmsAccessKeyId; }
    public String getAliyunSmsAccessKeySecret() { return aliyunSmsAccessKeySecret; }
    public String getAliyunSmsSignName() { return aliyunSmsSignName; }
    public String getAliyunSmsTemplateCode() { return aliyunSmsTemplateCode; }
    public String getTencentSmsSecretId() { return tencentSmsSecretId; }
    public String getTencentSmsSecretKey() { return tencentSmsSecretKey; }
    public String getTencentSmsSdkAppId() { return tencentSmsSdkAppId; }
    public String getTencentSmsSignName() { return tencentSmsSignName; }
    public String getTencentSmsTemplateId() { return tencentSmsTemplateId; }
    public String getLarkAppId() { return larkAppId; }
    public String getLarkAppSecret() { return larkAppSecret; }
    public String getLarkWebhookUrl() { return larkWebhookUrl; }
    public boolean isLarkMockEnabled() { return larkMockEnabled; }
}