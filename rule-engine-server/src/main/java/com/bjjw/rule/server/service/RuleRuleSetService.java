package com.bjjw.rule.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.bjjw.rule.core.util.RuleSetHitPolicies;
import com.bjjw.rule.model.constant.RuleCompIds;
import com.bjjw.rule.model.dto.RuleResult;
import com.bjjw.rule.model.dto.RuleRuleSetExecuteSummary;
import com.bjjw.rule.model.entity.RuleDefinition;
import com.bjjw.rule.model.entity.RuleRuleSet;
import com.bjjw.rule.model.entity.RuleRuleSetMember;
import com.bjjw.rule.server.mapper.RuleDefinitionMapper;
import com.bjjw.rule.server.mapper.RuleRuleSetMapper;
import com.bjjw.rule.server.mapper.RuleRuleSetMemberMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 规则集设计态 CRUD、成员维护、链式试跑
 */
@Service
public class RuleRuleSetService extends ServiceImpl<RuleRuleSetMapper, RuleRuleSet> {

    @Resource
    private RuleRuleSetMemberMapper ruleRuleSetMemberMapper;

    @Resource
    private RuleDefinitionMapper ruleDefinitionMapper;

    @Resource
    private RuleExecuteService ruleExecuteService;

    /**
     * 分页查询规则集
     */
    public IPage<RuleRuleSet> pageList(int pageNum, int pageSize, Long projectId, String keyword) {
        LambdaQueryWrapper<RuleRuleSet> w = new LambdaQueryWrapper<>();
        if (projectId != null) {
            w.eq(RuleRuleSet::getProjectId, projectId);
        }
        if (keyword != null && !keyword.isEmpty()) {
            w.and(x -> x.like(RuleRuleSet::getSetName, keyword).or().like(RuleRuleSet::getSetCode, keyword));
        }
        List<String> allowed = getAllowedCompIds();
        if (allowed != null) {
            w.in(RuleRuleSet::getCompId, allowed);
        }
        w.orderByDesc(RuleRuleSet::getId);
        IPage<RuleRuleSet> page = page(new Page<>(pageNum, pageSize), w);
        fillMemberCount(page.getRecords());
        return page;
    }

    private void fillMemberCount(List<RuleRuleSet> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        for (RuleRuleSet s : records) {
            Long c = ruleRuleSetMemberMapper.selectCount(
                    new LambdaQueryWrapper<RuleRuleSetMember>().eq(RuleRuleSetMember::getSetId, s.getId()));
            s.setMemberCount(c != null ? c.intValue() : 0);
        }
    }

    /**
     * 作用域过滤扩展点：开源版不做登录用户驱动的过滤，返回 null 表示不过滤。
     */
    private List<String> getAllowedCompIds() {
        return null;
    }

    /**
     * 创建前校验 set_code 不与 rule_code 及其它规则集重复
     */
    @Transactional
    public String createWithValidation(RuleRuleSet set) {
        String err = validateSetCodeUnique(set.getSetCode(), null);
        if (err != null) {
            return err;
        }
        if (!RuleSetHitPolicies.isValid(set.getHitPolicy())) {
            return "非法命中策略: " + set.getHitPolicy();
        }
        set.setHitPolicy(RuleSetHitPolicies.normalize(set.getHitPolicy()));
        if (set.getStatus() == null) {
            set.setStatus(0);
        }
        save(set);
        return null;
    }

    /**
     * 更新元数据（可改 set_code，仍须全局唯一）
     */
    public String updateMeta(RuleRuleSet incoming) {
        RuleRuleSet db = getById(incoming.getId());
        if (db == null) {
            return "规则集不存在";
        }
        if (incoming.getSetCode() != null && !incoming.getSetCode().equals(db.getSetCode())) {
            String err = validateSetCodeUnique(incoming.getSetCode(), incoming.getId());
            if (err != null) {
                return err;
            }
            db.setSetCode(incoming.getSetCode());
        }
        if (incoming.getSetName() != null) {
            db.setSetName(incoming.getSetName());
        }
        if (incoming.getDescription() != null) {
            db.setDescription(incoming.getDescription());
        }
        if (incoming.getHitPolicy() != null) {
            if (!RuleSetHitPolicies.isValid(incoming.getHitPolicy())) {
                return "非法命中策略: " + incoming.getHitPolicy();
            }
            db.setHitPolicy(RuleSetHitPolicies.normalize(incoming.getHitPolicy()));
        }
        if (incoming.getReportLog() != null) {
            db.setReportLog(incoming.getReportLog());
        }
        updateById(db);
        return null;
    }

