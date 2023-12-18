import { ComponentFixture, TestBed } from '@angular/core/testing';

import { LegalAgreementComponent } from './legal-agreement.component';

describe('LegalAgreementComponent', () => {
  let component: LegalAgreementComponent;
  let fixture: ComponentFixture<LegalAgreementComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [LegalAgreementComponent]
    });
    fixture = TestBed.createComponent(LegalAgreementComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
