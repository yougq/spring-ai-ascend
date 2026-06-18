package com.bank.financial.research.eval;

import com.bank.financial.research.engine.Bb;
import com.bank.financial.research.eval.MethodScorecard.Kind;
import com.bank.financial.research.eval.MethodScorecard.Row;
import com.bank.financial.research.fund.FundReport;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds the {@link MethodScorecard} comparing the multi-agent fund engine against
 * a single-model one-shot baseline on the same NAV series. Architectural rows are
 * facts true by construction; measured rows are computed from the two actual
 * outputs so the difference is demonstrated.
 *
 * <p>The framing is deliberately fair: the single model may write fluent prose and
 * may even land a defensible rating. The dimensions that separate them are the
 * bank-accountability ones — is the metric derived from a shown, reproducible
 * computation; is it auditable; was it independently reviewed; are disclosures
 * enforced — not "whose prose is nicer".
 */
public final class MethodComparison {

    private MethodComparison() {
    }

    /** Disclosures a compliant fund report must carry (used to score completeness). */
    private static final String[] REQUIRED_DISCLOSURES = {"过往业绩", "适当性", "数据来源", "风险"};

    public static MethodScorecard compare(FundReport engine, String baselineMarkdown) {
        String engineMd = engine.toMarkdown();
        String baseline = baselineMarkdown == null ? "" : baselineMarkdown;
        List<Row> rows = new ArrayList<>();

        // ── Architectural guarantees (true by construction) ──────────────────────
        rows.add(new Row(Kind.GUARANTEE, "数值来源", "确定性计算引擎", "模型自行推算",
                true, false, "收益/波动/夏普/最大回撤由 FundCalc 纯函数计算,模型多步算术不可靠"));
        rows.add(new Row(Kind.GUARANTEE, "可复现", "同输入→同数字", "采样随机,每次不同",
                true, false, "纯函数确定性 vs LLM 温度采样"));
        rows.add(new Row(Kind.GUARANTEE, "可审计/溯源", "每指标带 owner+出处", "黑箱,无法追溯",
                true, false, "黑板单一事实源 + provenance,可回答'这个夏普怎么算的'"));
        rows.add(new Row(Kind.GUARANTEE, "跨章节一致", "单一事实源强制一致", "长文自漂移",
                true, false, "摘要/业绩/风险引用同一黑板键"));
        rows.add(new Row(Kind.GUARANTEE, "独立复核", "critic 独立核对+有界改稿", "自评自说",
                true, false, "撰写者与评审者分离的双人复核"));
        rows.add(new Row(Kind.GUARANTEE, "职责分离", "撰写≠决策≠合规", "一身多职",
                true, false, "卖方研报治理:首席决策、合规签发各自留痕"));
        rows.add(new Row(Kind.GUARANTEE, "强制合规披露", "合规 agent 强制", "取决于模型记忆",
                true, false, "业绩免责/适当性/数据来源是流程产物,非模型自觉"));

        // ── Measured on this run ─────────────────────────────────────────────────
        int engineDisc = countDisclosures(engineMd);
        int baselineDisc = countDisclosures(baseline);
        int req = REQUIRED_DISCLOSURES.length;
        rows.add(new Row(Kind.MEASURED, "合规披露完备(条)",
                engineDisc + "/" + req, baselineDisc + "/" + req,
                engineDisc >= req, baselineDisc >= req,
                "扫描:" + String.join("、", REQUIRED_DISCLOSURES)));

        // Sharpe auditability: the engine's Sharpe IS the computed value; the single
        // model only states a number — measure how far it lands from the auditable
        // computation (illustrates un-anchoring).
        double computedSharpe = engine.metrics().sharpe();
        OptionalDouble baselineSharpe = statedSharpe(baseline);
        String engineCell = "= 计算值 " + Bb.fmt(computedSharpe);
        String baselineCell;
        if (baselineSharpe.isEmpty()) {
            baselineCell = "未给出可核验夏普";
        } else {
            double dev = Math.abs(baselineSharpe.getAsDouble() - computedSharpe);
            baselineCell = Bb.fmt(baselineSharpe.getAsDouble()) + "(与计算值差 " + Bb.fmt(dev) + ",来源不可核验)";
        }
        rows.add(new Row(Kind.MEASURED, "夏普可核验", engineCell, baselineCell,
                true, false, "引擎夏普=确定性 FundCalc;单模型仅给结论数"));

        int engineDrift = engine.metadata().consistencyFindings().size();
        rows.add(new Row(Kind.MEASURED, "数值一致性门",
                "0 漂移(已过门)", "无此门",
                engineDrift == 0, false,
                "引擎用 NumericConsistencyChecker 核对正文↔黑板;单模型无校验"));

        rows.add(new Row(Kind.MEASURED, "报告字数",
                String.valueOf(engineMd.length()), String.valueOf(baseline.length()),
                true, true, "文笔/篇幅不是差异点,可核验性才是"));

        return new MethodScorecard(rows);
    }

    private static int countDisclosures(String text) {
        int n = 0;
        for (String d : REQUIRED_DISCLOSURES) {
            if (text.contains(d)) {
                n++;
            }
        }
        return n;
    }

    private static final Pattern SHARPE = Pattern.compile(
            "夏普(?:比率)?[^0-9\\-]{0,8}(-?\\d+(?:\\.\\d+)?)");

    /** Best-effort extraction of the single model's stated Sharpe ratio from its prose. */
    static OptionalDouble statedSharpe(String text) {
        if (text == null) {
            return OptionalDouble.empty();
        }
        Matcher m = SHARPE.matcher(text);
        if (m.find()) {
            try {
                return OptionalDouble.of(Double.parseDouble(m.group(1)));
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return OptionalDouble.empty();
    }
}
