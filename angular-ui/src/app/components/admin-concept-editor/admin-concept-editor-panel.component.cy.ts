import { AdminConceptEditorPanelComponent } from './admin-concept-editor-panel.component';
import { AdminConceptEditorDetail, AdminConceptEditorPanelState } from 'src/app/models/admin-concept-editor';

describe('AdminConceptEditorPanelComponent', () => {
  const internationalModuleIds = ['900000000000207008', '900000000000012004'];

  const mockDetail: AdminConceptEditorDetail = {
    codeSystem: 'SNOMEDCT-TEST',
    conceptId: '123456789',
    conceptActive: true,
    moduleId: '101000003010',
    defaultModuleId: '101000003010',
    fsnTerm: 'Finding (finding)',
    ptTerm: 'Finding',
    langRefsets: [
      { refsetId: '900000000000509007', label: 'US English', languageCode: 'en' }
    ],
    internationalModuleIds,
    descriptions: [
      {
        descriptionId: '111',
        term: 'Released term (finding)',
        type: 'FSN',
        lang: 'en',
        active: true,
        released: true,
        moduleId: '101000003010',
        acceptabilityMap: { '900000000000509007': 'PREFERRED' }
      },
      {
        descriptionId: '222',
        term: 'Editable synonym',
        type: 'SYNONYM',
        lang: 'en',
        active: true,
        released: false,
        moduleId: '101000003010',
        acceptabilityMap: { '900000000000509007': 'ACCEPTABLE' }
      }
    ]
  };

  function loadedPanel(detail: AdminConceptEditorDetail, overrides: Partial<AdminConceptEditorPanelState> = {}): AdminConceptEditorPanelState {
    return {
      panelId: 'test-panel',
      conceptId: detail.conceptId,
      loading: false,
      saving: false,
      dirty: false,
      detail,
      newSynonymRefsetId: detail.langRefsets[0]?.refsetId || '',
      newSynonymTerm: '',
      ...overrides
    };
  }

  it('shows extension, international, and other module dots with tooltips', () => {
    const moduleDetail: AdminConceptEditorDetail = {
      ...mockDetail,
      codeSystem: 'SNOMEDCT-TEST',
      defaultModuleId: '101000003010',
      descriptions: [
        {
          descriptionId: 'ext-1',
          term: 'Extension synonym',
          type: 'SYNONYM',
          lang: 'en',
          active: true,
          released: false,
          moduleId: '101000003010',
          acceptabilityMap: { '900000000000509007': 'ACCEPTABLE' }
        },
        {
          descriptionId: 'int-1',
          term: 'International FSN',
          type: 'FSN',
          lang: 'en',
          active: true,
          released: true,
          moduleId: '900000000000207008',
          acceptabilityMap: { '900000000000509007': 'PREFERRED' }
        },
        {
          descriptionId: 'other-1',
          term: 'Other module synonym',
          type: 'SYNONYM',
          lang: 'en',
          active: true,
          released: true,
          moduleId: '11000279109',
          acceptabilityMap: { '900000000000509007': 'ACCEPTABLE' }
        }
      ]
    };

    cy.mount(AdminConceptEditorPanelComponent, {
      componentProperties: {
        panel: loadedPanel(moduleDetail),
        editionDisplayName: 'Test Edition'
      }
    });

    cy.get('.module-dot--extension').should('have.length', 1);
    cy.get('.module-dot--international').should('have.length', 1);
    cy.get('.module-dot--other').should('have.length', 1);

    cy.get('.module-dot--extension').trigger('mouseenter');
    cy.get('.mat-mdc-tooltip').should('contain.text', 'SNOMEDCT-TEST / Test Edition');

    cy.get('.module-dot--international').trigger('mouseenter');
    cy.get('.mat-mdc-tooltip').should('contain.text', 'International');

    cy.get('.module-dot--other').trigger('mouseenter');
    cy.get('.mat-mdc-tooltip').should('contain.text', '11000279109');
  });

  it('keeps released description term read-only', () => {
    cy.mount(AdminConceptEditorPanelComponent, {
      componentProperties: {
        panel: loadedPanel(mockDetail)
      }
    });

    cy.contains('123456789');
    cy.contains('Released term (finding)');
    cy.get('[data-cy=admin-concept-term-readonly]').should('exist');
    cy.get('textarea').filter('[readonly]').should('have.length', 1);
  });

  it('highlights conflicting preferred synonym acceptability buttons', () => {
    const conflictDetail: AdminConceptEditorDetail = {
      ...mockDetail,
      descriptions: [
        mockDetail.descriptions[0],
        {
          ...mockDetail.descriptions[1],
          acceptabilityMap: { '900000000000509007': 'PREFERRED' }
        },
        {
          descriptionId: '333',
          term: 'Another preferred synonym',
          type: 'SYNONYM',
          lang: 'en',
          active: true,
          released: false,
          acceptabilityMap: { '900000000000509007': 'PREFERRED' }
        }
      ]
    };

    cy.mount(AdminConceptEditorPanelComponent, {
      componentProperties: {
        panel: loadedPanel(conflictDetail)
      }
    });

    cy.get('.acceptability-button--conflict').should('have.length.at.least', 2);
  });

  const belgianFrenchRefsetId = '21000220103';

  it('shows language refset on wrong-language description when acceptability exists', () => {
    const wrongLangDetail: AdminConceptEditorDetail = {
      ...mockDetail,
      langRefsets: [
        { refsetId: '900000000000509007', label: 'US English', languageCode: 'en' },
        { refsetId: belgianFrenchRefsetId, label: 'Belgian French', languageCode: 'fr' }
      ],
      descriptions: [
        {
          descriptionId: '444',
          term: 'English term on wrong refset',
          type: 'SYNONYM',
          lang: 'en',
          active: true,
          released: false,
          acceptabilityMap: { [belgianFrenchRefsetId]: 'PREFERRED' }
        }
      ]
    };

    cy.mount(AdminConceptEditorPanelComponent, {
      componentProperties: {
        panel: loadedPanel(wrongLangDetail)
      }
    });

    cy.contains('button', 'Belgian French: Pref').should('exist');
    cy.contains('button', 'US English:').should('exist');
  });

  it('hides language refset on wrong-language description without acceptability', () => {
    const wrongLangNoAcceptabilityDetail: AdminConceptEditorDetail = {
      ...mockDetail,
      langRefsets: [
        { refsetId: '900000000000509007', label: 'US English', languageCode: 'en' },
        { refsetId: belgianFrenchRefsetId, label: 'Belgian French', languageCode: 'fr' }
      ],
      descriptions: [
        {
          descriptionId: '555',
          term: 'English synonym',
          type: 'SYNONYM',
          lang: 'en',
          active: true,
          released: false,
          acceptabilityMap: {}
        }
      ]
    };

    cy.mount(AdminConceptEditorPanelComponent, {
      componentProperties: {
        panel: loadedPanel(wrongLangNoAcceptabilityDetail)
      }
    });

    cy.contains('button', 'Belgian French:').should('not.exist');
    cy.contains('button', 'US English: —').should('exist');
  });
});
