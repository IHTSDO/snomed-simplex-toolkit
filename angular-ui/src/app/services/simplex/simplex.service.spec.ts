import { TestBed } from '@angular/core/testing';

import { SimplexService } from './simplex.service';

describe('SimplexService', () => {
  let service: SimplexService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(SimplexService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
