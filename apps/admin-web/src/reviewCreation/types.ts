export type PaymentMethod = "MONTHLY" | "MILESTONE";

export type PricingMode = "FIXED_TOTAL_PRICE" | "PROVISIONAL_TOTAL_PRICE";

export type InvoiceType = "VAT_GENERAL" | "VAT_SPECIAL";

export interface StructuredFields {
  contractName: string;
  partyAName: string;
  partyBName: string;
  projectName: string;
  contractTotalAmount: number;
  taxExcludedAmount: number;
  taxAmount: number;
  taxRate: number;
  pricingMode: PricingMode;
  paymentMethod: PaymentMethod;
  invoiceType: InvoiceType;
  currency: "CNY";
  prepaymentRatio?: number;
  progressPaymentRatio?: number;
  completionPaymentRatio?: number;
  settlementPaymentRatio?: number;
  warrantyRetentionRatio?: number;
  milestonePaymentTerms?: string;
}

export interface CreateReviewTaskMetadata {
  businessDocumentId?: string;
  contractType: "ENGINEERING";
  structuredFields: StructuredFields;
}

export interface CreateReviewTaskResponse {
  taskId: string;
  executionId: string;
  status: "QUEUED";
  resultUrl: string;
}

export interface FieldError {
  field: string;
  code: string;
  message: string;
}

export interface ValidationErrorResponse {
  code: string;
  message: string;
  fieldErrors: FieldError[];
}

export interface BusinessErrorResponse {
  code: string;
  message: string;
  reason?: string;
  retryable?: boolean;
  operatorActionRequired?: boolean;
}

/** Form state for editable fields. Numbers kept as raw strings for controlled validation. */
export interface FormFields {
  contractName: string;
  partyAName: string;
  partyBName: string;
  projectName: string;
  contractTotalAmount: string;
  taxExcludedAmount: string;
  taxAmount: string;
  taxRate: string;
  pricingMode: PricingMode | "";
  paymentMethod: PaymentMethod | "";
  invoiceType: InvoiceType | "";
  businessDocumentId: string;
  prepaymentRatio: string;
  progressPaymentRatio: string;
  completionPaymentRatio: string;
  settlementPaymentRatio: string;
  warrantyRetentionRatio: string;
  milestonePaymentTerms: string;
}

export const EMPTY_FORM: FormFields = {
  contractName: "",
  partyAName: "",
  partyBName: "",
  projectName: "",
  contractTotalAmount: "",
  taxExcludedAmount: "",
  taxAmount: "",
  taxRate: "",
  pricingMode: "",
  paymentMethod: "",
  invoiceType: "",
  businessDocumentId: "",
  prepaymentRatio: "",
  progressPaymentRatio: "",
  completionPaymentRatio: "",
  settlementPaymentRatio: "",
  warrantyRetentionRatio: "",
  milestonePaymentTerms: "",
};

export const MAX_UPLOAD_BYTES = 26_214_400; // 25 MiB
