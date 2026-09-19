import { AdminSettingsComponent } from './admin-settings.component';
import { SimplexService } from 'src/app/services/simplex/simplex.service';
import { of } from 'rxjs';

describe('AdminSettingsComponent', () => {
  it('should mount with admin tabs and default LLM Usage tab', () => {
    cy.mount(AdminSettingsComponent, {
      providers: [
        {
          provide: SimplexService,
          useValue: {
            getRoles: () => of(['ADMIN']),
            getEditions: () => of({ items: [] }),
            getLlmUsage: () => of({
              period: 'week',
              inputTokens: 0,
              outputTokens: 0,
              totalTokens: 0,
              requestCount: 0,
              conceptsTranslated: 0,
              byModel: [],
              dailyBreakdown: []
            })
          }
        }
      ]
    });

    cy.contains('h3', 'Admin');
    cy.contains('LLM Usage');
    cy.contains('Concept editor');
    cy.get('[data-cy=admin-tab-llm-usage]').should('be.visible');
    cy.contains('Period');
  });
});
