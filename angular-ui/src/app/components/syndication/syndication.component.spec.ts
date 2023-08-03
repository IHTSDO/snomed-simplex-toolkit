import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SyndicationComponent } from './syndication.component';

describe('SyndicationComponent', () => {
  let component: SyndicationComponent;
  let fixture: ComponentFixture<SyndicationComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ SyndicationComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(SyndicationComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
