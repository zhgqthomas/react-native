/**
 * Copyright (c) 2015-present, Facebook, Inc.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.react.bridge.queue;


/**
 * Specifies which {@link MessageQueueThread}s must be used to run the various contexts of
 * execution within catalyst (Main UI thread, native modules, and JS). Some of these queues *may* be
 * the same but should be coded against as if they are different.
 *
 * UI Queue Thread: The standard Android main UI thread and Looper. Not configurable.
 * Native Modules Queue Thread: The thread and Looper that native modules are invoked on.
 * JS Queue Thread: The thread and Looper that JS is executed on.
 */
// ReactQueueConfigurationSpec：用于配置消息线程，在rn中有三个消息线程：UI线程、JS线程、Native线程，其中native调用js的代码会JS线程运行，JS调用native的代码会在Native线程中执行
public interface ReactQueueConfiguration {
  MessageQueueThread getUIQueueThread();
  MessageQueueThread getNativeModulesQueueThread();
  MessageQueueThread getJSQueueThread();
  void destroy();
}
