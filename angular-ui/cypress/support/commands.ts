declare global {
  namespace Cypress {
    interface Chainable {
      login(): void
    }
  }
}

Cypress.Commands.add('login', () => {
  cy.clearAllCookies()
  cy.visit('/')
  cy.get('body').then($body => {
    if ($body.text().includes('Legal Agreement')) {
      cy.get('.actions > button:first').click()
    }
  })
  cy.url({ timeout: 10_000 }).should('contain', 'ims')
  cy.get('#username').type(Cypress.env('username'))
  cy.get('#password').type(Cypress.env('password'))
  cy.get('form').submit()
  cy.url({ timeout: 10_000 }).should('contain', 'simplex')
})
