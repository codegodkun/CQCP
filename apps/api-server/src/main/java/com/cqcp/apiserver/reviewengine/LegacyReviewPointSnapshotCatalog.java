package com.cqcp.apiserver.reviewengine;

import java.util.List;

/**
 * Static catalog of the nine legacy v20260705.1 review-point snapshots.
 *
 * <p>Values are derived from ADR-005, {@code docs/review-point-definitions.md},
 * and the code-current {@link MinimalReviewEngine} severity logic.
 * Does NOT load {@code packages/review-assets} (DRAFT / NOT_BOUND).</p>
 */
public class LegacyReviewPointSnapshotCatalog {

    private static final String CONTRACT_TYPE = "ENGINEERING_PROCUREMENT";

    private static final List<ReviewPointSnapshot> ENABLED = List.of(
            snap(ReviewPointCode.PARTY_A_NAME_CONSISTENCY, 1,
                    "甲方名称一致性", "PARTY_FIELDS", FindingSeverity.ERROR),
            snap(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, 2,
                    "乙方名称一致性", "PARTY_FIELDS", FindingSeverity.ERROR),
            snap(ReviewPointCode.CONTRACT_TOTAL_AMOUNT_CONSISTENCY, 3,
                    "合同总金额一致性", "AMOUNT_TAX", FindingSeverity.ERROR),
            snap(ReviewPointCode.TAX_AMOUNT_FORMULA_CONSISTENCY, 4,
                    "税额公式一致性", "AMOUNT_TAX", FindingSeverity.WARNING),
            snap(ReviewPointCode.PREPAYMENT_RATIO_CONSISTENCY, 5,
                    "预付款比例一致性", "PAYMENT_TERMS", FindingSeverity.ERROR),
            snap(ReviewPointCode.PROGRESS_PAYMENT_RATIO_CONSISTENCY, 6,
                    "进度款比例一致性", "PAYMENT_TERMS", FindingSeverity.ERROR),
            snap(ReviewPointCode.COMPLETION_PAYMENT_RATIO_CONSISTENCY, 7,
                    "竣工款比例一致性", "PAYMENT_TERMS", FindingSeverity.ERROR),
            snap(ReviewPointCode.SETTLEMENT_PAYMENT_RATIO_CONSISTENCY, 8,
                    "结算款比例一致性", "PAYMENT_TERMS", FindingSeverity.ERROR),
            snap(ReviewPointCode.WARRANTY_RETENTION_RATIO_CONSISTENCY, 9,
                    "质保款比例一致性", "PAYMENT_TERMS", FindingSeverity.ERROR));

    private static final List<ReviewPointSnapshot> DISABLED = List.of();

    public List<ReviewPointSnapshot> enabledSnapshots() {
        return ENABLED;
    }

    public List<ReviewPointSnapshot> disabledSnapshots() {
        return DISABLED;
    }

    /** displayCode is the ReviewPointCode enum name (e.g. PARTY_A_NAME_CONSISTENCY),
     *  matching the frozen TASK_SPEC table column. */
    private static ReviewPointSnapshot snap(
            ReviewPointCode code, int displayOrder,
            String displayName, String family, FindingSeverity severity) {
        return new ReviewPointSnapshot(code, code.name(), displayName, family,
                CONTRACT_TYPE, severity, displayOrder);
    }
}
