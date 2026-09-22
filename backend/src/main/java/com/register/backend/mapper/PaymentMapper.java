package com.register.backend.mapper;

import com.register.backend.dto.response.PaymentResponse;
import com.register.backend.entity.Payment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class PaymentMapper {

    private final String bankAccountNumber;
    private final String bankCode;
    private final String accountHolderName;
    private final String qrTemplate;

    /**
     * Creates the mapper.
     *
     * @param bankAccountNumber the beneficiary bank account number used to build the VietQR image URL
     * @param bankCode          the beneficiary bank's VietQR bank code
     * @param accountHolderName the beneficiary account holder's display name
     * @param qrTemplate        the VietQR image template to use (e.g. {@code compact2})
     */
    public PaymentMapper(@Value("${app.sepay.bank-account-number}") String bankAccountNumber,
                          @Value("${app.sepay.bank-code}") String bankCode,
                          @Value("${app.sepay.account-holder-name}") String accountHolderName,
                          @Value("${app.sepay.qr-template}") String qrTemplate) {
        this.bankAccountNumber = bankAccountNumber;
        this.bankCode = bankCode;
        this.accountHolderName = accountHolderName;
        this.qrTemplate = qrTemplate;
    }

    /**
     * Maps a {@link Payment} entity to its response DTO, deriving a human-readable payment code and a
     * VietQR image URL for the payer to scan.
     *
     * @param payment the payment entity to map
     * @return the mapped response
     */
    public PaymentResponse toResponse(Payment payment) {
        String paymentCode = "DUP" + String.format("%06d", payment.getId());
        String amountText = payment.getAmount().setScale(0, RoundingMode.HALF_UP).toBigInteger().toString();
        String qrImageUrl = "https://img.vietqr.io/image/" + bankCode + "-" + bankAccountNumber + "-" + qrTemplate
                + ".png?amount=" + amountText
                + "&addInfo=" + URLEncoder.encode(paymentCode, StandardCharsets.UTF_8)
                + "&accountName=" + URLEncoder.encode(accountHolderName, StandardCharsets.UTF_8);

        return new PaymentResponse(
                payment.getId(),
                payment.getSubmissionId(),
                payment.getAmount(),
                payment.getStatus(),
                paymentCode,
                qrImageUrl,
                payment.getPaidAt(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }

}
