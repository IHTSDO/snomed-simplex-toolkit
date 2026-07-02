export interface LlmUsageByModel {
  model: string;
  provider: string;
  inputTokens: number;
  outputTokens: number;
  totalTokens: number;
  requestCount: number;
}

export interface LlmUsageDailyBreakdown {
  date: string;
  codesystem: string;
  model: string;
  provider: string;
  inputTokens: number;
  outputTokens: number;
  totalTokens: number;
  requestCount: number;
}

export interface LlmUsageSummary {
  period: string;
  codesystem?: string;
  model?: string;
  startDate?: string;
  endDate?: string;
  inputTokens: number;
  outputTokens: number;
  totalTokens: number;
  requestCount: number;
  byModel: LlmUsageByModel[];
  dailyBreakdown: LlmUsageDailyBreakdown[];
}

export type LlmUsagePeriod = 'day' | 'week' | 'month' | '3months' | '6months' | 'year' | 'all';

export const LLM_USAGE_PERIODS: { value: LlmUsagePeriod; label: string }[] = [
  { value: 'day', label: 'Last day' },
  { value: 'week', label: 'Week' },
  { value: 'month', label: 'Month' },
  { value: '3months', label: '3 months' },
  { value: '6months', label: '6 months' },
  { value: 'year', label: 'Year' },
  { value: 'all', label: 'All time' }
];
