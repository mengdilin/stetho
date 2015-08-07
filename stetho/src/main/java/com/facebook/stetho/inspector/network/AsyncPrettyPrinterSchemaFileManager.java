/*
 * Copyright (c) 2014-present, Facebook, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */

package com.facebook.stetho.inspector.network;

import android.content.Context;
import com.facebook.stetho.common.LogRedirector;
import com.facebook.stetho.common.Util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class AsyncPrettyPrinterSchemaFileManager {
  private static final String TAG = "AsyncPrettyPrinterSchemaCacheManager";
  private static final String FILENAME_PREFIX = "async-pretty-printer-schema-";
  private static int cacheFileCounter = 0;
  private final Map<URL,String> mFilenameMap = Collections.synchronizedMap(
      new HashMap<URL,String>());

  private final Context mContext;

  public AsyncPrettyPrinterSchemaFileManager(Context context) {
    mContext = context;
  }

  public synchronized OutputStream openNewSchemaCache(URL schemaUrl)
      throws IOException {
    String filename = FILENAME_PREFIX + cacheFileCounter++;
    mFilenameMap.put(schemaUrl, filename);
    return mContext.openFileOutput(filename, Context.MODE_PRIVATE);
  }

  public void cleanupFiles() {
    for (File file : mContext.getFilesDir().listFiles()) {
      if (file.getName().startsWith(FILENAME_PREFIX)) {
        if (!file.delete()) {
          LogRedirector.w(TAG, "Failed to delete " + file.getAbsolutePath());
        }
      }
    }
    LogRedirector.i(TAG, "Cleaned up cache pretty printing schema files.");
  }

  public synchronized String readCache(URL schemaUrl) throws IOException {
    String filename = mFilenameMap.get(schemaUrl);
    if (filename != null) {
      InputStream in = mContext.openFileInput(filename);
      String cacheSchema = Util.readAsUTF8(in);
      in.close();
      return cacheSchema;
    }
    return null;
  }
}
