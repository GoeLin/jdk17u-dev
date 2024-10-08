/*
 * Copyright (c) 2001, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package nsk.jdi.EventRequestManager.threadDeathRequests;

import com.sun.jdi.VirtualMachine;
import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.request.ThreadDeathRequest;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.event.*;

import java.util.List;
import java.io.*;

import nsk.share.*;
import nsk.share.jpda.*;
import nsk.share.jdi.*;

/**
 * The test checks that the JDI method
 * <code>com.sun.jdi.request.EventRequestManager.threadDeathRequests()</code>
 * properly returns all ThreadDeathRequest objects when:
 * <li>event requests are disabled
 * <li>event requests are enabled.<br>
 * ThreadDeathRequest objects are distinguished by the different
 * <code>EventRequest</code>'s properties.<br>
 * JDI EventHandler was added as workaround for the bug 4430096.
 * This prevents the target VM from potential hangup.
 */
public class thrdeathreq001 {
    public static final int PASSED = 0;
    public static final int FAILED = 2;
    public static final int JCK_STATUS_BASE = 95;
    static final String DEBUGGEE_CLASS =
        "nsk.jdi.EventRequestManager.threadDeathRequests.thrdeathreq001t";
    static final String COMMAND_READY = "ready";
    static final String COMMAND_QUIT = "quit";

    static final int THR_NUM = 7;
    static final String PROPS[][] = {
        {"first", "a quick"},
        {"second", "brown"},
        {"third", "fox"},
        {"fourth", "jumps"},
        {"fifth", "over"},
        {"sixth", "the lazy"},
        {"seventh", "dog"}
    };

    private Log log;
    private IOPipe pipe;
    private Debugee debuggee;
    private VirtualMachine vm;
    private EventListener elThread;
    private volatile int tot_res = PASSED;

    public static void main (String argv[]) {
        System.exit(run(argv,System.out) + JCK_STATUS_BASE);
    }

    public static int run(String argv[], PrintStream out) {
        return new thrdeathreq001().runIt(argv, out);
    }

    private int runIt(String args[], PrintStream out) {
        ArgumentHandler argHandler = new ArgumentHandler(args);
        log = new Log(out, argHandler);
        Binder binder = new Binder(argHandler, log);
        ThreadDeathRequest thrdeathRequest[] =
            new ThreadDeathRequest[THR_NUM];
        String cmd;

        debuggee = binder.bindToDebugee(DEBUGGEE_CLASS);
        pipe = debuggee.createIOPipe();
        debuggee.redirectStderr(log, "thrdeathreq001t.err> ");
        vm = debuggee.VM();
        EventRequestManager erManager = vm.eventRequestManager();
        debuggee.resume();
        cmd = pipe.readln();
        if (!cmd.equals(COMMAND_READY)) {
            log.complain("TEST BUG: unknown debuggee's command: " + cmd);
            tot_res = FAILED;
            return quitDebuggee();
        }

        for (int i=0; i<THR_NUM; i++) {
            log.display("Creating ThreadDeathRequest #" + i
                + " with the property ("
                + PROPS[i][0] + "," + PROPS[i][1] + ")...");
            thrdeathRequest[i] = erManager.createThreadDeathRequest();
            thrdeathRequest[i].putProperty(PROPS[i][0], PROPS[i][1]);
        }
        elThread = new EventListener();
        elThread.start();

// Check ThreadDeath requests when event requests are disabled
        log.display("\n1) Getting ThreadDeathRequest objects with disabled event requests...");
        checkRequests(erManager, 1);

// Check ThreadDeath requests when event requests are enabled
        for (int i=0; i<THR_NUM; i++) {
            thrdeathRequest[i].enable();
        }
        log.display("\n2) Getting ThreadDeathRequest objects with enabled event requests...");
        checkRequests(erManager, 2);

// Finish the test
        for (int i=0; i<THR_NUM; i++) {
            thrdeathRequest[i].disable();
        }
        return quitDebuggee();
    }

    private void checkRequests(EventRequestManager erManager, int t_case) {
        List reqL = erManager.threadDeathRequests();
        if (reqL.size() != THR_NUM) {
            log.complain("TEST CASE #" + t_case + " FAILED: found " + reqL.size()
                + " ThreadDeathRequest objects\n\texpected: " + THR_NUM);
            tot_res = FAILED;
            return;
        }
        for (int i=0; i<THR_NUM; i++) {
            ThreadDeathRequest thrdReq =
                (ThreadDeathRequest) reqL.get(i);
            boolean notFound = true;
            for (int j=0; j<THR_NUM; j++) {
                String propKey = (String) thrdReq.getProperty(PROPS[j][0]);
                if (propKey != null) {
                    if (propKey.equals(PROPS[j][1])) {
                        log.display("Found expected ThreadDeathRequest object with the property: ("
                            + PROPS[j][0] + "," + propKey + ")");
                    } else {
                        log.complain("TEST CASE #" + t_case
                            + " FAILED: found ThreadDeathRequest object with the unexpected property: ("
                            + PROPS[j][0] + "," + propKey + ")");
                        tot_res = FAILED;
                    }
                    notFound = false;
                    break;
                }
            }
            if (notFound) {
                log.complain("\nTEST CASE #" + t_case
                    + " FAILED: found unexpected ThreadDeathRequest object");
                tot_res = FAILED;
            }
        }
    }

    private int quitDebuggee() {
        pipe.println(COMMAND_QUIT);
        if (elThread != null) {
            try {
                if (elThread.isAlive())
                    elThread.join();
            } catch (InterruptedException e) {
                log.complain("TEST INCOMPLETE: caught InterruptedException "
                    + e);
                tot_res = FAILED;
            }
        }

        debuggee.waitFor();
        int debStat = debuggee.getStatus();
        if (debStat != (JCK_STATUS_BASE + PASSED)) {
            log.complain("TEST FAILED: debuggee's process finished with status: "
                + debStat);
            tot_res = FAILED;
        } else
            log.display("Debuggee's process finished with status: "
                + debStat);

        return tot_res;
    }

    class EventListener extends Thread {

        public void run() {
            try {
                boolean isConnected = true;
                do {
                    EventSet eventSet = vm.eventQueue().remove(1000);
                    if (eventSet != null) { // there is not a timeout
                        EventIterator it = eventSet.eventIterator();
                        while (it.hasNext()) {
                            Event event = it.nextEvent();
                            if (event instanceof VMDeathEvent || event instanceof VMDisconnectEvent) {
                                log.display("EventListener: got " + event);
                                isConnected = false;
                            } else {
                                log.display("EventListener: following JDI event occured: "
                                    + event.toString());
                                eventSet.resume();
                            }
                        }
                    }
                } while (isConnected);
            } catch (InterruptedException e) {
                tot_res = FAILED;
                log.complain("FAILURE in EventListener: caught unexpected "
                    + e);
            } catch (VMDisconnectedException e) {
                tot_res = FAILED;
                log.complain("FAILURE in EventListener: caught unexpected "
                    + e);
                e.printStackTrace();
            }
            log.display("EventListener: exiting");
        }
    }
}