    /**
     * 删除规则集（仅草稿或已下线），并清理成员
     */
    @Transactional
    public String deleteSet(Long id) {
        RuleRuleSet set = getById(id);
        if (set == null) {
            return "规则集不存在";
        }
        if (set.getStatus() != null && set.getStatus() == 1) {
            return "已发布的规则集请先下线再删除";
        }
        ruleRuleSetMemberMapper.delete(new LambdaQueryWrapper<RuleRuleSetMember>()
                .eq(RuleRuleSetMember::getSetId, id));
        removeById(id);
        return null;
    }

    /**
     * 全量替换成员及顺序
     */
    @Transactional
    public String saveMembers(Long setId, List<Long> definitionIdsInOrder) {
        RuleRuleSet set = getById(setId);
        if (set == null) {
            return "规则集不存在";
        }
        if (definitionIdsInOrder == null) {
            definitionIdsInOrder = new ArrayList<>();
        }
        ruleRuleSetMemberMapper.delete(new LambdaQueryWrapper<RuleRuleSetMember>()
                .eq(RuleRuleSetMember::getSetId, setId));
        int order = 0;
        for (Long defId : definitionIdsInOrder) {
            if (defId == null) {
                continue;
            }
            RuleDefinition def = ruleDefinitionMapper.selectById(defId);
            if (def == null) {
                return "规则定义不存在: " + defId;
            }
            if (!set.getProjectId().equals(def.getProjectId())) {
                return "成员必须与规则集同属一个项目: " + def.getRuleCode();
            }
            RuleRuleSetMember m = new RuleRuleSetMember();
            m.setSetId(setId);
            m.setDefinitionId(defId);
            m.setSortOrder(order++);
            ruleRuleSetMemberMapper.insert(m);
        }
        return null;
    }

    /**
     * 成员规则列表（按顺序）
     */
    public List<RuleDefinition> listOrderedMembers(Long setId) {
        List<RuleRuleSetMember> ms = ruleRuleSetMemberMapper.selectList(
                new LambdaQueryWrapper<RuleRuleSetMember>()
                        .eq(RuleRuleSetMember::getSetId, setId)
                        .orderByAsc(RuleRuleSetMember::getSortOrder));
        List<RuleDefinition> out = new ArrayList<>();
        for (RuleRuleSetMember m : ms) {
            RuleDefinition d = ruleDefinitionMapper.selectById(m.getDefinitionId());
            if (d != null) {
                out.add(d);
            }
        }
        return out;
    }

    /**
     * 按设计态成员顺序试跑（使用各成员已编译设计内容）。
     * <p>按规则集命中策略分派：ALL-管道式全部执行；FIRST/UNIQUE-成员为独立候选，
     * 均以原始入参执行，不做上下文累积（避免未命中成员的全 null 输出污染后续候选入参）。</p>
     */
    public RuleRuleSetExecuteSummary executeChain(Long setId, String scopeCompId, Map<String, Object> params, String businessId) {
        RuleRuleSetExecuteSummary summary = new RuleRuleSetExecuteSummary();
        RuleRuleSet set = getById(setId);
        if (set == null) {
            summary.setSuccess(false);
            summary.setErrorMessage("规则集不存在");
            return summary;
        }
        List<RuleDefinition> members = listOrderedMembers(setId);
        if (members.isEmpty()) {
            summary.setSuccess(false);
            summary.setErrorMessage("规则集无成员");
            return summary;
        }
        String scope = RuleCompIds.normalize(scopeCompId);
        String hitPolicy = RuleSetHitPolicies.normalize(set.getHitPolicy());
        if (!RuleSetHitPolicies.ALL.equals(hitPolicy)) {
            return executeCandidates(members, scope, params, businessId, hitPolicy, summary);
        }
        Map<String, Object> ctx = params != null ? new HashMap<>(params) : new HashMap<>();
        for (RuleDefinition def : members) {
            RuleResult step = ruleExecuteService.testExecute(def.getId(), scope, ctx, businessId);
            appendStep(summary, def, step);
            if (!step.isSuccess()) {
                summary.setSuccess(false);
                summary.setErrorMessage("成员执行失败: " + def.getRuleCode() + " — " + step.getErrorMessage());
                return summary;
            }
            mergeContext(ctx, step.getResult());
        }
        summary.setSuccess(true);
        summary.setFinalResult(ctx.get("result") != null ? ctx.get("result") : ctx);
        return summary;
    }

