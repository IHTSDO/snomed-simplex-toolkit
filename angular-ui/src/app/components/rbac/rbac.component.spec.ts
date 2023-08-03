import { ComponentFixture, TestBed } from '@angular/core/testing';

import { RbacComponent } from './rbac.component';

describe('RbacComponent', () => {
  let component: RbacComponent;
  let fixture: ComponentFixture<RbacComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ RbacComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(RbacComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
