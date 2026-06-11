import Utils from '../commands/Utils';

const utils = new Utils();

describe('Edition Navigation', () => {

    it('should log in', () => {
        utils.login();
    });

    it('should navigate sidebar routes', () => {
        cy.dataCy('nav-artifacts').click();
        cy.url({timeout: 15000}).should('include', '/artifact/');
        cy.dataCy('artifacts-title').should('contain', 'Edition artifacts');

        cy.dataCy('nav-translation-studio').click();
        cy.url({timeout: 15000}).should('include', '/translation-studio/');
        cy.contains('Translation Sets');

        cy.dataCy('nav-manage-edition').click();
        cy.url({timeout: 15000}).should('include', '/manage/');
        cy.contains('Manage Current Editing Cycle');

        cy.dataCy('nav-releases').click();
        cy.url({timeout: 15000}).should('include', '/releases/');
        cy.contains('Releases:');

        cy.dataCy('nav-edition-info').click();
        cy.url({timeout: 15000}).should('include', '/info/');
        cy.contains('Details of:');
    });

    it('should log out', () => {
        utils.logout();
    });
});
