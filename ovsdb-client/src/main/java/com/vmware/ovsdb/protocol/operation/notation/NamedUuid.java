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

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.vmware.ovsdb.protocol.operation.notation.deserializer.NamedUuidDeserializer;
import com.vmware.ovsdb.protocol.util.OvsdbConstant;

import java.util.Objects;

/**
 * Representation of {@literal <named-uuid>}.
 *
 * <pre>
 * {@literal
 * <named-uuid>
 *   A 2-element JSON array that represents the UUID of a row inserted
 *   in an "insert" operation within the same transaction.  The first
 *   element of the array must be the string "named-uuid", and the
 *   second element should be the <id> specified as the "uuid-name" for
 *   an "insert" operation within the same transaction.  For example,
 *   if an "insert" operation within this transaction specifies a
 *   "uuid-name" of "myrow", the following <named-uuid> represents the
 *   UUID created by that operation:
 *
 *   ["named-uuid", "myrow"]
 *
 *   A <named-uuid> may be used anywhere a <uuid> is valid.  This
 *   enables a single transaction to both insert a new row and then
 *   refer to that row using the "uuid-name" that was associated with
 *   that row when it was inserted.  Note that the "uuid-name" is only
 *   meaningful within the scope of a single transaction.
 * }
 * </pre>
 */
@JsonDeserialize(using = NamedUuidDeserializer.class)
@JsonFormat(shape = JsonFormat.Shape.ARRAY)
public class NamedUuid {

  public final String namedUuidString = OvsdbConstant.NAMED_UUID; // For serializing

  private final String uuidName;

  public NamedUuid(String uuidName) {
    this.uuidName = uuidName;
  }

  public String getUuidName() {
    return uuidName;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof NamedUuid)) {
      return false;
    }
    NamedUuid that = (NamedUuid) other;
    return Objects.equals(uuidName, that.uuidName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(namedUuidString, uuidName);
  }

  @Override
  public String toString() {
    return uuidName;
  }
}
