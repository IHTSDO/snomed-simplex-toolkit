import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ManageCodesystemComponent } from './manage-codesystem.component';

describe('ManageCodesystem', () => {
  let component: ManageCodesystemComponent;
  let fixture: ComponentFixture<ManageCodesystemComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [ManageCodesystemComponent]
    });
    fixture = TestBed.createComponent(ManageCodesystemComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
