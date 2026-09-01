package com.floristeriarosy.domain.model.discount;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.floristeriarosy.domain.exception.discount.DiscountAlreadyStartedException;
import com.floristeriarosy.domain.exception.discount.DiscountLimitBelowSoldException;
import com.floristeriarosy.domain.exception.discount.DiscountNotEditableException;
import com.floristeriarosy.domain.exception.discount.DiscountPeriodInvalidException;
import com.floristeriarosy.domain.exception.discount.DiscountPriceNotLowerException;
import com.floristeriarosy.domain.model.discount.valueobject.DiscountId;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class DiscountTest {

  private static final BigDecimal ORIGINAL_PRICE = new BigDecimal("20.00");
  private static final BigDecimal SALE_PRICE = new BigDecimal("15.00");

  private Discount newDiscount(Instant startsAt, Instant endsAt) {
    return newDiscount(startsAt, endsAt, null);
  }

  private Discount newDiscount(Instant startsAt, Instant endsAt, Integer quantityLimit) {
    return Discount.create(
        DiscountId.newId(), ProductId.newId(), ORIGINAL_PRICE, SALE_PRICE, startsAt, endsAt, quantityLimit);
  }

  private Discount reconstituteDiscount(
      Instant startsAt, Instant endsAt, Integer quantityLimit, int quantitySold) {
    return Discount.reconstitute(
        DiscountId.newId(),
        ProductId.newId(),
        ORIGINAL_PRICE,
        SALE_PRICE,
        startsAt,
        endsAt,
        quantityLimit,
        quantitySold,
        Instant.now(),
        Instant.now());
  }

  @Test
  void createBuildsANewDiscountWithZeroUnitsSold() {
    Instant now = Instant.now();
    Discount discount = newDiscount(now.plusSeconds(3600), now.plusSeconds(7200), 10);

    assertThat(discount.quantitySold()).isZero();
    assertThat(discount.originalPrice()).isEqualByComparingTo(ORIGINAL_PRICE);
    assertThat(discount.salePrice()).isEqualByComparingTo(SALE_PRICE);
  }

  @Test
  void createRejectsSalePriceNotLowerThanOriginalPrice() {
    Instant now = Instant.now();
    assertThatThrownBy(
            () ->
                Discount.create(
                    DiscountId.newId(),
                    ProductId.newId(),
                    ORIGINAL_PRICE,
                    ORIGINAL_PRICE,
                    now.plusSeconds(3600),
                    now.plusSeconds(7200),
                    null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createRejectsEndsAtNotAfterStartsAt() {
    Instant now = Instant.now();
    assertThatThrownBy(() -> newDiscount(now.plusSeconds(7200), now.plusSeconds(3600)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createRejectsNonPositiveQuantityLimit() {
    Instant now = Instant.now();
    assertThatThrownBy(() -> newDiscount(now.plusSeconds(3600), now.plusSeconds(7200), 0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void stateIsScheduledWhenStartsAtIsInTheFuture() {
    Instant now = Instant.now();
    Discount discount = newDiscount(now.plusSeconds(3600), now.plusSeconds(7200));

    assertThat(discount.state()).isEqualTo(DiscountState.SCHEDULED);
  }

  @Test
  void stateIsActiveWhenVigentWithRoomLeft() {
    Instant now = Instant.now();
    Discount discount = reconstituteDiscount(now.minusSeconds(3600), now.plusSeconds(3600), 5, 2);

    assertThat(discount.state()).isEqualTo(DiscountState.ACTIVE);
  }

  @Test
  void stateIsActiveWhenVigentAndUnlimited() {
    Instant now = Instant.now();
    Discount discount = newDiscount(now.minusSeconds(3600), now.plusSeconds(3600));

    assertThat(discount.state()).isEqualTo(DiscountState.ACTIVE);
  }

  @Test
  void stateIsSoldOutWhenVigentAndQuantitySoldReachesTheLimit() {
    Instant now = Instant.now();
    Discount discount = reconstituteDiscount(now.minusSeconds(3600), now.plusSeconds(3600), 5, 5);

    assertThat(discount.state()).isEqualTo(DiscountState.SOLD_OUT);
  }

  @Test
  void stateIsEndedWhenEndsAtHasAlreadyPassed() {
    Instant now = Instant.now();
    Discount discount = newDiscount(now.minusSeconds(7200), now.minusSeconds(3600));

    assertThat(discount.state()).isEqualTo(DiscountState.ENDED);
  }

  @Test
  void stateIsEndedWhenBothEndedAndSoldOutSimultaneously() {
    Instant now = Instant.now();
    Discount discount = reconstituteDiscount(now.minusSeconds(7200), now.minusSeconds(3600), 5, 5);

    assertThat(discount.state()).isEqualTo(DiscountState.ENDED);
  }

  @Test
  void updateAllowsChangingEveryFieldWhenScheduled() {
    Instant now = Instant.now();
    Discount discount = newDiscount(now.plusSeconds(3600), now.plusSeconds(7200), 10);
    Instant newStartsAt = now.plusSeconds(1800);
    Instant newEndsAt = now.plusSeconds(9000);
    BigDecimal newSalePrice = new BigDecimal("12.00");

    discount.update(newStartsAt, newEndsAt, 20, newSalePrice);

    assertThat(discount.startsAt()).isEqualTo(newStartsAt);
    assertThat(discount.endsAt()).isEqualTo(newEndsAt);
    assertThat(discount.quantityLimit()).isEqualTo(20);
    assertThat(discount.salePrice()).isEqualByComparingTo(newSalePrice);
  }

  @Test
  void updateRejectsChangingStartsAtOnceStarted() {
    Instant now = Instant.now();
    Discount discount = newDiscount(now.minusSeconds(3600), now.plusSeconds(3600));

    assertThatThrownBy(() -> discount.update(now.minusSeconds(1800), null, null, null))
        .isInstanceOf(DiscountNotEditableException.class);
  }

  @Test
  void updateAllowsResendingTheSameStartsAtOnceStarted() {
    Instant now = Instant.now();
    Instant startsAt = now.minusSeconds(3600);
    Discount discount = newDiscount(startsAt, now.plusSeconds(3600));

    assertThatCode(() -> discount.update(startsAt, null, null, null)).doesNotThrowAnyException();
    assertThat(discount.startsAt()).isEqualTo(startsAt);
  }

  @Test
  void updateAllowsChangingSalePriceWhenActiveWithNoSales() {
    Instant now = Instant.now();
    Discount discount = newDiscount(now.minusSeconds(3600), now.plusSeconds(3600));
    BigDecimal newSalePrice = new BigDecimal("11.00");

    discount.update(null, null, null, newSalePrice);

    assertThat(discount.salePrice()).isEqualByComparingTo(newSalePrice);
  }

  @Test
  void updateRejectsChangingSalePriceWhenActiveWithSales() {
    Instant now = Instant.now();
    Discount discount = reconstituteDiscount(now.minusSeconds(3600), now.plusSeconds(3600), 10, 2);

    assertThatThrownBy(() -> discount.update(null, null, null, new BigDecimal("11.00")))
        .isInstanceOf(DiscountNotEditableException.class);
  }

  @Test
  void updateAllowsResendingTheSameSalePriceWhenActiveWithSales() {
    Instant now = Instant.now();
    Discount discount = reconstituteDiscount(now.minusSeconds(3600), now.plusSeconds(3600), 10, 2);

    assertThatCode(() -> discount.update(null, null, null, SALE_PRICE)).doesNotThrowAnyException();
    assertThat(discount.salePrice()).isEqualByComparingTo(SALE_PRICE);
  }

  @Test
  void updateAllowsRaisingQuantityLimitWhenActiveWithSales() {
    Instant now = Instant.now();
    Discount discount = reconstituteDiscount(now.minusSeconds(3600), now.plusSeconds(3600), 10, 2);

    discount.update(null, null, 15, null);

    assertThat(discount.quantityLimit()).isEqualTo(15);
  }

  @Test
  void updateRejectsChangingEndsAtOnceEnded() {
    Instant now = Instant.now();
    Discount discount = newDiscount(now.minusSeconds(7200), now.minusSeconds(3600));

    assertThatThrownBy(() -> discount.update(null, now.plusSeconds(3600), null, null))
        .isInstanceOf(DiscountNotEditableException.class);
  }

  @Test
  void updateRejectsChangingQuantityLimitOnceEnded() {
    Instant now = Instant.now();
    Discount discount = newDiscount(now.minusSeconds(7200), now.minusSeconds(3600), 10);

    assertThatThrownBy(() -> discount.update(null, null, 20, null))
        .isInstanceOf(DiscountNotEditableException.class);
  }

  @Test
  void updateRejectsChangingSalePriceOnceEnded() {
    Instant now = Instant.now();
    Discount discount = newDiscount(now.minusSeconds(7200), now.minusSeconds(3600));

    assertThatThrownBy(() -> discount.update(null, null, null, new BigDecimal("11.00")))
        .isInstanceOf(DiscountNotEditableException.class);
  }

  @Test
  void updateRejectsEndsAtNotInTheFuture() {
    Instant now = Instant.now();
    Discount discount = newDiscount(now.minusSeconds(3600), now.plusSeconds(3600));

    assertThatThrownBy(() -> discount.update(null, now.minusSeconds(60), null, null))
        .isInstanceOf(DiscountPeriodInvalidException.class);
  }

  @Test
  void updateRejectsQuantityLimitBelowQuantitySold() {
    Instant now = Instant.now();
    Discount discount = reconstituteDiscount(now.minusSeconds(3600), now.plusSeconds(3600), 10, 5);

    assertThatThrownBy(() -> discount.update(null, null, 3, null))
        .isInstanceOf(DiscountLimitBelowSoldException.class);
  }

  @Test
  void updateRejectsSalePriceNotLowerThanOriginalPrice() {
    Instant now = Instant.now();
    Discount discount = newDiscount(now.minusSeconds(3600), now.plusSeconds(3600));

    assertThatThrownBy(() -> discount.update(null, null, null, ORIGINAL_PRICE))
        .isInstanceOf(DiscountPriceNotLowerException.class);
  }

  @Test
  void endRejectsAScheduledDiscount() {
    Instant now = Instant.now();
    Discount discount = newDiscount(now.plusSeconds(3600), now.plusSeconds(7200));

    assertThatThrownBy(discount::end).isInstanceOf(DiscountPeriodInvalidException.class);
  }

  @Test
  void endSucceedsOnAnActiveDiscount() {
    Instant now = Instant.now();
    Discount discount = newDiscount(now.minusSeconds(3600), now.plusSeconds(3600));

    discount.end();

    assertThat(discount.endsAt()).isBeforeOrEqualTo(Instant.now());
  }

  @Test
  void endRejectsAnAlreadyEndedDiscount() {
    Instant now = Instant.now();
    Discount discount = newDiscount(now.minusSeconds(7200), now.minusSeconds(3600));

    assertThatThrownBy(discount::end).isInstanceOf(DiscountPeriodInvalidException.class);
  }

  @Test
  void requireNotStartedAcceptsAScheduledDiscount() {
    Instant now = Instant.now();
    Discount discount = newDiscount(now.plusSeconds(3600), now.plusSeconds(7200));

    assertThatCode(discount::requireNotStarted).doesNotThrowAnyException();
  }

  @Test
  void requireNotStartedRejectsAStartedDiscount() {
    Instant now = Instant.now();
    Discount discount = newDiscount(now.minusSeconds(3600), now.plusSeconds(3600));

    assertThatThrownBy(discount::requireNotStarted).isInstanceOf(DiscountAlreadyStartedException.class);
  }
}
