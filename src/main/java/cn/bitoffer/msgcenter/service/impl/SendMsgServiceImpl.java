package cn.bitoffer.msgcenter.service.impl;

import cn.bitoffer.msgcenter.common.conf.SendMsgConf;
import cn.bitoffer.msgcenter.enums.MsgStatus;
import cn.bitoffer.msgcenter.enums.PriorityEnum;
import cn.bitoffer.msgcenter.enums.TemplateStatus;
import cn.bitoffer.msgcenter.exception.BusinessException;
import cn.bitoffer.msgcenter.exception.ErrorCode;
import cn.bitoffer.msgcenter.manager.SendMsgManager;
import cn.bitoffer.msgcenter.model.TemplateModel;
import cn.bitoffer.msgcenter.model.dto.SendMsgReq;
import cn.bitoffer.msgcenter.service.TemplateService;
import cn.bitoffer.msgcenter.tools.MsgRecordService;
import cn.bitoffer.msgcenter.tools.RateLimitService;
import cn.bitoffer.msgcenter.service.SendMsgService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SendMsgServiceImpl implements SendMsgService {

    @Autowired
    private TemplateService templateService;

    @Autowired
    SendMsgConf sendMsgConf;

    @Autowired
    SendMsgManager sendMsgManager;

    @Autowired
    RateLimitService rateLimitService;

    @Autowired
    MsgRecordService msgRecordService;


    @Override
    public String SendMsg(SendMsgReq sendMsgReq) {
        // 1.校验发送参数
        if (sendMsgReq == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数不能为空");
        }
        if (StringUtils.isEmpty(sendMsgReq.getTemplateId())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "模板ID不能为空");
        }
        if (StringUtils.isEmpty(sendMsgReq.getTo())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "接收人不能为空");
        }
        if (sendMsgReq.getTemplateData() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "模板参数不能为空");
        }
        if (sendMsgReq.getPriority() == 0) {
            // 未指定优先级时默认低优，避免越权占用高优资源
            sendMsgReq.setPriority(PriorityEnum.PRIORITY_LOW.getPriorty());
        } else if (sendMsgReq.getPriority() < 1 || sendMsgReq.getPriority() > 3) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "优先级取值非法，应为1-3");
        }
        if (sendMsgReq.getSendTimestamp() != null && sendMsgReq.getSendTimestamp() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "定时发送时间非法");
        }
        // 2.查询模板&校验模板状态
        TemplateModel tp = templateService.GetTemplateWithCache(sendMsgReq.getTemplateId());
        if(tp.getStatus() != TemplateStatus.TEMPLATE_STATUS_NORMAL.getStatus()){
            throw new BusinessException(ErrorCode.TEMPLATE_STATUS_ERROR, "模板尚未准备好，检查模板状态");
        }

        //判断是否为定时消息
        boolean isTimerMsg = false;
        if(sendMsgReq.getSendTimestamp() != null){
            isTimerMsg =true;
        }

        // 3.校验发送配额
        boolean allowed = rateLimitService.isRequestAllowed(tp.getSourceId(),tp.getChannel(),isTimerMsg);
        if(!allowed){
            log.warn("请求频繁，限流了，请稍后重试");
            throw new BusinessException(ErrorCode.RateLimit_ERROR,"请求频繁，限流了，请稍后重试");
        }

        // 3.发送到缓冲区 定时｜Mysql 缓冲｜MQ 缓冲
        if(isTimerMsg){
            return sendMsgManager.SendToTimer(sendMsgReq);
        }

        String msgId = null;
        if(sendMsgConf.isMysqlAsMq()){
            // 发送到 Mysql
            msgId = sendMsgManager.SendToMysql(sendMsgReq);
        }else{
            // 发送到 MQ
            msgId = sendMsgManager.SendToMq(sendMsgReq);
        }

        if (!StringUtils.isEmpty(msgId)){
            // 记录消息记录
            msgRecordService.CreateMsgRecord(msgId,sendMsgReq,tp,MsgStatus.Pending);
        }

        return msgId;
    }
}
