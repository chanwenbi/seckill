/*
 *   Copyright 2017 Huawei Technologies Co., Ltd
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package io.servicecomb.poc.demo.seckill;

import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.servicecomb.poc.demo.seckill.entities.PromotionEntity;
import io.servicecomb.poc.demo.seckill.entities.EventEntity;
import io.servicecomb.poc.demo.seckill.event.CouponGrabbedEvent;
import io.servicecomb.poc.demo.seckill.event.SecKillEventFormat;
import io.servicecomb.poc.demo.seckill.event.PromotionFinishEvent;
import io.servicecomb.poc.demo.seckill.event.PromotionStartEvent;
import io.servicecomb.poc.demo.seckill.json.JacksonGeneralFormat;
import io.servicecomb.poc.demo.seckill.repositories.spring.SpringSecKillEventRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class SecKillRecoveryServiceTest {

  private final PromotionEntity unpublishedPromotion = new PromotionEntity(new Date(), 5, 0.7f);
  private final PromotionEntity runningPromotion = new PromotionEntity(new Date(), 5, 0.7f);
  private final PromotionEntity endedPromotion = new PromotionEntity(new Date(), 5, 0.7f);

  private SpringSecKillEventRepository repository = mock(SpringSecKillEventRepository.class);

  private final JacksonGeneralFormat jsonFormat = new JacksonGeneralFormat();
  private final SecKillEventFormat eventFormat = new SecKillEventFormat(jsonFormat);

  private SecKillRecoveryService<String> recoveryService = new SecKillRecoveryService<>(repository, eventFormat);

  @Before
  public void setup() {
    when(repository.findByPromotionId(unpublishedPromotion.getPromotionId()))
        .thenReturn(Collections.emptyList());

    List<EventEntity> runningPromotionEvents = new ArrayList<>();
    runningPromotionEvents.add(eventFormat.toEntity(new PromotionStartEvent(runningPromotion)));
    runningPromotionEvents.add(eventFormat.toEntity(new CouponGrabbedEvent<>(runningPromotion, "zyy")));
    when(repository.findByPromotionId(runningPromotion.getPromotionId()))
        .thenReturn(runningPromotionEvents);

    List<EventEntity> endedPromotionEvents = new ArrayList<>();
    endedPromotionEvents.add(eventFormat.toEntity(new PromotionStartEvent(endedPromotion)));
    for (int i = 0; i < endedPromotion.getNumberOfCoupons(); i++) {
      endedPromotionEvents.add(eventFormat.toEntity(new CouponGrabbedEvent<>(endedPromotion, String.valueOf(i))));
    }
    endedPromotionEvents.add(eventFormat.toEntity(new PromotionFinishEvent(endedPromotion)));
    when(repository.findByPromotionId(endedPromotion.getPromotionId()))
        .thenReturn(endedPromotionEvents);
  }

  @Test
  public void unstartPromotionCheck() {
    SecKillRecoveryCheckResult<String> result = recoveryService.check(unpublishedPromotion);
    assertThat(result.isStarted(), is(false));
    assertThat(result.isFinished(), is(false));
    assertThat(result.remainingCoupons(), is(unpublishedPromotion.getNumberOfCoupons()));
    assertThat(result.getClaimedCustomers().isEmpty(), is(true));
  }

  @Test
  public void recoverPromotionCheck() {
    SecKillRecoveryCheckResult<String> result = recoveryService.check(runningPromotion);
    assertThat(result.isStarted(), is(true));
    assertThat(result.isFinished(), is(false));
    assertThat(result.remainingCoupons(), is(runningPromotion.getNumberOfCoupons() - 1));
    assertThat(result.getClaimedCustomers(), contains("zyy"));
  }

  @Test
  public void finishPromotionCheck() {
    SecKillRecoveryCheckResult<String> result = recoveryService.check(endedPromotion);
    assertThat(result.isStarted(), is(true));
    assertThat(result.isFinished(), is(true));
    assertThat(result.remainingCoupons(), is(0));
    assertThat(result.getClaimedCustomers(), contains("0", "1", "2", "3", "4"));
  }
}
