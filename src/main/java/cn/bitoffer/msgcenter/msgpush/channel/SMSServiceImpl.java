package cn.bitoffer.msgcenter.msgpush.channel;

import cn.bitoffer.msgcenter.common.conf.SendMsgConf;
import cn.bitoffer.msgcenter.msgpush.MsgPushService;
import cn.bitoffer.msgcenter.msgpush.base.ChannelMsgBase;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 短信渠道推送服务。
 *
 * <p>容灾设计：
 * <ul>
 *   <li>主渠道 + 备用渠道：默认阿里云为主、腾讯云为备用（可通过配置 send-msg-conf.sms.primary 互换）</li>
 *   <li>熔断切换：同一渠道连续失败达到 failover-threshold 次，自动切换到备用渠道；单次成功即重置失败计数</li>
 *   <li>未配置密钥（本地/演示环境）：自动降级为模拟发送，避免启动失败，日志输出结果</li>
 *   <li>所有渠道都失败：抛出异常，由上层消费逻辑捕获后进入重试队列，不静默丢消息</li>
 * </ul>
 */
@Service
@Slf4j
public class SMSServiceImpl implements MsgPushService {

    @Autowired
    private SendMsgConf sendMsgConf;

    /** 当前主渠道标识：aliyun / tencent */
    private volatile String currentPrimary;
    /** 当前主渠道连续失败次数 */
    private final AtomicInteger primaryFailCount = new AtomicInteger(0);
    /** 当前备用渠道连续失败次数 */
    private final AtomicInteger backupFailCount = new AtomicInteger(0);

    @PostConstruct
    public void init() {
        // 主渠道默认 aliyun，可按配置互换
        String primary = sendMsgConf.getSmsPrimary();
        currentPrimary = "tencent".equalsIgnoreCase(primary) ? "tencent" : "aliyun";
        log.info("[SMS] 短信渠道初始化完成，主渠道={}，备用渠道={}，切换阈值={}，mock={}",
                currentPrimary, getBackup(), sendMsgConf.getSmsFailoverThreshold(), sendMsgConf.isSmsMockEnabled());
    }

    private String getBackup() {
        return "aliyun".equals(currentPrimary) ? "tencent" : "aliyun";
    }

    @Override
    public void pushMsg(ChannelMsgBase msgBase) {
        // 1. 优先使用当前主渠道
        if (trySend(currentPrimary, msgBase)) {
            resetCounters();
            return;
        }

        // 2. 主渠道连续失败达到阈值，切换备用渠道
        int fail = primaryFailCount.incrementAndGet();
        if (fail >= sendMsgConf.getSmsFailoverThreshold()) {
            log.warn("[SMS] 主渠道 {} 连续失败 {} 次，切换备用渠道 {}",
                    currentPrimary, fail, getBackup());
            String old = currentPrimary;
            currentPrimary = getBackup();
            primaryFailCount.set(0);
            backupFailCount.set(0);
            if (trySend(currentPrimary, msgBase)) {
                return;
            }
            log.error("[SMS] 切换后备用渠道 {} 也发送失败（原主渠道 {} 已切换）", currentPrimary, old);
            throw new RuntimeException("SMS all channels failed, primary=" + old + ", backup=" + currentPrimary);
        }
        log.warn("[SMS] 主渠道 {} 发送失败（第 {} 次），暂不切换，等待达到阈值", currentPrimary, fail);
        throw new RuntimeException("SMS primary channel send failed");
    }

    private void resetCounters() {
        primaryFailCount.set(0);
        backupFailCount.set(0);
    }

    /**
     * 尝试用指定渠道发送。返回 true 表示发送成功。
     */
    private boolean trySend(String channel, ChannelMsgBase msgBase) {
        try {
            if ("aliyun".equals(channel)) {
                return sendByAliyun(msgBase);
            }
            return sendByTencent(msgBase);
        } catch (Exception e) {
            log.error("[SMS] 渠道 {} 发送异常: {}", channel, e.getMessage());
            return false;
        }
    }

