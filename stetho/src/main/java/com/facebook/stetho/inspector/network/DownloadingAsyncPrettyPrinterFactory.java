package com.facebook.stetho.inspector.network;

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

import javax.annotation.Nullable;

/**
 * Abstract class for pretty printer factory that asynchronously downloads schema needed for
 * pretty printing the payload
 */
public abstract class DownloadingAsyncPrettyPrinterFactory implements AsyncPrettyPrinterFactory {

  private static ExecutorService sExecutorService =
      AsyncPrettyPrinterExecutorHolder.sExecutorService;

  @Override
  public AsyncPrettyPrinter getInstance(String headerName, String headerValue) {
    try {
      final MatchResult result = matchAndParseHeader(headerName, headerValue);
      String uri = result.getSchemaUri();
      final Future<String> response = sExecutorService.submit(new Request(new URL(uri)));
      return new AsyncPrettyPrinter() {

        public void printTo(PrintWriter output, InputStream payload)
            throws IOException {
          try {
            String schema = Util.getUninterruptibly(response);
            doPrint(output, payload, schema);
          } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            ExceptionUtil.propagateIfInstanceOf(cause, IOException.class);
            throw ExceptionUtil.propagate(cause);
          }
        }

        public DownloadingAsyncPrettyPrinterFactory.PrettyPrinterDisplayType getPrettifiedType(){
          return result.getDisplayType();
        }
      };
    } catch (MalformedURLException e) {
      //cannot properly create async pretty printer without a correct url
      return null;
    }
  }

  /**
   * Match the correct header that contains information about the schema uri
   * @param headerName header name of a response that needs to be pretty printed
   * @param headerValue header value which contains the URI for
   * the schema data needed to pretty print the response body
   * @return MatchResult that has the schema uri and the type of prettified result. Null
   * if there is no correct header match.
   */
  @Nullable
  protected abstract MatchResult matchAndParseHeader(String headerName, String headerValue);

  protected abstract void doPrint(PrintWriter output, InputStream payload, String schema)
      throws IOException;

  public enum PrettyPrinterDisplayType {
    JSON("JSON"),
    HTML("Html"),
    TEXT("Text");

    private final String mDisplayType;

    private PrettyPrinterDisplayType(String displayType) {
      mDisplayType = displayType;
    }

    public String getDisplayType() {
      return mDisplayType;
    }
  }

  protected class MatchResult {
    private String mSchemaUri;
    private PrettyPrinterDisplayType mDisplayType;

    public MatchResult(String schemaUri, PrettyPrinterDisplayType displayType) {
      mSchemaUri = schemaUri;
      mDisplayType = displayType;
    }

    public String getSchemaUri() {
      return mSchemaUri;
    }

    public PrettyPrinterDisplayType getDisplayType() {
      return mDisplayType;
    }
  }

  private class Request implements Callable<String> {
    private URL url;

    public Request(URL url) {
      this.url = url;
    }

    @Override
    public String call() throws Exception {
      InputStream urlStream = url.openStream();
      String result = ResponseBodyFileManager.readContentsAsUTF8(urlStream);
      urlStream.close();
      return result;
    }
  }
}

