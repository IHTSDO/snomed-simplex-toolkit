describe('Visit home page, accept legal agreement, log in.', () => {
  it('Login', () => {
    cy.login()
    cy.contains('Edition Artifacts')
  })
})
