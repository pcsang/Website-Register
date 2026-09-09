import { HttpClientTestingModule } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { InformationFormComponent } from './information-form.component';

describe('InformationFormComponent', () => {
  let component: InformationFormComponent;
  let fixture: ComponentFixture<InformationFormComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [InformationFormComponent, HttpClientTestingModule, NoopAnimationsModule]
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
