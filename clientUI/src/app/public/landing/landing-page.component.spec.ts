import { HttpClientTestingModule } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { LandingPageComponent } from './landing-page.component';
import { Course } from '../../models/course.model';

describe('LandingPageComponent', () => {
  let component: LandingPageComponent;
  let fixture: ComponentFixture<LandingPageComponent>;

  const b2Course: Course = {
    id: 2,
    name: 'Hạng B2 (Ô tô đến 9 chỗ)',
    licenseClass: 'B2',
    price: 9800000,
    durationMonths: 4,
    practiceHours: 24,
    description: 'Xe đưa đón điểm tập trung',
    branch: 'Quận 1',
    teacherName: 'Trần Thị Bình',
    seatsTotal: 40,
    seatsRegistered: 10,
    availabilityStatus: 'AVAILABLE',
    startDate: '2026-10-01',
    createdAt: '2026-09-01T00:00:00',
    updatedAt: '2026-09-01T00:00:00'
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LandingPageComponent, HttpClientTestingModule, NoopAnimationsModule]
    }).compileComponents();

    fixture = TestBed.createComponent(LandingPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('marks only the B2 license class as the popular course', () => {
    expect(component.isPopular(b2Course)).toBe(true);
    expect(component.isPopular({ ...b2Course, licenseClass: 'B1' })).toBe(false);
    expect(component.isPopular({ ...b2Course, licenseClass: 'C' })).toBe(false);
  });

  it('formats a course price as Vietnamese-grouped digits with a trailing đ suffix', () => {
    expect(component.formattedPrice(b2Course)).toBe('9.800.000đ');
  });

  it('requires all 4 registration fields before the form is valid', () => {
    expect(component.registrationForm.valid).toBe(false);
    component.registrationForm.setValue({
      fullName: 'Nguyễn Văn A',
      phone: '0912345678',
      email: 'a@example.com',
      licenseClass: 'B1'
    });
    expect(component.registrationForm.valid).toBe(true);
  });

  it('rejects a phone number that does not match the Vietnamese mobile format', () => {
    const phoneControl = component.registrationForm.controls.phone;
    phoneControl.setValue('12345');
    expect(phoneControl.hasError('pattern')).toBe(true);
  });
});
