package com.facebook.stethointernal.chrome.network;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;

import com.facebook.stetho.common.Util;
import com.facebook.stetho.inspector.network.AsyncPrettyPrinter;
import com.facebook.stetho.inspector.network.AsyncPrettyPrinterFactory;

/**
 * Implementation of {@link AsyncPrettyPrinterFactory} which generates asynchronous pretty printers
 * that allows callers to pretty print responses with specific header names
 */
public abstract class BasePrettyPrinterFactory implements AsyncPrettyPrinterFactory {

  @Override
  public AsyncPrettyPrinter getInstance(String headerName, String headerValue) {
    //TODO: async download schema
    final String schema = "";
    return new AsyncPrettyPrinter() {
      //TODO: pool byte array if this method is used frequently to avoid GC churn
      public void printTo(PrintWriter output, InputStream payload) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Util.copy(payload, out, new byte[1024]);
        output.write(doPrint(out.toByteArray(), schema));
        output.close();
      }
    };
  }

  protected abstract String doPrint(byte[] payload, String schema);

}
