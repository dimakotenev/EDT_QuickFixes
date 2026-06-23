package com.quickfixes.edt.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Generic outcome of a Quick Fix run: items handled successfully (with a detail, e.g. the target
 * region or the new name) and items that were skipped/failed (with a reason). Rendered by
 * {@link com.quickfixes.edt.ui.ResultDialog}.
 */
public final class OpResult
{
    /** One reported item. */
    public static final class Entry
    {
        /** Subject name (method, region, variable…). */
        public final String name;
        /** Detail for a success (target region / new name / …), or {@code null} for a failure. */
        public final String detail;
        /** Reason for a failure, or {@code null} for a success. */
        public final String reason;

        Entry(String name, String detail, String reason)
        {
            this.name = name;
            this.detail = detail;
            this.reason = reason;
        }
    }

    private final List<Entry> successes = new ArrayList<>();
    private final List<Entry> failures = new ArrayList<>();

    /**
     * Records a successful item.
     *
     * @param name subject name
     * @param detail what happened (target region, new name, …)
     */
    public void addSuccess(String name, String detail)
    {
        successes.add(new Entry(name, detail, null));
    }

    /**
     * Records a skipped/failed item.
     *
     * @param name subject name
     * @param reason why it was not handled
     */
    public void addFailure(String name, String reason)
    {
        failures.add(new Entry(name, null, reason));
    }

    /** @return successful items. */
    public List<Entry> getSuccesses()
    {
        return successes;
    }

    /** @return skipped/failed items. */
    public List<Entry> getFailures()
    {
        return failures;
    }

    /** @return {@code true} when nothing was recorded. */
    public boolean isEmpty()
    {
        return successes.isEmpty() && failures.isEmpty();
    }
}
