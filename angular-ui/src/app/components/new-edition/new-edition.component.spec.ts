import { ComponentFixture, TestBed } from '@angular/core/testing';

import { NewEditionComponent } from './new-edition.component';

describe('NewEditionComponent', () => {
  let component: NewEditionComponent;
  let fixture: ComponentFixture<NewEditionComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [NewEditionComponent]
    });
    fixture = TestBed.createComponent(NewEditionComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
