package com.bank.financial.research.eval;

import com.bank.financial.research.data.FundData;
import com.bank.financial.research.engine.Bb;
import com.bank.financial.research.model.ReportModel;

/**
 * The "just prompt one big model" baseline for a fund report: hand the model the
 * same raw NAV series the engine's agents work from, and ask it — in a single
 * call, with no blackboard, no deterministic {@code FundCalc}, no independent
 * critic, no compliance agent — to produce the whole report including the
 * return/volatility/Sharpe/drawdown it computes itself. This is the honest
 * representative of "为什么不直接用大模型", scored against the engine by
 * {@link MethodComparison}.
 *
 * <p>The instruction deliberately does NOT impose grounding discipline (the live
 * model's {@code baseline} role gets a plain system prompt): the whole point is to
 * observe what an undisciplined single call does with the numbers.
 */
public final class SingleModelBaseline {

    private SingleModelBaseline() {
    }

    /** Generate a one-shot baseline fund report (one model call) from the NAV dataset. */
    public static String generate(FundData.Dataset ds, ReportModel model) {
        String brief = digest(ds);
        String instruction =
                "你是一名基金分析师。请仅凭下列净值数据,一次性撰写一份简明而完整的基金研究报告(约 800 字以内),"
                + "需包含:综合评级、累计收益、年化收益、年化波动、夏普比率、最大回撤、适配人群,以及必要的合规披露。"
                + "请自行从净值序列计算各项指标,直接给出结论数字。";
        return model.generate(new ReportModel.ModelTask("baseline", instruction, brief, 1600));
    }

    /** A compact raw-data digest — the NAV inputs, not any computed conclusion. */
    private static String digest(FundData.Dataset ds) {
        StringBuilder sb = new StringBuilder();
        sb.append("基金: ").append(ds.name()).append(" (").append(ds.code()).append("),类型 ").append(ds.type()).append('\n');
        var navs = ds.navs();
        sb.append("净值样本数: ").append(navs.size());
        if (!navs.isEmpty()) {
            sb.append(",期初 ").append(Bb.fmt(navs.get(0))).append(",期末 ").append(Bb.fmt(navs.get(navs.size() - 1)));
        }
        sb.append('\n');
        sb.append("年化期数: ").append(Bb.fmt(ds.periodsPerYear()))
                .append(",无风险利率 ").append(Bb.pct(ds.riskFreeRate())).append('\n');
        sb.append("净值序列(累计净值): ").append(navs).append('\n');
        if (ds.hasBenchmark() && !ds.benchmark().isEmpty()) {
            sb.append("基准序列: ").append(ds.benchmark()).append('\n');
        }
        return sb.toString();
    }
}
