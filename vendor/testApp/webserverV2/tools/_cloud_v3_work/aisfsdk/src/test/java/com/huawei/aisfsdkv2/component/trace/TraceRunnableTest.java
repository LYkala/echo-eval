/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.trace;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * TraceRunnable 测试
 */
public class TraceRunnableTest {

    @Before
    public void setUp() {
        TraceManager.getInstance().clearCurrentTaskId();
    }

    @After
    public void tearDown() {
        TraceManager.getInstance().clearCurrentTaskId();
    }

    @Test
    public void testDelegateIsExecuted() {
        AtomicBoolean executed = new AtomicBoolean(false);
        Runnable delegate = () -> executed.set(true);
        TraceRunnable traceRunnable = new TraceRunnable(delegate);
        traceRunnable.run();
        assertTrue(executed.get());
    }

    @Test
    public void testTaskIdPropagated() {
        TraceManager.getInstance().setCurrentTaskId("parent-task");
        AtomicReference<String> capturedTaskId = new AtomicReference<>();
        Runnable delegate = () -> capturedTaskId.set(TraceManager.getInstance().getCurrentTaskId());
        TraceRunnable traceRunnable = new TraceRunnable(delegate);
        traceRunnable.run();
        assertEquals("parent-task", capturedTaskId.get());
    }

    @Test
    public void testTaskIdClearedAfterRun() {
        TraceManager.getInstance().setCurrentTaskId("task-1");
        Runnable delegate = () -> {};
        TraceRunnable traceRunnable = new TraceRunnable(delegate);
        traceRunnable.run();
        assertEquals("", TraceManager.getInstance().getCurrentTaskId());
    }

    @Test
    public void testTaskIdClearedEvenOnException() {
        TraceManager.getInstance().setCurrentTaskId("task-error");
        Runnable delegate = () -> {
            throw new RuntimeException("test exception");
        };
        TraceRunnable traceRunnable = new TraceRunnable(delegate);
        try {
            traceRunnable.run();
        } catch (RuntimeException e) {
            // expected
        }
        assertEquals("", TraceManager.getInstance().getCurrentTaskId());
    }

    @Test
    public void testSnapshotAtConstructionTime() {
        TraceManager.getInstance().setCurrentTaskId("snapshot-task");
        AtomicReference<String> capturedTaskId = new AtomicReference<>();
        Runnable delegate = () -> capturedTaskId.set(TraceManager.getInstance().getCurrentTaskId());
        TraceRunnable traceRunnable = new TraceRunnable(delegate);
        TraceManager.getInstance().setCurrentTaskId("changed-task");
        traceRunnable.run();
        assertEquals("snapshot-task", capturedTaskId.get());
    }

    @Test
    public void testEmptyTaskIdPropagated() {
        AtomicReference<String> capturedTaskId = new AtomicReference<>();
        Runnable delegate = () -> capturedTaskId.set(TraceManager.getInstance().getCurrentTaskId());
        TraceRunnable traceRunnable = new TraceRunnable(delegate);
        traceRunnable.run();
        assertEquals("", capturedTaskId.get());
    }

    @Test
    public void testCrossThreadPropagation() throws InterruptedException {
        TraceManager.getInstance().setCurrentTaskId("cross-thread-task");
        AtomicReference<String> capturedTaskId = new AtomicReference<>();
        Runnable delegate = () -> capturedTaskId.set(TraceManager.getInstance().getCurrentTaskId());
        TraceRunnable traceRunnable = new TraceRunnable(delegate);
        Thread thread = new Thread(traceRunnable);
        thread.start();
        thread.join();
        assertEquals("cross-thread-task", capturedTaskId.get());
    }
}
