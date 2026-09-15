package com.example.approval.mycases;

/**
 * Status filter of the <b>My Cases</b> listing ({@code /my-cases.xhtml}).
 *
 * <p>The token doubles as the suffix of the localized label
 * ({@code mc.status.<token>}) and - except for {@link #ALL} - as the CSS
 * status-badge key of {@link CaseRow#getStatusKey()}, so the dropdown and
 * the table badges share one vocabulary. The filter is applied inside the
 * Flowable {@code HistoricProcessInstanceQuery} (see
 * {@code MyCasesService#applyStatusCriteria}) - never in the UI - so
 * pagination and the total count stay server-side for every status.</p>
 *
 * <p>Only engine-derivable states are offered: a business "rejected"
 * outcome is a subset of {@link #COMPLETED} that is not expressible as a
 * clean query-side split (the final outcome variable does not exist yet),
 * so it is deliberately not a filter token.</p>
 */
public enum CaseStatusFilter {

    /** No status restriction (default). */
    ALL("all"),

    /** Still running: {@code unfinished()} ({@code END_TIME_ IS NULL}). */
    IN_PROGRESS("inprogress"),

    /** Finished normally: {@code finished().notDeleted()}. */
    COMPLETED("completed"),

    /** Deleted while running: {@code finished().deleted()} ({@code DELETE_REASON_} set). */
    CANCELLED("cancelled");

    private final String token;

    CaseStatusFilter(String token) {
        this.token = token;
    }

    /** Stable token used in labels and CSS (never null/blank). */
    public String getToken() {
        return token;
    }

    /** Null/unknown-safe parse; blank or unknown falls back to {@link #ALL}. */
    public static CaseStatusFilter fromToken(String token) {
        if (token != null && !token.isBlank()) {
            for (CaseStatusFilter filter : values()) {
                if (filter.token.equals(token.trim())) {
                    return filter;
                }
            }
        }
        return ALL;
    }
}