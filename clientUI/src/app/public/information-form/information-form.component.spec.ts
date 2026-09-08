import { ComponentFixture, TestBed } from '@angular/core/testing';

import { InformationFormComponent } from './information-form.component';

describe('InformationFormComponent', () => {
  let component: InformationFormComponent;
  let fixture: ComponentFixture<InformationFormComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [InformationFormComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(InformationFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
