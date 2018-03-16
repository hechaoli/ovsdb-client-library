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

import static junit.framework.TestCase.assertNotNull;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.vmware.ovsdb.callback.ConnectionCallback;
import com.vmware.ovsdb.callback.MonitorCallback;
import com.vmware.ovsdb.exception.OvsdbClientException;
import com.vmware.ovsdb.jsonrpc.v1.util.JsonUtil;
import com.vmware.ovsdb.protocol.methods.MonitorRequest;
import com.vmware.ovsdb.protocol.methods.MonitorRequests;
import com.vmware.ovsdb.protocol.methods.RowUpdate;
import com.vmware.ovsdb.protocol.methods.TableUpdate;
import com.vmware.ovsdb.protocol.methods.TableUpdates;
import com.vmware.ovsdb.protocol.operation.Delete;
import com.vmware.ovsdb.protocol.operation.Insert;
import com.vmware.ovsdb.protocol.operation.Mutate;
import com.vmware.ovsdb.protocol.operation.Select;
import com.vmware.ovsdb.protocol.operation.Update;
import com.vmware.ovsdb.protocol.operation.notation.Atom;
import com.vmware.ovsdb.protocol.operation.notation.Function;
import com.vmware.ovsdb.protocol.operation.notation.Mutator;
import com.vmware.ovsdb.protocol.operation.notation.NamedUuid;
import com.vmware.ovsdb.protocol.operation.notation.Row;
import com.vmware.ovsdb.protocol.operation.notation.Set;
import com.vmware.ovsdb.protocol.operation.notation.Uuid;
import com.vmware.ovsdb.protocol.operation.notation.Value;
import com.vmware.ovsdb.protocol.operation.result.ErrorResult;
import com.vmware.ovsdb.protocol.operation.result.InsertResult;
import com.vmware.ovsdb.protocol.operation.result.OperationResult;
import com.vmware.ovsdb.protocol.operation.result.SelectResult;
import com.vmware.ovsdb.protocol.operation.result.UpdateResult;
import com.vmware.ovsdb.protocol.schema.DatabaseSchema;
import com.vmware.ovsdb.protocol.util.OvsdbConstant;
import com.vmware.ovsdb.service.impl.OvsdbPassiveConnectionListenerImpl;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.net.ssl.SSLException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class OvsdbClientTest {

    private static final ScheduledExecutorService executorService = Executors
        .newScheduledThreadPool(10);

    private static final OvsdbPassiveConnectionListener passiveConnectionService
        = new OvsdbPassiveConnectionListenerImpl(executorService);

    private static final String HOST = "127.0.0.1";

    // Use port 6641 for testing purpose
    private static final int PORT = 6641;

    private static ConnectionEmulator connectionEmulator;

    private static OvsdbClient ovsdbClient;

    private static int id = 0;

    @BeforeClass
    public static void setUpClass() throws InterruptedException {
        SslContext sslContext = null;
        try {
            SelfSignedCertificate serverCert = new SelfSignedCertificate();
            SelfSignedCertificate clientCert = new SelfSignedCertificate();
            sslContext = SslContextBuilder.forServer(
                serverCert.certificate(), serverCert.privateKey())
                .trustManager(clientCert.certificate())
                .build();

            connectionEmulator = new ConnectionEmulator(
                SslContextBuilder.forClient()
                    .keyManager(
                        clientCert.certificate(), clientCert.privateKey())
                    .trustManager(serverCert.certificate())
                    .build());
        } catch (CertificateException | SSLException e) {
            fail();
        }
        passiveConnectionService
            .startListeningWithSsl(PORT, sslContext, new ConnectionCallback() {
                @Override
                public void connected(OvsdbClient ovsdbClient) {
                    OvsdbClientTest.ovsdbClient = ovsdbClient;
                }

                @Override
                public void disconnected(OvsdbClient ovsdbClient) {
                    OvsdbClientTest.ovsdbClient = null;
                }
            });
        // Wait for the server to start. This is not accurate. Just for testing.
        TimeUnit.SECONDS.sleep(2);

        connectionEmulator.connect(HOST, PORT);

        // Wait for the emulator to connect. This is not accurate. Just for testing.
        TimeUnit.SECONDS.sleep(2);
    }

    @AfterClass
    public static void tearDownClass() {
        passiveConnectionService.stopListening(PORT);
    }

    @Test
    public void testListDatabases() throws OvsdbClientException {
        String expectedRequest = getJsonRequestString(OvsdbConstant.LIST_DBS);
        setupEmulator(expectedRequest, "[\"ovs\",\"hardware_vtep\"]", null);

        CompletableFuture<String[]> f = ovsdbClient.listDatabases();
        assertArrayEquals(new String[]{"ovs", "hardware_vtep"}, f.join());
    }

    @Test
    public void testGetSchema() throws OvsdbClientException, IOException {
        URL url = getClass().getClassLoader().getResource("vtep_schema.json");
        assertNotNull(url);

        File schemaFile = new File(url.getFile());
        String schemaString = new String(
            Files.readAllBytes(schemaFile.toPath()));
        String expectedRequest = getJsonRequestString(
            OvsdbConstant.GET_SCHEMA, "hardware_vtep"
        );
        setupEmulator(expectedRequest, schemaString, null);
        DatabaseSchema expectedResult = JsonUtil.deserialize(
            schemaString, DatabaseSchema.class);
        CompletableFuture<DatabaseSchema> f = ovsdbClient.getSchema("hardware_vtep");

        assertEquals(expectedResult, f.join());
    }

    @Test
    public void testInsertTransact() throws OvsdbClientException {
        UUID uuid = UUID.randomUUID();
        Map<String, Value> columns = ImmutableMap.of(
            "name", Atom.string("ls1"),
            "description", Atom.string("first logical switch"),
            "tunnel_key", Atom.integer(5001)
        );
        Insert insert = new Insert("Logical_Switch", new Row(columns));
        String expectedRequest = getJsonRequestString(OvsdbConstant.TRANSACT,
            "hardware_vtep", insert
        );
        setupEmulator(expectedRequest,
            "[{\"uuid\":[\"uuid\",\"" + uuid + "\"]}]", null
        );

        CompletableFuture<OperationResult[]> f =
            ovsdbClient.transact("hardware_vtep", ImmutableList.of(insert));

        InsertResult expectedResult = new InsertResult(new Uuid(uuid));
        assertArrayEquals(new OperationResult[]{expectedResult}, f.join());
    }

    @Test
    public void testSelectTransact() throws OvsdbClientException {
        Map<String, Value> columns = ImmutableMap.of(
            "name", Atom.string("ls1"),
            "description", Atom.string("first logical switch"),
            "tunnel_key", Atom.integer(5001)
        );

        Select select = new Select("Logical_Switch").where("name", Function.EQUALS, "ls1");
        String expectedRequest = getJsonRequestString(
            OvsdbConstant.TRANSACT, "hardware_vtep", select);

        setupEmulator(expectedRequest,
            "[{\"rows\":[{\"name\":\"ls1\",\"description\":\"first "
                + "logical switch\",\"tunnel_key\":5001}]"
                + "}]", null
        );

        CompletableFuture<OperationResult[]> f =
            ovsdbClient.transact("hardware_vtep", ImmutableList.of(select));

        SelectResult expectedResult = new SelectResult(ImmutableList.of(new Row(columns)));
        assertArrayEquals(new OperationResult[]{expectedResult}, f.join());
    }

    @Test
    public void testUpdateTransact() throws OvsdbClientException {
        Map<String, Value> columns = ImmutableMap.of(
            "name", Atom.string("ls1"),
            "description", Atom.string("first logical switch"),
            "tunnel_key", Atom.integer(5001)
        );
        Update update = new Update(
            "Logical_Switch", new Row(columns))
            .where("tunnel_key", Function.LESS_THAN, 10000);
        String expectedRequest = getJsonRequestString(
            OvsdbConstant.TRANSACT, "hardware_vtep", update);
        setupEmulator(expectedRequest, "[" + "{\"count\":1}" + "]", null);

        CompletableFuture<OperationResult[]> f = ovsdbClient.transact(
            "hardware_vtep", ImmutableList.of(update));

        UpdateResult expectedResult = new UpdateResult(1);
        assertArrayEquals(new OperationResult[]{expectedResult}, f.join());
    }

    @Test
    public void testMutateTransact() throws OvsdbClientException {
        Mutate mutate = new Mutate("Logical_Switch")
            .where("tunnel_key", Function.GREATER_THAN, 5000)
            .mutation("tunnel_key", Mutator.SUM, 3);
        String expectedRequest = getJsonRequestString(OvsdbConstant.TRANSACT,
            "hardware_vtep", mutate
        );
        setupEmulator(expectedRequest, "[" + "{\"count\":3}" + "]", null);

        CompletableFuture<OperationResult[]> f = ovsdbClient
            .transact("hardware_vtep", ImmutableList.of(mutate));

        UpdateResult expectedResult = new UpdateResult(3);
        assertArrayEquals(new OperationResult[]{expectedResult}, f.join());
    }

    @Test
    public void testDeleteTransact() throws OvsdbClientException {
        Delete delete = new Delete("Logical_Switch")
            .where("description", Function.INCLUDES, "something");

        String expectedRequest = getJsonRequestString(OvsdbConstant.TRANSACT,
            "hardware_vtep", delete
        );
        setupEmulator(expectedRequest, "[" + "{\"count\":0}" + "]", null);

        CompletableFuture<OperationResult[]> f = ovsdbClient
            .transact("hardware_vtep", ImmutableList.of(delete));

        UpdateResult expectedResult = new UpdateResult(0);
        assertArrayEquals(new OperationResult[]{expectedResult}, f.join());
    }

    @Test
    public void testMultiOperationTransact() throws OvsdbClientException {
        UUID uuid = UUID.randomUUID();
        Map<String, Value> columns = ImmutableMap.of(
            "name", Atom.string("ls1"),
            "description", Atom.string("first logical switch"),
            "tunnel_key", Atom.integer(5001)
        );
        String uuidName = "insert";
        Insert insert = new Insert(
            "Logical_Switch", new Row(columns), "insert");

        Mutate mutate = new Mutate("Ucast_Macs_Remote")
            .where("MAC", Function.EQUALS, "01:23:45:67:89:ab")
            .mutation("logical_switch", Mutator.INSERT, new NamedUuid(uuidName));

        Delete delete = new Delete("Physical_Switch")
            .where("tunnel_ips", Function.INCLUDES, Set.of("192.168.1.1"));

        String expectedRequest = getJsonRequestString(OvsdbConstant.TRANSACT,
            "hardware_vtep", insert, mutate, delete
        );
        setupEmulator(expectedRequest,
            "[{\"uuid\":[\"uuid\",\"" + uuid + "\"]},"
                + "{\"count\":1},{\"count\":0}]", null
        );

        CompletableFuture<OperationResult[]> f = ovsdbClient.transact(
            "hardware_vtep", ImmutableList.of(insert, mutate, delete));

        OperationResult[] expectedResult = new OperationResult[3];
        expectedResult[0] = new InsertResult(new Uuid(uuid));
        expectedResult[1] = new UpdateResult(1);
        expectedResult[2] = new UpdateResult(0);
        assertArrayEquals(expectedResult, f.join());
    }

    @Test
    public void testTransactErrorResult() throws OvsdbClientException {
        Map<String, Value> columns = ImmutableMap.of(
            "name", Atom.string("ls1"),
            "description", Atom.string("first logical switch"),
            "tunnel_key", Atom.integer(5001)
        );
        String uuidName = "insert";
        Insert insert = new Insert(
            "Logical_Switch", new Row(columns), "insert");

        Mutate mutate = new Mutate("Ucast_Macs_Remote")
            .where("MAC", Function.EQUALS, "01:23:45:67:89:ab")
            .mutation("logical_switch", Mutator.INSERT, new NamedUuid(uuidName));

        Delete delete = new Delete("Physical_Switch")
            .where("tunnel_ips", Function.INCLUDES, Set.of("192.168.1.1"));

        String expectedRequest = getJsonRequestString(OvsdbConstant.TRANSACT,
            "hardware_vtep", insert, mutate, delete
        );
        setupEmulator(expectedRequest,
            "[{\"error\":\"constraint violation\",\"details\":\"ls1 already "
                + "exists\"}, null, null]", null
        );

        CompletableFuture<OperationResult[]> f = ovsdbClient.transact(
            "hardware_vtep", ImmutableList.of(insert, mutate, delete));

        OperationResult[] expectedResult = new OperationResult[3];
        expectedResult[0] = new ErrorResult("constraint violation", "ls1 already exists");

        assertArrayEquals(expectedResult, f.join());
    }

    @Test
    public void testMonitor() throws OvsdbClientException {
        String monitorId = "1";
        MonitorRequest monitorRequest = new MonitorRequest();
        MonitorRequests monitorRequests = new MonitorRequests(
            ImmutableMap.of("Logical_Switch", monitorRequest));

        Map<String, Value> columns = ImmutableMap.of(
            "name", Atom.string("ls1"),
            "description", Atom.string("First logical switch"),
            "tunnel_key", Atom.integer(5001)
        );
        UUID uuid = UUID.randomUUID();
        RowUpdate initRowUpdate = new RowUpdate(null, new Row(columns));

        TableUpdate expectedInitUpdate = new TableUpdate(
            ImmutableMap.of(uuid, initRowUpdate));
        TableUpdates expectedInitUpdates = new TableUpdates(
            ImmutableMap.of("Logical_Switch", expectedInitUpdate));

        String expectedRequest = getJsonRequestString(
            "monitor", "hardware_vtep", monitorId, monitorRequests);

        setupEmulator(
            expectedRequest,
            "{\"Logical_Switch\":{\"" + uuid
                + "\":{\"old\":null,\"new\":{\"name\":\"ls1\","
                + "\"description\":\"First logical switch\","
                + "\"tunnel_key\":5001}}}}",
            null
        );

        MonitorCallback monitorCallback = mock(MonitorCallback.class);
        CompletableFuture<TableUpdates> f = ovsdbClient
            .monitor("hardware_vtep", monitorId, monitorRequests, monitorCallback);

        assertEquals(expectedInitUpdates, f.join());

        // Verify that the monitor callback works
        UUID insertUuid = UUID.randomUUID();
        RowUpdate insertUpdate = new RowUpdate(
            null,
            new Row(ImmutableMap.of(
                "name", Atom.string("ls2"),
                "tunnel_key", Atom.integer(5002)
            ))
        );

        UUID deleteUuid = UUID.randomUUID();
        RowUpdate deleteUpdate = new RowUpdate(
            new Row(ImmutableMap.of(
                "name", Atom.string("ls1"),
                "tunnel_key", Atom.integer(5001)
            )), null
        );

        UUID modifyUuid = UUID.randomUUID();
        RowUpdate modifyUpdate = new RowUpdate(
            new Row(ImmutableMap.of("tunnel_key", Atom.integer(5003))),
            new Row(ImmutableMap.of("tunnel_key", Atom.integer(5004)))
        );

        TableUpdate expectedTableUpdate = new TableUpdate(
            ImmutableMap.of(
                insertUuid, insertUpdate, deleteUuid, deleteUpdate,
                modifyUuid, modifyUpdate
            ));
        TableUpdates expectedTableUpdates = new TableUpdates(
            ImmutableMap.of("Logical_Switch", expectedTableUpdate));

        connectionEmulator.write(
            "{\"method\":\"update\",\"params\":[\"" + monitorId + "\","
                + "{\"Logical_Switch\":" + "{"
                + "\"" + insertUuid + "\":{\"old\":null,"
                + "\"new\":{\"name\":\"ls2\",\"tunnel_key\":5002}},"
                + "\"" + deleteUuid + "\":"
                + "{\"old\":{\"name\":\"ls1\",\"tunnel_key\":5001},"
                + "\"new\":null},"
                + "\"" + modifyUuid + "\":{\"old\":{\"tunnel_key\":5003},"
                + "\"new\":{\"tunnel_key\":5004}}}"
                + "}],\"id\":null}");

        verify(monitorCallback, timeout(1000).times(1)).update(expectedTableUpdates);
    }


    @Test
    public void testCancelMonitor() throws OvsdbClientException {
        String monitorId = "1";
        MonitorRequest monitorRequest = new MonitorRequest();
        MonitorRequests monitorRequests = new MonitorRequests(
            ImmutableMap.of("Logical_Switch", monitorRequest));

        String expectedRequest = getJsonRequestString(
            "monitor", "hardware_vtep", monitorId, monitorRequests);

        setupEmulator(expectedRequest, "{}", null);

        MonitorCallback monitorCallback = mock(MonitorCallback.class);
        CompletableFuture f = ovsdbClient
            .monitor("hardware_vtep", monitorId, monitorRequests, monitorCallback);
        f.join();

        expectedRequest = getJsonRequestString("monitor_cancel", "1");
        setupEmulator(expectedRequest, "{}", null);
        f = ovsdbClient.cancelMonitor(monitorId);
        f.join();
    }


    @Test
    public void testConnectionInfo() {
        OvsdbConnectionInfo connectionInfo = ovsdbClient.getConnectionInfo();
        String localIp = connectionInfo.getLocalAddress().getHostAddress();
        assertEquals("127.0.0.1", localIp);

        String remoteIp = connectionInfo.getLocalAddress().getHostAddress();
        assertEquals("127.0.0.1", remoteIp);
    }

    @Test
    public void testErrorOperation() throws OvsdbClientException {
        Map<String, Value> columns = ImmutableMap.of(
            "name", Atom.string("ls1"),
            "description", Atom.string("first logical switch"),
            "tunnel_key", Atom.integer(5001)
        );
        String uuidName = "insert";
        Insert insert = new Insert(
            "Logical_Switch", new Row(columns), "insert");

        Mutate mutate = new Mutate("Ucast_Macs_Remote")
            .where("MAC", Function.EQUALS, "01:23:45:67:89:ab")
            .mutation("logical_switch", Mutator.INSERT, new NamedUuid(uuidName));

        Delete delete = new Delete("Physical_Switch")
            .where("tunnel_ips", Function.INCLUDES, Set.of("192.168.1.1"));

        String expectedRequest = getJsonRequestString(OvsdbConstant.TRANSACT,
            "hardware_vtep", insert, mutate, delete
        );
        setupEmulator(expectedRequest,
            "[{\"error\":\"resources exhausted\"}, null, null]", null
        );

        CompletableFuture<OperationResult[]> f = ovsdbClient.transact(
            "hardware_vtep", ImmutableList.of(insert, mutate, delete));

        // After the first error, following results are all null
        OperationResult[] expectedResult = new OperationResult[3];
        expectedResult[0] = new ErrorResult("resources exhausted", null);
        assertArrayEquals(expectedResult, f.join());
    }

    private void setupEmulator(
        String request, String result, String error
    ) {
        connectionEmulator.registerReadCallback(msg -> {
            if (msg.equals(request)) {
                connectionEmulator.write(
                    "{\"id\":\"" + (id++) + "\", \"result\":" + result + ", "
                        + "\"error\":" + error + "}");
            }
        });
    }

    private String getJsonRequestString(String method, Object... params) {
        // Need to remove space between each two params
        String strParams = Arrays.stream(params).map(
            JsonUtil::serializeNoException).collect(
            Collectors.toList()).toString().replace(", ", ",");
        return "{\"method\":\"" + method
            + "\",\"params\":" + strParams + ",\"id\":\"" + id + "\"}";
    }
}