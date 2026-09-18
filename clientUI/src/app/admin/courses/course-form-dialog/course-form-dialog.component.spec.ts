import { HttpClientTestingModule } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

import { CourseFormDialogComponent } from './course-form-dialog.component';

describe('CourseFormDialogComponent', () => {
  let component: CourseFormDialogComponent;
  let fixture: ComponentFixture<CourseFormDialogComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CourseFormDialogComponent, HttpClientTestingModule, NoopAnimationsModule],
      providers: [
        { provide: MatDialogRef, useValue: { close: () => {} } },
        { provide: MAT_DIALOG_DATA, useValue: { course: null } }
      ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(CourseFormDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
