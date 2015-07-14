/*
 * Copyright (c) 2014-present, Facebook, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */

package com.facebook.stetho.inspector.network;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;

/**
 * Interface that callers need to implement in order to pretty print the payload received by Stetho
 */
public interface AsyncPrettyPrinter {
  /**
   * Prints the prettified version of payload to output
   *
   * @param output Writes the prettified version of payload
   * @param payload Response stream that has the raw data to be prettified
   * @throws IOException
   */
  public void printTo(PrintWriter output, InputStream payload) throws IOException;
}
