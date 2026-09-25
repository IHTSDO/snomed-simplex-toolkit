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
  internationalModuleIds: string[];
}

export type DescriptionModuleKind = 'extension' | 'international' | 'other';

export interface AdminConceptUpdateRequest {
  descriptions: AdminConceptDescription[];
}

export interface AdminConceptEditorPanelState {
  panelId: string;
  conceptId: string;
  loading: boolean;
  saving: boolean;
  dirty: boolean;
  detail: AdminConceptEditorDetail | null;
  newSynonymRefsetId: string;
  newSynonymTerm: string;
  newDescriptionType: AdminDescriptionType;
}

/** Matches AdminConceptEditorService DESCRIPTION_DISPLAY_ORDER on the API. */
function descriptionTypeSortKey(type: AdminDescriptionType | string | undefined): number {
  switch (type) {
    case 'FSN':
      return 0;
    case 'SYNONYM':
      return 1;
    case 'TEXT_DEFINITION':
      return 2;
    default:
      return 99;
  }
}

export function sortAdminConceptDescriptions(descriptions: AdminConceptDescription[]): AdminConceptDescription[] {
  return [...descriptions].sort((a, b) => {
    if (a.active !== b.active) {
      return a.active ? -1 : 1;
    }
    const typeDiff = descriptionTypeSortKey(a.type) - descriptionTypeSortKey(b.type);
    if (typeDiff !== 0) {
      return typeDiff;
    }
    return (a.term || '').localeCompare(b.term || '', undefined, { sensitivity: 'base' });
  });
}