    /**
     * FIRST/UNIQUE：成员作为独立候选逐个以原始入参执行。
     * FIRST 首个命中即返回（后续成员不执行）；无命中时成功返回 null。
     * UNIQUE 全部执行完毕，命中数 != 1 则失败。
     */
    private RuleRuleSetExecuteSummary executeCandidates(List<RuleDefinition> members, String scope,
                                                        Map<String, Object> params, String businessId,
                                                        String hitPolicy, RuleRuleSetExecuteSummary summary) {
        boolean isFirst = RuleSetHitPolicies.FIRST.equals(hitPolicy);
        int hitCount = 0;
        Object hitResult = null;
        String hitRuleCode = null;
        for (RuleDefinition def : members) {
            RuleResult step = ruleExecuteService.testExecute(def.getId(), scope,
                    params != null ? new HashMap<>(params) : new HashMap<>(), businessId);
            appendStep(summary, def, step);
            if (!step.isSuccess()) {
                summary.setSuccess(false);
                summary.setErrorMessage("成员执行失败: " + def.getRuleCode() + " — " + step.getErrorMessage());
                return summary;
            }
            if (RuleSetHitPolicies.isHit(step.getResult())) {
                hitCount++;
                if (hitResult == null) {
                    hitResult = step.getResult();
                    hitRuleCode = def.getRuleCode();
                }
                if (isFirst) {
                    break;
                }
            }
        }
        if (!isFirst && hitCount != 1) {
            summary.setSuccess(false);
            summary.setErrorMessage("唯一命中(UNIQUE)策略校验失败：实际命中 " + hitCount + " 个成员，要求有且仅有 1 个");
            return summary;
        }
        summary.setSuccess(true);
        Map<String, Object> finalOut = new HashMap<>();
        finalOut.put("hitRuleCode", hitRuleCode);
        finalOut.put("result", hitResult);
        summary.setFinalResult(finalOut);
        return summary;
    }

    private static void appendStep(RuleRuleSetExecuteSummary summary, RuleDefinition def, RuleResult step) {
        RuleRuleSetExecuteSummary.RuleRuleSetStepResult sr = new RuleRuleSetExecuteSummary.RuleRuleSetStepResult();
        sr.setRuleCode(def.getRuleCode());
        sr.setSuccess(step.isSuccess());
        sr.setResult(step.getResult());
        sr.setErrorMessage(step.getErrorMessage());
        sr.setExecuteTimeMs(step.getExecuteTimeMs());
        summary.getSteps().add(sr);
    }

    /**
     * 与客户端一致的上下文合并：Map 则 putAll，否则写入 _lastStepResult
     */
    private static void mergeContext(Map<String, Object> ctx, Object stepResult) {
        if (stepResult instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) stepResult;
            ctx.putAll(m);
        } else {
            ctx.put("_lastStepResult", stepResult);
        }
    }

    private String validateSetCodeUnique(String setCode, Long excludeSetId) {
        if (setCode == null || setCode.trim().isEmpty()) {
            return "规则集编码不能为空";
        }
        String code = setCode.trim();
        Long dupDef = ruleDefinitionMapper.selectCount(
                new LambdaQueryWrapper<RuleDefinition>().eq(RuleDefinition::getRuleCode, code));
        if (dupDef != null && dupDef > 0) {
            return "编码与已有规则 rule_code 冲突: " + code;
        }
        LambdaQueryWrapper<RuleRuleSet> w = new LambdaQueryWrapper<RuleRuleSet>().eq(RuleRuleSet::getSetCode, code);
        if (excludeSetId != null) {
            w.ne(RuleRuleSet::getId, excludeSetId);
        }
        Long dupSet = baseMapper.selectCount(w);
        if (dupSet != null && dupSet > 0) {
            return "规则集编码已存在: " + code;
        }
        return null;
    }
}
