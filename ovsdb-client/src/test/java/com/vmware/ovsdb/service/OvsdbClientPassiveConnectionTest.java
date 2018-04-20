/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the BSD-2 license (the "License").
 * You may not use this product except in compliance with the BSD-2 License.
 *
 * This product may include a number of subcomponents with separate copyright
 * notices and license terms. Your use of these subcomponents is subject to the
 * terms and conditions of the subcomponent's license, as noted in the LICENSE
 * file.
 *
 * SPDX-License-Identifier: BSD-2-Clause
 */

package com.vmware.ovsdb.service;

import com.vmware.ovsdb.exception.OvsdbClientException;
import com.vmware.ovsdb.service.impl.OvsdbPassiveConnectionListenerImpl;
import com.vmware.ovsdb.utils.ActiveOvsdbServerEmulator;
import io.netty.handler.ssl.SslContext;
import org.junit.After;
import org.junit.Test;

import java.io.IOException;

public class OvsdbClientPassiveConnectionTest extends OvsdbClientTest {

  private static final OvsdbPassiveConnectionListener passiveListener
      = new OvsdbPassiveConnectionListenerImpl(executorService);

  private static final ActiveOvsdbServerEmulator activeOvsdbServer =
      new ActiveOvsdbServerEmulator(HOST, PORT);

  public OvsdbClientPassiveConnectionTest() {
    super(activeOvsdbServer);
  }

  @After
  public void tearDown() {
    activeOvsdbServer.disconnect().join();
    passiveListener.stopListening(PORT);
  }

  @Override
  void setUp(boolean withSsl) {
    if (!withSsl) {
      passiveListener.startListening(PORT, connectionCallback);
      activeOvsdbServer.connect().join();
    } else {
      // In passive connection test, the controller is the server and the ovsdb-server is the client
      SslContext serverSslCtx = sslContextPair.getServerSslCtx();
      SslContext clientSslCtx = sslContextPair.getClientSslCtx();
      passiveListener.startListeningWithSsl(PORT, serverSslCtx, connectionCallback);
      activeOvsdbServer.connectWithSsl(clientSslCtx).join();
    }
  }

  @Test(timeout = TEST_TIMEOUT_MILLIS)
  public void testTcpConnection()
      throws OvsdbClientException, IOException {
    super.testTcpConnection();
  }

  @Test(timeout = TEST_TIMEOUT_MILLIS)
  public void testSslConnection() throws OvsdbClientException, IOException {
    super.testSslConnection();
  }
}
