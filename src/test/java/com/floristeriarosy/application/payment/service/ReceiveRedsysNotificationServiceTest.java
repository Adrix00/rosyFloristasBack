package com.floristeriarosy.application.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.order.command.ConfirmCheckoutCommand;
import com.floristeriarosy.application.order.dto.ConfirmCheckoutOutcome;
import com.floristeriarosy.application.order.dto.IdempotencyLock;
import com.floristeriarosy.application.order.port.in.ConfirmCheckoutUseCase;
import com.floristeriarosy.application.order.port.out.CheckoutIdempotencyPort;
import com.floristeriarosy.application.payment.dto.GatewayRefundResult;
import com.floristeriarosy.application.payment.port.out.PaymentGatewayPort;
import com.floristeriarosy.application.shared.port.out.PiiCryptoPort;
import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;
import com.floristeriarosy.infrastructure.payment.redsys.RedsysProperties;
import com.floristeriarosy.infrastructure.payment.redsys.RedsysSignature;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class ReceiveRedsysNotificationServiceTest {

  private static final RedsysProperties PROPERTIES =
      new RedsysProperties(
          "999008881",
          "001",
          "sq7HjrUOBfKmC576ILgskD5srU870gJ7",
          "https://sis-t.redsys.es:25443/sis/realizarPago",
          "https://sis-t.redsys.es:25443/sis/rest/trataPeticionREST",
          "http://localhost:8080/api/v1/payments/redsys/notification",
          "http://localhost:5173/checkout/ok",
          "http://localhost:5173/checkout/ko");

  private static final String BUSINESS_ORDER_NUMBER = "WEB-20260914-000001";
  private static final String REDSYS_ORDER_ID = "000000000001";
  private static final String IDEMPOTENCY_KEY = "idem-1";

  private final ConfirmCheckoutUseCase confirmCheckoutUseCase = mock(ConfirmCheckoutUseCase.class);
  private final CheckoutIdempotencyPort idempotencyPort = mock(CheckoutIdempotencyPort.class);
  private final PiiCryptoPort piiCryptoPort = mock(PiiCryptoPort.class);
  private final PaymentGatewayPort gatewayPort = mock(PaymentGatewayPort.class);
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final ReceiveRedsysNotificationService service =
      new ReceiveRedsysNotificationService(
          PROPERTIES,
          objectMapper,
          idempotencyPort,
          piiCryptoPort,
          confirmCheckoutUseCase,
          gatewayPort);

  @Test
  void approvedAfterExpiryRefundsTheChargeFromTheNotificationDataAndCreatesNoOrder() throws Exception {
    String params = signedParams(REDSYS_ORDER_ID, "0000", IDEMPOTENCY_KEY, "auth-1");
    when(idempotencyPort.find(IDEMPOTENCY_KEY)).thenReturn(Optional.of(lockWithStatus("FAILED")));
    when(gatewayPort.refundByGatewayOrderId(any(), any()))
        .thenReturn(new GatewayRefundResult(true, null, null));

    service.execute(params, signature(params, REDSYS_ORDER_ID));

    verify(gatewayPort).refundByGatewayOrderId(REDSYS_ORDER_ID, new BigDecimal("42.00"));
    verify(confirmCheckoutUseCase, never()).execute(any());
  }

  @Test
  void aFailedRefundAfterExpiryStillAnswersNormally() throws Exception {
    String params = signedParams(REDSYS_ORDER_ID, "0000", IDEMPOTENCY_KEY, "auth-1");
    when(idempotencyPort.find(IDEMPOTENCY_KEY)).thenReturn(Optional.of(lockWithStatus("FAILED")));
    when(gatewayPort.refundByGatewayOrderId(any(), any()))
        .thenReturn(new GatewayRefundResult(false, null, "SIS0000"));

    assertThatCode(() -> service.execute(params, signature(params, REDSYS_ORDER_ID)))
        .doesNotThrowAnyException();

    verify(gatewayPort).refundByGatewayOrderId(any(), any());
  }

  @Test
  void aGatewayExceptionAfterExpiryStillAnswersNormally() throws Exception {
    String params = signedParams(REDSYS_ORDER_ID, "0000", IDEMPOTENCY_KEY, "auth-1");
    when(idempotencyPort.find(IDEMPOTENCY_KEY)).thenReturn(Optional.of(lockWithStatus("FAILED")));
    when(gatewayPort.refundByGatewayOrderId(any(), any())).thenThrow(new IllegalStateException("boom"));

    assertThatCode(() -> service.execute(params, signature(params, REDSYS_ORDER_ID)))
        .doesNotThrowAnyException();
  }

  @Test
  void declinedAfterExpiryChangesNothing() throws Exception {
    String params = signedParams(REDSYS_ORDER_ID, "0190", IDEMPOTENCY_KEY, null);
    when(idempotencyPort.find(IDEMPOTENCY_KEY)).thenReturn(Optional.of(lockWithStatus("FAILED")));

    service.execute(params, signature(params, REDSYS_ORDER_ID));

    verify(gatewayPort, never()).refundByGatewayOrderId(any(), any());
    verify(confirmCheckoutUseCase, never()).execute(any());
  }

  @Test
  void approvedForACompletedCheckoutIsAPlainNoOp() throws Exception {
    String params = signedParams(REDSYS_ORDER_ID, "0000", IDEMPOTENCY_KEY, "auth-1");
    when(idempotencyPort.find(IDEMPOTENCY_KEY)).thenReturn(Optional.of(lockWithStatus("COMPLETED")));

    service.execute(params, signature(params, REDSYS_ORDER_ID));

    verify(gatewayPort, never()).refundByGatewayOrderId(any(), any());
    verify(confirmCheckoutUseCase, never()).execute(any());
  }

  @Test
  void approvedThatLosesTheRaceToTheCleanupIsRefundedToo() throws Exception {
    String params = signedParams(REDSYS_ORDER_ID, "0000", IDEMPOTENCY_KEY, "auth-1");
    pendingLockWithOrderNumber(BUSINESS_ORDER_NUMBER);
    when(confirmCheckoutUseCase.execute(any())).thenReturn(ConfirmCheckoutOutcome.EXPIRED);
    when(gatewayPort.refundByGatewayOrderId(any(), any()))
        .thenReturn(new GatewayRefundResult(true, null, null));

    service.execute(params, signature(params, REDSYS_ORDER_ID));

    verify(gatewayPort).refundByGatewayOrderId(REDSYS_ORDER_ID, new BigDecimal("42.00"));
  }

  @Test
  void declinedThatLosesTheRaceToTheCleanupIsNotRefunded() throws Exception {
    String params = signedParams(REDSYS_ORDER_ID, "0190", IDEMPOTENCY_KEY, null);
    pendingLockWithOrderNumber(BUSINESS_ORDER_NUMBER);
    when(confirmCheckoutUseCase.execute(any())).thenReturn(ConfirmCheckoutOutcome.EXPIRED);

    service.execute(params, signature(params, REDSYS_ORDER_ID));

    verify(gatewayPort, never()).refundByGatewayOrderId(any(), any());
  }

  @Test
  void approvedForAPreviousAttemptOfARearmedKeyIsRefundedNotConfirmed() throws Exception {
    String params = signedParams(REDSYS_ORDER_ID, "0000", IDEMPOTENCY_KEY, "auth-1");
    pendingLockWithOrderNumber("WEB-20260914-000002");
    when(gatewayPort.refundByGatewayOrderId(any(), any()))
        .thenReturn(new GatewayRefundResult(true, null, null));

    service.execute(params, signature(params, REDSYS_ORDER_ID));

    verify(gatewayPort).refundByGatewayOrderId(REDSYS_ORDER_ID, new BigDecimal("42.00"));
    verify(confirmCheckoutUseCase, never()).execute(any());
  }

  @Test
  void declinedForAPreviousAttemptMustNotReleaseTheNewAttemptsReservation() throws Exception {
    String params = signedParams(REDSYS_ORDER_ID, "0190", IDEMPOTENCY_KEY, null);
    pendingLockWithOrderNumber("WEB-20260914-000002");

    service.execute(params, signature(params, REDSYS_ORDER_ID));

    verify(gatewayPort, never()).refundByGatewayOrderId(any(), any());
    verify(confirmCheckoutUseCase, never()).execute(any());
  }

  @Test
  void aResolvedApprovalIsNeverRefunded() throws Exception {
    String params = signedParams(REDSYS_ORDER_ID, "0000", IDEMPOTENCY_KEY, "auth-1");
    pendingLockWithOrderNumber(BUSINESS_ORDER_NUMBER);
    when(confirmCheckoutUseCase.execute(any())).thenReturn(ConfirmCheckoutOutcome.RESOLVED);

    service.execute(params, signature(params, REDSYS_ORDER_ID));

    verify(gatewayPort, never()).refundByGatewayOrderId(any(), any());
  }

  private IdempotencyLock lockWithStatus(String status) {
    return new IdempotencyLock(status, new byte[0], UUID.randomUUID(), null);
  }

  @Test
  void approvedNotificationConfirmsTheCheckoutWithOrderNumberFromPayload() throws Exception {
    String params = signedParams(REDSYS_ORDER_ID, "0000", IDEMPOTENCY_KEY, "auth-1");
    pendingLockWithOrderNumber(BUSINESS_ORDER_NUMBER);

    service.execute(params, signature(params, REDSYS_ORDER_ID));

    ArgumentCaptor<ConfirmCheckoutCommand> captor =
        ArgumentCaptor.forClass(ConfirmCheckoutCommand.class);
    verify(confirmCheckoutUseCase).execute(captor.capture());
    ConfirmCheckoutCommand command = captor.getValue();
    assertThat(command.orderNumber()).isEqualTo(OrderNumber.of(BUSINESS_ORDER_NUMBER));
    assertThat(command.approved()).isTrue();
    assertThat(command.providerPaymentIntentId()).isEqualTo("auth-1");
  }

  @Test
  void declinedNotificationCompensatesWithOrderNumberFromPayload() throws Exception {
    String params = signedParams(REDSYS_ORDER_ID, "0190", IDEMPOTENCY_KEY, null);
    pendingLockWithOrderNumber(BUSINESS_ORDER_NUMBER);

    service.execute(params, signature(params, REDSYS_ORDER_ID));

    ArgumentCaptor<ConfirmCheckoutCommand> captor =
        ArgumentCaptor.forClass(ConfirmCheckoutCommand.class);
    verify(confirmCheckoutUseCase).execute(captor.capture());
    ConfirmCheckoutCommand command = captor.getValue();
    assertThat(command.orderNumber()).isEqualTo(OrderNumber.of(BUSINESS_ORDER_NUMBER));
    assertThat(command.approved()).isFalse();
    assertThat(command.failureReason()).contains("0190");
  }

  @Test
  void tamperedSignatureIsRejectedBeforeConfirming() {
    String params =
        Base64.getEncoder()
            .encodeToString(
                ("{\"Ds_Order\":\"" + REDSYS_ORDER_ID + "\","
                    + "\"Ds_Response\":\"0000\","
                    + "\"Ds_Merchant_MerchantData\":\"" + IDEMPOTENCY_KEY + "\"}")
                    .getBytes());

    assertThatIllegalArgumentException()
        .isThrownBy(() -> service.execute(params, "not-a-real-signature"));
    verify(confirmCheckoutUseCase, never()).execute(any());
    verify(idempotencyPort, never()).find(any());
  }

  @Test
  void notificationWithoutAnyOrderIdIsRejectedBeforeAnyStateAccess() {
    String params =
        Base64.getEncoder()
            .encodeToString(
                ("{\"Ds_Response\":\"0000\",\"Ds_Merchant_MerchantData\":\""
                        + IDEMPOTENCY_KEY
                        + "\"}")
                    .getBytes(StandardCharsets.UTF_8));

    assertThatIllegalArgumentException().isThrownBy(() -> service.execute(params, "any-signature"));
    verify(confirmCheckoutUseCase, never()).execute(any());
    verify(idempotencyPort, never()).find(any());
  }

  @Test
  void noPendingLockIsIgnoredNotReprocessed() throws Exception {
    String params = signedParams(REDSYS_ORDER_ID, "0000", IDEMPOTENCY_KEY, "auth-1");
    when(idempotencyPort.find(IDEMPOTENCY_KEY)).thenReturn(java.util.Optional.empty());

    service.execute(params, signature(params, REDSYS_ORDER_ID));

    verify(confirmCheckoutUseCase, never()).execute(any());
  }

  @Test
  void urlSafeBase64IsAccepted() throws Exception {
    String params = signedParams(REDSYS_ORDER_ID, "0000", IDEMPOTENCY_KEY, "auth-1");
    String urlSafeParams = params.replace('+', '-').replace('/', '_');
    String standardSignature = signature(params, REDSYS_ORDER_ID);
    String urlSafeSignature = standardSignature.replace('+', '-').replace('/', '_');
    pendingLockWithOrderNumber(BUSINESS_ORDER_NUMBER);

    service.execute(urlSafeParams, urlSafeSignature);

    verify(confirmCheckoutUseCase).execute(any());
  }

  private String signedParams(
      String redsysOrderId, String responseCode, String idempotencyKey, String authorisationCode)
      throws Exception {
    StringBuilder json =
        new StringBuilder()
            .append("{\"Ds_Order\":\"" + redsysOrderId + "\",")
            .append("\"Ds_Response\":\"" + responseCode + "\",")
            .append("\"Ds_Amount\":\"4200\",")
            .append("\"Ds_Merchant_MerchantData\":\"" + idempotencyKey + "\"");
    if (authorisationCode != null) {
      json.append(",\"Ds_AuthorisationCode\":\"" + authorisationCode + "\"");
    }
    json.append("}");
    return Base64.getEncoder().encodeToString(json.toString().getBytes(StandardCharsets.UTF_8));
  }

  private String signature(String params, String redsysOrderId) {
    return RedsysSignature.sign(PROPERTIES.secretKey(), redsysOrderId, params);
  }

  private void pendingLockWithOrderNumber(String orderNumber) throws Exception {
    String payload =
        "{\"channel\":\"WEB\",\"fulfillment\":\"NONE\",\"orderNumber\":\""
            + orderNumber
            + "\",\"items\":[]}";
    when(idempotencyPort.find(IDEMPOTENCY_KEY))
        .thenReturn(
            java.util.Optional.of(
                new IdempotencyLock("PENDING", new byte[0], UUID.randomUUID(), "encrypted".getBytes())));
    when(piiCryptoPort.decrypt(any())).thenReturn(payload);
    // Avoid unused stubbing warnings when a test does not reach decryption
    when(piiCryptoPort.encrypt(any())).thenReturn("encrypted".getBytes(StandardCharsets.UTF_8));
  }
}
