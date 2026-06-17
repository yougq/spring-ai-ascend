package com.bank.financial.research.engine;

import com.bank.financial.research.data.CompanyData;
import com.bank.financial.research.model.ReportModel;
import com.huawei.ascend.a2a.memory.obs.MemoryObserver;
import com.huawei.ascend.a2a.memory.shared.SharedMemoryStore;
import java.util.function.LongSupplier;

/**
 * Per-run context for the single-company equity pipeline. Adds the assembled
 * {@link CompanyData.Dataset} on top of the shared {@link RunContext} plumbing
 * (blackboard, model, budget, degradation).
 */
public final class ReportContext extends RunContext {

    private final CompanyData.Dataset dataset;

    public ReportContext(ReportRequest request, CompanyData.Dataset dataset, ReportModel model,
            SharedMemoryStore store, MemoryObserver observer, LongSupplier clock) {
        super(request, model, store, observer, clock);
        this.dataset = dataset;
    }

    public CompanyData.Dataset dataset() {
        return dataset;
    }
}
