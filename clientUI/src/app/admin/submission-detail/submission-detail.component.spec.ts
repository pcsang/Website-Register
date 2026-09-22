import { HttpClientTestingModule } from '@angular/common/http/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';

import { PaymentService } from '../../core/services/payment.service';
import { SubmissionService } from '../../core/services/submission.service';
import { Payment } from '../../models/payment.model';
import { Submission } from '../../models/submission.model';
import { SubmissionDetailComponent } from './submission-detail.component';

const MOCK_SUBMISSION: Submission = {
  id: 1,
  fullName: 'Nguyen Van A',
  email: 'a@example.com',
  phone: '0901234567',
  message: null,
  status: 'PENDING_CONSULTATION',
  courseId: null,
  createdAt: '2026-09-01T10:00:00',
  updatedAt: '2026-09-01T10:00:00'
};

const MOCK_PAYMENT: Payment = {
  id: 1,
  submissionId: 1,
  amount: 12000000,
  status: 'PENDING',
  paymentCode: 'DUP000001',
  qrImageUrl: 'https://img.vietqr.io/image/foo.png',
  paidAt: null,
  createdAt: '2026-09-21T10:00:00',
  updatedAt: '2026-09-21T10:00:00'
};

describe('SubmissionDetailComponent', () => {
  let component: SubmissionDetailComponent;
  let fixture: ComponentFixture<SubmissionDetailComponent>;
  let submissionServiceSpy: jasmine.SpyObj<SubmissionService>;
  let paymentServiceSpy: jasmine.SpyObj<PaymentService>;
  let snackBar: MatSnackBar;

  /**
   * Builds a 404 `HttpErrorResponse` matching what the real `HttpClient` would surface for a
   * missing payment.
   *
   * @returns an Observable that errors with a 404 `HttpErrorResponse`
   */
  function notFoundError(): Observable<never> {
    return throwError(
      () =>
        new HttpErrorResponse({
          status: 404,
          error: { status: 404, message: 'Payment not found', timestamp: '', path: '' }
        })
    );
  }

  beforeEach(async () => {
    submissionServiceSpy = jasmine.createSpyObj('SubmissionService', ['getSubmission', 'updateStatus']);
    paymentServiceSpy = jasmine.createSpyObj('PaymentService', ['getPayment', 'createOrGetPayment']);

    submissionServiceSpy.getSubmission.and.returnValue(of(MOCK_SUBMISSION));
    paymentServiceSpy.getPayment.and.returnValue(notFoundError());

    await TestBed.configureTestingModule({
      imports: [SubmissionDetailComponent, HttpClientTestingModule, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        { provide: SubmissionService, useValue: submissionServiceSpy },
        { provide: PaymentService, useValue: paymentServiceSpy },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id: '1' }) } }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(SubmissionDetailComponent);
    component = fixture.componentInstance;
    // `MatSnackBarModule` (imported by the component) re-provides `MatSnackBar` in the
    // component's own injector scope, shadowing a root-level TestBed override — so the spy must
    // be installed on the instance resolved via the component's own injector, not `TestBed.inject`.
    snackBar = fixture.debugElement.injector.get(MatSnackBar);
  });

  it('should create', () => {
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('sets paymentNotFound without showing an error snackbar when the payment 404s on load', () => {
    spyOn(snackBar, 'open');

    fixture.detectChanges();

    expect(component.paymentNotFound).toBeTrue();
    expect(component.payment).toBeNull();
    expect(snackBar.open).not.toHaveBeenCalled();
  });

  it('populates payment with the correct fields when a payment already exists', () => {
    paymentServiceSpy.getPayment.and.returnValue(of(MOCK_PAYMENT));

    fixture.detectChanges();

    expect(component.paymentNotFound).toBeFalse();
    expect(component.payment).toEqual(MOCK_PAYMENT);
  });

  it('generatePaymentQr() sets payment, clears paymentGenerating, and shows a success snackbar', () => {
    paymentServiceSpy.createOrGetPayment.and.returnValue(of(MOCK_PAYMENT));
    fixture.detectChanges();
    spyOn(snackBar, 'open');

    component.generatePaymentQr();

    expect(component.payment).toEqual(MOCK_PAYMENT);
    expect(component.paymentNotFound).toBeFalse();
    expect(component.paymentGenerating).toBeFalse();
    expect(snackBar.open).toHaveBeenCalled();
  });

  it('generatePaymentQr() shows an error snackbar and clears paymentGenerating on failure', () => {
    paymentServiceSpy.createOrGetPayment.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 500, error: { message: 'boom' } }))
    );
    fixture.detectChanges();
    spyOn(snackBar, 'open');

    component.generatePaymentQr();

    expect(component.paymentGenerating).toBeFalse();
    expect(snackBar.open).toHaveBeenCalled();
  });

  it('refreshPaymentStatus() updates payment from the mocked service response', () => {
    fixture.detectChanges();
    expect(component.paymentNotFound).toBeTrue();

    paymentServiceSpy.getPayment.and.returnValue(of({ ...MOCK_PAYMENT, status: 'PAID', paidAt: '2026-09-21T12:00:00' }));

    component.refreshPaymentStatus();

    expect(component.payment?.status).toBe('PAID');
    expect(component.paymentNotFound).toBeFalse();
    expect(component.paymentLoading).toBeFalse();
  });
});
