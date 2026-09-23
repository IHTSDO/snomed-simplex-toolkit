import { AdminConceptEditorComponent } from './admin-concept-editor.component';
import { SimplexService } from 'src/app/services/simplex/simplex.service';
import { of } from 'rxjs';

describe('AdminConceptEditorComponent', () => {
  const mockDetail = {
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

  it('loads concept and keeps released description term read-only', () => {
    cy.mount(AdminConceptEditorComponent, {
      componentProperties: {
        editions: [{ shortName: 'SNOMEDCT-TEST' }],
        selectedEdition: 'SNOMEDCT-TEST',
        conceptIdInput: '123456789',
        detail: mockDetail
      },
      providers: [
        {
          provide: SimplexService,
          useValue: {
            getAdminConceptForEditor: () => of(mockDetail),
            updateAdminConceptDescriptions: () => of(mockDetail)
          }
        }
      ]
    });

    cy.contains('123456789');
    cy.contains('Released term (finding)');
    cy.get('[data-cy=admin-concept-term-readonly]').should('exist');
    cy.get('textarea').filter('[readonly]').should('have.length', 1);
  });

  it('highlights conflicting preferred synonym acceptability buttons', () => {
    const conflictDetail = {
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

    cy.mount(AdminConceptEditorComponent, {
      componentProperties: {
        editions: [{ shortName: 'SNOMEDCT-TEST' }],
        selectedEdition: 'SNOMEDCT-TEST',
        detail: conflictDetail
      },
      providers: [
        {
          provide: SimplexService,
          useValue: {
            getAdminConceptForEditor: () => of(conflictDetail),
            updateAdminConceptDescriptions: () => of(conflictDetail)
          }
        }
      ]
    });

    cy.get('.acceptability-button--conflict').should('have.length.at.least', 2);
  });

  const belgianFrenchRefsetId = '21000220103';

  it('shows language refset on wrong-language description when acceptability exists', () => {
    const wrongLangDetail = {
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

    cy.mount(AdminConceptEditorComponent, {
      componentProperties: {
        editions: [{ shortName: 'SNOMEDCT-TEST' }],
        selectedEdition: 'SNOMEDCT-TEST',
        detail: wrongLangDetail
      },
      providers: [
        {
          provide: SimplexService,
          useValue: {
            getAdminConceptForEditor: () => of(wrongLangDetail),
            updateAdminConceptDescriptions: () => of(wrongLangDetail)
          }
        }
      ]
    });

    cy.contains('button', 'Belgian French: Pref').should('exist');
    cy.contains('button', 'US English:').should('exist');
  });

  it('hides language refset on wrong-language description without acceptability', () => {
    const wrongLangNoAcceptabilityDetail = {
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

    cy.mount(AdminConceptEditorComponent, {
      componentProperties: {
        editions: [{ shortName: 'SNOMEDCT-TEST' }],
        selectedEdition: 'SNOMEDCT-TEST',
        detail: wrongLangNoAcceptabilityDetail
      },
      providers: [
        {
          provide: SimplexService,
          useValue: {
            getAdminConceptForEditor: () => of(wrongLangNoAcceptabilityDetail),
            updateAdminConceptDescriptions: () => of(wrongLangNoAcceptabilityDetail)
          }
        }
      ]
    });

    cy.contains('button', 'Belgian French:').should('not.exist');
    cy.contains('button', 'US English: —').should('exist');
  });
});
