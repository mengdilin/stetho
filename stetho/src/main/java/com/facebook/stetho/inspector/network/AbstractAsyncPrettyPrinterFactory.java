package com.facebook.stetho.inspector.network;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;

import com.facebook.stetho.common.Util;
import com.facebook.stetho.inspector.network.AsyncPrettyPrinter;
import com.facebook.stetho.inspector.network.AsyncPrettyPrinterFactory;

/**
 * Abstract class for pretty printer factory that asynchronously downloads schema needed for
 * pretty printing the payload
 */
public abstract class AbstractAsyncPrettyPrinterFactory implements AsyncPrettyPrinterFactory {

  @Override
  public AsyncPrettyPrinter getInstance(String headerName, String headerValue) {
    //TODO: async download schema
    final String schema = "";
    return new AsyncPrettyPrinter() {
      //TODO: pool byte array if this method is used frequently to avoid GC churn
      public void printTo(PrintWriter output, InputStream payload) throws IOException {
        doPrint(output, payload, schema);
      }
    };
  }

  protected abstract void doPrint(PrintWriter output, InputStream payload, String schema)
      throws IOException;
}

