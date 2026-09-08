import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { convertToParamMap } from '@angular/router';

import { SubmissionDetailComponent } from './submission-detail.component';

describe('SubmissionDetailComponent', () => {
  let component: SubmissionDetailComponent;
  let fixture: ComponentFixture<SubmissionDetailComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SubmissionDetailComponent],
      providers: [
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id: '1' }) } }
        }
      ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(SubmissionDetailComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