    /**
     * 阿里云短信（主）。未配置密钥时降级为模拟发送。
     */
    private boolean sendByAliyun(ChannelMsgBase msgBase) throws Exception {
        String accessKeyId = sendMsgConf.getAliyunSmsAccessKeyId();
        String accessKeySecret = sendMsgConf.getAliyunSmsAccessKeySecret();
        String signName = sendMsgConf.getAliyunSmsSignName();
        String templateCode = sendMsgConf.getAliyunSmsTemplateCode();

        if (StringUtils.isEmpty(accessKeyId) || StringUtils.isEmpty(accessKeySecret)) {
            if (sendMsgConf.isSmsMockEnabled()) {
                log.info("[SMS-MOCK] 阿里云短信（模拟发送） to={}, content={}", maskPhone(msgBase.getTo()), msgBase.getContent());
                return true;
            }
            throw new IllegalStateException("阿里云短信未配置 AccessKey");
        }

        com.aliyun.teaopenapi.models.Config config = new com.aliyun.teaopenapi.models.Config()
                .setAccessKeyId(accessKeyId)
                .setAccessKeySecret(accessKeySecret);
        config.endpoint = "dysmsapi.aliyuncs.com";
        com.aliyun.dysmsapi20170525.Client client = new com.aliyun.dysmsapi20170525.Client(config);

        com.aliyun.dysmsapi20170525.models.SendSmsRequest req =
                new com.aliyun.dysmsapi20170525.models.SendSmsRequest()
                        .setPhoneNumbers(msgBase.getTo())
                        .setSignName(signName)
                        .setTemplateCode(templateCode)
                        .setTemplateParam(buildTemplateParam(msgBase));
        com.aliyun.dysmsapi20170525.models.SendSmsResponse resp = client.sendSms(req);
        log.info("[SMS] 阿里云短信发送结果: {}", resp.getBody().getMessage());
        return true;
    }

    /**
     * 腾讯云短信（容灾备用）。未配置密钥时降级为模拟发送。
     */
    private boolean sendByTencent(ChannelMsgBase msgBase) throws Exception {
        String secretId = sendMsgConf.getTencentSmsSecretId();
        String secretKey = sendMsgConf.getTencentSmsSecretKey();

        if (StringUtils.isEmpty(secretId) || StringUtils.isEmpty(secretKey)) {
            if (sendMsgConf.isSmsMockEnabled()) {
                log.info("[SMS-MOCK] 腾讯云短信（模拟发送） to={}, content={}", maskPhone(msgBase.getTo()), msgBase.getContent());
                return true;
            }
            throw new IllegalStateException("腾讯云短信未配置 SecretId");
        }

        com.tencentcloudapi.common.Credential cred =
                new com.tencentcloudapi.common.Credential(secretId, secretKey);
        com.tencentcloudapi.common.profile.HttpProfile httpProfile =
                new com.tencentcloudapi.common.profile.HttpProfile();
        httpProfile.setEndpoint("sms.tencentcloudapi.com");
        com.tencentcloudapi.common.profile.ClientProfile clientProfile =
                new com.tencentcloudapi.common.profile.ClientProfile();
        clientProfile.setHttpProfile(httpProfile);
        com.tencentcloudapi.sms.v20210111.SmsClient client =
                new com.tencentcloudapi.sms.v20210111.SmsClient(cred, "ap-guangzhou", clientProfile);

        com.tencentcloudapi.sms.v20210111.models.SendSmsRequest req =
                new com.tencentcloudapi.sms.v20210111.models.SendSmsRequest();
        req.setPhoneNumberSet(new String[]{msgBase.getTo()});
        req.setSmsSdkAppId(sendMsgConf.getTencentSmsSdkAppId());
        req.setSignName(sendMsgConf.getTencentSmsSignName());
        req.setTemplateId(sendMsgConf.getTencentSmsTemplateId());
        req.setTemplateParamSet(buildTemplateParamValues(msgBase));
        client.SendSms(req);
        log.info("[SMS] 腾讯云短信发送成功");
        return true;
    }

    private String buildTemplateParam(ChannelMsgBase msgBase) {
        // 阿里云模板参数：{"code":"xxx"}，这里把模板数据透传
        if (msgBase.getTemplateData() == null || msgBase.getTemplateData().isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (java.util.Map.Entry<String, String> e : msgBase.getTemplateData().entrySet()) {
            if (!first) {
                sb.append(",");
            }
            sb.append("\"").append(e.getKey()).append("\":\"").append(e.getValue()).append("\"");
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private String[] buildTemplateParamValues(ChannelMsgBase msgBase) {
        if (msgBase.getTemplateData() == null || msgBase.getTemplateData().isEmpty()) {
            return new String[0];
        }
        return msgBase.getTemplateData().values().toArray(new String[0]);
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
