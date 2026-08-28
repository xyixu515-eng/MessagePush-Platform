package cn.bitoffer.msgcenter.msgpush.channel;

import cn.bitoffer.msgcenter.common.conf.SendMsgConf;
import cn.bitoffer.msgcenter.msgpush.MsgPushService;
import cn.bitoffer.msgcenter.msgpush.base.ChannelMsgBase;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * 飞书渠道推送服务。
 *
 * <p>支持两种接入方式：
 * <ul>
 *   <li>自定义机器人 Webhook：配置 send-msg-conf.lark.webhook-url 后，POST 消息内容到群机器人</li>
 *   <li>应用凭证方式：预留 app-id / app-secret 字段，可扩展为通过飞书开放平台发送单聊/群聊消息</li>
 *   <li>未配置 Webhook：自动降级为模拟发送，日志输出结果，避免启动失败</li>
 * </ul>
 */
@Service
@Slf4j
public class LarkServiceImpl implements MsgPushService {

    @Autowired
    private SendMsgConf sendMsgConf;

    @Override
    public void pushMsg(ChannelMsgBase msgBase) {
        String webhook = sendMsgConf.getLarkWebhookUrl();

        // 未配置 Webhook：降级为模拟发送
        if (StringUtils.isEmpty(webhook)) {
            if (sendMsgConf.isLarkMockEnabled()) {
                log.info("[LARK-MOCK] 飞书消息（模拟发送） to={}, content={}", msgBase.getTo(), msgBase.getContent());
                return;
            }
            throw new RuntimeException("Lark webhook not configured");
        }

        // 自定义机器人 Webhook 方式发送
        try {
            String payload = "{\"msg_type\":\"text\",\"content\":{\"text\":"
                    + toJsonString(msgBase.getSubject() + "\n" + msgBase.getContent())
                    + "}}";
            HttpURLConnection conn = (HttpURLConnection) new URL(webhook).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.getBytes(StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            if (code >= 200 && code < 300) {
                log.info("[LARK] 飞书消息发送成功");
            } else {
                log.warn("[LARK] 飞书消息发送失败, httpCode={}", code);
                throw new RuntimeException("Lark send failed, httpCode=" + code);
            }
        } catch (Exception e) {
            log.error("[LARK] 飞书消息发送异常: {}", e.getMessage());
            throw new RuntimeException("Lark send error", e);
        }
    }

    private String toJsonString(String s) {
        if (s == null) {
            return "\"\"";
        }
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}
