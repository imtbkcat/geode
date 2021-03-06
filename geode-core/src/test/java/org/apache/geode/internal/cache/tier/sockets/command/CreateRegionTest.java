/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.geode.internal.cache.tier.sockets.command;

import static org.apache.geode.cache.Region.SEPARATOR;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import org.apache.geode.CancelCriterion;
import org.apache.geode.cache.AttributesFactory;
import org.apache.geode.internal.cache.InternalCache;
import org.apache.geode.internal.cache.LocalRegion;
import org.apache.geode.internal.cache.tier.sockets.CacheServerStats;
import org.apache.geode.internal.cache.tier.sockets.Message;
import org.apache.geode.internal.cache.tier.sockets.Part;
import org.apache.geode.internal.cache.tier.sockets.ServerConnection;
import org.apache.geode.internal.security.AuthorizeRequest;
import org.apache.geode.internal.security.SecurityService;
import org.apache.geode.internal.serialization.Version;
import org.apache.geode.security.NotAuthorizedException;
import org.apache.geode.security.ResourcePermission.Operation;
import org.apache.geode.security.ResourcePermission.Resource;
import org.apache.geode.test.junit.categories.ClientServerTest;

@Category({ClientServerTest.class})
public class CreateRegionTest {

  private static final String REGION_NAME = "region1";
  private static final String PARENT_REGION_NAME = "parent-region";

  @Mock
  private SecurityService securityService;
  @Mock
  private Message message;
  @Mock
  private ServerConnection serverConnection;
  @Mock
  private AuthorizeRequest authzRequest;
  @Mock
  private LocalRegion region;
  @Mock
  private LocalRegion parentRegion;
  @Mock
  private InternalCache cache;
  @Mock
  private Message errorResponseMessage;
  @Mock
  private Message responseMessage;
  @Mock
  private Part regionNamePart;
  @Mock
  private Part parentRegionNamePart;
  @InjectMocks
  private CreateRegion createRegion;

  @Before
  public void setUp() throws Exception {
    this.createRegion = new CreateRegion();
    MockitoAnnotations.initMocks(this);

    when(this.cache.getRegion(eq(PARENT_REGION_NAME))).thenReturn(this.parentRegion);
    when(this.cache.getCancelCriterion()).thenReturn(mock(CancelCriterion.class));

    when(this.message.getPart(eq(0))).thenReturn(this.parentRegionNamePart);
    when(this.message.getPart(eq(1))).thenReturn(this.regionNamePart);

    when(this.parentRegion.getAttributes()).thenReturn(new AttributesFactory().create());

    when(this.parentRegionNamePart.getCachedString()).thenReturn(PARENT_REGION_NAME);

    when(this.regionNamePart.getCachedString()).thenReturn(REGION_NAME);

    when(this.serverConnection.getCache()).thenReturn(this.cache);
    when(this.serverConnection.getAuthzRequest()).thenReturn(this.authzRequest);
    when(this.serverConnection.getCacheServerStats()).thenReturn(mock(CacheServerStats.class));
    when(this.serverConnection.getReplyMessage()).thenReturn(this.responseMessage);
    when(this.serverConnection.getErrorResponseMessage()).thenReturn(this.errorResponseMessage);
    when(this.serverConnection.getClientVersion()).thenReturn(Version.CURRENT);
  }

  @Test
  public void noSecurityShouldSucceed() throws Exception {
    when(this.securityService.isClientSecurityRequired()).thenReturn(false);

    this.createRegion.cmdExecute(this.message, this.serverConnection, this.securityService, 0);

    verify(this.responseMessage).send(this.serverConnection);
  }

  @Test
  public void integratedSecurityShouldSucceedIfAuthorized() throws Exception {
    // arrange
    when(this.securityService.isClientSecurityRequired()).thenReturn(true);
    when(this.securityService.isIntegratedSecurity()).thenReturn(true);

    // act
    this.createRegion.cmdExecute(this.message, this.serverConnection, this.securityService, 0);

    // assert
    verify(this.securityService).authorize(Resource.DATA, Operation.MANAGE);
    verify(this.responseMessage).send(this.serverConnection);
  }

  @Test
  public void integratedSecurityShouldFailIfNotAuthorized() throws Exception {
    when(this.securityService.isClientSecurityRequired()).thenReturn(true);
    when(this.securityService.isIntegratedSecurity()).thenReturn(true);
    doThrow(new NotAuthorizedException("")).when(this.securityService).authorize(Resource.DATA,
        Operation.MANAGE);

    this.createRegion.cmdExecute(this.message, this.serverConnection, this.securityService, 0);

    verify(this.securityService).authorize(Resource.DATA, Operation.MANAGE);
    verify(this.errorResponseMessage).send(eq(this.serverConnection));
  }

  @Test
  public void oldSecurityShouldSucceedIfAuthorized() throws Exception {
    when(this.securityService.isClientSecurityRequired()).thenReturn(true);
    when(this.securityService.isIntegratedSecurity()).thenReturn(false);

    this.createRegion.cmdExecute(this.message, this.serverConnection, this.securityService, 0);

    verify(this.authzRequest)
        .createRegionAuthorize(eq(PARENT_REGION_NAME + SEPARATOR + REGION_NAME));
    verify(this.responseMessage).send(this.serverConnection);
  }

  @Test
  public void oldSecurityShouldFailIfNotAuthorized() throws Exception {
    when(this.securityService.isClientSecurityRequired()).thenReturn(true);
    when(this.securityService.isIntegratedSecurity()).thenReturn(false);
    doThrow(new NotAuthorizedException("")).when(this.authzRequest)
        .createRegionAuthorize(eq(PARENT_REGION_NAME + SEPARATOR + REGION_NAME));

    this.createRegion.cmdExecute(this.message, this.serverConnection, this.securityService, 0);

    verify(this.authzRequest)
        .createRegionAuthorize(eq(PARENT_REGION_NAME + SEPARATOR + REGION_NAME));
    verify(this.errorResponseMessage).send(eq(this.serverConnection));
  }

}
