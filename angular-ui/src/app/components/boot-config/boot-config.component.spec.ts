import { ComponentFixture, TestBed } from '@angular/core/testing';

import { BootConfigComponent } from './boot-config.component';

describe('BootConfigComponent', () => {
  let component: BootConfigComponent;
  let fixture: ComponentFixture<BootConfigComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ BootConfigComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(BootConfigComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
