import { TestBed } from '@angular/core/testing';

import { UiConfigurationService } from './ui-configuration.service';

describe('UiConfigurationService', () => {
  let service: UiConfigurationService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(UiConfigurationService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
