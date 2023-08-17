import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SubsetsComponent } from './subsets.component';

describe('SubsetsComponent', () => {
  let component: SubsetsComponent;
  let fixture: ComponentFixture<SubsetsComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [SubsetsComponent]
    });
    fixture = TestBed.createComponent(SubsetsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
