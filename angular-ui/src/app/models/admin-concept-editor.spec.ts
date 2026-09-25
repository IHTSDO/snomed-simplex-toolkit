import { AdminConceptDescription, sortAdminConceptDescriptions } from './admin-concept-editor';

function desc(overrides: Partial<AdminConceptDescription> & Pick<AdminConceptDescription, 'term' | 'type'>): AdminConceptDescription {
  return {
    lang: 'en',
    active: true,
    released: false,
    acceptabilityMap: {},
    ...overrides
  };
}

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
