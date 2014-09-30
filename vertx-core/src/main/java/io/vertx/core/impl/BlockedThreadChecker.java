/*
 * Copyright 2014 Red Hat, Inc.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.impl;

import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.WeakHashMap;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.impl.LoggerFactory;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class BlockedThreadChecker {

  private static final Logger log = LoggerFactory.getLogger(BlockedThreadChecker.class);

  private static final Object O = new Object();
  private Map<VertxThread, Object> threads = new WeakHashMap<>();
  private final Timer timer; // Need to use our own timer - can't use event loop for this

  BlockedThreadChecker(long interval, long maxEventLoopExecTime, long maxWorkerExecTime) {
    timer = new Timer("vertx-blocked-thread-checker", true);
    timer.schedule(new TimerTask() {
      @Override
      public void run() {
        long now = System.nanoTime();
        for (VertxThread thread: threads.keySet()) {
          //use the thread's max exec time if available
          Long limit = thread.getContext().getDeployment().deploymentOptions().getMaxExecTime();
          if(DeploymentOptions.MAX_EXEC_TIME_IGNORE.equals(limit)) {
              continue;
          }
          else if(limit == null) {
            //use fallback values if thread does not specify
            limit = thread.isWorker() ? maxWorkerExecTime : maxEventLoopExecTime;
          }

          long execStart = thread.startTime();
          long dur = now - execStart;
          if (execStart != 0 && dur > limit) {
            log.warn("Thread " + thread + " has been blocked for " + (dur / 1000000) + " ms" + " time " + maxEventLoopExecTime);
            if (dur/1000000 > 5000) {
              StackTraceElement[] stack = thread.getStackTrace();
              for (StackTraceElement elem: stack) {
                System.out.println(elem);
              }
            }
          }
        }
      }
    }, interval, interval);
  }

  public synchronized void registerThread(VertxThread thread) {
    threads.put(thread, O);
  }

  public void close() {
    timer.cancel();
  }
}
