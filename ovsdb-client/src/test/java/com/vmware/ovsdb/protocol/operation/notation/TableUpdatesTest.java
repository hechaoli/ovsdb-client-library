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

package com.vmware.ovsdb.protocol.operation.notation;

import static org.junit.Assert.assertEquals;


import com.google.common.collect.ImmutableMap;
import com.vmware.ovsdb.jsonrpc.v1.util.JsonUtil;
import com.vmware.ovsdb.protocol.methods.RowUpdate;
import com.vmware.ovsdb.protocol.methods.TableUpdate;
import com.vmware.ovsdb.protocol.methods.TableUpdates;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.junit.Test;

public class TableUpdatesTest {

  @Test
  public void testDeserialization() throws IOException {
    Row old1 = new Row(ImmutableMap.of(
        "name", Atom.string("ls1"),
        "description", Atom.string("First Logical Switch"),
        "tunnel_key", Atom.integer(5001)
    ));

    Row new1 = new Row(ImmutableMap.of(
        "name", Atom.string("ls2"),
        "description", Atom.string("Second Logical Switch"),
        "tunnel_key", Atom.integer(5002)
    ));

    Map<UUID, RowUpdate> rowUpdates1 = ImmutableMap.of(
        UUID.randomUUID(), new RowUpdate(old1, null),
        UUID.randomUUID(), new RowUpdate(null, new1)
    );
    TableUpdate tableUpdate1 = new TableUpdate(rowUpdates1);

    Row old2 = new Row(ImmutableMap.of(
        "name", Atom.string("ps1"),
        "description", Atom.string("First Physical Switch"),
        "tunnel_ips", Atom.string("192.168.1.1")
    ));

    Row new2 = new Row(ImmutableMap.of(
        "name", Atom.string("ps2"),
        "description", Atom.string("Second Physical Switch"),
        "tunnel_ips", Set.of("192.168.1.2")
    ));

    Map<UUID, RowUpdate> rowUpdates2 = ImmutableMap.of(
        UUID.randomUUID(), new RowUpdate(old2, new2)
    );
    TableUpdate tableUpdate2 = new TableUpdate(rowUpdates2);

    TableUpdates expectedResult = new TableUpdates(ImmutableMap.of(
        "Logical_Switch", tableUpdate1,
        "Physical_Switch", tableUpdate2
    ));

    String textTableUpdates = "{\"Logical_Switch\":"
        + JsonUtil.serialize(tableUpdate1.getRowUpdates())
        + "," + "\"Physical_Switch\":" + JsonUtil.serialize(
        tableUpdate2.getRowUpdates()) + "}";

    assertEquals(
        expectedResult,
        JsonUtil.deserialize(textTableUpdates, TableUpdates.class)
    );
  }
}
