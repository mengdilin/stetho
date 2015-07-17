package com.facebook.stetho.inspector.network;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.facebook.stetho.common.ExceptionUtil;
import com.facebook.stetho.common.Util;
import com.facebook.stetho.inspector.network.AsyncPrettyPrinter;
import com.facebook.stetho.inspector.network.AsyncPrettyPrinterFactory;

import javax.annotation.Nullable;

/**
 * Abstract class for pretty printer factory that asynchronously downloads schema needed for
 * pretty printing the payload
 */
public abstract class AbstractAsyncPrettyPrinterFactory implements AsyncPrettyPrinterFactory {

  private static ExecutorService sExecutorService = Executors.newFixedThreadPool(1);

  @Override
  public AsyncPrettyPrinter getInstance(String headerName, String headerValue) {
    try {
      String uri = parseHeaderValueToUri(headerValue);
      final Future<Response> response = sExecutorService.submit(new Request(new URL(uri)));
      return new AsyncPrettyPrinter() {
        public void printTo(PrintWriter output, InputStream payload)
            throws IOException {
          try {
            Response schemaResponse = Util.getUninterruptibly(response);
            doPrint(output, payload, schemaResponse.getBody());
          } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            ExceptionUtil.propagateIfInstanceOf(cause, IOException.class);
            throw ExceptionUtil.propagate(cause);
          }
        }
      };
    } catch (MalformedURLException e) {
      //cannot properly create async pretty printer without a correct url
      return null;
    }
  }

  @Nullable
  protected abstract String parseHeaderValueToUri(String headerValue);

  protected abstract void doPrint(PrintWriter output, InputStream payload, String schema)
      throws IOException;

  public class Request implements Callable<Response> {
    private URL url;

    public Request(URL url) {
      this.url = url;
    }

    @Override
    public Response call() throws Exception {
      return new Response(url.openStream());
    }
  }

  public class Response {
    private InputStream body;

    public Response(InputStream body) {
      this.body = body;
    }

    //TODO: pool byte array if this method is used frequently to avoid GC churn
    public String getBody() throws IOException {
      return ResponseBodyFileManager.readContentsAsUTF8(body);
    }
  }
}

