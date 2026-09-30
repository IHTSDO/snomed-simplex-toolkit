import {
  AdminConceptDescription,
  AdminConceptEditorPanelState,
  AdminConceptLangRefset,
  defaultAcceptabilityForNewDescription,
  insertPanelInNonSavingSection,
  movePanelToSavingTail,
  orderPanelsNonSavingThenSaving,
  sortAdminConceptDescriptions
} from './admin-concept-editor';

function desc(overrides: Partial<AdminConceptDescription> & Pick<AdminConceptDescription, 'term' | 'type'>): AdminConceptDescription {
  return {
    lang: 'en',
    active: true,
    released: false,
    acceptabilityMap: {},
    ...overrides
  };
}

const usRefset: AdminConceptLangRefset = {
  refsetId: '900000000000509007',
  label: 'US English',
  languageCode: 'en'
};

function panel(id: string, saving = false): AdminConceptEditorPanelState {
  return {
    panelId: id,
    conceptId: id,
    loading: false,
    saving,
    dirty: false,
    detail: null,
    newSynonymRefsetId: '',
    newSynonymTerm: '',
    newDescriptionType: 'SYNONYM'
  };
}

describe('panel ordering', () => {
  it('inserts a new panel before saving panels', () => {
    const ordered = insertPanelInNonSavingSection(
      [panel('a'), panel('saving', true)],
      panel('b')
    );
    expect(ordered.map(p => p.panelId)).toEqual(['a', 'b', 'saving']);
  });

  it('moves a panel to the saving tail', () => {
    const panels = [panel('a'), panel('b')];
    const ordered = movePanelToSavingTail(panels, 'a');
    expect(ordered.map(p => p.panelId)).toEqual(['b', 'a']);
    expect(ordered[1].saving).toBe(true);
  });

  it('reorders failed save back to non-saving at the end', () => {
    const a = panel('a');
    a.saving = true;
    const ordered = orderPanelsNonSavingThenSaving([panel('b'), a]);
    expect(ordered.map(p => p.panelId)).toEqual(['b', 'a']);
  });
});

describe('defaultAcceptabilityForNewDescription', () => {
  it('always prefers a new FSN', () => {
    expect(defaultAcceptabilityForNewDescription('FSN', usRefset.refsetId, [usRefset], [
      desc({ term: 'Existing synonym', type: 'SYNONYM', acceptabilityMap: { [usRefset.refsetId]: 'PREFERRED' } })
    ])).toBe('PREFERRED');
  });

  it('prefers the first synonym in a language refset', () => {
    expect(defaultAcceptabilityForNewDescription('SYNONYM', usRefset.refsetId, [usRefset], [
      desc({ term: 'FSN only', type: 'FSN', acceptabilityMap: { [usRefset.refsetId]: 'PREFERRED' } })
    ])).toBe('PREFERRED');
  });

  it('marks an additional synonym as acceptable', () => {
    expect(defaultAcceptabilityForNewDescription('SYNONYM', usRefset.refsetId, [usRefset], [
      desc({ term: 'First synonym', type: 'SYNONYM', acceptabilityMap: { [usRefset.refsetId]: 'PREFERRED' } })
    ])).toBe('ACCEPTABLE');
  });
});

describe('sortAdminConceptDescriptions', () => {
  it('sorts active FSNs before active synonyms and inactive descriptions last', () => {
    const sorted = sortAdminConceptDescriptions([
      desc({ term: 'Inactive synonym', type: 'SYNONYM', active: false }),
      desc({ term: 'Z synonym', type: 'SYNONYM', active: true }),
      desc({ term: 'New FSN (finding)', type: 'FSN', active: true }),
      desc({ term: 'Released FSN (finding)', type: 'FSN', active: true })
    ]);

    expect(sorted.map(d => d.term)).toEqual([
      'New FSN (finding)',
      'Released FSN (finding)',
      'Z synonym',
      'Inactive synonym'
    ]);
  });

  it('places a newly added active FSN at the top among display order rules', () => {
    const existing = [
      desc({ term: 'Released term (finding)', type: 'FSN', active: true }),
      desc({ term: 'Editable synonym', type: 'SYNONYM', active: true })
    ];
    const sorted = sortAdminConceptDescriptions([
      ...existing,
      desc({ term: 'New FSN (finding)', type: 'FSN', active: true })
    ]);

    expect(sorted[0].type).toBe('FSN');
    expect(sorted[0].term).toBe('New FSN (finding)');
    expect(sorted[1].type).toBe('FSN');
    expect(sorted[2].type).toBe('SYNONYM');
  });
});
