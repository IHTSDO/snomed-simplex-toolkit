import { AdminConceptEditorComponent } from './admin-concept-editor.component';
import { SimplexService } from 'src/app/services/simplex/simplex.service';
import { AdminConceptEditorDetail, AdminConceptEditorPanelState } from 'src/app/models/admin-concept-editor';
import { delay, of, throwError } from 'rxjs';

describe('AdminConceptEditorComponent', () => {
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
    internationalModuleIds: ['900000000000207008', '900000000000012004'],
    descriptions: [
      {
        descriptionId: '111',
        term: 'Released term (finding)',
        type: 'FSN',
        lang: 'en',
        active: true,
        released: true,
        acceptabilityMap: { '900000000000509007': 'PREFERRED' }
      }
    ]
  };

  const mockDetailB: AdminConceptEditorDetail = {
    ...mockDetail,
    conceptId: '987654321',
    fsnTerm: 'Other concept (finding)'
  };

  function loadedPanel(detail: AdminConceptEditorDetail, panelId: string): AdminConceptEditorPanelState {
    return {
      panelId,
      conceptId: detail.conceptId,
      loading: false,
      saving: false,
      dirty: false,
      detail,
      newSynonymRefsetId: detail.langRefsets[0]?.refsetId || '',
      newSynonymTerm: ''
    };
  }

  it('shows a loading placeholder when Load is clicked', () => {
    cy.mount(AdminConceptEditorComponent, {
      componentProperties: {
        editions: [{ shortName: 'SNOMEDCT-TEST' }],
        selectedEdition: 'SNOMEDCT-TEST',
        conceptIdInput: '123456789'
      },
      providers: [
        {
          provide: SimplexService,
          useValue: {
            getAdminConceptForEditor: () => of(mockDetail).pipe(delay(500)),
            updateAdminConceptDescriptions: () => of(mockDetail)
          }
        }
      ]
    });

    cy.get('[data-cy=admin-concept-load]').click();
    cy.get('[data-cy=admin-concept-panel-loading]').should('exist');
    cy.get('[data-cy=admin-concept-panel-loading]').contains('123456789');
    cy.contains('Finding (finding)', { timeout: 10000 });
  });

  it('stacks newly loaded concepts at the top', () => {
    cy.mount(AdminConceptEditorComponent, {
      componentProperties: {
        editions: [{ shortName: 'SNOMEDCT-TEST' }],
        selectedEdition: 'SNOMEDCT-TEST'
      },
      providers: [
        {
          provide: SimplexService,
          useValue: {
            getAdminConceptForEditor: (_edition: string, conceptId: string) => {
              if (conceptId === '123456789') {
                return of(mockDetail);
              }
              return of(mockDetailB);
            },
            updateAdminConceptDescriptions: () => of(mockDetail)
          }
        }
      ]
    });

    cy.get('[data-cy=admin-concept-id-input]').type('123456789');
    cy.get('[data-cy=admin-concept-load]').click();
    cy.contains('Finding (finding)');

    cy.get('[data-cy=admin-concept-id-input]').type('987654321');
    cy.get('[data-cy=admin-concept-load]').click();
    cy.contains('Other concept (finding)');

    cy.get('[data-cy=admin-concept-panel-987654321]').should('exist');
    cy.get('.concept-editor-panels').children().first().should('have.attr', 'data-cy', 'admin-concept-panel-987654321');
  });

  it('blocks loading a concept that is already open', () => {
    cy.mount(AdminConceptEditorComponent, {
      componentProperties: {
        editions: [{ shortName: 'SNOMEDCT-TEST' }],
        selectedEdition: 'SNOMEDCT-TEST',
        panels: [loadedPanel(mockDetail, 'panel-a')]
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

    cy.get('[data-cy=admin-concept-id-input]').type('123456789');
    cy.get('[data-cy=admin-concept-load]').click();
    cy.get('[data-cy=admin-concept-panel-123456789]').should('have.length', 1);
  });

  it('removes the loading placeholder when load fails', () => {
    cy.mount(AdminConceptEditorComponent, {
      componentProperties: {
        editions: [{ shortName: 'SNOMEDCT-TEST' }],
        selectedEdition: 'SNOMEDCT-TEST',
        conceptIdInput: '123456789'
      },
      providers: [
        {
          provide: SimplexService,
          useValue: {
            getAdminConceptForEditor: () => throwError(() => ({ message: 'Concept not found.' })),
            updateAdminConceptDescriptions: () => of(mockDetail)
          }
        }
      ]
    });

    cy.get('[data-cy=admin-concept-load]').click();
    cy.get('[data-cy=admin-concept-panel-loading]').should('exist');
    cy.get('[data-cy=admin-concept-panel-loading]', { timeout: 5000 }).should('not.exist');
  });

  it('save and close removes only the saved panel', () => {
    const dirtyPanel = loadedPanel(mockDetail, 'panel-a');
    dirtyPanel.dirty = true;
    const otherPanel = loadedPanel(mockDetailB, 'panel-b');

    cy.mount(AdminConceptEditorComponent, {
      componentProperties: {
        editions: [{ shortName: 'SNOMEDCT-TEST' }],
        selectedEdition: 'SNOMEDCT-TEST',
        panels: [dirtyPanel, otherPanel]
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

    cy.get('[data-cy=admin-concept-panel-123456789]').find('[data-cy=admin-concept-save]').click();
    cy.get('[data-cy=admin-concept-panel-123456789]').should('not.exist');
    cy.get('[data-cy=admin-concept-panel-987654321]').should('exist');
  });
});
