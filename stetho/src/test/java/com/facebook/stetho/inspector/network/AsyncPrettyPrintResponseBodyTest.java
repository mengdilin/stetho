/*
 * Copyright (c) 2014-present, Facebook, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */

package com.facebook.stetho.inspector.network;

import com.facebook.stetho.common.Util;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Matchers.any;

import com.facebook.stetho.inspector.network.NetworkEventReporter;
import com.facebook.stetho.inspector.protocol.module.Network;
import com.facebook.stetho.inspector.protocol.module.Page;
import org.junit.Before;
import org.junit.Test;

import javax.annotation.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.Override;
import java.lang.String;
import java.util.ArrayList;
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
    mNetworkPeerManager = new NetworkPeerManager(mResponseBodyFileManager);
    mAsyncPrettyPrinterRegistry = mNetworkPeerManager.getAsyncPrettyPrinterRegistry();
    mAsyncPrettyPrinterRegistry.register(TEST_HEADER_NAME, mPrettyPrinterTestFactory);
  }

  @Test
  public void testAsyncPrettyPrinterResult() throws IOException {
    StringWriter out = new StringWriter();
    PrintWriter writer = new PrintWriter(out);
    AsyncPrettyPrinter mAsyncPrettyPrinter = mPrettyPrinterTestFactory.getInstance(
        TEST_HEADER_NAME,
        TEST_HEADER_VALUE);
    mAsyncPrettyPrinter.printTo(writer, mInputStream);
    assertEquals(PRETTY_PRINT_PREFIX + Arrays.toString(TEST_RESPONSE_BODY), out.toString());
  }

  @Test
  public void testInitAsyncPrettyPrinterForResponseWithRegisteredHeader() {
    ArrayList<String> headerNames = new ArrayList<String>();
    ArrayList<String> headerValues = new ArrayList<String>();

    headerNames.add("unregistered header name 1");
    headerNames.add("unregistered header name 2");
    headerNames.add(TEST_HEADER_NAME);
    headerValues.add("unregistered header value 1");
    headerValues.add("unregistered header value 2");
    headerValues.add(TEST_HEADER_VALUE);

    TestInspectorResponse testResponse = new TestInspectorResponse(
        headerNames,
        headerValues,
        TEST_REQUEST_ID
    );
    Network.ResponseReceivedParams receivedParams = new Network.ResponseReceivedParams();
    NetworkEventReporterImpl.initAsyncPrettyPrinterForResponse(
        testResponse,
        mNetworkPeerManager,
        receivedParams);
    verify(mResponseBodyFileManager, times(1)).associateAsyncPrettyPrinterWithId(
        eq(TEST_REQUEST_ID),
        any(AsyncPrettyPrinter.class)
    );
  }

  @Test
  public void testInitAsyncPrettyPrinterForResponseWithUnregisteredHeader() {
    ArrayList<String> headerNames = new ArrayList<String>();
    ArrayList<String> headerValues = new ArrayList<String>();

    headerNames.add("unregistered header name 1");
    headerNames.add("unregistered header name 2");
    headerNames.add("unregistered header name 3");
    headerValues.add("unregistered header value 1");
    headerValues.add("unregistered header value 2");
    headerValues.add("unregistered header value 3");

    TestInspectorResponse testResponse = new TestInspectorResponse(
        headerNames,
        headerValues,
        TEST_REQUEST_ID
    );
    Network.ResponseReceivedParams receivedParams = new Network.ResponseReceivedParams();
    NetworkEventReporterImpl.initAsyncPrettyPrinterForResponse(
        testResponse,
        mNetworkPeerManager,
        receivedParams);
    verify(mResponseBodyFileManager, never()).associateAsyncPrettyPrinterWithId(
        eq(TEST_REQUEST_ID),
        any(AsyncPrettyPrinter.class)
    );
  }

  private class PrettyPrinterTestFactory extends AbstractAsyncPrettyPrinterFactory {
    @Override
    protected void doPrint(PrintWriter output, InputStream payload, String schema)
        throws IOException {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      Util.copy(payload, out, new byte[1024]);
      String prettifiedContent = PRETTY_PRINT_PREFIX + Arrays.toString(out.toByteArray());
      output.write(prettifiedContent);
      output.close();
    }

    @Override @Nullable
    protected String parseHeaderValueToUri(String headerName, String headerValue) {
      return "https://www.facebook.com";
    }

    @Override
    public Page.ResourceType getPrettifiedType() {
      return Page.ResourceType.OTHER;
    }
  }

  private class TestInspectorResponse implements NetworkEventReporter.InspectorResponse {
    private ArrayList<String> mHeaderNames;
    private ArrayList<String> mHeaderValues;
    private String mRequestId;

    public TestInspectorResponse(
        ArrayList<String> headerNames,
        ArrayList<String> headerValues,
        String requestId) {
      mHeaderNames = headerNames;
      mHeaderValues = headerValues;
      mRequestId = requestId;
    }

    public int headerCount() {
      return mHeaderNames.size();
    }

    public String headerName(int index) {
      return mHeaderNames.get(index);
    }

    public String headerValue(int index) {
      return mHeaderValues.get(index);
    }

    @Nullable
    public String firstHeaderValue(String name) {
      return mHeaderValues.get(0);
    }

    public String requestId() {
      return mRequestId;
    }

    public String url() {
      return "test url";
    }

    public int statusCode() {
      return 200;
    }

    public String reasonPhrase() {
      return "test reason phrase";
    }

    public boolean connectionReused() {
      return false;
    }

    public int connectionId() {
      return 111;
    }

    public boolean fromDiskCache() {
      return false;
    }
  }

  /**
   * Returns the truncated byte value of position.
   */
  private static byte positionToByte(int position) {
    return (byte) (position % 0xff);
  }
}
