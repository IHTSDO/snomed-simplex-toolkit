import { TestBed } from '@angular/core/testing';

import { LegalAgreementService } from './legal-agreement.service';

describe('LegalAgreementService', () => {
  let service: LegalAgreementService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(LegalAgreementService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
