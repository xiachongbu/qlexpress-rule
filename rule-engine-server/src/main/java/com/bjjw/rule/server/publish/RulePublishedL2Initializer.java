package com.bjjw.rule.server.publish;

import com.bjjw.rule.model.constant.RuleCompIds;
import com.bjjw.rule.model.dto.RulePushMessage;
import com.bjjw.rule.model.entity.RulePublished;
import com.bjjw.rule.server.mapper.RulePublishedMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * 服务启动时将数据库中所有已上线的 rule_published 行同步到 Redis L2 缓存。
 * 解决直接通过 SQL 脚本写入 rule_published 但未写入 L2 的场景，确保客户端 SDK 在 HTTP 不可达时仍能从 L2 拿到规则。
 */
@Component
public class RulePublishedL2Initializer {

    private static final Logger log = LoggerFactory.getLogger(RulePublishedL2Initializer.class);

    @Resource
    private RulePublishedMapper publishedMapper;

    @Resource
    private RulePublishedL2Service l2Service;

    @EventListener(ApplicationReadyEvent.class)
    public void syncAllPublishedToL2() {
        try {
            List<RulePublished> onlineRules = publishedMapper.selectList(
                    new LambdaQueryWrapper<RulePublished>()
                            .eq(RulePublished::getStatus, 1));

            int count = 0;
            for (RulePublished p : onlineRules) {
                RulePushMessage msg = new RulePushMessage();
                msg.setRuleCode(p.getRuleCode());
                msg.setCompId(RuleCompIds.normalize(p.getCompId()));
                msg.setVersion(p.getVersion());
                msg.setModelType(p.getModelType());
                msg.setCompiledScript(p.getCompiledScript());
                msg.setCompiledType(p.getCompiledType());
                msg.setModelJson(p.getModelJson());
                msg.setProjectCode(p.getProjectCode());
                msg.setPublishTime(System.currentTimeMillis());
                msg.setAction("PUBLISH");
                l2Service.savePublishedSnapshot(msg);
                count++;
            }
            log.info("L2 cache initialized from DB: {} published rules synced to Redis", count);
        } catch (Exception e) {
            log.warn("L2 cache initialization failed (non-fatal, client can still use HTTP sync): {}", e.getMessage());
        }
    }
}
