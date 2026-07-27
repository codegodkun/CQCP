import type { FormFields } from "./types";

/** Frozen demo preset value from CQCP-MVP-DOCX-001 goldenExpected.structuredFields.
 *  contractType is NOT included here — fixed in metadata.contractType = "ENGINEERING".
 *  currency is NOT included here — fixed in api.ts buildStructuredFields() as "CNY". */
export const DEMO_PRESET: Partial<FormFields> = {
  contractName: "奔腾公司企鹅岛项目三标段土建总承包工程合同",
  partyAName: "奔腾公司",
  partyBName: "前水公司",
  projectName: "企鹅岛",
  contractTotalAmount: "8848",
  taxExcludedAmount: "7830.09",
  taxAmount: "1017.91",
  taxRate: "13",
  pricingMode: "FIXED_TOTAL_PRICE",
  paymentMethod: "MONTHLY",
  prepaymentRatio: "0",
  progressPaymentRatio: "70",
  completionPaymentRatio: "80",
  settlementPaymentRatio: "97",
  warrantyRetentionRatio: "3",
  invoiceType: "VAT_SPECIAL",
};
