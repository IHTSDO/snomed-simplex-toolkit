import { ComponentFixture, TestBed } from '@angular/core/testing';

import { JobsComponent } from './jobs.component';

describe('JobsComponent', () => {
  let component: JobsComponent;
  let fixture: ComponentFixture<JobsComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [JobsComponent]
    });
    fixture = TestBed.createComponent(JobsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should identify Foundation English refsets by refsetId input', () => {
    component.artifact = { type: 'translation', conceptId: '900000000000509007' };
    component.refsetId = '900000000000509007';
    expect(component.isFoundationEnglishRefset()).toBeTrue();

    component.refsetId = '900000000000508004';
    expect(component.isFoundationEnglishRefset()).toBeTrue();

    component.refsetId = '123456789012345678';
    expect(component.isFoundationEnglishRefset()).toBeFalse();
  });
});
