export type AdminDescriptionType = 'FSN' | 'SYNONYM' | 'TEXT_DEFINITION';
export type AdminAcceptability = 'PREFERRED' | 'ACCEPTABLE';

export interface AdminConceptLangRefset {
  refsetId: string;
  label: string;
  languageCode: string;
}

export interface AdminConceptDescription {
  descriptionId?: string | null;
  term: string;
  type: AdminDescriptionType | string;
  lang: string;
  active: boolean;
  released: boolean;
  moduleId?: string;
  caseSignificance?: string;
  acceptabilityMap: Record<string, AdminAcceptability | string>;
}

export interface AdminConceptEditorDetail {
  codeSystem: string;
  conceptId: string;
  conceptActive: boolean;
  moduleId: string;
  defaultModuleId: string;
  fsnTerm?: string;
  ptTerm?: string;
  descriptions: AdminConceptDescription[];
  langRefsets: AdminConceptLangRefset[];
}

export interface AdminConceptUpdateRequest {
  descriptions: AdminConceptDescription[];
}
