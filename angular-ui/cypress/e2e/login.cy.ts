import Utils from '../commands/Utils';

const utils = new Utils();

describe('Login', () => {

    it('should log in and land on Edition artifacts', () => {
        utils.login();
        cy.dataCy('artifacts-title').should('contain', 'Edition artifacts');
    });

    it('should log out', () => {
        utils.logout();
    });
});
