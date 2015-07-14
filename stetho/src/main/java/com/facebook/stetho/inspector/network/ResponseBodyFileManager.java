/*
 * Copyright (c) 2014-present, Facebook, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */

package com.facebook.stetho.inspector.network;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import android.content.Context;
import android.util.Base64;
import android.util.Base64OutputStream;

import com.facebook.stetho.common.LogRedirector;
import com.facebook.stetho.common.Util;

/**
 * Manages temporary files created by {@link ChromeHttpFlowObserver} to serve request bodies.
 */
public class ResponseBodyFileManager {
  private static final String TAG = "ResponseBodyFileManager";
  private static final String FILENAME_PREFIX = "network-response-body-";

  private final Context mContext;

  private Map<String, AsyncPrettyPrinter> mRequestIdMap = new HashMap<>();

  public ResponseBodyFileManager(Context context) {
    mContext = context;
  }

  public void cleanupFiles() {
    for (File file : mContext.getFilesDir().listFiles()) {
      if (file.getName().startsWith(FILENAME_PREFIX)) {
        if (!file.delete()) {
          LogRedirector.w(TAG, "Failed to delete " + file.getAbsolutePath());
        }
      }
    }
    LogRedirector.i(TAG, "Cleaned up temporary network files.");
  }

  public ResponseBodyData readFile(String requestId) throws IOException {
    InputStream in = mContext.openFileInput(getFilename(requestId));
    try {
      int firstByte = in.read();
      if (firstByte == -1) {
        throw new EOFException("Failed to read base64Encode byte");
      }
      ResponseBodyData bodyData = new ResponseBodyData();
      bodyData.base64Encoded = firstByte != 0;

      if (mRequestIdMap.containsKey(requestId)) {
        //current response needs to be asynchronously pretty printed
        AsyncPrettyPrinter asyncPrettyPrinter = mRequestIdMap.get(requestId);
        AsyncPrettyPrintingTask prettyPrintingTask = new AsyncPrettyPrintingTask(
            in,
            asyncPrettyPrinter);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<String> future = executor.submit(prettyPrintingTask);
        try {
          bodyData.data = future.get(1, TimeUnit.SECONDS);
        } catch (TimeoutException | InterruptedException | ExecutionException e) {
          future.cancel(true);
          String errorMessage = "Failed to pretty print data\n";
          bodyData.data = errorMessage + readContentsAsUTF8(in);
        }
        executor.shutdownNow();
      } else {
        bodyData.data = readContentsAsUTF8(in);
      }

      return bodyData;
    } finally {
      in.close();
    }
  }

  private static String readContentsAsUTF8(InputStream in) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Util.copy(in, out, new byte[1024]);
    return out.toString("UTF-8");
  }

  public OutputStream openResponseBodyFile(String requestId, boolean base64Encode)
      throws IOException {
    OutputStream out = mContext.openFileOutput(getFilename(requestId), Context.MODE_PRIVATE);
    out.write(base64Encode ? 1 : 0);
    if (base64Encode) {
      return new Base64OutputStream(out, Base64.DEFAULT);
    } else {
      return out;
    }
  }

  private static String getFilename(String requestId) {
    return FILENAME_PREFIX + requestId;
  }

  /**
   * Associates an asynchronous pretty printer with a response request id
   * The pretty printer will be used to pretty print the response body that has
   * the particular request id
   *
   * @param requestId Unique identifier for the response
   * as per {@link NetworkEventReporter.InspectorResponse#requestId()}
   * @param asyncPrettyPrinter Asynchronous Pretty Printer to pretty print the response body
   */
  public void associateAsyncPrettyPrinterWithId(
      String requestId,
      AsyncPrettyPrinter asyncPrettyPrinter) {
    if (mRequestIdMap.containsKey(requestId)) {
      throw new IllegalArgumentException("cannot associate different " +
          "pretty printers with the same request id: "+requestId);
    }
    mRequestIdMap.put(requestId, asyncPrettyPrinter);
  }

  private class AsyncPrettyPrintingTask implements Callable<String> {
    private final InputStream mInputStream;
    private final AsyncPrettyPrinter mAsyncPrettyPrinter;

    public AsyncPrettyPrintingTask(
        InputStream in,
        AsyncPrettyPrinter asyncPrettyPrinter) {
      mInputStream = in;
      mAsyncPrettyPrinter = asyncPrettyPrinter;
    }

    @Override
    public String call() throws IOException {
      return prettyPrintContent(mInputStream, mAsyncPrettyPrinter);
    }

    private String prettyPrintContent(InputStream in, AsyncPrettyPrinter asyncPrettyPrinter)
        throws IOException {
      StringWriter out = new StringWriter();
      PrintWriter writer = new PrintWriter(out);
      asyncPrettyPrinter.printTo(writer, in);
      return out.toString();
    }
  }
}
