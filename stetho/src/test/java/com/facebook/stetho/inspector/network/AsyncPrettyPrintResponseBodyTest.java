/*
 * Copyright (c) 2014-present, Facebook, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */

package com.facebook.stetho.inspector.network;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Matchers.any;

import com.facebook.stetho.inspector.network.AsyncPrettyPrinter;
import com.facebook.stetho.inspector.network.AsyncPrettyPrinterRegistry;
import com.facebook.stetho.inspector.network.ResponseBodyFileManager;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.Override;
import java.lang.String;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class AsyncPrettyPrintResponseBodyTest {

  private static final String TEST_REQUEST_ID = "1234";
  private static final String TEST_HEADER_NAME = "header name";
  private static final String TEST_HEADER_VALUE = "header value";
  private static final String PRETTY_PRINT_PREFIX = "pretty printed result: ";
  private static final byte[] TEST_RESPONSE_BODY;
  private static final ByteArrayInputStream mInputStream;

  static {
    int responseBodyLength = 4096 * 2 + 2048; // span multiple buffers when tee-ing
    TEST_RESPONSE_BODY = new byte[responseBodyLength];
    for (int i = 0; i < responseBodyLength; i++) {
      TEST_RESPONSE_BODY[i] = positionToByte(i);
    }
    mInputStream = new ByteArrayInputStream(TEST_RESPONSE_BODY);
  }

  private AsyncPrettyPrinterRegistry mAsyncPrettyPrinterRegistry;
  private PrettyPrinterTestFactory mPrettyPrinterTestFactory;
  private NetworkPeerManager mNetworkPeerManager;
  private ResponseBodyFileManager mResponseBodyFileManager;

  @Before
  public void setup() {
    mPrettyPrinterTestFactory = new PrettyPrinterTestFactory();
    mResponseBodyFileManager = mock(ResponseBodyFileManager.class);
    mAsyncPrettyPrinterRegistry = new AsyncPrettyPrinterRegistry();
    mAsyncPrettyPrinterRegistry.register(TEST_HEADER_NAME, mPrettyPrinterTestFactory);
    mNetworkPeerManager = new NetworkPeerManager(
        mResponseBodyFileManager,
        mAsyncPrettyPrinterRegistry);
  }

  @Test
  public void testAsyncPrettyPrinterResult() {
    try {
      StringWriter out = new StringWriter();
      PrintWriter writer = new PrintWriter(out);
      AsyncPrettyPrinter mAsyncPrettyPrinter = mPrettyPrinterTestFactory.getInstance(
          TEST_HEADER_NAME,
          TEST_HEADER_VALUE);
      mAsyncPrettyPrinter.printTo(writer, mInputStream);
      assertEquals(PRETTY_PRINT_PREFIX + Arrays.toString(TEST_RESPONSE_BODY), out.toString());
    }
    catch (IOException e) {
      fail(e.getMessage());
    }
  }

  @Test
  public void testInitAsyncPrettyPrinterForResponseWithRegisteredHeader() {
    NetworkEventReporter.InspectorResponse mockResponse = mock(
        NetworkEventReporter.InspectorResponse.class);
    when(mockResponse.requestId()).thenReturn(TEST_REQUEST_ID);
    when(mockResponse.headerCount()).thenReturn(1);
    when(mockResponse.headerName(0)).thenReturn(TEST_HEADER_NAME);
    when(mockResponse.headerValue(0)).thenReturn(TEST_HEADER_VALUE);

    NetworkEventReporterImpl.initAsyncPrettyPrinterForResponse(mockResponse, mNetworkPeerManager);
    verify(mResponseBodyFileManager, times(1)).associateAsyncPrettyPrinterWithId(
        eq(TEST_REQUEST_ID),
        any(AsyncPrettyPrinter.class)
    );
  }

  @Test
  public void testInitAsyncPrettyPrinterForResponseWithUnregisteredHeader() {
    NetworkEventReporter.InspectorResponse mockResponse = mock(
        NetworkEventReporter.InspectorResponse.class);
    when(mockResponse.requestId()).thenReturn(TEST_REQUEST_ID);
    when(mockResponse.headerCount()).thenReturn(1);
    when(mockResponse.headerName(0)).thenReturn("unregistered header name");
    when(mockResponse.headerValue(0)).thenReturn(TEST_HEADER_VALUE);

    NetworkEventReporterImpl.initAsyncPrettyPrinterForResponse(mockResponse, mNetworkPeerManager);
    verify(mResponseBodyFileManager, never()).associateAsyncPrettyPrinterWithId(
        any(String.class),
        any(AsyncPrettyPrinter.class)
    );
  }

  private class PrettyPrinterTestFactory extends BasePrettyPrinterFactory {
    @Override
    protected String doPrint(byte[] payload, String schema) {
      return PRETTY_PRINT_PREFIX + Arrays.toString(payload);
    }
  }


  /**
   * Returns the truncated byte value of position.
   */
  private static byte positionToByte(int position) {
    return (byte) (position % 0xff);
  }
}