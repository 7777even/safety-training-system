package com.sts.paper.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.common.exception.BusinessException;
import com.sts.common.service.BaseServiceImpl;
import com.sts.paper.dto.*;
import com.sts.paper.entity.*;
import com.sts.paper.mapper.*;
import com.sts.paper.vo.*;
import com.sts.question.entity.Question;
import com.sts.question.entity.QuestionVersion;
import com.sts.question.mapper.QuestionMapper;
import com.sts.question.mapper.QuestionVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaperServiceImpl
  extends BaseServiceImpl<PaperMapper, Paper>
  implements PaperService {

  private final PaperMapper paperMapper;
  private final PaperVersionMapper paperVersionMapper;
  private final PaperVersionQuestionMapper paperVersionQuestionMapper;
  private final PaperDrawRuleMapper paperDrawRuleMapper;
  private final QuestionMapper questionMapper;
  private final QuestionVersionMapper questionVersionMapper;

  @Override
  public PageResult<PaperVO> page(PageRequest<PaperQuery> request) {
    PaperQuery query = request.getQuery();
    LambdaQueryWrapper<Paper> wrapper = new LambdaQueryWrapper<>();
    if (query != null) {
      wrapper
        .eq(StringUtils.hasText(query.getPaperType()), Paper::getPaperType, query.getPaperType())
        .eq(query.getPaperCategoryId() != null, Paper::getPaperCategoryId, query.getPaperCategoryId())
        .like(StringUtils.hasText(query.getPaperName()), Paper::getPaperName, query.getPaperName())
        .eq(query.getStatus() != null, Paper::getStatus, query.getStatus());
      // 「有无可用发布版本」在版本表上，用子查询筛选，保证分页总数与条件一致
      if (Boolean.TRUE.equals(query.getHasPublishedVersion())) {
        wrapper.exists("SELECT 1 FROM sts_paper_version v WHERE v.paper_id = sts_paper_paper.paper_id"
                         + " AND v.published_status = 'PUBLISHED'");
      } else if (Boolean.FALSE.equals(query.getHasPublishedVersion())) {
        wrapper.notExists("SELECT 1 FROM sts_paper_version v WHERE v.paper_id = sts_paper_paper.paper_id"
                            + " AND v.published_status = 'PUBLISHED'");
      }
    }
    wrapper.orderByDesc(Paper::getCreateTime);
    Page<Paper> page = paperMapper.selectPage(new Page<>(request.getPageNum(), request.getPageSize()), wrapper);
    return new PageResult<>(page.getRecords().stream().map(this::toPaperVO).toList(), page.getTotal());
  }

  @Override
  public PaperVO get(Long paperId) {
    return toPaperVO(requirePaper(paperId));
  }

  @Override
  @Transactional
  public Long create(PaperCreateRequest request) {
    Paper paper = new Paper();
    paper.setPaperCode(request.getPaperCode().trim());
    paper.setPaperName(request.getPaperName().trim());
    paper.setPaperType(request.getPaperType());
    paper.setPaperCategoryId(request.getPaperCategoryId());
    paper.setCoverFileId(request.getCoverFileId());
    paper.setCurrentVersionNo(0);
    paper.setArchiveStatus("ACTIVE");
    paper.setStatus(request.getStatus());
    try {
      paperMapper.insert(paper);
    } catch (DuplicateKeyException ex) {
      throw new BusinessException(HttpStatus.CONFLICT, 6502, "同机构下试卷编码已存在");
    }
    return paper.getPaperId();
  }

  @Override
  @Transactional
  public void update(Long paperId, PaperUpdateRequest request) {
    Paper paper = requirePaper(paperId);
    paper.setVersion(request.getVersion());
    paper.setPaperName(request.getPaperName().trim());
    paper.setPaperCategoryId(request.getPaperCategoryId());
    paper.setCoverFileId(request.getCoverFileId());
    paper.setStatus(request.getStatus());
    paperMapper.updateById(paper);
  }

  @Override
  @Transactional
  public void delete(Long paperId) {
    this.removeById(requirePaper(paperId).getPaperId());
  }

  @Override
  @Transactional
  public PaperVersionVO compose(Long paperId, PaperComposeRequest request) {
    return buildVersion(paperId, request, "DRAFT", false);
  }

  @Override
  public PaperPreviewVO preview(Long paperId) {
    Paper paper = requirePaper(paperId);
    if (paper.getCurrentVersionNo() == null || paper.getCurrentVersionNo() <= 0) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 6504, "试卷尚无版本可预览");
    }
    PaperVersion version = requirePaperVersion(paperId, paper.getCurrentVersionNo());
    PaperPreviewVO vo = new PaperPreviewVO();
    vo.setPaperId(paperId);
    vo.setPaperType(paper.getPaperType());
    vo.setTotalScore(version.getTotalScore());
    vo.setPassScore(version.getPassScore());
    vo.setQuestionCount(version.getQuestionCount());
    vo.setQuestions(listVersionQuestions(version.getPaperVersionId()));
    return vo;
  }

  @Override
  @Transactional
  public PaperVersionVO publish(Long paperId, PaperComposeRequest request) {
    return buildVersion(paperId, request, "PUBLISHED", true);
  }

  @Override
  public List<PaperVersionSummaryVO> listVersions(Long paperId) {
    requirePaper(paperId);
    return paperVersionMapper.selectList(new LambdaQueryWrapper<PaperVersion>()
                                           .eq(PaperVersion::getPaperId, paperId)
                                           .orderByDesc(PaperVersion::getVersionNo))
      .stream().map(version -> {
        PaperVersionSummaryVO vo = new PaperVersionSummaryVO();
        BeanUtils.copyProperties(version, vo);
        return vo;
      }).toList();
  }

  @Override
  public PaperVersionVO getVersion(Long paperVersionId) {
    PaperVersion version = paperVersionMapper.selectById(paperVersionId);
    if (version == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 6503, "试卷版本不存在");
    }
    return toVersionVO(version);
  }

  private PaperVersionVO buildVersion(Long paperId, PaperComposeRequest request,
                                      String publishedStatus, boolean publish) {
    Paper paper = requirePaper(paperId);
    int nextNo = (paper.getCurrentVersionNo() == null ? 0 : paper.getCurrentVersionNo()) + 1;
    // 随机卷必须落库实际使用的种子，否则抽题结果不可重现（路线图阶段 2「结果可重现」）。
    Long effectiveSeed = "RANDOM".equals(paper.getPaperType())
      ? (request.getRandomSeed() == null ? paper.getPaperId() : request.getRandomSeed())
      : null;
    List<ResolvedQuestion> resolved = resolveQuestions(paper, request, effectiveSeed);
    if (resolved.isEmpty()) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 6505, "组卷结果为空");
    }
    BigDecimal total = resolved.stream().map(ResolvedQuestion::score)
      .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal passScore = total.multiply(request.getPassRatio()).setScale(2, RoundingMode.HALF_UP);

    PaperVersion version = new PaperVersion();
    version.setPaperId(paperId);
    version.setVersionNo(nextNo);
    version.setDurationSeconds(request.getDurationSeconds());
    version.setTotalScore(total);
    version.setPassRatio(request.getPassRatio());
    version.setPassScore(passScore);
    version.setQuestionCount(resolved.size());
    version.setQuestionOrderMode(request.getQuestionOrderMode());
    version.setMultiSelectScoring(request.getMultiSelectScoring());
    version.setDrawSettingMode(request.getDrawSettingMode() == null ? "UNIFIED" : request.getDrawSettingMode());
    version.setRandomSeed(effectiveSeed);
    version.setPublishedStatus(publishedStatus);
    version.setVersionNote(request.getVersionNote());
    if (publish) {
      version.setPublishedBy(StpUtil.getLoginIdAsLong());
      version.setPublishedTime(LocalDateTime.now());
    }
    paperVersionMapper.insert(version);

    if (request.getDrawRules() != null) {
      for (PaperDrawRuleRequest ruleReq : request.getDrawRules()) {
        PaperDrawRule rule = new PaperDrawRule();
        rule.setPaperVersionId(version.getPaperVersionId());
        rule.setQuestionBankId(ruleReq.getQuestionBankId());
        rule.setQuestionType(ruleReq.getQuestionType());
        rule.setDifficulty(ruleReq.getDifficulty() == null ? "ALL" : ruleReq.getDifficulty());
        rule.setKnowledgePointId(ruleReq.getKnowledgePointId() == null ? 0L : ruleReq.getKnowledgePointId());
        rule.setDrawCount(ruleReq.getDrawCount());
        rule.setScorePerQuestion(ruleReq.getScorePerQuestion());
        rule.setBankWeight(ruleReq.getBankWeight());
        rule.setSortOrder(ruleReq.getSortOrder());
        paperDrawRuleMapper.insert(rule);
      }
    }

    int sort = 1;
    for (ResolvedQuestion rq : resolved) {
      PaperVersionQuestion pq = new PaperVersionQuestion();
      pq.setPaperVersionId(version.getPaperVersionId());
      pq.setQuestionId(rq.questionId());
      pq.setQuestionVersionId(rq.questionVersionId());
      pq.setQuestionType(rq.questionType());
      pq.setScore(rq.score());
      pq.setSortOrder(rq.sortOrder() != null ? rq.sortOrder() : sort++);
      pq.setSourceBankId(rq.sourceBankId());
      paperVersionQuestionMapper.insert(pq);
    }

    paper.setCurrentVersionNo(nextNo);
    paperMapper.updateById(paper);
    return toVersionVO(version);
  }

  private List<ResolvedQuestion> resolveQuestions(Paper paper, PaperComposeRequest request, Long effectiveSeed) {
    if ("RANDOM".equals(paper.getPaperType())) {
      if (request.getDrawRules() == null || request.getDrawRules().isEmpty()) {
        throw new BusinessException(HttpStatus.BAD_REQUEST, 6506, "随机试卷必须提供抽题规则");
      }
      Random random = new Random(effectiveSeed);
      List<ResolvedQuestion> result = new ArrayList<>();
      int sort = 1;
      for (PaperDrawRuleRequest rule : request.getDrawRules()) {
        List<Question> pool = questionMapper.selectList(new LambdaQueryWrapper<Question>()
          .eq(Question::getQuestionBankId, rule.getQuestionBankId())
          .eq(Question::getStatus, 1)
          .eq(Question::getArchiveStatus, "ACTIVE"));
        List<ResolvedQuestion> typed = new ArrayList<>();
        for (Question q : pool) {
          QuestionVersion qv = latestPublished(q);
          if (qv == null) {
            continue;
          }
          if (!rule.getQuestionType().equals(qv.getQuestionType())) {
            continue;
          }
          if (!"ALL".equals(rule.getDifficulty()) && !rule.getDifficulty().equals(qv.getDifficulty())) {
            continue;
          }
          typed.add(new ResolvedQuestion(q.getQuestionId(), qv.getQuestionVersionId(),
            qv.getQuestionType(), rule.getScorePerQuestion(), sort++, q.getQuestionBankId()));
        }
        Collections.shuffle(typed, random);
        if (typed.size() < rule.getDrawCount()) {
          throw new BusinessException(HttpStatus.BAD_REQUEST, 6507,
            "题库可抽题目不足: bank=" + rule.getQuestionBankId());
        }
        result.addAll(typed.subList(0, rule.getDrawCount()));
      }
      return result;
    }
    if (request.getQuestions() == null || request.getQuestions().isEmpty()) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 6505, "固定/自定义试卷必须指定题目");
    }
    List<ResolvedQuestion> result = new ArrayList<>();
    for (PaperQuestionItemRequest item : request.getQuestions()) {
      Question q = questionMapper.selectById(item.getQuestionId());
      if (q == null) {
        throw new BusinessException(HttpStatus.NOT_FOUND, 6031, "题目不存在");
      }
      QuestionVersion qv;
      if (item.getQuestionVersionId() != null) {
        qv = questionVersionMapper.selectById(item.getQuestionVersionId());
        if (qv == null || !qv.getQuestionId().equals(q.getQuestionId())) {
          throw new BusinessException(HttpStatus.BAD_REQUEST, 6508, "题目版本无效");
        }
      } else {
        qv = latestPublished(q);
        if (qv == null) {
          throw new BusinessException(HttpStatus.BAD_REQUEST, 6508, "题目尚无已发布版本");
        }
      }
      result.add(new ResolvedQuestion(q.getQuestionId(), qv.getQuestionVersionId(),
        qv.getQuestionType(), item.getScore(), item.getSortOrder(), q.getQuestionBankId()));
    }
    return result;
  }

  private QuestionVersion latestPublished(Question q) {
    return questionVersionMapper.selectOne(new LambdaQueryWrapper<QuestionVersion>()
      .eq(QuestionVersion::getQuestionId, q.getQuestionId())
      .eq(QuestionVersion::getPublishedStatus, "PUBLISHED")
      .orderByDesc(QuestionVersion::getVersionNo)
      .last("LIMIT 1"));
  }

  private Paper requirePaper(Long id) {
    Paper paper = paperMapper.selectById(id);
    if (paper == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 6501, "试卷不存在");
    }
    return paper;
  }

  private PaperVersion requirePaperVersion(Long paperId, Integer versionNo) {
    PaperVersion version = paperVersionMapper.selectOne(new LambdaQueryWrapper<PaperVersion>()
      .eq(PaperVersion::getPaperId, paperId)
      .eq(PaperVersion::getVersionNo, versionNo));
    if (version == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 6503, "试卷版本不存在");
    }
    return version;
  }

  private List<PaperVersionQuestionVO> listVersionQuestions(Long paperVersionId) {
    return paperVersionQuestionMapper.selectList(new LambdaQueryWrapper<PaperVersionQuestion>()
        .eq(PaperVersionQuestion::getPaperVersionId, paperVersionId)
        .orderByAsc(PaperVersionQuestion::getSortOrder))
      .stream().map(pq -> {
        PaperVersionQuestionVO vo = new PaperVersionQuestionVO();
        vo.setPaperQuestionId(pq.getPaperQuestionId());
        vo.setQuestionId(pq.getQuestionId());
        vo.setQuestionVersionId(pq.getQuestionVersionId());
        vo.setQuestionType(pq.getQuestionType());
        vo.setScore(pq.getScore());
        vo.setSortOrder(pq.getSortOrder());
        vo.setSourceBankId(pq.getSourceBankId());
        return vo;
      }).collect(Collectors.toList());
  }

  private PaperVO toPaperVO(Paper paper) {
    PaperVO vo = new PaperVO();
    vo.setPaperId(paper.getPaperId());
    vo.setOrganizationId(paper.getOrganizationId());
    vo.setPaperCode(paper.getPaperCode());
    vo.setPaperName(paper.getPaperName());
    vo.setPaperType(paper.getPaperType());
    vo.setPaperCategoryId(paper.getPaperCategoryId());
    vo.setCoverFileId(paper.getCoverFileId());
    vo.setCurrentVersionNo(paper.getCurrentVersionNo());
    vo.setArchiveStatus(paper.getArchiveStatus());
    vo.setStatus(paper.getStatus());
    vo.setVersion(paper.getVersion());
    vo.setCreateTime(paper.getCreateTime());
    return vo;
  }

  private PaperVersionVO toVersionVO(PaperVersion version) {
    PaperVersionVO vo = new PaperVersionVO();
    vo.setPaperVersionId(version.getPaperVersionId());
    vo.setPaperId(version.getPaperId());
    vo.setVersionNo(version.getVersionNo());
    vo.setDurationSeconds(version.getDurationSeconds());
    vo.setTotalScore(version.getTotalScore());
    vo.setPassRatio(version.getPassRatio());
    vo.setPassScore(version.getPassScore());
    vo.setQuestionCount(version.getQuestionCount());
    vo.setQuestionOrderMode(version.getQuestionOrderMode());
    vo.setMultiSelectScoring(version.getMultiSelectScoring());
    vo.setDrawSettingMode(version.getDrawSettingMode());
    vo.setRandomSeed(version.getRandomSeed());
    vo.setPublishedStatus(version.getPublishedStatus());
    vo.setPublishedBy(version.getPublishedBy());
    vo.setPublishedTime(version.getPublishedTime());
    vo.setVersionNote(version.getVersionNote());
    vo.setQuestions(listVersionQuestions(version.getPaperVersionId()));
    vo.setDrawRules(paperDrawRuleMapper.selectList(new LambdaQueryWrapper<PaperDrawRule>()
                                                     .eq(PaperDrawRule::getPaperVersionId, version.getPaperVersionId())
                                                     .orderByAsc(PaperDrawRule::getSortOrder))
                      .stream().map(rule -> {
        PaperDrawRuleVO rv = new PaperDrawRuleVO();
        BeanUtils.copyProperties(rule, rv);
        return rv;
      }).toList());
    return vo;
  }

  private record ResolvedQuestion(Long questionId, Long questionVersionId, String questionType,
                                  BigDecimal score, Integer sortOrder, Long sourceBankId) {
  }
}
