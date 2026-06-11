export default class Utils {
	loginTimeoutInSeconds = 30_000;

	url = Cypress.env('URL_SIMPLEX') || `${Cypress.config('baseUrl')}/`;
	username = Cypress.env('TEST_LOGIN_USR');
	password = Cypress.env('TEST_LOGIN_PSW');

	acceptLegalAgreement(): void {
		cy.contains('Legal Agreement', {timeout: 15000});
		cy.dataCy('legal-agree-button').click();
	}

	login(): void {
		cy.clearAllCookies();
		cy.visit(this.url);
		this.acceptLegalAgreement();
		cy.url({timeout: this.loginTimeoutInSeconds}).should('contain', 'ims');
		cy.contains('Please Log In');
		cy.get('#username').clear();
		cy.get('#username').type(this.username);
		cy.get('#password').clear();
		cy.get('#password').type(this.password, {log: false});
		cy.get('form').submit();
		cy.url({timeout: this.loginTimeoutInSeconds}).should('contain', 'simplex');
	}

	logout(): void {
		cy.dataCy('user-menu').click({force: true});
		cy.contains('Logout').should('be.visible').click();
		cy.get('input#kc-logout').click();
		cy.clearAllCookies();
	}
}
