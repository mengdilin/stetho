package com.facebook.stetho.inspector.network;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import com.facebook.stetho.common.ExceptionUtil;
import com.facebook.stetho.common.Util;

import javax.annotation.Nullable;

/**
 * Abstract class for pretty printer factory that asynchronously downloads schema needed for
 * pretty printing the payload
 */
public abstract class DownloadingAsyncPrettyPrinterFactory implements AsyncPrettyPrinterFactory {

  private final static ExecutorService sExecutorService =
      AsyncPrettyPrinterExecutorHolder.sExecutorService;

  @Override
  public AsyncPrettyPrinter getInstance(final String headerName, final String headerValue) {

    final MatchResult result = matchAndParseHeader(headerName, headerValue);
    String uri = result.getSchemaUri();
    URL schemaURL = parseURL(uri);
    if (schemaURL == null) {
      return getErrorAsyncPrettyPrinter(headerName, headerValue);
    } else {
      final Future<String> response = sExecutorService.submit(new Request(schemaURL));
      return new AsyncPrettyPrinter() {
        public void printTo(PrintWriter output, InputStream payload)
            throws IOException {
          try {
            String schema = response.get();
            if (schema != null) {
              doPrint(output, payload, schema);
            } else {
              doErrorPrint(
                  output,
                  payload,
                  "http get request for schema url fails to return a valid response");
            }
          } catch (InterruptedException e) {
            doErrorPrint(
                output,
                payload,
                "Error while downloading schema for pretty printing: " + e.getMessage());
          } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            ExceptionUtil.propagateIfInstanceOf(cause, IOException.class);
            throw ExceptionUtil.propagate(cause);
          }
        }
        public DownloadingAsyncPrettyPrinterFactory.PrettyPrinterDisplayType getPrettifiedType() {
          return result.getDisplayType();
        }
      };
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

  @Nullable
  private static URL parseURL(String uri) {
    try {
      return new URL(uri);
    } catch (MalformedURLException e) {
      return null;
    }
  }

  private static void doErrorPrint(PrintWriter output, InputStream payload, String errorMessage)
    throws IOException {
    output.print(errorMessage + "\n" + Util.readAsUTF8(payload));
  }

  private static AsyncPrettyPrinter getErrorAsyncPrettyPrinter(
      final String headerName,
      final String headerValue) {
    return new AsyncPrettyPrinter() {
      @Override
      public void printTo(PrintWriter output, InputStream payload) throws IOException {
        String errorMessage = "[Failed to parse header: "
            + headerName + " : " + headerValue + " ]";
        doErrorPrint(output, payload, errorMessage);
      }

      @Override
      public PrettyPrinterDisplayType getPrettifiedType() {
        return PrettyPrinterDisplayType.TEXT;
      }
    };
  }


  public enum PrettyPrinterDisplayType {
    JSON("Json"),
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

    @Override @Nullable
    public String call() {
      try {
        InputStream urlStream = url.openStream();
        String result = Util.readAsUTF8(urlStream);
        urlStream.close();
        return result;
      }
      catch (IOException e) {
        return null;
      }
    }
  }
}

